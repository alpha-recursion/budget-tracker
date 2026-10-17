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

    @Query("SELECT * FROM sms_imports WHERE dedupeKey = :dedupeKey")
    suspend fun getByKey(dedupeKey: String): SmsImportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<SmsImportEntity>)

    /** The transaction an SMS became was moved to money lent; keep the SMS marked as handled. */
    @Query("UPDATE sms_imports SET status = 'LENDING', transactionId = NULL WHERE transactionId = :transactionId")
    suspend fun markMovedToLending(transactionId: Long)

    @Query("DELETE FROM sms_imports")
    suspend fun deleteAll()
}
