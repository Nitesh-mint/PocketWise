package com.pocketwise.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pocketwise.core.model.Expense

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val currency: String,
    val category: String,
    val description: String,
    val source: String,
    val timestamp: Long
)

fun ExpenseEntity.toDomain() = Expense(id, amount, currency, category, description, source, timestamp)

fun Expense.toEntity() = ExpenseEntity(id, amount, currency, category, description, source, timestamp)
