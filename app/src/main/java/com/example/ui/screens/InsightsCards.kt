package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.TransactionEntity
import com.example.data.model.TransactionType
import com.example.domain.CategoryBreakdown
import com.example.domain.FinancialCalculations
import com.example.domain.FinancialSummary
import com.example.ui.components.PillShape
import com.example.ui.components.PwCard
import com.example.ui.components.Space
import com.example.ui.components.softShadow
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentLight
import com.example.ui.theme.AccentTrack
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.InkTertiary
import com.example.ui.theme.Navy
import com.example.ui.theme.Positive
import com.example.ui.theme.PositiveSoft
import com.example.ui.theme.PositiveText
import com.example.ui.theme.SurfaceWhite
import com.example.ui.theme.TrackSoft
import com.example.ui.theme.WarningSoft
import com.example.ui.theme.WarningText
import com.example.ui.util.Formatters
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** "October 2026" pill at the top right of Insights. */
@Composable
internal fun MonthPill(nowMs: Long) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(nowMs)),
            style = MaterialTheme.typography.labelMedium,
            color = Ink,
            modifier = Modifier
                .softShadow(PillShape, 3.dp)
                .clip(PillShape)
                .background(SurfaceWhite)
                .padding(horizontal = Space.m, vertical = Space.s)
        )
    }
}

/** Section title outside the cards, with a quiet note on the right. */
@Composable
internal fun InsightsHeader(title: String, note: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.xs, end = Space.xs, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.weight(1f))
        if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
    }
}

/** Savings rate this month: the percentage, a spent / saved split bar and the three totals. */
@Composable
internal fun SavingsRateCard(month: FinancialSummary) {
    val income = month.totalIncome
    val rate = if (income > 0) (income - month.totalExpense) / income * 100 else 0.0
    val (tag, good) = when {
        income <= 0 -> "Add income to see this" to false
        rate >= 20 -> "Above the 20% target" to true
        rate > 0 -> "Aim for 20%" to false
        else -> "Spending ≥ income" to false
    }
    PwCard(modifier = Modifier.testTag("savings_rate_card")) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Savings rate", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                Text(String.format(Locale.US, "%.1f%%", rate), style = MaterialTheme.typography.displaySmall, color = Ink)
            }
            Text(
                tag,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                color = if (good) PositiveText else WarningText,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (good) PositiveSoft else WarningSoft)
                    .padding(horizontal = Space.s, vertical = Space.xs)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        val spentShare = if (income > 0) (month.totalExpense / income).toFloat().coerceIn(0f, 1f) else 0f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(TrackSoft),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (spentShare > 0f) Box(modifier = Modifier.weight(spentShare).fillMaxHeight().background(Navy))
            if (spentShare < 1f && income > 0) Box(modifier = Modifier.weight(1f - spentShare).fillMaxHeight().background(Positive))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row {
            Total("Income", Formatters.formatCurrency(income), PositiveText, Modifier.weight(1f))
            Total("Spent", Formatters.formatCurrency(month.totalExpense), Ink, Modifier.weight(1f))
            Total("Net saved", Formatters.formatCurrency(income - month.totalExpense), Accent, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Total(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp), color = InkSecondary)
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = color, maxLines = 1)
    }
}

/**
 * Month to date: cumulative spending per day with a dashed projection to the month's end at
 * the current daily average, and a dot on today.
 */
@Composable
internal fun MonthTrendCard(transactions: List<TransactionEntity>, nowMs: Long) {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
    val today = cal.get(Calendar.DAY_OF_MONTH)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val monthShort = SimpleDateFormat("MMM", Locale.US).format(Date(nowMs))
    val cumulative = remember(transactions, nowMs) {
        val perDay = DoubleArray(today)
        FinancialCalculations.filterToMonth(transactions, nowMs)
            .filter { it.type == TransactionType.EXPENSE.name && it.date <= nowMs }
            .forEach { tx ->
                val day = Calendar.getInstance().apply { timeInMillis = tx.date }.get(Calendar.DAY_OF_MONTH)
                if (day in 1..today) perDay[day - 1] += tx.amount
            }
        var total = 0.0
        perDay.map { total += it; total }
    }
    val spent = cumulative.lastOrNull() ?: 0.0
    val projection = spent / today * daysInMonth
    PwCard(modifier = Modifier.testTag("month_trend_card")) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Spent to date", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                Text(Formatters.formatCurrency(spent), style = MaterialTheme.typography.headlineSmall, color = Ink)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("On pace for", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                Text(
                    Formatters.formatCurrency(Math.round(projection).toDouble()),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Accent
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val w = size.width
            val h = size.height
            val maxY = maxOf(1.0, projection, spent) * 1.08
            fun x(day: Int) = (day - 1) / (daysInMonth - 1).coerceAtLeast(1).toFloat() * w
            fun y(v: Double) = (h - 8f - (v / maxY).toFloat() * (h - 16f))
            listOf(0.25f, 0.5f, 0.75f).forEach { f ->
                drawLine(TrackSoft, Offset(0f, h * f), Offset(w, h * f), 1.dp.toPx())
            }
            val line = Path()
            cumulative.forEachIndexed { i, v -> if (i == 0) line.moveTo(x(1), y(v)) else line.lineTo(x(i + 1), y(v)) }
            val lastX = x(today)
            val lastY = y(spent)
            val area = Path().apply {
                addPath(line)
                lineTo(lastX, h)
                lineTo(0f, h)
                close()
            }
            drawPath(area, Accent.copy(alpha = 0.12f))
            if (today < daysInMonth) {
                drawLine(
                    Color(0xFFB9A6EC), Offset(lastX, lastY), Offset(x(daysInMonth), y(projection)),
                    2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
                )
            }
            drawPath(line, Accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(Accent.copy(alpha = 0.25f), 10.dp.toPx(), Offset(lastX, lastY))
            drawCircle(SurfaceWhite, 6.dp.toPx(), Offset(lastX, lastY))
            drawCircle(Accent, 4.dp.toPx(), Offset(lastX, lastY))
        }
        Spacer(modifier = Modifier.height(Space.s))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("1 $monthShort", "8", "15", "22", "$daysInMonth $monthShort").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp), color = InkTertiary)
            }
        }
    }
}

/** Last 7 days as bars; tapping a bar shows that day's total. */
@Composable
internal fun LastSevenDaysCard(transactions: List<TransactionEntity>, nowMs: Long) {
    val todayStart = FinancialCalculations.startOfDay(nowMs)
    val days = remember(transactions, todayStart) {
        (6 downTo 0).map { back ->
            val start = Calendar.getInstance().apply { timeInMillis = todayStart; add(Calendar.DAY_OF_YEAR, -back) }.timeInMillis
            val end = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
            start to transactions.filter { it.type == TransactionType.EXPENSE.name && it.date in start until end }.sumOf { it.amount }
        }
    }
    var selected by rememberSaveable { mutableIntStateOf(6) }
    val max = (days.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
    val (selStart, selAmount) = days[selected.coerceIn(0, 6)]
    val dayLabel = when (selected) {
        6 -> "Today"
        5 -> "Yesterday"
        else -> SimpleDateFormat("EEE, d MMM", Locale.US).format(Date(selStart))
    }
    PwCard(modifier = Modifier.testTag("last_seven_days_card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dayLabel, style = MaterialTheme.typography.bodySmall, color = InkSecondary, modifier = Modifier.weight(1f))
            Text(Formatters.formatCurrency(selAmount), style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), color = Accent)
        }
        Spacer(modifier = Modifier.height(Space.m))
        Row(
            modifier = Modifier.fillMaxWidth().height(136.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalAlignment = Alignment.Bottom
        ) {
            days.forEachIndexed { i, (start, amount) ->
                val on = i == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selected = i },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom)
                ) {
                    if (on) {
                        Text(
                            if (amount >= 1000) String.format(Locale.US, "₹%.1fk", amount / 1000) else Formatters.formatCurrency(amount),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = SurfaceWhite,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Navy)
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 30.dp)
                            .height((84 * amount / max).toFloat().coerceAtLeast(4f).dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) Brush.verticalGradient(listOf(AccentLight, Accent)) else Brush.verticalGradient(listOf(AccentTrack, AccentTrack)))
                    )
                    Text(
                        SimpleDateFormat("EE", Locale.US).format(Date(start)).take(2),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                        color = if (on) Accent else InkSecondary
                    )
                }
            }
        }
    }
}

/** Where this month's money went: a stacked bar and one row per category. */
@Composable
internal fun WhereItWentCard(mix: List<CategoryBreakdown>) {
    PwCard(modifier = Modifier.testTag("where_it_went_card")) {
        if (mix.isEmpty()) {
            Text("No spending recorded this month yet.", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = InkSecondary)
            return@PwCard
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            mix.filter { it.percentage > 0 }.forEach { part ->
                Box(modifier = Modifier.weight(part.percentage.toFloat()).fillMaxHeight().background(Color(part.category.colorHex)))
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            mix.forEach { part ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(Color(part.category.colorHex)))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        part.categoryName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        String.format(Locale.US, "%.1f%%", part.percentage),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(48.dp)
                    )
                    Text(
                        Formatters.formatCurrency(part.amount),
                        style = MaterialTheme.typography.labelLarge,
                        color = Ink,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.width(84.dp)
                    )
                }
            }
        }
    }
}
