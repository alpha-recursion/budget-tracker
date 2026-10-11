package com.example.domain

import com.example.data.local.GoalEntity
import com.example.data.local.GoalEntryEntity
import com.example.data.local.GoalMilestoneEntity
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
    val pace: GoalPace?,
    /** The owner's named checkpoints for this goal, smallest first. */
    val checkpoints: List<GoalMilestoneEntity> = emptyList(),
    /** When the goal will be reached at the recent saving pace; null without enough history. */
    val forecastDate: Long? = null
) {
    /** "I bought it" was used: the money was spent on what the goal was for. */
    val isPurchased: Boolean get() = goal.purchasedAt != null
    val fraction: Float
        get() = when {
            isPurchased -> 1f
            goal.target <= 0.0 -> 0f
            else -> (saved / goal.target).toFloat().coerceIn(0f, 1f)
        }
    val remaining: Double get() = if (isPurchased) 0.0 else (goal.target - saved).coerceAtLeast(0.0)
    val isComplete: Boolean get() = saved >= goal.target - Goals.EPSILON
    /** Shown on the Achieved shelf: reached its target, or bought. */
    val isAchieved: Boolean get() = isPurchased || isComplete
}

/** Saved amount at the end of a day, for the progress chart. */
data class SavingsPoint(val dayMs: Long, val saved: Double)

/** The next thing to reach: a named checkpoint or a percentage milestone. */
data class NextCheckpoint(val name: String, val amount: Double, val toGo: Double)

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

    fun progress(
        goal: GoalEntity,
        entries: List<GoalEntryEntity>,
        nowMs: Long,
        milestones: List<GoalMilestoneEntity> = emptyList()
    ): GoalProgress {
        val own = entries.filter { it.goalId == goal.id }
            .sortedWith(compareByDescending<GoalEntryEntity> { it.date }.thenByDescending { it.id })
        val checkpoints = milestones.filter { it.goalId == goal.id }.sortedWith(compareBy({ it.amount }, { it.id }))
        val saved = own.sumOf { it.amount }.coerceAtLeast(0.0)
        val complete = saved >= goal.target - EPSILON
        val due = goal.targetDate
        val reached = if (goal.purchasedAt != null) 100 else milestone(saved, goal.target)
        val forecast = if (complete || goal.purchasedAt != null) null else forecastDate(goal, own, saved, nowMs)
        if (due == null || complete || goal.purchasedAt != null) {
            return GoalProgress(goal, saved, own, reached, null, null, null, checkpoints, forecast)
        }
        val today = FinancialCalculations.startOfDay(nowMs)
        if (due < today) {
            return GoalProgress(goal, saved, own, reached, null, null, GoalPace.MISSED, checkpoints, forecast)
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
        return GoalProgress(goal, saved, own, reached, remaining / weeksLeft, weeksLeft, pace, checkpoints, forecast)
    }

    /** Unfinished goals first (soonest date first, undated after); achieved goals last, newest first. */
    fun all(
        goals: List<GoalEntity>,
        entries: List<GoalEntryEntity>,
        nowMs: Long,
        milestones: List<GoalMilestoneEntity> = emptyList()
    ): List<GoalProgress> {
        val all = goals.map { progress(it, entries, nowMs, milestones) }
        val active = all.filterNot { it.isAchieved }
            .sortedWith(compareBy<GoalProgress> { it.goal.targetDate ?: Long.MAX_VALUE }.thenBy { it.goal.createdAt })
        val achieved = all.filter { it.isAchieved }
            .sortedByDescending { it.goal.purchasedAt ?: it.goal.completedAt ?: it.goal.createdAt }
        return active + achieved
    }

    /** Days of history the saving pace is averaged over. */
    private const val PACE_WINDOW_DAYS = 56L
    private const val MIN_PACE_DAYS = 7L

    /**
     * When [goal] will be reached if the owner keeps saving at their recent pace: the net amount
     * added over the last 8 weeks (at least a week), per day. The "already saved" amount entered
     * when the goal was made (dated exactly at creation) is a starting balance, not pace.
     * Null when nothing was added recently or the date is more than 10 years away.
     */
    internal fun forecastDate(goal: GoalEntity, entries: List<GoalEntryEntity>, saved: Double, nowMs: Long): Long? {
        val paced = entries.filterNot { it.date == goal.createdAt }
        if (paced.isEmpty()) return null
        val today = FinancialCalculations.startOfDay(nowMs)
        val firstDay = FinancialCalculations.startOfDay(paced.minOf { it.date })
        val windowStart = maxOf(firstDay, today - (PACE_WINDOW_DAYS - 1) * DAY_MS)
        val days = maxOf(MIN_PACE_DAYS, (today - windowStart) / DAY_MS + 1)
        val net = paced.filter { it.date >= windowStart }.sumOf { it.amount }
        if (net <= EPSILON) return null
        val perDay = net / days
        val daysNeeded = ceil((goal.target - saved) / perDay).toLong()
        if (daysNeeded > 3650) return null
        return today + daysNeeded * DAY_MS
    }

    /** Saved amount at the end of each day money moved, oldest first, starting from zero. */
    fun timeline(entries: List<GoalEntryEntity>): List<SavingsPoint> {
        if (entries.isEmpty()) return emptyList()
        var total = 0.0
        val points = ArrayList<SavingsPoint>()
        entries.sortedWith(compareBy<GoalEntryEntity> { it.date }.thenBy { it.id })
            .groupBy { FinancialCalculations.startOfDay(it.date) }
            .forEach { (day, dayEntries) ->
                total = (total + dayEntries.sumOf { it.amount }).coerceAtLeast(0.0)
                points += SavingsPoint(day, total)
            }
        return points
    }

    /** The smallest unreached named checkpoint or percentage milestone; null once complete. */
    fun nextCheckpoint(progress: GoalProgress): NextCheckpoint? {
        if (progress.isAchieved) return null
        val target = progress.goal.target
        val candidates = progress.checkpoints.map { it.name to it.amount } +
            MILESTONES.map { (if (it == 100) "Goal" else "$it%") to target * it / 100.0 }
        return candidates
            .filter { it.second > progress.saved + EPSILON }
            .minByOrNull { it.second }
            ?.let { (name, amount) -> NextCheckpoint(name, amount, amount - progress.saved) }
    }

    /** Named checkpoints reached by [savedAfter] that were not celebrated yet. */
    fun checkpointsToCelebrate(milestones: List<GoalMilestoneEntity>, savedAfter: Double): List<GoalMilestoneEntity> =
        milestones.filter { !it.celebrated && savedAfter >= it.amount - EPSILON }.sortedBy { it.amount }

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
