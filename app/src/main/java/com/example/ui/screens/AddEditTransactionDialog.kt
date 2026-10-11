package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.TransactionEntity
import com.example.data.model.CategoryInfo
import com.example.data.model.TransactionType
import com.example.ui.components.CategoryGrid
import com.example.ui.components.PwCard
import com.example.ui.components.PwChip
import com.example.ui.components.PwHeaderIconButton
import com.example.ui.components.PwPrimaryButton
import com.example.ui.components.PwSegmented
import com.example.ui.components.PwTextField
import com.example.ui.components.Space
import com.example.ui.theme.Accent
import com.example.ui.theme.Canvas
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.InkTertiary
import com.example.ui.theme.PositiveText
import com.example.ui.util.DateConversions
import com.example.ui.util.Formatters
import java.util.Calendar

/**
 * Full-screen Add / Edit transaction page: big amount at the top, fields in one card, and Save
 * fixed at the bottom above the keyboard. Must be placed above the app's Scaffold so the header
 * and tab bar do not draw over it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTransactionDialog(
    isOpen: Boolean,
    editingTransaction: TransactionEntity?,
    type: TransactionType,
    amount: String,
    title: String,
    selectedCategory: String,
    dateMs: Long,
    note: String,
    amountError: String?,
    titleError: String?,
    categoryError: String?,
    isSaving: Boolean = false,
    categories: List<CategoryInfo> = CategoryInfo.builtIns,
    onCreateCategory: (name: String, type: TransactionType, iconKey: String, colorHex: Long) -> String? = { _, _, _, _ -> null },
    onTypeChange: (TransactionType) -> Unit,
    onAmountChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onDateChange: (Long) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    /** Editing only: this payment was really money lent or repaid. */
    onMoveToLending: (() -> Unit)? = null
) {
    if (!isOpen) return

    val isEditing = editingTransaction != null
    val availableCategories = CategoryInfo.forType(categories, type)
    var showDatePicker by remember { mutableStateOf(false) }
    var showCategoryEditor by remember { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }

    BackHandler(onBack = onDismiss)
    // New entry: put the cursor in the amount straight away.
    LaunchedEffect(Unit) { if (!isEditing) amountFocus.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
            .statusBarsPadding()
            .testTag("add_edit_dialog")
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.screen, vertical = Space.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PwHeaderIconButton(Icons.Default.Close, "Close", onDismiss, Modifier.testTag("close_dialog_button"))
            Text(
                if (isEditing) "Edit transaction" else "New transaction",
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            if (onDelete != null) {
                PwHeaderIconButton(Icons.Outlined.DeleteOutline, "Delete transaction", onDelete, Modifier.testTag("delete_transaction_button"))
            } else {
                Spacer(modifier = Modifier.size(44.dp))
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen)
        ) {
            // Amount: the focus of the page.
            Spacer(modifier = Modifier.height(Space.l))
            Text("Amount", style = MaterialTheme.typography.labelMedium, color = InkSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("₹", style = MaterialTheme.typography.displaySmall, color = InkSecondary)
                Spacer(modifier = Modifier.width(Space.xs))
                BasicTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.displayLarge.copy(color = Ink),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .width(IntrinsicSize.Min)
                        .widthIn(min = 40.dp)
                        .focusRequester(amountFocus)
                        .testTag("amount_input"),
                    decorationBox = { inner ->
                        Box {
                            if (amount.isEmpty()) {
                                Text("0", style = MaterialTheme.typography.displayLarge, color = InkTertiary)
                            }
                            inner()
                        }
                    }
                )
            }
            if (amountError != null) {
                Text(
                    amountError,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag("amount_error_text")
                )
            }
            Spacer(modifier = Modifier.height(Space.xl))

            PwSegmented(
                options = listOf("Expense", "Income"),
                selectedIndex = if (type == TransactionType.EXPENSE) 0 else 1,
                onSelect = { onTypeChange(if (it == 0) TransactionType.EXPENSE else TransactionType.INCOME) },
                selectedColors = listOf(Ink, PositiveText),
                modifier = Modifier.testTag("type_toggle")
            )
            Spacer(modifier = Modifier.height(Space.l))

            PwCard {
                PwTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = if (type == TransactionType.EXPENSE) "Paid to" else "Received from",
                    placeholder = if (type == TransactionType.EXPENSE) "e.g. Zepto, Rent, Chai" else "e.g. Salary, Rahul",
                    errorText = titleError,
                    modifier = Modifier.testTag("title_input")
                )
                Spacer(modifier = Modifier.height(Space.xl))

                Text("Category", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                if (categoryError != null) {
                    Text(categoryError, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(Space.s))
                CategoryGrid(
                    categories = availableCategories,
                    selected = selectedCategory,
                    onSelect = onCategoryChange,
                    onCreateNew = { showCategoryEditor = true }
                )
                Spacer(modifier = Modifier.height(Space.xl))

                Text("Date", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                Spacer(modifier = Modifier.height(Space.s))
                val today = isSameDay(dateMs, System.currentTimeMillis())
                val yesterday = isSameDay(dateMs, System.currentTimeMillis() - 86_400_000L)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    PwChip("Today", today, { onDateChange(System.currentTimeMillis()) })
                    PwChip("Yesterday", yesterday, { onDateChange(System.currentTimeMillis() - 86_400_000L) })
                    PwChip(
                        text = if (today || yesterday) "Pick date" else Formatters.formatDate(dateMs),
                        selected = !today && !yesterday,
                        onClick = { showDatePicker = true },
                        leading = {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = if (!today && !yesterday) MaterialTheme.colorScheme.onPrimary else Ink,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("pick_custom_date_button")
                    )
                }
                Spacer(modifier = Modifier.height(Space.xl))

                PwTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    label = "Note (optional)",
                    placeholder = "Add details",
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.testTag("note_input")
                )
            }
            if (onMoveToLending != null) {
                Spacer(modifier = Modifier.height(Space.s))
                TextButton(
                    onClick = onMoveToLending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("move_to_lending_button")
                ) {
                    Icon(Icons.Default.Handshake, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(Space.s))
                    Text(
                        if (type == TransactionType.EXPENSE) "This was money I lent or paid back" else "This was money I got back or borrowed",
                        color = Accent,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            Spacer(modifier = Modifier.height(Space.l))
        }

        // Fixed bottom action, lifted above the keyboard.
        PwPrimaryButton(
            text = if (isEditing) "Update" else "Save",
            onClick = onSave,
            loading = isSaving,
            modifier = Modifier
                .fillMaxWidth()
                // Larger of keyboard and navigation bar (applying both would double the gap).
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = Space.screen, vertical = Space.m)
                .testTag("save_transaction_button")
        )
    }

    if (showCategoryEditor) {
        CategoryEditorDialog(
            initialType = type,
            onSave = { name, newType, iconKey, colorHex ->
                val error = onCreateCategory(name, newType, iconKey, colorHex)
                if (error == null) showCategoryEditor = false
                error
            },
            onDismiss = { showCategoryEditor = false }
        )
    }

    if (showDatePicker) {
        // DatePicker works in UTC-midnight millis; convert so the picker shows (and returns)
        // the same calendar day the user sees in the form, and block future dates.
        val todayUtc = remember { DateConversions.localToUtcDay(System.currentTimeMillis()) }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = DateConversions.localToUtcDay(dateMs),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtc
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            onDateChange(DateConversions.utcDayToLocal(it, keepTimeOfMs = dateMs))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Select", color = Accent, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = Ink) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun isSameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}
