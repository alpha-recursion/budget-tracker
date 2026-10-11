package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One movement of money between the owner and another person: lent, got back, borrowed or
 * paid back (see DebtKind). Kept apart from transactions so lending never counts as spending
 * or income.
 */
@Entity(tableName = "debts")
data class DebtEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** Name as the owner typed it; the latest entry's spelling is shown. */
    val person: String,
    /** Lowercase, single-spaced name that groups a person's entries (see Lending.personKey). */
    val personKey: String,
    val kind: String, // DebtKind name
    val amount: Double,
    val date: Long,
    /** When the money should come back (lent) or be returned (borrowed); null = no due date. */
    val dueDate: Long? = null,
    val note: String = "",
    /** Dedupe key of the bank SMS this came from, if any. */
    val smsKey: String? = null
)
