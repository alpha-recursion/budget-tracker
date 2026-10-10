package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.BudgetEntity
import com.example.data.local.CustomCategoryEntity
import com.example.data.local.PennywiseDatabase
import com.example.data.local.TransactionEntity
import com.example.data.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

// No demo data is seeded: the app starts empty so it only ever shows the user's own money.
class PennywiseRepository(
    private val database: PennywiseDatabase
) {
    private val transactionDao = database.transactionDao()
    private val budgetDao = database.budgetDao()
    private val categoryDao = database.categoryDao()

    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val allBudgets: Flow<List<BudgetEntity>> = budgetDao.getAllBudgets()
    val customCategories: Flow<List<CustomCategoryEntity>> = categoryDao.getAllCategories()

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

    suspend fun removeBudget(category: String) = withContext(Dispatchers.IO) {
        budgetDao.deleteByCategory(category)
    }

    suspend fun saveCustomCategory(category: CustomCategoryEntity) = withContext(Dispatchers.IO) {
        categoryDao.insertOrUpdate(category)
    }

    /** Deletes a custom category; its transactions move to "Other" and its budget is removed. */
    suspend fun deleteCustomCategory(name: String) = withContext(Dispatchers.IO) {
        database.withTransaction {
            transactionDao.reassignCategory(from = name, to = Category.OTHER.displayName)
            budgetDao.deleteByCategory(name)
            categoryDao.deleteByName(name)
        }
    }
}
