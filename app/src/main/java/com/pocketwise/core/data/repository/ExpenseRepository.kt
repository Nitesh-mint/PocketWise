package com.pocketwise.core.data.repository

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.pocketwise.core.data.local.ExpenseDao
import com.pocketwise.core.data.local.toDomain
import com.pocketwise.core.data.local.toEntity
import com.pocketwise.core.model.Expense
import com.pocketwise.feature.widget.PocketWiseWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Single source of truth for expense data. Entry-point-agnostic: callers
 * (manual form, voice, widget, import, ...) all funnel through here.
 */
class ExpenseRepository @Inject constructor(
    private val dao: ExpenseDao,
    @ApplicationContext private val context: Context
) {
    val expenses: Flow<List<Expense>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun expensesForRange(startMillis: Long, endMillis: Long): Flow<List<Expense>> =
        dao.observeForRange(startMillis, endMillis).map { list -> list.map { it.toDomain() } }

    suspend fun getExpenseById(id: Long): Expense? = dao.getById(id)?.toDomain()

    suspend fun addExpense(expense: Expense): Long = dao.insert(expense.toEntity()).also { refreshWidget() }

    suspend fun updateExpense(expense: Expense) {
        dao.update(expense.toEntity())
        refreshWidget()
    }

    suspend fun deleteExpenses(expenses: List<Expense>) {
        dao.delete(expenses.map { it.toEntity() })
        refreshWidget()
    }

    /** Undo for [deleteExpenses]: same rows, same ids. */
    suspend fun restoreExpenses(expenses: List<Expense>) {
        dao.insertAll(expenses.map { it.toEntity() })
        refreshWidget()
    }

    // Categories are stored as a plain name string on each expense (no FK),
    // so renaming a category must also update every expense already using
    // the old name — otherwise their history silently disconnects from it.
    suspend fun renameCategoryInExpenses(oldName: String, newName: String) = dao.renameCategory(oldName, newName)

    // One app-wide currency: switching relabels everything (no conversion),
    // so totals never mix amounts from different currencies.
    suspend fun setCurrencyForAll(code: String) {
        dao.setCurrencyForAll(code)
        refreshWidget()
    }

    // Every write funnels through here, so this is the one place the
    // home-screen widget needs a refresh. Public for batch writers
    // (RecurringRepository) that insert via the DAO and refresh once.
    suspend fun refreshWidget() = PocketWiseWidget().updateAll(context)
}
