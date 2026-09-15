package com.pocketwise.core.data.repository

import androidx.room.withTransaction
import com.pocketwise.core.data.local.AppDatabase
import com.pocketwise.core.data.local.ExpenseDao
import com.pocketwise.core.data.local.ExpenseEntity
import com.pocketwise.core.data.local.RecurringExpenseDao
import com.pocketwise.core.data.local.RecurringExpenseEntity
import com.pocketwise.core.data.local.UserPreferences
import com.pocketwise.core.data.local.toDomain
import com.pocketwise.core.data.local.toEntity
import com.pocketwise.core.model.RecurringExpense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

const val SOURCE_RECURRING = "recurring"

@Singleton
class RecurringRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: RecurringExpenseDao,
    private val expenseDao: ExpenseDao,
    private val expenseRepository: ExpenseRepository,
    private val userPreferences: UserPreferences
) {
    val rules: Flow<List<RecurringExpense>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    // App start and the widget can both trigger posting at the same moment.
    private val postingLock = Mutex()

    /** Adds every expense a rule owes up to [today] — including months missed while the app wasn't opened. */
    suspend fun postDue(today: LocalDate = LocalDate.now()) {
        val posted = postingLock.withLock {
            val currency = userPreferences.currencyCode.first()
            var count = 0
            dao.getAll().forEach { rule ->
                dueDates(rule.dayOfMonth, YearMonth.parse(rule.lastPostedMonth), today).forEach { date ->
                    // Expense + advanced month in one transaction: a crash can't
                    // leave a month posted twice or skipped.
                    db.withTransaction {
                        expenseDao.insert(
                            ExpenseEntity(
                                amount = rule.amount,
                                currency = currency,
                                category = rule.category,
                                description = rule.description,
                                source = SOURCE_RECURRING,
                                timestamp = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            )
                        )
                        dao.update(rule.copy(lastPostedMonth = YearMonth.from(date).toString()))
                    }
                    count++
                }
            }
            count
        }
        // Refresh once, outside the lock and the transaction — the widget itself
        // calls postDue(), so refreshing inside could wait on ourselves.
        if (posted > 0) expenseRepository.refreshWidget()
    }

    /**
     * Starts repeating an expense the user just saved on [firstDate]. That month
     * is already covered by the saved expense, so it's recorded as posted; if the
     * date was in an earlier month, the months since are filled in right away.
     */
    suspend fun create(amount: Double, description: String, category: String, firstDate: LocalDate) {
        dao.insert(
            RecurringExpenseEntity(
                amount = amount,
                description = description,
                category = category,
                dayOfMonth = firstDate.dayOfMonth,
                lastPostedMonth = YearMonth.from(firstDate).toString()
            )
        )
        postDue()
    }

    /** Changes apply to future months only; expenses already added stay as they are. */
    suspend fun update(rule: RecurringExpense) = dao.update(rule.toEntity())

    /** Stops future posts; expenses already added stay. */
    suspend fun stop(rule: RecurringExpense) = dao.delete(rule.toEntity())
}

/**
 * Dates a monthly rule still owes: each month after [lastPostedMonth] through
 * [today]'s month, on [dayOfMonth] clamped to that month's length. The current
 * month counts only once its day has arrived.
 */
internal fun dueDates(dayOfMonth: Int, lastPostedMonth: YearMonth, today: LocalDate): List<LocalDate> {
    val dates = mutableListOf<LocalDate>()
    val currentMonth = YearMonth.from(today)
    var month = lastPostedMonth.plusMonths(1)
    while (month <= currentMonth) {
        val date = month.atDay(minOf(dayOfMonth, month.lengthOfMonth()))
        if (date.isAfter(today)) break
        dates += date
        month = month.plusMonths(1)
    }
    return dates
}

/** Next date a rule will post, for display. */
fun RecurringExpense.nextDate(): LocalDate {
    val month = lastPostedMonth.plusMonths(1)
    return month.atDay(minOf(dayOfMonth, month.lengthOfMonth()))
}
