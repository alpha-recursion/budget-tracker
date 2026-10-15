package com.example.domain

import java.util.TimeZone

data class DayStreak(
    /** Days in a row (a frozen day keeps the streak alive but adds nothing). */
    val current: Int,
    val best: Int,
    /** Qualifying days in total, frozen days not counted. */
    val totalDays: Int,
    /** Freezes available to cover a missed day (0 or 1). */
    val freezes: Int,
    val doneToday: Boolean,
    /** Qualifying days still needed to earn a used freeze back; 0 when one is available. */
    val daysToFreeze: Int
) {
    companion object {
        val NONE = DayStreak(0, 0, 0, 1, false, 0)
    }
}

/** What the Home check-in card shows. */
data class DailyReviewState(
    val reviewedToday: Boolean = false,
    val spentToday: Boolean = false,
    val review: DayStreak = DayStreak.NONE,
    val noSpend: DayStreak = DayStreak.NONE
)

/**
 * Daily check-in streaks. A day is a plain number (days since 1970, local time).
 *
 * - Check-in streak: days the owner tapped "I've logged everything".
 * - No-spend streak: checked-in days with no expense at all, so a forgotten day never counts.
 *
 * Today only counts once it qualifies, so a streak never breaks mid-day. One freeze covers one
 * missed day; it is earned back after [REFILL_DAYS] more qualifying days.
 */
object DailyStreaks {

    const val REFILL_DAYS = 7
    private const val DAY_MS = 86_400_000L

    fun dayNumber(ms: Long, timeZone: TimeZone = TimeZone.getDefault()): Long =
        Math.floorDiv(ms + timeZone.getOffset(ms), DAY_MS)

    fun review(reviewedDays: Set<Long>, today: Long): DayStreak = streak(reviewedDays, today)

    fun noSpend(reviewedDays: Set<Long>, spendDays: Set<Long>, today: Long): DayStreak =
        streak(reviewedDays - spendDays, today)

    fun state(
        reviewedDays: Set<Long>,
        spendDays: Set<Long>,
        today: Long
    ) = DailyReviewState(
        reviewedToday = today in reviewedDays,
        spentToday = today in spendDays,
        review = review(reviewedDays, today),
        noSpend = noSpend(reviewedDays, spendDays, today)
    )

    private fun streak(qualifying: Set<Long>, today: Long): DayStreak {
        val doneToday = today in qualifying
        // Days in the future (clock changes) are ignored.
        val days = qualifying.filter { it <= today }.toSet()
        if (days.isEmpty()) return DayStreak.NONE

        var streak = 0
        var best = 0
        var freezes = 1
        var sinceRefill = 0
        val lastDay = if (doneToday) today else today - 1
        var day = days.min()
        while (day <= lastDay) {
            if (day in days) {
                streak++
                best = maxOf(best, streak)
                if (freezes == 0 && ++sinceRefill >= REFILL_DAYS) {
                    freezes = 1
                    sinceRefill = 0
                }
            } else if (streak > 0 && freezes > 0) {
                freezes-- // the freeze keeps the streak alive through this day
                sinceRefill = 0
            } else {
                streak = 0
                sinceRefill = 0
            }
            day++
        }
        return DayStreak(
            current = streak,
            best = best,
            totalDays = days.size,
            freezes = freezes,
            doneToday = doneToday,
            daysToFreeze = if (freezes > 0) 0 else REFILL_DAYS - sinceRefill
        )
    }
}
