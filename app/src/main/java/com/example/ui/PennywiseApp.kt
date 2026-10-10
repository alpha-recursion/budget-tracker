package com.example.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.InsertChart
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.components.PwHeaderIconButton
import com.example.ui.components.Space
import com.example.ui.components.softShadow
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.SurfaceWhite
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.example.data.model.TransactionType
import com.example.ui.screens.AddEditTransactionDialog
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SmsImportScreen
import com.example.ui.screens.TeachFormatDialog
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.Negative
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentSoft
import com.example.ui.util.LocalCategories
import com.example.ui.util.LocalCategoryUsage
import com.example.ui.util.Formatters

// Screen keys for the main content area: the four tabs plus full screens shown on top of them.
private const val SETTINGS_SCREEN = SETTINGS_TAB
private const val SMS_IMPORT_SCREEN = 4

private fun tabTitle(tab: Int): String = when (tab) {
    1 -> "Transactions"
    2 -> "Insights"
    SETTINGS_SCREEN -> "Settings"
    SMS_IMPORT_SCREEN -> "Import from SMS"
    else -> "Pennywise"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PennywiseApp(
    viewModel: PennywiseViewModel,
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val overlay by viewModel.overlay.collectAsStateWithLifecycle()
    val isOverlayOpen = overlay != null
    val isDataLoaded by viewModel.isDataLoaded.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val pendingRestore by viewModel.pendingRestore.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            viewModel.consumeUserMessage()
            snackbarHostState.showSnackbar(it)
        }
    }
    // Tab 3 is Settings, which is also SETTINGS_SCREEN.
    val currentScreen = when (overlay) {
        Overlay.SMS_IMPORT -> SMS_IMPORT_SCREEN
        null -> selectedTab
    }

    val application = LocalContext.current.applicationContext as Application
    val smsImportViewModel: SmsImportViewModel = composeViewModel(factory = SmsImportViewModel.Factory(application))
    val smsImportState by smsImportViewModel.state.collectAsStateWithLifecycle()
    // Text of the SMS being taught as a new format; null when the dialog is closed.
    var teachText by rememberSaveable { mutableStateOf<String?>(null) }
    val smsTemplates by viewModel.smsTemplates.collectAsStateWithLifecycle()

    val openSmsImport = {
        // Always start from a fresh scan so an old preview is never shown.
        smsImportViewModel.reset()
        viewModel.openSmsImport()
    }

    // Screen data is collected here (not inside each tab) so every flow stays subscribed and
    // already holds its latest value when a tab is opened - no empty frame on tab switch.
    val summary by viewModel.financialSummary.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val filteredTransactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val categoryBreakdowns by viewModel.categoryBreakdowns.collectAsStateWithLifecycle()
    val budgetStatuses by viewModel.budgetStatuses.collectAsStateWithLifecycle()
    val topVendors by viewModel.topVendors.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterType by viewModel.filterType.collectAsStateWithLifecycle()
    val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()

    // Keeps each tab's saveable state (e.g. list scroll positions) alive while the tab is not
    // shown, so switching tabs does not rebuild screens from scratch and jump back to the top.
    val tabStateHolder = rememberSaveableStateHolder()

    // Handle back button: close a full screen first, then return to the Dashboard tab
    BackHandler(enabled = isOverlayOpen || selectedTab != 0) {
        if (isOverlayOpen) viewModel.closeOverlay() else viewModel.setTab(0)
    }

    val categoryUsage by viewModel.categoryUsage.collectAsStateWithLifecycle()

    CompositionLocalProvider(
        LocalCategories provides categories,
        LocalCategoryUsage provides categoryUsage
    ) {
        Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                PageHeader(
                    screen = currentScreen,
                    showBack = isOverlayOpen,
                    onBack = { viewModel.closeOverlay() }
                )
            },
            bottomBar = {
                // Full-screen pages (SMS import) hide the tab bar so the task has the whole screen.
                if (!isOverlayOpen) {
                    PwBottomBar(
                        selectedTab = selectedTab,
                        onSelectTab = { viewModel.setTab(it) },
                        onAdd = { viewModel.openAddDialog() }
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
                        color = Accent,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    AnimatedContent(
                        targetState = currentScreen,
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
                                    onTransactionDelete = { tx -> viewModel.promptDelete(tx) },
                                    onImportSms = openSmsImport,
                                    budgetStatuses = budgetStatuses,
                                    onOpenBudgets = { viewModel.setTab(2) }
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
                                    budgetStatuses = budgetStatuses,
                                    topVendors = topVendors,
                                    onSaveBudget = viewModel::saveBudget,
                                    onRemoveBudget = { viewModel.removeBudget(it) }
                                )

                                SETTINGS_SCREEN -> SettingsScreen(
                                    categories = categories,
                                    onCreateCategory = viewModel::addCustomCategory,
                                    onDeleteCategory = { viewModel.deleteCustomCategory(it) },
                                    pendingRestore = pendingRestore,
                                    onExportBackup = { viewModel.exportBackup(it) },
                                    onPickBackup = { viewModel.readBackup(it) },
                                    onConfirmRestore = { viewModel.confirmRestore() },
                                    onCancelRestore = { viewModel.cancelRestore() },
                                    onEraseAll = { viewModel.eraseAllData() },
                                    onOpenSmsImport = openSmsImport,
                                    smsTemplates = smsTemplates,
                                    onTeachFormat = { teachText = "" },
                                    onDeleteFormat = { viewModel.deleteSmsTemplate(it) }
                                )

                                SMS_IMPORT_SCREEN -> SmsImportScreen(
                                    state = smsImportState,
                                    categories = categories,
                                    hasPermission = { smsImportViewModel.hasPermission() },
                                    onScan = { since ->
                                        smsImportViewModel.scan(since, categories.map { it.name }.toSet())
                                    },
                                    onToggleItem = { smsImportViewModel.toggleItem(it) },
                                    onToggleGroup = { smsImportViewModel.toggleGroup(it) },
                                    onSetCategory = { group, category -> smsImportViewModel.setGroupCategory(group, category) },
                                    onSetSelfTransfer = { group, on -> smsImportViewModel.setGroupSelfTransfer(group, on) },
                                    onImport = {
                                        smsImportViewModel.importSelected { message ->
                                            viewModel.showMessage(message)
                                            viewModel.closeOverlay()
                                        }
                                    },
                                    onStartOver = { smsImportViewModel.reset() },
                                    onTeach = { body -> teachText = body }
                                )
                            }
                        }
                    }
                }
            }

            // Dialogs read their own state so typing in the form does not recompose the whole app.
            DeleteConfirmationHost(viewModel = viewModel)
            SimilarCategoryHost(viewModel = viewModel)

            teachText?.let { initial ->
                TeachFormatDialog(
                    initialText = initial,
                    onSave = { template ->
                        teachText = null
                        viewModel.saveSmsTemplate(template) {
                            // Taught from a scan: scan the same period again with the new format.
                            if (overlay == Overlay.SMS_IMPORT) {
                                smsImportViewModel.rescan(categories.map { it.name }.toSet())
                            }
                        }
                    },
                    onDismiss = { teachText = null }
                )
            }
        }

        // Full-screen form drawn above the Scaffold so the header and tab bar never cover it.
        AddEditTransactionHost(viewModel = viewModel)
        }
    }
}

private const val TAB_FADE_OUT_MS = 90
private const val TAB_FADE_IN_MS = 210

@Composable
private fun AddEditTransactionHost(viewModel: PennywiseViewModel) {
    val isAddEditOpen by viewModel.isAddEditOpen.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = isAddEditOpen,
        enter = slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180))
    ) {
        AddEditTransactionContent(viewModel)
    }
}

@Composable
private fun AddEditTransactionContent(viewModel: PennywiseViewModel) {

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
        categories = LocalCategories.current,
        onCreateCategory = viewModel::addCustomCategory,
        onTypeChange = { viewModel.updateFormType(it) },
        onAmountChange = { viewModel.updateFormAmount(it) },
        onTitleChange = { viewModel.updateFormTitle(it) },
        onCategoryChange = { viewModel.updateFormCategory(it) },
        onDateChange = { viewModel.updateFormDate(it) },
        onNoteChange = { viewModel.updateFormNote(it) },
        onSave = { viewModel.saveTransaction() },
        onDismiss = { viewModel.dismissAddEditDialog() },
        onDelete = editingTransaction?.let { tx ->
            {
                viewModel.dismissAddEditDialog()
                viewModel.promptDelete(tx)
            }
        }
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
                colors = ButtonDefaults.buttonColors(containerColor = Negative),
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

/** After a category change: "Also set 3 more from Some Other T Center to Food & Dining?" */
@Composable
private fun SimilarCategoryHost(viewModel: PennywiseViewModel) {
    val prompt by viewModel.similarPrompt.collectAsStateWithLifecycle()
    val current = prompt ?: return
    // All ticked by default; keyed by the prompt so a new prompt starts fresh.
    var ticked by remember(current) { mutableStateOf(current.transactions.map { it.id }.toSet()) }

    AlertDialog(
        onDismissRequest = { viewModel.dismissSimilarPrompt() },
        modifier = Modifier.testTag("similar_category_dialog"),
        title = {
            Text(
                "Also set ${current.transactions.size} more to ${current.category}?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    "Other transactions from ${current.vendorName}:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(current.transactions, key = { it.id }) { tx ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ticked = if (tx.id in ticked) ticked - tx.id else ticked + tx.id
                                }
                        ) {
                            Checkbox(
                                checked = tx.id in ticked,
                                onCheckedChange = { on -> ticked = if (on) ticked + tx.id else ticked - tx.id },
                                colors = CheckboxDefaults.colors(checkedColor = Accent)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tx.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    Formatters.formatDate(tx.date) + " · now " + tx.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                Formatters.formatCurrency(tx.amount),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.applySimilarCategory(ticked) },
                enabled = ticked.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                modifier = Modifier.testTag("apply_similar_button")
            ) { Text("Update ${ticked.size}", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissSimilarPrompt() }) { Text("Not now") }
        }
    )
}

/** Large left-aligned page title; Home shows a greeting and today's date (reference style). */
@Composable
private fun PageHeader(screen: Int, showBack: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = Space.screen, end = Space.screen, top = Space.m, bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            PwHeaderIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Spacer(modifier = Modifier.width(Space.m))
        }
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                fadeIn(tween(TAB_FADE_IN_MS, delayMillis = TAB_FADE_OUT_MS)).togetherWith(fadeOut(tween(TAB_FADE_OUT_MS)))
            },
            label = "page_header",
            modifier = Modifier.weight(1f)
        ) { target ->
            Column {
                Text(
                    text = if (target == 0) greeting() else tabTitle(target),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Ink,
                    maxLines = 1
                )
                if (target == 0) {
                    Text(
                        text = Formatters.formatLongDate(System.currentTimeMillis()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                }
            }
        }
    }
}

private fun greeting(): String = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

private data class TabItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val tag: String)

private val TAB_ITEMS = listOf(
    TabItem("Home", Icons.Outlined.Home, Icons.Filled.Home, "nav_tab_dashboard"),
    TabItem("Transactions", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong, "nav_tab_transactions"),
    TabItem("Insights", Icons.Outlined.InsertChart, Icons.Filled.InsertChart, "nav_tab_analytics"),
    TabItem("Settings", Icons.Outlined.Settings, Icons.Filled.Settings, "nav_tab_settings")
)

/**
 * Bottom bar from the references: outline icons, the selected tab in a blue rounded square,
 * and a raised dark "+" in the middle for adding a transaction.
 */
@Composable
private fun PwBottomBar(selectedTab: Int, onSelectTab: (Int) -> Unit, onAdd: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bottom_navigation_bar"),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            modifier = Modifier
                .padding(top = 20.dp) // room for the raised + button
                .fillMaxWidth()
                .softShadow(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), 8.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(SurfaceWhite)
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = Space.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TAB_ITEMS.forEachIndexed { index, item ->
                if (index == 2) Spacer(modifier = Modifier.weight(1f)) // centre slot for +
                BottomTab(
                    item = item,
                    selected = selectedTab == index,
                    onClick = { onSelectTab(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Box(
            modifier = Modifier
                .size(60.dp)
                .softShadow(CircleShape, 10.dp)
                .clip(CircleShape)
                .background(Ink)
                .clickable(onClick = onAdd)
                .testTag("fab_add_transaction"),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add transaction", tint = SurfaceWhite, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun BottomTab(item: TabItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val background by animateColorAsState(if (selected) Accent else Color.Transparent, label = "tab_bg")
    val tint by animateColorAsState(if (selected) SurfaceWhite else Ink, label = "tab_tint")
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .testTag(item.tag),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (selected) item.selectedIcon else item.icon,
                contentDescription = item.label,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
