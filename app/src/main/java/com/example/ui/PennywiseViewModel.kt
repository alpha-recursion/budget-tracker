package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.backup.BackupData
import com.example.data.backup.BackupSerializer
import com.example.data.backup.InvalidBackupException
import com.example.data.local.BudgetEntity
import com.example.data.local.CustomCategoryEntity
import com.example.data.local.MerchantRuleEntity
import com.example.data.local.PennywiseDatabase
import com.example.data.local.TransactionEntity
import com.example.data.model.Category
import com.example.data.model.CategoryInfo
import com.example.data.model.TransactionType
import com.example.data.repository.PennywiseRepository
import com.example.domain.CategoryBreakdown
import com.example.domain.CategoryBudgetStatus
import com.example.domain.FinancialCalculations
import com.example.domain.FinancialSummary
import com.example.domain.CategoryPropagation
import com.example.domain.FormValidation
import com.example.domain.VendorSpend
import com.example.data.sms.AutoCaptureNotifier
import com.example.data.sms.AutoCaptureStore
import com.example.domain.sms.CaptureState
import com.example.domain.DebtKind
import com.example.domain.GoalProgress
import com.example.domain.Goals
import com.example.domain.RoundUpSettings
import com.example.domain.RoundUps
import com.example.domain.SavingStreak
import com.example.domain.Streaks
import com.example.data.local.RoundUpStore
import com.example.ui.util.Formatters
import com.example.data.local.GoalEntity
import com.example.domain.Lending
import com.example.domain.PersonBalance
import com.example.data.local.DebtEntryEntity
import com.example.domain.sms.SmsImportPlanner
import com.example.domain.sms.SmsTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

enum class SortOption(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    HIGHEST_AMOUNT("Highest ₹"),
    LOWEST_AMOUNT("Lowest ₹")
}

enum class Overlay { SMS_IMPORT, LENDING, GOALS }

const val SETTINGS_TAB = 3

/** Offer to give other transactions from the same vendor the category just chosen. */
data class SimilarCategoryPrompt(
    val category: String,
    val vendorName: String,
    val transactions: List<TransactionEntity>
)

class PennywiseViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: PennywiseRepository = PennywiseRepository(
        PennywiseDatabase.getDatabase(application)
    )
) : AndroidViewModel(application) {

    companion object {
        fun Factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PennywiseViewModel(application) as T
                }
            }
    }

    // Tab Navigation: 0 = Home, 1 = Transactions, 2 = Insights, 3 = Settings
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab

    // Full screens shown on top of the tabs; only one can be open at a time.
    private val _overlay = MutableStateFlow<Overlay?>(null)
    val overlay: StateFlow<Overlay?> = _overlay

    // Built-in categories followed by the user's own. Eager so the list is ready for the form
    // the first time it opens.
    val categories: StateFlow<List<CategoryInfo>> = repository.customCategories
        .map { custom ->
            CategoryInfo.builtIns + custom.map { entity ->
                CategoryInfo(
                    name = entity.name,
                    iconKey = entity.iconKey,
                    colorHex = entity.colorHex,
                    type = if (entity.type == TransactionType.INCOME.name) TransactionType.INCOME else TransactionType.EXPENSE,
                    isCustom = true
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = CategoryInfo.builtIns
        )

    // Filter & Search
    private val _filterType = MutableStateFlow<String?>(null) // null = All, "EXPENSE", "INCOME"
    val filterType: StateFlow<String?> = _filterType

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter

    private val _sortOption = MutableStateFlow(SortOption.NEWEST)
    val sortOption: StateFlow<SortOption> = _sortOption

    // Transactions from Room. Null until the first query result arrives so the UI can tell
    // "still loading" apart from "no transactions" and avoid flashing empty states.
    private val loadedTransactions: StateFlow<List<TransactionEntity>?> = repository.allTransactions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val isDataLoaded: StateFlow<Boolean> = loadedTransactions
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val allTransactions: StateFlow<List<TransactionEntity>> = loadedTransactions
        .map { it.orEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allBudgets: StateFlow<List<BudgetEntity>> = repository.allBudgets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Derived Financial Summary directly from Room
    val financialSummary: StateFlow<FinancialSummary> = allTransactions
        .map { txs -> FinancialCalculations.calculateSummary(txs) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FinancialSummary()
        )

    // Category Spending Breakdown
    val categoryBreakdowns: StateFlow<List<CategoryBreakdown>> = combine(allTransactions, categories) { txs, cats ->
        FinancialCalculations.calculateCategoryBreakdown(txs) { CategoryInfo.resolve(it, cats) }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Category Budget Statuses. Budgets are monthly limits, so only this month's spending counts;
    // comparing against all-time spending pushed every budget over its limit over time.
    val budgetStatuses: StateFlow<List<CategoryBudgetStatus>> = combine(
        allBudgets,
        allTransactions,
        categories
    ) { budgets, txs, cats ->
        val thisMonth = FinancialCalculations.filterToMonth(txs, System.currentTimeMillis())
        FinancialCalculations.calculateBudgetStatuses(budgets, thisMonth) { CategoryInfo.resolve(it, cats) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Filtered Transactions for Transactions screen
    val filteredTransactions: StateFlow<List<TransactionEntity>> = combine(
        allTransactions,
        _filterType,
        _searchQuery,
        _selectedCategoryFilter,
        _sortOption
    ) { txs, type, query, category, sort ->
        val filtered = txs.filter { tx ->
            val matchesType = type == null || tx.type.equals(type, ignoreCase = true)
            val matchesQuery = query.isBlank() ||
                    tx.title.contains(query, ignoreCase = true) ||
                    tx.category.contains(query, ignoreCase = true) ||
                    tx.note.contains(query, ignoreCase = true)
            val matchesCategory = category == null || tx.category.equals(category, ignoreCase = true)
            matchesType && matchesQuery && matchesCategory
        }
        when (sort) {
            SortOption.NEWEST -> filtered.sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.id })
            SortOption.OLDEST -> filtered.sortedWith(compareBy<TransactionEntity> { it.date }.thenBy { it.id })
            SortOption.HIGHEST_AMOUNT -> filtered.sortedByDescending { it.amount }
            SortOption.LOWEST_AMOUNT -> filtered.sortedBy { it.amount }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Form State (Add / Edit)
    private val _isAddEditOpen = MutableStateFlow(false)
    val isAddEditOpen: StateFlow<Boolean> = _isAddEditOpen

    private val _editingTransaction = MutableStateFlow<TransactionEntity?>(null)
    val editingTransaction: StateFlow<TransactionEntity?> = _editingTransaction

    private val _formType = MutableStateFlow(TransactionType.EXPENSE)
    val formType: StateFlow<TransactionType> = _formType

    private val _formAmount = MutableStateFlow("")
    val formAmount: StateFlow<String> = _formAmount

    private val _formTitle = MutableStateFlow("")
    val formTitle: StateFlow<String> = _formTitle

    private val _formCategory = MutableStateFlow(Category.FOOD_DINING.displayName)
    val formCategory: StateFlow<String> = _formCategory

    private val _formDate = MutableStateFlow(System.currentTimeMillis())
    val formDate: StateFlow<Long> = _formDate

    private val _formNote = MutableStateFlow("")
    val formNote: StateFlow<String> = _formNote

    private val _amountError = MutableStateFlow<String?>(null)
    val amountError: StateFlow<String?> = _amountError

    private val _titleError = MutableStateFlow<String?>(null)
    val titleError: StateFlow<String?> = _titleError

    private val _categoryError = MutableStateFlow<String?>(null)
    val categoryError: StateFlow<String?> = _categoryError

    // True while a save is being written, so a double tap cannot insert the transaction twice.
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    // True once the user picks a category in the open form; auto-category never overrides that.
    private var formCategoryChosenByUser = false

    // Remembered vendor -> category choices (from SMS imports and saved transactions).
    private val merchantRules: StateFlow<List<MerchantRuleEntity>> = repository.merchantRulesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Where the most money went this month, by vendor (similar spellings combined). */
    val topVendors: StateFlow<List<VendorSpend>> = allTransactions
        .map { txs -> FinancialCalculations.topVendors(FinancialCalculations.filterToMonth(txs, System.currentTimeMillis())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** How often each category is used, for ordering the category picker. */
    val categoryUsage: StateFlow<Map<String, Int>> = allTransactions
        .map { txs -> txs.groupingBy { it.category }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _similarPrompt = MutableStateFlow<SimilarCategoryPrompt?>(null)
    val similarPrompt: StateFlow<SimilarCategoryPrompt?> = _similarPrompt

    // Delete Confirmation
    private val _transactionToDelete = MutableStateFlow<TransactionEntity?>(null)
    val transactionToDelete: StateFlow<TransactionEntity?> = _transactionToDelete

    // Navigation and Filters
    fun setTab(index: Int) {
        _overlay.value = null
        _selectedTab.value = index
    }

    /** Settings is the fourth tab. */
    fun openSettings() {
        setTab(SETTINGS_TAB)
    }

    fun openSmsImport() {
        _overlay.value = Overlay.SMS_IMPORT
    }

    // Live capture of bank SMS: the Settings switch and what it did while the app was closed.
    private val captureStore = AutoCaptureStore(application)
    val autoCaptureEnabled: StateFlow<Boolean> = captureStore.updates
        .map { it.first }
        .stateIn(viewModelScope, SharingStarted.Eagerly, captureStore.enabled)
    val captureState: StateFlow<CaptureState> = captureStore.updates
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.Eagerly, captureStore.state())

    fun setAutoCapture(enabled: Boolean) {
        captureStore.enabled = enabled
    }

    /** The app is in front: auto-added payments are visible in the list, so the notification can go. */
    fun onAppForeground() {
        val app = getApplication<Application>()
        AutoCaptureNotifier.cancel(app)
        viewModelScope.launch { captureStore.update { it.seen() } }
    }

    fun dismissPendingCapture() {
        viewModelScope.launch { captureStore.update { it.dismissPending() } }
    }

    // --- Money lent and borrowed (kept out of spending and income) ---

    /** Everyone the owner lent to or borrowed from; recomputed whenever an entry changes. */
    val lendingBalances: StateFlow<List<PersonBalance>> = repository.debts
        .map { entries -> Lending.balances(entries, FinancialCalculations.startOfDay(System.currentTimeMillis())) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Person whose history is open on the Money lent screen; null shows the list. */
    private val _lendingPerson = MutableStateFlow<String?>(null)
    val lendingPerson: StateFlow<String?> = _lendingPerson

    fun openLending(personKey: String? = null) {
        _lendingPerson.value = personKey
        _overlay.value = Overlay.LENDING
    }

    fun showLendingPerson(personKey: String?) {
        _lendingPerson.value = personKey
    }

    fun addDebt(person: String, kind: DebtKind, amount: Double, date: Long, dueDate: Long?, note: String) {
        val name = Lending.displayName(person)
        if (name.isEmpty() || amount <= 0.0) return
        viewModelScope.launch {
            try {
                repository.addDebt(
                    DebtEntryEntity(
                        person = name,
                        personKey = Lending.personKey(name),
                        kind = kind.name,
                        amount = amount,
                        date = date,
                        dueDate = dueDate.takeIf { kind.canHaveDueDate },
                        note = note.trim()
                    )
                )
            } catch (e: Exception) {
                showMessage("Could not save. Please try again.")
            }
        }
    }

    /** Records a repayment of whatever is still outstanding, in either direction. */
    fun settleUp(person: PersonBalance) {
        if (person.isSettled) return
        val kind = if (person.theyOweMe) DebtKind.GOT_BACK else DebtKind.PAID_BACK
        addDebt(person.name, kind, kotlin.math.abs(person.balance), System.currentTimeMillis(), null, "Settled up")
        showMessage("Settled up with ${person.name}")
    }

    fun deleteDebt(id: Long) {
        viewModelScope.launch { repository.deleteDebt(id) }
    }

    fun deletePerson(personKey: String) {
        if (_lendingPerson.value == personKey) _lendingPerson.value = null
        viewModelScope.launch { repository.deletePerson(personKey) }
    }

    // --- Savings goals (money set aside, not spending) ---

    val goals: StateFlow<List<GoalProgress>> = combine(
        repository.goals,
        repository.goalEntries,
        repository.goalMilestones
    ) { goals, entries, milestones ->
        Goals.all(goals, entries, System.currentTimeMillis(), milestones)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Goal whose history is open on the Goals screen; null shows all goals. */
    private val _selectedGoal = MutableStateFlow<Long?>(null)
    val selectedGoal: StateFlow<Long?> = _selectedGoal

    /** Something just reached: a percentage [milestone] or a named [checkpoint]. */
    data class Celebration(val goalName: String, val milestone: Int? = null, val checkpoint: String? = null)

    // Several can be reached by one deposit; they are shown one after another.
    private val celebrationQueue = MutableStateFlow<List<Celebration>>(emptyList())
    val celebration: StateFlow<Celebration?> = celebrationQueue
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun openGoals(goalId: Long? = null) {
        _selectedGoal.value = goalId
        _overlay.value = Overlay.GOALS
    }

    fun showGoal(goalId: Long?) {
        _selectedGoal.value = goalId
    }

    /** Creates a goal ([id] = 0) or edits one; [alreadySaved] is only used for a new goal. */
    fun saveGoal(id: Long, name: String, target: Double, iconKey: String, colorHex: Long, targetDate: Long?, alreadySaved: Double) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || target <= 0.0) return
        val existing = goals.value.firstOrNull { it.goal.id == id }
        val goal = if (existing == null) {
            val now = System.currentTimeMillis()
            GoalEntity(
                name = trimmed,
                target = target,
                iconKey = iconKey,
                colorHex = colorHex,
                targetDate = targetDate,
                createdAt = now,
                // Money already saved when the goal is made is not a milestone to celebrate.
                celebratedMilestone = Goals.milestone(alreadySaved, target),
                completedAt = if (alreadySaved >= target) now else null
            )
        } else {
            // A new target can move milestones: celebrate again only what is newly reached.
            val reached = Goals.milestone(existing.saved, target)
            existing.goal.copy(
                name = trimmed,
                target = target,
                iconKey = iconKey,
                colorHex = colorHex,
                targetDate = targetDate,
                celebratedMilestone = minOf(existing.goal.celebratedMilestone, reached),
                completedAt = existing.goal.completedAt.takeIf { reached == 100 }
            )
        }
        viewModelScope.launch {
            try {
                repository.saveGoal(goal, if (existing == null) alreadySaved else 0.0)
            } catch (e: Exception) {
                showMessage("Could not save the goal. Please try again.")
            }
        }
    }

    /** Puts money into a goal (or takes it out with [takeOut]); celebrates a new milestone. */
    fun moveGoalMoney(goalId: Long, amount: Double, takeOut: Boolean, note: String) {
        if (amount <= 0.0) return
        val name = goals.value.firstOrNull { it.goal.id == goalId }?.goal?.name ?: return
        viewModelScope.launch {
            try {
                val reached = repository.addToGoal(goalId, if (takeOut) -amount else amount, System.currentTimeMillis(), note.trim())
                val queue = reached.checkpoints.map { Celebration(name, checkpoint = it) } +
                    listOfNotNull(reached.milestone?.let { Celebration(name, milestone = it) })
                if (queue.isNotEmpty()) celebrationQueue.update { it + queue }
            } catch (e: Exception) {
                showMessage("Could not save. Please try again.")
            }
        }
    }

    fun dismissCelebration() {
        celebrationQueue.update { it.drop(1) }
    }

    // --- Round-up jar and saving streak ---

    private val roundUpStore = RoundUpStore(application)
    val roundUps: StateFlow<RoundUpSettings> = roundUpStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, roundUpStore.read())
    val roundUpJar: StateFlow<Double> = combine(roundUps, repository.allTransactions) { settings, txs ->
        RoundUps.jar(settings, txs)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val savingStreak: StateFlow<SavingStreak> = combine(repository.goals, repository.goalEntries) { goals, entries ->
        Streaks.saving(goals, entries, System.currentTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SavingStreak(0, 0, 1, false, 0))

    fun setRoundUpsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val txs = repository.transactionsAllOnce()
            roundUpStore.update { RoundUps.setEnabled(it, enabled, txs, System.currentTimeMillis()) }
        }
    }

    fun setRoundUpUnit(unit: Int) {
        viewModelScope.launch {
            val txs = repository.transactionsAllOnce()
            roundUpStore.update { RoundUps.changeUnit(it, unit, txs, System.currentTimeMillis()) }
        }
    }

    /** Empties the jar into [goalId]; the deposit can reach milestones like any other. */
    fun moveJarToGoal(goalId: Long) {
        val amount = (roundUpJar.value * 100).toLong() / 100.0 // whole paise
        if (amount <= 0.0) return
        val name = goals.value.firstOrNull { it.goal.id == goalId }?.goal?.name ?: return
        viewModelScope.launch {
            try {
                val reached = repository.addToGoal(goalId, amount, System.currentTimeMillis(), "Round-up jar")
                roundUpStore.update { RoundUps.emptied(it, amount) }
                showMessage("Moved ${Formatters.formatCurrency(amount)} to $name")
                val queue = reached.checkpoints.map { Celebration(name, checkpoint = it) } +
                    listOfNotNull(reached.milestone?.let { Celebration(name, milestone = it) })
                if (queue.isNotEmpty()) celebrationQueue.update { it + queue }
            } catch (e: Exception) {
                showMessage("Could not move the jar. Please try again.")
            }
        }
    }

    fun addCheckpoint(goalId: Long, name: String, amount: Double) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || amount <= 0.0) return
        viewModelScope.launch {
            try {
                repository.addCheckpoint(goalId, trimmed, amount)
            } catch (e: Exception) {
                showMessage("Could not save the checkpoint.")
            }
        }
    }

    fun deleteCheckpoint(id: Long) {
        viewModelScope.launch { repository.deleteCheckpoint(id) }
    }

    /** "I bought it": the purchase becomes an expense in [category]; the money set aside is released. */
    fun buyGoal(goalId: Long, amount: Double, category: String) {
        val progress = goals.value.firstOrNull { it.goal.id == goalId } ?: return
        if (amount <= 0.0 || category.isBlank()) return
        viewModelScope.launch {
            try {
                repository.buyGoal(
                    goalId,
                    TransactionEntity(
                        title = progress.goal.name.take(60),
                        amount = amount,
                        type = TransactionType.EXPENSE.name,
                        category = category,
                        date = System.currentTimeMillis(),
                        note = "Bought with savings goal"
                    )
                )
                showMessage("${progress.goal.name} bought · ${Formatters.formatCurrency(amount)} added to $category")
            } catch (e: Exception) {
                showMessage("Could not save. Nothing was changed.")
            }
        }
    }

    fun deleteGoalEntry(id: Long) {
        viewModelScope.launch { repository.deleteGoalEntry(id) }
    }

    fun deleteGoal(id: Long) {
        if (_selectedGoal.value == id) _selectedGoal.value = null
        viewModelScope.launch { repository.deleteGoal(id) }
    }

    /** The transaction being edited was really a loan or repayment: move it to money lent. */
    fun moveTransactionToLending(transaction: TransactionEntity, person: String, kind: DebtKind) {
        val name = Lending.displayName(person)
        if (name.isEmpty() || transaction.id == 0L) return
        dismissAddEditDialog()
        viewModelScope.launch {
            try {
                repository.moveTransactionToDebt(
                    transaction.id,
                    DebtEntryEntity(
                        person = name,
                        personKey = Lending.personKey(name),
                        kind = kind.name,
                        amount = transaction.amount,
                        date = transaction.date,
                        note = transaction.note.ifBlank { transaction.title }
                    )
                )
                showMessage("Moved to money lent: ${kind.label.lowercase()} · $name")
            } catch (e: Exception) {
                showMessage("Could not move it. Nothing was changed.")
            }
        }
    }

    fun closeOverlay() {
        _overlay.value = null
    }

    /**
     * Validates and creates a custom category. Returns an error message for the user, or null
     * when the category was accepted (it is written to the database asynchronously).
     */
    fun addCustomCategory(name: String, type: TransactionType, iconKey: String, colorHex: Long): String? {
        val trimmed = name.trim().replace(Regex("\\s+"), " ")
        when {
            trimmed.isEmpty() -> return "Please enter a name"
            trimmed.length > 30 -> return "Name must be 30 characters or fewer"
            categories.value.any { it.name.equals(trimmed, ignoreCase = true) } ->
                return "A category with this name already exists"
        }
        viewModelScope.launch {
            repository.saveCustomCategory(
                CustomCategoryEntity(name = trimmed, iconKey = iconKey, colorHex = colorHex, type = type.name)
            )
        }
        // When created from the transaction form, select the new category straight away.
        if (_isAddEditOpen.value && _formType.value == type) {
            formCategoryChosenByUser = true
            _formCategory.value = trimmed
            _categoryError.value = null
        }
        return null
    }

    /**
     * Sets the monthly limit for [category]. Returns an error message for the user, or null
     * when the budget was accepted.
     */
    fun saveBudget(category: String, rawLimit: String): String? {
        if (category.isBlank()) return "Please choose a category"
        FormValidation.validateAmount(rawLimit).errorOrNull?.let { return it }
        val limit = FormValidation.parseAmount(rawLimit) ?: return "Please enter a valid numeric amount"
        viewModelScope.launch { repository.setBudget(category, limit) }
        return null
    }

    fun removeBudget(category: String) {
        viewModelScope.launch { repository.removeBudget(category) }
    }

    // SMS formats taught by the owner.
    val smsTemplates: StateFlow<List<SmsTemplate>> = repository.smsTemplates
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Saves a taught format; [onSaved] runs after it is stored (e.g. to rescan). */
    fun saveSmsTemplate(template: SmsTemplate, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveSmsTemplate(template)
            _userMessage.value = "Message format saved"
            onSaved()
        }
    }

    fun deleteSmsTemplate(id: Long) {
        viewModelScope.launch { repository.deleteSmsTemplate(id) }
    }

    // Backup / restore. Messages are shown once as a snackbar, then cleared.
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage

    // A parsed backup waiting for the user to confirm that it may replace all data.
    private val _pendingRestore = MutableStateFlow<BackupData?>(null)
    val pendingRestore: StateFlow<BackupData?> = _pendingRestore

    fun showMessage(message: String) {
        _userMessage.value = message
    }

    fun consumeUserMessage() {
        _userMessage.value = null
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _userMessage.value = try {
                val data = repository.exportBackup()
                val json = BackupSerializer.toJson(data)
                withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("Could not open the file")
                    stream.bufferedWriter().use { it.write(json) }
                }
                "Backup saved (${data.transactions.size} transactions)"
            } catch (e: Exception) {
                "Backup failed: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun readBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw IOException("Could not open the file")
                    stream.bufferedReader().use { it.readText() }
                }
                _pendingRestore.value = withContext(Dispatchers.Default) { BackupSerializer.fromJson(json) }
            } catch (e: InvalidBackupException) {
                _userMessage.value = e.message
            } catch (e: Exception) {
                _userMessage.value = "Could not read the backup: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun confirmRestore() {
        val data = _pendingRestore.value ?: return
        _pendingRestore.value = null
        viewModelScope.launch {
            _userMessage.value = try {
                repository.restoreBackup(data)
                "Restored ${data.transactions.size} transactions"
            } catch (e: Exception) {
                "Restore failed, your data was not changed"
            }
        }
    }

    fun cancelRestore() {
        _pendingRestore.value = null
    }

    fun eraseAllData() {
        viewModelScope.launch {
            repository.eraseAllData()
            _userMessage.value = "All data erased"
        }
    }

    fun deleteCustomCategory(name: String) {
        viewModelScope.launch {
            repository.deleteCustomCategory(name)
        }
    }

    fun setFilterType(type: String?) {
        _filterType.value = type
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategoryFilter(category: String?) {
        _selectedCategoryFilter.value = category
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    // Dialog & Form Handlers
    fun openAddDialog(type: TransactionType = TransactionType.EXPENSE) {
        _editingTransaction.value = null
        _formType.value = type
        _formAmount.value = ""
        _formTitle.value = ""
        _formCategory.value = CategoryInfo.forType(categories.value, type).first().name
        _formDate.value = System.currentTimeMillis()
        _formNote.value = ""
        _amountError.value = null
        _titleError.value = null
        _categoryError.value = null
        formCategoryChosenByUser = false
        _isAddEditOpen.value = true
    }

    fun openEditDialog(transaction: TransactionEntity) {
        _editingTransaction.value = transaction
        _formType.value = if (transaction.type.equals(TransactionType.INCOME.name, ignoreCase = true)) {
            TransactionType.INCOME
        } else {
            TransactionType.EXPENSE
        }
        // Locale.US: in comma-decimal locales "%.2f" produced "12,50", which the parser read as 1250.
        _formAmount.value = String.format(Locale.US, "%.2f", transaction.amount)
        _formTitle.value = transaction.title
        _formCategory.value = transaction.category
        _formDate.value = transaction.date
        _formNote.value = transaction.note
        _amountError.value = null
        _titleError.value = null
        _categoryError.value = null
        formCategoryChosenByUser = true // keep the existing category while editing
        _isAddEditOpen.value = true
    }

    fun dismissAddEditDialog() {
        _isAddEditOpen.value = false
        _editingTransaction.value = null
    }

    fun updateFormType(type: TransactionType) {
        _formType.value = type
        // Reset category to appropriate default if current isn't in type
        val available = CategoryInfo.forType(categories.value, type)
        if (available.none { it.name.equals(_formCategory.value, ignoreCase = true) }) {
            _formCategory.value = available.firstOrNull()?.name ?: Category.OTHER.displayName
        }
        // Remembered choices are per direction, so look the vendor up again.
        suggestCategoryFor(_formTitle.value)
    }

    fun updateFormAmount(amount: String) {
        _formAmount.value = amount
        if (_amountError.value != null) {
            _amountError.value = FormValidation.validateAmount(amount).errorOrNull
        }
    }

    fun updateFormTitle(title: String) {
        _formTitle.value = title
        if (_titleError.value != null) {
            _titleError.value = FormValidation.validateTitle(title).errorOrNull
        }
        suggestCategoryFor(title)
    }

    /** Pre-selects the remembered category of a known vendor, unless the user chose one already. */
    private fun suggestCategoryFor(title: String) {
        if (formCategoryChosenByUser) return
        val type = _formType.value
        val rules = merchantRules.value.associateBy { it.counterpartyKey }
        val rule = SmsImportPlanner.findRule(type, title, SmsImportPlanner.ruleKey(type, title), rules) ?: return
        if (rule.isSelfTransfer) return
        if (CategoryInfo.forType(categories.value, type).any { it.name == rule.category }) {
            _formCategory.value = rule.category
            _categoryError.value = null
        }
    }

    fun updateFormCategory(category: String) {
        formCategoryChosenByUser = true
        _formCategory.value = category
        _categoryError.value = null
    }

    fun updateFormDate(dateMs: Long) {
        _formDate.value = dateMs
    }

    fun updateFormNote(note: String) {
        _formNote.value = note
    }

    fun saveTransaction(): Boolean {
        if (_isSaving.value) return false

        val amountVal = FormValidation.validateAmount(_formAmount.value)
        val titleVal = FormValidation.validateTitle(_formTitle.value)
        val catVal = FormValidation.validateCategory(_formCategory.value)

        _amountError.value = amountVal.errorOrNull
        _titleError.value = titleVal.errorOrNull
        _categoryError.value = catVal.errorOrNull

        if (!amountVal.isValid || !titleVal.isValid || !catVal.isValid) {
            return false
        }

        val parsedAmount = FormValidation.parseAmount(_formAmount.value) ?: return false
        val currentEditing = _editingTransaction.value

        val entity = currentEditing?.copy(
            title = _formTitle.value.trim(),
            amount = parsedAmount,
            type = _formType.value.name,
            category = _formCategory.value,
            date = _formDate.value,
            note = _formNote.value.trim()
        ) ?: TransactionEntity(
            title = _formTitle.value.trim(),
            amount = parsedAmount,
            type = _formType.value.name,
            category = _formCategory.value,
            date = _formDate.value,
            note = _formNote.value.trim()
        )

        // Snapshot before saving: used to offer the same category for this vendor's other entries.
        val categoryChanged = currentEditing != null && currentEditing.category != entity.category
        val similar = if (categoryChanged) {
            CategoryPropagation.similarTo(entity, entity.category, allTransactions.value)
        } else {
            emptyList()
        }

        _isSaving.value = true
        viewModelScope.launch {
            try {
                if (currentEditing != null) {
                    repository.updateTransaction(entity)
                } else {
                    repository.insertTransaction(entity)
                }
                // Remember the vendor's category for auto-category and future SMS imports.
                if (currentEditing == null || categoryChanged) {
                    repository.rememberCategory(_formType.value, entity.title, entity.category)
                }
                dismissAddEditDialog()
                if (similar.isNotEmpty()) {
                    _similarPrompt.value = SimilarCategoryPrompt(
                        category = entity.category,
                        vendorName = CategoryPropagation.vendorName(entity, similar),
                        transactions = similar
                    )
                }
            } finally {
                _isSaving.value = false
            }
        }
        return true
    }

    /** Applies the prompt's category to the chosen transactions ([ids] ticked by the user). */
    fun applySimilarCategory(ids: Set<Long>) {
        val prompt = _similarPrompt.value ?: return
        _similarPrompt.value = null
        val chosen = prompt.transactions.filter { it.id in ids }.map { it.id }
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            repository.setCategory(chosen, prompt.category)
            _userMessage.value = "Updated ${chosen.size} more transaction${if (chosen.size == 1) "" else "s"} to ${prompt.category}"
        }
    }

    fun dismissSimilarPrompt() {
        _similarPrompt.value = null
    }

    fun promptDelete(transaction: TransactionEntity) {
        _transactionToDelete.value = transaction
    }

    fun dismissDelete() {
        _transactionToDelete.value = null
    }

    fun confirmDelete() {
        val toDelete = _transactionToDelete.value ?: return
        viewModelScope.launch {
            repository.deleteTransaction(toDelete)
            _transactionToDelete.value = null
        }
    }
}
