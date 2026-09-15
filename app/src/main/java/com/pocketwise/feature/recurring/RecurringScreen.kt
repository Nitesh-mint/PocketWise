package com.pocketwise.feature.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pocketwise.core.data.repository.nextDate
import com.pocketwise.core.data.voice.FALLBACK_CATEGORY
import com.pocketwise.core.model.Category
import com.pocketwise.core.model.CategoryIcon
import com.pocketwise.core.model.RecurringExpense
import com.pocketwise.core.model.currencySymbolFor
import com.pocketwise.core.ui.icons.imageVector
import com.pocketwise.core.util.formatAmount
import com.pocketwise.core.util.ordinal
import java.time.format.DateTimeFormatter

private val NextDateFormat = DateTimeFormatter.ofPattern("MMM d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(onBack: () -> Unit, viewModel: RecurringViewModel = hiltViewModel()) {
    val rules by viewModel.rules.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val currencyCode by viewModel.currencyCode.collectAsState()
    val symbol = currencySymbolFor(currencyCode)
    var editing by remember { mutableStateOf<RecurringExpense?>(null) }
    var stopping by remember { mutableStateOf<RecurringExpense?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring expenses") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (rules.isEmpty()) {
            EmptyRecurring(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "summary") {
                    Text(
                        "${rules.size} active · $symbol${formatAmount(rules.sumOf { it.amount })} every month",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }
                items(rules, key = { it.id }) { rule ->
                    RecurringRow(
                        rule = rule,
                        icon = categories.find { it.name == rule.category }?.icon ?: CategoryIcon.OTHER,
                        symbol = symbol,
                        onClick = { editing = rule },
                        onStop = { stopping = rule }
                    )
                }
            }
        }
    }

    editing?.let { rule ->
        EditRecurringDialog(
            rule = rule,
            categories = categories,
            symbol = symbol,
            onDismiss = { editing = null },
            onSave = {
                viewModel.update(it)
                editing = null
            }
        )
    }

    stopping?.let { rule ->
        AlertDialog(
            onDismissRequest = { stopping = null },
            title = { Text("Stop repeating?") },
            text = { Text("\"${rule.description}\" won't be added anymore. Expenses already added stay.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.stop(rule)
                    stopping = null
                }) {
                    Text("Stop", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { stopping = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringRow(rule: RecurringExpense, icon: CategoryIcon, symbol: String, onClick: () -> Unit, onStop: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
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
                Text(rule.description, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Every month on the ${ordinal(rule.dayOfMonth)} · next ${rule.nextDate().format(NextDateFormat)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "$symbol${formatAmount(rule.amount)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp)
            )
            IconButton(onClick = onStop) {
                Icon(Icons.Filled.Delete, contentDescription = "Stop repeating ${rule.description}", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun EditRecurringDialog(
    rule: RecurringExpense,
    categories: List<Category>,
    symbol: String,
    onDismiss: () -> Unit,
    onSave: (RecurringExpense) -> Unit
) {
    var description by remember { mutableStateOf(rule.description) }
    var amountText by remember { mutableStateOf(formatAmount(rule.amount)) }
    var dayText by remember { mutableStateOf(rule.dayOfMonth.toString()) }
    var category by remember { mutableStateOf(rule.category) }
    val amount = amountText.toDoubleOrNull()?.takeIf { it > 0 }
    val day = dayText.toIntOrNull()?.takeIf { it in 1..31 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit recurring expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) amountText = it },
                    label = { Text("Amount ($symbol)") },
                    singleLine = true,
                    isError = amount == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = dayText,
                    onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) dayText = it },
                    label = { Text("Day of month") },
                    singleLine = true,
                    isError = day == null,
                    supportingText = { Text("Days past a month's end use its last day") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                CategoryPicker(selected = category, categories = categories, onSelect = { category = it })
                Text(
                    "Changes apply from the next time it's added.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && day != null && description.isNotBlank(),
                onClick = {
                    onSave(rule.copy(description = description.trim(), amount = amount!!, dayOfMonth = day!!, category = category))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CategoryPicker(selected: String, categories: List<Category>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val names = (categories.map { it.name } + FALLBACK_CATEGORY).distinct()

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected, maxLines = 1)
            Icon(Icons.Filled.ExpandMore, contentDescription = "Change category")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            names.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(name)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyRecurring(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Autorenew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp)
        )
        Text(
            "No recurring expenses",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            "When adding an expense like rent or internet, turn on “Repeat every month” and it's added automatically each month.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
