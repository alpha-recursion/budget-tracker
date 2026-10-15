package com.example

import com.example.data.local.DebtEntryEntity
import com.example.domain.DebtKind
import com.example.domain.DueReminders
import com.example.domain.Lending
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DueRemindersTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 86_400_000L
    private val today = 100 * day
    private val now = today + 10 * 3_600_000L
    private var nextId = 1L

    private fun entry(person: String, kind: DebtKind, amount: Double, due: Long?) = DebtEntryEntity(
        id = nextId++,
        person = person,
        personKey = Lending.personKey(person),
        kind = kind.name,
        amount = amount,
        date = today - 5 * day,
        dueDate = due
    )

    private fun remind(vararg entries: DebtEntryEntity) =
        DueReminders.dueTomorrow(Lending.balances(entries.toList(), today), now, utc)

    @Test
    fun remindsOnlyForMoneyDueTomorrow() {
        val result = remind(
            entry("Rahul", DebtKind.LENT, 500.0, today + day + 3_600_000L),
            entry("Asha", DebtKind.LENT, 300.0, today),
            entry("Vik", DebtKind.LENT, 200.0, today + 2 * day)
        )
        assertEquals(1, result.size)
        assertEquals("Rahul", result.single().person)
        assertEquals(500.0, result.single().amount, 0.001)
        assertTrue(result.single().theyOweMe)
    }

    @Test
    fun remindsEvenWhenAnEarlierDueDateExistsForTheSamePerson() {
        val result = remind(
            entry("Rahul", DebtKind.LENT, 100.0, today),
            entry("Rahul", DebtKind.LENT, 400.0, today + day)
        )
        assertEquals(400.0, result.single().amount, 0.001)
    }

    @Test
    fun repaidMoneyIsNotRemindedAbout() {
        val result = remind(
            entry("Rahul", DebtKind.LENT, 500.0, today + day),
            entry("Rahul", DebtKind.GOT_BACK, 500.0, null)
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun borrowedMoneyIsFlaggedAsOwedByTheOwner() {
        val result = remind(entry("Neha", DebtKind.BORROWED, 250.0, today + day))
        assertEquals(false, result.single().theyOweMe)
    }
}
