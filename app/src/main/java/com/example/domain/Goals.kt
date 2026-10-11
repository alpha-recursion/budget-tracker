package com.example.domain

import com.example.data.local.GoalEntity
import com.example.data.local.GoalEntryEntity
import kotlin.math.ceil

/** Whether saving keeps up with the target date. */
enum class GoalPace { ON_TRACK, BEHIND, MISSED }

data class GoalProgress(
    val goal: GoalEntity,
    val saved: Double,
    /** Newest first. */
    val entries: List<GoalEntryEntity>,
    /** Highest milestone reached: 0, 25, 50, 75 or 100. */
    val milestone: Int,
    /** Needed per week to reach the target by its date; null without a date or once missed/complete. */
    val weeklyNeeded: Double?,
    val weeksLeft: Int?,
    /** Null when the goal has no date or is complete. */
    val pace: GoalPace?
) {
    val fraction: Float get() = if (goal.target <= 0.0) 0f else (saved / goal.target).toFloat().coerceIn(0f, 1f)
    val remaining: Double get() = (goal.target - saved).coerceAtLeast(0.0)
    val isComplete: Boolean get() = saved >= goal.target - Goals.EPSILON
}

/** Savings goals: progress, milestones and the weekly plan. Pure Kotlin so it is unit tested. */
object Goals {

    const val EPSILON = 0.005
    val MILESTONES = listOf(25, 50, 75, 100)
    private const val DAY_MS = 86_400_000L

    /** Highest milestone [saved] reaches for [target]. */
    fun milestone(saved: Double, target: Double): Int {
        if (target <= 0.0) return 0
        return MILESTONES.lastOrNull { saved >= target * it / 100.0 - EPSILON } ?: 0
    }

    fun milestoneTitle(milestone: Int): String = when (milestone) {
        25 -> "A quarter of the way!"
        50 -> "Halfway there!"
        75 -> "Almost there!"
        100 -> "Goal reached!"
        else -> ""
    }

    fun progress(goal: GoalEntity, entries: List<GoalEntryEntity>, nowMs: Long): GoalProgress {
        val own = entries.filter { it.goalId == goal.id }
            .sortedWith(compareByDescending<GoalEntryEntity> { it.date }.thenByDescending { it.id })
        val saved = own.sumOf { it.amount }.coerceAtLeast(0.0)
        val complete = saved >= goal.target - EPSILON
        val due = goal.targetDate
        if (due == null || complete) {
            return GoalProgress(goal, saved, own, milestone(saved, goal.target), null, null, null)
        }
        val today = FinancialCalculations.startOfDay(nowMs)
        if (due < today) {
            return GoalProgress(goal, saved, own, milestone(saved, goal.target), null, null, GoalPace.MISSED)
        }
        // The target day counts: a goal due today still has this week.
        val daysLeft = (due - today) / DAY_MS + 1
        val weeksLeft = ceil(daysLeft / 7.0).toInt().coerceAtLeast(1)
        val remaining = goal.target - saved
        // Where a steady saver would be by now, from the day the goal was made to its end.
        val span = (due + DAY_MS - goal.createdAt).coerceAtLeast(1L)
        val elapsed = (nowMs - goal.createdAt).coerceIn(0L, span)
        val expected = goal.target * elapsed / span
        val pace = if (saved + 1.0 >= expected) GoalPace.ON_TRACK else GoalPace.BEHIND
        return GoalProgress(goal, saved, own, milestone(saved, goal.target), remaining / weeksLeft, weeksLeft, pace)
    }

    /** Unfinished goals first (soonest date first, undated after), completed goals last. */
    fun all(goals: List<GoalEntity>, entries: List<GoalEntryEntity>, nowMs: Long): List<GoalProgress> =
        goals.map { progress(it, entries, nowMs) }
            .sortedWith(
                compareBy<GoalProgress> { it.isComplete }
                    .thenBy { it.goal.targetDate ?: Long.MAX_VALUE }
                    .thenBy { it.goal.createdAt }
            )

    /** Money currently reserved across all goals. */
    fun totalSetAside(progress: List<GoalProgress>): Double = progress.sumOf { it.saved }

    /**
     * The milestone to celebrate after money was added, or null. Each milestone is celebrated
     * once ([GoalEntity.celebratedMilestone]); taking money out and adding it back does not repeat it.
     */
    fun milestoneToCelebrate(goal: GoalEntity, savedAfter: Double): Int? {
        val reached = milestone(savedAfter, goal.target)
        return reached.takeIf { it > goal.celebratedMilestone }
    }
}
