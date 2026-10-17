package com.example.domain

import com.example.data.local.BudgetEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.TransactionType
import java.util.Calendar
import java.util.TimeZone

/**
 * Suggested monthly budgets from the owner's salary and [SavingsStrategy]: savings come off the
 * top and are never budgeted; needs categories share the needs part and wants categories the
 * wants part. Within a group, each category gets its usual monthly spend trimmed by 10% (or a
 * starter split without history), and a group never goes over its share; whatever is left
 * unbudgeted stays saved. Pure Kotlin.
 */
object BudgetPlanner {

    private const val TRIM = 0.90
    private const val HISTORY_MONTHS = 3

    /**
     * @param expenseCategories expense categories currently offered; nothing else is budgeted.
     * @return one budget per category, rounded down to ₹100; empty when there is no income.
     */
    fun suggest(
        monthlyIncome: Double,
        transactions: List<TransactionEntity>,
        expenseCategories: Set<String>,
        nowMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
        strategy: SavingsStrategy = SavingsStrategy.DEFAULT
    ): List<BudgetEntity> {
        if (monthlyIncome <= 0) return emptyList()
        val usual = usualMonthlySpend(transactions, nowMs, timeZone).filterKeys { it in expenseCategories }

        fun group(isNeed: Boolean, starter: List<Pair<String, Double>>, share: Double): Map<String, Double> {
            val pool = monthlyIncome * share
            val history = usual.filterKeys { CategoryGroups.isNeed(it) == isNeed }
            val raw = if (usual.isNotEmpty()) {
                history.mapValues { it.value * TRIM }
            } else {
                val weights = starter.filter { it.first in expenseCategories }
                val total = weights.sumOf { it.second }
                weights.associate { (name, w) -> name to pool * w / total }
            }
            val sum = raw.values.sum()
            // Small tolerance: the starter split adds up to exactly the pool.
            val scale = if (sum > pool + 0.01) pool / sum else 1.0
            return raw.mapValues { it.value * scale }
        }

        val budgets = group(true, CategoryGroups.NEEDS, strategy.needs) + group(false, CategoryGroups.WANTS, strategy.wants)
        return budgets.mapNotNull { (category, amount) ->
            val limit = roundDown100(amount)
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
