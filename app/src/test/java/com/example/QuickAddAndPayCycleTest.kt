package com.example

import com.example.data.local.GoalEntity
import com.example.data.local.GoalEntryEntity
import com.example.data.local.TransactionEntity
import com.example.domain.PayCycle
import com.example.domain.PaySettings
import com.example.domain.QuickAdd
import com.example.domain.QuickPin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class QuickAddAndPayCycleTest {

    private val ist = TimeZone.getTimeZone("Asia/Kolkata")
    private val day = 86_400_000L
    private fun at(month: Int, d: Int, hour: Int = 12) = Calendar.getInstance(ist).apply {
        clear()
        set(2026, month, d, hour, 0)
    }.timeInMillis
    private val now = at(Calendar.OCTOBER, 11)

    private fun spend(title: String, amount: Double, date: Long, category: String = "Food & Dining") =
        TransactionEntity(title = title, amount = amount, type = "EXPENSE", category = category, date = date)

    @Test
    fun frequentPayeesBecomeChipsWithTheirUsualAmount() {
        val txs = listOf(
            spend("Tea", 10.0, now - 1 * day), spend("tea ", 10.0, now - 2 * day), spend("Tea", 15.0, now - 3 * day),
            spend("Zepto", 453.0, now - day, "Shopping"), spend("Zepto", 120.0, now - 5 * day, "Groceries"),
            spend("Once", 99.0, now - day),
            spend("Old", 50.0, now - 90 * day), spend("Old", 50.0, now - 91 * day)
        )
        val chips = QuickAdd.chips(txs, emptyList(), emptySet(), now)
        assertEquals(listOf("tea", "zepto"), chips.map { it.key })
        assertEquals(10.0, chips[0].amount, 0.001)          // used twice vs once
        assertEquals(453.0, chips[1].amount, 0.001)         // tie -> the latest
        assertEquals("Shopping", chips[1].category)          // latest category
    }

    @Test
    fun pinsComeFirstAndHiddenNeverShow() {
        val txs = listOf(spend("Tea", 10.0, now - day), spend("Tea", 10.0, now - 2 * day), spend("Paan", 30.0, now - day), spend("Paan", 30.0, now - 3 * day))
        val pins = listOf(QuickPin("Petrol", 200.0, "Transportation"), QuickPin("Tea", 12.0, "Food & Dining"))
        val chips = QuickAdd.chips(txs, pins, setOf("paan"), now)
        assertEquals(listOf("petrol", "tea"), chips.map { it.key })
        assertTrue(chips.all { it.pinned })
        assertEquals(12.0, chips[1].amount, 0.001) // the pin's amount wins over history
    }

    @Test
    fun pinsSurviveEncoding() {
        val pins = listOf(QuickPin("Tea\twith friends", 10.5, "Food & Dining"), QuickPin("Petrol", 200.0, "Transportation"))
        val back = QuickAdd.decodePins(QuickAdd.encodePins(pins))
        assertEquals(listOf("Tea with friends", "Petrol"), back.map { it.title })
        assertEquals(10.5, back[0].amount, 0.001)
        assertEquals(emptyList<QuickPin>(), QuickAdd.decodePins(null))
    }

    @Test
    fun cycleRunsFromTheLastPaydayToTheNext() {
        val (start, next) = PayCycle.bounds(1, now, ist)
        assertEquals(at(Calendar.OCTOBER, 1, 0), start)
        assertEquals(at(Calendar.NOVEMBER, 1, 0), next)
        val (s2, n2) = PayCycle.bounds(25, now, ist)
        assertEquals(at(Calendar.SEPTEMBER, 25, 0), s2)
        assertEquals(at(Calendar.OCTOBER, 25, 0), n2)
        // Payday 31 in a 30-day month falls on the 30th.
        val (s3, _) = PayCycle.bounds(31, at(Calendar.OCTOBER, 1), ist)
        assertEquals(at(Calendar.SEPTEMBER, 30, 0), s3)
    }

    @Test
    fun leftIsIncomeMinusSpendingAndGoalMoney() {
        val goal = GoalEntity(id = 1, name = "Phone", target = 10000.0, iconKey = "x", colorHex = 0, createdAt = at(Calendar.SEPTEMBER, 1))
        val status = PayCycle.status(
            PaySettings(monthlyIncome = 30000.0, payday = 1),
            listOf(
                spend("Rent", 10000.0, at(Calendar.OCTOBER, 2)),
                spend("Food", 2000.0, at(Calendar.OCTOBER, 9)),
                spend("Before", 999.0, at(Calendar.SEPTEMBER, 28)),
                TransactionEntity(title = "Salary", amount = 30000.0, type = "INCOME", category = "Salary & Wages", date = at(Calendar.OCTOBER, 1, 10))
            ),
            listOf(goal),
            listOf(GoalEntryEntity(id = 1, goalId = 1, amount = 3000.0, date = at(Calendar.OCTOBER, 5))),
            now, ist
        )!!
        assertEquals(12000.0, status.spent, 0.001)
        assertEquals(3000.0, status.savedToGoals, 0.001)
        assertEquals(15000.0, status.left, 0.001)
        assertEquals(21, status.daysLeft)                   // Oct 11 .. Oct 31
        assertEquals(15000.0 / 21, status.perDay, 0.001)
        assertFalse(status.salaryDue)
        assertEquals(0.5f, status.fractionUsed, 0.001f)
    }

    @Test
    fun salaryIsDueUntilRecordedAndNothingWithoutSettings() {
        val status = PayCycle.status(PaySettings(30000.0, 10), emptyList(), emptyList(), emptyList(), now, ist)!!
        assertTrue(status.salaryDue)
        assertEquals(30, status.daysLeft) // Oct 11 .. Nov 9
        assertNull(PayCycle.status(PaySettings(), emptyList(), emptyList(), emptyList(), now, ist))
    }
}
