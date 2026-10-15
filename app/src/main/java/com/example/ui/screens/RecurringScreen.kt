package com.example.ui.screens

import com.example.domain.InputGuard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.example.data.local.RecurringEntity
import com.example.data.model.CategoryInfo
import com.example.data.model.TransactionType
import com.example.domain.FinancialCalculations
import com.example.domain.FormValidation
import com.example.domain.Recurring
import com.example.domain.RecurringFrequency
import com.example.domain.RecurringSuggestion
import com.example.ui.components.CategoryPickerDialog
import com.example.ui.components.PwCard
import com.example.ui.components.PwChip
import com.example.ui.components.PwPrimaryButton
import com.example.ui.components.PwSecondaryButton
import com.example.ui.components.PwSectionTitle
import com.example.ui.components.PwSegmented
import com.example.ui.components.PwTextField
import com.example.ui.components.Space
import com.example.ui.theme.AccentSoft
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.InkTertiary
import com.example.ui.theme.Negative
import com.example.ui.util.DateConversions
import com.example.ui.util.Formatters

/** Subscriptions and bills: what is still to pay this month, suggestions, and the full list. */
@Composable
fun RecurringScreen(
    entries: List<RecurringEntity>,
    suggestions: List<RecurringSuggestion>,
    categories: List<CategoryInfo>,
    onSave: (RecurringEntity) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // 0 = closed, otherwise the id being edited; -1 = new.
    var editingId by rememberSaveable { mutableStateOf(0L) }
    var suggestion by remember { mutableStateOf<RecurringSuggestion?>(null) }
    val now = System.currentTimeMillis()
    val today = FinancialCalculations.startOfDay(now)
    val month = Recurring.month(entries, now)

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("recurring_screen"),
        contentPadding = PaddingValues(start = Space.screen, end = Space.screen, top = Space.s, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.section)
    ) {
        item {
            PwCard(color = AccentSoft) {
                Text("Still to pay this month", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                Text(
                    Formatters.formatCurrency(month.remaining),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink,
                    modifier = Modifier.testTag("recurring_month_total")
                )
                Text(
                    when {
                        entries.none { it.active } -> "Add your subscriptions and bills to see what is coming up"
                        month.remainingCount == 0 -> "Nothing else due this month"
                        else -> "${month.remainingCount} payment${if (month.remainingCount == 1) "" else "s"} to go · reminders arrive ${Recurring.ALERT_DAYS} days before"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
                Spacer(modifier = Modifier.height(Space.m))
                PwPrimaryButton(
                    text = "Add recurring payment",
                    onClick = { editingId = -1L },
                    modifier = Modifier.fillMaxWidth().testTag("recurring_add_button")
                )
            }
        }

        if (suggestions.isNotEmpty()) {
            item { PwSectionTitle("Looks recurring", subtitle = "Found in your spending") }
            items(suggestions, key = { "s:" + it.name }) { s ->
                PwCard(modifier = Modifier.testTag("recurring_suggestion")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleSmall, color = Ink)
                            Text(
                                "${Formatters.formatCurrency(s.amount)} · ${s.frequency.label} · next ${Formatters.formatShortDate(s.nextDue)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(Space.m))
                        PwSecondaryButton(text = "Add", onClick = { suggestion = s })
                    }
                }
            }
        }

        item { PwSectionTitle("Your recurring payments") }
        if (entries.isEmpty()) {
            item {
                Text(
                    "Nothing here yet. Netflix, a phone recharge, rent: add anything that repeats.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkTertiary
                )
            }
        }
        items(entries, key = { it.id }) { item ->
            val next = Recurring.nextDue(item, today)
            PwCard(onClick = { editingId = item.id }, modifier = Modifier.testTag("recurring_row")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleSmall, color = if (item.active) Ink else InkTertiary)
                        Text(
                            if (item.active) "${Recurring.frequencyOf(item).label} · next ${Formatters.formatShortDate(next)}"
                            else "Paused · no reminders",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary
                        )
                    }
                    Text(
                        Formatters.formatCurrency(item.amount),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (item.active) Ink else InkTertiary
                    )
                }
            }
        }
    }

    val editing = entries.firstOrNull { it.id == editingId }
    if (editingId == -1L || editing != null) {
        RecurringDialog(
            existing = editing,
            categories = categories,
            onSave = {
                onSave(it)
                editingId = 0L
            },
            onDelete = editing?.let { e ->
                {
                    onDelete(e.id)
                    editingId = 0L
                }
            },
            onDismiss = { editingId = 0L }
        )
    }
    suggestion?.let { s ->
        RecurringDialog(
            existing = null,
            prefill = s,
            categories = categories,
            onSave = {
                onSave(it)
                suggestion = null
            },
            onDelete = null,
            onDismiss = { suggestion = null }
        )
    }
}

private val FREQUENCIES = RecurringFrequency.entries

/** Add or edit one recurring payment. Dates here are "next due"; saving keeps the same schedule. */
@Composable
private fun RecurringDialog(
    existing: RecurringEntity?,
    categories: List<CategoryInfo>,
    onSave: (RecurringEntity) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    prefill: RecurringSuggestion? = null
) {
    val expenseCategories = categories.filter { it.type == TransactionType.EXPENSE }
    val today = FinancialCalculations.startOfDay(System.currentTimeMillis())
    val initialDue = when {
        existing != null -> Recurring.nextDue(existing, today)
        prefill != null -> prefill.nextDue
        else -> today
    }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: prefill?.name.orEmpty()) }
    var amountText by rememberSaveable {
        mutableStateOf((existing?.amount ?: prefill?.amount)?.let { plain(it) }.orEmpty())
    }
    var amountTouched by rememberSaveable { mutableStateOf(false) }
    var frequencyIndex by rememberSaveable {
        mutableStateOf(FREQUENCIES.indexOf(existing?.let { Recurring.frequencyOf(it) } ?: prefill?.frequency ?: RecurringFrequency.MONTHLY))
    }
    var dueMs by rememberSaveable { mutableLongStateOf(initialDue) }
    var category by rememberSaveable {
        mutableStateOf(
            existing?.category ?: prefill?.category
                ?: expenseCategories.firstOrNull { it.name.contains("Bill", ignoreCase = true) }?.name
                ?: expenseCategories.firstOrNull()?.name.orEmpty()
        )
    }
    var active by rememberSaveable { mutableStateOf(existing?.active ?: true) }
    var pickDate by remember { mutableStateOf(false) }
    var pickCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val amountError = FormValidation.validateAmount(amountText).errorOrNull
    val canSave = FormValidation.validateName(name, InputGuard.MAX_NAME).isValid && amountError == null && category.isNotBlank()

    LendingDialogFrame(
        title = if (existing == null) "Recurring payment" else "Edit recurring payment",
        onDismiss = onDismiss,
        tag = "recurring_dialog"
    ) {
        PwTextField(
            value = name,
            onValueChange = { name = it },
            label = "Name",
            placeholder = "e.g. Netflix, Jio recharge",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            filter = { InputGuard.label(it, InputGuard.MAX_NAME) },
            modifier = Modifier.testTag("recurring_name_input")
        )
        PwTextField(
            value = amountText,
            onValueChange = {
                amountText = it
                amountTouched = true
            },
            label = "Amount (₹)",
            placeholder = "0",
            errorText = amountError.takeIf { amountTouched },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            filter = InputGuard::amount,
            modifier = Modifier.testTag("recurring_amount_input")
        )
        Text("Repeats", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
        PwSegmented(
            options = FREQUENCIES.map { it.label },
            selectedIndex = frequencyIndex,
            onSelect = { frequencyIndex = it }
        )
        Text("Next due", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            PwChip("Today", dueMs == today, { dueMs = today })
            PwChip(
                if (dueMs == today) "Pick date" else Formatters.formatDate(dueMs),
                dueMs != today,
                { pickDate = true }
            )
        }
        Text("Category", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            PwChip(category.ifBlank { "Choose" }, true, { pickCategory = true })
        }
        if (existing != null) {
            PwSegmented(
                options = listOf("Reminders on", "Paused"),
                selectedIndex = if (active) 0 else 1,
                onSelect = { active = it == 0 }
            )
        }
        DialogButtons(
            confirm = "Save",
            enabled = canSave,
            onConfirm = {
                val amount = FormValidation.parseAmount(amountText) ?: return@DialogButtons
                onSave(
                    RecurringEntity(
                        id = existing?.id ?: 0L,
                        name = InputGuard.clean(name),
                        amount = amount,
                        category = category,
                        frequency = FREQUENCIES[frequencyIndex].name,
                        startDate = FinancialCalculations.startOfDay(dueMs),
                        active = active,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis()
                    )
                )
            },
            onDismiss = onDismiss
        )
        if (onDelete != null) {
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete", color = Negative, style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (pickDate) {
        PwDatePicker(
            initialMs = dueMs,
            future = true,
            onPick = { dueMs = FinancialCalculations.startOfDay(DateConversions.utcDayToLocal(it, keepTimeOfMs = 0L)) },
            onDismiss = { pickDate = false }
        )
    }
    if (pickCategory) {
        CategoryPickerDialog(
            title = "Category",
            categories = expenseCategories,
            selected = category,
            onSelect = { category = it },
            onDismiss = { pickCategory = false }
        )
    }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${existing?.name}?") },
            text = { Text("Its reminders stop. Past transactions are not touched.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = Negative) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = Ink) } }
        )
    }
}

/** 500.0 -> "500", 12.5 -> "12.50": what the amount field expects. */
private fun plain(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(java.util.Locale.US, "%.2f", value)
