package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.model.CategoryInfo
import com.example.data.backup.BackupData
import com.example.data.local.BudgetEntity
import com.example.data.local.CustomCategoryEntity
import com.example.data.local.DebtEntryEntity
import com.example.data.local.GoalEntity
import com.example.data.local.GoalEntryEntity
import com.example.data.local.GoalMilestoneEntity
import com.example.domain.Goals
import com.example.data.local.MerchantRuleEntity
import com.example.data.local.PennywiseDatabase
import com.example.data.local.SmsImportEntity
import com.example.data.local.SmsImportStatus
import com.example.data.local.TransactionEntity
import com.example.data.model.Category
import com.example.data.model.TransactionType
import com.example.domain.sms.SmsImportPlanner
import com.example.data.sms.toDomainOrNull
import com.example.data.sms.toEntity
import com.example.domain.sms.SmsTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    private val debtDao = database.debtDao()
    private val goalDao = database.goalDao()

    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val allBudgets: Flow<List<BudgetEntity>> = budgetDao.getAllBudgets()
    val customCategories: Flow<List<CustomCategoryEntity>> = categoryDao.getAllCategories()
    val merchantRulesFlow: Flow<List<MerchantRuleEntity>> = merchantRuleDao.getAll()
    val smsTemplates: Flow<List<SmsTemplate>> = smsTemplateDao.getAll()
        .map { rows -> rows.mapNotNull { it.toDomainOrNull() } }
    val debts: Flow<List<DebtEntryEntity>> = debtDao.getAll()
    val goals: Flow<List<GoalEntity>> = goalDao.getGoals()
    val goalEntries: Flow<List<GoalEntryEntity>> = goalDao.getEntries()
    val goalMilestones: Flow<List<GoalMilestoneEntity>> = goalDao.getMilestones()

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
                debts = debtDao.getAllOnce(),
                goals = goalDao.getGoalsOnce(),
                goalEntries = goalDao.getEntriesOnce(),
                goalMilestones = goalDao.getMilestonesOnce(),
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
            debtDao.insertAll(data.debts)
            goalDao.insertGoals(data.goals)
            goalDao.insertEntries(data.goalEntries)
            goalDao.insertMilestones(data.goalMilestones)
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
        debtDao.deleteAll()
        goalDao.deleteAllEntries()
        goalDao.deleteAllMilestones()
        goalDao.deleteAllGoals()
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

    // --- Money lent and borrowed ---

    suspend fun addDebt(entry: DebtEntryEntity): Long = withContext(Dispatchers.IO) {
        debtDao.insert(entry)
    }

    suspend fun deleteDebt(id: Long) = withContext(Dispatchers.IO) {
        debtDao.deleteById(id)
    }

    suspend fun deletePerson(personKey: String) = withContext(Dispatchers.IO) {
        debtDao.deletePerson(personKey)
    }

    /** A payment that was really a loan (or repayment): removes it from spending/income. */
    suspend fun moveTransactionToDebt(transactionId: Long, entry: DebtEntryEntity) = withContext(Dispatchers.IO) {
        database.withTransaction {
            transactionDao.deleteById(transactionId)
            smsImportDao.markMovedToLending(transactionId)
            debtDao.insert(entry)
        }
    }

    // --- Savings goals ---

    suspend fun saveGoal(goal: GoalEntity, initialAmount: Double) = withContext(Dispatchers.IO) {
        database.withTransaction {
            if (goal.id == 0L) {
                val id = goalDao.insertGoal(goal)
                if (initialAmount > 0.0) {
                    goalDao.insertEntry(GoalEntryEntity(goalId = id, amount = initialAmount, date = goal.createdAt, note = "Already saved"))
                }
            } else {
                goalDao.updateGoal(goal)
            }
        }
    }

    /**
     * Adds (or with a negative amount takes out) money and returns what to celebrate: a newly
     * reached percentage milestone and named checkpoints. They are recorded in the same
     * transaction, so each is celebrated only once.
     */
    suspend fun addToGoal(goalId: Long, amount: Double, date: Long, note: String): GoalReached =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val goal = goalDao.getGoal(goalId) ?: return@withTransaction GoalReached()
                goalDao.insertEntry(GoalEntryEntity(goalId = goalId, amount = amount, date = date, note = note))
                if (amount <= 0.0) return@withTransaction GoalReached()
                val saved = goalDao.savedFor(goalId)
                val checkpoints = Goals.checkpointsToCelebrate(goalDao.milestonesFor(goalId), saved)
                if (checkpoints.isNotEmpty()) goalDao.markCelebrated(checkpoints.map { it.id })
                val milestone = Goals.milestoneToCelebrate(goal, saved)
                if (milestone != null) {
                    goalDao.updateGoal(
                        goal.copy(
                            celebratedMilestone = milestone,
                            completedAt = goal.completedAt ?: if (milestone == 100) date else null
                        )
                    )
                }
                GoalReached(milestone, checkpoints.map { it.name })
            }
        }

    /** A named checkpoint; one that is already reached is not celebrated after the fact. */
    suspend fun addCheckpoint(goalId: Long, name: String, amount: Double) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val saved = goalDao.savedFor(goalId)
            goalDao.insertMilestone(
                GoalMilestoneEntity(goalId = goalId, name = name, amount = amount, celebrated = saved >= amount - Goals.EPSILON)
            )
        }
    }

    suspend fun deleteCheckpoint(id: Long) = withContext(Dispatchers.IO) {
        goalDao.deleteMilestone(id)
    }

    /**
     * "I bought it": records the purchase as an expense, releases the money set aside (it was
     * spent) and moves the goal to the Achieved shelf. All or nothing.
     */
    suspend fun buyGoal(goalId: Long, purchase: TransactionEntity) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val goal = goalDao.getGoal(goalId) ?: return@withTransaction
            transactionDao.insert(purchase)
            val saved = goalDao.savedFor(goalId)
            if (saved > 0.0) {
                goalDao.insertEntry(GoalEntryEntity(goalId = goalId, amount = -saved, date = purchase.date, note = "Bought"))
            }
            goalDao.updateGoal(goal.copy(purchasedAt = purchase.date, purchasedAmount = purchase.amount))
        }
    }

    suspend fun deleteGoalEntry(id: Long) = withContext(Dispatchers.IO) {
        goalDao.deleteEntry(id)
    }

    suspend fun deleteGoal(id: Long) = withContext(Dispatchers.IO) {
        database.withTransaction {
            goalDao.deleteEntriesFor(id)
            goalDao.deleteMilestonesFor(id)
            goalDao.deleteGoal(id)
        }
    }

    // --- SMS import ---

    /** Names of every category currently offered: built-in ones plus the user's own. */
    suspend fun categoryNames(): Set<String> =
        (CategoryInfo.builtIns.map { it.name } + customCategories.first().map { it.name }).toSet()

    suspend fun smsImportKeys(): Set<String> = withContext(Dispatchers.IO) {
        smsImportDao.getAllKeys().toSet()
    }

    suspend fun merchantRules(): List<MerchantRuleEntity> = withContext(Dispatchers.IO) {
        merchantRuleDao.getAllOnce()
    }

    /** Sets [category] on the given transactions (e.g. all from the same vendor). */
    suspend fun setCategory(ids: List<Long>, category: String) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) transactionDao.setCategory(ids, category)
    }

    /**
     * Remembers [category] for the vendor [title], for future SMS imports and auto-category
     * while typing. An existing rule for the same vendor (any spelling) is updated in place.
     */
    suspend fun rememberCategory(type: TransactionType, title: String, category: String) = withContext(Dispatchers.IO) {
        val exactKey = SmsImportPlanner.ruleKey(type, title) ?: return@withContext
        val rules = merchantRuleDao.getAllOnce().associateBy { it.counterpartyKey }
        val key = SmsImportPlanner.findRule(type, title, exactKey, rules)?.counterpartyKey ?: exactKey
        merchantRuleDao.insertAll(listOf(MerchantRuleEntity(key, category, isSelfTransfer = false)))
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

    suspend fun transactionsAllOnce(): List<TransactionEntity> = withContext(Dispatchers.IO) {
        transactionDao.getAllTransactionsOnce()
    }

    /**
     * Transactions not created from an SMS: what a new bank SMS may duplicate. SMS-created ones
     * are excluded, so two equal payments on one day (two ₹10 teas) are not flagged as duplicates.
     */
    suspend fun handEnteredTransactions(): List<TransactionEntity> = withContext(Dispatchers.IO) {
        val fromSms = smsImportDao.getAllOnce().mapNotNull { it.transactionId }.toHashSet()
        transactionDao.getAllTransactionsOnce().filterNot { it.id in fromSms }
    }

    /**
     * Writes an SMS import all-or-nothing: new transactions, the handled-SMS records that stop
     * them being imported again, and the remembered payee choices. Returns how many
     * transactions were created.
     */
    suspend fun importFromSms(items: List<SmsImportItem>, rules: List<MerchantRuleEntity>): Int =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                // Live capture and an in-app import can race on the same SMS; whoever commits
                // second skips it instead of creating the transaction twice.
                val recorded = smsImportDao.getAllKeys().toHashSet()
                var created = 0
                val records = items.filter { recorded.add(it.dedupeKey) }.map { item ->
                    val transactionId = item.transaction?.let {
                        created++
                        transactionDao.insert(it)
                    }
                    item.debt?.let { debtDao.insert(it.copy(smsKey = item.dedupeKey)) }
                    val status = when {
                        transactionId != null -> SmsImportStatus.IMPORTED
                        item.debt != null -> SmsImportStatus.LENDING
                        else -> SmsImportStatus.SELF_TRANSFER
                    }
                    SmsImportEntity(
                        dedupeKey = item.dedupeKey,
                        status = status.name,
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

/** What a deposit reached: a new percentage milestone and/or named checkpoints. */
data class GoalReached(val milestone: Int? = null, val checkpoints: List<String> = emptyList())

/**
 * One SMS to record: a [transaction], a lending [debt], or neither for a self transfer
 * (recorded, but not counted).
 */
data class SmsImportItem(
    val dedupeKey: String,
    val receivedAt: Long,
    val transaction: TransactionEntity?,
    val debt: DebtEntryEntity? = null
)
