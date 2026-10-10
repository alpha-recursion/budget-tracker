package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A message format the owner taught (see domain.sms.SmsTemplate). */
@Entity(tableName = "sms_templates")
data class SmsTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val bank: String, // SmsBank name
    val type: String, // TransactionType name
    val pattern: String,
    val amountGroup: Int,
    val payeeGroup: Int?,
    val dateGroup: Int?,
    val dateFormat: String?, // SmsDateFormat name
    val refGroup: Int?,
    val sample: String,
    val createdAt: Long
)
