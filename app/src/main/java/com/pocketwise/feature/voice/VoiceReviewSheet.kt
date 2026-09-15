package com.pocketwise.feature.voice

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pocketwise.core.data.voice.FALLBACK_CATEGORY
import com.pocketwise.core.data.voice.ParsedExpense
import com.pocketwise.core.model.Category
import com.pocketwise.core.model.CategoryIcon
import com.pocketwise.core.ui.icons.imageVector
import com.pocketwise.core.util.formatAmount
import com.pocketwise.feature.voice.VoiceExpenseViewModel.UiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DayFormat = DateTimeFormatter.ofPattern("EEE, MMM d")

/** Same fields as [ParsedExpense], but with the amount as editable text. */
private data class EditableExpense(val amountText: String, val description: String, val category: String, val date: LocalDate) {
    val amount: Double? get() = amountText.toDoubleOrNull()?.takeIf { it > 0 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceReviewSheet(
    state: UiState,
    categories: List<Category>,
    currencySymbol: String,
    onSave: (List<ParsedExpense>) -> Unit,
    onStopListening: () -> Unit,
    onRetry: () -> Unit,
    onEnterManually: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            when (state) {
                is UiState.Listening -> ListeningContent(state, onStopListening)
                UiState.Parsing -> ParsingContent()
                is UiState.Review -> ReviewContent(state, categories, currencySymbol, onSave)
                is UiState.Error -> ErrorContent(state, onRetry, onEnterManually)
                UiState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun Transcript(text: String) {
    if (text.isBlank()) return
    Text(
        "“$text”",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun ListeningContent(state: UiState.Listening, onStop: () -> Unit) {
    // The halo follows the live mic level, so it's obvious the app is hearing you.
    val haloScale by animateFloatAsState(targetValue = 1f + state.level * 0.6f, label = "micLevel")
    val seconds = state.elapsedMs / 1000

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        scaleX = haloScale
                        scaleY = haloScale
                    }
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
            }
        }
        Text(
            "Listening…",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            "Say it in any language, like “coffee 250” or “sabji dui saya pachas”",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            "%d:%02d".format(seconds / 60, seconds % 60),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(
            onClick = onStop,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .height(52.dp)
        ) {
            Text("Done", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ParsingContent() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            "Understanding…",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
    Box(modifier = Modifier.height(32.dp))
}

@Composable
private fun ErrorContent(state: UiState.Error, onRetry: () -> Unit, onEnterManually: () -> Unit) {
    Icon(
        Icons.Filled.MicOff,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(32.dp)
    )
    Text(
        state.message,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp)
    )
    state.transcript?.let { Transcript(it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = onEnterManually, modifier = Modifier.weight(1f)) { Text("Enter manually") }
        Button(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("Try again") }
    }
}

@Composable
private fun ReviewContent(
    state: UiState.Review,
    categories: List<Category>,
    currencySymbol: String,
    onSave: (List<ParsedExpense>) -> Unit
) {
    var items by remember(state) {
        mutableStateOf(state.items.map { EditableExpense(formatAmount(it.amount), it.description, it.category, it.date) })
    }
    val canSave = items.isNotEmpty() && items.all { it.amount != null && it.description.isNotBlank() }

    Text(
        if (items.size == 1) "Check this expense" else "Check these ${items.size} expenses",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Transcript(state.transcript)

    items.forEachIndexed { index, item ->
        ReviewRow(
            item = item,
            categories = categories,
            currencySymbol = currencySymbol,
            onChange = { updated -> items = items.toMutableList().also { it[index] = updated } },
            onRemove = { items = items.filterIndexed { i, _ -> i != index } },
            modifier = Modifier.padding(top = 12.dp)
        )
    }

    if (items.isEmpty()) {
        Text(
            "Nothing left to save.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
    }

    Button(
        onClick = {
            onSave(items.map { ParsedExpense(it.amount!!, it.description.trim(), it.category, it.date) })
        },
        enabled = canSave,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .height(52.dp)
    ) {
        Text(if (items.size <= 1) "Save expense" else "Save ${items.size} expenses", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ReviewRow(
    item: EditableExpense,
    categories: List<Category>,
    currencySymbol: String,
    onChange: (EditableExpense) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = categories.find { it.name == item.category }?.icon ?: CategoryIcon.OTHER

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    PlainField(
                        value = item.description,
                        onValueChange = { onChange(item.copy(description = it)) },
                        placeholder = "What was it?",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        dayLabel(item.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove this expense", modifier = Modifier.size(18.dp))
                }
            }

            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    currencySymbol,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PlainField(
                    value = item.amountText,
                    // Only accept text that is (or is on its way to being) a number.
                    onValueChange = { text -> if (text.isEmpty() || text.toDoubleOrNull() != null) onChange(item.copy(amountText = text)) },
                    placeholder = "0",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    keyboardType = KeyboardType.Decimal,
                    isError = item.amount == null,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .weight(1f)
                )
                CategoryPicker(
                    selected = item.category,
                    categories = categories,
                    onSelect = { onChange(item.copy(category = it)) }
                )
            }
        }
    }
}

@Composable
private fun CategoryPicker(selected: String, categories: List<Category>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val names = (categories.map { it.name } + FALLBACK_CATEGORY).distinct()

    Box {
        TextButton(onClick = { expanded = true }) {
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
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false
) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style.copy(color = color),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
                inner()
            }
        }
    )
}

private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DayFormat)
    }
}
