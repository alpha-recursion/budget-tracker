package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
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
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.VioletPrimary
import com.example.ui.theme.VioletPrimaryContainer

private fun tabTitle(tab: Int): String = when (tab) {
    1 -> "Transactions"
    2 -> "Analytics & Budget"
    else -> "Pennywise"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PennywiseApp(
    viewModel: PennywiseViewModel,
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isDataLoaded by viewModel.isDataLoaded.collectAsStateWithLifecycle()

    // Screen data is collected here (not inside each tab) so every flow stays subscribed and
    // already holds its latest value when a tab is opened - no empty frame on tab switch.
    val summary by viewModel.financialSummary.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val filteredTransactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val categoryBreakdowns by viewModel.categoryBreakdowns.collectAsStateWithLifecycle()
    val budgetStatuses by viewModel.budgetStatuses.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterType by viewModel.filterType.collectAsStateWithLifecycle()
    val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()

    // Keeps each tab's saveable state (e.g. list scroll positions) alive while the tab is not
    // shown, so switching tabs does not rebuild screens from scratch and jump back to the top.
    val tabStateHolder = rememberSaveableStateHolder()

    // Handle back button: return to Dashboard tab if on other tabs
    BackHandler(enabled = selectedTab != 0) {
        viewModel.setTab(0)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            fadeIn(tween(TAB_FADE_IN_MS, delayMillis = TAB_FADE_OUT_MS))
                                .togetherWith(fadeOut(tween(TAB_FADE_OUT_MS)))
                        },
                        label = "top_bar_title"
                    ) { tab ->
                        Text(
                            text = tabTitle(tab),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                // NavigationBar already applies the navigation-bar insets itself; adding
                // windowInsetsPadding here doubled the empty gap below the tabs.
                modifier = Modifier.testTag("bottom_navigation_bar"),
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
                        selectedIconColor = VioletPrimary,
                        selectedTextColor = VioletPrimary,
                        indicatorColor = VioletPrimaryContainer
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
                        selectedIconColor = VioletPrimary,
                        selectedTextColor = VioletPrimary,
                        indicatorColor = VioletPrimaryContainer
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
                        selectedIconColor = VioletPrimary,
                        selectedTextColor = VioletPrimary,
                        indicatorColor = VioletPrimaryContainer
                    ),
                    modifier = Modifier.testTag("nav_tab_analytics")
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddDialog() },
                containerColor = VioletPrimary,
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
            if (!isDataLoaded) {
                // Avoid flashing "No transactions" empty states before Room's first result.
                CircularProgressIndicator(
                    color = VioletPrimary,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        // Material "fade through": the old page fades out quickly, then the new
                        // page fades and scales in. Shorter and less overlap than the default
                        // crossfade, so two heavy screens are not blended for long.
                        (fadeIn(tween(TAB_FADE_IN_MS, delayMillis = TAB_FADE_OUT_MS, easing = LinearOutSlowInEasing)) +
                            scaleIn(
                                initialScale = 0.96f,
                                animationSpec = tween(TAB_FADE_IN_MS, delayMillis = TAB_FADE_OUT_MS, easing = FastOutSlowInEasing)
                            ))
                            .togetherWith(fadeOut(tween(TAB_FADE_OUT_MS, easing = FastOutSlowInEasing)))
                    },
                    label = "tab_page_transition"
                ) { tab ->
                    tabStateHolder.SaveableStateProvider(key = tab) {
                        when (tab) {
                            0 -> DashboardScreen(
                                summary = summary,
                                breakdowns = categoryBreakdowns,
                                recentTransactions = allTransactions,
                                onAddExpense = { viewModel.openAddDialog(TransactionType.EXPENSE) },
                                onAddIncome = { viewModel.openAddDialog(TransactionType.INCOME) },
                                onViewAllTransactions = { viewModel.setTab(1) },
                                onTransactionClick = { tx -> viewModel.openEditDialog(tx) },
                                onTransactionDelete = { tx -> viewModel.promptDelete(tx) }
                            )

                            1 -> TransactionsScreen(
                                transactions = filteredTransactions,
                                searchQuery = searchQuery,
                                onSearchChange = { viewModel.setSearchQuery(it) },
                                selectedFilterType = filterType,
                                onFilterTypeChange = { viewModel.setFilterType(it) },
                                selectedCategory = selectedCategoryFilter,
                                onCategoryChange = { viewModel.setSelectedCategoryFilter(it) },
                                currentSortOption = sortOption,
                                onSortOptionChange = { viewModel.setSortOption(it) },
                                onTransactionClick = { tx -> viewModel.openEditDialog(tx) },
                                onTransactionDelete = { tx -> viewModel.promptDelete(tx) }
                            )

                            2 -> AnalyticsScreen(
                                summary = summary,
                                breakdowns = categoryBreakdowns,
                                budgetStatuses = budgetStatuses
                            )
                        }
                    }
                }
            }
        }

        // Dialogs read their own state so typing in the form does not recompose the whole app.
        AddEditTransactionHost(viewModel = viewModel)
        DeleteConfirmationHost(viewModel = viewModel)
    }
}

private const val TAB_FADE_OUT_MS = 90
private const val TAB_FADE_IN_MS = 210

@Composable
private fun AddEditTransactionHost(viewModel: PennywiseViewModel) {
    val isAddEditOpen by viewModel.isAddEditOpen.collectAsStateWithLifecycle()
    if (!isAddEditOpen) return

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
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

    AddEditTransactionDialog(
        isOpen = true,
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
        isSaving = isSaving,
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

@Composable
private fun DeleteConfirmationHost(viewModel: PennywiseViewModel) {
    val transactionToDelete by viewModel.transactionToDelete.collectAsStateWithLifecycle()
    val pending = transactionToDelete ?: return

    // Global Delete Confirmation Dialog (Works on Dashboard, Transactions & everywhere)
    AlertDialog(
        onDismissRequest = { viewModel.dismissDelete() },
        title = { Text("Delete Transaction", fontWeight = FontWeight.Bold) },
        text = {
            Text("Are you sure you want to delete \"${pending.title}\"? This action will update all dashboard metrics and cannot be undone.")
        },
        confirmButton = {
            Button(
                onClick = { viewModel.confirmDelete() },
                colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                Text("Delete", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = { viewModel.dismissDelete() },
                modifier = Modifier.testTag("cancel_delete_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
