package com.pocketwise.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CurrencyFormatTest {

    @Test
    fun groupsWholeAmountAndKeepsRealCentsOnly() {
        Locale.setDefault(Locale.US)
        assertEquals("6,758" to "", groupedAmountParts(6758.0))
        assertEquals("1,234,567" to ".50", groupedAmountParts(1_234_567.5))
        assertEquals("0" to ".05", groupedAmountParts(0.05))
        // Rounding can carry into the whole part.
        assertEquals("10" to "", groupedAmountParts(9.999))
        assertEquals("-6,508" to "", groupedAmountParts(-6508.0))
    }

    @Test
    fun spacesOnlyLetterSymbols() {
        Locale.setDefault(Locale.US)
        assertEquals("Rs 6,758", formatMoney("Rs", 6758.0))
        assertEquals("₨ 6,758", formatMoney("₨", 6758.0))
        assertEquals("€6,758", formatMoney("€", 6758.0))
        assertEquals("$6,758.50", formatMoney("$", 6758.5))
    }
}
