package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.DebtEntryEntity
import com.example.domain.DebtKind
import com.example.domain.Lending
import com.example.domain.PersonBalance
import com.example.ui.components.PwCard
import com.example.ui.components.PwDivider
import com.example.ui.components.PwIconBadge
import com.example.ui.components.PwListRow
import com.example.ui.components.PwPrimaryButton
import com.example.ui.components.PwSecondaryButton
import com.example.ui.components.Space
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentSoft
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.InkTertiary
import com.example.ui.theme.Negative
import com.example.ui.theme.PositiveText
import com.example.ui.util.Formatters

/**
 * Money lent and borrowed: everyone's balance, or one person's history when [selectedKey]
 * is set. Nothing here counts as spending or income.
 */
@Composable
fun LendingScreen(
    balances: List<PersonBalance>,
    selectedKey: String?,
    onSelectPerson: (String?) -> Unit,
    onAddEntry: (person: String, kind: DebtKind, amount: Double, date: Long, dueDate: Long?, note: String) -> Unit,
    onSettleUp: (PersonBalance) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onDeletePerson: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var addKind by rememberSaveable { mutableStateOf<DebtKind?>(null) }
    var deleteEntry by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletePerson by rememberSaveable { mutableStateOf(false) }
    val person = selectedKey?.let { key -> balances.firstOrNull { it.key == key } }

    Box(modifier = modifier.fillMaxSize().testTag("lending_screen")) {
        if (person == null) {
            PeopleList(balances = balances, onSelect = { onSelectPerson(it.key) }, onAdd = { addKind = it })
        } else {
            PersonHistory(
                person = person,
                onAdd = { addKind = it },
                onSettleUp = { onSettleUp(person) },
                onDeleteEntry = { deleteEntry = it },
                onDeletePerson = { deletePerson = true }
            )
        }
    }

    addKind?.let { kind ->
        DebtEntryDialog(
            kind = kind,
            people = balances,
            fixedPerson = person?.name,
            // A repayment usually clears what is outstanding; start from that amount.
            suggestedAmount = person?.takeIf { !kind.canHaveDueDate && !it.isSettled }?.let { kotlin.math.abs(it.balance) },
            onSave = { name, amount, date, due, note ->
                onAddEntry(name, kind, amount, date, due, note)
                addKind = null
            },
            onDismiss = { addKind = null }
        )
    }

    deleteEntry?.let { id ->
        ConfirmDialog(
            title = "Delete this entry?",
            text = "The balance is recalculated without it.",
            confirm = "Delete",
            onConfirm = {
                onDeleteEntry(id)
                deleteEntry = null
            },
            onDismiss = { deleteEntry = null }
        )
    }

    if (deletePerson && person != null) {
        ConfirmDialog(
            title = "Delete ${person.name}?",
            text = "All ${person.entries.size} entries with ${person.name} are removed. This cannot be undone.",
            confirm = "Delete",
            onConfirm = {
                onDeletePerson(person.key)
                deletePerson = false
            },
            onDismiss = { deletePerson = false }
        )
    }
}

@Composable
private fun PeopleList(balances: List<PersonBalance>, onSelect: (PersonBalance) -> Unit, onAdd: (DebtKind) -> Unit) {
    val totals = Lending.totals(balances)
    LazyColumn(
        contentPadding = PaddingValues(start = Space.screen, end = Space.screen, top = Space.s, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.section),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            PwCard {
                Row {
                    TotalStat("People owe you", Formatters.formatCurrency(totals.owedToMe), PositiveText, Modifier.weight(1f))
                    TotalStat("You owe", Formatters.formatCurrency(totals.iOwe), Ink, Modifier.weight(1f))
                }
                if (totals.overdueCount > 0) {
                    Spacer(modifier = Modifier.height(Space.m))
                    Text(
                        "${totals.overdueCount} overdue",
                        style = MaterialTheme.typography.labelLarge,
                        color = Negative
                    )
                }
                Spacer(modifier = Modifier.height(Space.l))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    PwPrimaryButton(
                        text = "I lent money",
                        onClick = { onAdd(DebtKind.LENT) },
                        modifier = Modifier.weight(1f).testTag("lend_button")
                    )
                    PwSecondaryButton(
                        text = "I borrowed",
                        onClick = { onAdd(DebtKind.BORROWED) },
                        modifier = Modifier.weight(1f).testTag("borrow_button")
                    )
                }
            }
        }

        if (balances.isEmpty()) {
            item {
                Text(
                    "Nobody here yet. Add money you lend or borrow, or mark a bank SMS as lent when you import it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary,
                    modifier = Modifier.padding(horizontal = Space.xs)
                )
            }
        } else {
            item {
                PwCard(contentPadding = PaddingValues(vertical = Space.xs)) {
                    balances.forEachIndexed { index, p ->
                        if (index > 0) PwDivider()
                        PersonRow(person = p, onClick = { onSelect(p) })
                    }
                }
            }
        }
    }
}

@Composable
internal fun PersonRow(person: PersonBalance, onClick: () -> Unit) {
    val due = dueLine(person)
    PwListRow(
        title = person.name,
        subtitle = listOfNotNull(statusLine(person), due).joinToString(" · "),
        leading = { Avatar(person.name, overdue = person.isOverdue) },
        onClick = onClick,
        modifier = Modifier.testTag("person_row_${person.key}")
    ) {
        Text(
            if (person.isSettled) "—" else Formatters.formatCurrency(kotlin.math.abs(person.balance)),
            style = MaterialTheme.typography.titleSmall,
            color = when {
                person.isOverdue -> Negative
                person.theyOweMe -> PositiveText
                person.iOweThem -> Ink
                else -> InkTertiary
            }
        )
    }
}

@Composable
private fun PersonHistory(
    person: PersonBalance,
    onAdd: (DebtKind) -> Unit,
    onSettleUp: () -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onDeletePerson: () -> Unit
) {
    // Most likely next step first: collect when they owe, repay when the owner owes.
    val (primary, secondary) = when {
        person.theyOweMe -> DebtKind.GOT_BACK to DebtKind.LENT
        person.iOweThem -> DebtKind.PAID_BACK to DebtKind.BORROWED
        else -> DebtKind.LENT to DebtKind.BORROWED
    }
    LazyColumn(
        contentPadding = PaddingValues(start = Space.screen, end = Space.screen, top = Space.s, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.section),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            PwCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(person.name, overdue = person.isOverdue)
                    Spacer(modifier = Modifier.width(Space.m))
                    Text(person.name, style = MaterialTheme.typography.titleLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(modifier = Modifier.height(Space.l))
                Text(
                    when {
                        person.theyOweMe -> "Owes you"
                        person.iOweThem -> "You owe"
                        else -> "All settled up"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSecondary
                )
                if (!person.isSettled) {
                    Text(
                        Formatters.formatCurrency(kotlin.math.abs(person.balance)),
                        style = MaterialTheme.typography.displaySmall,
                        color = if (person.theyOweMe) PositiveText else Ink,
                        modifier = Modifier.testTag("person_balance_text")
                    )
                }
                dueLine(person)?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge, color = if (person.isOverdue) Negative else InkSecondary)
                }
                Spacer(modifier = Modifier.height(Space.l))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    PwPrimaryButton(
                        text = primary.actionTitle(),
                        onClick = { onAdd(primary) },
                        modifier = Modifier.weight(1f).testTag("person_primary_button")
                    )
                    PwSecondaryButton(
                        text = if (person.isSettled) secondary.actionTitle() else "${secondary.label} more",
                        onClick = { onAdd(secondary) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!person.isSettled) {
                    TextButton(onClick = onSettleUp, modifier = Modifier.fillMaxWidth().testTag("settle_up_button")) {
                        Text("Settle up (mark all repaid)", color = Accent, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        item { Text("History · hold an entry to delete it", style = MaterialTheme.typography.labelLarge, color = InkSecondary) }
        item {
            PwCard(contentPadding = PaddingValues(vertical = Space.xs)) {
                person.entries.forEachIndexed { index, entry ->
                    if (index > 0) PwDivider()
                    EntryRow(entry = entry, onLongClick = { onDeleteEntry(entry.id) })
                }
            }
        }
        item {
            TextButton(onClick = onDeletePerson, modifier = Modifier.fillMaxWidth()) {
                Text("Delete ${person.name}", color = Negative, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(entry: DebtEntryEntity, onLongClick: () -> Unit) {
    val kind = DebtKind.fromName(entry.kind) ?: return
    val out = kind.towardsTheyOweMe // money left the owner's account
    val details = listOfNotNull(
        Formatters.formatDate(entry.date),
        entry.dueDate?.let { "due ${Formatters.formatDate(it)}" },
        entry.note.takeIf { it.isNotBlank() },
        "from bank SMS".takeIf { entry.smsKey != null }
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(horizontal = Space.l, vertical = Space.m)
            .testTag("debt_entry_${entry.id}")
    ) {
        PwIconBadge(
            icon = if (out) Icons.Default.NorthEast else Icons.Default.SouthWest,
            tint = if (out) Ink else PositiveText
        )
        Spacer(modifier = Modifier.width(Space.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(kind.label, style = MaterialTheme.typography.titleSmall, color = Ink)
            Text(
                details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(Space.s))
        Text(
            Formatters.formatCurrency(entry.amount),
            style = MaterialTheme.typography.titleSmall,
            color = if (out) Ink else PositiveText
        )
    }
}

@Composable
internal fun TotalStat(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = InkSecondary)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color, maxLines = 1)
    }
}

@Composable
internal fun Avatar(name: String, overdue: Boolean = false) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (overdue) Negative.copy(alpha = 0.12f) else AccentSoft)
    ) {
        Text(
            name.trim().take(1).uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = if (overdue) Negative else Accent
        )
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = Negative) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Ink) } }
    )
}
