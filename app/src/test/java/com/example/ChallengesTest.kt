package com.example

import com.example.data.local.BudgetEntity
import com.example.data.local.ChallengeEntity
import com.example.data.local.GoalEntity
import com.example.data.local.GoalEntryEntity
import com.example.data.local.TransactionEntity
import com.example.domain.ChallengeState
import com.example.domain.ChallengeType
import com.example.domain.Challenges
import com.example.domain.MoneyData
import com.example.domain.ReportCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ChallengesTest {

    private val ist = TimeZone.getTimeZone("Asia/Kolkata")
    private fun at(month: Int, day: Int, hour: Int = 12) = Calendar.getInstance(ist).apply {
        clear()
        set(2026, month, day, hour, 0)
    }.timeInMillis
    private val now = at(Calendar.OCTOBER, 11)
    private val october = Challenges.month(now, ist)

    private fun spend(amount: Double, date: Long, category: String = "Food & Dining") =
        TransactionEntity(title = "x", amount = amount, type = "EXPENSE", category = category, date = date)
    private fun earn(amount: Double, date: Long) =
        TransactionEntity(title = "pay", amount = amount, type = "INCOME", category = "Salary", date = date)
    private fun challenge(type: ChallengeType, target: Double, category: String? = null) =
        ChallengeEntity(id = 1, month = october.key, type = type.name, category = category, target = target, createdAt = now)

    @Test
    fun monthKeysAndRanges() {
        assertEquals(202610, october.key)
        assertEquals(at(Calendar.OCTOBER, 1, 0), october.start)
        assertEquals(at(Calendar.NOVEMBER, 1, 0), october.end)
        assertEquals(202609, Challenges.previous(october, ist).key)
        assertEquals(202512, Challenges.previous(Challenges.monthOf(202601, ist), ist).key)
    }

    @Test
    fun suggestionsComeFromLastMonth() {
        val data = MoneyData(
            transactions = listOf(
                spend(3000.0, at(Calendar.SEPTEMBER, 5)),
                spend(1000.0, at(Calendar.SEPTEMBER, 6), "Shopping"),
                earn(20000.0, at(Calendar.SEPTEMBER, 1))
            )
        )
        val s = Challenges.suggest(data, october, emptyList(), ist)
        assertEquals(listOf(ChallengeType.CATEGORY_LIMIT, ChallengeType.SAVE_AMOUNT, ChallengeType.NO_SPEND_DAYS), s.map { it.type })
        assertEquals("Food & Dining", s[0].category)
        assertEquals(2700.0, s[0].target, 0.001)          // 90% of 3000
        assertEquals(2000.0, s[1].target, 0.001)          // 10% of income, nothing saved last month
        assertEquals(15.0, s[2].target, 0.001)            // 28 no-spend days + 2, capped at 15
        // Picking one brings in the next kind.
        val picked = listOf(challenge(ChallengeType.SAVE_AMOUNT, 2000.0))
        assertEquals(ChallengeType.TOTAL_SPEND, Challenges.suggest(data, october, picked, ist).last().type)
    }

    @Test
    fun startersWithoutHistory() {
        val s = Challenges.suggest(MoneyData(), october, emptyList(), ist)
        assertEquals(listOf(ChallengeType.SAVE_AMOUNT, ChallengeType.NO_SPEND_DAYS), s.map { it.type })
        assertEquals(1000.0, s[0].target, 0.001)
        assertEquals(5.0, s[1].target, 0.001)
    }

    @Test
    fun limitChallengesFailWhenOverAndWinAtMonthEnd() {
        val c = challenge(ChallengeType.CATEGORY_LIMIT, 2000.0, "Food & Dining")
        val data = MoneyData(transactions = listOf(spend(1500.0, at(Calendar.OCTOBER, 3)), spend(900.0, at(Calendar.OCTOBER, 4), "Shopping")))
        val active = Challenges.evaluate(c, data, now, ist)!!
        assertEquals(ChallengeState.ACTIVE, active.state)
        assertEquals(1500.0, active.current, 0.001)
        assertEquals(ChallengeState.WON, Challenges.evaluate(c, data, at(Calendar.NOVEMBER, 1, 1), ist)!!.state)
        val over = data.copy(transactions = data.transactions + spend(600.0, at(Calendar.OCTOBER, 9)))
        assertEquals(ChallengeState.FAILED, Challenges.evaluate(c, over, now, ist)!!.state)
        // Total spend counts every category.
        assertEquals(2400.0, Challenges.evaluate(challenge(ChallengeType.TOTAL_SPEND, 3000.0), data, now, ist)!!.current, 0.001)
    }

    @Test
    fun saveChallengeWinsWhenReached() {
        val goal = GoalEntity(id = 7, name = "Phone", target = 9000.0, iconKey = "x", colorHex = 0, createdAt = at(Calendar.OCTOBER, 2))
        val entries = listOf(
            GoalEntryEntity(id = 1, goalId = 7, amount = 5000.0, date = goal.createdAt), // starting amount
            GoalEntryEntity(id = 2, goalId = 7, amount = 700.0, date = at(Calendar.OCTOBER, 5)),
            GoalEntryEntity(id = 3, goalId = 7, amount = 400.0, date = at(Calendar.OCTOBER, 8))
        )
        val c = challenge(ChallengeType.SAVE_AMOUNT, 1000.0)
        val r = Challenges.evaluate(c, MoneyData(goals = listOf(goal), goalEntries = entries), now, ist)!!
        assertEquals(1100.0, r.current, 0.001)
        assertEquals(ChallengeState.WON, r.state)
        val short = Challenges.evaluate(c, MoneyData(goals = listOf(goal), goalEntries = entries.take(2)), at(Calendar.NOVEMBER, 2), ist)!!
        assertEquals(ChallengeState.FAILED, short.state)
    }

    @Test
    fun noSpendDaysCountFinishedDaysOnly() {
        // Oct 1-10 are over by Oct 11; spending on 3 of them leaves 7 no-spend days.
        val data = MoneyData(transactions = listOf(2, 5, 9, 11).map { spend(50.0, at(Calendar.OCTOBER, it)) })
        val r = Challenges.evaluate(challenge(ChallengeType.NO_SPEND_DAYS, 10.0), data, now, ist)!!
        assertEquals(7.0, r.current, 0.001)
        assertEquals(ChallengeState.ACTIVE, r.state)
        assertEquals(ChallengeState.WON, Challenges.evaluate(challenge(ChallengeType.NO_SPEND_DAYS, 7.0), data, now, ist)!!.state)
        // 21 days left (Oct 11-31) cannot reach 7 + 21 < 30.
        assertEquals(ChallengeState.FAILED, Challenges.evaluate(challenge(ChallengeType.NO_SPEND_DAYS, 29.0), data, now, ist)!!.state)
    }

    @Test
    fun reportCardGrades() {
        val september = Challenges.previous(october, ist)
        val data = MoneyData(
            transactions = listOf(
                earn(30000.0, at(Calendar.SEPTEMBER, 1)),
                spend(9000.0, at(Calendar.SEPTEMBER, 3)),
                spend(3000.0, at(Calendar.SEPTEMBER, 20), "Shopping"),
                spend(20000.0, at(Calendar.AUGUST, 10))
            ),
            budgets = listOf(BudgetEntity("Food & Dining", 10000.0), BudgetEntity("Shopping", 2000.0))
        )
        val card = ReportCards.build(data, september, emptyList(), now, ist)!!
        // 50 + 30 (60% saved, capped) + 20*1/2 - 10 (a budget missed) + 5 (spent less than August) = 85.
        assertEquals(85, card.score)
        assertEquals("A", card.grade)
        assertEquals(12000.0, card.spent, 0.001)
        assertEquals(0.6, card.savingsRate!!, 0.001)
        assertEquals(-0.4, card.spendChange!!, 0.001)
        assertEquals("Food & Dining", card.topCategory!!.first)
        assertEquals(1, card.budgetsKept)
        assertNull(ReportCards.build(data, october, emptyList(), now, ist))
        assertEquals(listOf(202609, 202608), ReportCards.monthsWithData(data.transactions, timeZone = ist).map { it.key })
        assertTrue(card.noSpendDays in 27..28)
    }
}
