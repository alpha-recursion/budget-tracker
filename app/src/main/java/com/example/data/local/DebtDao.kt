package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY date DESC, id DESC")
    fun getAll(): Flow<List<DebtEntryEntity>>

    @Query("SELECT * FROM debts")
    suspend fun getAllOnce(): List<DebtEntryEntity>

    @Insert
    suspend fun insert(entry: DebtEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DebtEntryEntity>)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM debts WHERE personKey = :personKey")
    suspend fun deletePerson(personKey: String)

    @Query("DELETE FROM debts")
    suspend fun deleteAll()
}
