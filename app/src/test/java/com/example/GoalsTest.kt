package com.example

import com.example.data.local.GoalEntity
import com.example.data.local.GoalEntryEntity
import com.example.domain.FinancialCalculations
import com.example.domain.GoalPace
import com.example.domain.Goals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalsTest {

    private val day = 86_400_000L
    // Midday, so "today" is unambiguous in any time zone the tests run in.
    private val now = FinancialCalculations.startOfDay(1_790_000_000_000L) + 12 * 3_600_000L
    private val today = FinancialCalculations.startOfDay(now)

    private fun goal(target: Double, targetDate: Long? = null, createdAt: Long = now, celebrated: Int = 0) =
        GoalEntity(id = 1, name = "Phone", target = target, iconKey = "phone_android", colorHex = 0xFF0984E3,
            targetDate = targetDate, createdAt = createdAt, celebratedMilestone = celebrated)

    private fun add(amount: Double, date: Long = now, id: Long = 0) = GoalEntryEntity(id = id, goalId = 1, amount = amount, date = date)

    @Test
    fun milestonesAreReachedAtQuarters() {
        assertEquals(0, Goals.milestone(4999.0, 20000.0))
        assertEquals(25, Goals.milestone(5000.0, 20000.0))
        assertEquals(50, Goals.milestone(14999.0, 20000.0))
        assertEquals(75, Goals.milestone(15000.0, 20000.0))
        assertEquals(100, Goals.milestone(25000.0, 20000.0))
    }

    @Test
    fun withdrawalsReduceSavedButNeverBelowZero() {
        val p = Goals.progress(goal(1000.0), listOf(add(600.0, id = 1), add(-200.0, id = 2)), now)
        assertEquals(400.0, p.saved, 0.001)
        assertEquals(0.4f, p.fraction, 0.001f)
        assertEquals(600.0, p.remaining, 0.001)
        assertEquals(-200.0, p.entries.first().amount, 0.001) // newest first (same date, higher id)

        val emptied = Goals.progress(goal(1000.0), listOf(add(100.0, id = 1), add(-300.0, id = 2)), now)
        assertEquals(0.0, emptied.saved, 0.001)
    }

    @Test
    fun weeklyPlanSpreadsWhatIsLeftOverTheWeeksLeft() {
        // Due in 27 days -> 28 days including the target day -> 4 weeks.
        val p = Goals.progress(goal(20000.0, targetDate = today + 27 * day), listOf(add(4000.0)), now)
        assertEquals(4, p.weeksLeft)
        assertEquals(4000.0, p.weeklyNeeded!!, 0.001)
        assertEquals(GoalPace.ON_TRACK, p.pace)
    }

    @Test
    fun dueTodayStillHasThisWeek() {
        val p = Goals.progress(goal(1000.0, targetDate = today), listOf(add(400.0)), now)
        assertEquals(1, p.weeksLeft)
        assertEquals(600.0, p.weeklyNeeded!!, 0.001)
    }

    @Test
    fun behindWhenSavingLagsTheCalendar() {
        // Created 20 days ago, due in 9 days (30-day span): a steady saver would have about 2/3.
        val g = goal(3000.0, targetDate = today + 9 * day, createdAt = now - 20 * day)
        assertEquals(GoalPace.BEHIND, Goals.progress(g, listOf(add(500.0)), now).pace)
        assertEquals(GoalPace.ON_TRACK, Goals.progress(g, listOf(add(2100.0)), now).pace)
    }

    @Test
    fun missedOnceTheDateHasPassed() {
        val p = Goals.progress(goal(1000.0, targetDate = today - day, createdAt = now - 30 * day), listOf(add(100.0)), now)
        assertEquals(GoalPace.MISSED, p.pace)
        assertNull(p.weeklyNeeded)
    }

    @Test
    fun completeGoalHasNoPlan() {
        val p = Goals.progress(goal(1000.0, targetDate = today + 10 * day), listOf(add(1000.0)), now)
        assertTrue(p.isComplete)
        assertNull(p.pace)
        assertEquals(100, p.milestone)
    }

    @Test
    fun eachMilestoneIsCelebratedOnce() {
        assertEquals(50, Goals.milestoneToCelebrate(goal(1000.0, celebrated = 25), 500.0))
        assertNull(Goals.milestoneToCelebrate(goal(1000.0, celebrated = 50), 500.0))
        assertNull(Goals.milestoneToCelebrate(goal(1000.0), 100.0))
        // Jumping several milestones at once celebrates the highest.
        assertEquals(100, Goals.milestoneToCelebrate(goal(1000.0), 1000.0))
    }

    @Test
    fun unfinishedGoalsComeFirstSoonestDateFirst() {
        val a = goal(1000.0).copy(id = 1, name = "Done")
        val b = goal(1000.0, targetDate = today + 60 * day).copy(id = 2, name = "Later")
        val c = goal(1000.0, targetDate = today + 5 * day).copy(id = 3, name = "Soon")
        val d = goal(1000.0).copy(id = 4, name = "Someday")
        val entries = listOf(GoalEntryEntity(goalId = 1, amount = 1000.0, date = now))
        val ordered = Goals.all(listOf(a, b, c, d), entries, now)
        assertEquals(listOf("Soon", "Later", "Someday", "Done"), ordered.map { it.goal.name })
        assertEquals(1000.0, Goals.totalSetAside(ordered), 0.001)
        assertFalse(ordered.first().isComplete)
    }
}
