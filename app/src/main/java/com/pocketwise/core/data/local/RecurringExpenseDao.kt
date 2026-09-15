package com.pocketwise.core.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringExpenseDao {
    @Insert
    suspend fun insert(rule: RecurringExpenseEntity): Long

    @Update
    suspend fun update(rule: RecurringExpenseEntity)

    @Delete
    suspend fun delete(rule: RecurringExpenseEntity)

    @Query("SELECT * FROM recurring_expenses ORDER BY dayOfMonth, description")
    fun observeAll(): Flow<List<RecurringExpenseEntity>>

    @Query("SELECT * FROM recurring_expenses")
    suspend fun getAll(): List<RecurringExpenseEntity>
}
