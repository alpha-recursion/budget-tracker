package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsTemplateDao {
    // Newest first: a format taught later takes precedence over an older one.
    @Query("SELECT * FROM sms_templates ORDER BY createdAt DESC, id DESC")
    fun getAll(): Flow<List<SmsTemplateEntity>>

    @Query("SELECT * FROM sms_templates ORDER BY createdAt DESC, id DESC")
    suspend fun getAllOnce(): List<SmsTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: SmsTemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<SmsTemplateEntity>)

    @Query("DELETE FROM sms_templates WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sms_templates")
    suspend fun deleteAll()
}
