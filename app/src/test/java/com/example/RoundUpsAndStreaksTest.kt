package com.example

import com.example.data.local.GoalEntity
import com.example.data.local.GoalEntryEntity
import com.example.data.local.TransactionEntity
import com.example.domain.FinancialCalculations
import com.example.domain.RoundUpSettings
import com.example.domain.RoundUps
import com.example.domain.Streaks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundUpsAndStreaksTest {

    private val day = 86_400_000L
    // A Wednesday at midday, so week boundaries are unambiguous.
    private val monday = FinancialCalculations.startOfWeek(1_790_000_000_000L)
    private val now = monday + 2 * day + 12 * 3_600_000L

    private fun expense(amount: Double, date: Long) =
        TransactionEntity(title = "x", amount = amount, type = "EXPENSE", category = "Other", date = date)

    @Test
    fun roundUpToTheChosenUnit() {
        assertEquals(7.0, RoundUps.roundUp(453.0, 10), 0.001)
        assertEquals(47.0, RoundUps.roundUp(453.0, 50), 0.001)
        assertEquals(47.0, RoundUps.roundUp(453.0, 100), 0.001)
        assertEquals(0.0, RoundUps.roundUp(450.0, 10), 0.001)
        assertEquals(0.5, RoundUps.roundUp(29.5, 10), 0.001)
    }

    @Test
    fun jarCollectsExpensesSinceTurningOnMinusWhatWasMoved() {
        val txs = listOf(
            expense(453.0, now - 2 * day), // before turning on: ignored
            expense(30.0, now),            // nothing to round
            expense(53.0, now + 1000),     // +7
            expense(45.0, now + 2000),     // +5
            TransactionEntity(title = "pay", amount = 1001.0, type = "INCOME", category = "Salary", date = now + 3000)
        )
        val on = RoundUps.setEnabled(RoundUpSettings(), true, txs, now)
        assertEquals(12.0, RoundUps.jar(on, txs), 0.001)
        val afterMove = RoundUps.emptied(on, 12.0)
        assertEquals(0.0, RoundUps.jar(afterMove, txs), 0.001)
        assertEquals(3.0, RoundUps.jar(afterMove, txs + expense(17.0, now + 4000)), 0.001)
    }

    @Test
    fun changingTheUnitKeepsWhatWasCollected() {
        val txs = listOf(expense(53.0, now + 1000))
        val on = RoundUps.setEnabled(RoundUpSettings(), true, emptyList(), now)
        val changed = RoundUps.changeUnit(on, 100, txs, now + 5000)
        assertEquals(7.0, RoundUps.jar(changed, txs), 0.001) // old expense keeps its ₹10 round-up
        assertEquals(7.0 + 67.0, RoundUps.jar(changed, txs + expense(33.0, now + 6000)), 0.001)
    }

    @Test
    fun turningOffKeepsTheJarUntilMoved() {
        val txs = listOf(expense(53.0, now + 1000))
        val off = RoundUps.setEnabled(RoundUps.setEnabled(RoundUpSettings(), true, emptyList(), now), false, txs, now + 2000)
        assertFalse(off.enabled)
        assertEquals(7.0, RoundUps.jar(off, txs + expense(11.0, now + 3000)), 0.001) // no longer collecting
        assertEquals(0.0, RoundUps.jar(RoundUps.emptied(off, 7.0), txs), 0.001)
    }

    private val goal = GoalEntity(id = 1, name = "Phone", target = 10000.0, iconKey = "savings", colorHex = 0, createdAt = monday - 60 * day)

    private fun savedInWeeksAgo(vararg weeksAgo: Int) = weeksAgo.mapIndexed { i, w ->
        GoalEntryEntity(id = i + 1L, goalId = 1, amount = 100.0, date = monday - w * 7 * day + day)
    }

    @Test
    fun consecutiveWeeksBuildTheStreak() {
        val s = Streaks.saving(listOf(goal), savedInWeeksAgo(3, 2, 1, 0), now)
        assertEquals(4, s.current)
        assertTrue(s.savedThisWeek)
        assertEquals(1, s.freezes)
    }

    @Test
    fun thisWeekDoesNotBreakTheStreakBeforeItEnds() {
        val s = Streaks.saving(listOf(goal), savedInWeeksAgo(2, 1), now)
        assertEquals(2, s.current)
        assertFalse(s.savedThisWeek)
    }

    @Test
    fun aFreezeCoversOneMissedWeek() {
        // Saved 4 and 3 weeks ago, missed 2 weeks ago, saved last week.
        val s = Streaks.saving(listOf(goal), savedInWeeksAgo(4, 3, 1), now)
        assertEquals(3, s.current)
        assertEquals(0, s.freezes)
        assertEquals(3, s.weeksToFreeze) // one saving week since the freeze was used
    }

    @Test
    fun twoMissedWeeksResetTheStreakButKeepTheBest() {
        val s = Streaks.saving(listOf(goal), savedInWeeksAgo(6, 5, 4, 1), now)
        assertEquals(1, s.current)
        assertEquals(3, s.best)
    }

    @Test
    fun freezeIsEarnedBackAfterFourSavingWeeks() {
        // Missed 6 weeks ago (freeze used), then saved 4 weeks in a row: the freeze is back.
        val s = Streaks.saving(listOf(goal), savedInWeeksAgo(7, 5, 4, 3, 2, 1), now)
        assertEquals(6, s.current)
        assertEquals(1, s.freezes)
        // Missing last week as well uses that freeze again.
        val again = Streaks.saving(listOf(goal), savedInWeeksAgo(7, 5, 4, 3, 2), now)
        assertEquals(5, again.current)
        assertEquals(0, again.freezes)
    }

    @Test
    fun startingAmountAndWithdrawalsDoNotCount() {
        val g = goal.copy(createdAt = monday - 14 * day + day)
        val entries = listOf(
            GoalEntryEntity(id = 1, goalId = 1, amount = 5000.0, date = g.createdAt),
            GoalEntryEntity(id = 2, goalId = 1, amount = -200.0, date = monday - 7 * day + day)
        )
        assertEquals(0, Streaks.saving(listOf(g), entries, now).current)
    }
}
