package com.pocketwise.feature.expense_core

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onPlaced
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.filter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.pocketwise.core.util.formatMoney
import com.pocketwise.core.util.groupedAmountParts
import com.pocketwise.core.util.symbolPrefix
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.focus.onFocusChanged
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

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

private val CompactBarHeight = 52.dp

// Plain holder, not state: coordinates change every scroll frame and must not trigger recomposition.
private class PlacedCoordinates {
    var hero: LayoutCoordinates? = null
    var amount: LayoutCoordinates? = null
}

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
    val comparison by viewModel.comparison.collectAsState()
    var showBudgetDialog by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    var searchQuery by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    // While searching, the hero card gives its space to results; the compact bar keeps the total visible.
    val searching = searchFocused || searchQuery.isNotBlank()
    // Long-press enters selection mode (selecting that row); further taps
    // toggle rows in/out instead of opening them for edit.
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }

    val listState = rememberLazyListState()
    // The collapse is keyed to the big amount, not the whole card: the compact total
    // lands exactly as the big one slides out of view, so the number is never gone
    // and never on screen twice. amountBottomPx = where it ends inside the hero item.
    var amountBottomPx by remember { mutableFloatStateOf(0f) }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val placed = remember { PlacedCoordinates() }
    fun measureAmount() {
        val hero = placed.hero
        val amount = placed.amount
        if (hero?.isAttached == true && amount?.isAttached == true) {
            val bottom = hero.localBoundingBoxOf(amount, clipBounds = false).bottom
            // Guarded: the hero item is re-placed as it scrolls, but this value only changes with its layout.
            if (bottom != amountBottomPx) amountBottomPx = bottom
        }
    }
    // Read only from draw-phase lambdas (drawBehind/graphicsLayer): scrolling redraws
    // the header but never recomposes or re-measures anything.
    val morphFraction: () -> Float = {
        when {
            searching || listState.firstVisibleItemIndex > 0 -> 1f
            amountBottomPx <= 0f -> 0f
            else -> (listState.firstVisibleItemScrollOffset / amountBottomPx).coerceIn(0f, 1f)
        }
    }
    // Gone = scrolled away, or fully tucked under the floating header. A short list can
    // stop before item 1 with the card already hidden, so the index alone isn't enough.
    // A boolean, so it recomposes only when it flips.
    val barHeightPx = with(LocalDensity.current) { CompactBarHeight.toPx() }
    val heroGone by remember(barHeightPx) {
        derivedStateOf {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            listState.firstVisibleItemIndex > 0 ||
                (first != null && first.key == "hero" && first.offset + first.size <= barHeightPx)
        }
    }

    // Never rest half-collapsed: when a scroll settles mid-morph, finish it to the
    // nearer end — the same settle a collapsing app bar does.
    val isSearching by rememberUpdatedState(searching)
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect {
                val end = amountBottomPx
                val y = listState.firstVisibleItemScrollOffset.toFloat()
                if (!isSearching && listState.firstVisibleItemIndex == 0 && y > 1f && y < end - 1f) {
                    listState.animateScrollBy(if (y < end / 2) -y else end - y)
                }
            }
    }

    // Total/budget always reflect the real month, independent of the search
    // filter below — a search shouldn't make the budget look wrong.
    val total = expenses.sumOf { it.amount }
    val visibleExpenses = remember(expenses, searchQuery) {
        expenses.filter { expense ->
            searchQuery.isBlank() ||
                expense.description.contains(searchQuery, ignoreCase = true) ||
                expense.category.contains(searchQuery, ignoreCase = true)
        }
    }
    val expensesByDay = remember(visibleExpenses) { visibleExpenses.groupBy { it.timestamp.toLocalDate() } }
    val today = LocalDate.now()
    val isCurrentMonth = month == YearMonth.from(today)

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedIds.isNotEmpty()) {
            SelectionBar(
                count = selectedIds.size,
                onCancel = { selectedIds = emptySet() },
                // No confirm dialog: the Undo snackbar (MainActivity) covers mistakes.
                onDelete = {
                    viewModel.deleteExpenses(expenses.filter { it.id in selectedIds })
                    selectedIds = emptySet()
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        // The collapsed header floats over the list instead of sitting above it, so it
        // can appear and disappear without ever resizing the list or shoving content.
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // Bottom padding clears the FAB, so the last expense can always scroll
                // out from underneath it. While searching there's no hero, so results
                // start below the floating header instead of under it.
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = if (searching) with(LocalDensity.current) { headerHeightPx.toDp() } else 0.dp,
                    bottom = 96.dp
                )
            ) {
                if (!searching) item(key = "hero") {
                    Column(
                        modifier = Modifier
                            .onPlaced {
                                placed.hero = it
                                measureAmount()
                            }
                            .drawWithContent {
                                // Tuck the card under the floating header: nothing of it draws above
                                // a line that slides from the list top to the header's bottom edge as
                                // the morph runs. Draw-phase only — scrolling never re-measures.
                                val top = if (listState.firstVisibleItemIndex == 0) {
                                    morphFraction() * barHeightPx + listState.firstVisibleItemScrollOffset
                                } else {
                                    0f
                                }
                                if (top <= 0f) {
                                    drawContent()
                                } else {
                                    // Clip the top edge only. The card's shadow spills past the item's
                                    // bounds; a default clipRect cuts it into hard dark lines at the edges.
                                    clipRect(left = -size.width, top = top, right = size.width * 2, bottom = size.height * 2) {
                                        this@drawWithContent.drawContent()
                                    }
                                }
                            }
                    ) {
                        SummaryCard(
                            total = total,
                            budget = budget,
                            comparison = comparison,
                            currencySymbol = currencySymbol,
                            // Pace only means something for the month in progress.
                            monthProgress = if (isCurrentMonth) today.dayOfMonth.toFloat() / month.lengthOfMonth() else null,
                            daysLeft = if (isCurrentMonth) month.lengthOfMonth() - today.dayOfMonth + 1 else null,
                            onEditBudget = { showBudgetDialog = true },
                            onAmountPlaced = {
                                placed.amount = it
                                measureAmount()
                            }
                        )
                    }
                }

                if (visibleExpenses.isEmpty()) {
                    item(key = "empty") {
                        EmptyExpenses(
                            message = when {
                                expenses.isEmpty() && isCurrentMonth -> "Tap + to log your first expense this month."
                                expenses.isEmpty() -> "Nothing was logged in $monthLabel."
                                else -> "No expenses match \"$searchQuery\"."
                            }
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
                                        formatMoney(currencySymbol, dayExpenses.sumOf { it.amount }),
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
                                            selected = expense.id in selectedIds,
                                            onClick = {
                                                if (selectedIds.isNotEmpty()) {
                                                    selectedIds = selectedIds.toggle(expense.id)
                                                } else {
                                                    onEditExpense(expense.id)
                                                }
                                            },
                                            onLongClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                selectedIds = selectedIds.toggle(expense.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.onSizeChanged { headerHeightPx = it.height }) {
                // Takes over showing the total as the big one leaves, so that number is never off screen.
                CompactSummaryBar(total = total, currencySymbol = currencySymbol, fraction = morphFraction)
                if (selectedIds.isEmpty() && expenses.isNotEmpty()) {
                    // Search joins the header once the whole card has scrolled away, and stays while in use.
                    // ponytail: a list too short to scroll the card away never shows search — fine for a handful of rows.
                    AnimatedVisibility(
                        visible = searching || heroGone,
                        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 3 },
                        exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 3 }
                    ) {
                        SearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                                .onFocusChanged { searchFocused = it.isFocused }
                        )
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
}

// Pinned over the top of the list at a fixed height — never part of its layout, so it
// can't push content. Everything animates in the draw phase from [fraction].
@Composable
private fun CompactSummaryBar(
    total: Double,
    currencySymbol: String,
    fraction: () -> Float,
    modifier: Modifier = Modifier
) {
    val background = MaterialTheme.colorScheme.background
    val hairline = MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CompactBarHeight)
            .drawBehind {
                val f = fraction()
                // Transparent while the card tucks under — the card clips itself at the header
                // line instead, since a translucent wash would band across it (badly in dark mode).
                // Opaque once docked, when only page background is underneath: an invisible switch.
                if (f >= 1f) drawRect(background)
                // Hairline only once docked: the "content scrolls under me" cue.
                val line = 1.dp.toPx()
                drawRect(
                    hairline.copy(alpha = ((f - 0.9f) * 10f).coerceIn(0f, 1f)),
                    topLeft = Offset(0f, size.height - line),
                    size = Size(size.width, line)
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .graphicsLayer {
                    // Fades in over the second half — the big amount has tucked out of sight by ~60%.
                    val t = ((fraction() - 0.5f) / 0.5f).coerceIn(0f, 1f)
                    alpha = t
                    // Rises the last few dp into place, moving the same way as the big amount leaving.
                    translationY = (1f - t) * 12.dp.toPx()
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
                formatMoney(currencySymbol, total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("Search expenses") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun SelectionBar(count: Int, onCancel: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
        }
        Text(
            "$count selected",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(start = 4.dp)
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
        }
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
    comparison: PeriodComparison?,
    currencySymbol: String,
    monthProgress: Float?,
    daysLeft: Int?,
    onEditBudget: () -> Unit,
    modifier: Modifier = Modifier,
    onAmountPlaced: (LayoutCoordinates) -> Unit = {}
) {
    val ink = MaterialTheme.colorScheme.onTertiaryContainer
    val muted = ink.copy(alpha = 0.65f)

    // Hero surface — the one card that should read as elevated above
    // everything else; a soft shadow does that job now, not a loud color.
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Total spent", style = MaterialTheme.typography.labelLarge, color = muted)
            HeroAmount(
                amount = total,
                symbol = currencySymbol,
                ink = ink,
                muted = muted,
                // onPlaced, not onGloballyPositioned: scrolling moves the list item's layer without
                // re-placing its children, so this fires on real layout changes, not every frame.
                modifier = Modifier.onPlaced(onAmountPlaced)
            )
            // Context for the number: is this month heavier or lighter than usual?
            if (comparison != null && comparison.previousTotal > 0) {
                DeltaPill(total = total, comparison = comparison, modifier = Modifier.padding(top = 8.dp))
            }

            if (budget > 0) {
                val remaining = budget - total
                val overBudget = remaining < 0
                val statusColor = if (overBudget) MaterialTheme.colorScheme.error else ink

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when {
                            overBudget -> "${formatMoney(currencySymbol, -remaining)} over"
                            daysLeft == null -> "${formatMoney(currencySymbol, remaining)} under budget"
                            else -> "${formatMoney(currencySymbol, remaining)} left"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                    // The budget itself is the edit target: a lone pencil beside
                    // the total read as "edit the total".
                    Row(
                        modifier = Modifier
                            .offset(x = 8.dp) // keep the text flush with the bar's end
                            .clip(CircleShape)
                            .clickable(onClickLabel = "Change monthly budget", onClick = onEditBudget)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("of ${formatMoney(currencySymbol, budget)}", style = MaterialTheme.typography.labelLarge, color = muted)
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = null,
                            tint = muted,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(14.dp)
                        )
                    }
                }
                BudgetMeter(
                    progress = (total / budget).toFloat().coerceIn(0f, 1f),
                    monthProgress = monthProgress,
                    fillColor = statusColor,
                    trackColor = ink.copy(alpha = 0.12f),
                    markerColor = ink,
                    gapColor = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Text(
                    // Mid-month, the actionable number: what's left to spend per day.
                    // Once over, a percent like "2703%" says nothing — days still to cover does.
                    when {
                        daysLeft == null && overBudget -> "${"%.1f".format(total / budget).removeSuffix(".0")}× the budget"
                        daysLeft == null -> "${(total / budget * 100).roundToInt()}% of budget used"
                        overBudget -> "No budget left · $daysLeft ${if (daysLeft == 1) "day" else "days"} to go"
                        else -> "About ${formatMoney(currencySymbol, (remaining / daysLeft).roundToInt().toDouble())}/day for $daysLeft ${if (daysLeft == 1) "day" else "days"}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    modifier = Modifier.padding(top = 10.dp)
                )
            } else {
                TextButton(
                    onClick = onEditBudget,
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
                    Text("Set a monthly budget", color = ink, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

// Symbol and cents recede so the whole amount is what the eye lands on;
// tabular digits keep the width steady as the number changes.
@Composable
private fun HeroAmount(amount: Double, symbol: String, ink: Color, muted: Color, modifier: Modifier = Modifier) {
    val (whole, fraction) = groupedAmountParts(amount)
    val minor = SpanStyle(fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Medium, color = muted)
    Text(
        buildAnnotatedString {
            withStyle(minor) { append(symbolPrefix(symbol)) }
            append(whole)
            withStyle(minor) { append(fraction) }
        },
        style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
        fontWeight = FontWeight.SemiBold,
        color = ink,
        maxLines = 1,
        modifier = modifier
    )
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
    val animatedProgress by animateFloatAsState(progress, ProgressIndicatorDefaults.ProgressAnimationSpec, label = "budgetProgress")
    Box(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "${(progress * 100).roundToInt()}% of budget used" +
                (monthProgress?.let { ", ${(it * 100).roundToInt()}% of the month gone" } ?: "")
        }
    ) {
        // Material expressive wavy bar, flowing at its default speed. The default amplitude
        // flattens near full, which made an over-budget month a plain line — keep the wave for any spend.
        LinearWavyProgressIndicator(
            progress = { animatedProgress },
            color = fillColor,
            trackColor = trackColor,
            amplitude = { if (it > 0f) 1f else 0f },
            modifier = Modifier.fillMaxWidth()
        )
        monthProgress?.let {
            Canvas(modifier = Modifier.matchParentSize()) {
                val x = size.width * it
                val overhang = 4.dp.toPx()
                // Card-colored gap first, so the marker stays visible even on top of the wave.
                drawLine(gapColor, Offset(x, -overhang), Offset(x, size.height + overhang), strokeWidth = 4.dp.toPx())
                drawLine(markerColor, Offset(x, -overhang), Offset(x, size.height + overhang), strokeWidth = 2.dp.toPx())
            }
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

// Tap to edit, long-press to select (more taps add to the selection, then
// delete from the bar above). No inline delete button and no swipe: swipe
// would fight the tab swipe, and long-press-to-select is one gesture that
// covers both the single- and multi-delete case.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpenseRow(
    expense: Expense,
    icon: CategoryIcon,
    currencySymbol: String,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Select expense")
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(icon.imageVector(), contentDescription = null, modifier = Modifier.size(20.dp))
            }
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
            formatMoney(currencySymbol, expense.amount),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
