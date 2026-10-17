package com.example.domain

import com.example.data.local.BudgetEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.TransactionType
import java.util.Calendar
import java.util.TimeZone

/**
 * Suggested monthly budgets from the owner's salary, aimed at saving: at most [SPEND_SHARE] of
 * income is budgeted (so at least 30% is saved). With spending history each category gets its
 * usual monthly spend trimmed by 10%; without history, a starter split of income. Pure Kotlin.
 */
object BudgetPlanner {

    const val SPEND_SHARE = 0.70
    private const val TRIM = 0.90
    private const val HISTORY_MONTHS = 3

    /** Starter split (share of income) when there is no history; adds up to [SPEND_SHARE]. */
    private val STARTER = listOf(
        "Housing & Rent" to 0.20,
        "Food & Dining" to 0.10,
        "Groceries" to 0.08,
        "Tea & Snacks" to 0.03,
        "Transportation" to 0.05,
        "Fuel & Vehicle" to 0.05,
        "Utilities & Bills" to 0.05,
        "Mobile & Recharge" to 0.02,
        "Healthcare" to 0.04,
        "Shopping" to 0.05,
        "Entertainment" to 0.03
    )

    /**
     * @param expenseCategories expense categories currently offered; nothing else is budgeted.
     * @return one budget per category, rounded down to ₹100; empty when there is no income.
     */
    fun suggest(
        monthlyIncome: Double,
        transactions: List<TransactionEntity>,
        expenseCategories: Set<String>,
        nowMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<BudgetEntity> {
        if (monthlyIncome <= 0) return emptyList()
        val usual = usualMonthlySpend(transactions, nowMs, timeZone)
        val raw: Map<String, Double> = if (usual.isNotEmpty()) {
            usual.mapValues { it.value * TRIM }
        } else {
            STARTER.associate { (name, share) -> name to monthlyIncome * share }
        }.filterKeys { it in expenseCategories }

        // Never budget more than the spend share of income.
        val cap = monthlyIncome * SPEND_SHARE
        val total = raw.values.sum()
        // Small tolerance: the starter shares add up to exactly the cap.
        val scale = if (total > cap + 0.01) cap / total else 1.0
        return raw.mapNotNull { (category, amount) ->
            val limit = roundDown100(amount * scale)
            if (limit <= 0) null else BudgetEntity(category = category, monthlyLimit = limit)
        }.sortedByDescending { it.monthlyLimit }
    }

    /**
     * Average monthly spending per category over the last [HISTORY_MONTHS] full calendar months
     * that have any spending; empty when there is no full month yet.
     */
    internal fun usualMonthlySpend(transactions: List<TransactionEntity>, nowMs: Long, timeZone: TimeZone): Map<String, Double> {
        val monthStart = Calendar.getInstance(timeZone).apply {
            timeInMillis = FinancialCalculations.startOfDay(nowMs, timeZone)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val windowStart = (monthStart.clone() as Calendar).apply { add(Calendar.MONTH, -HISTORY_MONTHS) }.timeInMillis
        val expenses = transactions.filter {
            it.type == TransactionType.EXPENSE.name && it.date >= windowStart && it.date < monthStart.timeInMillis
        }
        val months = expenses.map { tx ->
            Calendar.getInstance(timeZone).apply { timeInMillis = tx.date }.let { it.get(Calendar.YEAR) * 12 + it.get(Calendar.MONTH) }
        }.toSet().size
        if (months == 0) return emptyMap()
        return expenses.groupBy { it.category }.mapValues { (_, txs) -> txs.sumOf { it.amount } / months }
    }

    private fun roundDown100(amount: Double): Double = (amount / 100).toLong() * 100.0
}
