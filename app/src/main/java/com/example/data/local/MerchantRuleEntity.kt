package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** What the owner chose for a payee during an SMS import, reused on the next scan. */
@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey
    val counterpartyKey: String, // normalised payee name, see SmsImportPlanner.counterpartyKey
    val category: String,
    val isSelfTransfer: Boolean
)
