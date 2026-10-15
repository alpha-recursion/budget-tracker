package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A subscription or bill that repeats. Reminders only: it never creates transactions itself. */
@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val amount: Double,
    val category: String,
    val frequency: String, // RecurringFrequency name
    /** Local midnight of the first due date; later due dates are worked out from it. */
    val startDate: Long,
    val active: Boolean = true,
    val createdAt: Long
)
