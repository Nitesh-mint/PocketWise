package com.pocketwise.feature.expense_core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketwise.core.data.local.UserPreferences
import com.pocketwise.core.data.repository.ExpenseRepository
import com.pocketwise.core.data.repository.RecurringRepository
import com.pocketwise.core.model.Expense
import com.pocketwise.core.util.endMillis
import com.pocketwise.core.util.startMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val MonthLabelFormat = DateTimeFormatter.ofPattern("MMMM yyyy")
private val FullMonthFormat = DateTimeFormatter.ofPattern("MMMM")
private val ShortMonthFormat = DateTimeFormatter.ofPattern("MMM")

/** Previous-period total plus a human label for it ("Aug 1–13" or "August"). */
data class PeriodComparison(val previousTotal: Double, val label: String)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    private val userPreferences: UserPreferences,
    private val recurringRepository: RecurringRepository
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()
    val monthLabel: StateFlow<String> = _selectedMonth
        .map { it.format(MonthLabelFormat) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), YearMonth.now().format(MonthLabelFormat))

    val expenses: StateFlow<List<Expense>> = _selectedMonth
        .flatMapLatest { month -> repository.expensesForRange(month.startMillis(), month.endMillis()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Like-for-like: while a month is still in progress, compare it with the
    // same days of the month before (Sep 1–13 vs Aug 1–13). Against all of
    // August, every month would look cheap until its very last day.
    val comparison: StateFlow<PeriodComparison?> = _selectedMonth
        .flatMapLatest { month ->
            val previous = month.minusMonths(1)
            val today = LocalDate.now()
            val (endMillis, label) = if (month == YearMonth.from(today)) {
                val lastDay = minOf(today.dayOfMonth, previous.lengthOfMonth())
                previous.atDay(lastDay).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() to
                    "${previous.format(ShortMonthFormat)} 1–$lastDay"
            } else {
                previous.endMillis() to previous.format(FullMonthFormat)
            }
            repository.expensesForRange(previous.startMillis(), endMillis)
                .map { list -> PeriodComparison(list.sumOf { it.amount }, label) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectMonth(month: YearMonth) {
        // Never a future month — nothing can be logged there yet.
        _selectedMonth.value = minOf(month, YearMonth.now())
    }

    val currencyCode: StateFlow<String> = userPreferences.currencyCode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "USD")

    fun setCurrency(code: String) {
        viewModelScope.launch {
            userPreferences.setCurrency(code)
            repository.setCurrencyForAll(code)
        }
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

    /** Repeats an expense just saved on [firstDate] every month from the next month on. */
    fun addRecurring(amount: Double, category: String, description: String, firstDate: LocalDate) {
        viewModelScope.launch { recurringRepository.create(amount, description, category, firstDate) }
    }

    suspend fun getExpenseById(id: Long): Expense? = repository.getExpenseById(id)

    fun updateExpense(expense: Expense, amount: Double, category: String, description: String, timestamp: Long) {
        viewModelScope.launch {
            repository.updateExpense(
                expense.copy(amount = amount, category = category, description = description, timestamp = timestamp)
            )
        }
    }

    // What was just deleted, so the UI can offer Undo.
    private val _deleted = Channel<List<Expense>>(Channel.CONFLATED)
    val deleted: Flow<List<Expense>> = _deleted.receiveAsFlow()

    fun deleteExpenses(expenses: List<Expense>) {
        viewModelScope.launch {
            repository.deleteExpenses(expenses)
            _deleted.send(expenses)
        }
    }

    fun restoreExpenses(expenses: List<Expense>) {
        viewModelScope.launch { repository.restoreExpenses(expenses) }
    }
}
