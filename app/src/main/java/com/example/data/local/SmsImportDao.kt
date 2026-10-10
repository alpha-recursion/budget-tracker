package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SmsImportDao {
    @Query("SELECT dedupeKey FROM sms_imports")
    suspend fun getAllKeys(): List<String>

    @Query("SELECT * FROM sms_imports")
    suspend fun getAllOnce(): List<SmsImportEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<SmsImportEntity>)

    @Query("DELETE FROM sms_imports")
    suspend fun deleteAll()
}
