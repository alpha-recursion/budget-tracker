package com.example

import com.example.data.local.BudgetEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.Category
import com.example.data.model.TransactionType
import com.example.domain.FinancialCalculations
import com.example.domain.FormValidation
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
    fun testFormValidation_amount() {
        assertTrue(FormValidation.validateAmount("12.50").isValid)
        assertTrue(FormValidation.validateAmount("$45.00").isValid)
        assertTrue(FormValidation.validateAmount("1,200.50").isValid)

        assertFalse(FormValidation.validateAmount("").isValid)
        assertFalse(FormValidation.validateAmount("0").isValid)
        assertFalse(FormValidation.validateAmount("-5.00").isValid)
        assertFalse(FormValidation.validateAmount("abc").isValid)
        assertFalse(FormValidation.validateAmount("12.999").isValid) // More than 2 decimals
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
