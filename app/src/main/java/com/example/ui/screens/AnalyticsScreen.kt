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
import com.example.ui.util.Formatters
import com.example.ui.util.LocalCategories
import com.example.ui.util.getCategoryIcon

private const val NEW_BUDGET = "\u0000new"

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
    transactions: List<TransactionEntity> = emptyList()
) {
    // null = closed, NEW_BUDGET = adding, otherwise the category being edited.
    var budgetEditorTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val categories = LocalCategories.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("analytics_screen"),
        contentPadding = PaddingValues(start = Space.screen, end = Space.screen, top = Space.s, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.section)
    ) {
        val now = System.currentTimeMillis()
        val month = FinancialCalculations.filterToMonth(transactions, now)
        val monthSummary = FinancialCalculations.calculateSummary(month)
        val monthMix = FinancialCalculations.calculateCategoryBreakdown(month) { CategoryInfo.resolve(it, categories) }
        val monthName = java.text.SimpleDateFormat("MMMM", java.util.Locale.US).format(java.util.Date(now))
        item(key = "month") { MonthPill(now) }
        item(key = "rate") { SavingsRateCard(monthSummary) }
        if (monthSummary.totalExpense > 0) {
            item(key = "trend_title") { InsightsHeader("$monthName so far", "Cumulative spend") }
            item(key = "trend") { MonthTrendCard(transactions, now) }
        }
        item(key = "week_title") {
            val weekStart = FinancialCalculations.startOfDay(now) - 6 * 86_400_000L
            val weekTotal = transactions.filter { it.type == TransactionType.EXPENSE.name && it.date >= weekStart && it.date <= now }.sumOf { it.amount }
            InsightsHeader("Last 7 days", "${Formatters.formatCurrency(weekTotal)} spent")
        }
        item(key = "week") { LastSevenDaysCard(transactions, now) }
        item(key = "where_title") { InsightsHeader("Where it went", "By category") }
        item(key = "where") { WhereItWentCard(monthMix) }
        item {
            BudgetsCard(
                statuses = budgetStatuses,
                onAdd = { budgetEditorTarget = NEW_BUDGET },
                onEdit = { budgetEditorTarget = it }
            )
        }
        item { TopVendorsCard(topVendors) }
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
    onAdd: () -> Unit,
    onEdit: (String) -> Unit
) {
    PwCard(contentPadding = PaddingValues(vertical = Space.l)) {
        PwSectionTitle(
            title = "Budgets",
            subtitle = "Spending in " + Formatters.formatMonthYear(System.currentTimeMillis()),
            action = "Add",
            onAction = onAdd,
            modifier = Modifier
                .padding(horizontal = Space.xl)
                .testTag("add_budget_button")
        )
        Spacer(modifier = Modifier.height(Space.s))
        if (statuses.isEmpty()) {
            Column(modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.s)) {
                Text(
                    "Set monthly limits to see how much you have left in each category.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )
                Spacer(modifier = Modifier.height(Space.m))
                PwPrimaryButton(text = "Set a budget", onClick = onAdd, modifier = Modifier.fillMaxWidth())
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
