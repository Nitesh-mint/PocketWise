package com.pocketwise.feature.expense_core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pocketwise.core.model.Category
import com.pocketwise.core.model.CategoryIcon
import com.pocketwise.core.model.currencySymbolFor
import com.pocketwise.core.ui.icons.PickableCategoryIcons
import com.pocketwise.core.ui.icons.imageVector
import com.pocketwise.core.util.formatAmount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(viewModel: CategoryViewModel = hiltViewModel()) {
    val categories by viewModel.categories.collectAsState()
    val currencyCode by viewModel.currencyCode.collectAsState()
    val currencySymbol = currencySymbolFor(currencyCode)
    var newCategoryName by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf(CategoryIcon.OTHER) }
    var budgetText by remember { mutableStateOf("") }
    var categoryPendingDelete by remember { mutableStateOf<Category?>(null) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }

    fun resetComposer() {
        newCategoryName = ""
        selectedIcon = CategoryIcon.OTHER
        budgetText = ""
        editingCategory = null
    }

    // The app bar is shared across tabs (see MainActivity); this is just the
    // page content below it.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        NewCategoryCard(
            name = newCategoryName,
            onNameChange = { newCategoryName = it },
            selectedIcon = selectedIcon,
            onSelectIcon = { selectedIcon = it },
            budget = budgetText,
            onBudgetChange = { budgetText = it },
            currencySymbol = currencySymbol,
            isEditing = editingCategory != null,
            onSave = {
                val editing = editingCategory
                val budget = budgetText.toDoubleOrNull() ?: 0.0
                if (editing != null) {
                    viewModel.updateCategory(editing, newCategoryName, selectedIcon, budget)
                } else {
                    viewModel.addCategory(newCategoryName, selectedIcon, budget)
                }
                resetComposer()
            },
            onCancel = { resetComposer() },
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(categories, key = { it.id }) { category ->
                CategoryRow(
                    category = category,
                    currencySymbol = currencySymbol,
                    onClick = {
                        editingCategory = category
                        newCategoryName = category.name
                        selectedIcon = category.icon
                        budgetText = if (category.monthlyBudget > 0) formatAmount(category.monthlyBudget) else ""
                    },
                    onDelete = { categoryPendingDelete = category }
                )
            }
        }
    }

    categoryPendingDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { categoryPendingDelete = null },
            title = { Text("Delete category?") },
            text = { Text("\"${category.name}\" will be removed. Expenses already using it keep their category name.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCategory(category)
                    categoryPendingDelete = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryPendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun NewCategoryCard(
    name: String,
    onNameChange: (String) -> Unit,
    selectedIcon: CategoryIcon,
    onSelectIcon: (CategoryIcon) -> Unit,
    budget: String,
    onBudgetChange: (String) -> Unit,
    currencySymbol: String,
    isEditing: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Live preview — the avatar looks exactly like the row it'll
            // become once added, so picking an icon shows its real result.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        selectedIcon.imageVector(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                CategoryNameField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier.padding(start = 12.dp).weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            IconPicker(selected = selectedIcon, onSelect = onSelectIcon)

            Spacer(modifier = Modifier.height(16.dp))

            CategoryBudgetField(value = budget, onValueChange = onBudgetChange, currencySymbol = currencySymbol)

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isEditing) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Cancel")
                    }
                }
                Button(
                    onClick = onSave,
                    enabled = name.isNotBlank(),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(if (isEditing) "Save Changes" else "Add Category")
                }
            }
        }
    }
}

@Composable
private fun CategoryNameField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier,
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text("Category name", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun CategoryBudgetField(value: String, onValueChange: (String) -> Unit, currencySymbol: String) {
    val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Monthly limit  $currencySymbol", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(
            value = value,
            // Only accept something that parses as a non-negative number
            // (or empty = no limit) so the saved value is never garbage.
            onValueChange = { new -> if (new.isEmpty() || (new.toDoubleOrNull() ?: -1.0) >= 0) onValueChange(new) },
            textStyle = style,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.padding(start = 4.dp).weight(1f),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text("None", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun IconPicker(selected: CategoryIcon, onSelect: (CategoryIcon) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(PickableCategoryIcons) { icon ->
            val isSelected = icon == selected
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
                    .clickable { onSelect(icon) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon.imageVector(),
                    contentDescription = icon.name,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(category: Category, currencySymbol: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        category.icon.imageVector(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(category.name, style = MaterialTheme.typography.bodyLarge)
                    if (category.monthlyBudget > 0) {
                        Text(
                            "Limit $currencySymbol${formatAmount(category.monthlyBudget)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Close, contentDescription = "Delete ${category.name}")
            }
        }
    }
}
