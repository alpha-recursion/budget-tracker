package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ShoppingBag
import com.example.data.model.CategoryInfo
import com.example.data.model.TransactionType
import com.example.ui.components.CategoryGrid
import com.example.ui.components.GoalSavingsChart
import com.example.ui.components.PwSectionTitle
import com.example.ui.util.LocalCategories
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.example.domain.RoundUpSettings
import com.example.domain.RoundUps
import com.example.domain.AchievementProgress
import com.example.domain.ChallengeResult
import com.example.domain.Achievements
import com.example.domain.Badge
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.example.domain.SavingStreak
import com.example.ui.theme.Accent
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.GoalEntryEntity
import com.example.domain.FinancialCalculations
import com.example.domain.FormValidation
import com.example.domain.GoalPace
import com.example.domain.GoalProgress
import com.example.domain.Goals
import com.example.ui.components.ConfettiBurst
import com.example.ui.components.GoalRing
import com.example.ui.components.PwCard
import com.example.ui.components.PwChip
import com.example.ui.components.PwDivider
import com.example.ui.components.PwIconBadge
import com.example.ui.components.PwPrimaryButton
import com.example.ui.components.PwSecondaryButton
import com.example.ui.components.PwTextField
import com.example.ui.components.Space
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.InkTertiary
import com.example.ui.theme.Negative
import com.example.ui.theme.PositiveText
import com.example.ui.theme.SurfaceMuted
import com.example.ui.theme.SurfaceWhite
import com.example.ui.theme.WarningText
import com.example.ui.util.CategoryColorChoices
import com.example.ui.util.DateConversions
import com.example.ui.util.Formatters
import com.example.ui.util.getCategoryIcon
import java.util.Calendar

/** Icons offered for goals: things people save for. */
private val GoalIconChoices = listOf(
    "phone_android", "laptop", "two_wheeler", "flight", "home",
    "school", "card_giftcard", "savings", "local_hospital", "checkroom"
)

/**
 * Savings goals: all goals with their progress, or one goal's details when [selectedId] is set.
 * Money put into a goal is set aside, not spending.
 */
@Composable
fun GoalsScreen(
    goals: List<GoalProgress>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onSaveGoal: (id: Long, name: String, target: Double, iconKey: String, colorHex: Long, targetDate: Long?, alreadySaved: Double) -> Unit,
    onMoveMoney: (goalId: Long, amount: Double, takeOut: Boolean, note: String) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onDeleteGoal: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onAddCheckpoint: (goalId: Long, name: String, amount: Double) -> Unit = { _, _, _ -> },
    onDeleteCheckpoint: (Long) -> Unit = {},
    onBuy: (goalId: Long, amount: Double, category: String) -> Unit = { _, _, _ -> },
    streak: SavingStreak = SavingStreak(0, 0, 1, false, 0),
    roundUps: RoundUpSettings = RoundUpSettings(),
    jar: Double = 0.0,
    onRoundUpsEnabled: (Boolean) -> Unit = {},
    onRoundUpUnit: (Int) -> Unit = {},
    onMoveJar: (goalId: Long) -> Unit = {},
    achievements: AchievementProgress? = null,
    onOpenBadges: () -> Unit = {},
    challenges: List<ChallengeResult> = emptyList(),
    hasChallengeSuggestions: Boolean = false,
    onOpenChallenges: () -> Unit = {}
) {
    var chooseJarGoal by rememberSaveable { mutableStateOf(false) }
    var buyFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var checkpointFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteCheckpoint by rememberSaveable { mutableStateOf<Long?>(null) }
    // 0 = new goal; otherwise the id of the goal being edited.
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    // Goal id and direction of the money dialog.
    var moneyFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var takeOut by rememberSaveable { mutableStateOf(false) }
    var deleteEntry by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteGoal by rememberSaveable { mutableStateOf(false) }
    val selected = selectedId?.let { id -> goals.firstOrNull { it.goal.id == id } }
    val openMoney = { id: Long, out: Boolean ->
        moneyFor = id
        takeOut = out
    }

    Box(modifier = modifier.fillMaxSize().testTag("goals_screen")) {
        if (selected == null) {
            GoalList(
                challenges = challenges,
                hasChallengeSuggestions = hasChallengeSuggestions,
                onOpenChallenges = onOpenChallenges,
                achievements = achievements,
                onOpenBadges = onOpenBadges,
                streak = streak,
                roundUps = roundUps,
                jar = jar,
                onRoundUpsEnabled = onRoundUpsEnabled,
                onRoundUpUnit = onRoundUpUnit,
                onMoveJar = {
                    val active = goals.filterNot { it.isAchieved }
                    if (active.size == 1) onMoveJar(active.first().goal.id) else chooseJarGoal = true
                },
                goals = goals,
                onOpen = { onSelect(it) },
                onAdd = { openMoney(it, false) },
                onNew = { editing = 0L }
            )
        } else {
            GoalDetail(
                progress = selected,
                onAdd = { openMoney(selected.goal.id, false) },
                onTakeOut = { openMoney(selected.goal.id, true) },
                onEdit = { editing = selected.goal.id },
                onDeleteEntry = { deleteEntry = it },
                onDeleteGoal = { deleteGoal = true },
                onBuy = { buyFor = selected.goal.id },
                onAddCheckpoint = { checkpointFor = selected.goal.id },
                onDeleteCheckpoint = { deleteCheckpoint = it }
            )
        }
    }

    editing?.let { id ->
        GoalEditorDialog(
            existing = goals.firstOrNull { it.goal.id == id },
            onSave = { name, target, icon, color, date, saved ->
                onSaveGoal(id, name, target, icon, color, date, saved)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    moneyFor?.let { id ->
        // The goal may have been deleted meanwhile; then there is nothing to show.
        val goal = goals.firstOrNull { it.goal.id == id }
        if (goal != null) {
            GoalMoneyDialog(
                progress = goal,
                takeOut = takeOut,
                onSave = { amount, note ->
                    onMoveMoney(id, amount, takeOut, note)
                    moneyFor = null
                },
                onDismiss = { moneyFor = null }
            )
        }
    }

    if (chooseJarGoal) {
        AlertDialog(
            onDismissRequest = { chooseJarGoal = false },
            title = { Text("Move ${Formatters.formatCurrency(jar)} to…") },
            text = {
                Column {
                    goals.filterNot { it.isAchieved }.forEach { g ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onMoveJar(g.goal.id)
                                    chooseJarGoal = false
                                }
                                .padding(vertical = Space.s)
                        ) {
                            GoalRing(fraction = g.fraction, color = Color(g.goal.colorHex), size = 36.dp, stroke = 4.dp)
                            Spacer(modifier = Modifier.width(Space.m))
                            Text(g.goal.name, style = MaterialTheme.typography.titleSmall, color = Ink, modifier = Modifier.weight(1f))
                            Text(Formatters.formatCurrency(g.remaining) + " to go", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { chooseJarGoal = false }) { Text("Cancel", color = Ink) } }
        )
    }

    buyFor?.let { id ->
        goals.firstOrNull { it.goal.id == id }?.let { goal ->
            BuyGoalDialog(
                progress = goal,
                onBuy = { amount, category ->
                    onBuy(id, amount, category)
                    buyFor = null
                },
                onDismiss = { buyFor = null }
            )
        }
    }

    checkpointFor?.let { id ->
        goals.firstOrNull { it.goal.id == id }?.let { goal ->
            CheckpointDialog(
                progress = goal,
                onSave = { name, amount ->
                    onAddCheckpoint(id, name, amount)
                    checkpointFor = null
                },
                onDismiss = { checkpointFor = null }
            )
        }
    }

    deleteCheckpoint?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteCheckpoint = null },
            title = { Text("Delete this checkpoint?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCheckpoint(id)
                    deleteCheckpoint = null
                }) { Text("Delete", color = Negative) }
            },
            dismissButton = { TextButton(onClick = { deleteCheckpoint = null }) { Text("Cancel", color = Ink) } }
        )
    }

    deleteEntry?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteEntry = null },
            title = { Text("Delete this entry?") },
            text = { Text("The goal's progress is recalculated without it.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteEntry(id)
                    deleteEntry = null
                }) { Text("Delete", color = Negative) }
            },
            dismissButton = { TextButton(onClick = { deleteEntry = null }) { Text("Cancel", color = Ink) } }
        )
    }

    if (deleteGoal && selected != null) {
        AlertDialog(
            onDismissRequest = { deleteGoal = false },
            title = { Text("Delete \"${selected.goal.name}\"?") },
            text = { Text("The goal and its history are removed. The ${Formatters.formatCurrency(selected.saved)} set aside is no longer reserved.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteGoal(selected.goal.id)
                    deleteGoal = false
                }) { Text("Delete", color = Negative) }
            },
            dismissButton = { TextButton(onClick = { deleteGoal = false }) { Text("Cancel", color = Ink) } }
        )
    }
}

@Composable
private fun GoalList(
    challenges: List<ChallengeResult>,
    hasChallengeSuggestions: Boolean,
    onOpenChallenges: () -> Unit,
    achievements: AchievementProgress?,
    onOpenBadges: () -> Unit,
    streak: SavingStreak,
    roundUps: RoundUpSettings,
    jar: Double,
    onRoundUpsEnabled: (Boolean) -> Unit,
    onRoundUpUnit: (Int) -> Unit,
    onMoveJar: () -> Unit,
    goals: List<GoalProgress>,
    onOpen: (Long) -> Unit,
    onAdd: (Long) -> Unit,
    onNew: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(start = Space.screen, end = Space.screen, top = Space.s, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.section),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            PwCard {
                Text("Set aside for goals", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                Text(
                    Formatters.formatCurrency(Goals.totalSetAside(goals)),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Ink
                )
                Text(
                    "Not counted as spending. Your balance still includes it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
                Spacer(modifier = Modifier.height(Space.l))
                PwPrimaryButton(
                    text = "New goal",
                    onClick = onNew,
                    icon = Icons.Default.Add,
                    modifier = Modifier.fillMaxWidth().testTag("new_goal_button")
                )
            }
        }
        achievements?.let { progress -> item { LevelCard(progress, onClick = onOpenBadges) } }
        item { ChallengesSummaryCard(challenges, hasChallengeSuggestions, onClick = onOpenChallenges) }
        if (goals.isNotEmpty()) {
            item { StreakCard(streak) }
            item {
                RoundUpJarCard(
                    settings = roundUps,
                    jar = jar,
                    canMove = goals.any { !it.isAchieved },
                    onEnabled = onRoundUpsEnabled,
                    onUnit = onRoundUpUnit,
                    onMove = onMoveJar
                )
            }
        }
        if (goals.isEmpty()) {
            item {
                Text(
                    "Save for something you want - a phone, a trip, a bike service. Add money whenever you can and watch the milestones fill up.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary,
                    modifier = Modifier.padding(horizontal = Space.xs)
                )
            }
        }
        val active = goals.filterNot { it.isAchieved }
        val achieved = goals.filter { it.isAchieved }
        items(active, key = { it.goal.id }) { progress ->
            GoalCard(progress = progress, onClick = { onOpen(progress.goal.id) }, onAdd = { onAdd(progress.goal.id) })
        }
        if (achieved.isNotEmpty()) {
            item(key = "achieved_header") {
                Text(
                    "Achieved 🏆 · ${achieved.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    modifier = Modifier.padding(start = Space.xs, top = Space.s)
                )
            }
            items(achieved, key = { it.goal.id }) { progress ->
                GoalCard(progress = progress, onClick = { onOpen(progress.goal.id) }, onAdd = null)
            }
        }
    }
}

@Composable
internal fun GoalCard(progress: GoalProgress, onClick: () -> Unit, onAdd: (() -> Unit)?) {
    val color = Color(progress.goal.colorHex)
    PwCard(onClick = onClick, modifier = Modifier.testTag("goal_card_${progress.goal.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoalRing(fraction = progress.fraction, color = color, size = 56.dp, stroke = 6.dp) {
                Icon(getCategoryIcon(progress.goal.iconKey), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(Space.l))
            Column(modifier = Modifier.weight(1f)) {
                Text(progress.goal.name, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Formatters.formatCurrency(progress.saved)} of ${Formatters.formatCurrency(progress.goal.target)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )
                PlanLine(progress)
            }
            if (onAdd != null && !progress.isComplete) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.14f))
                        .clickable(onClick = onAdd)
                        .testTag("goal_add_${progress.goal.id}")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add money to ${progress.goal.name}", tint = color)
                }
            }
        }
        Spacer(modifier = Modifier.height(Space.m))
        MilestoneRow(progress.milestone, color)
    }
}

@Composable
private fun PlanLine(progress: GoalProgress) {
    val (text, color) = when {
        progress.isPurchased -> {
            val bought = progress.goal.purchasedAt?.let { Formatters.formatDate(it) }.orEmpty()
            "Bought $bought" + (progress.goal.purchasedAmount?.let { " for ${Formatters.formatCurrency(it)}" } ?: "") + " 🛍️" to PositiveText
        }
        progress.isComplete -> "Completed 🎉 · tap to mark as bought" to PositiveText
        progress.pace == GoalPace.MISSED -> "Target date passed · ${Formatters.formatCurrency(progress.remaining)} to go" to Negative
        progress.weeklyNeeded != null -> {
            val pace = if (progress.pace == GoalPace.BEHIND) "behind" else "on track"
            "Save ${Formatters.formatCurrency(progress.weeklyNeeded)}/week · $pace" to
                if (progress.pace == GoalPace.BEHIND) WarningText else PositiveText
        }
        else -> "${Formatters.formatCurrency(progress.remaining)} to go" to InkSecondary
    }
    Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** The four milestone badges: filled once reached. */
@Composable
private fun MilestoneRow(reached: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
        Goals.MILESTONES.forEach { m ->
            val done = reached >= m
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (done) color else SurfaceMuted)
            ) {
                Text(
                    if (m == 100) "100% ✓" else "$m%",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (done) SurfaceWhite else InkTertiary
                )
            }
        }
    }
}

@Composable
private fun GoalDetail(
    progress: GoalProgress,
    onAdd: () -> Unit,
    onTakeOut: () -> Unit,
    onEdit: () -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onDeleteGoal: () -> Unit,
    onBuy: () -> Unit,
    onAddCheckpoint: () -> Unit,
    onDeleteCheckpoint: (Long) -> Unit
) {
    val color = Color(progress.goal.colorHex)
    LazyColumn(
        contentPadding = PaddingValues(start = Space.screen, end = Space.screen, top = Space.s, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.section),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            PwCard {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    GoalRing(fraction = progress.fraction, color = color, size = 148.dp, stroke = 12.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(getCategoryIcon(progress.goal.iconKey), contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
                            Text(
                                if (progress.isPurchased) "🛍️" else "${(progress.fraction * 100).toInt()}%",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Ink
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(Space.l))
                    Text(progress.goal.name, style = MaterialTheme.typography.titleLarge, color = Ink, textAlign = TextAlign.Center)
                    if (!progress.isPurchased) {
                        Text(
                            "${Formatters.formatCurrency(progress.saved)} of ${Formatters.formatCurrency(progress.goal.target)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = InkSecondary
                        )
                    }
                    progress.goal.targetDate?.takeIf { !progress.isAchieved }?.let {
                        Text("By ${Formatters.formatDate(it)}", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                    }
                    Spacer(modifier = Modifier.height(Space.xs))
                    PlanLine(progress)
                    ForecastLine(progress)
                    Goals.nextCheckpoint(progress)?.let { next ->
                        Text(
                            "Next: ${next.name} · ${Formatters.formatCurrency(next.toGo)} to go",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary,
                            modifier = Modifier.testTag("goal_next_checkpoint")
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Space.l))
                MilestoneRow(progress.milestone, color)
                if (!progress.isPurchased) {
                    Spacer(modifier = Modifier.height(Space.l))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                        if (progress.isComplete) {
                            PwPrimaryButton(
                                text = "I bought it",
                                onClick = onBuy,
                                icon = Icons.Default.ShoppingBag,
                                modifier = Modifier.weight(1f).testTag("goal_buy_button")
                            )
                        } else {
                            PwPrimaryButton(
                                text = "Add money",
                                onClick = onAdd,
                                icon = Icons.Default.Add,
                                modifier = Modifier.weight(1f).testTag("goal_add_money_button")
                            )
                        }
                        PwSecondaryButton(
                            text = "Take out",
                            onClick = onTakeOut,
                            enabled = progress.saved > 0.0,
                            icon = Icons.Default.Remove,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = onEdit, modifier = Modifier.weight(1f).testTag("edit_goal_button")) {
                            Text("Edit goal", color = Ink, style = MaterialTheme.typography.labelLarge)
                        }
                        // Bought before reaching the target: still record it.
                        if (!progress.isComplete && progress.saved > 0.0) {
                            TextButton(onClick = onBuy, modifier = Modifier.weight(1f)) {
                                Text("Bought it already", color = Ink, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }

        val timeline = Goals.timeline(progress.entries)
        if (timeline.isNotEmpty()) {
            item {
                PwCard {
                    Text("Savings over time", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Spacer(modifier = Modifier.height(Space.m))
                    GoalSavingsChart(
                        points = timeline,
                        target = progress.goal.target,
                        color = color,
                        todayMs = FinancialCalculations.startOfDay(System.currentTimeMillis())
                    )
                }
            }
        }

        if (!progress.isPurchased) {
            item {
                PwCard(contentPadding = PaddingValues(vertical = Space.l)) {
                    PwSectionTitle(
                        title = "Checkpoints",
                        subtitle = "Your own milestones, celebrated when reached",
                        action = "Add",
                        onAction = onAddCheckpoint,
                        modifier = Modifier
                            .padding(horizontal = Space.xl)
                            .testTag("add_checkpoint_button")
                    )
                    if (progress.checkpoints.isEmpty()) {
                        Text(
                            "e.g. \"First ₹1,000\" or \"Half the deposit\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary,
                            modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.s)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(Space.s))
                        progress.checkpoints.forEach { checkpoint ->
                            CheckpointRow(
                                name = checkpoint.name,
                                amount = checkpoint.amount,
                                reached = progress.saved >= checkpoint.amount - Goals.EPSILON || progress.isAchieved,
                                color = color,
                                onLongClick = { onDeleteCheckpoint(checkpoint.id) }
                            )
                        }
                    }
                }
            }
        }

        if (progress.entries.isNotEmpty()) {
            item { Text("History · hold an entry to delete it", style = MaterialTheme.typography.labelLarge, color = InkSecondary) }
            item {
                PwCard(contentPadding = PaddingValues(vertical = Space.xs)) {
                    progress.entries.forEachIndexed { index, entry ->
                        if (index > 0) PwDivider()
                        GoalEntryRow(entry = entry, color = color, onLongClick = { onDeleteEntry(entry.id) })
                    }
                }
            }
        }
        item {
            TextButton(onClick = onDeleteGoal, modifier = Modifier.fillMaxWidth()) {
                Text("Delete goal", color = Negative, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Level, XP towards the next level and badges earned; opens the Badges screen. */
@Composable
internal fun LevelCard(progress: AchievementProgress, onClick: () -> Unit) {
    val level = progress.level
    PwCard(onClick = onClick, modifier = Modifier.testTag("level_card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoalRing(fraction = level.fraction(progress.xp), color = Accent, size = 56.dp, stroke = 6.dp) {
                Text("${level.number}", style = MaterialTheme.typography.titleLarge, color = Ink)
            }
            Spacer(modifier = Modifier.width(Space.l))
            Column(modifier = Modifier.weight(1f)) {
                Text("Level ${level.number} · ${level.name}", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    level.nextXp?.let { "${progress.xp} / $it XP" } ?: "${progress.xp} XP · top level",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
                Text(
                    "🏅 ${progress.earnedCount} of ${Badge.entries.size} badges",
                    style = MaterialTheme.typography.labelLarge,
                    color = Accent
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "See badges", tint = InkTertiary)
        }
    }
}

/** Every badge: earned ones in colour, locked ones greyed with how to earn them. */
@Composable
fun BadgesScreen(progress: AchievementProgress, modifier: Modifier = Modifier) {
    LazyColumn(
        contentPadding = PaddingValues(start = Space.screen, end = Space.screen, top = Space.s, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.section),
        modifier = modifier.fillMaxSize().testTag("badges_screen")
    ) {
        item {
            PwCard {
                Text("Level ${progress.level.number}", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                Text(progress.level.name, style = MaterialTheme.typography.headlineSmall, color = Ink)
                Spacer(modifier = Modifier.height(Space.m))
                val fraction = progress.level.fraction(progress.xp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(SurfaceMuted)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Accent)
                    )
                }
                Spacer(modifier = Modifier.height(Space.s))
                Text(
                    progress.level.nextXp?.let { "${progress.xp} XP · ${it - progress.xp} XP to level ${progress.level.number + 1}" }
                        ?: "${progress.xp} XP · you've reached the top level",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
                Spacer(modifier = Modifier.height(Space.s))
                Text(
                    "XP: ${Achievements.XP_TRANSACTION} per transaction · ${Achievements.XP_DEPOSIT} per goal deposit · " +
                        "${Achievements.XP_MILESTONE} per milestone · ${Achievements.XP_CHECKPOINT} per checkpoint · " +
                        "${Achievements.XP_STREAK_WEEK} per week of your best streak · ${Achievements.XP_BADGE} per badge",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkTertiary
                )
            }
        }
        item { Text("Badges · ${progress.earnedCount} of ${Badge.entries.size}", style = MaterialTheme.typography.titleMedium, color = Ink) }
        items(Badge.entries.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                pair.forEach { badge -> BadgeTile(badge, earned = badge in progress.earned, modifier = Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BadgeTile(badge: Badge, earned: Boolean, modifier: Modifier = Modifier) {
    PwCard(
        color = if (earned) SurfaceWhite else SurfaceMuted,
        contentPadding = PaddingValues(Space.l),
        modifier = modifier.testTag("badge_${badge.name}")
    ) {
        Text(
            if (earned) badge.emoji else "🔒",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.graphicsLayer { alpha = if (earned) 1f else 0.6f }
        )
        Spacer(modifier = Modifier.height(Space.s))
        Text(badge.title, style = MaterialTheme.typography.titleSmall, color = if (earned) Ink else InkSecondary)
        Text(badge.howTo, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
    }
}

/** Weeks in a row with money added to a goal, the best run, and the freeze. */
@Composable
private fun StreakCard(streak: SavingStreak) {
    PwCard(modifier = Modifier.testTag("saving_streak_card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (streak.current > 0) "🔥" else "🌱", style = MaterialTheme.typography.displaySmall)
            Spacer(modifier = Modifier.width(Space.l))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when (streak.current) {
                        0 -> "Start a saving streak"
                        1 -> "1-week saving streak"
                        else -> "${streak.current}-week saving streak"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink
                )
                Text(
                    if (streak.savedThisWeek) "Saved this week ✓" else "Add any amount to a goal this week to keep it going",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (streak.savedThisWeek) PositiveText else InkSecondary
                )
                val details = listOfNotNull(
                    "Best: ${streak.best} week${if (streak.best == 1) "" else "s"}".takeIf { streak.best > 0 },
                    if (streak.freezes > 0) "🧊 1 freeze ready" else "🧊 freeze back in ${streak.weeksToFreeze} saving week${if (streak.weeksToFreeze == 1) "" else "s"}"
                )
                Text(details.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = InkTertiary)
            }
        }
    }
}

/** Spare change from expenses, collected until the owner moves it into a goal. */
@Composable
private fun RoundUpJarCard(
    settings: RoundUpSettings,
    jar: Double,
    canMove: Boolean,
    onEnabled: (Boolean) -> Unit,
    onUnit: (Int) -> Unit,
    onMove: () -> Unit
) {
    PwCard(modifier = Modifier.testTag("round_up_jar_card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Round-up jar", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    if (settings.enabled) "Each expense rounds up to the next ₹${settings.unit}; the change lands here"
                    else "Collect the spare change from every expense",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
            }
            Spacer(modifier = Modifier.width(Space.s))
            Switch(
                checked = settings.enabled,
                onCheckedChange = onEnabled,
                colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = SurfaceWhite),
                modifier = Modifier.testTag("round_up_switch")
            )
        }
        if (settings.enabled || jar > 0.0) {
            Spacer(modifier = Modifier.height(Space.m))
            Text(Formatters.formatCurrency(jar), style = MaterialTheme.typography.headlineSmall, color = Ink, modifier = Modifier.testTag("round_up_jar_amount"))
            if (settings.enabled) {
                Spacer(modifier = Modifier.height(Space.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    RoundUps.UNITS.forEach { unit ->
                        PwChip("₹$unit", settings.unit == unit, { onUnit(unit) })
                    }
                }
            }
            Spacer(modifier = Modifier.height(Space.m))
            PwPrimaryButton(
                text = if (canMove) "Move to a goal" else "Create a goal to move it",
                onClick = onMove,
                enabled = canMove && jar >= 1.0,
                modifier = Modifier.fillMaxWidth().testTag("move_jar_button")
            )
        }
    }
}

/** "At this pace: Feb 12, 2027", amber when that is after the target date. */
@Composable
private fun ForecastLine(progress: GoalProgress) {
    if (progress.isAchieved) return
    val forecast = progress.forecastDate
    val due = progress.goal.targetDate
    val (text, color) = when {
        forecast == null && progress.entries.isEmpty() -> return
        forecast == null -> "Keep adding money to see when you'll get there" to InkSecondary
        due != null && forecast > due -> "At this pace: ${Formatters.formatDate(forecast)} (after your date)" to WarningText
        else -> "At this pace: ${Formatters.formatDate(forecast)}" to InkSecondary
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.testTag("goal_forecast"))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CheckpointRow(name: String, amount: Double, reached: Boolean, color: Color, onLongClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(horizontal = Space.xl, vertical = Space.s)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (reached) color else SurfaceMuted)
        ) {
            if (reached) Icon(Icons.Default.Check, contentDescription = "Reached", tint = SurfaceWhite, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(Space.m))
        Text(name, style = MaterialTheme.typography.titleSmall, color = if (reached) Ink else InkSecondary, modifier = Modifier.weight(1f))
        Text(Formatters.formatCurrency(amount), style = MaterialTheme.typography.titleSmall, color = if (reached) Ink else InkSecondary)
    }
}

/** "I bought it": record the purchase as an expense and release the money set aside. */
@Composable
private fun BuyGoalDialog(progress: GoalProgress, onBuy: (amount: Double, category: String) -> Unit, onDismiss: () -> Unit) {
    val categories = CategoryInfo.forType(LocalCategories.current, TransactionType.EXPENSE)
    val initial = if (progress.saved > 0.0) progress.saved else progress.goal.target
    var amountText by rememberSaveable { mutableStateOf(plainNumber(initial)) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val amount = FormValidation.parseAmount(amountText)
    val error = FormValidation.validateAmount(amountText).errorOrNull

    LendingDialogFrame(title = "Bought ${progress.goal.name}?", onDismiss = onDismiss, tag = "buy_goal_dialog") {
        Text(
            "Adds the price as an expense and releases the ${Formatters.formatCurrency(progress.saved)} you set aside. " +
                "The goal moves to Achieved.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSecondary
        )
        PwTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = "What it cost (₹)",
            errorText = error,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.testTag("buy_amount_input")
        )
        Text("Category", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
        CategoryGrid(categories = categories, selected = category, onSelect = { category = it })
        DialogButtons(
            confirm = "Mark as bought",
            enabled = error == null && amount != null && category != null,
            onConfirm = {
                val c = category ?: return@DialogButtons
                amount?.let { onBuy(it, c) }
            },
            onDismiss = onDismiss
        )
    }
}

/** A named checkpoint, e.g. "First ₹1,000". */
@Composable
private fun CheckpointDialog(progress: GoalProgress, onSave: (name: String, amount: Double) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var touched by rememberSaveable { mutableStateOf(false) }
    val amount = FormValidation.parseAmount(amountText)
    val error = FormValidation.validateAmount(amountText).errorOrNull
        ?: if (amount != null && amount > progress.goal.target + Goals.EPSILON) {
            "Must be at most the target (${Formatters.formatCurrency(progress.goal.target)})"
        } else {
            null
        }

    LendingDialogFrame(title = "New checkpoint", onDismiss = onDismiss, tag = "checkpoint_dialog") {
        PwTextField(
            value = name,
            onValueChange = { name = it },
            label = "Name",
            placeholder = "e.g. First ₹1,000",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.testTag("checkpoint_name_input")
        )
        PwTextField(
            value = amountText,
            onValueChange = {
                amountText = it
                touched = true
            },
            label = "When saved reaches (₹)",
            placeholder = "1000",
            errorText = error.takeIf { touched },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.testTag("checkpoint_amount_input")
        )
        DialogButtons(
            confirm = "Add",
            enabled = name.isNotBlank() && name.trim().length <= 40 && error == null && amount != null,
            onConfirm = { amount?.let { onSave(name, it) } },
            onDismiss = onDismiss
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GoalEntryRow(entry: GoalEntryEntity, color: Color, onLongClick: () -> Unit) {
    val added = entry.amount >= 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(horizontal = Space.l, vertical = Space.m)
            .testTag("goal_entry_${entry.id}")
    ) {
        PwIconBadge(icon = if (added) Icons.Default.Add else Icons.Default.Remove, tint = if (added) color else InkSecondary)
        Spacer(modifier = Modifier.width(Space.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(if (added) "Added" else "Taken out", style = MaterialTheme.typography.titleSmall, color = Ink)
            Text(
                listOfNotNull(Formatters.formatDate(entry.date), entry.note.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            (if (added) "+" else "-") + Formatters.formatCurrency(kotlin.math.abs(entry.amount)),
            style = MaterialTheme.typography.titleSmall,
            color = if (added) Ink else InkSecondary
        )
    }
}

/** New goal ([existing] null) or edit. */
@Composable
private fun GoalEditorDialog(
    existing: GoalProgress?,
    onSave: (name: String, target: Double, iconKey: String, colorHex: Long, targetDate: Long?, alreadySaved: Double) -> Unit,
    onDismiss: () -> Unit
) {
    val goal = existing?.goal
    var name by rememberSaveable { mutableStateOf(goal?.name.orEmpty()) }
    var targetText by rememberSaveable { mutableStateOf(goal?.target?.let { plainNumber(it) }.orEmpty()) }
    var savedText by rememberSaveable { mutableStateOf("") }
    var iconKey by rememberSaveable { mutableStateOf(goal?.iconKey ?: GoalIconChoices.first()) }
    var colorHex by rememberSaveable { mutableLongStateOf(goal?.colorHex ?: CategoryColorChoices[5]) }
    var targetDate by rememberSaveable { mutableStateOf(goal?.targetDate) }
    var touched by rememberSaveable { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }

    val targetError = FormValidation.validateAmount(targetText).errorOrNull
    val savedError = if (savedText.isBlank()) null else FormValidation.validateAmount(savedText).errorOrNull
    val canSave = name.isNotBlank() && name.trim().length <= 40 && targetError == null && savedError == null

    LendingDialogFrame(title = if (goal == null) "New goal" else "Edit goal", onDismiss = onDismiss, tag = "goal_editor_dialog") {
        PwTextField(
            value = name,
            onValueChange = { name = it },
            label = "What are you saving for?",
            placeholder = "e.g. New phone",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.testTag("goal_name_input")
        )
        PwTextField(
            value = targetText,
            onValueChange = {
                targetText = it
                touched = true
            },
            label = "Target (₹)",
            placeholder = "20000",
            errorText = targetError.takeIf { touched },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.testTag("goal_target_input")
        )
        if (goal == null) {
            PwTextField(
                value = savedText,
                onValueChange = { savedText = it },
                label = "Already saved (optional)",
                placeholder = "0",
                errorText = savedError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }

        Text("Reach it by", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
        TargetDateChips(targetDate = targetDate, onChange = { targetDate = it }, onPick = { pickDate = true })

        Text("Icon", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
        GoalIconChoices.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    val selected = key == iconKey
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color(colorHex).copy(alpha = 0.16f) else SurfaceMuted)
                            .border(BorderStroke(2.dp, if (selected) Color(colorHex) else Color.Transparent), CircleShape)
                            .clickable { iconKey = key }
                    ) {
                        Icon(getCategoryIcon(key), contentDescription = key, tint = if (selected) Color(colorHex) else InkSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        Text("Colour", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
        CategoryColorChoices.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                row.forEach { hex ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(hex))
                            .border(BorderStroke(3.dp, if (hex == colorHex) Ink else Color.Transparent), CircleShape)
                            .clickable { colorHex = hex }
                    )
                }
            }
        }

        DialogButtons(
            confirm = if (goal == null) "Create goal" else "Save",
            enabled = canSave,
            onConfirm = {
                val target = FormValidation.parseAmount(targetText) ?: return@DialogButtons
                val saved = if (savedText.isBlank()) 0.0 else FormValidation.parseAmount(savedText) ?: return@DialogButtons
                onSave(name.trim(), target, iconKey, colorHex, targetDate, saved)
            },
            onDismiss = onDismiss
        )
    }

    if (pickDate) {
        PwDatePicker(
            initialMs = targetDate ?: (System.currentTimeMillis() + 90 * 86_400_000L),
            future = true,
            onPick = { targetDate = FinancialCalculations.startOfDay(DateConversions.utcDayToLocal(it, keepTimeOfMs = 0L)) },
            onDismiss = { pickDate = false }
        )
    }
}

@Composable
private fun TargetDateChips(targetDate: Long?, onChange: (Long?) -> Unit, onPick: () -> Unit) {
    fun monthsAhead(months: Int): Long = FinancialCalculations.startOfDay(
        Calendar.getInstance().apply { add(Calendar.MONTH, months) }.timeInMillis
    )
    val presets = listOf(3, 6, 12).map { it to monthsAhead(it) }
    val custom = targetDate != null && presets.none { it.second == targetDate }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        PwChip("No date", targetDate == null, { onChange(null) })
        presets.forEach { (months, date) ->
            PwChip(if (months == 12) "1 year" else "$months months", targetDate == date, { onChange(date) })
        }
        PwChip(if (custom) Formatters.formatDate(targetDate!!) else "Pick date", custom, onPick)
    }
}

/** Add money to a goal or take some out. */
@Composable
private fun GoalMoneyDialog(
    progress: GoalProgress,
    takeOut: Boolean,
    onSave: (amount: Double, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var touched by rememberSaveable { mutableStateOf(false) }
    val amount = FormValidation.parseAmount(amountText)
    val error = FormValidation.validateAmount(amountText).errorOrNull
        ?: if (takeOut && amount != null && amount > progress.saved + Goals.EPSILON) {
            "Only ${Formatters.formatCurrency(progress.saved)} is set aside"
        } else {
            null
        }

    LendingDialogFrame(
        title = if (takeOut) "Take out of ${progress.goal.name}" else "Add to ${progress.goal.name}",
        onDismiss = onDismiss,
        tag = "goal_money_dialog"
    ) {
        Text(
            if (takeOut) "${Formatters.formatCurrency(progress.saved)} set aside" else "${Formatters.formatCurrency(progress.remaining)} to go",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSecondary
        )
        PwTextField(
            value = amountText,
            onValueChange = {
                amountText = it
                touched = true
            },
            label = "Amount (₹)",
            placeholder = "0",
            errorText = error.takeIf { touched },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.testTag("goal_amount_input")
        )
        if (!takeOut) {
            // Quick amounts, capped at what is left so one tap can finish the goal.
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                listOf(100.0, 500.0, 1000.0).filter { it < progress.remaining }.forEach { quick ->
                    PwChip(Formatters.formatCurrency(quick), amount == quick, {
                        amountText = plainNumber(quick)
                        touched = true
                    })
                }
                if (progress.remaining > 0) {
                    PwChip("All ${Formatters.formatCurrency(progress.remaining)}", amount == progress.remaining, {
                        amountText = plainNumber(progress.remaining)
                        touched = true
                    })
                }
            }
        }
        PwTextField(
            value = note,
            onValueChange = { note = it },
            label = "Note (optional)",
            placeholder = if (takeOut) "e.g. emergency" else "e.g. from salary"
        )
        DialogButtons(
            confirm = if (takeOut) "Take out" else "Add",
            enabled = error == null && amount != null,
            onConfirm = { amount?.let { onSave(it, note) } },
            onDismiss = onDismiss
        )
    }
}

/**
 * Full-screen celebration (goal milestone, checkpoint, badge or level): confetti over a dimmed
 * app and a card. Shown above everything, not in a dialog window, so the confetti covers the
 * whole screen. [onShown] runs once when it appears (sound and vibration).
 */
@Composable
fun CelebrationOverlay(emoji: String, title: String, message: String, onShown: () -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val scale = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) {
        onShown()
        scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 400f))
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .testTag("milestone_celebration")
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = SurfaceWhite,
            modifier = Modifier
                .padding(Space.screen)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(Space.xxl)
            ) {
                Text(emoji, style = MaterialTheme.typography.displayLarge)
                Spacer(modifier = Modifier.height(Space.m))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Ink,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(Space.s))
                Text(message, style = MaterialTheme.typography.bodyLarge, color = InkSecondary, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(Space.xl))
                PwPrimaryButton(text = "Nice!", onClick = onDismiss, modifier = Modifier.fillMaxWidth().testTag("celebration_ok_button"))
            }
        }
        ConfettiBurst()
    }
}

private fun plainNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(java.util.Locale.US, "%.2f", value)
