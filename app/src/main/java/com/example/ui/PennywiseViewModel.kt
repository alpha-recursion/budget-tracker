package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.BudgetEntity
import com.example.data.local.PennywiseDatabase
import com.example.data.local.TransactionEntity
import com.example.data.model.Category
import com.example.data.model.TransactionType
import com.example.data.repository.PennywiseRepository
import com.example.domain.CategoryBreakdown
import com.example.domain.CategoryBudgetStatus
import com.example.domain.FinancialCalculations
import com.example.domain.FinancialSummary
import com.example.domain.FormValidation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortOption(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    HIGHEST_AMOUNT("Highest $"),
    LOWEST_AMOUNT("Lowest $")
}

class PennywiseViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: PennywiseRepository = PennywiseRepository(
        PennywiseDatabase.getDatabase(application).transactionDao(),
        PennywiseDatabase.getDatabase(application).budgetDao()
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

    // Tab Navigation: 0 = Dashboard, 1 = Transactions, 2 = Analytics
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab

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
    val categoryBreakdowns: StateFlow<List<CategoryBreakdown>> = allTransactions
        .map { txs -> FinancialCalculations.calculateCategoryBreakdown(txs) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Category Budget Statuses
    val budgetStatuses: StateFlow<List<CategoryBudgetStatus>> = combine(
        allBudgets,
        allTransactions
    ) { budgets, txs ->
        FinancialCalculations.calculateBudgetStatuses(budgets, txs)
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

    // Delete Confirmation
    private val _transactionToDelete = MutableStateFlow<TransactionEntity?>(null)
    val transactionToDelete: StateFlow<TransactionEntity?> = _transactionToDelete

    // Navigation and Filters
    fun setTab(index: Int) {
        _selectedTab.value = index
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
        _formCategory.value = Category.getCategoriesForType(type).first().displayName
        _formDate.value = System.currentTimeMillis()
        _formNote.value = ""
        _amountError.value = null
        _titleError.value = null
        _categoryError.value = null
        _isAddEditOpen.value = true
    }

    fun openEditDialog(transaction: TransactionEntity) {
        _editingTransaction.value = transaction
        _formType.value = if (transaction.type.equals(TransactionType.INCOME.name, ignoreCase = true)) {
            TransactionType.INCOME
        } else {
            TransactionType.EXPENSE
        }
        _formAmount.value = String.format("%.2f", transaction.amount)
        _formTitle.value = transaction.title
        _formCategory.value = transaction.category
        _formDate.value = transaction.date
        _formNote.value = transaction.note
        _amountError.value = null
        _titleError.value = null
        _categoryError.value = null
        _isAddEditOpen.value = true
    }

    fun dismissAddEditDialog() {
        _isAddEditOpen.value = false
        _editingTransaction.value = null
    }

    fun updateFormType(type: TransactionType) {
        _formType.value = type
        // Reset category to appropriate default if current isn't in type
        val available = Category.getCategoriesForType(type)
        if (available.none { it.displayName.equals(_formCategory.value, ignoreCase = true) }) {
            _formCategory.value = available.firstOrNull()?.displayName ?: Category.OTHER.displayName
        }
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
    }

    fun updateFormCategory(category: String) {
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

        viewModelScope.launch {
            if (currentEditing != null) {
                val updated = currentEditing.copy(
                    title = _formTitle.value.trim(),
                    amount = parsedAmount,
                    type = _formType.value.name,
                    category = _formCategory.value,
                    date = _formDate.value,
                    note = _formNote.value.trim()
                )
                repository.updateTransaction(updated)
            } else {
                val newTx = TransactionEntity(
                    title = _formTitle.value.trim(),
                    amount = parsedAmount,
                    type = _formType.value.name,
                    category = _formCategory.value,
                    date = _formDate.value,
                    note = _formNote.value.trim()
                )
                repository.insertTransaction(newTx)
            }
            dismissAddEditDialog()
        }
        return true
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
