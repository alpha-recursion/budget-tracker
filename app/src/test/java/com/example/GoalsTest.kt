package com.example

import com.example.data.local.GoalEntity
import com.example.data.local.GoalEntryEntity
import com.example.data.local.GoalMilestoneEntity
import com.example.domain.FinancialCalculations
import com.example.domain.GoalPace
import com.example.domain.Goals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Test
    fun forecastFollowsTheRecentPaceAndIgnoresTheStartingAmount() {
        val g = goal(10000.0, createdAt = now - 28 * day)
        val entries = listOf(
            GoalEntryEntity(id = 1, goalId = 1, amount = 5000.0, date = g.createdAt, note = "Already saved"),
            add(1000.0, date = now - 21 * day, id = 2),
            add(1000.0, date = now - 14 * day, id = 3),
            add(1000.0, date = now - 7 * day, id = 4),
            add(1000.0, date = now, id = 5)
        )
        // 4000 over 22 days (first paced deposit to today) -> 1000 left needs 6 days.
        val p = Goals.progress(g, entries, now)
        assertEquals(today + 6 * day, p.forecastDate)
    }

    @Test
    fun noForecastWithoutRecentSaving() {
        val g = goal(10000.0, createdAt = now - 10 * day)
        val onlyStart = listOf(GoalEntryEntity(id = 1, goalId = 1, amount = 2000.0, date = g.createdAt))
        assertNull(Goals.progress(g, onlyStart, now).forecastDate)
        val tookOut = onlyStart + add(500.0, date = now - 2 * day, id = 2) + add(-800.0, date = now - day, id = 3)
        assertNull(Goals.progress(g, tookOut, now).forecastDate)
        // A single deposit today is averaged over at least a week.
        val oneDeposit = Goals.progress(goal(1000.0, createdAt = now - day), listOf(add(100.0, date = now)), now)
        assertEquals(today + 63 * day, oneDeposit.forecastDate) // 900 left at 100/7 per day
    }

    @Test
    fun timelineIsTheRunningTotalPerDay() {
        val points = Goals.timeline(
            listOf(
                add(500.0, date = now - 3 * day, id = 1),
                add(200.0, date = now - 3 * day + 1000, id = 2),
                add(-100.0, date = now - day, id = 3)
            )
        )
        assertEquals(listOf(700.0, 600.0), points.map { it.saved })
        assertEquals(FinancialCalculations.startOfDay(now - 3 * day), points.first().dayMs)
    }

    @Test
    fun nextCheckpointMixesNamedAndPercentMilestones() {
        val checkpoints = listOf(
            GoalMilestoneEntity(id = 1, goalId = 1, name = "First ₹1,000", amount = 1000.0),
            GoalMilestoneEntity(id = 2, goalId = 1, name = "Deposit", amount = 6000.0)
        )
        val start = Goals.progress(goal(20000.0), listOf(add(400.0)), now, checkpoints)
        assertEquals("First ₹1,000", Goals.nextCheckpoint(start)!!.name)
        assertEquals(600.0, Goals.nextCheckpoint(start)!!.toGo, 0.001)

        val later = Goals.progress(goal(20000.0), listOf(add(5500.0)), now, checkpoints)
        assertEquals("Deposit", Goals.nextCheckpoint(later)!!.name) // 6000 comes before 50% (10000)

        val done = Goals.progress(goal(20000.0), listOf(add(20000.0)), now, checkpoints)
        assertNull(Goals.nextCheckpoint(done))
    }

    @Test
    fun reachedCheckpointsAreCelebratedOnce() {
        val checkpoints = listOf(
            GoalMilestoneEntity(id = 1, goalId = 1, name = "A", amount = 1000.0, celebrated = true),
            GoalMilestoneEntity(id = 2, goalId = 1, name = "B", amount = 2000.0),
            GoalMilestoneEntity(id = 3, goalId = 1, name = "C", amount = 3000.0)
        )
        assertEquals(listOf("B"), Goals.checkpointsToCelebrate(checkpoints, 2500.0).map { it.name })
        assertEquals(listOf("B", "C"), Goals.checkpointsToCelebrate(checkpoints, 3000.0).map { it.name })
    }

    @Test
    fun boughtGoalsAreAchievedEvenWithTheMoneyReleased() {
        val bought = goal(1000.0).copy(id = 2, name = "Bought", purchasedAt = now, purchasedAmount = 950.0)
        val entries = listOf(
            GoalEntryEntity(id = 1, goalId = 2, amount = 1000.0, date = now - day),
            GoalEntryEntity(id = 2, goalId = 2, amount = -1000.0, date = now)
        )
        val p = Goals.progress(bought, entries, now)
        assertTrue(p.isAchieved)
        assertEquals(1f, p.fraction, 0.001f)
        assertEquals(0.0, p.saved, 0.001)
        assertNull(p.pace)
        assertNotNull(p.goal.purchasedAmount)

        val active = goal(1000.0).copy(id = 3, name = "Active")
        assertEquals(listOf("Active", "Bought"), Goals.all(listOf(bought, active), entries, now).map { it.goal.name })
    }
}
