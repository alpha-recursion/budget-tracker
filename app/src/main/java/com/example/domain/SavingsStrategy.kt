package com.example.domain

import com.example.data.local.GoalEntryEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.TransactionType
import java.util.Calendar
import java.util.TimeZone

/**
 * How income is split. Savings come out first ("pay yourself first") and are never counted as
 * spendable; needs and wants are spread over budget categories. Shares of monthly income.
 */
enum class SavingsStrategy(val label: String, val needs: Double, val wants: Double, val savings: Double, val description: String) {
    FIFTY_THIRTY_TWENTY("50/30/20", 0.50, 0.30, 0.20, "50% needs, 30% wants, 20% savings"),
    SAVE_HALF("Save half", 0.40, 0.10, 0.50, "40% needs, 10% wants, 50% savings"),
    SIXTY_TWENTY_TWENTY("60/20/20", 0.60, 0.20, 0.20, "60% needs, 20% wants, 20% savings (higher rent)"),
    // 70% living costs (split 55 needs / 15 wants), 20% savings, 10% debt or giving (not budgeted).
    SEVENTY_TWENTY_TEN("70/20/10", 0.55, 0.15, 0.20, "70% living, 20% savings, 10% debt or giving");

    companion object {
        val DEFAULT = FIFTY_THIRTY_TWENTY
        fun fromName(name: String?): SavingsStrategy = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Needs vs wants, for splitting a strategy's shares over categories. */
object CategoryGroups {
    val NEEDS = listOf(
        "Housing & Rent" to 40.0, "Groceries" to 20.0, "Utilities & Bills" to 10.0, "Healthcare" to 10.0,
        "Transportation" to 8.0, "Fuel & Vehicle" to 7.0, "Mobile & Recharge" to 5.0
    )
    val WANTS = listOf("Food & Dining" to 40.0, "Shopping" to 30.0, "Tea & Snacks" to 15.0, "Entertainment" to 15.0)

    private val needNames = NEEDS.map { it.first }.toSet()

    /** Anything not listed as a need (custom categories, Other) counts as a want. */
    fun isNeed(category: String): Boolean = category in needNames
}

/** Money not spent in the last finished pay cycle, offered once to move into savings. */
data class CycleLeftover(
    /** Start of the finished cycle; remembered so the offer is made only once. */
    val cycleStart: Long,
    val amount: Double
)

object CycleSavings {

    /**
     * Income minus spending minus what was already moved into goals during the last finished
     * cycle. Null when there is nothing left, no income is set, or nothing was recorded then
     * (e.g. the app was not used yet, so the whole income would look "unspent").
     */
    fun lastCycleLeftover(
        pay: PaySettings,
        transactions: List<TransactionEntity>,
        goalEntries: List<GoalEntryEntity>,
        nowMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): CycleLeftover? {
        if (!pay.isSet) return null
        val (currentStart, _) = PayCycle.bounds(pay.payday, nowMs, timeZone)
        // The day before this cycle lies in the previous one.
        val dayBefore = Calendar.getInstance(timeZone).apply { timeInMillis = currentStart; add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        val (previousStart, previousEnd) = PayCycle.bounds(pay.payday, dayBefore, timeZone)
        val expenses = transactions.filter { it.type == TransactionType.EXPENSE.name && it.date >= previousStart && it.date < previousEnd }
        if (expenses.isEmpty()) return null
        val moved = goalEntries.filter { it.date >= previousStart && it.date < previousEnd }.sumOf { it.amount }
        val left = pay.monthlyIncome - expenses.sumOf { it.amount } - moved
        // Whole rupees: nobody needs ₹0.40 moved.
        val amount = kotlin.math.floor(left)
        return if (amount >= 1.0) CycleLeftover(previousStart, amount) else null
    }
}
