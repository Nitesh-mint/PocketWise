package com.pocketwise.core.util

import java.time.YearMonth
import java.time.ZoneId

/** [startMillis, endMillis) bounds of a month in the device's time zone. */
fun YearMonth.startMillis(): Long = atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun YearMonth.endMillis(): Long = plusMonths(1).startMillis()
