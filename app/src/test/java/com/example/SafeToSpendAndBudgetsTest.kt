package com.example

import com.example.data.local.BudgetEntity
import com.example.data.local.RecurringEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.CategoryInfo
import com.example.domain.BudgetPlanner
import com.example.domain.PaySettings
import com.example.domain.SafeToSpendCalculator
import com.example.domain.SavingsStrategy
import com.example.domain.CycleSavings
import com.example.data.local.GoalEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SafeToSpendAndBudgetsTest {

    private val ist = TimeZone.getTimeZone("Asia/Kolkata")
    private val categories = CategoryInfo.builtIns.map { it.name }.toSet()

    private fun at(month: Int, day: Int, hour: Int = 10): Long = Calendar.getInstance(ist).apply {
        clear()
        set(2026, month, day, hour, 0, 0)
    }.timeInMillis

    private val now = at(Calendar.OCTOBER, 17)

    private fun spend(amount: Double, date: Long, category: String = "Food & Dining") =
        TransactionEntity(title = "x", amount = amount, type = "EXPENSE", category = category, date = date)

    private val rentLikeBill = RecurringEntity(
        name = "Internet", amount = 500.0, category = "Utilities & Bills", frequency = "MONTHLY",
        startDate = at(Calendar.JANUARY, 25, 0), createdAt = 0L
    )

    @Test
    fun safeToSpendSpreadsWhatIsLeftOverTheDaysToPayday() {
        // Payday on the 1st: 17-31 Oct is 15 days including today.
        val safe = SafeToSpendCalculator.compute(
            pay = PaySettings(monthlyIncome = 30_000.0, payday = 1),
            budgets = emptyList(),
            transactions = listOf(spend(3_000.0, at(Calendar.OCTOBER, 5)), spend(200.0, at(Calendar.OCTOBER, 17, 9))),
            recurring = listOf(rentLikeBill), // due 25 Oct: held back
            nowMs = now,
            timeZone = ist
        )!!
        assertEquals(15, safe.daysLeft)
        assertEquals(500.0, safe.billsHeldBack, 0.001)
        // 50/30/20: ₹6,000 is saved first and never shown as spendable.
        assertEquals((30_000.0 - 6_000.0 - 3_000.0 - 500.0) / 15, safe.dailyAllowance, 0.01)
        assertEquals(safe.dailyAllowance - 200.0, safe.today, 0.01)
        assertFalse(safe.fromBudgets)
    }

    @Test
    fun overspendingTodayShowsHowFarOver() {
        val safe = SafeToSpendCalculator.compute(
            PaySettings(15_000.0, 1), emptyList(),
            listOf(spend(5_000.0, at(Calendar.OCTOBER, 17, 9))), emptyList(), now, ist
        )!!
        assertEquals(800.0, safe.dailyAllowance, 0.01) // (15,000 - 20% saved) / 15 days
        assertEquals(-4_200.0, safe.today, 0.01)
    }

    @Test
    fun withoutIncomeMonthlyBudgetsAreUsedAndWithoutEitherNothing() {
        val safe = SafeToSpendCalculator.compute(
            PaySettings(), listOf(BudgetEntity("Food & Dining", 10_000.0), BudgetEntity("Shopping", 5_000.0)),
            listOf(spend(3_000.0, at(Calendar.OCTOBER, 5))), emptyList(), now, ist
        )!!
        assertTrue(safe.fromBudgets)
        assertEquals((15_000.0 - 3_000.0) / 15, safe.dailyAllowance, 0.01)
        assertNull(SafeToSpendCalculator.compute(PaySettings(), emptyList(), emptyList(), emptyList(), now, ist))
    }

    @Test
    fun starterBudgetsFollowTheStrategyAndKeepSavingsOut() {
        // 50/30/20: needs share ₹25,000 (rent 40% of it), wants ₹15,000, ₹10,000 saved.
        val budgets = BudgetPlanner.suggest(50_000.0, emptyList(), categories, now, ist)
        assertTrue(budgets.sumOf { it.monthlyLimit } <= 50_000.0 * 0.80)
        assertEquals(10_000.0, budgets.first { it.category == "Housing & Rent" }.monthlyLimit, 0.001)
        assertEquals(6_000.0, budgets.first { it.category == "Food & Dining" }.monthlyLimit, 0.001)
        assertTrue(budgets.all { it.monthlyLimit % 100.0 == 0.0 })

        val saveHalf = BudgetPlanner.suggest(50_000.0, emptyList(), categories, now, ist, SavingsStrategy.SAVE_HALF)
        assertTrue(saveHalf.sumOf { it.monthlyLimit } <= 25_000.0)
        assertEquals(8_000.0, saveHalf.first { it.category == "Housing & Rent" }.monthlyLimit, 0.001)
    }

    @Test
    fun unspentMoneyFromTheLastCycleIsOfferedOnce() {
        val pay = PaySettings(30_000.0, 1)
        val september = listOf(spend(20_000.0, at(Calendar.SEPTEMBER, 10)))
        val moved = listOf(GoalEntryEntity(goalId = 1, amount = 3_000.0, date = at(Calendar.SEPTEMBER, 15)))
        val leftover = CycleSavings.lastCycleLeftover(pay, september, moved, now, ist)!!
        assertEquals(7_000.0, leftover.amount, 0.001)
        assertEquals(at(Calendar.SEPTEMBER, 1, 0), leftover.cycleStart)
        // Nothing recorded last cycle (e.g. the app was new): no offer.
        assertNull(CycleSavings.lastCycleLeftover(pay, emptyList(), emptyList(), now, ist))
    }

    @Test
    fun historyBasedBudgetsTrimUsualSpendingAndRespectTheCap() {
        val september = listOf(
            spend(4_000.0, at(Calendar.SEPTEMBER, 10)),
            spend(1_000.0, at(Calendar.SEPTEMBER, 12), "Tea & Snacks"),
            spend(9_999.0, at(Calendar.OCTOBER, 2)) // this month: not part of the usual spend
        )
        val roomy = BudgetPlanner.suggest(50_000.0, september, categories, now, ist).associate { it.category to it.monthlyLimit }
        assertEquals(mapOf("Food & Dining" to 3_600.0, "Tea & Snacks" to 900.0), roomy)

        // A small salary: scaled down so 30% is still saved.
        val tight = BudgetPlanner.suggest(4_000.0, september, categories, now, ist)
        assertTrue(tight.sumOf { it.monthlyLimit } <= 4_000.0 * 0.30) // wants share only
        assertTrue(BudgetPlanner.suggest(0.0, september, categories, now, ist).isEmpty())
    }
}
