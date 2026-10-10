package com.example.domain

import com.example.data.local.BudgetEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.CategoryInfo
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
    val category: CategoryInfo,
    val amount: Double,
    val percentage: Double, // 0.0 to 100.0
    val transactionCount: Int
)

data class CategoryBudgetStatus(
    val categoryName: String,
    val category: CategoryInfo,
    val spent: Double,
    val budgetLimit: Double,
    val remaining: Double,
    val percentageUsed: Double, // 0.0 to 100.0+
    val isOverBudget: Boolean
)

/** Spending at one vendor (similar spellings combined). */
data class VendorSpend(
    val name: String,
    val total: Double,
    val count: Int,
    val percentage: Double // share of all expense in the input, 0.0 to 100.0
)

object FinancialCalculations {

    /**
     * Biggest vendors by total spent among [transactions]' expenses. Truncated spellings of
     * the same vendor (see [VendorMatcher]) are combined under the fullest name.
     */
    fun topVendors(transactions: List<TransactionEntity>, limit: Int = 5): List<VendorSpend> {
        val expenses = transactions.filter {
            it.type == TransactionType.EXPENSE.name && VendorMatcher.compact(it.title).isNotEmpty()
        }
        val totalExpense = expenses.sumOf { it.amount }
        if (totalExpense <= 0.0) return emptyList()
        val canonical = VendorMatcher.canonicalNames(expenses.map { it.title })
        return expenses
            .groupBy { VendorMatcher.groupKey(it.title, canonical) }
            .map { (_, items) ->
                val total = items.sumOf { it.amount }
                VendorSpend(
                    name = canonical.getValue(items.first().title),
                    total = total,
                    count = items.size,
                    percentage = total / totalExpense * 100.0
                )
            }
            .sortedByDescending { it.total }
            .take(limit)
    }


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

    fun calculateCategoryBreakdown(
        transactions: List<TransactionEntity>,
        resolve: (String) -> CategoryInfo = { CategoryInfo.resolve(it) }
    ): List<CategoryBreakdown> {
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
                category = resolve(catName),
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
        transactions: List<TransactionEntity>,
        resolve: (String) -> CategoryInfo = { CategoryInfo.resolve(it) }
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
                category = resolve(budget.category),
                spent = spent,
                budgetLimit = limit,
                remaining = remaining,
                percentageUsed = percentage,
                isOverBudget = spent > limit
            )
        }.sortedByDescending { it.percentageUsed }
    }
}
