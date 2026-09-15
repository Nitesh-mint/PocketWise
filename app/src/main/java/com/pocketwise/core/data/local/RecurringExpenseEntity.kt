package com.pocketwise.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pocketwise.core.model.RecurringExpense
import java.time.YearMonth

// Columns must match MIGRATION_5_6's CREATE TABLE exactly, or Room's schema check fails at startup.
@Entity(tableName = "recurring_expenses")
data class RecurringExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val description: String,
    val category: String,
    val dayOfMonth: Int,
    /** ISO "yyyy-MM". */
    val lastPostedMonth: String
)

fun RecurringExpenseEntity.toDomain() = RecurringExpense(
    id = id,
    amount = amount,
    description = description,
    category = category,
    dayOfMonth = dayOfMonth,
    lastPostedMonth = YearMonth.parse(lastPostedMonth)
)

fun RecurringExpense.toEntity() = RecurringExpenseEntity(id, amount, description, category, dayOfMonth, lastPostedMonth.toString())
