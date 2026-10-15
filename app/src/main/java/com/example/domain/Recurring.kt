package com.example.domain

import com.example.data.local.RecurringEntity
import com.example.data.local.TransactionEntity
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

enum class RecurringFrequency(val label: String, val perYear: Int) {
    WEEKLY("Weekly", 52),
    MONTHLY("Monthly", 12),
    YEARLY("Yearly", 1);

    companion object {
        fun fromName(name: String): RecurringFrequency? = entries.firstOrNull { it.name == name }
    }
}

/** A subscription or bill the app noticed in past spending. */
data class RecurringSuggestion(
    val name: String,
    val amount: Double,
    val category: String,
    val frequency: RecurringFrequency,
    /** Next expected payment, local midnight. */
    val nextDue: Long
)

/** What is still to pay this calendar month, for the Home card. */
data class RecurringMonth(
    val remaining: Double,
    val remainingCount: Int,
    val next: UpcomingPayment?
)

data class UpcomingPayment(val item: RecurringEntity, val date: Long)

/** Recurring payments: due dates, month totals, 3-day alerts and detection. Pure Kotlin. */
object Recurring {

    /** The reminder goes out this many days before a payment is due. */
    const val ALERT_DAYS = 3

    private const val DAY_MS = 86_400_000.0

    fun frequencyOf(item: RecurringEntity): RecurringFrequency =
        RecurringFrequency.fromName(item.frequency) ?: RecurringFrequency.MONTHLY

    /** The [n]th due date (0 = [RecurringEntity.startDate]), counted from the start so the day never drifts. */
    fun occurrence(item: RecurringEntity, n: Int, timeZone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = item.startDate
            when (frequencyOf(item)) {
                RecurringFrequency.WEEKLY -> add(Calendar.DAY_OF_YEAR, 7 * n)
                RecurringFrequency.MONTHLY -> add(Calendar.MONTH, n)
                RecurringFrequency.YEARLY -> add(Calendar.YEAR, n)
            }
        }.timeInMillis

    /** First due date on or after [fromMs] (local midnight of the day to look from). */
    fun nextDue(item: RecurringEntity, fromMs: Long, timeZone: TimeZone = TimeZone.getDefault()): Long {
        var n = 0
        var date = occurrence(item, 0, timeZone)
        while (date < fromMs && n < MAX_STEPS) {
            n++
            date = occurrence(item, n, timeZone)
        }
        return date
    }

    /** Due dates inside [fromMs, toMs). */
    fun dueBetween(item: RecurringEntity, fromMs: Long, toMs: Long, timeZone: TimeZone = TimeZone.getDefault()): List<Long> {
        val result = mutableListOf<Long>()
        var date = nextDue(item, fromMs, timeZone)
        var n = 0
        while (date < toMs && n < MAX_STEPS) {
            result += date
            n++
            date = nextDue(item, date + 1, timeZone)
        }
        return result
    }

    fun month(items: List<RecurringEntity>, nowMs: Long, timeZone: TimeZone = TimeZone.getDefault()): RecurringMonth {
        val today = FinancialCalculations.startOfDay(nowMs, timeZone)
        val monthEnd = Calendar.getInstance(timeZone).apply {
            timeInMillis = today
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, 1)
        }.timeInMillis
        val payments = items.filter { it.active }.flatMap { item ->
            dueBetween(item, today, monthEnd, timeZone).map { UpcomingPayment(item, it) }
        }.sortedBy { it.date }
        // "Next" may fall in the next month when everything this month is already paid.
        val next = payments.firstOrNull() ?: items.filter { it.active }
            .map { UpcomingPayment(it, nextDue(it, today, timeZone)) }
            .minByOrNull { it.date }
        return RecurringMonth(payments.sumOf { it.item.amount }, payments.size, next)
    }

    /** Payments due exactly [ALERT_DAYS] days from today. */
    fun dueForAlert(
        items: List<RecurringEntity>,
        nowMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<UpcomingPayment> {
        val start = Calendar.getInstance(timeZone).apply {
            timeInMillis = FinancialCalculations.startOfDay(nowMs, timeZone)
            add(Calendar.DAY_OF_YEAR, ALERT_DAYS)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        return items.filter { it.active }.flatMap { item ->
            dueBetween(item, start.timeInMillis, end.timeInMillis, timeZone).map { UpcomingPayment(item, it) }
        }.sortedByDescending { it.item.amount }
    }

    /**
     * Payees that keep charging about the same amount at a steady interval: at least three
     * payments a week or a month apart (two for a yearly one), the latest still recent, and
     * not already listed.
     */
    fun detect(
        transactions: List<TransactionEntity>,
        existing: List<RecurringEntity>,
        nowMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<RecurringSuggestion> {
        val expenses = transactions.filter { it.type == "EXPENSE" }
        if (expenses.isEmpty()) return emptyList()
        val canonical = VendorMatcher.canonicalNames(expenses.map { it.title }.distinct())
        val known = existing.map { VendorMatcher.compact(it.name) }
        val today = FinancialCalculations.startOfDay(nowMs, timeZone)
        return expenses.groupBy { VendorMatcher.groupKey(it.title, canonical) }
            .mapNotNull { (key, group) ->
                if (key.isEmpty() || VendorMatcher.bestMatch(key, known) != null) return@mapNotNull null
                suggestion(group.sortedBy { it.date }, canonical, today, timeZone)
            }
            .sortedByDescending { it.amount }
    }

    private fun suggestion(
        sorted: List<TransactionEntity>,
        canonical: Map<String, String>,
        today: Long,
        timeZone: TimeZone
    ): RecurringSuggestion? {
        val recent = sorted.takeLast(RECENT_PAYMENTS)
        if (recent.size < 2) return null
        val gaps = recent.zipWithNext { a, b -> (b.date - a.date) / DAY_MS }
        val frequency = when {
            recent.size >= 3 && gaps.all { it in 6.0..8.0 } -> RecurringFrequency.WEEKLY
            recent.size >= 3 && gaps.all { it in 26.0..35.0 } -> RecurringFrequency.MONTHLY
            gaps.all { it in 355.0..375.0 } -> RecurringFrequency.YEARLY
            else -> return null
        }
        val latest = recent.last()
        // Steady charge: every recent amount within 20% of the latest.
        if (recent.any { abs(it.amount - latest.amount) > latest.amount * 0.2 }) return null
        // Still going: the latest payment is within one and a half periods.
        val periodDays = when (frequency) {
            RecurringFrequency.WEEKLY -> 7.0
            RecurringFrequency.MONTHLY -> 30.0
            RecurringFrequency.YEARLY -> 365.0
        }
        if ((today - latest.date) / DAY_MS > periodDays * 1.5) return null

        val draft = RecurringEntity(
            name = canonical[latest.title] ?: latest.title,
            amount = latest.amount,
            category = latest.category,
            frequency = frequency.name,
            startDate = FinancialCalculations.startOfDay(latest.date, timeZone),
            createdAt = today
        )
        return RecurringSuggestion(
            name = draft.name,
            amount = draft.amount,
            category = draft.category,
            frequency = frequency,
            nextDue = nextDue(draft, today, timeZone)
        )
    }

    private const val RECENT_PAYMENTS = 4
    private const val MAX_STEPS = 5000
}
