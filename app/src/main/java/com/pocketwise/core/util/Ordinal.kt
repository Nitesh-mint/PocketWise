package com.pocketwise.core.util

/** 1 → "1st", 2 → "2nd", 12 → "12th", 22 → "22nd". */
fun ordinal(day: Int): String {
    val suffix = if (day % 100 in 11..13) "th" else when (day % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$day$suffix"
}
