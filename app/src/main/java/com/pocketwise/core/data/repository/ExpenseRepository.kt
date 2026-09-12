package com.pocketwise.core.data.repository

import com.pocketwise.core.data.local.ExpenseDao
import com.pocketwise.core.data.local.toDomain
import com.pocketwise.core.data.local.toEntity
import com.pocketwise.core.model.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Single source of truth for expense data. Entry-point-agnostic: callers
 * (manual form, voice, widget, import, ...) all funnel through here.
 */
class ExpenseRepository @Inject constructor(
    private val dao: ExpenseDao
) {
    val expenses: Flow<List<Expense>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun expensesForRange(startMillis: Long, endMillis: Long): Flow<List<Expense>> =
        dao.observeForRange(startMillis, endMillis).map { list -> list.map { it.toDomain() } }

    suspend fun getExpenseById(id: Long): Expense? = dao.getById(id)?.toDomain()

    suspend fun addExpense(expense: Expense): Long = dao.insert(expense.toEntity())

    suspend fun updateExpense(expense: Expense) = dao.update(expense.toEntity())

    suspend fun deleteExpense(expense: Expense) = dao.delete(expense.toEntity())
}
