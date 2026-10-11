package com.example.domain

import com.example.data.local.BudgetEntity
import com.example.data.local.ChallengeEntity
import com.example.data.local.GoalEntity
import com.example.data.local.GoalEntryEntity
import com.example.data.local.TransactionEntity
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class ChallengeType { CATEGORY_LIMIT, TOTAL_SPEND, SAVE_AMOUNT, NO_SPEND_DAYS }

enum class ChallengeState { ACTIVE, WON, FAILED }

/** A challenge the app suggests; picking it stores a ChallengeEntity. */
data class ChallengeSuggestion(val type: ChallengeType, val category: String?, val target: Double)

data class ChallengeResult(
    val challenge: ChallengeEntity,
    val type: ChallengeType,
    /** Spent (limits), saved (save amount) or no-spend days so far. */
    val current: Double,
    val state: ChallengeState
) {
    val fraction: Float get() = if (challenge.target <= 0.0) 0f else (current / challenge.target).toFloat().coerceIn(0f, 1f)
}

/** A calendar month in the owner's time zone. */
data class Month(val key: Int, val start: Long, val end: Long) {
    val year: Int get() = key / 100
    val month: Int get() = key % 100
}

/** The data challenges and report cards are worked out from. */
data class MoneyData(
    val transactions: List<TransactionEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val goalEntries: List<GoalEntryEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList()
)

/** Monthly challenges: suggestions from last month's numbers, and live progress. Pure Kotlin. */
object Challenges {

    private const val DAY_MS = 86_400_000L

    fun month(referenceMs: Long, timeZone: TimeZone = TimeZone.getDefault()): Month {
        val c = Calendar.getInstance(timeZone).apply {
            timeInMillis = referenceMs
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val key = c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH) + 1
        val start = c.timeInMillis
        c.add(Calendar.MONTH, 1)
        return Month(key, start, c.timeInMillis)
    }

    fun monthOf(key: Int, timeZone: TimeZone = TimeZone.getDefault()): Month {
        val c = Calendar.getInstance(timeZone).apply {
            clear()
            set(key / 100, key % 100 - 1, 1)
        }
        return month(c.timeInMillis, timeZone)
    }

    fun previous(month: Month, timeZone: TimeZone = TimeZone.getDefault()): Month = month(month.start - DAY_MS, timeZone)

    fun title(type: ChallengeType, category: String?, target: Double): String = when (type) {
        ChallengeType.CATEGORY_LIMIT -> "Keep $category under ${rupees(target)}"
        ChallengeType.TOTAL_SPEND -> "Spend under ${rupees(target)} this month"
        ChallengeType.SAVE_AMOUNT -> "Add ${rupees(target)} to your goals"
        ChallengeType.NO_SPEND_DAYS -> "${target.toInt()} no-spend days"
    }

    private fun rupees(value: Double): String = "₹" + String.format(java.util.Locale.US, "%,d", value.roundToInt())

    /** Rounds to a friendly amount: the nearest ₹100 (₹50 under ₹1,000), never below ₹100. */
    private fun friendly(value: Double): Double {
        val step = if (value < 1000) 50.0 else 100.0
        return maxOf(100.0, (value / step).roundToInt() * step)
    }

    /**
     * Up to three suggestions for [month] from the previous month, skipping kinds already picked.
     * Without history the targets fall back to sensible starters.
     */
    fun suggest(
        data: MoneyData,
        month: Month,
        picked: List<ChallengeEntity>,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<ChallengeSuggestion> {
        val last = previous(month, timeZone)
        val lastExpenses = expenses(data.transactions, last)
        val pickedTypes = picked.filter { it.month == month.key }.map { it.type }.toSet()
        val out = ArrayList<ChallengeSuggestion>()

        val top = lastExpenses.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .maxByOrNull { it.value }
        if (top != null && top.value >= 500.0) {
            out += ChallengeSuggestion(ChallengeType.CATEGORY_LIMIT, top.key, friendly(top.value * 0.9))
        }
        val income = data.transactions.filter { it.type == "INCOME" && it.date in last.start until last.end }.sumOf { it.amount }
        val lastSaved = savedInto(data, last)
        val saveTarget = when {
            lastSaved > 0 -> friendly(lastSaved * 1.2)
            income > 0 -> friendly(income * 0.1)
            else -> 1000.0
        }
        out += ChallengeSuggestion(ChallengeType.SAVE_AMOUNT, null, saveTarget)
        val lastNoSpend = noSpendDays(data.transactions, last, last.end, timeZone)
        val noSpendTarget = if (lastExpenses.isEmpty()) 5 else (lastNoSpend + 2).coerceIn(3, 15)
        out += ChallengeSuggestion(ChallengeType.NO_SPEND_DAYS, null, noSpendTarget.toDouble())
        val lastTotal = lastExpenses.sumOf { it.amount }
        if (lastTotal >= 1000.0) {
            out += ChallengeSuggestion(ChallengeType.TOTAL_SPEND, null, friendly(lastTotal * 0.95))
        }
        return out.filter { it.type.name !in pickedTypes }.take(3)
    }

    fun evaluate(challenge: ChallengeEntity, data: MoneyData, nowMs: Long, timeZone: TimeZone = TimeZone.getDefault()): ChallengeResult? {
        val type = ChallengeType.entries.firstOrNull { it.name == challenge.type } ?: return null
        val month = monthOf(challenge.month, timeZone)
        val over = nowMs >= month.end
        return when (type) {
            ChallengeType.CATEGORY_LIMIT, ChallengeType.TOTAL_SPEND -> {
                val spent = expenses(data.transactions, month)
                    .filter { type == ChallengeType.TOTAL_SPEND || it.category == challenge.category }
                    .sumOf { it.amount }
                val state = when {
                    spent > challenge.target + 0.005 -> ChallengeState.FAILED
                    over -> ChallengeState.WON
                    else -> ChallengeState.ACTIVE
                }
                ChallengeResult(challenge, type, spent, state)
            }
            ChallengeType.SAVE_AMOUNT -> {
                val saved = savedInto(data, month)
                val state = when {
                    saved >= challenge.target - 0.005 -> ChallengeState.WON
                    over -> ChallengeState.FAILED
                    else -> ChallengeState.ACTIVE
                }
                ChallengeResult(challenge, type, saved, state)
            }
            ChallengeType.NO_SPEND_DAYS -> {
                val upTo = minOf(nowMs, month.end)
                val done = noSpendDays(data.transactions, month, upTo, timeZone)
                val daysLeft = if (over) 0 else ceil((month.end - FinancialCalculations.startOfDay(nowMs, timeZone)) / DAY_MS.toDouble()).toInt()
                val state = when {
                    done >= challenge.target -> ChallengeState.WON
                    done + daysLeft < challenge.target -> ChallengeState.FAILED
                    else -> ChallengeState.ACTIVE
                }
                ChallengeResult(challenge, type, done.toDouble(), state)
            }
        }
    }

    private fun expenses(transactions: List<TransactionEntity>, month: Month) =
        transactions.filter { it.type == "EXPENSE" && it.date >= month.start && it.date < month.end }

    /** Net money added to goals in [month]; the starting amount of a new goal does not count. */
    internal fun savedInto(data: MoneyData, month: Month): Double {
        val createdAt = data.goals.associate { it.id to it.createdAt }
        return data.goalEntries
            .filter { it.date >= month.start && it.date < month.end && it.date != createdAt[it.goalId] }
            .sumOf { it.amount }
            .coerceAtLeast(0.0)
    }

    /** Whole days of [month] before [upToMs] (today only counts once it is over) with no expense. */
    internal fun noSpendDays(transactions: List<TransactionEntity>, month: Month, upToMs: Long, timeZone: TimeZone): Int {
        val spendDays = expenses(transactions, month).map { FinancialCalculations.startOfDay(it.date, timeZone) }.toSet()
        val lastDay = FinancialCalculations.startOfDay(minOf(upToMs, month.end), timeZone)
        var count = 0
        var day = month.start
        while (day < lastDay) {
            if (day !in spendDays) count++
            day = FinancialCalculations.startOfDay(day + DAY_MS + 3 * 3_600_000L, timeZone)
        }
        return count
    }
}

/** A month's report card. */
data class ReportCard(
    val month: Month,
    val grade: String,
    val score: Int,
    val headline: String,
    val spent: Double,
    val earned: Double,
    /** (earned - spent) / earned; null without income. */
    val savingsRate: Double?,
    /** Change in spending against the month before; null when that month had none. */
    val spendChange: Double?,
    val topCategory: Pair<String, Double>?,
    val biggestExpense: TransactionEntity?,
    val budgetsKept: Int,
    val budgetsTotal: Int,
    val savedToGoals: Double,
    val challengesWon: Int,
    val challengesTotal: Int,
    val noSpendDays: Int
)

object ReportCards {

    /** Months (newest first) that have at least one transaction, up to [limit]. */
    fun monthsWithData(transactions: List<TransactionEntity>, limit: Int = 12, timeZone: TimeZone = TimeZone.getDefault()): List<Month> =
        transactions.map { Challenges.month(it.date, timeZone).key }.distinct().sortedDescending().take(limit)
            .map { Challenges.monthOf(it, timeZone) }

    /**
     * Grade for [month] (it may still be running). Starts at 50 and moves with the savings rate,
     * budgets kept, money put into goals, challenges won and spending against the month before.
     */
    fun build(
        data: MoneyData,
        month: Month,
        challenges: List<ChallengeResult>,
        nowMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): ReportCard? {
        val inMonth = data.transactions.filter { it.date >= month.start && it.date < month.end }
        if (inMonth.isEmpty()) return null
        val expenses = inMonth.filter { it.type == "EXPENSE" }
        val spent = expenses.sumOf { it.amount }
        val earned = inMonth.filter { it.type == "INCOME" }.sumOf { it.amount }
        val rate = if (earned > 0) (earned - spent) / earned else null
        val prev = Challenges.previous(month, timeZone)
        val prevSpent = data.transactions.filter { it.type == "EXPENSE" && it.date >= prev.start && it.date < prev.end }.sumOf { it.amount }
        val change = if (prevSpent > 0) (spent - prevSpent) / prevSpent else null
        val byCategory = expenses.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amount } }
        val kept = data.budgets.count { (byCategory[it.category] ?: 0.0) <= it.monthlyLimit + 0.005 }
        val saved = Challenges.savedInto(data, month)
        val monthChallenges = challenges.filter { it.challenge.month == month.key }
        val won = monthChallenges.count { it.state == ChallengeState.WON }

        var score = 50.0
        rate?.let { score += (it * 100).coerceIn(-20.0, 30.0) }
        if (data.budgets.isNotEmpty()) {
            score += 20.0 * kept / data.budgets.size
            if (kept < data.budgets.size) score -= 10.0
        }
        if (saved > 0) score += 10.0
        if (monthChallenges.isNotEmpty()) score += 15.0 * won / monthChallenges.size
        if (change != null && change < 0) score += 5.0
        val finalScore = score.roundToInt().coerceIn(0, 100)
        val grade = when {
            finalScore >= 90 -> "A+"
            finalScore >= 80 -> "A"
            finalScore >= 70 -> "B"
            finalScore >= 60 -> "C"
            else -> "D"
        }
        val headline = when (grade) {
            "A+" -> "Outstanding month. You're in full control."
            "A" -> "Great month. Keep this up!"
            "B" -> "Good month with room to grow."
            "C" -> "Okay month. A challenge could help next time."
            else -> "Tough month. Small steps count. Try one challenge."
        }
        return ReportCard(
            month = month,
            grade = grade,
            score = finalScore,
            headline = headline,
            spent = spent,
            earned = earned,
            savingsRate = rate,
            spendChange = change,
            topCategory = byCategory.maxByOrNull { it.value }?.toPair(),
            biggestExpense = expenses.maxByOrNull { it.amount },
            budgetsKept = kept,
            budgetsTotal = data.budgets.size,
            savedToGoals = saved,
            challengesWon = won,
            challengesTotal = monthChallenges.size,
            noSpendDays = Challenges.noSpendDays(data.transactions, month, minOf(nowMs, month.end), timeZone)
        )
    }
}
