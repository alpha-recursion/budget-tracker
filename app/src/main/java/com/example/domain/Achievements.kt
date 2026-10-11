package com.example.domain

import com.example.data.local.BudgetEntity
import com.example.data.local.DebtEntryEntity
import com.example.data.local.GoalEntity
import com.example.data.local.GoalEntryEntity
import com.example.data.local.GoalMilestoneEntity
import com.example.data.local.TransactionEntity
import java.util.Calendar
import java.util.TimeZone

/** Badges the owner can earn; everything is worked out from their data, nothing is stored. */
enum class Badge(val emoji: String, val title: String, val howTo: String) {
    FIRST_STEPS("📝", "First steps", "Record 10 transactions"),
    HABIT("📚", "Habit formed", "Record 100 transactions"),
    AUTO_PILOT("📲", "On autopilot", "Import 10 payments from bank SMS"),
    FIRST_GOAL("🎯", "First goal", "Create a savings goal"),
    QUARTER("🌱", "Quarter way", "Reach 25% of a goal"),
    HALFWAY("⛰️", "Halfway hero", "Reach 50% of a goal"),
    GOAL_GETTER("🏆", "Goal getter", "Reach a goal's target"),
    TREAT("🛍️", "Treat yourself", "Buy something you saved for"),
    CHECKPOINT("🚩", "Checkpoint champ", "Reach one of your own checkpoints"),
    SPARE_CHANGE("🫙", "Spare change", "Move your round-up jar into a goal"),
    STREAK_3("🔥", "On a roll", "Save 3 weeks in a row"),
    STREAK_8("☄️", "Unstoppable", "Save 8 weeks in a row"),
    BUDGET_KEEPER("🛡️", "Budget keeper", "Finish a month within all your budgets"),
    SETTLED("🤝", "Settled up", "Get a loan fully repaid or repay one")
}

/** Everything the badges and XP are worked out from. */
data class AchievementData(
    val transactions: List<TransactionEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val goalEntries: List<GoalEntryEntity> = emptyList(),
    val goalMilestones: List<GoalMilestoneEntity> = emptyList(),
    val debts: List<DebtEntryEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val bestStreak: Int = 0
)

data class Level(val number: Int, val name: String, val startXp: Int, val nextXp: Int?) {
    /** Progress through this level, 0..1 (1 at the top level). */
    fun fraction(xp: Int): Float =
        if (nextXp == null) 1f else ((xp - startXp).toFloat() / (nextXp - startXp)).coerceIn(0f, 1f)
}

data class AchievementProgress(val xp: Int, val level: Level, val earned: Set<Badge>) {
    val earnedCount: Int get() = earned.size
}

object Achievements {

    /** XP needed to reach each level (index 0 = level 1). */
    private val LEVEL_XP = listOf(0, 100, 250, 500, 900, 1400, 2000, 2800, 3800, 5000)
    private val LEVEL_NAMES = listOf(
        "Penny Pincher", "Coin Keeper", "Smart Spender", "Saver", "Planner",
        "Budget Boss", "Wealth Builder", "Goal Crusher", "Money Sage", "Money Master"
    )

    const val XP_TRANSACTION = 2
    const val XP_DEPOSIT = 10
    const val XP_MILESTONE = 25
    const val XP_CHECKPOINT = 15
    const val XP_STREAK_WEEK = 20
    const val XP_BADGE = 50

    /** Only the first 1,000 transactions earn XP, so bulk imports cannot run away with it. */
    private const val MAX_TRANSACTIONS_FOR_XP = 1000

    fun level(xp: Int): Level {
        val index = LEVEL_XP.indexOfLast { xp >= it }.coerceAtLeast(0)
        return Level(index + 1, LEVEL_NAMES[index], LEVEL_XP[index], LEVEL_XP.getOrNull(index + 1))
    }

    fun compute(data: AchievementData, nowMs: Long, timeZone: TimeZone = TimeZone.getDefault()): AchievementProgress {
        val earned = badges(data, nowMs, timeZone)
        val createdAt = data.goals.associate { it.id to it.createdAt }
        val deposits = data.goalEntries.count { it.amount > 0.0 && it.date != createdAt[it.goalId] }
        val milestones = data.goals.sumOf { goal -> Goals.MILESTONES.count { it <= goal.celebratedMilestone } }
        val checkpoints = data.goalMilestones.count { it.celebrated }
        val xp = minOf(data.transactions.size, MAX_TRANSACTIONS_FOR_XP) * XP_TRANSACTION +
            deposits * XP_DEPOSIT +
            milestones * XP_MILESTONE +
            checkpoints * XP_CHECKPOINT +
            data.bestStreak * XP_STREAK_WEEK +
            earned.size * XP_BADGE
        return AchievementProgress(xp, level(xp), earned)
    }

    private fun badges(data: AchievementData, nowMs: Long, timeZone: TimeZone): Set<Badge> {
        val earned = mutableSetOf<Badge>()
        val txCount = data.transactions.size
        if (txCount >= 10) earned += Badge.FIRST_STEPS
        if (txCount >= 100) earned += Badge.HABIT
        // Imported transactions carry "From <bank> SMS" in their note.
        if (data.transactions.count { it.note.startsWith("From ") && it.note.contains(" SMS") } >= 10) earned += Badge.AUTO_PILOT
        if (data.goals.isNotEmpty()) earned += Badge.FIRST_GOAL
        val best = data.goals.maxOfOrNull { goal ->
            if (goal.purchasedAt != null || goal.completedAt != null) 100 else goal.celebratedMilestone
        } ?: 0
        if (best >= 25) earned += Badge.QUARTER
        if (best >= 50) earned += Badge.HALFWAY
        if (best >= 100) earned += Badge.GOAL_GETTER
        if (data.goals.any { it.purchasedAt != null }) earned += Badge.TREAT
        if (data.goalMilestones.any { it.celebrated }) earned += Badge.CHECKPOINT
        if (data.goalEntries.any { it.amount > 0.0 && it.note == "Round-up jar" }) earned += Badge.SPARE_CHANGE
        if (data.bestStreak >= 3) earned += Badge.STREAK_3
        if (data.bestStreak >= 8) earned += Badge.STREAK_8
        if (keptLastMonthsBudgets(data, nowMs, timeZone)) earned += Badge.BUDGET_KEEPER
        val settled = Lending.balances(data.debts, nowMs).any { it.isSettled && it.entries.size >= 2 }
        if (settled) earned += Badge.SETTLED
        return earned
    }

    /**
     * Last complete calendar month: at least one budget and one expense, and every budgeted
     * category stayed within its limit.
     */
    internal fun keptLastMonthsBudgets(data: AchievementData, nowMs: Long, timeZone: TimeZone): Boolean {
        if (data.budgets.isEmpty()) return false
        val start = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMs
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -1)
        }
        val from = start.timeInMillis
        val to = start.apply { add(Calendar.MONTH, 1) }.timeInMillis
        val expenses = data.transactions.filter { it.type == "EXPENSE" && it.date in from until to }
        if (expenses.isEmpty()) return false
        val spent = expenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }
        return data.budgets.all { (spent[it.category] ?: 0.0) <= it.monthlyLimit + 0.005 }
    }

    /** Badges earned now that the owner has not been shown yet. */
    fun newBadges(progress: AchievementProgress, seen: Set<String>): List<Badge> =
        progress.earned.filter { it.name !in seen }.sortedBy { it.ordinal }
}
