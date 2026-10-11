package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY createdAt ASC, id ASC")
    fun getGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goal_entries ORDER BY date DESC, id DESC")
    fun getEntries(): Flow<List<GoalEntryEntity>>

    @Query("SELECT * FROM goals")
    suspend fun getGoalsOnce(): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getGoal(id: Long): GoalEntity?

    @Query("SELECT * FROM goal_entries")
    suspend fun getEntriesOnce(): List<GoalEntryEntity>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM goal_entries WHERE goalId = :goalId")
    suspend fun savedFor(goalId: Long): Double

    @Insert
    suspend fun insertGoal(goal: GoalEntity): Long

    @Update
    suspend fun updateGoal(goal: GoalEntity)

    @Insert
    suspend fun insertEntry(entry: GoalEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoals(goals: List<GoalEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<GoalEntryEntity>)

    @Query("DELETE FROM goal_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("DELETE FROM goal_entries WHERE goalId = :goalId")
    suspend fun deleteEntriesFor(goalId: Long)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Query("DELETE FROM goal_entries")
    suspend fun deleteAllEntries()

    @Query("DELETE FROM goals")
    suspend fun deleteAllGoals()
}
