package com.example

import com.example.data.local.RecurringEntity
import com.example.data.local.TransactionEntity
import com.example.domain.Recurring
import com.example.domain.RecurringFrequency
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(y: Int, m: Int, d: Int, hour: Int = 0): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(y, m - 1, d, hour, 0, 0)
        }.timeInMillis

    private fun item(name: String, amount: Double, freq: RecurringFrequency, start: Long, active: Boolean = true) =
        RecurringEntity(
            id = name.hashCode().toLong(), name = name, amount = amount, category = "Entertainment",
            frequency = freq.name, startDate = start, active = active, createdAt = start
        )

    private fun tx(title: String, amount: Double, date: Long, type: String = "EXPENSE") =
        TransactionEntity(title = title, amount = amount, type = type, category = "Bills", date = date)

    @Test
    fun monthlyNextDueKeepsTheDayOfMonth() {
        val netflix = item("Netflix", 649.0, RecurringFrequency.MONTHLY, at(2026, 1, 31))
        // Feb has no 31st: it falls on the 28th, but March goes back to the 31st.
        assertEquals(at(2026, 2, 28), Recurring.nextDue(netflix, at(2026, 2, 1), utc))
        assertEquals(at(2026, 3, 31), Recurring.nextDue(netflix, at(2026, 3, 1), utc))
    }

    @Test
    fun aDueTodayStaysDueToday() {
        val s = item("Spotify", 119.0, RecurringFrequency.MONTHLY, at(2026, 5, 10))
        assertEquals(at(2026, 10, 10), Recurring.nextDue(s, at(2026, 10, 10), utc))
    }

    @Test
    fun futureStartIsItsOwnNextDue() {
        val s = item("Gym", 1000.0, RecurringFrequency.MONTHLY, at(2026, 12, 1))
        assertEquals(at(2026, 12, 1), Recurring.nextDue(s, at(2026, 10, 15), utc))
    }

    @Test
    fun weeklyAndYearlyStepCorrectly() {
        val weekly = item("Maid", 200.0, RecurringFrequency.WEEKLY, at(2026, 10, 1))
        assertEquals(at(2026, 10, 15), Recurring.nextDue(weekly, at(2026, 10, 9), utc))
        val yearly = item("Insurance", 12000.0, RecurringFrequency.YEARLY, at(2025, 11, 20))
        assertEquals(at(2026, 11, 20), Recurring.nextDue(yearly, at(2026, 10, 15), utc))
    }

    @Test
    fun monthCountsOnlyWhatIsStillToPay() {
        val now = at(2026, 10, 15, 10)
        val items = listOf(
            item("Netflix", 649.0, RecurringFrequency.MONTHLY, at(2026, 1, 5)), // already paid on the 5th
            item("Jio", 299.0, RecurringFrequency.MONTHLY, at(2026, 1, 20)),
            item("Old", 500.0, RecurringFrequency.MONTHLY, at(2026, 1, 25), active = false)
        )
        val month = Recurring.month(items, now, utc)
        assertEquals(299.0, month.remaining, 0.001)
        assertEquals(1, month.remainingCount)
        assertEquals("Jio", month.next?.item?.name)
        assertEquals(at(2026, 10, 20), month.next?.date)
    }

    @Test
    fun nextPaymentCanBeInNextMonth() {
        val now = at(2026, 10, 28, 9)
        val items = listOf(item("Netflix", 649.0, RecurringFrequency.MONTHLY, at(2026, 1, 5)))
        val month = Recurring.month(items, now, utc)
        assertEquals(0, month.remainingCount)
        assertEquals(at(2026, 11, 5), month.next?.date)
    }

    @Test
    fun alertsGoOutThreeDaysBefore() {
        val now = at(2026, 10, 15, 10)
        val items = listOf(
            item("Jio", 299.0, RecurringFrequency.MONTHLY, at(2026, 1, 18)),
            item("Netflix", 649.0, RecurringFrequency.MONTHLY, at(2026, 1, 19)),
            item("Paused", 99.0, RecurringFrequency.MONTHLY, at(2026, 1, 18), active = false)
        )
        val due = Recurring.dueForAlert(items, now, utc)
        assertEquals(listOf("Jio"), due.map { it.item.name })
    }

    @Test
    fun detectsAMonthlySubscription() {
        val now = at(2026, 10, 15)
        val txs = listOf(
            tx("NETFLIX", 649.0, at(2026, 7, 5)),
            tx("NETFLIX", 649.0, at(2026, 8, 5)),
            tx("NETFLIX", 649.0, at(2026, 9, 5)),
            tx("NETFLIX", 649.0, at(2026, 10, 5)),
            tx("Zomato", 300.0, at(2026, 10, 1)),
            tx("Zomato", 120.0, at(2026, 10, 9))
        )
        val found = Recurring.detect(txs, emptyList(), now, utc)
        assertEquals(1, found.size)
        assertEquals("NETFLIX", found.single().name)
        assertEquals(RecurringFrequency.MONTHLY, found.single().frequency)
        assertEquals(at(2026, 11, 5), found.single().nextDue)
    }

    @Test
    fun ignoresIrregularOrChangingPayments() {
        val now = at(2026, 10, 15)
        val irregular = listOf(
            tx("Cafe", 150.0, at(2026, 8, 2)),
            tx("Cafe", 150.0, at(2026, 8, 9)),
            tx("Cafe", 150.0, at(2026, 9, 20))
        )
        assertTrue(Recurring.detect(irregular, emptyList(), now, utc).isEmpty())
        val changing = listOf(
            tx("Power", 900.0, at(2026, 8, 5)),
            tx("Power", 2500.0, at(2026, 9, 5)),
            tx("Power", 400.0, at(2026, 10, 5))
        )
        assertTrue(Recurring.detect(changing, emptyList(), now, utc).isEmpty())
    }

    @Test
    fun skipsEndedAndAlreadyListedPayments() {
        val now = at(2026, 10, 15)
        val ended = listOf(
            tx("Hotstar", 299.0, at(2026, 2, 3)),
            tx("Hotstar", 299.0, at(2026, 3, 3)),
            tx("Hotstar", 299.0, at(2026, 4, 3))
        )
        assertTrue(Recurring.detect(ended, emptyList(), now, utc).isEmpty())

        val live = listOf(
            tx("Spotify", 119.0, at(2026, 8, 12)),
            tx("Spotify", 119.0, at(2026, 9, 12)),
            tx("Spotify", 119.0, at(2026, 10, 12))
        )
        assertNotNull(Recurring.detect(live, emptyList(), now, utc).singleOrNull())
        val listed = listOf(item("spotify", 119.0, RecurringFrequency.MONTHLY, at(2026, 8, 12)))
        assertNull(Recurring.detect(live, listed, now, utc).firstOrNull())
    }

    @Test
    fun incomeIsNeverASubscription() {
        val now = at(2026, 10, 15)
        val salary = listOf(
            tx("Salary", 50000.0, at(2026, 8, 1), "INCOME"),
            tx("Salary", 50000.0, at(2026, 9, 1), "INCOME"),
            tx("Salary", 50000.0, at(2026, 10, 1), "INCOME")
        )
        assertTrue(Recurring.detect(salary, emptyList(), now, utc).isEmpty())
    }
}
