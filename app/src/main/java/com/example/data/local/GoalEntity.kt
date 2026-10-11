package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Something the owner is saving for; the money set aside lives in GoalEntryEntity rows. */
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val target: Double,
    val iconKey: String,
    val colorHex: Long,
    /** Optional "reach it by" day (local midnight). */
    val targetDate: Long? = null,
    val createdAt: Long,
    /** Highest milestone (0, 25, 50, 75, 100) already celebrated, so each is celebrated once. */
    val celebratedMilestone: Int = 0,
    /** When the goal first reached its target. */
    val completedAt: Long? = null
)

/** Money put into (positive [amount]) or taken out of (negative) a goal. Not spending. */
@Entity(tableName = "goal_entries")
data class GoalEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val goalId: Long,
    val amount: Double,
    val date: Long,
    val note: String = ""
)
