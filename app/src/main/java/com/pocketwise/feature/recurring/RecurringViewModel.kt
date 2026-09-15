package com.pocketwise.feature.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketwise.core.data.local.UserPreferences
import com.pocketwise.core.data.repository.CategoryRepository
import com.pocketwise.core.data.repository.RecurringRepository
import com.pocketwise.core.model.Category
import com.pocketwise.core.model.RecurringExpense
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecurringViewModel @Inject constructor(
    private val repository: RecurringRepository,
    categoryRepository: CategoryRepository,
    userPreferences: UserPreferences
) : ViewModel() {

    val rules: StateFlow<List<RecurringExpense>> = repository.rules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = categoryRepository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currencyCode: StateFlow<String> = userPreferences.currencyCode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "USD")

    fun update(rule: RecurringExpense) {
        viewModelScope.launch { repository.update(rule) }
    }

    fun stop(rule: RecurringExpense) {
        viewModelScope.launch { repository.stop(rule) }
    }
}
