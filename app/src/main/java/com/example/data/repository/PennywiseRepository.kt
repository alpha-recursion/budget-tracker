package com.example.data.repository

import com.example.data.local.BudgetDao
import com.example.data.local.BudgetEntity
import com.example.data.local.TransactionDao
import com.example.data.local.TransactionEntity
import com.example.data.model.Category
import com.example.data.model.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PennywiseRepository(
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val allBudgets: Flow<List<BudgetEntity>> = budgetDao.getAllBudgets()

    init {
        coroutineScope.launch {
            try {
                seedInitialDataIfEmpty()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun seedInitialDataIfEmpty() = withContext(Dispatchers.IO) {
        if (transactionDao.getTransactionCount() == 0) {
            val now = System.currentTimeMillis()
            val oneDayMs = 86_400_000L

            val initialTransactions = listOf(
                TransactionEntity(
                    title = "Monthly Salary",
                    amount = 4500.00,
                    type = TransactionType.INCOME.name,
                    category = Category.SALARY.displayName,
                    date = now - (oneDayMs * 9),
                    note = "Direct deposit from employer"
                ),
                TransactionEntity(
                    title = "Apartment Rent",
                    amount = 1200.00,
                    type = TransactionType.EXPENSE.name,
                    category = Category.HOUSING.displayName,
                    date = now - (oneDayMs * 8),
                    note = "October rent payment"
                ),
                TransactionEntity(
                    title = "Whole Foods Groceries",
                    amount = 135.50,
                    type = TransactionType.EXPENSE.name,
                    category = Category.FOOD_DINING.displayName,
                    date = now - (oneDayMs * 6),
                    note = "Weekly grocery run"
                ),
                TransactionEntity(
                    title = "Electric & Water Bill",
                    amount = 94.20,
                    type = TransactionType.EXPENSE.name,
                    category = Category.UTILITIES.displayName,
                    date = now - (oneDayMs * 5),
                    note = "City utility payment"
                ),
                TransactionEntity(
                    title = "Freelance UI Design",
                    amount = 650.00,
                    type = TransactionType.INCOME.name,
                    category = Category.FREELANCE.displayName,
                    date = now - (oneDayMs * 4),
                    note = "Landing page design client"
                ),
                TransactionEntity(
                    title = "Metro Transit Pass",
                    amount = 75.00,
                    type = TransactionType.EXPENSE.name,
                    category = Category.TRANSPORTATION.displayName,
                    date = now - (oneDayMs * 3),
                    note = "Monthly subway card"
                ),
                TransactionEntity(
                    title = "New Headphones",
                    amount = 129.99,
                    type = TransactionType.EXPENSE.name,
                    category = Category.SHOPPING.displayName,
                    date = now - (oneDayMs * 2),
                    note = "Noise-cancelling earbuds"
                ),
                TransactionEntity(
                    title = "Movie Night & Snacks",
                    amount = 42.00,
                    type = TransactionType.EXPENSE.name,
                    category = Category.ENTERTAINMENT.displayName,
                    date = now - oneDayMs,
                    note = "Weekend cinema"
                ),
                TransactionEntity(
                    title = "Coffee & Bagel",
                    amount = 8.75,
                    type = TransactionType.EXPENSE.name,
                    category = Category.FOOD_DINING.displayName,
                    date = now,
                    note = "Morning espresso"
                )
            )
            transactionDao.insertAll(initialTransactions)
        }

        if (budgetDao.getBudgetCount() == 0) {
            val initialBudgets = listOf(
                BudgetEntity(category = Category.FOOD_DINING.displayName, monthlyLimit = 500.00),
                BudgetEntity(category = Category.HOUSING.displayName, monthlyLimit = 1300.00),
                BudgetEntity(category = Category.SHOPPING.displayName, monthlyLimit = 300.00),
                BudgetEntity(category = Category.TRANSPORTATION.displayName, monthlyLimit = 150.00),
                BudgetEntity(category = Category.UTILITIES.displayName, monthlyLimit = 200.00),
                BudgetEntity(category = Category.ENTERTAINMENT.displayName, monthlyLimit = 150.00)
            )
            budgetDao.insertAll(initialBudgets)
        }
    }

    suspend fun insertTransaction(transaction: TransactionEntity): Long = withContext(Dispatchers.IO) {
        transactionDao.insert(transaction)
    }

    suspend fun updateTransaction(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        transactionDao.update(transaction)
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        transactionDao.delete(transaction)
    }

    suspend fun deleteTransactionById(id: Long) = withContext(Dispatchers.IO) {
        transactionDao.deleteById(id)
    }

    suspend fun setBudget(category: String, limit: Double) = withContext(Dispatchers.IO) {
        budgetDao.insertOrUpdate(BudgetEntity(category = category, monthlyLimit = limit))
    }
}
