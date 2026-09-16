package com.pocketwise.core.data.local

// Increment this when a schema change makes old backups incompatible.
// Must match AppDatabase.version (currently 6).
const val BACKUP_DB_VERSION = 6

data class BackupSnapshot(
    val dbVersion: Int,
    val expenses: List<ExpenseEntity>,
    val categories: List<CategoryEntity>,
    val recurringExpenses: List<RecurringExpenseEntity>
)
