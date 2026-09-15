package com.pocketwise.di

import android.content.Context
import androidx.room.Room
import com.pocketwise.core.data.local.AppDatabase
import com.pocketwise.core.data.local.CategoryDao
import com.pocketwise.core.data.local.ExpenseDao
import com.pocketwise.core.data.local.MIGRATION_4_5
import com.pocketwise.core.data.local.MIGRATION_5_6
import com.pocketwise.core.data.local.RecurringExpenseDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "pocketwise.db")
            // Real migrations only — a missing one should crash loudly, never
            // silently wipe the user's expenses.
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
            .build()

    @Provides
    fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()

    @Provides
    fun provideRecurringExpenseDao(db: AppDatabase): RecurringExpenseDao = db.recurringExpenseDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
}
