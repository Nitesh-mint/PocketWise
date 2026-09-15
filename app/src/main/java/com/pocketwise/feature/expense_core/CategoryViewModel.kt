package com.pocketwise.feature.expense_core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketwise.core.data.local.UserPreferences
import com.pocketwise.core.data.repository.CategoryRepository
import com.pocketwise.core.data.repository.ExpenseRepository
import com.pocketwise.core.model.Category
import com.pocketwise.core.model.CategoryIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val repository: CategoryRepository,
    private val expenseRepository: ExpenseRepository,
    userPreferences: UserPreferences
) : ViewModel() {

    val categories: StateFlow<List<Category>> = repository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currencyCode: StateFlow<String> = userPreferences.currencyCode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "USD")

    init {
        viewModelScope.launch { repository.seedDefaultsIfEmpty() }
    }

    fun addCategory(name: String, icon: CategoryIcon, monthlyBudget: Double) {
        viewModelScope.launch { repository.addCategory(name, icon, monthlyBudget) }
    }

    fun updateCategory(category: Category, name: String, icon: CategoryIcon, monthlyBudget: Double) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return@launch
            repository.updateCategory(category, trimmed, icon, monthlyBudget)
            if (trimmed != category.name) {
                expenseRepository.renameCategoryInExpenses(category.name, trimmed)
            }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { repository.deleteCategory(category) }
    }
}
