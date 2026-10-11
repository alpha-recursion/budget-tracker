package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Records an SMS that has already been handled, so scanning the same period again never
 * imports it twice. Only decisions are stored, not the message text.
 */
@Entity(tableName = "sms_imports")
data class SmsImportEntity(
    @PrimaryKey
    val dedupeKey: String,
    val status: String, // SmsImportStatus name
    val transactionId: Long?, // set when status is IMPORTED
    val receivedAt: Long
)

enum class SmsImportStatus {
    /** Became a transaction. */
    IMPORTED,

    /** Money moved between the owner's own accounts; excluded from spending and income. */
    SELF_TRANSFER,

    /** Money lent, borrowed or repaid; recorded in `debts`, not as a transaction. */
    LENDING
}
