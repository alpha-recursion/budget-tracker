package com.example.domain

import com.example.data.local.GoalEntity
import com.example.data.local.GoalEntryEntity

data class SavingStreak(
    /** Weeks in a row with money added to a goal (a frozen week keeps it, but adds nothing). */
    val current: Int,
    val best: Int,
    /** Freezes available to cover a missed week (0 or 1). */
    val freezes: Int,
    val savedThisWeek: Boolean,
    /** Saving weeks still needed to earn a used freeze back; 0 when one is available. */
    val weeksToFreeze: Int
)

/**
 * Weekly saving streak: a week (Monday to Sunday) counts when any money was added to a goal.
 * The current week only counts once something is added, so the streak never breaks mid-week.
 * One freeze covers a missed week; it is earned back after [REFILL_WEEKS] more saving weeks.
 */
object Streaks {

    const val REFILL_WEEKS = 4
    private const val WEEK_MS = 7 * 86_400_000L

    fun saving(goals: List<GoalEntity>, entries: List<GoalEntryEntity>, nowMs: Long): SavingStreak {
        val createdAt = goals.associate { it.id to it.createdAt }
        // The "already saved" amount entered with a new goal is a starting balance, not saving.
        val savingWeeks = entries
            .filter { it.amount > 0.0 && it.date != createdAt[it.goalId] }
            .map { FinancialCalculations.startOfWeek(it.date) }
            .toSet()
        val thisWeek = FinancialCalculations.startOfWeek(nowMs)
        val savedThisWeek = thisWeek in savingWeeks
        if (savingWeeks.isEmpty()) return SavingStreak(0, 0, 1, false, 0)

        var streak = 0
        var best = 0
        var freezes = 1
        var sinceRefill = 0
        var week = savingWeeks.min()
        // Walk week by week; the current week is only looked at when it already counts.
        val lastWeek = if (savedThisWeek) thisWeek else previousWeek(thisWeek)
        while (week <= lastWeek) {
            if (week in savingWeeks) {
                streak++
                best = maxOf(best, streak)
                if (freezes == 0 && ++sinceRefill >= REFILL_WEEKS) {
                    freezes = 1
                    sinceRefill = 0
                }
            } else if (streak > 0 && freezes > 0) {
                freezes-- // the freeze keeps the streak alive through this week
                sinceRefill = 0
            } else {
                streak = 0
                sinceRefill = 0
            }
            week = nextWeek(week)
        }
        return SavingStreak(
            current = streak,
            best = best,
            freezes = freezes,
            savedThisWeek = savedThisWeek,
            weeksToFreeze = if (freezes > 0) 0 else REFILL_WEEKS - sinceRefill
        )
    }

    // Through startOfWeek so daylight-saving changes cannot shift the boundary.
    private fun nextWeek(weekStart: Long) = FinancialCalculations.startOfWeek(weekStart + WEEK_MS + 12 * 3_600_000L)
    private fun previousWeek(weekStart: Long) = FinancialCalculations.startOfWeek(weekStart - WEEK_MS + 12 * 3_600_000L)
}
