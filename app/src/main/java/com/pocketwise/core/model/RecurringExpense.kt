package com.pocketwise.core.model

import java.time.YearMonth

/**
 * A monthly rule (rent, internet, a subscription). [lastPostedMonth] is the
 * latest month already covered by an expense, so posting never repeats a month.
 * A [dayOfMonth] past a month's end (e.g. 31 in February) uses its last day.
 */
data class RecurringExpense(
    val id: Long = 0,
    val amount: Double,
    val description: String,
    val category: String,
    val dayOfMonth: Int,
    val lastPostedMonth: YearMonth
)
