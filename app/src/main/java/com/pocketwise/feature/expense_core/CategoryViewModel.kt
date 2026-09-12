package com.pocketwise.feature.expense_core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketwise.core.data.repository.CategoryRepository
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
    private val repository: CategoryRepository
) : ViewModel() {

    val categories: StateFlow<List<Category>> = repository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch { repository.seedDefaultsIfEmpty() }
    }

    fun addCategory(name: String, icon: CategoryIcon) {
        viewModelScope.launch { repository.addCategory(name, icon) }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { repository.deleteCategory(category) }
    }
}
