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
import com.example.data.backup.PhoneSettingsBackup
import com.example.data.local.BudgetEntity
import com.example.data.local.CategoryStyle
import com.example.domain.AutoCategorizer
import com.example.domain.AutoSuggestion
import com.example.domain.CategoryModel
import com.example.domain.OtherFix
import com.example.data.local.CategoryStyleStore
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
import com.example.domain.AchievementData
import com.example.domain.PayCycle
import com.example.domain.PayCycleStatus
import com.example.domain.PaySettings
import com.example.domain.QuickAdd
import com.example.domain.QuickChip
import com.example.domain.QuickPin
import com.example.data.local.PlanningPrefs
import com.example.data.local.PlanningStore
import com.example.domain.ChallengeResult
import com.example.domain.ChallengeState
import com.example.domain.ChallengeSuggestion
import com.example.domain.Challenges
import com.example.domain.MoneyData
import com.example.domain.ReportCard
import com.example.domain.ReportCards
import com.example.data.local.ChallengeEntity
import com.example.domain.AchievementProgress
import com.example.domain.Achievements
import com.example.data.local.RewardsStore
import com.example.data.repository.GoalReached
import com.example.domain.RoundUpSettings
import com.example.domain.RoundUps
import com.example.data.local.RecurringEntity
import com.example.data.reminders.DailyReviewStore
import com.example.data.reminders.ReminderNotifier
import com.example.domain.DailyReviewState
import com.example.domain.InputGuard
import com.example.domain.Recurring
import com.example.domain.RecurringSuggestion
import com.example.domain.DailyStreaks
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
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

enum class Overlay { SMS_IMPORT, LENDING, GOALS, BADGES, CHALLENGES, RECURRING }

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
        private const val MAX_CHALLENGES_PER_MONTH = 3
        private const val QUICK_ADD_REPEAT_GUARD_MS = 3_000L
        private const val REPEAT_GUARD_MS = 2_000L
        private const val MAX_AMOUNT = 10_000_000.0

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

    // Declared before [categories], which reads it while initialising.
    private val categoryStyleStore = CategoryStyleStore(application)

    // Built-in categories (with any icon / colour the owner picked) followed by the user's own.
    // Eager so the list is ready for the form the first time it opens.
    val categories: StateFlow<List<CategoryInfo>> = combine(
        repository.customCategories,
        categoryStyleStore.styles
    ) { custom, styles ->
        // A custom category made before a built-in of the same name existed keeps its look;
        // the built-in is dropped so the name appears once.
        val customNames = custom.map { it.name.lowercase() }.toSet()
        CategoryInfo.builtIns.filterNot { it.name.lowercase() in customNames }.map { builtIn ->
            styles[builtIn.name]?.let { builtIn.copy(iconKey = it.iconKey, colorHex = it.colorHex) } ?: builtIn
        } + custom.map { entity ->
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

    // What the owner's categorised payees teach about payee words, for suggesting categories.
    private val categoryModel: StateFlow<CategoryModel> = allTransactions
        .map { CategoryModel.learn(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, CategoryModel.EMPTY)

    // "Sort out Other": payees whose Other payments can now be categorised; null when closed.
    private val _otherFixes = MutableStateFlow<List<OtherFix>?>(null)
    val otherFixes: StateFlow<List<OtherFix>?> = _otherFixes

    fun openOtherFixes() {
        _otherFixes.value = AutoCategorizer.suggestForOther(
            allTransactions.value,
            categories.value.map { it.name }.toSet(),
            categoryModel.value
        )
    }

    fun closeOtherFixes() {
        _otherFixes.value = null
    }

    /**
     * Moves the chosen payees' Other payments to the picked categories and remembers each choice,
     * so future imports of those payees are categorised too.
     */
    fun applyOtherFixes(choices: List<Pair<OtherFix, String>>) {
        _otherFixes.value = null
        if (choices.isEmpty()) return
        viewModelScope.launch {
            try {
                choices.forEach { (fix, category) ->
                    repository.setCategory(fix.transactionIds, category)
                    repository.rememberCategory(fix.type, fix.title, category)
                }
                val count = choices.sumOf { it.first.transactionIds.size }
                showMessage("Categorised $count payment${if (count == 1) "" else "s"}")
            } catch (e: Exception) {
                showMessage("Could not update categories. Nothing was changed.")
            }
        }
    }

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
        today.value = DailyStreaks.dayNumber(System.currentTimeMillis())
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
        if (!InputGuard.hasLetter(name) || amount <= 0.0 || amount > MAX_AMOUNT) return
        if (isRepeat("debt:${Lending.personKey(name)}:${kind.name}:$amount:$date")) return
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
        if (person.isSettled || isRepeat("settle:${person.key}")) return
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
    data class Celebration(val emoji: String, val title: String, val message: String)

    /** Celebrations for what one deposit into [goalName] reached: checkpoints first, then the milestone. */
    private fun goalCelebrations(goalName: String, reached: GoalReached): List<Celebration> =
        reached.checkpoints.map { Celebration("🚩", "Checkpoint reached!", "\"$it\" on $goalName. Keep going!") } +
            listOfNotNull(
                reached.milestone?.let { m ->
                    Celebration(
                        if (m == 100) "🏆" else "🎉",
                        Goals.milestoneTitle(m),
                        if (m == 100) "You saved the full amount for $goalName." else "You've saved $m% of $goalName. Keep going!"
                    )
                }
            )

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
        val trimmed = InputGuard.clean(name)
        if (!FormValidation.validateName(trimmed, InputGuard.MAX_NAME).isValid || target <= 0.0) return
        val existing = goals.value.firstOrNull { it.goal.id == id }
        if (existing == null && isRepeat("goal:new:$trimmed:$target")) return
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
        if (isRepeat("goal:move:$goalId:$takeOut:$amount")) return
        viewModelScope.launch {
            try {
                val reached = repository.addToGoal(goalId, if (takeOut) -amount else amount, System.currentTimeMillis(), note.trim())
                val queue = goalCelebrations(name, reached)
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

    // --- Daily check-in: "I've logged everything" streak and no-spend days ---

    private val dailyReviewStore = DailyReviewStore(application)

    /** Today as a day number; refreshed when the app returns to the foreground. */
    private val today = MutableStateFlow(DailyStreaks.dayNumber(System.currentTimeMillis()))

    val dailyReview: StateFlow<DailyReviewState> = combine(
        dailyReviewStore.reviewedDays,
        repository.allTransactions,
        today
    ) { reviewed, txs, day ->
        val spendDays = txs.filter { it.type == "EXPENSE" }.map { DailyStreaks.dayNumber(it.date) }.toSet()
        DailyStreaks.state(reviewed, spendDays, day)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DailyReviewState())

    // --- Recurring payments and subscriptions (reminders only) ---

    val recurring: StateFlow<List<RecurringEntity>> = repository.recurring
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Payees that keep charging the same amount; refreshed daily and when spending changes. */
    val recurringSuggestions: StateFlow<List<RecurringSuggestion>> = combine(
        recurring,
        repository.allTransactions,
        today
    ) { items, txs, _ ->
        Recurring.detect(txs, items, System.currentTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun openRecurring() {
        _overlay.value = Overlay.RECURRING
    }

    fun saveRecurring(item: RecurringEntity) {
        // A new item has no id yet, so a double tap would add it twice.
        if (!FormValidation.validateName(item.name, InputGuard.MAX_NAME).isValid || item.amount <= 0.0 || item.amount > MAX_AMOUNT) return
        if (item.id == 0L && isRepeat("recurring:${item.name}:${item.amount}:${item.frequency}")) return
        viewModelScope.launch { repository.saveRecurring(item) }
    }

    fun deleteRecurring(id: Long) {
        viewModelScope.launch { repository.deleteRecurring(id) }
    }

    fun confirmDailyReview() {
        val now = System.currentTimeMillis()
        today.value = DailyStreaks.dayNumber(now)
        dailyReviewStore.markReviewed(today.value)
        ReminderNotifier.cancelReview(getApplication())
    }

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
        // The jar total is only emptied after the move, so a second tap would move it again.
        if (isRepeat("jar:$goalId")) return
        viewModelScope.launch {
            try {
                val reached = repository.addToGoal(goalId, amount, System.currentTimeMillis(), "Round-up jar")
                roundUpStore.update { RoundUps.emptied(it, amount) }
                showMessage("Moved ${Formatters.formatCurrency(amount)} to $name")
                val queue = goalCelebrations(name, reached)
                if (queue.isNotEmpty()) celebrationQueue.update { it + queue }
            } catch (e: Exception) {
                showMessage("Could not move the jar. Please try again.")
            }
        }
    }

    // --- Quick-add chips and payday ---

    private val planningStore = PlanningStore(application)
    val planning: StateFlow<PlanningPrefs> = planningStore.updates
        .stateIn(viewModelScope, SharingStarted.Eagerly, planningStore.read())

    val quickChips: StateFlow<List<QuickChip>> = combine(repository.allTransactions, planning) { txs, prefs ->
        QuickAdd.chips(txs, prefs.pins, prefs.hidden, System.currentTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val payCycle: StateFlow<PayCycleStatus?> = combine(
        repository.allTransactions,
        repository.goals,
        repository.goalEntries,
        planning
    ) { txs, goals, entries, prefs ->
        PayCycle.status(prefs.pay, txs, goals, entries, System.currentTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** A quick-add that can still be undone from the snackbar. */
    data class UndoQuickAdd(val transactionId: Long, val message: String)

    private val _undoQuickAdd = MutableStateFlow<UndoQuickAdd?>(null)
    val undoQuickAdd: StateFlow<UndoQuickAdd?> = _undoQuickAdd

    /** One tap: adds the chip's expense now; the snackbar offers Undo. */
    fun quickAdd(chip: QuickChip) {
        if (isRepeat("quick:${chip.key}", QUICK_ADD_REPEAT_GUARD_MS)) {
            showMessage("${chip.title} was just added. Tap again in a moment to add it once more.")
            return
        }
        viewModelScope.launch {
            try {
                val id = repository.insertTransaction(
                    TransactionEntity(
                        title = chip.title.take(60),
                        amount = chip.amount,
                        type = TransactionType.EXPENSE.name,
                        category = chip.category,
                        date = System.currentTimeMillis()
                    )
                )
                _undoQuickAdd.value = UndoQuickAdd(id, "Added ${chip.title} ${Formatters.formatCurrency(chip.amount)}")
            } catch (e: Exception) {
                showMessage("Could not add it. Please try again.")
            }
        }
    }

    fun undoQuickAdd(transactionId: Long) {
        viewModelScope.launch { repository.deleteTransactionById(transactionId) }
    }

    fun consumeUndoQuickAdd() {
        _undoQuickAdd.value = null
    }

    /** Opens Add expense filled in from the chip, to change the amount before saving. */
    fun quickAddWithChanges(chip: QuickChip) {
        openAddDialog(TransactionType.EXPENSE)
        updateFormTitle(chip.title)
        updateFormAmount(plainAmount(chip.amount))
        updateFormCategory(chip.category)
    }

    fun pinChip(chip: QuickChip) = planningStore.update { prefs ->
        prefs.copy(
            pins = prefs.pins.filterNot { it.key == chip.key } + QuickPin(chip.title, chip.amount, chip.category),
            hidden = prefs.hidden - chip.key
        )
    }

    fun unpinChip(chip: QuickChip) = planningStore.update { prefs ->
        prefs.copy(pins = prefs.pins.filterNot { it.key == chip.key })
    }

    fun hideChip(chip: QuickChip) = planningStore.update { prefs ->
        prefs.copy(pins = prefs.pins.filterNot { it.key == chip.key }, hidden = prefs.hidden + chip.key)
    }

    fun savePaySettings(monthlyIncome: Double, payday: Int) = planningStore.update {
        it.copy(pay = PaySettings(monthlyIncome, payday))
    }

    /** Payday card: records the usual salary as income now. */
    fun addSalary() {
        val pay = planning.value.pay
        if (!pay.isSet || isRepeat("salary")) return
        viewModelScope.launch {
            repository.insertTransaction(
                TransactionEntity(
                    title = "Salary",
                    amount = pay.monthlyIncome,
                    type = TransactionType.INCOME.name,
                    category = Category.SALARY.displayName,
                    date = System.currentTimeMillis()
                )
            )
            showMessage("Salary added: ${Formatters.formatCurrency(pay.monthlyIncome)}")
        }
    }

    /** Payday card, salary differed: Add income with the usual details filled in. */
    fun addSalaryWithChanges() {
        openAddDialog(TransactionType.INCOME)
        updateFormTitle("Salary")
        updateFormAmount(plainAmount(planning.value.pay.monthlyIncome))
        updateFormCategory(Category.SALARY.displayName)
    }

    fun dismissSalaryCard() {
        val cycle = payCycle.value ?: return
        planningStore.update { it.copy(salaryDismissedCycle = cycle.cycleStart) }
    }

    private fun plainAmount(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value)

    // --- Monthly challenges and report cards ---

    private val moneyData = combine(
        repository.allTransactions,
        repository.goals,
        repository.goalEntries,
        repository.allBudgets
    ) { txs, goals, entries, budgets -> MoneyData(txs, goals, entries, budgets) }

    val challengeResults: StateFlow<List<ChallengeResult>> = combine(moneyData, repository.challenges) { data, challenges ->
        val now = System.currentTimeMillis()
        challenges.mapNotNull { Challenges.evaluate(it, data, now) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val challengeSuggestions: StateFlow<List<ChallengeSuggestion>> = combine(moneyData, repository.challenges) { data, challenges ->
        val month = Challenges.month(System.currentTimeMillis())
        // Up to three challenges a month.
        val picked = challenges.count { it.month == month.key }
        if (picked >= MAX_CHALLENGES_PER_MONTH) emptyList() else Challenges.suggest(data, month, challenges).take(MAX_CHALLENGES_PER_MONTH - picked)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Report cards for the last 12 months with data, newest first (the current month included). */
    val reportCards: StateFlow<List<ReportCard>> = combine(moneyData, challengeResults) { data, results ->
        val now = System.currentTimeMillis()
        ReportCards.monthsWithData(data.transactions).mapNotNull { ReportCards.build(data, it, results, now) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Month key of the report card open on the Challenges screen; null shows the list. */
    private val _selectedReport = MutableStateFlow<Int?>(null)
    val selectedReport: StateFlow<Int?> = _selectedReport
    private var challengesFromGoals = false

    fun openChallenges(fromGoals: Boolean, reportMonth: Int? = null) {
        challengesFromGoals = fromGoals
        _selectedReport.value = reportMonth
        _overlay.value = Overlay.CHALLENGES
    }

    fun showReport(monthKey: Int?) {
        _selectedReport.value = monthKey
    }

    fun closeChallenges() {
        if (challengesFromGoals) _overlay.value = Overlay.GOALS else closeOverlay()
    }

    fun pickChallenge(suggestion: ChallengeSuggestion) {
        val now = System.currentTimeMillis()
        val month = Challenges.month(now).key
        val alreadyPicked = challengeResults.value.any {
            it.challenge.month == month && it.challenge.type == suggestion.type.name &&
                it.challenge.category == suggestion.category
        }
        if (alreadyPicked || isRepeat("challenge:${suggestion.type.name}:${suggestion.category}")) return
        viewModelScope.launch {
            try {
                repository.addChallenge(
                    ChallengeEntity(
                        month = Challenges.month(now).key,
                        type = suggestion.type.name,
                        category = suggestion.category,
                        target = suggestion.target,
                        createdAt = now
                    )
                )
            } catch (e: Exception) {
                showMessage("Could not add the challenge.")
            }
        }
    }

    fun removeChallenge(id: Long) {
        viewModelScope.launch { repository.deleteChallenge(id) }
    }

    // --- Badges, levels and celebration feedback ---

    private val rewardsStore = RewardsStore(application)
    val celebrationFeedback: StateFlow<Boolean> = rewardsStore.feedbackEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, rewardsStore.isFeedbackEnabled())

    fun setCelebrationFeedback(enabled: Boolean) = rewardsStore.setFeedbackEnabled(enabled)

    // Back from Badges returns to where it was opened from (Goals or Settings).
    private var badgesFromGoals = false

    fun openBadges(fromGoals: Boolean) {
        badgesFromGoals = fromGoals
        _overlay.value = Overlay.BADGES
    }

    fun closeBadges() {
        if (badgesFromGoals) _overlay.value = Overlay.GOALS else closeOverlay()
    }

    private val achievementData = combine(
        repository.allTransactions,
        repository.goals,
        repository.goalEntries,
        repository.goalMilestones,
        repository.debts
    ) { txs, goals, entries, milestones, debts ->
        AchievementData(txs, goals, entries, milestones, debts)
    }

    val achievements: StateFlow<AchievementProgress> = combine(
        achievementData,
        repository.allBudgets,
        savingStreak,
        challengeResults,
        dailyReview
    ) { data, budgets, streak, challenges, daily ->
        Achievements.compute(
            data.copy(
                budgets = budgets,
                bestStreak = streak.best,
                challengesWon = challenges.count { it.state == ChallengeState.WON },
                checkInDays = daily.review.totalDays,
                noSpendDays = daily.noSpend.totalDays,
                bestCheckInStreak = daily.review.best,
                bestNoSpendStreak = daily.noSpend.best
            ),
            System.currentTimeMillis()
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Achievements.compute(AchievementData(), 0L))

    init {
        // A challenge is celebrated once, the first time it shows as won.
        viewModelScope.launch {
            isDataLoaded.first { it }
            challengeResults.collect { results ->
                val seen = rewardsStore.seenBadges()
                val won = results.filter { it.state == ChallengeState.WON && "challenge:${it.challenge.id}" !in seen }
                if (won.isEmpty()) return@collect
                rewardsStore.markBadgesSeen(won.map { "challenge:${it.challenge.id}" })
                celebrationQueue.update { queue ->
                    queue + won.map {
                        Celebration(
                            "🥇",
                            "Challenge won!",
                            Challenges.title(it.type, it.challenge.category, it.challenge.target) + " · +${Achievements.XP_CHALLENGE} XP"
                        )
                    }
                }
            }
        }
        // New badges and level-ups are celebrated once, after the data has loaded.
        viewModelScope.launch {
            isDataLoaded.first { it }
            achievements.collect { progress ->
                val fresh = Achievements.newBadges(progress, rewardsStore.seenBadges())
                val levelUp = progress.level.number > rewardsStore.lastLevel()
                if (fresh.isEmpty() && !levelUp) return@collect
                rewardsStore.markBadgesSeen(fresh.map { it.name })
                if (levelUp) rewardsStore.setLastLevel(progress.level.number)
                val queue = buildList {
                    when (fresh.size) {
                        0 -> Unit
                        1 -> add(Celebration(fresh[0].emoji, "Badge earned: ${fresh[0].title}", fresh[0].howTo + ". Nice work!"))
                        else -> add(
                            Celebration(
                                "🏅",
                                "${fresh.size} new badges!",
                                fresh.joinToString(" · ") { "${it.emoji} ${it.title}" }
                            )
                        )
                    }
                    if (levelUp) {
                        add(Celebration("⭐", "Level ${progress.level.number}: ${progress.level.name}", "You've earned ${progress.xp} XP so far. Keep it up!"))
                    }
                }
                celebrationQueue.update { it + queue }
            }
        }
    }

    fun addCheckpoint(goalId: Long, name: String, amount: Double) {
        val trimmed = InputGuard.clean(name)
        if (!FormValidation.validateName(trimmed, InputGuard.MAX_NAME).isValid || amount <= 0.0) return
        if (isRepeat("checkpoint:$goalId:$trimmed:$amount")) return
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
        if (amount <= 0.0 || category.isBlank() || isRepeat("buy:$goalId")) return
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
        if (name.isEmpty() || transaction.id == 0L || isRepeat("lend:${transaction.id}")) return
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
        val trimmed = InputGuard.clean(name)
        when {
            trimmed.isEmpty() -> return "Please enter a name"
            trimmed.length > InputGuard.MAX_CATEGORY -> return "Name must be ${InputGuard.MAX_CATEGORY} characters or fewer"
            !InputGuard.hasContent(trimmed) -> return "Use at least one letter or number"
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
     * Changes the icon and colour of an existing category. Custom categories are updated in the
     * database; built-ins store an override, removed again when set back to their default.
     */
    fun updateCategoryStyle(category: CategoryInfo, iconKey: String, colorHex: Long) {
        if (category.isCustom) {
            viewModelScope.launch {
                repository.saveCustomCategory(
                    CustomCategoryEntity(name = category.name, iconKey = iconKey, colorHex = colorHex, type = category.type.name)
                )
            }
        } else {
            val default = CategoryInfo.builtIns.firstOrNull { it.name == category.name } ?: return
            val isDefault = default.iconKey == iconKey && default.colorHex == colorHex
            categoryStyleStore.set(category.name, if (isDefault) null else CategoryStyle(iconKey, colorHex))
        }
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

    /** When each action last ran, by key (main thread only), to catch accidental double taps. */
    private val recentActions = HashMap<String, Long>()

    /**
     * True when [key] already ran within [windowMs]: a double tap or a second tap before the
     * screen has caught up. Otherwise records it as running now. Checked before launching any
     * work, so two taps in the same frame cannot both get through.
     */
    private fun isRepeat(key: String, windowMs: Long = REPEAT_GUARD_MS): Boolean {
        val now = System.currentTimeMillis()
        val last = recentActions[key]
        if (last != null && now - last < windowMs) return true
        recentActions[key] = now
        return false
    }

    fun showMessage(message: String) {
        _userMessage.value = message
    }

    fun consumeUserMessage() {
        _userMessage.value = null
    }

    private val phoneSettings = PhoneSettingsBackup(application)

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _userMessage.value = try {
                val data = repository.exportBackup().copy(phoneSettings = phoneSettings.read())
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
                // Older backups have no settings: keep the phone's own rather than wiping them.
                data.phoneSettings?.let { phoneSettings.apply(it) }
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
            phoneSettings.clear()
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

    /**
     * Pre-selects a category for the payee being typed, unless the user chose one already: the
     * remembered choice for a known vendor, otherwise a keyword or learned suggestion.
     */
    private fun suggestCategoryFor(title: String) {
        if (formCategoryChosenByUser) return
        val type = _formType.value
        val offered = CategoryInfo.forType(categories.value, type).map { it.name }.toSet()
        val rules = merchantRules.value.associateBy { it.counterpartyKey }
        val rule = SmsImportPlanner.findRule(type, title, SmsImportPlanner.ruleKey(type, title), rules)
        if (rule != null) {
            if (!rule.isSelfTransfer && rule.category in offered) {
                _formCategory.value = rule.category
                _categoryError.value = null
            }
            return
        }
        if (title.trim().length < 3) return
        val guess = AutoCategorizer.suggest(title, type, offered, categoryModel.value) as? AutoSuggestion.Category ?: return
        _formCategory.value = guess.name
        _categoryError.value = null
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
            title = InputGuard.clean(_formTitle.value),
            amount = parsedAmount,
            type = _formType.value.name,
            category = _formCategory.value,
            date = _formDate.value,
            note = _formNote.value.trim()
        ) ?: TransactionEntity(
            title = InputGuard.clean(_formTitle.value),
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
