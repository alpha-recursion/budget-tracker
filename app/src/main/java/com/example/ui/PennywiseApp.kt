package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import com.example.ui.theme.PwMotion
import com.example.ui.theme.Navy
import com.example.ui.theme.AccentLight
import com.example.ui.components.pressScale
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.LocalIndication
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.reminders.ReminderNotifier
import com.example.data.sms.AutoCaptureNotifier
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.example.data.model.TransactionType
import com.example.ui.screens.AddEditTransactionDialog
import com.example.ui.screens.LendingPersonDialog
import com.example.ui.screens.LendingScreen
import com.example.ui.screens.GoalsScreen
import com.example.ui.screens.CelebrationOverlay
import com.example.ui.screens.BadgesScreen
import com.example.ui.screens.ChallengesScreen
import com.example.ui.screens.RecurringScreen
import com.example.ui.screens.PaySettingsDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import com.example.domain.Challenges
import com.example.domain.ReportCard
import com.example.ui.util.CelebrationSound
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
private const val LENDING_SCREEN = 5
private const val GOALS_SCREEN = 6
private const val BADGES_SCREEN = 7
private const val CHALLENGES_SCREEN = 8
private const val RECURRING_SCREEN = 9

private fun tabTitle(tab: Int): String = when (tab) {
    1 -> "Transactions"
    2 -> "Insights"
    SETTINGS_SCREEN -> "Settings"
    SMS_IMPORT_SCREEN -> "Import from SMS"
    LENDING_SCREEN -> "Money lent"
    GOALS_SCREEN -> "Savings goals"
    BADGES_SCREEN -> "Badges"
    CHALLENGES_SCREEN -> "Challenges"
    RECURRING_SCREEN -> "Recurring payments"
    else -> "Pennywise"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PennywiseApp(
    viewModel: PennywiseViewModel,
    modifier: Modifier = Modifier,
    /** What a notification asked to open (see AutoCaptureNotifier), handled once. */
    openRequest: String? = null,
    onOpenRequestHandled: () -> Unit = {}
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
        Overlay.LENDING -> LENDING_SCREEN
        Overlay.GOALS -> GOALS_SCREEN
        Overlay.BADGES -> BADGES_SCREEN
        Overlay.CHALLENGES -> CHALLENGES_SCREEN
        Overlay.RECURRING -> RECURRING_SCREEN
        null -> selectedTab
    }
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val selectedGoal by viewModel.selectedGoal.collectAsStateWithLifecycle()
    val savingStreak by viewModel.savingStreak.collectAsStateWithLifecycle()
    val dailyReview by viewModel.dailyReview.collectAsStateWithLifecycle()
    val recurringItems by viewModel.recurring.collectAsStateWithLifecycle()
    val recurringSuggestions by viewModel.recurringSuggestions.collectAsStateWithLifecycle()
    val roundUps by viewModel.roundUps.collectAsStateWithLifecycle()
    val roundUpJar by viewModel.roundUpJar.collectAsStateWithLifecycle()
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()
    val celebrationFeedback by viewModel.celebrationFeedback.collectAsStateWithLifecycle()
    val challengeResults by viewModel.challengeResults.collectAsStateWithLifecycle()
    val challengeSuggestions by viewModel.challengeSuggestions.collectAsStateWithLifecycle()
    val reportCards by viewModel.reportCards.collectAsStateWithLifecycle()
    val selectedReport by viewModel.selectedReport.collectAsStateWithLifecycle()
    val quickChips by viewModel.quickChips.collectAsStateWithLifecycle()
    val payCycle by viewModel.payCycle.collectAsStateWithLifecycle()
    val planning by viewModel.planning.collectAsStateWithLifecycle()
    val safeToSpend by viewModel.safeToSpend.collectAsStateWithLifecycle()
    val cycleLeftover by viewModel.cycleLeftover.collectAsStateWithLifecycle()
    val budgetSuggestion by viewModel.budgetSuggestion.collectAsStateWithLifecycle()
    val canSuggestBudgets by viewModel.canSuggestBudgets.collectAsStateWithLifecycle()
    val undoQuickAdd by viewModel.undoQuickAdd.collectAsStateWithLifecycle()
    var showPayDialog by rememberSaveable { mutableStateOf(false) }
    // Quick add: a snackbar with Undo for every one-tap expense.
    LaunchedEffect(undoQuickAdd) {
        val event = undoQuickAdd ?: return@LaunchedEffect
        viewModel.consumeUndoQuickAdd()
        val result = snackbarHostState.showSnackbar(event.message, actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoQuickAdd(event.transactionId)
    }
    val lendingBalances by viewModel.lendingBalances.collectAsStateWithLifecycle()
    val lendingPerson by viewModel.lendingPerson.collectAsStateWithLifecycle()
    // Back from a person's history returns to the people list before leaving the screen.
    val goBack = {
        when {
            overlay == Overlay.LENDING && lendingPerson != null -> viewModel.showLendingPerson(null)
            overlay == Overlay.GOALS && selectedGoal != null -> viewModel.showGoal(null)
            overlay == Overlay.BADGES -> viewModel.closeBadges()
            overlay == Overlay.CHALLENGES && selectedReport != null -> viewModel.showReport(null)
            overlay == Overlay.CHALLENGES -> viewModel.closeChallenges()
            isOverlayOpen -> viewModel.closeOverlay()
            else -> viewModel.setTab(0)
        }
    }

    val application = LocalContext.current.applicationContext as Application
    val smsImportViewModel: SmsImportViewModel = composeViewModel(factory = SmsImportViewModel.Factory(application))
    val smsImportState by smsImportViewModel.state.collectAsStateWithLifecycle()
    // Text of the SMS being taught as a new format; null when the dialog is closed.
    var teachText by rememberSaveable { mutableStateOf<String?>(null) }
    val smsTemplates by viewModel.smsTemplates.collectAsStateWithLifecycle()
    val otherFixes by viewModel.otherFixes.collectAsStateWithLifecycle()

    val openSmsImport = {
        // Always start from a fresh scan so an old preview is never shown.
        smsImportViewModel.reset()
        viewModel.openSmsImport()
    }

    // Live capture: payments waiting for review open straight into the review step.
    val captureState by viewModel.captureState.collectAsStateWithLifecycle()
    val autoCaptureEnabled by viewModel.autoCaptureEnabled.collectAsStateWithLifecycle()
    val reviewCaptured = {
        openSmsImport()
        // Without SMS access the import screen shows its permission step instead.
        if (smsImportViewModel.hasPermission()) smsImportViewModel.reviewPending()
    }
    LifecycleResumeEffect(Unit) {
        viewModel.onAppForeground()
        onPauseOrDispose { }
    }
    LaunchedEffect(openRequest) {
        when (openRequest) {
            AutoCaptureNotifier.OPEN_SMS_REVIEW -> reviewCaptured()
            AutoCaptureNotifier.OPEN_TRANSACTIONS -> viewModel.setTab(1)
            ReminderNotifier.OPEN_LENDING -> viewModel.openLending()
            ReminderNotifier.OPEN_REVIEW -> viewModel.setTab(0)
            ReminderNotifier.OPEN_RECURRING -> viewModel.openRecurring()
            else -> return@LaunchedEffect
        }
        onOpenRequestHandled()
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
        goBack()
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
                    onBack = goBack
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
                                    onOpenBudgets = { viewModel.setTab(2) },
                                    captureState = captureState,
                                    onReviewCaptured = reviewCaptured,
                                    onDismissCaptured = { viewModel.dismissPendingCapture() },
                                    lending = lendingBalances,
                                    onOpenLending = { viewModel.openLending(it) },
                                    goals = goals,
                                    onOpenGoals = { viewModel.openGoals(it) },
                                    savingStreakWeeks = savingStreak.current,
                                    dailyReview = dailyReview,
                                    onConfirmDailyReview = { viewModel.confirmDailyReview() },
                                    recurringItems = recurringItems,
                                    onOpenRecurring = { viewModel.openRecurring() },
                                    reportReady = lastMonthReportIfNew(reportCards),
                                    onOpenReport = { viewModel.openChallenges(fromGoals = false, reportMonth = it) },
                                    quickChips = quickChips,
                                    onQuickAdd = { viewModel.quickAdd(it) },
                                    onQuickAddWithChanges = { viewModel.quickAddWithChanges(it) },
                                    onPinChip = { viewModel.pinChip(it) },
                                    onUnpinChip = { viewModel.unpinChip(it) },
                                    onHideChip = { viewModel.hideChip(it) },
                                    payCycle = payCycle,
                                    showSalaryCard = payCycle?.let { it.salaryDue && planning.salaryDismissedCycle != it.cycleStart } == true,
                                    salaryAmount = planning.pay.monthlyIncome,
                                    onAddSalary = { viewModel.addSalary() },
                                    onSalaryOtherAmount = { viewModel.addSalaryWithChanges() },
                                    onDismissSalary = { viewModel.dismissSalaryCard() },
                                    onEditPay = { showPayDialog = true },
                                    safeToSpend = safeToSpend,
                                    cycleLeftover = cycleLeftover,
                                    onMoveLeftover = { viewModel.moveLeftoverToSavings() },
                                    onDismissLeftover = { viewModel.dismissLeftover() }
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
                                    transactions = allTransactions,
                                    onSaveBudget = viewModel::saveBudget,
                                    onRemoveBudget = { viewModel.removeBudget(it) },
                                    onSuggestBudgets = if (canSuggestBudgets) { { viewModel.openBudgetSuggestion() } } else null,
                                    budgetSuggestion = budgetSuggestion,
                                    monthlyIncome = planning.pay.monthlyIncome,
                                    savingsNote = planning.pay.takeIf { it.monthlyIncome > 0 }?.let {
                                        "${Formatters.formatWholeRupees(it.savingsTarget)} a month saved first (${it.strategy.label})"
                                    },
                                    onApplyBudgetSuggestion = { viewModel.applyBudgetSuggestion() },
                                    onDismissBudgetSuggestion = { viewModel.closeBudgetSuggestion() }
                                )

                                SETTINGS_SCREEN -> SettingsScreen(
                                    categories = categories,
                                    onCreateCategory = viewModel::addCustomCategory,
                                    onDeleteCategory = { viewModel.deleteCustomCategory(it) },
                                    onUpdateCategoryStyle = viewModel::updateCategoryStyle,
                                    otherFixes = otherFixes,
                                    onOpenOtherFixes = { viewModel.openOtherFixes() },
                                    onApplyOtherFixes = { viewModel.applyOtherFixes(it) },
                                    onCloseOtherFixes = { viewModel.closeOtherFixes() },
                                    pendingRestore = pendingRestore,
                                    onExportBackup = { viewModel.exportBackup(it) },
                                    onPickBackup = { viewModel.readBackup(it) },
                                    onConfirmRestore = { viewModel.confirmRestore() },
                                    onCancelRestore = { viewModel.cancelRestore() },
                                    onEraseAll = { viewModel.eraseAllData() },
                                    onOpenSmsImport = openSmsImport,
                                    smsTemplates = smsTemplates,
                                    onTeachFormat = { teachText = "" },
                                    onDeleteFormat = { viewModel.deleteSmsTemplate(it) },
                                    autoCaptureEnabled = autoCaptureEnabled,
                                    onAutoCaptureChange = { viewModel.setAutoCapture(it) },
                                    onAutoCapturePermissionDenied = {
                                        viewModel.showMessage("Auto-detect needs SMS permission")
                                    },
                                    achievements = achievements,
                                    onOpenBadges = { viewModel.openBadges(fromGoals = false) },
                                    recurringCount = recurringItems.count { it.active },
                                    onOpenRecurring = { viewModel.openRecurring() },
                                    celebrationFeedback = celebrationFeedback,
                                    onCelebrationFeedbackChange = { viewModel.setCelebrationFeedback(it) },
                                    paySummary = if (planning.pay.isSet) {
                                        "${Formatters.formatCurrency(planning.pay.monthlyIncome)} on day ${planning.pay.payday}"
                                    } else {
                                        null
                                    },
                                    onEditPay = { showPayDialog = true }
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
                                    onTeach = { body -> teachText = body },
                                    people = lendingBalances,
                                    onSetLending = { group, choice -> smsImportViewModel.setGroupLending(group, choice) },
                                    onPickStatement = { uri ->
                                        smsImportViewModel.importStatement(uri, categories.map { it.name }.toSet())
                                    }
                                )

                                LENDING_SCREEN -> LendingScreen(
                                    balances = lendingBalances,
                                    selectedKey = lendingPerson,
                                    onSelectPerson = { viewModel.showLendingPerson(it) },
                                    onAddEntry = { person, kind, amount, date, due, note ->
                                        viewModel.addDebt(person, kind, amount, date, due, note)
                                    },
                                    onSettleUp = { viewModel.settleUp(it) },
                                    onDeleteEntry = { viewModel.deleteDebt(it) },
                                    onDeletePerson = { viewModel.deletePerson(it) }
                                )

                                GOALS_SCREEN -> GoalsScreen(
                                    goals = goals,
                                    selectedId = selectedGoal,
                                    onSelect = { viewModel.showGoal(it) },
                                    onSaveGoal = { id, name, target, icon, color, date, saved ->
                                        viewModel.saveGoal(id, name, target, icon, color, date, saved)
                                    },
                                    onMoveMoney = { id, amount, out, note -> viewModel.moveGoalMoney(id, amount, out, note) },
                                    onDeleteEntry = { viewModel.deleteGoalEntry(it) },
                                    onDeleteGoal = { viewModel.deleteGoal(it) },
                                    onAddCheckpoint = { id, name, amount -> viewModel.addCheckpoint(id, name, amount) },
                                    onDeleteCheckpoint = { viewModel.deleteCheckpoint(it) },
                                    onBuy = { id, amount, category -> viewModel.buyGoal(id, amount, category) },
                                    streak = savingStreak,
                                    roundUps = roundUps,
                                    jar = roundUpJar,
                                    onRoundUpsEnabled = { viewModel.setRoundUpsEnabled(it) },
                                    onRoundUpUnit = { viewModel.setRoundUpUnit(it) },
                                    onMoveJar = { viewModel.moveJarToGoal(it) },
                                    achievements = achievements,
                                    onOpenBadges = { viewModel.openBadges(fromGoals = true) },
                                    challenges = challengeResults,
                                    hasChallengeSuggestions = challengeSuggestions.isNotEmpty(),
                                    onOpenChallenges = { viewModel.openChallenges(fromGoals = true) }
                                )

                                BADGES_SCREEN -> BadgesScreen(progress = achievements)

                                RECURRING_SCREEN -> RecurringScreen(
                                    entries = recurringItems,
                                    suggestions = recurringSuggestions,
                                    categories = categories,
                                    onSave = { viewModel.saveRecurring(it) },
                                    onDelete = { viewModel.deleteRecurring(it) }
                                )

                                CHALLENGES_SCREEN -> ChallengesScreen(
                                    results = challengeResults,
                                    suggestions = challengeSuggestions,
                                    reports = reportCards,
                                    selectedReport = selectedReport,
                                    onPick = { viewModel.pickChallenge(it) },
                                    onRemove = { viewModel.removeChallenge(it) },
                                    onSelectReport = { viewModel.showReport(it) }
                                )
                            }
                        }
                    }
                }
            }

            if (showPayDialog) {
                PaySettingsDialog(
                    current = planning.pay,
                    onSave = { income, day, strategy ->
                        viewModel.savePaySettings(income, day, strategy)
                        showPayDialog = false
                    },
                    onDismiss = { showPayDialog = false }
                )
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

        // Goal milestone reached: confetti over everything.
        val celebration by viewModel.celebration.collectAsStateWithLifecycle()
        celebration?.let {
            val haptics = LocalHapticFeedback.current
            val context = LocalContext.current
            // Keyed so each queued celebration plays its own confetti, sound and vibration.
            androidx.compose.runtime.key(it) {
                CelebrationOverlay(
                    emoji = it.emoji,
                    title = it.title,
                    message = it.message,
                    onShown = {
                        if (celebrationFeedback) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            CelebrationSound.play(context)
                        }
                    },
                    onDismiss = { viewModel.dismissCelebration() }
                )
            }
        }
        }
    }
}

/** Last month's report card during the first 7 days of a month; otherwise null. */
private fun lastMonthReportIfNew(reports: List<ReportCard>, nowMs: Long = System.currentTimeMillis()): ReportCard? {
    val thisMonth = Challenges.month(nowMs)
    if (nowMs - thisMonth.start >= 7 * 86_400_000L) return null
    val last = Challenges.previous(thisMonth)
    return reports.firstOrNull { it.month.key == last.key }
}

private const val TAB_FADE_OUT_MS = 90
private const val TAB_FADE_IN_MS = 210

@Composable
private fun AddEditTransactionHost(viewModel: PennywiseViewModel) {
    val isAddEditOpen by viewModel.isAddEditOpen.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = isAddEditOpen,
        // The sheet slides itself up over a fading scrim; closing slides it back down.
        enter = fadeIn(tween(200)),
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
    val lendingBalances by viewModel.lendingBalances.collectAsStateWithLifecycle()
    var moveToLending by remember { mutableStateOf(false) }

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
        },
        onMoveToLending = editingTransaction?.let { { moveToLending = true } }
    )

    val tx = editingTransaction
    if (moveToLending && tx != null) {
        val type = if (tx.type == TransactionType.INCOME.name) TransactionType.INCOME else TransactionType.EXPENSE
        LendingPersonDialog(
            type = type,
            payee = tx.title,
            people = lendingBalances,
            amount = tx.amount,
            onConfirm = { person, kind ->
                moveToLending = false
                viewModel.moveTransactionToLending(tx, person, kind)
            },
            onDismiss = { moveToLending = false }
        )
    }
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
                if (target == 0) {
                    // v4 Home: small date above a 20sp greeting.
                    Text(
                        text = Formatters.formatLongDate(System.currentTimeMillis()),
                        style = MaterialTheme.typography.labelMedium,
                        color = InkSecondary
                    )
                    Text(text = greeting(), style = MaterialTheme.typography.headlineSmall, color = Ink, maxLines = 1)
                } else {
                    Text(text = tabTitle(target), style = MaterialTheme.typography.headlineLarge, color = Ink, maxLines = 1)
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
    TabItem("Activity", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong, "nav_tab_transactions"),
    TabItem("Insights", Icons.Outlined.InsertChart, Icons.Filled.InsertChart, "nav_tab_analytics"),
    TabItem("Settings", Icons.Outlined.Settings, Icons.Filled.Settings, "nav_tab_settings")
)

/**
 * Bottom bar from the references: outline icons, the selected tab in a blue rounded square,
 * and a raised dark "+" in the middle for adding a transaction.
 */
@Composable
private fun PwBottomBar(selectedTab: Int, onSelectTab: (Int) -> Unit, onAdd: () -> Unit) {
    // Floating navy pill (v4): four tabs around a raised gradient + button; the active tab
    // shows its label.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = Space.l, end = Space.l, bottom = 14.dp, top = Space.s)
            .height(64.dp)
            .shadow(14.dp, RoundedCornerShape(32.dp), ambientColor = Navy.copy(alpha = 0.2f), spotColor = Navy.copy(alpha = 0.35f))
            .clip(RoundedCornerShape(32.dp))
            .background(Navy)
            .padding(horizontal = Space.s)
            .testTag("bottom_navigation_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TAB_ITEMS.forEachIndexed { index, item ->
            if (index == 2) AddButton(onAdd)
            BottomTab(item = item, selected = selectedTab == index, onClick = { onSelectTab(index) })
        }
    }
}

@Composable
private fun AddButton(onAdd: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(52.dp)
            .pressScale(interaction, 0.94f)
            .shadow(10.dp, CircleShape, ambientColor = Accent.copy(alpha = 0.3f), spotColor = Accent.copy(alpha = 0.55f))
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(AccentLight, Accent)))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onAdd)
            .testTag("fab_add_transaction"),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Add, contentDescription = "Add transaction", tint = SurfaceWhite, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun BottomTab(item: TabItem, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val background by animateColorAsState(
        if (selected) SurfaceWhite.copy(alpha = 0.14f) else Color.Transparent, PwMotion.fade(), label = "tab_bg"
    )
    val tint by animateColorAsState(
        if (selected) SurfaceWhite else SurfaceWhite.copy(alpha = 0.62f), PwMotion.fade(), label = "tab_tint"
    )
    Row(
        modifier = Modifier
            .height(48.dp)
            .pressScale(interaction, 0.95f)
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = if (selected) 14.dp else Space.m, end = if (selected) Space.l else Space.m)
            .testTag(item.tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (selected) item.selectedIcon else item.icon,
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        androidx.compose.animation.AnimatedVisibility(
            visible = selected,
            enter = androidx.compose.animation.fadeIn(PwMotion.fade(300)) +
                androidx.compose.animation.expandHorizontally(PwMotion.fade(300)),
            exit = androidx.compose.animation.fadeOut(PwMotion.fade(150)) +
                androidx.compose.animation.shrinkHorizontally(PwMotion.fade(150))
        ) {
            Text(
                item.label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp),
                color = SurfaceWhite,
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}
