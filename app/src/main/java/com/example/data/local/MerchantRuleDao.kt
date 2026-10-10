package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules")
    suspend fun getAllOnce(): List<MerchantRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<MerchantRuleEntity>)

    @Query("UPDATE merchant_rules SET category = :to WHERE category = :from")
    suspend fun reassignCategory(from: String, to: String)

    @Query("DELETE FROM merchant_rules")
    suspend fun deleteAll()
}
