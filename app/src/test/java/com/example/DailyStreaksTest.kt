package com.example

import com.example.domain.AchievementData
import com.example.domain.Achievements
import com.example.domain.Badge
import com.example.domain.DailyStreaks
import com.example.domain.DayStreak
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyStreaksTest {

    @Test
    fun nothingCheckedInGivesNoStreak() {
        assertEquals(DayStreak.NONE, DailyStreaks.review(emptySet(), 10))
    }

    @Test
    fun consecutiveDaysBuildTheStreak() {
        val s = DailyStreaks.review(setOf(1L, 2, 3, 4, 5), 5)
        assertEquals(5, s.current)
        assertEquals(5, s.best)
        assertTrue(s.doneToday)
        assertEquals(1, s.freezes)
    }

    @Test
    fun todayNotYetCheckedInDoesNotBreakTheStreak() {
        val s = DailyStreaks.review(setOf(1L, 2, 3, 4), 5)
        assertEquals(4, s.current)
        assertFalse(s.doneToday)
    }

    @Test
    fun oneMissedDayIsCoveredByTheFreeze() {
        val s = DailyStreaks.review(setOf(1L, 2, 4), 4)
        assertEquals(3, s.current)
        assertEquals(0, s.freezes)
        assertEquals(DailyStreaks.REFILL_DAYS - 1, s.daysToFreeze) // day 4 already counts
    }

    @Test
    fun twoMissedDaysBreakTheStreak() {
        val s = DailyStreaks.review(setOf(1L, 2, 5), 5)
        assertEquals(1, s.current)
        assertEquals(2, s.best)
    }

    @Test
    fun aUsedFreezeIsEarnedBackAfterSevenDays() {
        // Day 3 is frozen; days 4..10 are seven check-ins in a row.
        val s = DailyStreaks.review(setOf(1L, 2, 4, 5, 6, 7, 8, 9, 10), 10)
        assertEquals(1, s.freezes)
        assertEquals(0, s.daysToFreeze)
    }

    @Test
    fun noSpendDayNeedsACheckInAndNoExpense() {
        // Day 2 had an expense, so only days 1 and 3 qualify; the freeze bridges day 2.
        val s = DailyStreaks.noSpend(setOf(1L, 2, 3), spendDays = setOf(2L), today = 3)
        assertEquals(2, s.current)
        assertEquals(2, s.totalDays)
        assertEquals(0, s.freezes)
    }

    @Test
    fun aQuietDayThatWasNeverCheckedInDoesNotCount() {
        assertEquals(DayStreak.NONE, DailyStreaks.noSpend(emptySet(), emptySet(), 3))
    }

    @Test
    fun stateReportsToday() {
        val state = DailyStreaks.state(setOf(4L), spendDays = setOf(4L, 5L), today = 5)
        assertFalse(state.reviewedToday)
        assertTrue(state.spentToday)
        assertEquals(1, state.review.current)
    }

    @Test
    fun dayNumberFollowsLocalTime() {
        val ms = 20_000L * 86_400_000L + 19 * 3_600_000L // 19:00 UTC = 00:30 next day in India
        assertEquals(20_000L, DailyStreaks.dayNumber(ms, TimeZone.getTimeZone("UTC")))
        assertEquals(20_001L, DailyStreaks.dayNumber(ms, TimeZone.getTimeZone("Asia/Kolkata")))
    }

    @Test
    fun streakBadgesAndXpAreEarned() {
        val progress = Achievements.compute(
            AchievementData(checkInDays = 30, noSpendDays = 7, bestCheckInStreak = 30, bestNoSpendStreak = 7),
            nowMs = 0L
        )
        assertTrue(
            progress.earned.containsAll(
                listOf(Badge.CHECK_IN_7, Badge.CHECK_IN_30, Badge.NO_SPEND_3, Badge.NO_SPEND_7)
            )
        )
        val expectedXp = 30 * Achievements.XP_CHECK_IN_DAY + 7 * Achievements.XP_NO_SPEND_DAY +
            4 * Achievements.XP_BADGE
        assertEquals(expectedXp, progress.xp)
    }
}
