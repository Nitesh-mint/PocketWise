package com.pocketwise.core.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class VoiceSanitizeTest {
    private val today = LocalDate.of(2026, 9, 13)
    private val categories = listOf("Food & Dining", "Transport", "Other")

    @Test
    fun validExpensePassesThrough() {
        val result = sanitize(listOf(RawExpense(250.0, "Coffee", "Food & Dining", "2026-09-12")), categories, today)
        assertEquals(listOf(ParsedExpense(250.0, "Coffee", "Food & Dining", LocalDate.of(2026, 9, 12))), result)
    }

    @Test
    fun dropsMissingZeroNegativeAndNonFiniteAmounts() {
        val raw = listOf(
            RawExpense(null, "A", "Transport", "2026-09-13"),
            RawExpense(0.0, "B", "Transport", "2026-09-13"),
            RawExpense(-5.0, "C", "Transport", "2026-09-13"),
            RawExpense(Double.POSITIVE_INFINITY, "D", "Transport", "2026-09-13"),
        )
        assertEquals(emptyList<ParsedExpense>(), sanitize(raw, categories, today))
    }

    @Test
    fun unknownCategoryFallsBackToOther() {
        val result = sanitize(listOf(RawExpense(10.0, "Gift", "Presents", "2026-09-13")), categories, today)
        assertEquals(FALLBACK_CATEGORY, result.single().category)
    }

    @Test
    fun futureOrGarbledDateBecomesToday() {
        val raw = listOf(
            RawExpense(10.0, "Tomorrow thing", "Transport", "2026-09-14"),
            RawExpense(10.0, "Garbled", "Transport", "next week-ish"),
        )
        assertEquals(listOf(today, today), sanitize(raw, categories, today).map { it.date })
    }

    @Test
    fun blankDescriptionUsesCategoryName() {
        val result = sanitize(listOf(RawExpense(12.0, "  ", "Transport", "2026-09-13")), categories, today)
        assertEquals("Transport", result.single().description)
    }
}
