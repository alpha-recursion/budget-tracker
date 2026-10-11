package com.example

import com.example.data.local.BudgetEntity
import com.example.data.local.DebtEntryEntity
import com.example.data.local.GoalEntity
import com.example.data.local.GoalEntryEntity
import com.example.data.local.GoalMilestoneEntity
import com.example.data.local.TransactionEntity
import com.example.domain.AchievementData
import com.example.domain.Achievements
import com.example.domain.Badge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AchievementsTest {

    private val ist = TimeZone.getTimeZone("Asia/Kolkata")
    private fun date(month: Int, day: Int) = Calendar.getInstance(ist).apply {
        clear()
        set(2026, month, day, 12, 0)
    }.timeInMillis
    private val now = date(Calendar.OCTOBER, 11)

    private fun tx(amount: Double, category: String = "Food & Dining", date: Long = now, note: String = "") =
        TransactionEntity(title = "x", amount = amount, type = "EXPENSE", category = category, date = date, note = note)

    @Test
    fun levelsClimbWithXp() {
        assertEquals(1, Achievements.level(0).number)
        assertEquals("Penny Pincher", Achievements.level(99).name)
        assertEquals(2, Achievements.level(100).number)
        assertEquals(0.5f, Achievements.level(175).fraction(175), 0.001f) // 100..250
        assertEquals(10, Achievements.level(99_999).number)
        assertEquals(1f, Achievements.level(99_999).fraction(99_999), 0.001f)
    }

    @Test
    fun xpAddsUpFromWhatWasDone() {
        val goal = GoalEntity(id = 1, name = "Phone", target = 1000.0, iconKey = "x", colorHex = 0, createdAt = now - 1000, celebratedMilestone = 50)
        val data = AchievementData(
            transactions = List(10) { tx(10.0) },
            goals = listOf(goal),
            goalEntries = listOf(
                GoalEntryEntity(id = 1, goalId = 1, amount = 100.0, date = goal.createdAt), // starting amount: no XP
                GoalEntryEntity(id = 2, goalId = 1, amount = 400.0, date = now)
            ),
            goalMilestones = listOf(GoalMilestoneEntity(id = 1, goalId = 1, name = "A", amount = 200.0, celebrated = true)),
            bestStreak = 1
        )
        val p = Achievements.compute(data, now, ist)
        // Badges: first steps, first goal, quarter, halfway, checkpoint = 5.
        assertEquals(setOf(Badge.FIRST_STEPS, Badge.FIRST_GOAL, Badge.QUARTER, Badge.HALFWAY, Badge.CHECKPOINT), p.earned)
        val expected = 10 * 2 + 1 * 10 + 2 * 25 + 1 * 15 + 1 * 20 + 5 * 50
        assertEquals(expected, p.xp)
        assertEquals(Achievements.level(expected), p.level)
    }

    @Test
    fun goalBadges() {
        val bought = GoalEntity(id = 1, name = "Helmet", target = 3000.0, iconKey = "x", colorHex = 0, createdAt = 0, purchasedAt = now)
        val earned = Achievements.compute(
            AchievementData(
                goals = listOf(bought),
                goalEntries = listOf(GoalEntryEntity(id = 1, goalId = 1, amount = 7.0, date = now, note = "Round-up jar")),
                bestStreak = 8
            ),
            now, ist
        ).earned
        assertTrue(earned.containsAll(listOf(Badge.GOAL_GETTER, Badge.TREAT, Badge.SPARE_CHANGE, Badge.STREAK_3, Badge.STREAK_8, Badge.QUARTER)))
    }

    @Test
    fun budgetKeeperLooksAtLastFullMonth() {
        val budgets = listOf(BudgetEntity("Food & Dining", 3000.0), BudgetEntity("Shopping", 1000.0))
        val within = AchievementData(
            transactions = listOf(tx(2500.0, date = date(Calendar.SEPTEMBER, 10)), tx(5000.0, date = now)), // October doesn't count
            budgets = budgets
        )
        assertTrue(Achievements.keptLastMonthsBudgets(within, now, ist))
        val over = within.copy(transactions = within.transactions + tx(1200.0, "Shopping", date(Calendar.SEPTEMBER, 30)))
        assertFalse(Achievements.keptLastMonthsBudgets(over, now, ist))
        assertFalse(Achievements.keptLastMonthsBudgets(within.copy(budgets = emptyList()), now, ist))
        assertFalse(Achievements.keptLastMonthsBudgets(within.copy(transactions = listOf(tx(10.0, date = now))), now, ist))
    }

    @Test
    fun smsAndLendingBadges() {
        val sms = List(10) { tx(10.0, note = "From Kotak SMS · UPI Ref $it") }
        val debts = listOf(
            DebtEntryEntity(id = 1, person = "Rahul", personKey = "rahul", kind = "LENT", amount = 500.0, date = now - 10_000),
            DebtEntryEntity(id = 2, person = "Rahul", personKey = "rahul", kind = "GOT_BACK", amount = 500.0, date = now)
        )
        val earned = Achievements.compute(AchievementData(transactions = sms, debts = debts), now, ist).earned
        assertTrue(Badge.AUTO_PILOT in earned)
        assertTrue(Badge.SETTLED in earned)
        assertTrue(Badge.FIRST_STEPS in earned)
    }

    @Test
    fun onlyUnseenBadgesAreNew() {
        val p = Achievements.compute(AchievementData(transactions = List(10) { tx(1.0) }, goals = listOf(
            GoalEntity(id = 1, name = "x", target = 1.0, iconKey = "x", colorHex = 0, createdAt = 0)
        )), now, ist)
        assertEquals(listOf(Badge.FIRST_GOAL), Achievements.newBadges(p, setOf("FIRST_STEPS")))
    }
}
