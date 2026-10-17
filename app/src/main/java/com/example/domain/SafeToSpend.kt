package com.example.domain

import com.example.data.local.BudgetEntity
import com.example.data.local.RecurringEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.TransactionType
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToLong

/**
 * How much can be spent today without running out before the cycle ends.
 *
 * @property today what is left of today's allowance; negative when today's spending went over.
 * @property dailyAllowance the money available at the start of today, spread over the days left.
 * @property fromBudgets true when worked out from monthly budgets because no income is set.
 */
data class SafeToSpend(
    val today: Double,
    val dailyAllowance: Double,
    val spentToday: Double,
    val daysLeft: Int,
    val billsHeldBack: Double,
    val fromBudgets: Boolean
)

/**
 * "₹680 safe to spend today": (money for the cycle, after the savings target − spent before today − bills still due
 * before the cycle ends) ÷ days left including today, minus what was spent today. Spend less
 * and tomorrow's allowance grows; overspend and the rest of the cycle shares the shortfall.
 * Pure Kotlin.
 */
object SafeToSpendCalculator {

    private const val DAY_MS = 86_400_000L

    /**
     * Uses income and payday when set; otherwise the total of monthly budgets over the calendar
     * month. Null when neither is set.
     */
    fun compute(
        pay: PaySettings,
        budgets: List<BudgetEntity>,
        transactions: List<TransactionEntity>,
        recurring: List<RecurringEntity>,
        nowMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): SafeToSpend? {
        val budgetTotal = budgets.sumOf { it.monthlyLimit }
        val (pool, start, end) = when {
            // Savings are taken off the top ("pay yourself first") and never shown as spendable.
            pay.isSet -> PayCycle.bounds(pay.payday, nowMs, timeZone).let { (s, e) -> Triple(pay.monthlyIncome - pay.savingsTarget, s, e) }
            budgetTotal > 0 -> monthBounds(nowMs, timeZone).let { (s, e) -> Triple(budgetTotal, s, e) }
            else -> return null
        }
        val todayStart = FinancialCalculations.startOfDay(nowMs, timeZone)
        val tomorrowStart = Calendar.getInstance(timeZone).apply { timeInMillis = todayStart; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        val daysLeft = ((end - todayStart).toDouble() / DAY_MS).roundToLong().toInt().coerceAtLeast(1)

        val expenses = transactions.filter { it.type == TransactionType.EXPENSE.name }
        val spentBefore = expenses.filter { it.date >= start && it.date < todayStart }.sumOf { it.amount }
        val spentToday = expenses.filter { it.date >= todayStart && it.date < tomorrowStart }.sumOf { it.amount }
        // Bills due after today and before the cycle ends; a bill paid today is already in today's spending.
        val bills = recurring.filter { it.active }.sumOf { item ->
            Recurring.dueBetween(item, tomorrowStart, end, timeZone).size * item.amount
        }

        val available = (pool - spentBefore - bills).coerceAtLeast(0.0)
        val allowance = available / daysLeft
        return SafeToSpend(
            today = allowance - spentToday,
            dailyAllowance = allowance,
            spentToday = spentToday,
            daysLeft = daysLeft,
            billsHeldBack = bills,
            fromBudgets = !pay.isSet
        )
    }

    private fun monthBounds(nowMs: Long, timeZone: TimeZone): Pair<Long, Long> {
        val start = Calendar.getInstance(timeZone).apply {
            timeInMillis = FinancialCalculations.startOfDay(nowMs, timeZone)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        return start.timeInMillis to end.timeInMillis
    }
}
