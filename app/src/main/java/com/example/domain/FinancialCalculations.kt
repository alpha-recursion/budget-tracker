package com.example.domain

import com.example.data.local.BudgetEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.Category
import com.example.data.model.TransactionType
import java.util.Calendar
import java.util.TimeZone

data class FinancialSummary(
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val netBalance: Double = 0.0,
    val savingsRate: Double = 0.0,
    val transactionCount: Int = 0
)

data class CategoryBreakdown(
    val categoryName: String,
    val category: Category,
    val amount: Double,
    val percentage: Double, // 0.0 to 100.0
    val transactionCount: Int
)

data class CategoryBudgetStatus(
    val categoryName: String,
    val category: Category,
    val spent: Double,
    val budgetLimit: Double,
    val remaining: Double,
    val percentageUsed: Double, // 0.0 to 100.0+
    val isOverBudget: Boolean
)

object FinancialCalculations {

    fun calculateTotalIncome(transactions: List<TransactionEntity>): Double {
        return transactions
            .filter { it.type == TransactionType.INCOME.name }
            .sumOf { it.amount }
    }

    fun calculateTotalExpense(transactions: List<TransactionEntity>): Double {
        return transactions
            .filter { it.type == TransactionType.EXPENSE.name }
            .sumOf { it.amount }
    }

    fun calculateNetBalance(transactions: List<TransactionEntity>): Double {
        return calculateTotalIncome(transactions) - calculateTotalExpense(transactions)
    }

    fun calculateSavingsRate(totalIncome: Double, totalExpense: Double): Double {
        if (totalIncome <= 0.0) return 0.0
        val savings = totalIncome - totalExpense
        if (savings <= 0.0) return 0.0
        val rate = (savings / totalIncome) * 100.0
        return rate.coerceIn(0.0, 100.0)
    }

    fun calculateSummary(transactions: List<TransactionEntity>): FinancialSummary {
        val income = calculateTotalIncome(transactions)
        val expense = calculateTotalExpense(transactions)
        val balance = income - expense
        val savingsRate = calculateSavingsRate(income, expense)
        return FinancialSummary(
            totalIncome = income,
            totalExpense = expense,
            netBalance = balance,
            savingsRate = savingsRate,
            transactionCount = transactions.size
        )
    }

    fun calculateCategoryBreakdown(transactions: List<TransactionEntity>): List<CategoryBreakdown> {
        val expenseTransactions = transactions.filter { it.type == TransactionType.EXPENSE.name }
        val totalExpense = expenseTransactions.sumOf { it.amount }

        if (expenseTransactions.isEmpty()) {
            return emptyList()
        }

        val grouped = expenseTransactions.groupBy { it.category }

        return grouped.map { (catName, items) ->
            val amount = items.sumOf { it.amount }
            val percentage = if (totalExpense > 0.0) {
                (amount / totalExpense) * 100.0
            } else {
                0.0
            }
            CategoryBreakdown(
                categoryName = catName,
                category = Category.fromDisplayName(catName),
                amount = amount,
                percentage = percentage,
                transactionCount = items.size
            )
        }.sortedByDescending { it.amount }
    }

    /** Transactions dated in the same calendar month as [referenceMs] (local time). */
    fun filterToMonth(
        transactions: List<TransactionEntity>,
        referenceMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<TransactionEntity> {
        val start = Calendar.getInstance(timeZone).apply {
            timeInMillis = referenceMs
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        val startMs = start.timeInMillis
        val endMs = end.timeInMillis
        return transactions.filter { it.date in startMs until endMs }
    }

    fun calculateBudgetStatuses(
        budgets: List<BudgetEntity>,
        transactions: List<TransactionEntity>
    ): List<CategoryBudgetStatus> {
        val expenseTransactions = transactions.filter { it.type == TransactionType.EXPENSE.name }
        val spendingByCategory = expenseTransactions.groupBy { it.category }
            .mapValues { (_, items) -> items.sumOf { it.amount } }

        return budgets.map { budget ->
            val spent = spendingByCategory[budget.category] ?: 0.0
            val limit = budget.monthlyLimit
            val percentage = if (limit > 0.0) (spent / limit) * 100.0 else 0.0
            val remaining = limit - spent
            CategoryBudgetStatus(
                categoryName = budget.category,
                category = Category.fromDisplayName(budget.category),
                spent = spent,
                budgetLimit = limit,
                remaining = remaining,
                percentageUsed = percentage,
                isOverBudget = spent > limit
            )
        }.sortedByDescending { it.percentageUsed }
    }
}
