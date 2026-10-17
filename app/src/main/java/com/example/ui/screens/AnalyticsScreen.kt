package com.example.ui.screens

import com.example.domain.FinancialCalculations
import com.example.data.local.TransactionEntity
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.components.PwSecondaryButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CategoryInfo
import com.example.data.model.TransactionType
import com.example.domain.CategoryBreakdown
import com.example.domain.CategoryBudgetStatus
import com.example.domain.FinancialSummary
import com.example.domain.VendorSpend
import com.example.ui.components.BudgetGauge
import com.example.ui.components.PwCard
import com.example.ui.components.PwDivider
import com.example.ui.components.PwIconBadge
import com.example.ui.components.PwListRow
import com.example.ui.components.PwPrimaryButton
import com.example.ui.components.PwSectionTitle
import com.example.ui.components.Space
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentSoft
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.InkTertiary
import com.example.ui.theme.Negative
import com.example.ui.theme.Positive
import com.example.ui.theme.PositiveText
import com.example.ui.theme.SurfaceMuted
import com.example.ui.theme.Warning
import com.example.ui.theme.WarningText
import com.example.data.local.BudgetEntity
import androidx.compose.runtime.mutableIntStateOf
import com.example.ui.util.Formatters
import com.example.ui.util.LocalCategories
import com.example.ui.util.getCategoryIcon

private const val NEW_BUDGET = "\u0000new"

/** Now for this month ([offset] 0); the last millisecond of an earlier month otherwise. */
private fun monthReference(nowMs: Long, offset: Int): Long {
    if (offset >= 0) return nowMs
    return java.util.Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(java.util.Calendar.DAY_OF_MONTH, 1)
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
        add(java.util.Calendar.MONTH, offset + 1) // start of the month after the one shown
        add(java.util.Calendar.MILLISECOND, -1)
    }.timeInMillis
}

/** Calendar months from [fromMs] to [toMs] (0 when in the same month). */
private fun monthsBetween(fromMs: Long, toMs: Long): Int {
    val from = java.util.Calendar.getInstance().apply { timeInMillis = fromMs }
    val to = java.util.Calendar.getInstance().apply { timeInMillis = toMs }
    return (to.get(java.util.Calendar.YEAR) * 12 + to.get(java.util.Calendar.MONTH)) -
        (from.get(java.util.Calendar.YEAR) * 12 + from.get(java.util.Calendar.MONTH))
}

@Composable
fun AnalyticsScreen(
    summary: FinancialSummary,
    breakdowns: List<CategoryBreakdown>,
    budgetStatuses: List<CategoryBudgetStatus>,
    modifier: Modifier = Modifier,
    onSaveBudget: (category: String, rawLimit: String) -> String? = { _, _ -> null },
    onRemoveBudget: (category: String) -> Unit = {},
    topVendors: List<VendorSpend> = emptyList(),
    /** All transactions; Insights shows this calendar month. */
    transactions: List<TransactionEntity> = emptyList(),
    /** Opens suggested budgets from the salary; null until a salary is set. */
    onSuggestBudgets: (() -> Unit)? = null,
    /** Suggested budgets being previewed; null when closed. */
    budgetSuggestion: List<BudgetEntity>? = null,
    monthlyIncome: Double = 0.0,
    /** "₹6,000 a month saved first (50/30/20)"; null without a salary. */
    savingsNote: String? = null,
    onApplyBudgetSuggestion: () -> Unit = {},
    onDismissBudgetSuggestion: () -> Unit = {}
) {
    // null = closed, NEW_BUDGET = adding, otherwise the category being edited.
    var budgetEditorTarget by rememberSaveable { mutableStateOf<String?>(null) }
    // 0 = this month, -1 = last month, ...
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    val categories = LocalCategories.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("analytics_screen"),
        contentPadding = PaddingValues(start = Space.screen, end = Space.screen, top = Space.s, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.section)
    ) {
        val now = System.currentTimeMillis()
        // A past month is shown as of its last moment, so every card covers the whole month.
        val ref = monthReference(now, monthOffset)
        val isPast = monthOffset < 0
        val oldestOffset = -monthsBetween(transactions.minOfOrNull { it.date } ?: now, now)
        val month = FinancialCalculations.filterToMonth(transactions, ref)
        val monthSummary = FinancialCalculations.calculateSummary(month)
        val monthMix = FinancialCalculations.calculateCategoryBreakdown(month) { CategoryInfo.resolve(it, categories) }
        val monthName = java.text.SimpleDateFormat("MMMM", java.util.Locale.US).format(java.util.Date(ref))
        // Same limits every month; spending from the month shown.
        val monthBudgets = if (isPast) {
            FinancialCalculations.calculateBudgetStatuses(
                budgetStatuses.map { BudgetEntity(category = it.categoryName, monthlyLimit = it.budgetLimit) },
                month
            ) { CategoryInfo.resolve(it, categories) }
        } else {
            budgetStatuses
        }
        val monthVendors = if (isPast) FinancialCalculations.topVendors(month) else topVendors
        item(key = "month") {
            MonthPill(
                nowMs = ref,
                onPrevious = if (monthOffset > minOf(oldestOffset, 0)) { { monthOffset-- } } else null,
                onNext = if (monthOffset < 0) { { monthOffset++ } } else null
            )
        }
        item(key = "rate") { SavingsRateCard(monthSummary) }
        if (monthSummary.totalExpense > 0) {
            item(key = "trend_title") { InsightsHeader(if (isPast) monthName else "$monthName so far", "Cumulative spend") }
            item(key = "trend") { MonthTrendCard(transactions, ref, isPastMonth = isPast) }
        }
        item(key = "week_title") {
            val weekStart = FinancialCalculations.startOfDay(ref) - 6 * 86_400_000L
            val weekTotal = transactions.filter { it.type == TransactionType.EXPENSE.name && it.date >= weekStart && it.date <= ref }.sumOf { it.amount }
            InsightsHeader(if (isPast) "Last week of $monthName" else "Last 7 days", "${Formatters.formatCurrency(weekTotal)} spent")
        }
        item(key = "week") { LastSevenDaysCard(transactions, ref, isPastMonth = isPast) }
        item(key = "where_title") { InsightsHeader("Where it went", "By category") }
        item(key = "where") { WhereItWentCard(monthMix) }
        item {
            BudgetsCard(
                statuses = monthBudgets,
                monthMs = ref,
                onAdd = { budgetEditorTarget = NEW_BUDGET },
                onEdit = { budgetEditorTarget = it },
                onSuggest = onSuggestBudgets,
                savingsNote = savingsNote
            )
        }
        item { TopVendorsCard(monthVendors) }
    }

    budgetSuggestion?.let { suggestion ->
        BudgetSuggestionDialog(suggestion, monthlyIncome, savingsNote, onApplyBudgetSuggestion, onDismissBudgetSuggestion)
    }

    budgetEditorTarget?.let { target ->
        val editing = budgetStatuses.firstOrNull { it.categoryName == target }
        val budgeted = budgetStatuses.map { it.categoryName }.toSet()
        BudgetEditorDialog(
            editingCategory = editing?.category,
            currentLimit = editing?.budgetLimit,
            availableCategories = CategoryInfo.forType(categories, TransactionType.EXPENSE)
                .filter { it.name !in budgeted },
            onSave = onSaveBudget,
            onRemove = onRemoveBudget,
            onDismiss = { budgetEditorTarget = null }
        )
    }
}

@Composable
private fun SavingsCard(summary: FinancialSummary) {
    PwCard(modifier = Modifier.testTag("savings_rate_card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                // Computed from all recorded transactions, like the cash flow card.
                Text("Savings rate", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                Text(
                    Formatters.formatPercentage(summary.savingsRate),
                    style = MaterialTheme.typography.displaySmall,
                    color = Ink
                )
                Spacer(modifier = Modifier.height(Space.xs))
                Text(
                    text = when {
                        summary.totalIncome <= 0.0 -> "Add income to see how much you save."
                        summary.savingsRate >= 20.0 -> "Excellent — over 20% of income saved."
                        summary.savingsRate > 0.0 -> "Aim for 20% to build a cushion."
                        else -> "Spending matches or exceeds income."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
            }
            BudgetGauge(
                fraction = (summary.savingsRate / 100.0).toFloat(),
                color = Positive,
                value = Formatters.formatPercentage(summary.savingsRate),
                caption = "saved"
            )
        }
    }
}

@Composable
private fun CashFlowCard(summary: FinancialSummary) {
    PwCard {
        PwSectionTitle(title = "Cash flow", subtitle = "All time")
        Spacer(modifier = Modifier.height(Space.l))
        // Equal-width columns so large amounts cannot push into each other.
        Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            FlowStat("Income", summary.totalIncome, PositiveText, Modifier.weight(1f))
            FlowStat("Spent", summary.totalExpense, Ink, Modifier.weight(1f))
            FlowStat(
                if (summary.netBalance >= 0) "Net saved" else "Net deficit",
                summary.netBalance,
                if (summary.netBalance >= 0) Accent else Negative,
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FlowStat(label: String, amount: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = InkSecondary, maxLines = 1)
        Text(
            Formatters.formatCurrency(amount),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Budgets as compact rows (reference style): name, spent of limit, thin bar, percentage. */
@Composable
private fun BudgetsCard(
    statuses: List<CategoryBudgetStatus>,
    monthMs: Long,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onSuggest: (() -> Unit)?,
    savingsNote: String? = null
) {
    PwCard(contentPadding = PaddingValues(vertical = Space.l)) {
        PwSectionTitle(
            title = "Budgets",
            subtitle = "Spending in " + Formatters.formatMonthYear(monthMs),
            action = "Add",
            onAction = onAdd,
            modifier = Modifier
                .padding(horizontal = Space.xl)
                .testTag("add_budget_button")
        )
        if (savingsNote != null) {
            Text(
                savingsNote,
                style = MaterialTheme.typography.labelMedium,
                color = PositiveText,
                modifier = Modifier
                    .padding(horizontal = Space.xl)
                    .padding(top = Space.xs)
                    .testTag("savings_target_note")
            )
        }
        if (onSuggest != null && statuses.isNotEmpty()) {
            Text(
                "Suggest from my salary",
                style = MaterialTheme.typography.labelMedium,
                color = Accent,
                modifier = Modifier
                    .padding(horizontal = Space.xl)
                    .clickable(onClick = onSuggest)
                    .padding(vertical = Space.xs)
                    .testTag("suggest_budgets_link")
            )
        }
        Spacer(modifier = Modifier.height(Space.s))
        if (statuses.isEmpty()) {
            Column(modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.s)) {
                Text(
                    if (onSuggest != null) "Start from budgets based on your salary, or set your own limits."
                    else "Set monthly limits to see how much you have left in each category. Set your income in Settings to get suggested budgets.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )
                Spacer(modifier = Modifier.height(Space.m))
                if (onSuggest != null) {
                    PwPrimaryButton(
                        text = "Suggest from my salary",
                        onClick = onSuggest,
                        modifier = Modifier.fillMaxWidth().testTag("suggest_budgets_button")
                    )
                    Spacer(modifier = Modifier.height(Space.s))
                    PwSecondaryButton(text = "Set a budget", onClick = onAdd, modifier = Modifier.fillMaxWidth())
                } else {
                    PwPrimaryButton(text = "Set a budget", onClick = onAdd, modifier = Modifier.fillMaxWidth())
                }
            }
            return@PwCard
        }
        statuses.forEachIndexed { index, status ->
            if (index > 0) PwDivider()
            BudgetRow(status = status, onClick = { onEdit(status.categoryName) })
        }
    }
}

@Composable
private fun BudgetRow(status: CategoryBudgetStatus, onClick: () -> Unit) {
    val (barColor, textColor) = when {
        status.isOverBudget -> Negative to Negative
        status.percentageUsed > 80.0 -> Warning to WarningText
        else -> Positive to PositiveText
    }
    PwListRow(
        title = status.categoryName,
        subtitle = if (status.isOverBudget) {
            "Over by " + Formatters.formatCurrency(-status.remaining)
        } else {
            Formatters.formatCurrency(status.spent) + " of " + Formatters.formatCurrency(status.budgetLimit)
        },
        leading = { PwIconBadge(getCategoryIcon(status.category.iconKey), Color(status.category.colorHex), size = 36.dp) },
        onClick = onClick,
        modifier = Modifier.testTag("budget_card_${status.categoryName}")
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text(Formatters.formatPercentage(status.percentageUsed), style = MaterialTheme.typography.labelLarge, color = textColor)
            Spacer(modifier = Modifier.height(Space.xs))
            ThinBar(fraction = (status.percentageUsed / 100.0).toFloat(), color = barColor, modifier = Modifier.width(72.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = InkTertiary)
    }
}

@Composable
private fun ThinBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
        color = color,
        trackColor = SurfaceMuted,
        strokeCap = StrokeCap.Round,
        // Material3 1.3 adds a gap and an end "stop" dot by default; not wanted here.
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

@Composable
private fun TopVendorsCard(vendors: List<VendorSpend>) {
    PwCard(contentPadding = PaddingValues(vertical = Space.l), modifier = Modifier.testTag("top_vendors_card")) {
        PwSectionTitle(
            title = "Top vendors",
            subtitle = "Where your money went in " + Formatters.formatMonthYear(System.currentTimeMillis()),
            modifier = Modifier.padding(horizontal = Space.xl)
        )
        Spacer(modifier = Modifier.height(Space.s))
        if (vendors.isEmpty()) {
            Text(
                "No spending recorded this month yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary,
                modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.s)
            )
            return@PwCard
        }
        vendors.forEachIndexed { index, vendor ->
            if (index > 0) PwDivider()
            PwListRow(
                title = vendor.name,
                subtitle = "${vendor.count} payment${if (vendor.count == 1) "" else "s"} · " + Formatters.formatPercentage(vendor.percentage),
                leading = {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AccentSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = Accent)
                    }
                }
            ) {
                // The amount is the value people scan for: strongest text in the row.
                Text(Formatters.formatCurrency(vendor.total), style = MaterialTheme.typography.titleSmall, color = Ink, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CategoryRowsCard(breakdowns: List<CategoryBreakdown>) {
    PwCard {
        PwSectionTitle(title = "Spending by category", subtitle = "All time")
        Spacer(modifier = Modifier.height(Space.l))
        if (breakdowns.isEmpty()) {
            Text("No spending recorded yet.", style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
            return@PwCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.l)) {
            breakdowns.forEach { item ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(item.category.colorHex))
                        )
                        Spacer(modifier = Modifier.width(Space.s))
                        Text(
                            item.categoryName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            Formatters.formatPercentage(item.percentage),
                            style = MaterialTheme.typography.labelMedium,
                            color = InkSecondary
                        )
                        Spacer(modifier = Modifier.width(Space.m))
                        Text(Formatters.formatCurrency(item.amount), style = MaterialTheme.typography.titleSmall, color = Ink)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    ThinBar(
                        fraction = (item.percentage / 100.0).toFloat(),
                        color = Color(item.category.colorHex),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** Preview of budgets suggested from the salary before they are set. */
@Composable
private fun BudgetSuggestionDialog(
    suggestion: List<BudgetEntity>,
    monthlyIncome: Double,
    savingsNote: String?,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val total = suggestion.sumOf { it.monthlyLimit }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("budget_suggestion_dialog"),
        title = { Text("Budgets from your salary", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Savings come off the top: " + (savingsNote ?: "a share of your salary") + ". Needs and wants get " +
                        "their share, split by your usual spending trimmed by 10% (or a starter split without history); " +
                        "anything not budgeted is saved too.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
                Spacer(modifier = Modifier.height(Space.m))
                if (suggestion.isEmpty()) {
                    Text("Nothing to suggest yet.", style = MaterialTheme.typography.bodyMedium)
                }
                suggestion.forEach { budget ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(budget.category, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.weight(1f))
                        Text(
                            Formatters.formatWholeRupees(budget.monthlyLimit),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Ink
                        )
                    }
                }
                if (suggestion.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(Space.s))
                    PwDivider()
                    Spacer(modifier = Modifier.height(Space.s))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Total budgeted", style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.weight(1f))
                        Text(Formatters.formatWholeRupees(total), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Ink)
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Left to save", style = MaterialTheme.typography.bodyMedium, color = PositiveText, modifier = Modifier.weight(1f))
                        Text(
                            Formatters.formatWholeRupees(monthlyIncome - total),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = PositiveText
                        )
                    }
                    Spacer(modifier = Modifier.height(Space.s))
                    Text(
                        "Existing budgets for these categories are replaced; others are kept.",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSecondary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onApply, enabled = suggestion.isNotEmpty(), modifier = Modifier.testTag("apply_budget_suggestion_button")) {
                Text("Set these budgets", color = Accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
