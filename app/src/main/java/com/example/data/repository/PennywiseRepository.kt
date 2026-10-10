package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.backup.BackupData
import com.example.data.local.BudgetEntity
import com.example.data.local.CustomCategoryEntity
import com.example.data.local.MerchantRuleEntity
import com.example.data.local.PennywiseDatabase
import com.example.data.local.SmsImportEntity
import com.example.data.local.SmsImportStatus
import com.example.data.local.TransactionEntity
import com.example.data.model.Category
import com.example.data.sms.toDomainOrNull
import com.example.data.sms.toEntity
import com.example.domain.sms.SmsTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// No demo data is seeded: the app starts empty so it only ever shows the user's own money.
class PennywiseRepository(
    private val database: PennywiseDatabase
) {
    private val transactionDao = database.transactionDao()
    private val budgetDao = database.budgetDao()
    private val categoryDao = database.categoryDao()
    private val smsImportDao = database.smsImportDao()
    private val merchantRuleDao = database.merchantRuleDao()
    private val smsTemplateDao = database.smsTemplateDao()

    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val allBudgets: Flow<List<BudgetEntity>> = budgetDao.getAllBudgets()
    val customCategories: Flow<List<CustomCategoryEntity>> = categoryDao.getAllCategories()
    val smsTemplates: Flow<List<SmsTemplate>> = smsTemplateDao.getAll()
        .map { rows -> rows.mapNotNull { it.toDomainOrNull() } }

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

    suspend fun exportBackup(): BackupData = withContext(Dispatchers.IO) {
        database.withTransaction {
            BackupData(
                transactions = transactionDao.getAllTransactionsOnce(),
                budgets = budgetDao.getAllBudgetsOnce(),
                categories = categoryDao.getAllCategoriesOnce(),
                smsImports = smsImportDao.getAllOnce(),
                merchantRules = merchantRuleDao.getAllOnce(),
                smsTemplates = smsTemplateDao.getAllOnce(),
                exportedAt = System.currentTimeMillis()
            )
        }
    }

    /** Replaces everything with the backup's contents, all-or-nothing. */
    suspend fun restoreBackup(data: BackupData) = withContext(Dispatchers.IO) {
        database.withTransaction {
            clearAllTables()
            categoryDao.insertAll(data.categories)
            budgetDao.insertAll(data.budgets)
            transactionDao.insertAll(data.transactions)
            smsImportDao.insertAll(data.smsImports)
            merchantRuleDao.insertAll(data.merchantRules)
            smsTemplateDao.insertAll(data.smsTemplates)
        }
    }

    suspend fun eraseAllData() = withContext(Dispatchers.IO) {
        database.withTransaction { clearAllTables() }
    }

    private suspend fun clearAllTables() {
        transactionDao.deleteAll()
        budgetDao.deleteAll()
        categoryDao.deleteAll()
        smsImportDao.deleteAll()
        merchantRuleDao.deleteAll()
        smsTemplateDao.deleteAll()
    }

    /** Deletes a custom category; its transactions move to "Other" and its budget is removed. */
    suspend fun deleteCustomCategory(name: String) = withContext(Dispatchers.IO) {
        database.withTransaction {
            transactionDao.reassignCategory(from = name, to = Category.OTHER.displayName)
            merchantRuleDao.reassignCategory(from = name, to = Category.OTHER.displayName)
            budgetDao.deleteByCategory(name)
            categoryDao.deleteByName(name)
        }
    }

    // --- SMS import ---

    suspend fun smsImportKeys(): Set<String> = withContext(Dispatchers.IO) {
        smsImportDao.getAllKeys().toSet()
    }

    suspend fun merchantRules(): List<MerchantRuleEntity> = withContext(Dispatchers.IO) {
        merchantRuleDao.getAllOnce()
    }

    suspend fun smsTemplatesOnce(): List<SmsTemplate> = withContext(Dispatchers.IO) {
        smsTemplateDao.getAllOnce().mapNotNull { it.toDomainOrNull() }
    }

    suspend fun saveSmsTemplate(template: SmsTemplate): Long = withContext(Dispatchers.IO) {
        smsTemplateDao.insert(template.toEntity(createdAt = System.currentTimeMillis()))
    }

    suspend fun deleteSmsTemplate(id: Long) = withContext(Dispatchers.IO) {
        smsTemplateDao.deleteById(id)
    }

    suspend fun transactionsOnce(): List<TransactionEntity> = withContext(Dispatchers.IO) {
        transactionDao.getAllTransactionsOnce()
    }

    /**
     * Writes an SMS import all-or-nothing: new transactions, the handled-SMS records that stop
     * them being imported again, and the remembered payee choices. Returns how many
     * transactions were created.
     */
    suspend fun importFromSms(items: List<SmsImportItem>, rules: List<MerchantRuleEntity>): Int =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                var created = 0
                val records = items.map { item ->
                    val transactionId = item.transaction?.let {
                        created++
                        transactionDao.insert(it)
                    }
                    SmsImportEntity(
                        dedupeKey = item.dedupeKey,
                        status = if (transactionId != null) SmsImportStatus.IMPORTED.name else SmsImportStatus.SELF_TRANSFER.name,
                        transactionId = transactionId,
                        receivedAt = item.receivedAt
                    )
                }
                smsImportDao.insertAll(records)
                merchantRuleDao.insertAll(rules)
                created
            }
        }
}

/** One SMS to record; [transaction] is null for a self transfer (recorded, but not counted). */
data class SmsImportItem(
    val dedupeKey: String,
    val receivedAt: Long,
    val transaction: TransactionEntity?
)
