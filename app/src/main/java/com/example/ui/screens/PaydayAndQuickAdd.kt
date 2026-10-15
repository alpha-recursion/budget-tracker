package com.example.ui.screens

import com.example.domain.InputGuard
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.FormValidation
import com.example.domain.PayCycleStatus
import com.example.domain.PaySettings
import com.example.domain.QuickChip
import com.example.ui.components.PwCard
import com.example.ui.components.PwChip
import com.example.ui.components.PwIconBadge
import com.example.ui.components.PwPrimaryButton
import com.example.ui.components.PwSecondaryButton
import com.example.ui.components.PwTextField
import com.example.ui.components.Space
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentSoft
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.InkTertiary
import com.example.ui.theme.Negative
import com.example.ui.theme.Positive
import com.example.ui.theme.SurfaceMuted
import com.example.ui.theme.Warning
import com.example.ui.util.Formatters
import com.example.ui.util.LocalCategories
import com.example.ui.util.getCategoryIcon

/** Home: one-tap expenses for the owner's usual payees. Hold a chip for more. */
@Composable
fun QuickAddRow(
    chips: List<QuickChip>,
    onAdd: (QuickChip) -> Unit,
    onAddWithChanges: (QuickChip) -> Unit,
    onPin: (QuickChip) -> Unit,
    onUnpin: (QuickChip) -> Unit,
    onHide: (QuickChip) -> Unit
) {
    if (chips.isEmpty()) return
    Column(modifier = Modifier.testTag("quick_add_row")) {
        Text(
            "Quick add · tap to add, hold for more",
            style = MaterialTheme.typography.labelMedium,
            color = InkSecondary,
            modifier = Modifier.padding(start = Space.xs, bottom = Space.s)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            chips.forEach { chip ->
                QuickChipItem(chip, onAdd, onAddWithChanges, onPin, onUnpin, onHide)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickChipItem(
    chip: QuickChip,
    onAdd: (QuickChip) -> Unit,
    onAddWithChanges: (QuickChip) -> Unit,
    onPin: (QuickChip) -> Unit,
    onUnpin: (QuickChip) -> Unit,
    onHide: (QuickChip) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val categories = LocalCategories.current
    val info = categories.firstOrNull { it.name == chip.category }
    val tint = info?.let { Color(it.colorHex) } ?: Accent
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(com.example.ui.theme.SurfaceWhite)
                .combinedClickable(onClick = { onAdd(chip) }, onLongClick = { menu = true })
                .padding(horizontal = Space.m, vertical = Space.s)
                .testTag("quick_chip_${chip.key}")
        ) {
            Icon(
                getCategoryIcon(info?.iconKey ?: "more_horiz"),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(chip.title, style = MaterialTheme.typography.labelLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.width(4.dp))
            Text(Formatters.formatCurrency(chip.amount), style = MaterialTheme.typography.labelLarge, color = InkSecondary)
            if (chip.pinned) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = InkTertiary, modifier = Modifier.size(14.dp))
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Change amount and add") }, onClick = { menu = false; onAddWithChanges(chip) })
            if (chip.pinned) {
                DropdownMenuItem(text = { Text("Unpin") }, onClick = { menu = false; onUnpin(chip) })
            } else {
                DropdownMenuItem(text = { Text("Pin (always show)") }, onClick = { menu = false; onPin(chip) })
            }
            DropdownMenuItem(text = { Text("Hide", color = Negative) }, onClick = { menu = false; onHide(chip) })
        }
    }
}

/** Home: what is left of this month's income until the next payday. */
@Composable
fun PaydayCard(status: PayCycleStatus, onEdit: () -> Unit) {
    val over = status.left < 0
    val barColor = when {
        over -> Negative
        status.fractionUsed > 0.85f -> Warning
        else -> Positive
    }
    PwCard(onClick = onEdit, modifier = Modifier.testTag("payday_card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Until payday · ${status.daysLeft} day${if (status.daysLeft == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSecondary
                )
                Text(
                    if (over) "${Formatters.formatCurrency(-status.left)} over" else "${Formatters.formatCurrency(status.left)} left",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (over) Negative else Ink,
                    modifier = Modifier.testTag("payday_left_text")
                )
                Text(
                    if (over) "You've used more than this month's income" else "About ${Formatters.formatCurrency(status.perDay)} a day",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
            }
            Text("Payday\n${Formatters.formatShortDate(status.nextPayday)}", style = MaterialTheme.typography.labelMedium, color = InkTertiary)
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
                    .fillMaxWidth(status.fractionUsed)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor)
            )
        }
        Spacer(modifier = Modifier.height(Space.xs))
        Text(
            "Spent ${Formatters.formatCurrency(status.spent)}" +
                (if (status.savedToGoals > 0) " · ${Formatters.formatCurrency(status.savedToGoals)} to goals" else "") +
                " of ${Formatters.formatCurrency(status.income)}",
            style = MaterialTheme.typography.labelSmall,
            color = InkTertiary
        )
    }
}

/** Home, before payday is set: invitation to set it. */
@Composable
fun PaydayInviteCard(onClick: () -> Unit) {
    PwCard(onClick = onClick, contentPadding = PaddingValues(Space.l), modifier = Modifier.testTag("payday_invite_card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PwIconBadge(icon = Icons.Default.Payments, tint = Accent)
            Spacer(modifier = Modifier.width(Space.m))
            Column(modifier = Modifier.weight(1f)) {
                Text("Income & payday", style = MaterialTheme.typography.titleSmall, color = Ink)
                Text("See what's left until your next salary", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = InkTertiary)
        }
    }
}

/** Home, on payday until the salary is recorded: add it in one tap. */
@Composable
fun SalaryCard(amount: Double, onAdd: () -> Unit, onDifferent: () -> Unit, onDismiss: () -> Unit) {
    PwCard(color = AccentSoft, modifier = Modifier.testTag("salary_card")) {
        Text("💸 Salary day!", style = MaterialTheme.typography.titleMedium, color = Ink)
        Text(
            "Add your salary of ${Formatters.formatCurrency(amount)} as income?",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSecondary
        )
        Spacer(modifier = Modifier.height(Space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            PwPrimaryButton(text = "Add", onClick = onAdd, modifier = Modifier.weight(1f).testTag("salary_add_button"))
            PwSecondaryButton(text = "Other amount", onClick = onDifferent, modifier = Modifier.weight(1f))
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text("Not yet", color = InkSecondary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Monthly income and payday (day of the month). */
@Composable
fun PaySettingsDialog(current: PaySettings, onSave: (income: Double, payday: Int) -> Unit, onDismiss: () -> Unit) {
    var incomeText by rememberSaveable {
        mutableStateOf(if (current.monthlyIncome > 0) current.monthlyIncome.toLong().toString() else "")
    }
    var payday by rememberSaveable { mutableStateOf(current.payday.takeIf { it in 1..31 } ?: 1) }
    val error = FormValidation.validateAmount(incomeText).errorOrNull
    LendingDialogFrame(title = "Income & payday", onDismiss = onDismiss, tag = "pay_settings_dialog") {
        Text(
            "Used for \"left until payday\" and the salary reminder. Nothing is added to your transactions by itself.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSecondary
        )
        PwTextField(
            value = incomeText,
            onValueChange = { incomeText = it },
            label = "Monthly income (₹)",
            placeholder = "30000",
            errorText = error.takeIf { incomeText.isNotBlank() },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            filter = InputGuard::amount,
            modifier = Modifier.testTag("income_input")
        )
        Text("Payday (day of the month)", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
        (1..31).chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    Box(modifier = Modifier.weight(1f)) {
                        PwChip(text = "$d", selected = payday == d, onClick = { payday = d }, modifier = Modifier.fillMaxWidth())
                    }
                }
                repeat(7 - week.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
        if (payday > 28) {
            Text("In shorter months, the last day of the month counts.", style = MaterialTheme.typography.labelSmall, color = InkTertiary)
        }
        DialogButtons(
            confirm = "Save",
            enabled = error == null,
            onConfirm = { FormValidation.parseAmount(incomeText)?.let { onSave(it, payday) } },
            onDismiss = onDismiss
        )
    }
}
