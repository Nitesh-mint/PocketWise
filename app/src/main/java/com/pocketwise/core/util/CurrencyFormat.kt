package com.pocketwise.core.util

import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.roundToLong

/** "150" for whole amounts, "12.34" when there are actual cents — no
 * pointless trailing ".00". */
fun formatAmount(amount: Double): String {
    val rounded = "%.2f".format(amount)
    return if (rounded.endsWith(".00")) rounded.dropLast(3) else rounded
}

/**
 * Display-only "6,758" / "6,758.50", split into whole and cents so the cents
 * can be styled smaller. Never pre-fill an input with it: separators don't parse.
 */
fun groupedAmountParts(amount: Double): Pair<String, String> {
    val cents = abs((amount * 100).roundToLong())
    val sign = if (amount < 0 && cents > 0) "-" else ""
    val whole = NumberFormat.getIntegerInstance().format(cents / 100)
    val fraction = cents % 100
    return sign + whole to if (fraction == 0L) "" else DecimalFormatSymbols.getInstance().decimalSeparator + "%02d".format(fraction)
}

/**
 * Symbols that read as letters get a non-breaking space so they don't run into
 * the digits; "$" doesn't. "₨" is one glyph drawn as "Rs", so it counts as letters.
 */
fun symbolPrefix(symbol: String): String {
    val last = symbol.lastOrNull() ?: return symbol
    return if (last.isLetter() || last == '₨') "$symbol " else symbol
}

/** "Rs 6,758.50" / "$6,758" — display-only, see [groupedAmountParts]. */
fun formatMoney(symbol: String, amount: Double): String =
    groupedAmountParts(amount).let { (whole, fraction) -> symbolPrefix(symbol) + whole + fraction }
