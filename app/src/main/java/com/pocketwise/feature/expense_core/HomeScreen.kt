package com.pocketwise.feature.expense_core

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Receipt
import com.pocketwise.core.ui.components.AppDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pocketwise.core.data.repository.SOURCE_RECURRING
import com.pocketwise.core.model.CategoryIcon
import com.pocketwise.core.model.Expense
import com.pocketwise.core.model.SupportedCurrencies
import com.pocketwise.core.model.currencySymbolFor
import com.pocketwise.core.ui.icons.imageVector
import com.pocketwise.core.util.formatAmount
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val DateHeaderFormat = DateTimeFormatter.ofPattern("EEEE, MMM d")

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private fun dateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateHeaderFormat)
    }
}

// The app bar and month switcher are shared across tabs (see MainActivity);
// this screen is just the page content below them.
@Composable
fun HomeScreen(
    onEditExpense: (Long) -> Unit = {},
    viewModel: ExpenseViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel()
) {
    val month by viewModel.selectedMonth.collectAsState()
    val monthLabel by viewModel.monthLabel.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val categories by categoryViewModel.categories.collectAsState()
    val categoryIcons = remember(categories) { categories.associate { it.name to it.icon } }
    val currencyCode by viewModel.currencyCode.collectAsState()
    val currencySymbol = currencySymbolFor(currencyCode)
    val budget by viewModel.monthlyBudget.collectAsState()
    var showBudgetDialog by remember { mutableStateOf(false) }
    var expensePendingDelete by remember { mutableStateOf<Expense?>(null) }
    val haptics = LocalHapticFeedback.current

    val listState = rememberLazyListState()
    var heroHeightPx by remember { mutableIntStateOf(0) }
    // How far the hero block (summary card) has scrolled out of view, 0f
    // (fully visible) to 1f (fully scrolled past) — driven directly by real
    // scroll pixels, not a threshold guess, so the compact header below tracks
    // the finger 1:1 instead of lagging behind it.
    val collapseFraction by remember {
        derivedStateOf {
            if (heroHeightPx <= 0) {
                0f
            } else if (listState.firstVisibleItemIndex == 0) {
                (listState.firstVisibleItemScrollOffset.toFloat() / heroHeightPx).coerceIn(0f, 1f)
            } else {
                1f
            }
        }
    }

    val total = expenses.sumOf { it.amount }
    val expensesByDay = remember(expenses) { expenses.groupBy { it.timestamp.toLocalDate() } }
    val today = LocalDate.now()
    val isCurrentMonth = month == YearMonth.from(today)

    Column(modifier = Modifier.fillMaxSize()) {
        // Grows in as the hero card below scrolls out of view — takes over
        // showing the total once the big card isn't visible anymore, so that
        // information is never fully gone from screen.
        CompactSummaryBar(
            total = total,
            currencySymbol = currencySymbol,
            fraction = collapseFraction,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            // Bottom padding clears the FAB, so the last expense can always
            // scroll out from underneath it.
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp)
        ) {
            item(key = "hero") {
                Column(
                    modifier = Modifier.onGloballyPositioned {
                        // Guard against redundant writes — this callback
                        // fires on every scroll frame too (global
                        // *position* changes even when size doesn't), and
                        // an unconditional write would invalidate the
                        // collapseFraction state on every single frame.
                        if (it.size.height != heroHeightPx) heroHeightPx = it.size.height
                    }
                ) {
                    SummaryCard(
                        total = total,
                        budget = budget,
                        currencySymbol = currencySymbol,
                        // Pace only means something for the month in progress.
                        monthProgress = if (isCurrentMonth) today.dayOfMonth.toFloat() / month.lengthOfMonth() else null,
                        daysLeft = if (isCurrentMonth) month.lengthOfMonth() - today.dayOfMonth + 1 else null,
                        onEditBudget = { showBudgetDialog = true }
                    )
                }
            }

            if (expenses.isEmpty()) {
                item(key = "empty") {
                    EmptyExpenses(
                        message = if (isCurrentMonth) "Tap + to log your first expense this month." else "Nothing was logged in $monthLabel."
                    )
                }
            } else {
                expensesByDay.forEach { (date, dayExpenses) ->
                    // One card per day: a day reads as a single unit, and the
                    // day total sits right in its header.
                    item(key = "day_$date") {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(dateLabel(date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "$currencySymbol${formatAmount(dayExpenses.sumOf { it.amount })}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                dayExpenses.forEachIndexed { index, expense ->
                                    if (index > 0) {
                                        // Background-colored hairline: a gap, not a drawn border.
                                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.background)
                                    }
                                    ExpenseRow(
                                        expense = expense,
                                        icon = categoryIcons[expense.category] ?: CategoryIcon.OTHER,
                                        currencySymbol = currencySymbol,
                                        onClick = { onEditExpense(expense.id) },
                                        onLongClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            expensePendingDelete = expense
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBudgetDialog) {
        BudgetDialog(
            currentBudget = budget,
            currencySymbol = currencySymbol,
            onDismiss = { showBudgetDialog = false },
            onSave = { viewModel.setMonthlyBudget(it) }
        )
    }

    expensePendingDelete?.let { expense ->
        AppDialog(
            title = "Delete expense?",
            onDismiss = { expensePendingDelete = null },
            confirmLabel = "Delete",
            onConfirm = { viewModel.deleteExpense(expense); expensePendingDelete = null },
            destructive = true,
        ) {
            Text("\"${expense.description}\" ($currencySymbol${formatAmount(expense.amount)}) will be permanently removed.")
        }
    }
}

@Composable
private fun CompactSummaryBar(
    total: Double,
    currencySymbol: String,
    fraction: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // ponytail: flips 0dp/52dp only at rest, not every frame — a continuous
            // height animation is what caused the scroll lag this replaced, but a
            // fixed 52dp at fraction=0 left a dead gap above the dashboard card
            .height(if (fraction > 0f) 52.dp else 0.dp)
            .graphicsLayer {
                // alpha/scale are draw-only (GPU) properties — changing them
                // every frame costs nothing extra, unlike layout properties.
                alpha = fraction
                scaleY = fraction
                transformOrigin = TransformOrigin(0.5f, 0f)
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Total spent",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "$currencySymbol${formatAmount(total)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CurrencySelector(currencyCode: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text(currencyCode, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Filled.ExpandMore, contentDescription = "Change currency")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SupportedCurrencies.forEach { option ->
                DropdownMenuItem(
                    text = { Text("${option.code} — ${option.label}") },
                    onClick = {
                        onSelect(option.code)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    total: Double,
    budget: Double,
    currencySymbol: String,
    monthProgress: Float?,
    daysLeft: Int?,
    onEditBudget: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Hero surface — the one card that should read as elevated above
    // everything else; a soft shadow does that job now, not a loud color.
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Total spent",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                IconButton(onClick = onEditBudget, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Set monthly budget",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                "$currencySymbol${formatAmount(total)}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            if (budget > 0) {
                val remaining = budget - total
                val overBudget = remaining < 0
                val statusColor = if (overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onTertiaryContainer

                BudgetMeter(
                    progress = (total / budget).toFloat().coerceIn(0f, 1f),
                    monthProgress = monthProgress,
                    fillColor = statusColor,
                    trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.15f),
                    markerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    gapColor = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(8.dp)
                )
                Text(
                    when {
                        overBudget -> "Over budget by $currencySymbol${formatAmount(-remaining)}"
                        // The number people actually act on: what they can
                        // still spend per day and stay within budget.
                        daysLeft != null -> "$currencySymbol${formatAmount(remaining)} left · about " +
                            "$currencySymbol${(remaining / daysLeft).roundToInt()}/day for $daysLeft ${if (daysLeft == 1) "day" else "days"}"
                        else -> "$currencySymbol${formatAmount(remaining)} left of $currencySymbol${formatAmount(budget)} budget"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    modifier = Modifier.padding(top = 10.dp)
                )
            } else {
                TextButton(onClick = onEditBudget, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Set a monthly budget", color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
    }
}

// Budget bar with a "today" marker. Fill past the marker means spending is
// running ahead of the month's pace — something a bare percentage can't show.
@Composable
private fun BudgetMeter(
    progress: Float,
    monthProgress: Float?,
    fillColor: Color,
    trackColor: Color,
    markerColor: Color,
    gapColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.semantics {
            contentDescription = "${(progress * 100).roundToInt()}% of budget used" +
                (monthProgress?.let { ", ${(it * 100).roundToInt()}% of the month gone" } ?: "")
        }
    ) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(trackColor, cornerRadius = radius)
        if (progress > 0f) {
            drawRoundRect(fillColor, size = Size(size.width * progress, size.height), cornerRadius = radius)
        }
        monthProgress?.let {
            val x = size.width * it
            val overhang = 4.dp.toPx()
            // Card-colored gap first, so the marker stays visible even on top of the fill.
            drawLine(gapColor, Offset(x, -overhang), Offset(x, size.height + overhang), strokeWidth = 4.dp.toPx())
            drawLine(markerColor, Offset(x, -overhang), Offset(x, size.height + overhang), strokeWidth = 2.dp.toPx())
        }
    }
}

@Composable
private fun EmptyExpenses(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Receipt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp)
        )
        Text(
            "No expenses yet",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun BudgetDialog(
    currentBudget: Double,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var text by remember { mutableStateOf(if (currentBudget > 0) formatAmount(currentBudget) else "") }

    AppDialog(
        title = "Monthly Budget",
        onDismiss = onDismiss,
        confirmLabel = "Save",
        onConfirm = { text.toDoubleOrNull()?.let(onSave); onDismiss() },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Amount ($currencySymbol)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Tap to edit, long-press to delete. No inline delete button: it was one
// careless tap from data loss, and swipe-to-delete would fight the tab swipe.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpenseRow(expense: Expense, icon: CategoryIcon, currencySymbol: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Delete expense")
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.background, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon.imageVector(), contentDescription = null, modifier = Modifier.size(20.dp))
        }

        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        ) {
            Text(
                expense.description,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (expense.source == SOURCE_RECURRING) "${expense.category} · Recurring" else expense.category,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            "$currencySymbol${formatAmount(expense.amount)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
