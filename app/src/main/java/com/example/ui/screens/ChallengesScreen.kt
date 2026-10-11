package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.ChallengeResult
import com.example.domain.ChallengeState
import com.example.domain.ChallengeSuggestion
import com.example.domain.ChallengeType
import com.example.domain.Challenges
import com.example.domain.ReportCard
import com.example.ui.components.PwCard
import com.example.ui.components.PwDivider
import com.example.ui.components.PwListRow
import com.example.ui.components.PwSecondaryButton
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
import com.example.ui.theme.WarningText
import com.example.ui.util.Formatters
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * This month's challenges, suggestions to pick, and the monthly report cards; one report card
 * in detail when [selectedReport] (a month key) is set.
 */
@Composable
fun ChallengesScreen(
    results: List<ChallengeResult>,
    suggestions: List<ChallengeSuggestion>,
    reports: List<ReportCard>,
    selectedReport: Int?,
    onPick: (ChallengeSuggestion) -> Unit,
    onRemove: (Long) -> Unit,
    onSelectReport: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    var removing by rememberSaveable { mutableStateOf<Long?>(null) }
    val report = selectedReport?.let { key -> reports.firstOrNull { it.month.key == key } }
    Box(modifier = modifier.fillMaxSize().testTag("challenges_screen")) {
        if (report != null) {
            ReportCardDetail(report, results.filter { it.challenge.month == report.month.key })
        } else {
            ChallengeList(results, suggestions, reports, onPick, onRemove = { removing = it }, onSelectReport)
        }
    }
    removing?.let { id ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove this challenge?") },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(id)
                    removing = null
                }) { Text("Remove", color = Negative) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel", color = Ink) } }
        )
    }
}

@Composable
private fun ChallengeList(
    results: List<ChallengeResult>,
    suggestions: List<ChallengeSuggestion>,
    reports: List<ReportCard>,
    onPick: (ChallengeSuggestion) -> Unit,
    onRemove: (Long) -> Unit,
    onSelectReport: (Int?) -> Unit
) {
    val month = Challenges.month(System.currentTimeMillis())
    val current = results.filter { it.challenge.month == month.key }
    LazyColumn(
        contentPadding = PaddingValues(start = Space.screen, end = Space.screen, top = Space.s, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.section),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                "${Formatters.formatMonthYear(month.start)} · ${current.count { it.state == ChallengeState.WON }} of ${current.size} won",
                style = MaterialTheme.typography.titleMedium,
                color = Ink
            )
        }
        if (current.isEmpty()) {
            item {
                Text(
                    "Pick up to three challenges for this month. Each win is worth ${com.example.domain.Achievements.XP_CHALLENGE} XP.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )
            }
        }
        items(current, key = { "c" + it.challenge.id }) { result ->
            ChallengeCard(result, onRemove = { onRemove(result.challenge.id) })
        }
        if (suggestions.isNotEmpty()) {
            item { Text("Suggested for you", style = MaterialTheme.typography.labelLarge, color = InkSecondary) }
            items(suggestions, key = { "s" + it.type.name }) { suggestion ->
                PwCard(modifier = Modifier.testTag("challenge_suggestion_${suggestion.type.name}")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(typeEmoji(suggestion.type), style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.width(Space.m))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                Challenges.title(suggestion.type, suggestion.category, suggestion.target),
                                style = MaterialTheme.typography.titleSmall,
                                color = Ink
                            )
                            Text(typeHint(suggestion.type), style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                        }
                        Spacer(modifier = Modifier.width(Space.s))
                        PwSecondaryButton(text = "Pick", onClick = { onPick(suggestion) }, contentColor = Accent)
                    }
                }
            }
        }
        if (reports.isNotEmpty()) {
            item { Text("Report cards", style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.padding(top = Space.s)) }
            item {
                PwCard(contentPadding = PaddingValues(vertical = Space.xs)) {
                    reports.forEachIndexed { index, card ->
                        if (index > 0) PwDivider()
                        val running = card.month.key == month.key
                        PwListRow(
                            title = Formatters.formatMonthYear(card.month.start) + if (running) " (so far)" else "",
                            subtitle = "Spent ${Formatters.formatCurrency(card.spent)}" +
                                (card.savingsRate?.let { " · saved ${(it * 100).roundToInt()}%" } ?: ""),
                            leading = { GradeBadge(card.grade, size = 40) },
                            onClick = { onSelectReport(card.month.key) },
                            modifier = Modifier.testTag("report_row_${card.month.key}")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = InkTertiary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChallengeCard(result: ChallengeResult, onRemove: () -> Unit) {
    val c = result.challenge
    val (stateText, stateColor) = when (result.state) {
        ChallengeState.WON -> "Won 🥇" to PositiveText
        ChallengeState.FAILED -> "Missed" to Negative
        ChallengeState.ACTIVE -> "In progress" to Accent
    }
    val limit = result.type == ChallengeType.CATEGORY_LIMIT || result.type == ChallengeType.TOTAL_SPEND
    val progressText = when (result.type) {
        ChallengeType.NO_SPEND_DAYS -> "${result.current.toInt()} of ${c.target.toInt()} days"
        ChallengeType.SAVE_AMOUNT -> "${Formatters.formatCurrency(result.current)} of ${Formatters.formatCurrency(c.target)} added"
        else -> "${Formatters.formatCurrency(result.current)} of ${Formatters.formatCurrency(c.target)} spent"
    }
    // For limits the bar fills as the budget is used up; amber near the limit, red past it.
    val barColor = when {
        result.state == ChallengeState.FAILED -> Negative
        limit && result.fraction >= 0.85f -> WarningText
        limit -> Accent
        else -> Positive
    }
    PwCard(modifier = Modifier.testTag("challenge_${c.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(typeEmoji(result.type), style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.width(Space.m))
            Column(modifier = Modifier.weight(1f)) {
                Text(Challenges.title(result.type, c.category, c.target), style = MaterialTheme.typography.titleSmall, color = Ink)
                Text(progressText, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            }
            Text(stateText, style = MaterialTheme.typography.labelLarge, color = stateColor)
        }
        Spacer(modifier = Modifier.height(Space.m))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceMuted)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (result.state == ChallengeState.FAILED && limit) 1f else result.fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor)
            )
        }
        if (result.state == ChallengeState.ACTIVE) {
            TextButton(onClick = onRemove, modifier = Modifier.align(Alignment.End)) {
                Text("Remove", color = InkSecondary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun typeEmoji(type: ChallengeType): String = when (type) {
    ChallengeType.CATEGORY_LIMIT -> "🎯"
    ChallengeType.TOTAL_SPEND -> "🧾"
    ChallengeType.SAVE_AMOUNT -> "💰"
    ChallengeType.NO_SPEND_DAYS -> "🚫"
}

private fun typeHint(type: ChallengeType): String = when (type) {
    ChallengeType.CATEGORY_LIMIT -> "10% less than last month"
    ChallengeType.TOTAL_SPEND -> "5% less than last month"
    ChallengeType.SAVE_AMOUNT -> "Money added to any goal counts"
    ChallengeType.NO_SPEND_DAYS -> "Days with no expense at all"
}

/** Round grade badge: green for A grades, blue for B, amber for C, red for D. */
@Composable
internal fun GradeBadge(grade: String, size: Int) {
    val color = when (grade.first()) {
        'A' -> PositiveText
        'B' -> Accent
        'C' -> WarningText
        else -> Negative
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
    ) {
        Text(
            grade,
            style = if (size >= 64) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium,
            color = color
        )
    }
}

@Composable
private fun ReportCardDetail(card: ReportCard, challenges: List<ChallengeResult>) {
    LazyColumn(
        contentPadding = PaddingValues(start = Space.screen, end = Space.screen, top = Space.s, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.section),
        modifier = Modifier.fillMaxSize().testTag("report_card_detail")
    ) {
        item {
            PwCard {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(Formatters.formatMonthYear(card.month.start), style = MaterialTheme.typography.labelLarge, color = InkSecondary)
                    Spacer(modifier = Modifier.height(Space.m))
                    GradeBadge(card.grade, size = 96)
                    Spacer(modifier = Modifier.height(Space.m))
                    Text(card.headline, style = MaterialTheme.typography.titleMedium, color = Ink, textAlign = TextAlign.Center)
                    Text("Score ${card.score} / 100", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                }
            }
        }
        item {
            PwCard {
                StatRow("Spent", Formatters.formatCurrency(card.spent))
                StatRow("Earned", Formatters.formatCurrency(card.earned))
                card.savingsRate?.let { StatRow("Saved", "${(it * 100).roundToInt()}% of income", if (it >= 0) PositiveText else Negative) }
                card.spendChange?.let {
                    val pct = (abs(it) * 100).roundToInt()
                    StatRow("Vs month before", if (it <= 0) "$pct% less spent" else "$pct% more spent", if (it <= 0) PositiveText else WarningText)
                }
                card.topCategory?.let { StatRow("Top category", "${it.first} · ${Formatters.formatCurrency(it.second)}") }
                card.biggestExpense?.let { StatRow("Biggest expense", "${it.title} · ${Formatters.formatCurrency(it.amount)}") }
                if (card.budgetsTotal > 0) {
                    StatRow(
                        "Budgets kept",
                        "${card.budgetsKept} of ${card.budgetsTotal}",
                        if (card.budgetsKept == card.budgetsTotal) PositiveText else WarningText
                    )
                }
                StatRow("Added to goals", Formatters.formatCurrency(card.savedToGoals))
                if (card.challengesTotal > 0) StatRow("Challenges won", "${card.challengesWon} of ${card.challengesTotal}")
                StatRow("No-spend days", "${card.noSpendDays}")
            }
        }
        if (challenges.isNotEmpty()) {
            item { Text("Challenges", style = MaterialTheme.typography.titleMedium, color = Ink) }
            items(challenges, key = { it.challenge.id }) { ChallengeCard(it, onRemove = {}) }
        }
        item {
            Text(
                "How the grade works: it starts at 50 and goes up with your savings rate (up to +30), budgets kept (up to +20, " +
                    "-10 if any is missed), money added to goals (+10), challenges won (up to +15) and spending less than the month before (+5).",
                style = MaterialTheme.typography.labelSmall,
                color = InkTertiary
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color = Ink) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = InkSecondary, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f)
        )
    }
}

/** Goals screen entry: this month's challenge progress. */
@Composable
internal fun ChallengesSummaryCard(results: List<ChallengeResult>, hasSuggestions: Boolean, onClick: () -> Unit) {
    val month = Challenges.month(System.currentTimeMillis())
    val current = results.filter { it.challenge.month == month.key }
    PwCard(onClick = onClick, modifier = Modifier.testTag("challenges_card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🥇", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.width(Space.l))
            Column(modifier = Modifier.weight(1f)) {
                Text("Monthly challenges", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    when {
                        current.isEmpty() && hasSuggestions -> "Pick this month's challenges"
                        current.isEmpty() -> "See your report cards"
                        else -> "${current.count { it.state == ChallengeState.WON }} of ${current.size} won · " +
                            "${current.count { it.state == ChallengeState.ACTIVE }} in progress"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = InkTertiary)
        }
    }
}

/** Home, first week of a month: last month's report card is ready. */
@Composable
internal fun ReportReadyCard(card: ReportCard, onClick: () -> Unit) {
    PwCard(onClick = onClick, color = AccentSoft, modifier = Modifier.testTag("report_ready_card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradeBadge(card.grade, size = 48)
            Spacer(modifier = Modifier.width(Space.l))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Your ${Formatters.formatMonthYear(card.month.start)} report card",
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink
                )
                Text(card.headline, style = MaterialTheme.typography.bodySmall, color = InkSecondary, maxLines = 2)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = InkTertiary)
        }
    }
}
