package com.pocketwise.feature.expense_core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketwise.core.data.local.UserPreferences
import com.pocketwise.core.data.repository.ExpenseRepository
import com.pocketwise.core.model.Expense
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val MonthLabelFormat = DateTimeFormatter.ofPattern("MMMM yyyy")
private val SystemZone = ZoneId.systemDefault()

private fun YearMonth.startMillis(): Long = atDay(1).atStartOfDay(SystemZone).toInstant().toEpochMilli()
private fun YearMonth.endMillis(): Long = plusMonths(1).atDay(1).atStartOfDay(SystemZone).toInstant().toEpochMilli()

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()
    val monthLabel: StateFlow<String> = _selectedMonth
        .map { it.format(MonthLabelFormat) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), YearMonth.now().format(MonthLabelFormat))
    val hasNextMonth: StateFlow<Boolean> = _selectedMonth
        .map { it < YearMonth.now() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val expenses: StateFlow<List<Expense>> = _selectedMonth
        .flatMapLatest { month -> repository.expensesForRange(month.startMillis(), month.endMillis()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        val next = _selectedMonth.value.plusMonths(1)
        if (next <= YearMonth.now()) _selectedMonth.value = next
    }

    val currencyCode: StateFlow<String> = userPreferences.currencyCode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "USD")

    fun setCurrency(code: String) {
        viewModelScope.launch { userPreferences.setCurrency(code) }
    }

    val monthlyBudget: StateFlow<Double> = userPreferences.monthlyBudget
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun setMonthlyBudget(amount: Double) {
        viewModelScope.launch { userPreferences.setMonthlyBudget(amount) }
    }

    fun addExpense(amount: Double, category: String, description: String, timestamp: Long) {
        viewModelScope.launch {
            repository.addExpense(
                Expense(
                    amount = amount,
                    currency = userPreferences.currencyCode.first(),
                    category = category,
                    description = description,
                    source = "manual",
                    timestamp = timestamp
                )
            )
        }
    }

    suspend fun getExpenseById(id: Long): Expense? = repository.getExpenseById(id)

    fun updateExpense(expense: Expense, amount: Double, category: String, description: String, timestamp: Long) {
        viewModelScope.launch {
            repository.updateExpense(
                expense.copy(amount = amount, category = category, description = description, timestamp = timestamp)
            )
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch { repository.deleteExpense(expense) }
    }
}
