package com.pocketwise.core.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    // Keeps the ids given — used to restore deleted rows on Undo.
    @Insert
    suspend fun insertAll(expenses: List<ExpenseEntity>)

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expenses: List<ExpenseEntity>)

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): ExpenseEntity?

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE timestamp >= :startMillis AND timestamp < :endMillis ORDER BY timestamp DESC")
    fun observeForRange(startMillis: Long, endMillis: Long): Flow<List<ExpenseEntity>>

    @Query("UPDATE expenses SET category = :newName WHERE category = :oldName")
    suspend fun renameCategory(oldName: String, newName: String)

    @Query("UPDATE expenses SET currency = :code")
    suspend fun setCurrencyForAll(code: String)
}
