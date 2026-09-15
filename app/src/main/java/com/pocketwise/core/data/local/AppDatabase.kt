package com.pocketwise.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ExpenseEntity::class, CategoryEntity::class, RecurringExpenseEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
    abstract fun recurringExpenseDao(): RecurringExpenseDao
}

// Must match RecurringExpenseEntity exactly (types, nullability, autoincrement key).
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS recurring_expenses (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "amount REAL NOT NULL, " +
                "description TEXT NOT NULL, " +
                "category TEXT NOT NULL, " +
                "dayOfMonth INTEGER NOT NULL, " +
                "lastPostedMonth TEXT NOT NULL)"
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // One-time cleanup of the temporary sample rows used for visual review.
        db.execSQL("DELETE FROM expenses WHERE source = 'dummy'")
        db.execSQL("ALTER TABLE categories ADD COLUMN monthlyBudget REAL NOT NULL DEFAULT 0")
    }
}
