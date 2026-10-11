package com.example

import com.example.data.local.DebtEntryEntity
import com.example.data.model.TransactionType
import com.example.domain.DebtKind
import com.example.domain.Lending
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LendingTest {

    private val day = 86_400_000L
    private val today = 100 * day
    private var nextId = 1L

    private fun entry(person: String, kind: DebtKind, amount: Double, date: Long, due: Long? = null) =
        DebtEntryEntity(
            id = nextId++,
            person = person,
            personKey = Lending.personKey(person),
            kind = kind.name,
            amount = amount,
            date = date,
            dueDate = due
        )

    @Test
    fun partialRepaymentLeavesTheRestOwed() {
        val rahul = Lending.balances(
            listOf(
                entry("Rahul", DebtKind.LENT, 2000.0, today - 10 * day),
                entry("rahul ", DebtKind.GOT_BACK, 1500.0, today - 2 * day)
            ),
            today
        ).single()
        assertEquals(500.0, rahul.balance, 0.001)
        assertTrue(rahul.theyOweMe)
        assertEquals(2, rahul.entries.size)
        assertEquals("rahul ", rahul.entries.first().person) // newest first
    }

    @Test
    fun overdueOnlyWhileThatMoneyIsStillOut() {
        val lent = entry("Rahul", DebtKind.LENT, 1000.0, today - 20 * day, due = today - 5 * day)
        val stillOut = Lending.balances(listOf(lent), today).single()
        assertEquals(today - 5 * day, stillOut.overdueSince)

        // Paid back in full: no longer overdue, and settled.
        val repaid = Lending.balances(listOf(lent, entry("Rahul", DebtKind.GOT_BACK, 1000.0, today - day)), today).single()
        assertNull(repaid.overdueSince)
        assertTrue(repaid.isSettled)
    }

    @Test
    fun repaymentsClearTheOldestLoanFirst() {
        val old = entry("Sam", DebtKind.LENT, 500.0, today - 30 * day, due = today - 10 * day)
        val recent = entry("Sam", DebtKind.LENT, 500.0, today - 3 * day, due = today + 7 * day)
        val back = entry("Sam", DebtKind.GOT_BACK, 500.0, today - day)
        val sam = Lending.balances(listOf(old, recent, back), today).single()
        assertEquals(500.0, sam.balance, 0.001)
        assertFalse(sam.isOverdue) // the overdue loan is the one that came back
        assertEquals(today + 7 * day, sam.nextDue)
    }

    @Test
    fun borrowingAndPayingBackShowWhatIOwe() {
        val priya = Lending.balances(
            listOf(
                entry("Priya", DebtKind.BORROWED, 3000.0, today - 5 * day),
                entry("Priya", DebtKind.PAID_BACK, 1000.0, today - day)
            ),
            today
        ).single()
        assertEquals(-2000.0, priya.balance, 0.001)
        assertTrue(priya.iOweThem)
    }

    @Test
    fun overpaymentFlipsTheDirection() {
        val raj = Lending.balances(
            listOf(entry("Raj", DebtKind.LENT, 100.0, today - 2 * day), entry("Raj", DebtKind.GOT_BACK, 150.0, today - day)),
            today
        ).single()
        assertEquals(-50.0, raj.balance, 0.001)
    }

    @Test
    fun totalsAndOrdering() {
        val balances = Lending.balances(
            listOf(
                entry("Settled", DebtKind.LENT, 100.0, today - 9 * day),
                entry("Settled", DebtKind.GOT_BACK, 100.0, today - 8 * day),
                entry("Small", DebtKind.LENT, 100.0, today - 7 * day),
                entry("Big", DebtKind.LENT, 900.0, today - 6 * day),
                entry("Late", DebtKind.LENT, 50.0, today - 6 * day, due = today - day),
                entry("Lender", DebtKind.BORROWED, 300.0, today - 5 * day)
            ),
            today
        )
        assertEquals(listOf("Late", "Big", "Lender", "Small", "Settled"), balances.map { it.name })
        val totals = Lending.totals(balances)
        assertEquals(1050.0, totals.owedToMe, 0.001)
        assertEquals(300.0, totals.iOwe, 0.001)
        assertEquals(1, totals.overdueCount)
    }

    @Test
    fun defaultKindFollowsTheBalance() {
        assertEquals(DebtKind.LENT, Lending.defaultKind(TransactionType.EXPENSE, 0.0))
        assertEquals(DebtKind.PAID_BACK, Lending.defaultKind(TransactionType.EXPENSE, -200.0))
        assertEquals(DebtKind.GOT_BACK, Lending.defaultKind(TransactionType.INCOME, 500.0))
        assertEquals(DebtKind.BORROWED, Lending.defaultKind(TransactionType.INCOME, 0.0))
    }

    @Test
    fun bankPayeeMatchesAnExistingPerson() {
        val people = Lending.balances(
            listOf(entry("Yogesh", DebtKind.LENT, 100.0, today), entry("Om", DebtKind.LENT, 50.0, today)),
            today
        )
        assertEquals("Yogesh", Lending.matchPerson("YOGESH BBAN SUPEKAR", people)?.name)
        assertEquals("Om", Lending.matchPerson("OM PATIL", people)?.name)
        assertNull(Lending.matchPerson("ZEPTO", people))
    }

    @Test
    fun displayNameTidiesBankCapitals() {
        assertEquals("Yogesh Bban Supekar", Lending.displayName("  YOGESH   BBAN SUPEKAR "))
        assertEquals("McDonald", Lending.displayName("McDonald"))
    }
}
