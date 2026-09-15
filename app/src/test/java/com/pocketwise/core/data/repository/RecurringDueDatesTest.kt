package com.pocketwise.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RecurringDueDatesTest {
    private val today = LocalDate.of(2026, 9, 13)

    @Test
    fun alreadyPostedThisMonthOwesNothing() {
        assertEquals(emptyList<LocalDate>(), dueDates(1, YearMonth.of(2026, 9), today))
    }

    @Test
    fun dayNotYetReachedThisMonthOwesNothing() {
        assertEquals(emptyList<LocalDate>(), dueDates(20, YearMonth.of(2026, 8), today))
    }

    @Test
    fun dayReachedThisMonthOwesOne() {
        assertEquals(listOf(LocalDate.of(2026, 9, 1)), dueDates(1, YearMonth.of(2026, 8), today))
    }

    @Test
    fun backfillsMissedMonths() {
        assertEquals(
            listOf(LocalDate.of(2026, 7, 5), LocalDate.of(2026, 8, 5), LocalDate.of(2026, 9, 5)),
            dueDates(5, YearMonth.of(2026, 6), today)
        )
    }

    @Test
    fun clampsToShortMonthsIncludingLeapYears() {
        assertEquals(
            listOf(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31)),
            dueDates(31, YearMonth.of(2026, 1), LocalDate.of(2026, 3, 31))
        )
        assertEquals(listOf(LocalDate.of(2028, 2, 29)), dueDates(30, YearMonth.of(2028, 1), LocalDate.of(2028, 2, 29)))
    }

    @Test
    fun crossesYearBoundary() {
        assertEquals(listOf(LocalDate.of(2027, 1, 15)), dueDates(15, YearMonth.of(2026, 12), LocalDate.of(2027, 1, 20)))
    }
}
