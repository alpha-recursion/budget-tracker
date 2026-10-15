package com.example

import com.example.data.local.TransactionEntity
import com.example.domain.HomeSpend
import com.example.domain.SpendPeriod
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSpendTest {

    private val utc = TimeZone.getTimeZone("UTC")

    // Friday 16 October 2026, 14:00
    private val now = at(16, 14)

    private fun at(day: Int, hour: Int, month: Int = 10): Long = Calendar.getInstance(utc).apply {
        clear()
        set(2026, month - 1, day, hour, 0, 0)
    }.timeInMillis

    private fun tx(amount: Double, date: Long, type: String = "EXPENSE") =
        TransactionEntity(title = "x", amount = amount, type = type, category = "Other", date = date)

    private val txs = listOf(
        tx(100.0, at(16, 10)), // today, 9a bucket
        tx(50.0, at(16, 13)), // today, 12p bucket
        tx(200.0, at(13, 9)), // Tuesday this week
        tx(400.0, at(5, 9)), // earlier this month
        tx(999.0, at(30, 9, month = 9)), // last month
        tx(10_000.0, at(1, 9), "INCOME")
    )

    @Test
    fun todayAddsUpAndBucketsByHour() {
        val k = HomeSpend.kpi(txs, SpendPeriod.TODAY, now, utc)
        assertEquals(150.0, k.spent, 0.001)
        assertEquals(2, k.payments)
        assertEquals(100.0, k.bars.single { it.label == "9a" }.amount, 0.001)
        assertTrue(k.bars.single { it.label == "12p" }.current)
        assertEquals(750.0 / 16, k.averagePerDay, 0.001)
    }

    @Test
    fun weekRunsMondayToSunday() {
        val k = HomeSpend.kpi(txs, SpendPeriod.WEEK, now, utc)
        assertEquals(350.0, k.spent, 0.001)
        assertEquals(7, k.bars.size)
        assertTrue(k.bars[4].current) // Friday
        assertTrue(k.bars[5].future && k.bars[6].future)
        assertEquals(200.0, k.bars[1].amount, 0.001)
        assertEquals(350.0 / 5, k.perDayThisWeek, 0.001)
    }

    @Test
    fun monthCountsOnlyThisMonthAndSavingRate() {
        val k = HomeSpend.kpi(txs, SpendPeriod.MONTH, now, utc)
        assertEquals(750.0, k.spent, 0.001)
        assertEquals(10_000.0, k.monthIncome, 0.001)
        assertEquals(92.5, k.savingRate!!, 0.001)
        assertEquals(15, k.daysLeftInMonth)
        assertEquals(5, k.bars.size)
        assertTrue(k.bars[2].current) // 15–21
        assertTrue(k.bars[3].future)
    }
}
