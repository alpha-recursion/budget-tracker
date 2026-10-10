package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CustomCategoryEntity(
    @PrimaryKey
    val name: String,
    val iconKey: String,
    val colorHex: Long,
    val type: String // "EXPENSE" or "INCOME"
)
