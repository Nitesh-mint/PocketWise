package com.pocketwise.core.model

data class Expense(
    val id: Long = 0,
    val amount: Double,
    val currency: String,
    val category: String,
    val description: String,
    val source: String,
    val timestamp: Long
)
