package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A monthly challenge the owner picked; its progress is worked out from their data. */
@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** Year * 100 + month (1-12), e.g. 202610. */
    val month: Int,
    val type: String, // ChallengeType name
    /** For a category limit. */
    val category: String? = null,
    val target: Double,
    val createdAt: Long
)
