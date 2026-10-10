package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.TransactionEntity
import com.example.domain.CategoryBreakdown
import com.example.domain.CategoryBudgetStatus
import com.example.domain.sms.CaptureState
import com.example.domain.FinancialCalculations
import com.example.domain.FinancialSummary
import com.example.ui.components.BudgetGauge
import com.example.ui.components.PwCard
import com.example.ui.components.PwDivider
import com.example.ui.components.PwIconBadge
import com.example.ui.components.PwPrimaryButton
import com.example.ui.components.PwSecondaryButton
import com.example.ui.components.PwSectionTitle
import com.example.ui.components.Space
import com.example.ui.components.SpendingDonutChart
import com.example.ui.components.SpendingTrendChart
import com.example.ui.components.TransactionItemRow
import com.example.ui.components.softShadow
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.InkTertiary
import com.example.ui.theme.Negative
import com.example.ui.theme.Positive
import com.example.ui.theme.SurfaceWhite
import com.example.ui.theme.Warning
import com.example.ui.util.ChartMath
import com.example.ui.util.Formatters
import java.util.Calendar

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
    onDismissCaptured: () -> Unit = {}
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(start = Space.screen, end = Space.screen, top = Space.s, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.section)
    ) {
        item { BalanceHero(summary = summary, onAddIncome = onAddIncome, onAddExpense = onAddExpense) }
        if (captureState.pendingCount > 0) {
            item(key = "captured") {
                CapturedPaymentsCard(state = captureState, onReview = onReviewCaptured, onDismiss = onDismissCaptured)
            }
        }
        item { BudgetCard(statuses = budgetStatuses, onOpenBudgets = onOpenBudgets) }
        item { WeeklySpendingCard(transactions = recentTransactions) }
        item { ImportSmsCard(onImportSms = onImportSms) }
        item { SpendingDonutChart(breakdowns = breakdowns, totalExpense = summary.totalExpense) }
        item {
            RecentTransactionsCard(
                transactions = recentTransactions.take(5),
                onViewAll = onViewAllTransactions,
                onClick = onTransactionClick,
                onDelete = onTransactionDelete,
                onAddFirst = onAddExpense
            )
        }
    }
}

/** Dark hero card: the balance is the dominant number on the screen. */
@Composable
private fun BalanceHero(summary: FinancialSummary, onAddIncome: () -> Unit, onAddExpense: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(RoundedCornerShape(28.dp), 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Ink)
            .padding(Space.xxl)
            .testTag("balance_card")
    ) {
        Text("Total balance", style = MaterialTheme.typography.labelMedium, color = SurfaceWhite.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            Formatters.formatCurrency(summary.netBalance),
            style = MaterialTheme.typography.displayMedium,
            color = SurfaceWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("net_balance_text")
        )
        Spacer(modifier = Modifier.height(Space.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
            HeroStat(
                label = "Income",
                value = "+" + Formatters.formatCurrency(summary.totalIncome),
                dot = Positive,
                modifier = Modifier
                    .weight(1f)
                    .testTag("income_stat")
            )
            HeroStat(
                label = "Spent",
                value = "−" + Formatters.formatCurrency(summary.totalExpense),
                dot = SurfaceWhite.copy(alpha = 0.6f),
                modifier = Modifier
                    .weight(1f)
                    .testTag("expense_stat")
            )
        }
        Spacer(modifier = Modifier.height(Space.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            HeroAction("Add income", Icons.AutoMirrored.Filled.TrendingUp, onAddIncome, Modifier.weight(1f).testTag("quick_add_income_button"))
            HeroAction("Add expense", Icons.AutoMirrored.Filled.TrendingDown, onAddExpense, Modifier.weight(1f).testTag("quick_add_expense_button"))
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, dot: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dot)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = SurfaceWhite.copy(alpha = 0.7f))
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = SurfaceWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HeroAction(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceWhite.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(vertical = Space.m),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(Space.s))
        Text(text, style = MaterialTheme.typography.labelLarge, color = SurfaceWhite)
    }
}

/** Monthly budget gauge (reference style): legend on the left, arc on the right. */
@Composable
private fun BudgetCard(statuses: List<CategoryBudgetStatus>, onOpenBudgets: () -> Unit) {
    PwCard(onClick = onOpenBudgets, modifier = Modifier.testTag("budget_gauge_card")) {
        if (statuses.isEmpty()) {
            PwSectionTitle(title = "Monthly budget", subtitle = "Set limits to see how much you have left")
            Spacer(modifier = Modifier.height(Space.l))
            PwPrimaryButton(text = "Set budgets", onClick = onOpenBudgets, modifier = Modifier.fillMaxWidth())
            return@PwCard
        }
        val limit = statuses.sumOf { it.budgetLimit }
        val spent = statuses.sumOf { it.spent }
        val fraction = if (limit > 0) (spent / limit).toFloat() else 0f
        val over = statuses.count { it.isOverBudget }
        val near = statuses.count { !it.isOverBudget && it.percentageUsed > 80.0 }
        val onTrack = statuses.size - over - near
        val gaugeColor = when {
            fraction > 1f -> Negative
            fraction > 0.8f -> Warning
            else -> Positive
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Monthly budget", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    Formatters.formatMonthYear(System.currentTimeMillis()),
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSecondary
                )
                Spacer(modifier = Modifier.height(Space.m))
                LegendLine(Positive, "$onTrack on track")
                LegendLine(Warning, "$near near limit")
                LegendLine(Negative, "$over over budget")
            }
            BudgetGauge(
                fraction = fraction,
                color = gaugeColor,
                value = ChartMath.compactRupees(spent),
                caption = "of " + ChartMath.compactRupees(limit)
            )
        }
    }
}

@Composable
private fun LegendLine(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(Space.s))
        Text(text, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
    }
}

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** "This week" smooth spending chart with week navigation (reference style). */
@Composable
private fun WeeklySpendingCard(transactions: List<TransactionEntity>) {
    // 0 = this week, 1 = last week, ...
    var weeksBack by rememberSaveable { mutableIntStateOf(0) }
    val now = System.currentTimeMillis()
    val weekStart = remember(weeksBack) {
        Calendar.getInstance().apply {
            timeInMillis = FinancialCalculations.startOfWeek(now)
            add(Calendar.WEEK_OF_YEAR, -weeksBack)
        }.timeInMillis
    }
    val totals = remember(transactions, weekStart) { FinancialCalculations.dailyExpenseTotals(transactions, weekStart) }
    val todayIndex = ((Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7)
    var selected by rememberSaveable(weeksBack) {
        mutableIntStateOf(if (weeksBack == 0) todayIndex else totals.indices.maxByOrNull { totals[it] } ?: 0)
    }
    val weekEnd = remember(weekStart) {
        Calendar.getInstance().apply { timeInMillis = weekStart; add(Calendar.DAY_OF_YEAR, 6) }.timeInMillis
    }

    PwCard(modifier = Modifier.testTag("weekly_spending_card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (weeksBack == 0) "This week" else if (weeksBack == 1) "Last week" else "Week",
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { weeksBack++ }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous week", tint = Ink)
            }
            Text(
                Formatters.formatShortDate(weekStart) + " – " + Formatters.formatShortDate(weekEnd),
                style = MaterialTheme.typography.labelLarge,
                color = InkSecondary
            )
            IconButton(onClick = { if (weeksBack > 0) weeksBack-- }, enabled = weeksBack > 0, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next week",
                    tint = if (weeksBack > 0) Ink else InkTertiary
                )
            }
        }
        Spacer(modifier = Modifier.height(Space.m))
        Row {
            Column(modifier = Modifier.weight(1f)) {
                Text("Spent this week", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                Text(Formatters.formatCurrency(totals.sum()), style = MaterialTheme.typography.headlineSmall, color = Ink)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(DAY_LABELS.getOrElse(selected) { "" }, style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                Text(
                    Formatters.formatCurrency(totals.getOrElse(selected) { 0.0 }),
                    style = MaterialTheme.typography.titleMedium,
                    color = Accent
                )
            }
        }
        Spacer(modifier = Modifier.height(Space.l))
        SpendingTrendChart(
            values = totals,
            labels = DAY_LABELS,
            selectedIndex = selected,
            onSelect = { selected = it }
        )
    }
}

/** Bank SMS that live capture could not add on its own (new payee, credit, possible duplicate). */
@Composable
private fun CapturedPaymentsCard(state: CaptureState, onReview: () -> Unit, onDismiss: () -> Unit) {
    val count = state.pendingCount
    PwCard(
        contentPadding = PaddingValues(Space.l),
        modifier = Modifier.testTag("captured_payments_card")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PwIconBadge(icon = Icons.Default.Sms, tint = Accent)
            Spacer(modifier = Modifier.width(Space.m))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$count bank payment${if (count == 1) "" else "s"} to review",
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink
                )
                val latest = state.latest?.takeIf { it.addedAs == null }
                Text(
                    latest?.let { "Latest: ${Formatters.formatCurrency(it.amount)} · ${it.title}" }
                        ?: "New payees need a category once",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.height(Space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            PwSecondaryButton(
                text = "Not now",
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .testTag("captured_dismiss_button")
            )
            PwPrimaryButton(
                text = "Review",
                onClick = onReview,
                modifier = Modifier
                    .weight(1f)
                    .testTag("captured_review_button")
            )
        }
    }
}

@Composable
private fun ImportSmsCard(onImportSms: () -> Unit) {
    PwCard(
        onClick = onImportSms,
        contentPadding = PaddingValues(Space.l),
        modifier = Modifier.testTag("import_sms_card")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PwIconBadge(icon = Icons.Default.Sms, tint = Accent)
            Spacer(modifier = Modifier.width(Space.m))
            Column(modifier = Modifier.weight(1f)) {
                Text("Import from SMS", style = MaterialTheme.typography.titleSmall, color = Ink)
                Text(
                    "Add Kotak & BOI payments automatically",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = InkTertiary)
        }
    }
}

@Composable
private fun RecentTransactionsCard(
    transactions: List<TransactionEntity>,
    onViewAll: () -> Unit,
    onClick: (TransactionEntity) -> Unit,
    onDelete: (TransactionEntity) -> Unit,
    onAddFirst: () -> Unit
) {
    PwCard(contentPadding = PaddingValues(vertical = Space.l)) {
        PwSectionTitle(
            title = "Recent transactions",
            action = "See all",
            onAction = onViewAll,
            modifier = Modifier
                .padding(horizontal = Space.xl)
                .testTag("view_all_transactions_button")
        )
        Spacer(modifier = Modifier.height(Space.s))
        if (transactions.isEmpty()) {
            Column(modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.s)) {
                Text("No transactions yet", style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
                Spacer(modifier = Modifier.height(Space.m))
                PwPrimaryButton(text = "Add your first transaction", onClick = onAddFirst, modifier = Modifier.fillMaxWidth())
            }
        } else {
            transactions.forEachIndexed { index, tx ->
                if (index > 0) PwDivider()
                TransactionItemRow(
                    transaction = tx,
                    onClick = { onClick(tx) },
                    onDelete = { onDelete(tx) }
                )
            }
        }
    }
}
