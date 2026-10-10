package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TransactionType
import com.example.ui.screens.AddEditTransactionDialog
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.PennywisePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PennywiseApp(
    viewModel: PennywiseViewModel,
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val summary by viewModel.financialSummary.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val filteredTransactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val categoryBreakdowns by viewModel.categoryBreakdowns.collectAsStateWithLifecycle()
    val budgetStatuses by viewModel.budgetStatuses.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterType by viewModel.filterType.collectAsStateWithLifecycle()
    val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()

    val isAddEditOpen by viewModel.isAddEditOpen.collectAsStateWithLifecycle()
    val editingTransaction by viewModel.editingTransaction.collectAsStateWithLifecycle()
    val formType by viewModel.formType.collectAsStateWithLifecycle()
    val formAmount by viewModel.formAmount.collectAsStateWithLifecycle()
    val formTitle by viewModel.formTitle.collectAsStateWithLifecycle()
    val formCategory by viewModel.formCategory.collectAsStateWithLifecycle()
    val formDate by viewModel.formDate.collectAsStateWithLifecycle()
    val formNote by viewModel.formNote.collectAsStateWithLifecycle()
    val amountError by viewModel.amountError.collectAsStateWithLifecycle()
    val titleError by viewModel.titleError.collectAsStateWithLifecycle()
    val categoryError by viewModel.categoryError.collectAsStateWithLifecycle()
    val transactionToDelete by viewModel.transactionToDelete.collectAsStateWithLifecycle()

    // Handle back button: return to Dashboard tab if on other tabs
    BackHandler(enabled = selectedTab != 0) {
        viewModel.setTab(0)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            0 -> "Pennywise"
                            1 -> "Transactions"
                            2 -> "Analytics & Budget"
                            else -> "Pennywise"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_navigation_bar"),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                // Tab 0: Dashboard
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.setTab(0) },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Filled.Dashboard else Icons.Outlined.Dashboard,
                            contentDescription = "Dashboard"
                        )
                    },
                    label = { Text("Dashboard") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PennywisePrimary,
                        selectedTextColor = PennywisePrimary,
                        indicatorColor = PennywisePrimary.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_tab_dashboard")
                )

                // Tab 1: Transactions
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.setTab(1) },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 1) Icons.AutoMirrored.Filled.ReceiptLong else Icons.AutoMirrored.Outlined.ReceiptLong,
                            contentDescription = "Transactions"
                        )
                    },
                    label = { Text("Transactions") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PennywisePrimary,
                        selectedTextColor = PennywisePrimary,
                        indicatorColor = PennywisePrimary.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_tab_transactions")
                )

                // Tab 2: Analytics
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.setTab(2) },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 2) Icons.Filled.PieChart else Icons.Outlined.PieChart,
                            contentDescription = "Analytics"
                        )
                    },
                    label = { Text("Analytics") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PennywisePrimary,
                        selectedTextColor = PennywisePrimary,
                        indicatorColor = PennywisePrimary.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_tab_analytics")
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddDialog() },
                containerColor = PennywisePrimary,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                modifier = Modifier.testTag("fab_add_transaction")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Transaction",
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    summary = summary,
                    breakdowns = categoryBreakdowns,
                    recentTransactions = allTransactions,
                    onAddExpense = {
                        viewModel.openAddDialog()
                        viewModel.updateFormType(TransactionType.EXPENSE)
                    },
                    onAddIncome = {
                        viewModel.openAddDialog()
                        viewModel.updateFormType(TransactionType.INCOME)
                    },
                    onViewAllTransactions = {
                        viewModel.setTab(1)
                    },
                    onTransactionClick = { tx ->
                        viewModel.openEditDialog(tx)
                    },
                    onTransactionDelete = { tx ->
                        viewModel.promptDelete(tx)
                    }
                )

                1 -> TransactionsScreen(
                    transactions = filteredTransactions,
                    searchQuery = searchQuery,
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    selectedFilterType = filterType,
                    onFilterTypeChange = { viewModel.setFilterType(it) },
                    selectedCategory = selectedCategoryFilter,
                    onCategoryChange = { viewModel.setSelectedCategoryFilter(it) },
                    onTransactionClick = { tx ->
                        viewModel.openEditDialog(tx)
                    },
                    onTransactionDelete = { tx ->
                        viewModel.promptDelete(tx)
                    },
                    transactionToDelete = transactionToDelete,
                    onConfirmDelete = { viewModel.confirmDelete() },
                    onDismissDelete = { viewModel.dismissDelete() }
                )

                2 -> AnalyticsScreen(
                    summary = summary,
                    breakdowns = categoryBreakdowns,
                    budgetStatuses = budgetStatuses
                )
            }
        }

        // Add / Edit Transaction Dialog
        AddEditTransactionDialog(
            isOpen = isAddEditOpen,
            editingTransaction = editingTransaction,
            type = formType,
            amount = formAmount,
            title = formTitle,
            selectedCategory = formCategory,
            dateMs = formDate,
            note = formNote,
            amountError = amountError,
            titleError = titleError,
            categoryError = categoryError,
            onTypeChange = { viewModel.updateFormType(it) },
            onAmountChange = { viewModel.updateFormAmount(it) },
            onTitleChange = { viewModel.updateFormTitle(it) },
            onCategoryChange = { viewModel.updateFormCategory(it) },
            onDateChange = { viewModel.updateFormDate(it) },
            onNoteChange = { viewModel.updateFormNote(it) },
            onSave = { viewModel.saveTransaction() },
            onDismiss = { viewModel.dismissAddEditDialog() }
        )
    }
}
