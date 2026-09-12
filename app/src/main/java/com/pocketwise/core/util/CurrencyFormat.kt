package com.pocketwise.core.util

/** "150" for whole amounts, "12.34" when there are actual cents — no
 * pointless trailing ".00". */
fun formatAmount(amount: Double): String {
    val rounded = "%.2f".format(amount)
    return if (rounded.endsWith(".00")) rounded.dropLast(3) else rounded
}
