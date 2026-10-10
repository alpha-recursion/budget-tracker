package com.example

import com.example.data.local.BudgetEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.Category
import com.example.data.model.TransactionType
import com.example.domain.FinancialCalculations
import com.example.domain.FormValidation
import com.example.ui.util.DateConversions
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testFinancialCalculations_summary() {
        val transactions = listOf(
            TransactionEntity(
                id = 1,
                title = "Salary",
                amount = 3000.0,
                type = TransactionType.INCOME.name,
                category = Category.SALARY.displayName,
                date = 1000L
            ),
            TransactionEntity(
                id = 2,
                title = "Rent",
                amount = 1000.0,
                type = TransactionType.EXPENSE.name,
                category = Category.HOUSING.displayName,
                date = 2000L
            ),
            TransactionEntity(
                id = 3,
                title = "Groceries",
                amount = 200.0,
                type = TransactionType.EXPENSE.name,
                category = Category.FOOD_DINING.displayName,
                date = 3000L
            )
        )

        val summary = FinancialCalculations.calculateSummary(transactions)
        assertEquals(3000.0, summary.totalIncome, 0.001)
        assertEquals(1200.0, summary.totalExpense, 0.001)
        assertEquals(1800.0, summary.netBalance, 0.001)
        // Savings rate = (1800 / 3000) * 100 = 60.0%
        assertEquals(60.0, summary.savingsRate, 0.001)
        assertEquals(3, summary.transactionCount)
    }

    @Test
    fun testFinancialCalculations_zeroIncome_savingsRateZero() {
        val rate = FinancialCalculations.calculateSavingsRate(0.0, 500.0)
        assertEquals(0.0, rate, 0.001)
    }

    @Test
    fun testFinancialCalculations_categoryBreakdown() {
        val transactions = listOf(
            TransactionEntity(
                id = 1,
                title = "Groceries",
                amount = 300.0,
                type = TransactionType.EXPENSE.name,
                category = Category.FOOD_DINING.displayName,
                date = 1000L
            ),
            TransactionEntity(
                id = 2,
                title = "Dinner",
                amount = 100.0,
                type = TransactionType.EXPENSE.name,
                category = Category.FOOD_DINING.displayName,
                date = 2000L
            ),
            TransactionEntity(
                id = 3,
                title = "Transit",
                amount = 100.0,
                type = TransactionType.EXPENSE.name,
                category = Category.TRANSPORTATION.displayName,
                date = 3000L
            ),
            TransactionEntity(
                id = 4,
                title = "Salary",
                amount = 2000.0,
                type = TransactionType.INCOME.name,
                category = Category.SALARY.displayName,
                date = 4000L
            )
        )

        val breakdowns = FinancialCalculations.calculateCategoryBreakdown(transactions)
        assertEquals(2, breakdowns.size)
        // Total expense = 500. Food = 400 (80%), Transport = 100 (20%)
        assertEquals(Category.FOOD_DINING.displayName, breakdowns[0].categoryName)
        assertEquals(400.0, breakdowns[0].amount, 0.001)
        assertEquals(80.0, breakdowns[0].percentage, 0.001)

        assertEquals(Category.TRANSPORTATION.displayName, breakdowns[1].categoryName)
        assertEquals(100.0, breakdowns[1].amount, 0.001)
        assertEquals(20.0, breakdowns[1].percentage, 0.001)
    }

    @Test
    fun testBudgetStatuses_overBudgetDetection() {
        val budgets = listOf(
            BudgetEntity(category = Category.FOOD_DINING.displayName, monthlyLimit = 300.0),
            BudgetEntity(category = Category.ENTERTAINMENT.displayName, monthlyLimit = 100.0)
        )

        val transactions = listOf(
            TransactionEntity(
                id = 1,
                title = "Luxury Dining",
                amount = 350.0,
                type = TransactionType.EXPENSE.name,
                category = Category.FOOD_DINING.displayName,
                date = 1000L
            ),
            TransactionEntity(
                id = 2,
                title = "Cinema",
                amount = 30.0,
                type = TransactionType.EXPENSE.name,
                category = Category.ENTERTAINMENT.displayName,
                date = 2000L
            )
        )

        val statuses = FinancialCalculations.calculateBudgetStatuses(budgets, transactions)
        val foodStatus = statuses.first { it.categoryName == Category.FOOD_DINING.displayName }
        assertTrue(foodStatus.isOverBudget)
        assertEquals(-50.0, foodStatus.remaining, 0.001)
        assertEquals(116.666, foodStatus.percentageUsed, 0.1)

        val movieStatus = statuses.first { it.categoryName == Category.ENTERTAINMENT.displayName }
        assertFalse(movieStatus.isOverBudget)
        assertEquals(70.0, movieStatus.remaining, 0.001)
        assertEquals(30.0, movieStatus.percentageUsed, 0.001)
    }

    @Test
    fun testFilterToMonth_keepsOnlyReferenceMonth() {
        val zone = TimeZone.getTimeZone("America/New_York")
        fun at(month: Int, day: Int, hour: Int = 12): Long = Calendar.getInstance(zone).apply {
            clear()
            set(2026, month, day, hour, 0, 0)
        }.timeInMillis
        fun tx(id: Long, date: Long) = TransactionEntity(
            id = id,
            title = "t$id",
            amount = 10.0,
            type = TransactionType.EXPENSE.name,
            category = Category.FOOD_DINING.displayName,
            date = date
        )

        val transactions = listOf(
            tx(1, at(Calendar.SEPTEMBER, 30, 23)), // last month
            tx(2, at(Calendar.OCTOBER, 1, 0)),     // first moment-ish of the month
            tx(3, at(Calendar.OCTOBER, 31, 23)),   // end of the month
            tx(4, at(Calendar.NOVEMBER, 1, 0))     // next month
        )

        val result = FinancialCalculations.filterToMonth(transactions, at(Calendar.OCTOBER, 10), zone)
        assertEquals(listOf(2L, 3L), result.map { it.id })
    }

    @Test
    fun testCategoryInfo_resolveAndForType() {
        val bike = com.example.data.model.CategoryInfo(
            "Bike & Fuel", "two_wheeler", 0xFF0984E3, TransactionType.EXPENSE, isCustom = true
        )
        val all = com.example.data.model.CategoryInfo.builtIns + bike

        assertEquals(bike, com.example.data.model.CategoryInfo.resolve("bike & fuel", all))
        // Enum constant names from older data still resolve to the built-in.
        assertEquals("Food & Dining", com.example.data.model.CategoryInfo.resolve("FOOD_DINING", all).name)
        // A deleted custom category keeps its name but falls back to the "Other" look.
        val unknown = com.example.data.model.CategoryInfo.resolve("Gym", all)
        assertEquals("Gym", unknown.name)
        assertEquals(Category.OTHER.iconKey, unknown.iconKey)

        val expense = com.example.data.model.CategoryInfo.forType(all, TransactionType.EXPENSE).map { it.name }
        assertTrue("Bike & Fuel" in expense)
        assertTrue("Other" in expense)
        assertFalse("Salary & Wages" in expense)
        val income = com.example.data.model.CategoryInfo.forType(all, TransactionType.INCOME).map { it.name }
        assertTrue("Other" in income)
        assertFalse("Bike & Fuel" in income)
    }

    @Test
    fun testTopVendors_combinesTruncatedNames() {
        fun tx(id: Long, title: String, amount: Double, type: String = "EXPENSE") =
            TransactionEntity(id = id, title = title, amount = amount, type = type, category = "Other", date = id)
        val result = FinancialCalculations.topVendors(
            listOf(
                tx(1, "Some Other T Center", 20.0),
                tx(2, "Some Other T C", 25.0),
                tx(3, "Some Other T Cen", 30.0),
                tx(4, "Zepto", 453.0),
                tx(5, "Dev Tea", 53.0),
                tx(6, "Salary", 50000.0, type = "INCOME") // income is not spending
            ),
            limit = 2
        )
        assertEquals(listOf("Zepto", "Some Other T Center"), result.map { it.name })
        assertEquals(75.0, result[1].total, 0.001)
        assertEquals(3, result[1].count)
        assertEquals(453.0 / 581.0 * 100.0, result[0].percentage, 0.001)
    }

    @Test
    fun testFormValidation_amount() {
        assertTrue(FormValidation.validateAmount("12.50").isValid)
        assertTrue(FormValidation.validateAmount("₹45.00").isValid)
        assertTrue(FormValidation.validateAmount("$45.00").isValid)
        assertTrue(FormValidation.validateAmount("1,200.50").isValid)
        assertEquals(45.0, FormValidation.parseAmount("₹45.00")!!, 0.001)

        assertFalse(FormValidation.validateAmount("").isValid)
        assertFalse(FormValidation.validateAmount("0").isValid)
        assertFalse(FormValidation.validateAmount("-5.00").isValid)
        assertFalse(FormValidation.validateAmount("abc").isValid)
        assertFalse(FormValidation.validateAmount("12.999").isValid) // More than 2 decimals
    }

    @Test
    fun testFormValidation_acceptsRupeeFormats() {
        assertTrue(FormValidation.validateAmount("₹1,250").isValid)
        assertTrue(FormValidation.validateAmount("Rs. 99.50").isValid)
        assertTrue(FormValidation.validateAmount("Rs.45").isValid)
        assertTrue(FormValidation.validateAmount("INR 40").isValid)
        assertEquals(1250.0, FormValidation.parseAmount("₹1,250")!!, 0.001)
        assertEquals(99.5, FormValidation.parseAmount("Rs. 99.50")!!, 0.001)
        assertFalse(FormValidation.validateAmount("₹").isValid)
    }

    @Test
    fun testFormValidation_rejectsNonDecimalNumberSyntax() {
        // All of these parse with toDoubleOrNull() but are not amounts a user should save.
        assertFalse(FormValidation.validateAmount("NaN").isValid)
        assertFalse(FormValidation.validateAmount("Infinity").isValid)
        assertFalse(FormValidation.validateAmount("1e3").isValid)
        assertFalse(FormValidation.validateAmount("0x1p3").isValid)
        assertEquals(null, FormValidation.parseAmount("NaN"))

        assertTrue(FormValidation.validateAmount(".5").isValid)
        assertEquals(0.5, FormValidation.parseAmount(".5")!!, 0.0001)
    }

    @Test
    fun testDateConversions_roundTripKeepsCalendarDayAcrossTimeZones() {
        for (zoneId in listOf("America/Los_Angeles", "Asia/Kolkata", "Pacific/Auckland", "UTC")) {
            val zone = TimeZone.getTimeZone(zoneId)
            // 23:30 local on Mar 15 2026 - a time that lands on a different UTC day in most zones.
            val local = Calendar.getInstance(zone).apply {
                clear()
                set(2026, Calendar.MARCH, 15, 23, 30, 0)
            }.timeInMillis

            val utcDay = DateConversions.localToUtcDay(local, zone)
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcDay }
            assertEquals(zoneId, 15, utc.get(Calendar.DAY_OF_MONTH))
            assertEquals(zoneId, 0, utc.get(Calendar.HOUR_OF_DAY))

            // Picking Mar 10 in the picker keeps the original time of day on Mar 10 local.
            val pickedUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(2026, Calendar.MARCH, 10)
            }.timeInMillis
            val back = Calendar.getInstance(zone).apply {
                timeInMillis = DateConversions.utcDayToLocal(pickedUtc, keepTimeOfMs = local, timeZone = zone)
            }
            assertEquals(zoneId, 10, back.get(Calendar.DAY_OF_MONTH))
            assertEquals(zoneId, 23, back.get(Calendar.HOUR_OF_DAY))
            assertEquals(zoneId, 30, back.get(Calendar.MINUTE))
        }
    }

    @Test
    fun testFormValidation_title() {
        assertTrue(FormValidation.validateTitle("Whole Foods").isValid)
        assertFalse(FormValidation.validateTitle("   ").isValid)
        assertFalse(FormValidation.validateTitle("").isValid)
        assertFalse(FormValidation.validateTitle("A".repeat(61)).isValid)
    }

    @Test
    fun testFormValidation_category() {
        assertTrue(FormValidation.validateCategory("Food & Dining").isValid)
        assertFalse(FormValidation.validateCategory(null).isValid)
        assertFalse(FormValidation.validateCategory("").isValid)
    }
}
