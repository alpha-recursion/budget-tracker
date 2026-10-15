package com.example.domain

import com.example.data.local.TransactionEntity
import com.example.data.model.TransactionType
import java.util.Calendar
import java.util.TimeZone

enum class SpendPeriod { TODAY, WEEK, MONTH }

/** One bar of the hero chart: an hour range (today), a weekday (week) or a week (month). */
data class KpiBar(val label: String, val amount: Double, val current: Boolean, val future: Boolean = false)

/** What the Home hero shows for one period. Text is built by the screen. */
data class SpendKpi(
    val period: SpendPeriod,
    val spent: Double,
    val payments: Int,
    val bars: List<KpiBar>,
    /** Month spending so far divided by days so far: the "usual" day. */
    val averagePerDay: Double,
    /** This week's spending divided by the days of the week so far. */
    val perDayThisWeek: Double,
    val weekStart: Long,
    val weekEnd: Long,
    val monthIncome: Double,
    val monthStart: Long,
    val daysLeftInMonth: Int
) {
    /** Income minus spending this month, as a percentage of income; null without income. */
    val savingRate: Double? get() = if (monthIncome > 0.0) (monthIncome - spent) / monthIncome * 100 else null
}

/** Spending for today, this week (Monday to Sunday) or this month, with chart bars. */
object HomeSpend {

    private val HOUR_BUCKETS = listOf(0 to "12a", 6 to "6a", 9 to "9a", 12 to "12p", 15 to "3p", 18 to "6p", 21 to "9p")
    private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun kpi(
        transactions: List<TransactionEntity>,
        period: SpendPeriod,
        nowMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): SpendKpi {
        val expenses = transactions.filter { it.type == TransactionType.EXPENSE.name && it.date <= nowMs }
        val cal = { ms: Long -> Calendar.getInstance(timeZone).apply { timeInMillis = ms } }
        val todayStart = FinancialCalculations.startOfDay(nowMs, timeZone)
        val tomorrowStart = cal(todayStart).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        val monthStart = cal(todayStart).apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        val now = cal(nowMs)
        val dayOfMonth = now.get(Calendar.DAY_OF_MONTH)
        val daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)
        val weekStart = FinancialCalculations.startOfWeek(nowMs, timeZone)
        val dayStarts = (0..7).map { i -> cal(weekStart).apply { add(Calendar.DAY_OF_YEAR, i) }.timeInMillis }
        val weekdayIndex = dayStarts.indexOfLast { it <= nowMs }.coerceIn(0, 6)

        fun between(from: Long, to: Long) = expenses.filter { it.date in from until to }
        val monthSpent = between(monthStart, tomorrowStart).sumOf { it.amount }
        val weekList = between(weekStart, dayStarts[7])
        val monthIncome = transactions
            .filter { it.type == TransactionType.INCOME.name && it.date in monthStart until tomorrowStart }
            .sumOf { it.amount }

        val (inPeriod, bars) = when (period) {
            SpendPeriod.TODAY -> {
                val today = between(todayStart, tomorrowStart)
                val hourNow = now.get(Calendar.HOUR_OF_DAY)
                today to HOUR_BUCKETS.mapIndexed { i, (start, label) ->
                    val end = HOUR_BUCKETS.getOrNull(i + 1)?.first ?: 24
                    KpiBar(
                        label = label,
                        amount = today.filter { cal(it.date).get(Calendar.HOUR_OF_DAY) in start until end }.sumOf { it.amount },
                        current = hourNow in start until end
                    )
                }
            }
            SpendPeriod.WEEK -> weekList to WEEKDAYS.mapIndexed { i, label ->
                KpiBar(
                    label = label,
                    amount = between(dayStarts[i], dayStarts[i + 1]).sumOf { it.amount },
                    current = i == weekdayIndex,
                    future = i > weekdayIndex
                )
            }
            SpendPeriod.MONTH -> {
                val list = between(monthStart, tomorrowStart)
                val ranges = listOf(1 to 7, 8 to 14, 15 to 21, 22 to 28, 29 to daysInMonth).filter { it.first <= daysInMonth }
                list to ranges.map { (from, to) ->
                    KpiBar(
                        label = "$from–$to",
                        amount = list.filter { cal(it.date).get(Calendar.DAY_OF_MONTH) in from..to }.sumOf { it.amount },
                        current = dayOfMonth in from..to,
                        future = dayOfMonth < from
                    )
                }
            }
        }
        val weekSpent = weekList.sumOf { it.amount }
        return SpendKpi(
            period = period,
            spent = inPeriod.sumOf { it.amount },
            payments = inPeriod.size,
            bars = bars,
            averagePerDay = monthSpent / dayOfMonth,
            perDayThisWeek = weekSpent / (weekdayIndex + 1),
            weekStart = weekStart,
            weekEnd = dayStarts[6],
            monthIncome = monthIncome,
            monthStart = monthStart,
            daysLeftInMonth = daysInMonth - dayOfMonth
        )
    }
}
