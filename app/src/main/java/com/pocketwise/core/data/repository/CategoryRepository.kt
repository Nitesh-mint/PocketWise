package com.pocketwise.core.data.repository

import com.pocketwise.core.data.local.CategoryDao
import com.pocketwise.core.data.local.toDomain
import com.pocketwise.core.data.local.toEntity
import com.pocketwise.core.model.Category
import com.pocketwise.core.model.CategoryIcon
import com.pocketwise.core.model.DefaultCategories
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CategoryRepository @Inject constructor(
    private val dao: CategoryDao
) {
    val categories: Flow<List<Category>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun addCategory(name: String, icon: CategoryIcon, monthlyBudget: Double) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            dao.insert(Category(name = trimmed, icon = icon, monthlyBudget = monthlyBudget.coerceAtLeast(0.0)).toEntity())
        }
    }

    suspend fun updateCategory(category: Category, name: String, icon: CategoryIcon, monthlyBudget: Double) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            dao.update(category.copy(name = trimmed, icon = icon, monthlyBudget = monthlyBudget.coerceAtLeast(0.0)).toEntity())
        }
    }

    suspend fun deleteCategory(category: Category) {
        dao.delete(category.toEntity())
    }

    suspend fun seedDefaultsIfEmpty() {
        if (dao.getCount() == 0) {
            DefaultCategories.forEach { dao.insert(Category(name = it.name, icon = it.icon).toEntity()) }
        }
    }
}
