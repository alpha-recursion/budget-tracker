package com.example.domain

import com.example.data.local.GoalEntity
import com.example.data.local.GoalEntryEntity
import com.example.data.local.TransactionEntity
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToLong

/** Monthly income and the day of the month it arrives (1-31; short months use their last day). */
data class PaySettings(
    val monthlyIncome: Double = 0.0,
    val payday: Int = 0,
    /** How income is split into needs, wants and savings. */
    val strategy: SavingsStrategy = SavingsStrategy.DEFAULT
) {
    val isSet: Boolean get() = monthlyIncome > 0.0 && payday in 1..31

    /** Saved first each cycle and never shown as spendable. */
    val savingsTarget: Double get() = if (monthlyIncome > 0) monthlyIncome * strategy.savings else 0.0
}

data class PayCycleStatus(
    val cycleStart: Long,
    val nextPayday: Long,
    /** Days from today up to (not including) the next payday; at least 1. */
    val daysLeft: Int,
    val spent: Double,
    val savedToGoals: Double,
    /** Income minus spending and goal deposits since the last payday; can be negative. */
    val left: Double,
    val perDay: Double,
    /** True on payday (or after it) until a salary-sized income is recorded in this cycle. */
    val salaryDue: Boolean,
    val income: Double
) {
    /** Share of this cycle's income already used, 0..1. */
    val fractionUsed: Float get() = if (income <= 0) 1f else ((spent + savedToGoals) / income).toFloat().coerceIn(0f, 1f)
}

/** "₹X left until payday": the current pay cycle from [PaySettings]. Pure Kotlin. */
object PayCycle {

    private const val DAY_MS = 86_400_000L

    /** The payday in the month of [c], clamped to that month's last day. */
    private fun paydayIn(c: Calendar, payday: Int): Calendar = (c.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, minOf(payday, getActualMaximum(Calendar.DAY_OF_MONTH)))
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    /** Start of the current cycle (the latest payday on or before today) and the next payday. */
    fun bounds(payday: Int, nowMs: Long, timeZone: TimeZone = TimeZone.getDefault()): Pair<Long, Long> {
        val today = Calendar.getInstance(timeZone).apply {
            timeInMillis = FinancialCalculations.startOfDay(nowMs, timeZone)
        }
        val thisMonth = paydayIn(today, payday)
        return if (!thisMonth.after(today)) {
            val next = paydayIn((today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, 1) }, payday)
            thisMonth.timeInMillis to next.timeInMillis
        } else {
            val prev = paydayIn((today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -1) }, payday)
            prev.timeInMillis to thisMonth.timeInMillis
        }
    }

    fun status(
        settings: PaySettings,
        transactions: List<TransactionEntity>,
        goals: List<GoalEntity>,
        goalEntries: List<GoalEntryEntity>,
        nowMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): PayCycleStatus? {
        if (!settings.isSet) return null
        val (start, next) = bounds(settings.payday, nowMs, timeZone)
        val today = FinancialCalculations.startOfDay(nowMs, timeZone)
        val daysLeft = ((next - today).toDouble() / DAY_MS).roundToLong().toInt().coerceAtLeast(1)
        val inCycle = transactions.filter { it.date >= start && it.date < next }
        val spent = inCycle.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val createdAt = goals.associate { it.id to it.createdAt }
        val saved = goalEntries
            .filter { it.date >= start && it.date < next && it.date != createdAt[it.goalId] }
            .sumOf { it.amount }
            .coerceAtLeast(0.0)
        val left = settings.monthlyIncome - spent - saved
        // Salary counts as arrived once an income of at least half the usual amount is recorded.
        val salaryArrived = inCycle.any { it.type == "INCOME" && it.amount >= settings.monthlyIncome / 2 }
        return PayCycleStatus(
            cycleStart = start,
            nextPayday = next,
            daysLeft = daysLeft,
            spent = spent,
            savedToGoals = saved,
            left = left,
            perDay = if (left > 0) left / daysLeft else 0.0,
            salaryDue = !salaryArrived,
            income = settings.monthlyIncome
        )
    }
}
