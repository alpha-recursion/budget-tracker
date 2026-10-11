package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.TransactionType
import com.example.domain.DebtKind
import com.example.domain.FinancialCalculations
import com.example.domain.FormValidation
import com.example.domain.Lending
import com.example.domain.PersonBalance
import com.example.ui.components.PwChip
import com.example.ui.components.PwPrimaryButton
import com.example.ui.components.PwSegmented
import com.example.ui.components.PwTextField
import com.example.ui.components.Space
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.SurfaceWhite
import com.example.ui.util.DateConversions
import com.example.ui.util.Formatters
import java.util.Calendar

private const val DAY_MS = 86_400_000L

/** What each kind is called on buttons and dialog titles. */
internal fun DebtKind.actionTitle(): String = when (this) {
    DebtKind.LENT -> "Lent money"
    DebtKind.GOT_BACK -> "Got money back"
    DebtKind.BORROWED -> "Borrowed money"
    DebtKind.PAID_BACK -> "Paid back"
}

/** Short choice label when an SMS or transaction is marked as lending. */
private fun DebtKind.choiceLabel(): String = when (this) {
    DebtKind.LENT -> "I lent it"
    DebtKind.PAID_BACK -> "I paid back"
    DebtKind.GOT_BACK -> "Got it back"
    DebtKind.BORROWED -> "I borrowed"
}

/** Add a lent / got back / borrowed / paid back entry. */
@Composable
fun DebtEntryDialog(
    kind: DebtKind,
    people: List<PersonBalance>,
    onSave: (person: String, amount: Double, date: Long, dueDate: Long?, note: String) -> Unit,
    onDismiss: () -> Unit,
    /** Opened from a person's page: the name is fixed. */
    fixedPerson: String? = null,
    /** Pre-filled amount, e.g. what is still outstanding for a repayment. */
    suggestedAmount: Double? = null
) {
    var person by rememberSaveable { mutableStateOf(fixedPerson.orEmpty()) }
    var amountText by rememberSaveable {
        mutableStateOf(suggestedAmount?.let { plainAmount(it) }.orEmpty())
    }
    var amountTouched by rememberSaveable { mutableStateOf(false) }
    var dateMs by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var dueMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }
    var pickDue by remember { mutableStateOf(false) }

    val amountError = FormValidation.validateAmount(amountText).errorOrNull
    val canSave = person.isNotBlank() && amountError == null

    LendingDialogFrame(title = kind.actionTitle(), onDismiss = onDismiss, tag = "debt_entry_dialog") {
        if (fixedPerson == null) {
            PersonField(value = person, onValueChange = { person = it }, people = people, onPick = { person = it.name })
        } else {
            Text(fixedPerson, style = MaterialTheme.typography.titleMedium, color = Ink)
        }

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
            modifier = Modifier.testTag("debt_amount_input")
        )

        Text("Date", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
        DateChips(dateMs = dateMs, onChange = { dateMs = it }, onPick = { pickDate = true })

        if (kind.canHaveDueDate) {
            Text(
                if (kind == DebtKind.LENT) "Pay back by" else "I'll pay back by",
                style = MaterialTheme.typography.labelMedium,
                color = InkSecondary
            )
            DueChips(dueMs = dueMs, fromMs = dateMs, onChange = { dueMs = it }, onPick = { pickDue = true })
        }

        PwTextField(
            value = note,
            onValueChange = { note = it },
            label = "Note (optional)",
            placeholder = "e.g. trip, rent share",
            modifier = Modifier.testTag("debt_note_input")
        )

        DialogButtons(
            confirm = "Save",
            enabled = canSave,
            onConfirm = {
                val amount = FormValidation.parseAmount(amountText) ?: return@DialogButtons
                onSave(person, amount, dateMs, dueMs, note)
            },
            onDismiss = onDismiss
        )
    }

    if (pickDate) {
        PwDatePicker(
            initialMs = dateMs,
            future = false,
            onPick = { dateMs = DateConversions.utcDayToLocal(it, keepTimeOfMs = dateMs) },
            onDismiss = { pickDate = false }
        )
    }
    if (pickDue) {
        PwDatePicker(
            initialMs = dueMs ?: (System.currentTimeMillis() + 7 * DAY_MS),
            future = true,
            onPick = { dueMs = FinancialCalculations.startOfDay(DateConversions.utcDayToLocal(it, keepTimeOfMs = 0L)) },
            onDismiss = { pickDue = false }
        )
    }
}

/**
 * Marks an SMS payment or an existing transaction as lending: who it was, and which way the
 * money went. The person and direction are guessed from the payee and current balances.
 */
@Composable
fun LendingPersonDialog(
    type: TransactionType,
    payee: String,
    people: List<PersonBalance>,
    onConfirm: (person: String, kind: DebtKind) -> Unit,
    onDismiss: () -> Unit,
    amount: Double? = null,
    initialPerson: String? = null,
    initialKind: DebtKind? = null
) {
    val matched = remember(payee, people) { Lending.matchPerson(payee, people) }
    var person by rememberSaveable { mutableStateOf(initialPerson ?: matched?.name ?: Lending.displayName(payee)) }
    // Until the owner picks a direction, follow the selected person's balance.
    var chosenKind by rememberSaveable { mutableStateOf(initialKind) }
    val current = people.firstOrNull { it.key == Lending.personKey(person) }
    val kind = chosenKind ?: Lending.defaultKind(type, current?.balance ?: 0.0)
    val options = DebtKind.forType(type)

    val title = amount?.let { "${Formatters.formatCurrency(it)} " + if (type == TransactionType.EXPENSE) "paid" else "received" }
        ?: "Money lent or repaid"
    LendingDialogFrame(title = title, onDismiss = onDismiss, tag = "lending_person_dialog") {
        Text(
            "Kept out of your spending and income, and added to Money lent.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSecondary
        )
        PwSegmented(
            options = options.map { it.choiceLabel() },
            selectedIndex = options.indexOf(kind).coerceAtLeast(0),
            onSelect = { chosenKind = options[it] },
            modifier = Modifier.fillMaxWidth()
        )
        PersonField(value = person, onValueChange = { person = it }, people = people, onPick = { person = it.name })
        if (current != null && !current.isSettled) {
            Text(
                statusLine(current),
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary
            )
        }
        DialogButtons(
            confirm = "Done",
            enabled = person.isNotBlank(),
            onConfirm = { onConfirm(person, kind) },
            onDismiss = onDismiss
        )
    }
}

/** "Owes you ₹500" / "You owe ₹200" / "Settled up". */
internal fun statusLine(person: PersonBalance): String = when {
    person.theyOweMe -> "Owes you ${Formatters.formatCurrency(person.balance)}"
    person.iOweThem -> "You owe ${Formatters.formatCurrency(-person.balance)}"
    else -> "Settled up"
}

/** "Overdue since Oct 30, 2026" / "Due Oct 30, 2026", or null. */
internal fun dueLine(person: PersonBalance): String? = when {
    person.overdueSince != null -> "Overdue since ${Formatters.formatDate(person.overdueSince)}"
    person.nextDue != null -> "Due ${Formatters.formatDate(person.nextDue)}"
    else -> null
}

@Composable
private fun LendingDialogFrame(
    title: String,
    onDismiss: () -> Unit,
    tag: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = SurfaceWhite,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .testTag(tag)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(Space.xxl),
                verticalArrangement = Arrangement.spacedBy(Space.m)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)
                content()
            }
        }
    }
}

@Composable
private fun PersonField(
    value: String,
    onValueChange: (String) -> Unit,
    people: List<PersonBalance>,
    onPick: (PersonBalance) -> Unit
) {
    PwTextField(
        value = value,
        onValueChange = onValueChange,
        label = "Person",
        placeholder = "Name",
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.testTag("debt_person_input")
    )
    if (people.isNotEmpty()) {
        val currentKey = Lending.personKey(value)
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            people.take(12).forEach { p ->
                PwChip(text = p.name, selected = p.key == currentKey, onClick = { onPick(p) })
            }
        }
    }
}

@Composable
private fun DateChips(dateMs: Long, onChange: (Long) -> Unit, onPick: () -> Unit) {
    val now = System.currentTimeMillis()
    val today = sameDay(dateMs, now)
    val yesterday = sameDay(dateMs, now - DAY_MS)
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        PwChip("Today", today, { onChange(now) })
        PwChip("Yesterday", yesterday, { onChange(now - DAY_MS) })
        PwChip(if (today || yesterday) "Pick date" else Formatters.formatDate(dateMs), !today && !yesterday, onPick)
    }
}

@Composable
private fun DueChips(dueMs: Long?, fromMs: Long, onChange: (Long?) -> Unit, onPick: () -> Unit) {
    val week = FinancialCalculations.startOfDay(fromMs + 7 * DAY_MS)
    val month = FinancialCalculations.startOfDay(
        Calendar.getInstance().apply {
            timeInMillis = fromMs
            add(Calendar.MONTH, 1)
        }.timeInMillis
    )
    val custom = dueMs != null && dueMs != week && dueMs != month
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        PwChip("No date", dueMs == null, { onChange(null) })
        PwChip("In a week", dueMs == week, { onChange(week) })
        PwChip("In a month", dueMs == month, { onChange(month) })
        PwChip(if (custom) Formatters.formatDate(dueMs!!) else "Pick date", custom, onPick)
    }
}

@Composable
private fun DialogButtons(confirm: String, enabled: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Spacer(modifier = Modifier.height(Space.xs))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.m), modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(52.dp)) {
            Text("Cancel", color = Ink, style = MaterialTheme.typography.labelLarge)
        }
        PwPrimaryButton(
            text = confirm,
            onClick = onConfirm,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .testTag("debt_confirm_button")
        )
    }
}

/** Material date picker in local days; [future] allows only today onwards, else only up to today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PwDatePicker(initialMs: Long, future: Boolean, onPick: (utcDayMs: Long) -> Unit, onDismiss: () -> Unit) {
    val todayUtc = remember { DateConversions.localToUtcDay(System.currentTimeMillis()) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = DateConversions.localToUtcDay(initialMs),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                if (future) utcTimeMillis >= todayUtc else utcTimeMillis <= todayUtc
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let(onPick)
                onDismiss()
            }) {
                Text("Select", color = Accent, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Ink) }
        }
    ) {
        DatePicker(state = state)
    }
}

private fun sameDay(a: Long, b: Long): Boolean =
    FinancialCalculations.startOfDay(a) == FinancialCalculations.startOfDay(b)

/** 500.0 -> "500", 12.5 -> "12.50": what the amount field expects. */
private fun plainAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(java.util.Locale.US, "%.2f", value)
