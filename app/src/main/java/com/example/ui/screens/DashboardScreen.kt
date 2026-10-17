package com.example.ui.screens

import com.example.ui.components.shimmer
import com.example.ui.components.rememberPulse
import com.example.ui.components.rememberGrowth
import com.example.ui.components.animatedAmount
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DonutSmall
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.RecurringEntity
import com.example.data.local.TransactionEntity
import com.example.domain.CategoryBreakdown
import com.example.domain.CategoryBudgetStatus
import com.example.domain.DailyReviewState
import com.example.domain.FinancialCalculations
import com.example.domain.FinancialSummary
import com.example.domain.GoalProgress
import com.example.domain.Goals
import com.example.domain.HomeSpend
import com.example.domain.PayCycleStatus
import com.example.domain.SafeToSpend
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.PositiveSoft
import androidx.compose.material3.TextButton
import com.example.domain.CycleLeftover
import com.example.domain.PersonBalance
import com.example.domain.QuickChip
import com.example.domain.ReportCard
import com.example.domain.SpendKpi
import com.example.domain.SpendPeriod
import com.example.domain.sms.CaptureState
import com.example.data.model.CategoryInfo
import com.example.ui.components.CardShape
import com.example.ui.components.HeroShape
import com.example.ui.components.PillShape
import com.example.ui.components.PwCard
import com.example.ui.components.PwDivider
import com.example.ui.components.PwPrimaryButton
import com.example.ui.components.PwSegmented
import com.example.ui.components.Space
import com.example.ui.components.TransactionItemRow
import com.example.ui.components.softShadow
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentBright
import com.example.ui.theme.AccentSoft
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.Navy
import com.example.ui.theme.Negative
import com.example.ui.theme.Outline
import com.example.ui.theme.Positive
import com.example.ui.theme.PositiveText
import com.example.ui.theme.SurfaceWhite
import com.example.ui.theme.TrackSoft
import com.example.ui.theme.Warning
import com.example.ui.theme.WarningText
import com.example.ui.util.Formatters
import com.example.ui.util.LocalCategories
import com.example.ui.util.getCategoryIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home (redesign v4): spend hero with period switch and shortcuts, check-in strip, this month's
 * budget, spending mix, savings goals and recent transactions. Some parameters are no longer
 * shown here (quick-add, payday, SMS review, report and salary cards, lending, recurring); they
 * stay in the signature so the caller does not change, and their screens are reached elsewhere.
 */
@Composable
fun DashboardScreen(
    summary: FinancialSummary,
    breakdowns: List<CategoryBreakdown>,
    recentTransactions: List<TransactionEntity>,
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onViewAllTransactions: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    onTransactionDelete: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier,
    onImportSms: () -> Unit = {},
    budgetStatuses: List<CategoryBudgetStatus> = emptyList(),
    onOpenBudgets: () -> Unit = {},
    captureState: CaptureState = CaptureState(),
    onReviewCaptured: () -> Unit = {},
    onDismissCaptured: () -> Unit = {},
    lending: List<PersonBalance> = emptyList(),
    onOpenLending: (personKey: String?) -> Unit = {},
    goals: List<GoalProgress> = emptyList(),
    onOpenGoals: (goalId: Long?) -> Unit = {},
    savingStreakWeeks: Int = 0,
    dailyReview: DailyReviewState = DailyReviewState(),
    onConfirmDailyReview: () -> Unit = {},
    recurringItems: List<RecurringEntity> = emptyList(),
    onOpenRecurring: () -> Unit = {},
    /** Last month's report card, shown during the first week of a new month. */
    reportReady: ReportCard? = null,
    onOpenReport: (monthKey: Int) -> Unit = {},
    quickChips: List<QuickChip> = emptyList(),
    onQuickAdd: (QuickChip) -> Unit = {},
    onQuickAddWithChanges: (QuickChip) -> Unit = {},
    onPinChip: (QuickChip) -> Unit = {},
    onUnpinChip: (QuickChip) -> Unit = {},
    onHideChip: (QuickChip) -> Unit = {},
    payCycle: PayCycleStatus? = null,
    showSalaryCard: Boolean = false,
    salaryAmount: Double = 0.0,
    onAddSalary: () -> Unit = {},
    onSalaryOtherAmount: () -> Unit = {},
    onDismissSalary: () -> Unit = {},
    onEditPay: () -> Unit = {},
    /** What is safe to spend today; null without income or budgets. */
    safeToSpend: SafeToSpend? = null,
    /** Last pay cycle's unspent money, offered once; null when there is none. */
    cycleLeftover: CycleLeftover? = null,
    onMoveLeftover: () -> Unit = {},
    onDismissLeftover: () -> Unit = {}
) {
    var periodIndex by rememberSaveable { mutableIntStateOf(SpendPeriod.MONTH.ordinal) }
    val period = SpendPeriod.entries[periodIndex]
    val now = System.currentTimeMillis()
    val kpi = remember(recentTransactions, period) { HomeSpend.kpi(recentTransactions, period, now) }
    val categories = LocalCategories.current
    val monthMix = remember(recentTransactions, categories) {
        FinancialCalculations.calculateCategoryBreakdown(FinancialCalculations.filterToMonth(recentTransactions, now)) {
            CategoryInfo.resolve(it, categories)
        }
    }
    val activeGoals = goals.filter { it.goal.purchasedAt == null }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(start = Space.screen, end = Space.screen, top = Space.xs, bottom = Space.xxl)
    ) {
        item(key = "hero") {
            SpendHero(
                kpi = kpi,
                periodIndex = periodIndex,
                onPeriod = { periodIndex = it },
                onImportSms = onImportSms,
                onLending = { onOpenLending(null) },
                onGoals = { onOpenGoals(null) },
                onRecurring = onOpenRecurring
            )
        }
        item(key = "daily_review") {
            Spacer(modifier = Modifier.height(Space.m))
            TodayCard(review = dailyReview, safe = safeToSpend, onConfirm = onConfirmDailyReview, onSetIncome = onEditPay)
        }
        cycleLeftover?.let { leftover ->
            item(key = "leftover") {
                Spacer(modifier = Modifier.height(Space.s))
                LeftoverCard(leftover, onMoveLeftover, onDismissLeftover)
            }
        }

        item(key = "budget") {
            SectionHeader(
                title = SimpleDateFormat("MMMM", Locale.US).format(Date(now)) + " budget",
                action = "Manage",
                onAction = onOpenBudgets
            )
            if (budgetStatuses.isEmpty()) NoBudgetsCard(onOpenBudgets) else BudgetRingCard(budgetStatuses)
        }

        if (monthMix.isNotEmpty()) {
            item(key = "mix") {
                SectionHeader(title = "Spending mix", action = "Details", onAction = onOpenBudgets)
                SpendingMixCard(monthMix)
            }
        }

        item(key = "goals") {
            val streak = if (savingStreakWeeks > 0) " · 🔥 $savingStreakWeeks wk${if (savingStreakWeeks == 1) "" else "s"}" else ""
            SectionHeader(
                title = "Savings goals",
                meta = if (activeGoals.isEmpty()) null else "${Formatters.formatCurrency(Goals.totalSetAside(goals))} set aside$streak",
                action = if (activeGoals.isEmpty()) "Open" else null,
                onAction = { onOpenGoals(null) }
            )
            GoalTiles(activeGoals.take(2), onOpenGoals)
        }

        item(key = "recent") {
            SectionHeader(title = "Recent", action = "See all", onAction = onViewAllTransactions)
            RecentCard(recentTransactions.take(4), onTransactionClick, onTransactionDelete, onAddExpense)
        }
    }
}

/** "October budget" with an action on the right; 22dp above, 8dp below, as in the design. */
@Composable
private fun SectionHeader(title: String, meta: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.xs, end = Space.xs, top = 22.dp, bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.weight(1f))
        if (meta != null) {
            Text(meta, style = MaterialTheme.typography.bodySmall, color = InkSecondary, maxLines = 1)
        }
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                color = Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(Space.xs)
            )
        }
    }
}

// --- Hero ---

@Composable
private fun SpendHero(
    kpi: SpendKpi,
    periodIndex: Int,
    onPeriod: (Int) -> Unit,
    onImportSms: () -> Unit,
    onLending: () -> Unit,
    onGoals: () -> Unit,
    onRecurring: () -> Unit
) {
    val text = heroText(kpi)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(HeroShape, 14.dp)
            .clip(HeroShape)
            .drawBehind {
                drawRect(Navy)
                // Purple glow from the top right and a deep violet one from the bottom left.
                drawRect(
                    Brush.radialGradient(
                        listOf(AccentBright, AccentBright.copy(alpha = 0f)),
                        center = Offset(size.width, 0f),
                        radius = size.width * 0.75f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFF2E1A5E), Color(0x002E1A5E)),
                        center = Offset(0f, size.height),
                        radius = size.width * 0.6f
                    )
                )
            }
            .padding(horizontal = 18.dp, vertical = Space.l)
            .testTag("balance_card"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PwSegmented(
            options = listOf("Today", "This week", "This month"),
            selectedIndex = periodIndex,
            onSelect = onPeriod,
            dark = true,
            height = 30.dp,
            modifier = Modifier.testTag("hero_period")
        )
        // The tag sits beside the small label, so the big amount and the line under it get the
        // full width (a long tag like "Overspent ₹3,868" used to cut them off).
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = SurfaceWhite.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(Space.s))
                Text(
                    text.tag,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                    color = text.tagColors.second,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(PillShape)
                        .background(text.tagColors.first)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("hero_tag")
                )
            }
            Text(
                // Rounded to the rupee, so no ".00".
                Formatters.formatWholeRupees(animatedAmount(kpi.spent)),
                style = MaterialTheme.typography.displayMedium,
                color = SurfaceWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("net_balance_text")
            )
            Text(
                text.sub,
                style = MaterialTheme.typography.bodySmall,
                color = SurfaceWhite.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        HeroBars(kpi)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind { drawLine(SurfaceWhite.copy(alpha = 0.12f), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                .padding(top = 14.dp)
        ) {
            HeroAction(Icons.Default.Sms, "Import SMS", onImportSms, Modifier.weight(1f))
            HeroAction(Icons.Default.Handshake, "Lending", onLending, Modifier.weight(1f))
            HeroAction(Icons.Default.Savings, "Goals", onGoals, Modifier.weight(1f))
            HeroAction(Icons.Default.EventRepeat, "Recurring", onRecurring, Modifier.weight(1f))
        }
    }
}

private class HeroText(val label: String, val sub: String, val tag: String, val tagColors: Pair<Color, Color>)

private val ToneGood = Color(0x2E4ADE80) to Color(0xFF86EFAC)
private val ToneWarn = Color(0x33F5A524) to Color(0xFFFCD34D)
private val ToneNeutral = SurfaceWhite.copy(alpha = 0.14f) to SurfaceWhite

private fun heroText(kpi: SpendKpi): HeroText {
    fun payments(n: Int) = "$n payment${if (n == 1) "" else "s"}"
    return when (kpi.period) {
        SpendPeriod.TODAY -> {
            val below = kpi.spent <= kpi.averagePerDay
            HeroText(
                label = "Spent today",
                sub = "${payments(kpi.payments)} · avg ${Formatters.formatWholeRupees(kpi.averagePerDay)}/day",
                tag = when {
                    kpi.spent == 0.0 -> "No spend yet"
                    below -> "Below your average"
                    else -> "Above your average"
                },
                tagColors = if (below) ToneGood else ToneWarn
            )
        }
        SpendPeriod.WEEK -> {
            val from = SimpleDateFormat("EEE d", Locale.US).format(Date(kpi.weekStart))
            val to = SimpleDateFormat("EEE d MMM", Locale.US).format(Date(kpi.weekEnd))
            HeroText(
                label = "Spent this week",
                sub = "$from – $to · ${payments(kpi.payments)}",
                tag = "${Formatters.formatWholeRupees(kpi.perDayThisWeek)} / day",
                tagColors = ToneNeutral
            )
        }
        SpendPeriod.MONTH -> {
            val rate = kpi.savingRate
            HeroText(
                label = "Spent in " + SimpleDateFormat("MMMM", Locale.US).format(Date(kpi.monthStart)),
                sub = "Income ${Formatters.formatCurrency(kpi.monthIncome)} · ${kpi.daysLeftInMonth} day${if (kpi.daysLeftInMonth == 1) "" else "s"} left",
                tag = when {
                    rate == null -> "No income yet"
                    // A negative percentage of a small income reads as nonsense ("-2483%").
                    rate < 0 -> "Overspent ${Formatters.formatWholeRupees(kpi.spent - kpi.monthIncome)}"
                    else -> "Saving ${Math.round(rate)}%"
                },
                tagColors = if (rate != null && rate >= 20.0) ToneGood else ToneWarn
            )
        }
    }
}

@Composable
private fun HeroBars(kpi: SpendKpi) {
    val max = (kpi.bars.maxOfOrNull { it.amount } ?: 0.0).coerceAtLeast(1.0)
    val grow = rememberGrowth(kpi.period, durationMs = 550)
    val pulse = rememberPulse()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp), // dot 14 + gap 5 + bar 42 + gap 5 + label ~14
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        kpi.bars.forEachIndexed { index, bar ->
            // Staggered growth: each bar starts a little after the one before.
            val g = ((grow * 1.56f) - 0.07f * minOf(index, 8)).coerceIn(0f, 1f)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Bottom)
            ) {
                if (bar.current) {
                    // Fixed-size slot: the pulse is a scale, so it never changes the layout.
                    Box(modifier = Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .graphicsLayer {
                                    val s = (6 + 8 * pulse) / 14f
                                    scaleX = s
                                    scaleY = s
                                }
                                .clip(CircleShape)
                                .background(SurfaceWhite.copy(alpha = 0.6f * (1f - pulse)))
                        )
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(SurfaceWhite))
                    }
                }
                val color = when {
                    bar.future -> SurfaceWhite.copy(alpha = 0.10f)
                    bar.current -> SurfaceWhite
                    else -> SurfaceWhite.copy(alpha = 0.38f)
                }
                Box(
                    modifier = Modifier
                        // Cap first: after fillMaxWidth the cap could not shrink the bar.
                        .widthIn(max = 26.dp)
                        .fillMaxWidth()
                        .height((42 * (bar.amount / max) * g).toFloat().coerceAtLeast(3f).dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(color)
                )
                Text(
                    bar.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = if (bar.current) SurfaceWhite else SurfaceWhite.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun HeroAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(SurfaceWhite.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(21.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
            color = SurfaceWhite.copy(alpha = 0.9f),
            maxLines = 1
        )
    }
}

// --- Budget ---

@Composable
private fun BudgetRingCard(statuses: List<CategoryBudgetStatus>) {
    val limit = statuses.sumOf { it.budgetLimit }
    val spent = statuses.sumOf { it.spent }
    val pct = if (limit > 0) spent / limit * 100 else 0.0
    val over = statuses.count { it.isOverBudget }
    val ringColor = when {
        pct > 100 -> Negative
        pct > 80 -> Warning
        else -> Accent
    }
    val grow = rememberGrowth(statuses.size)
    PwCard(modifier = Modifier.testTag("budget_card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 8.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    drawArc(TrackSoft, -90f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                    drawArc(
                        ringColor, -90f, (pct.coerceAtMost(100.0) * 3.6 * grow).toFloat(), false,
                        topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke)
                    )
                }
                Text("${Math.round(pct * grow)}%", style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp), color = Ink)
            }
            Spacer(modifier = Modifier.width(Space.l))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Spent of ${Formatters.formatCurrency(limit)}", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                Text(Formatters.formatCurrency(spent), style = MaterialTheme.typography.headlineMedium, color = Ink, maxLines = 1)
                Text(
                    if (over > 0) "$over categor${if (over == 1) "y" else "ies"} over limit"
                    else "${Formatters.formatCurrency(limit - spent)} left",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (over > 0) Negative else InkSecondary
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TrackSoft))
        Spacer(modifier = Modifier.height(Space.m))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            statuses.sortedByDescending { it.percentageUsed }.take(3).forEach { BudgetLine(it) }
        }
    }
}

@Composable
private fun BudgetLine(status: CategoryBudgetStatus) {
    val pct = status.percentageUsed
    val near = !status.isOverBudget && pct > 80
    val barColor = when {
        status.isOverBudget -> Negative
        near -> Warning
        else -> Positive
    }
    val noteColor = when {
        status.isOverBudget -> Negative
        near -> WarningText
        else -> PositiveText
    }
    val note = when {
        status.isOverBudget -> "Over by ${Formatters.formatCurrency(status.spent - status.budgetLimit)}"
        near -> "${Formatters.formatCurrency(status.remaining)} left · ${Math.round(pct)}%"
        else -> "${Formatters.formatCurrency(status.remaining)} left"
    }
    val color = Color(status.category.colorHex)
    Row(verticalAlignment = Alignment.CenterVertically) {
        CategoryDot(getCategoryIcon(status.category.iconKey), color, 32)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row {
                Text(
                    status.categoryName,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(note, style = MaterialTheme.typography.labelMedium, color = noteColor, maxLines = 1)
            }
            ProgressLine((pct / 100).toFloat(), barColor, 4)
        }
    }
}

@Composable
private fun NoBudgetsCard(onSetUp: () -> Unit) {
    PwCard(contentPadding = PaddingValues(horizontal = Space.l, vertical = 14.dp), modifier = Modifier.testTag("budget_card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryDot(Icons.Default.DonutSmall, Accent, 40, background = AccentSoft)
            Spacer(modifier = Modifier.width(Space.m))
            Column(modifier = Modifier.weight(1f)) {
                Text("No budgets yet", style = MaterialTheme.typography.titleSmall, color = Ink)
                Text("Set monthly limits to see what's left", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            }
            SmallOutlineButton("Set up", onSetUp)
        }
    }
}

// --- Spending mix ---

@Composable
private fun SpendingMixCard(mix: List<CategoryBreakdown>) {
    val grow = rememberGrowth(mix.size)
    PwCard(modifier = Modifier.testTag("spending_mix_card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 17.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    var start = -90f
                    mix.forEach { part ->
                        val sweep = (part.percentage * 3.6 * grow).toFloat()
                        // A small white gap between segments, as in the design.
                        val gap = if (mix.size > 1) minOf(1.5f, sweep / 3) else 0f
                        drawArc(
                            Color(part.category.colorHex), start, sweep - gap, false,
                            topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke)
                        )
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${mix.size}", style = MaterialTheme.typography.titleLarge, color = Ink)
                    Text(
                        if (mix.size == 1) "category" else "categories",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = InkSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                mix.take(4).forEach { part ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(part.category.colorHex)))
                        Spacer(modifier = Modifier.width(Space.s))
                        Text(
                            part.categoryName,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                            color = Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${Math.round(part.percentage)}%",
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp),
                            color = Ink
                        )
                    }
                }
            }
        }
    }
}

// --- Goals ---

@Composable
private fun GoalTiles(goals: List<GoalProgress>, onOpen: (Long?) -> Unit) {
    if (goals.isEmpty()) {
        PwCard(onClick = { onOpen(null) }, contentPadding = PaddingValues(horizontal = Space.l, vertical = 14.dp), modifier = Modifier.testTag("goals_card")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryDot(Icons.Default.Savings, Accent, 40, background = AccentSoft)
                Spacer(modifier = Modifier.width(Space.m))
                Column {
                    Text("No goals yet", style = MaterialTheme.typography.titleSmall, color = Ink)
                    Text("Save for something you want", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                }
            }
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.testTag("goals_card")) {
        goals.forEach { GoalTile(it, { onOpen(it.goal.id) }, Modifier.weight(1f)) }
        if (goals.size == 1) Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun GoalTile(progress: GoalProgress, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val goal = progress.goal
    val color = Color(goal.colorHex)
    val fraction = if (goal.target > 0) (progress.saved / goal.target).toFloat().coerceIn(0f, 1f) else 0f
    Column(
        modifier = modifier
            .softShadow()
            .clip(CardShape)
            .background(SurfaceWhite)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryDot(getCategoryIcon(goal.iconKey), color, 32)
            Spacer(modifier = Modifier.weight(1f))
            Text("${Math.round(fraction * 100)}%", style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = color)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(goal.name, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${Formatters.formatCurrency(progress.saved)} of ${Formatters.formatCurrency(goal.target)}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = InkSecondary,
                maxLines = 1
            )
        }
        ProgressLine(fraction * rememberGrowth(goal.id), color, 6, shimmer = true)
        Text(
            goal.targetDate?.let { "By " + SimpleDateFormat("MMM yyyy", Locale.US).format(Date(it)) } ?: "No date set",
            style = MaterialTheme.typography.labelSmall,
            color = InkSecondary
        )
    }
}

// --- Recent ---

@Composable
private fun RecentCard(
    transactions: List<TransactionEntity>,
    onClick: (TransactionEntity) -> Unit,
    onDelete: (TransactionEntity) -> Unit,
    onAdd: () -> Unit
) {
    if (transactions.isEmpty()) {
        PwCard(contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.xl)) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No transactions yet", style = MaterialTheme.typography.titleSmall, color = Ink)
                Text("Add one, or import your bank SMS.", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                Spacer(modifier = Modifier.height(Space.m))
                PwPrimaryButton(text = "Add transaction", onClick = onAdd, modifier = Modifier.height(40.dp))
            }
        }
        return
    }
    PwCard(contentPadding = PaddingValues(0.dp), modifier = Modifier.testTag("recent_transactions_card")) {
        transactions.forEachIndexed { i, tx ->
            if (i > 0) PwDivider(startInset = 0.dp)
            TransactionItemRow(transaction = tx, onClick = { onClick(tx) }, onDelete = { onDelete(tx) })
        }
    }
}

// --- Small pieces ---

/** Icon on a tinted circle (category colour at 12%). */
@Composable
private fun CategoryDot(icon: ImageVector, color: Color, sizeDp: Int, background: Color = color.copy(alpha = 0.12f)) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size((sizeDp / 2).dp))
    }
}

@Composable
private fun ProgressLine(fraction: Float, color: Color, heightDp: Int, shimmer: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(heightDp.dp))
            .background(TrackSoft)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(heightDp.dp))
                .background(color)
                .then(if (shimmer) Modifier.shimmer() else Modifier)
        )
    }
}

@Composable
private fun SmallOutlineButton(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        color = Ink,
        modifier = Modifier
            .clip(PillShape)
            .background(SurfaceWhite)
            .border(1.dp, Outline, PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.m, vertical = 7.dp)
    )
}

/** "You didn't spend ₹X last pay cycle": one tap moves it into the Savings goal. */
@Composable
private fun LeftoverCard(leftover: CycleLeftover, onMove: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PositiveSoft)
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
            .testTag("leftover_card"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Savings, contentDescription = null, tint = PositiveText, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${Formatters.formatWholeRupees(leftover.amount)} unspent last pay cycle",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Ink
            )
            Text("Move it to Savings so it isn't spent", style = MaterialTheme.typography.labelSmall, color = InkSecondary)
        }
        TextButton(onClick = onDismiss, modifier = Modifier.testTag("leftover_dismiss")) {
            Text("Not now", color = InkSecondary, style = MaterialTheme.typography.labelMedium)
        }
        Text(
            "Move",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = SurfaceWhite,
            modifier = Modifier
                .clip(PillShape)
                .background(PositiveText)
                .clickable(onClick = onMove)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag("leftover_move")
        )
    }
}
