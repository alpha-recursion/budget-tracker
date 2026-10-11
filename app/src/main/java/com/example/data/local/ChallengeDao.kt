package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeDao {
    @Query("SELECT * FROM challenges ORDER BY month DESC, id ASC")
    fun getAll(): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges")
    suspend fun getAllOnce(): List<ChallengeEntity>

    @Insert
    suspend fun insert(challenge: ChallengeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(challenges: List<ChallengeEntity>)

    @Query("DELETE FROM challenges WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM challenges")
    suspend fun deleteAll()
}
