package com.pocketwise.feature.expense_core

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pocketwise.core.model.CategoryIcon
import com.pocketwise.core.model.Expense
import com.pocketwise.core.model.SupportedCurrencies
import com.pocketwise.core.model.currencySymbolFor
import com.pocketwise.core.ui.components.BottomNavBar
import com.pocketwise.core.ui.icons.imageVector
import com.pocketwise.core.util.formatAmount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onManageCategories: () -> Unit = {},
    onAddExpense: () -> Unit = {},
    onEditExpense: (Long) -> Unit = {},
    viewModel: ExpenseViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel()
) {
    val monthLabel by viewModel.monthLabel.collectAsState()
    val hasNextMonth by viewModel.hasNextMonth.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val categories by categoryViewModel.categories.collectAsState()
    val categoryIcons = remember(categories) { categories.associate { it.name to it.icon } }
    val currencyCode by viewModel.currencyCode.collectAsState()
    val currencySymbol = currencySymbolFor(currencyCode)
    val budget by viewModel.monthlyBudget.collectAsState()
    var showBudgetDialog by remember { mutableStateOf(false) }
    var expensePendingDelete by remember { mutableStateOf<Expense?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PocketWise") },
                actions = {
                    IconButton(onClick = onManageCategories) {
                        Icon(Icons.Filled.Sell, contentDescription = "Manage categories")
                    }
                    CurrencySelector(currencyCode = currencyCode, onSelect = viewModel::setCurrency)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = { BottomNavBar(onAddClick = onAddExpense) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            MonthSwitcher(
                label = monthLabel,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
                hasPrevious = true,
                hasNext = hasNextMonth
            )

            SummaryCard(
                total = expenses.sumOf { it.amount },
                budget = budget,
                currencySymbol = currencySymbol,
                onEditBudget = { showBudgetDialog = true },
                modifier = Modifier.padding(top = 16.dp)
            )

            Text(
                "Expenses",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )

            if (expenses.isEmpty()) {
                Text(
                    "No expenses this month yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                val expensesByDay = expenses.groupBy { it.timestamp.toLocalDate() }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    expensesByDay.forEach { (date, dayExpenses) ->
                        item(key = "header_$date") {
                            Text(
                                dateLabel(date),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                            )
                        }
                        items(dayExpenses, key = { it.id }) { expense ->
                            ExpenseRow(
                                expense = expense,
                                icon = categoryIcons[expense.category] ?: CategoryIcon.OTHER,
                                currencySymbol = currencySymbol,
                                onClick = { onEditExpense(expense.id) },
                                onDelete = { expensePendingDelete = expense }
                            )
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
        AlertDialog(
            onDismissRequest = { expensePendingDelete = null },
            title = { Text("Delete expense?") },
            text = { Text("This expense will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteExpense(expense)
                    expensePendingDelete = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { expensePendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MonthSwitcher(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    hasPrevious: Boolean,
    hasNext: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, enabled = hasPrevious) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
        }
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        IconButton(onClick = onNext, enabled = hasNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun CurrencySelector(currencyCode: String, onSelect: (String) -> Unit) {
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
    onEditBudget: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Hero surface: inverted charcoal (zinc-900), the one high-priority card.
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Total Spent",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                IconButton(onClick = onEditBudget, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Set monthly budget",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                "$currencySymbol${formatAmount(total)}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            if (budget > 0) {
                val overBudget = total > budget
                val statusColor = if (overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer

                LinearProgressIndicator(
                    progress = { (total / budget).toFloat().coerceIn(0f, 1f) },
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                )
                Text(
                    if (overBudget) {
                        "Over budget by $currencySymbol${formatAmount(total - budget)}"
                    } else {
                        "$currencySymbol${formatAmount(budget - total)} left of $currencySymbol${formatAmount(budget)} budget"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else {
                TextButton(onClick = onEditBudget, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Set a monthly budget", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly Budget") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Amount ($currencySymbol)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                text.toDoubleOrNull()?.let(onSave)
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ExpenseRow(expense: Expense, icon: CategoryIcon, currencySymbol: String, onClick: () -> Unit, onDelete: () -> Unit) {
    // Secondary surface: subtle nested rows (zinc-50/zinc-800), distinct
    // from the pure-white/zinc-900 panel they sit inside. Tap to edit.
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon.imageVector(),
                    contentDescription = expense.category,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(expense.description, style = MaterialTheme.typography.bodyLarge)
                Text(
                    expense.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "-$currencySymbol${formatAmount(expense.amount)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Delete expense",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
