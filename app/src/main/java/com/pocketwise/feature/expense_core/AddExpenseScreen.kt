package com.pocketwise.feature.expense_core

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pocketwise.core.model.Category
import com.pocketwise.core.model.Expense
import com.pocketwise.core.model.currencySymbolFor
import com.pocketwise.core.ui.icons.imageVector
import com.pocketwise.core.util.ordinal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DateFormat = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
private val SystemZone = ZoneId.systemDefault()

private fun LocalDate.toEpochMillisAtNoon(): Long = atTime(12, 0).atZone(SystemZone).toInstant().toEpochMilli()
private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(SystemZone).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    onDone: () -> Unit,
    expenseId: Long? = null,
    expenseViewModel: ExpenseViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel()
) {
    val categories by categoryViewModel.categories.collectAsState()
    val currencyCode by expenseViewModel.currencyCode.collectAsState()
    val currencySymbol = currencySymbolFor(currencyCode)

    var amountText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var description by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var prefilled by remember { mutableStateOf(false) }
    var repeatMonthly by remember { mutableStateOf(false) }

    LaunchedEffect(expenseId) {
        if (expenseId != null) editingExpense = expenseViewModel.getExpenseById(expenseId)
    }

    LaunchedEffect(editingExpense, categories) {
        val expense = editingExpense
        if (!prefilled && expense != null && categories.isNotEmpty()) {
            amountText = trimTrailingZeros(expense.amount)
            description = expense.description
            selectedCategory = categories.find { it.name == expense.category }
            selectedDate = expense.timestamp.toLocalDate()
            prefilled = true
        }
    }

    val amount = amountText.toDoubleOrNull()
    val canSave = amount != null && amount > 0 && selectedCategory != null && description.isNotBlank()
    // Editing a real expense means an async DB read before the fields have
    // real values — render nothing (not empty fields) until that lands, so
    // the form doesn't visibly pop from blank to filled mid-transition.
    val isReady = expenseId == null || prefilled

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (expenseId != null) "Edit Expense" else "Add Expense") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                // Delete lives here (and behind long-press on Home), not as a
                // one-tap button on every list row.
                actions = {
                    if (editingExpense != null) {
                        // Undo snackbar (MainActivity) replaces a confirm dialog.
                        IconButton(onClick = { expenseViewModel.deleteExpenses(listOfNotNull(editingExpense)); onDone() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete expense")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (isReady) {
                // Scaffold's bottomBar isn't auto-padded for the system nav bar
                // (only NavigationBar does that) — without this, 3-button nav
                // overlaps the Save button.
                Column(modifier = Modifier.navigationBarsPadding().padding(20.dp)) {
                    Button(
                        onClick = {
                            val timestamp = selectedDate.toEpochMillisAtNoon()
                            val expense = editingExpense
                            if (expense != null) {
                                expenseViewModel.updateExpense(expense, amount!!, selectedCategory!!.name, description, timestamp)
                            } else {
                                expenseViewModel.addExpense(amount!!, selectedCategory!!.name, description, timestamp)
                                if (repeatMonthly) {
                                    expenseViewModel.addRecurring(amount, selectedCategory!!.name, description, selectedDate)
                                }
                            }
                            onDone()
                        },
                        enabled = canSave,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("Save Expense", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    ) { padding ->
        if (!isReady) return@Scaffold

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Title as a big borderless heading — you're naming this expense,
            // not filling in a boxed form field.
            TitleField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 20.dp)
            )

            // Hero amount — the single most important input, given the most
            // visual weight instead of being just another boxed text field.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    currencySymbol,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
                AmountField(
                    value = amountText,
                    onValueChange = { amountText = it }
                )
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(
                    "Category",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(categories, key = { it.id }) { category ->
                    CategoryTile(
                        category = category,
                        selected = category.id == selectedCategory?.id,
                        onClick = { selectedCategory = category }
                    )
                }
            }

            Card(
                onClick = { showDatePicker = true },
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                "Date",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (selectedDate == LocalDate.now()) "Today" else selectedDate.format(DateFormat),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // New expenses only — editing one past occurrence shouldn't create a rule.
            if (editingExpense == null) {
                RepeatMonthlyRow(
                    checked = repeatMonthly,
                    onCheckedChange = { repeatMonthly = it },
                    dayOfMonth = selectedDate.dayOfMonth,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)
                )
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState, showModeToggle = false)
        }
    }
}

// Same card style as the date row above it.
@Composable
private fun RepeatMonthlyRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, dayOfMonth: Int, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Autorenew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Text("Repeat every month", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (checked) "Added automatically on the ${ordinal(dayOfMonth)} of each month" else "For rent, internet, subscriptions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

private fun trimTrailingZeros(amount: Double): String {
    val formatted = "%.2f".format(amount)
    return if (formatted.endsWith(".00")) formatted.dropLast(3) else formatted
}

@Composable
private fun TitleField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.headlineSmall.copy(
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text("Title", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun AmountField(value: String, onValueChange: (String) -> Unit) {
    val style = MaterialTheme.typography.displayLarge.copy(
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )

    BasicTextField(
        value = value,
        onValueChange = { new ->
            if (new.isEmpty() || new.matches(Regex("^\\d{0,7}(\\.\\d{0,2})?$"))) onValueChange(new)
        },
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.widthIn(min = 48.dp),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text("0", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun CategoryTile(category: Category, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(68.dp).clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                category.icon.imageVector(),
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            category.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
