package com.example.domain

import java.util.Calendar
import java.util.TimeZone

/** One outstanding amount that falls due tomorrow. */
data class DueReminder(val person: String, val amount: Double, val theyOweMe: Boolean)

/** Which lending due dates deserve a "due tomorrow" reminder. Pure Kotlin, unit tested. */
object DueReminders {

    fun dueTomorrow(
        balances: List<PersonBalance>,
        nowMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<DueReminder> {
        val tomorrowStart = Calendar.getInstance(timeZone).apply {
            timeInMillis = FinancialCalculations.startOfDay(nowMs, timeZone)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val dayAfterStart = (tomorrowStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val from = tomorrowStart.timeInMillis
        val to = dayAfterStart.timeInMillis
        return balances.flatMap { person ->
            // One reminder per person and direction, even if two amounts share the date.
            person.dues
                .filter { it.date in from until to }
                .groupBy { it.theyOweMe }
                .map { (theyOweMe, items) -> DueReminder(person.name, items.sumOf { it.amount }, theyOweMe) }
        }.sortedByDescending { it.amount }
    }
}
