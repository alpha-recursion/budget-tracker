package com.example.ui.screens

import com.example.ui.util.orderByUsage
import com.example.ui.util.getCategoryIcon
import com.example.ui.util.LocalCategoryUsage
import com.example.ui.theme.SurfaceWhite
import com.example.ui.theme.PwMotion
import com.example.ui.theme.NegativeSoft
import com.example.ui.theme.Negative
import com.example.ui.theme.Navy
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.Hairline
import com.example.ui.components.softShadow
import com.example.ui.components.SheetShape
import com.example.ui.components.PwDivider
import com.example.ui.components.PillShape
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.border
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import com.example.domain.InputGuard
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    // The amount uses the app's own keypad, not the phone keyboard; new entries start on it.
    var keypadOpen by remember { mutableStateOf(!isEditing) }
    val focusManager = LocalFocusManager.current

    BackHandler(onBack = onDismiss)
    // Registered later, so Back closes the keypad before it closes the sheet.
    BackHandler(enabled = keypadOpen) { keypadOpen = false }

    // Sheet slides up over a navy scrim (v4); the host fades the whole thing in and out.
    val reduceMotion = LocalReduceMotion.current
    val slide = remember { Animatable(if (reduceMotion) 0f else 1f) }
    LaunchedEffect(Unit) { slide.animateTo(0f, tween(PwMotion.MEDIUM, easing = FastOutSlowInEasing)) }
    val isExpense = type == TransactionType.EXPENSE

    Box(modifier = Modifier.fillMaxSize().testTag("add_edit_dialog")) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Navy.copy(alpha = 0.45f * (1f - slide.value)))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 16.dp)
                .graphicsLayer { translationY = size.height * slide.value }
                .softShadow(SheetShape, 16.dp)
                .clip(SheetShape)
                .background(Canvas)
                // Swallow taps so they do not reach the scrim behind the sheet.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        ) {
            Box(
                modifier = Modifier
                    .padding(top = Space.s)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 40.dp, height = 5.dp)
                    .clip(PillShape)
                    .background(Color(0xFFC9CED6))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.screen, vertical = Space.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PwHeaderIconButton(Icons.Default.Close, "Close", onDismiss, Modifier.testTag("close_dialog_button"))
                Text(
                    if (isEditing) "Edit transaction" else "New transaction",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    color = Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                if (onDelete != null) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(NegativeSoft)
                            .clickable(onClick = onDelete)
                            .testTag("delete_transaction_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete transaction", tint = Negative, modifier = Modifier.size(20.dp))
                    }
                } else {
                    Spacer(modifier = Modifier.size(44.dp))
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.screen, vertical = Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.l)
            ) {
                PwSegmented(
                    options = listOf("Expense", "Income"),
                    selectedIndex = if (isExpense) 0 else 1,
                    onSelect = { onTypeChange(if (it == 0) TransactionType.EXPENSE else TransactionType.INCOME) },
                    selectedColors = listOf(Ink, PositiveText),
                    modifier = Modifier.testTag("type_toggle")
                )

                // Amount: the focus of the sheet.
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = Space.m, bottom = Space.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.xs)
                ) {
                    Text("Amount", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                // Hide the phone keyboard (payee / note) and show the keypad.
                                focusManager.clearFocus()
                                keypadOpen = true
                            }
                            .padding(horizontal = Space.s)
                            .semantics { contentDescription = "Amount ${amount.ifEmpty { "0" }} rupees, tap to edit" }
                            .testTag("amount_input")
                    ) {
                        Text("₹", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = if (amount.isEmpty()) InkTertiary else Ink)
                        Spacer(modifier = Modifier.width(Space.xs))
                        Text(
                            amount.ifEmpty { "0" },
                            style = MaterialTheme.typography.displayLarge,
                            color = if (amount.isEmpty()) InkTertiary else Ink,
                            maxLines = 1
                        )
                        if (keypadOpen) {
                            // Caret: shows where the keypad types.
                            Box(
                                modifier = Modifier
                                    .padding(start = 2.dp)
                                    .size(width = 3.dp, height = 40.dp)
                                    .clip(PillShape)
                                    .background(Accent)
                            )
                        }
                    }
                    // Quick amounts add to what is there: +₹100 twice is ₹200. Equal-width pills
                    // with whole rupees, so all four always fit on one line.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        listOf(100, 500, 1000, 2000).forEach { step ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(PillShape)
                                    .background(SurfaceWhite)
                                    .border(1.dp, Color(0xFFE4DCF3), PillShape)
                                    .clickable {
                                        val total = (amount.toDoubleOrNull() ?: 0.0) + step
                                        val text = if (total % 1.0 == 0.0) total.toLong().toString() else String.format(java.util.Locale.US, "%.2f", total)
                                        onAmountChange(InputGuard.amount(text))
                                    }
                                    .testTag("quick_amount_$step")
                            ) {
                                Text(
                                    "+" + Formatters.formatWholeRupees(step.toDouble()),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Accent,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    if (amountError != null) {
                        Text(
                            amountError,
                            style = MaterialTheme.typography.labelMedium,
                            color = Negative,
                            modifier = Modifier.testTag("amount_error_text")
                        )
                    }
                }

                // Payee, date and note in one white card.
                PwCard(contentPadding = PaddingValues(0.dp)) {
                    SheetFieldRow(Icons.Default.Storefront) {
                        Column(modifier = Modifier.padding(vertical = Space.s)) {
                            Text(if (isExpense) "Paid to" else "Received from", style = MaterialTheme.typography.labelSmall, color = InkSecondary)
                            BareField(
                                value = title,
                                onValueChange = { onTitleChange(InputGuard.payee(it)) },
                                placeholder = if (isExpense) "e.g. Zepto, Rent, Chai" else "e.g. Salary, Rahul",
                                capitalization = KeyboardCapitalization.Words,
                                modifier = Modifier
                                    .onFocusChanged { if (it.isFocused) keypadOpen = false }
                                    .testTag("title_input")
                            )
                        }
                    }
                    if (titleError != null) {
                        Text(
                            titleError,
                            style = MaterialTheme.typography.labelMedium,
                            color = Negative,
                            modifier = Modifier.padding(start = 46.dp, end = 14.dp, bottom = Space.s)
                        )
                    }
                    PwDivider(startInset = 0.dp)
                    SheetFieldRow(Icons.Default.CalendarToday) {
                        val today = isSameDay(dateMs, System.currentTimeMillis())
                        val yesterday = isSameDay(dateMs, System.currentTimeMillis() - 86_400_000L)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 10.dp)) {
                            PwChip("Today", today, { onDateChange(System.currentTimeMillis()) })
                            PwChip("Yesterday", yesterday, { onDateChange(System.currentTimeMillis() - 86_400_000L) })
                            PwChip(
                                text = if (today || yesterday) "Pick date" else Formatters.formatDate(dateMs),
                                selected = !today && !yesterday,
                                onClick = { showDatePicker = true },
                                modifier = Modifier.testTag("pick_custom_date_button")
                            )
                        }
                    }
                    PwDivider(startInset = 0.dp)
                    SheetFieldRow(Icons.AutoMirrored.Filled.Notes) {
                        BareField(
                            value = note,
                            onValueChange = { onNoteChange(InputGuard.note(it)) },
                            placeholder = "Add a note (optional)",
                            capitalization = KeyboardCapitalization.Sentences,
                            modifier = Modifier
                                .padding(vertical = 14.dp)
                                .onFocusChanged { if (it.isFocused) keypadOpen = false }
                                .testTag("note_input")
                        )
                    }
                }

                // Category: wrapping icon + name chips, most used first.
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Row(modifier = Modifier.padding(horizontal = Space.xs)) {
                        Text("Category", style = MaterialTheme.typography.labelLarge, color = Ink, modifier = Modifier.weight(1f))
                        if (categoryError != null) {
                            Text(categoryError, style = MaterialTheme.typography.labelMedium, color = Negative)
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                        verticalArrangement = Arrangement.spacedBy(Space.s)
                    ) {
                        orderByUsage(availableCategories, LocalCategoryUsage.current).forEach { cat ->
                            CategoryChip(cat, selected = cat.name == selectedCategory, onClick = { onCategoryChange(cat.name) })
                        }
                        NewCategoryChip(onClick = { showCategoryEditor = true })
                    }
                }

                if (onMoveToLending != null) {
                    TextButton(
                        onClick = onMoveToLending,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .testTag("move_to_lending_button")
                    ) {
                        Icon(Icons.Default.Handshake, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (isExpense) "This was money I lent or paid back" else "This was money I got back or borrowed",
                            color = Accent,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }

            // Fixed bottom action, lifted above the keyboard.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Canvas)
                    .drawBehind { drawLine(Hairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                    // Larger of keyboard and navigation bar (applying both would double the gap).
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .padding(horizontal = Space.screen, vertical = Space.m)
            ) {
                if (keypadOpen) {
                    AmountKeypad(
                        onKey = { key -> onAmountChange(InputGuard.amount(amount + key)) },
                        onBackspace = { onAmountChange(amount.dropLast(1)) },
                        onClear = { onAmountChange("") },
                        modifier = Modifier.padding(bottom = Space.m)
                    )
                }
                PwPrimaryButton(
                    text = when {
                        isEditing -> "Update"
                        isExpense -> "Save expense"
                        else -> "Save income"
                    },
                    onClick = onSave,
                    loading = isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_transaction_button")
                )
            }
        }
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

/** One row of the sheet's white card: a grey icon, then the field. */
@Composable
private fun SheetFieldRow(icon: ImageVector, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = InkSecondary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(Space.m))
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

/**
 * Number pad for the amount (v4): 1-9, ".", 0 and backspace; holding backspace clears. Keys go
 * through [InputGuard.amount] at the call site, so the same rules apply as when typing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AmountKeypad(
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val rows = listOf("123", "456", "789", ".0<")
    Column(modifier = modifier.testTag("amount_keypad"), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                row.forEach { key ->
                    val isBackspace = key == '<'
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceWhite)
                            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
                            .combinedClickable(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (isBackspace) onBackspace() else onKey(key)
                                },
                                onLongClick = if (isBackspace) {
                                    {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onClear()
                                    }
                                } else {
                                    null
                                },
                                onLongClickLabel = if (isBackspace) "Clear amount" else null
                            )
                            .testTag(if (isBackspace) "keypad_backspace" else "keypad_$key"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isBackspace) {
                            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete digit", tint = Ink, modifier = Modifier.size(22.dp))
                        } else {
                            Text(
                                key.toString(),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = Ink,
                                modifier = Modifier.semantics { if (key == '.') contentDescription = "Decimal point" }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Text input without a box, for rows inside a card. */
@Composable
private fun BareField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    capitalization: KeyboardCapitalization,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(capitalization = capitalization),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = InkTertiary)
                inner()
            }
        }
    )
}

@Composable
private fun CategoryChip(category: CategoryInfo, selected: Boolean, onClick: () -> Unit) {
    val color = Color(category.colorHex)
    Row(
        modifier = Modifier
            .height(38.dp)
            .clip(PillShape)
            .background(if (selected) Navy else SurfaceWhite)
            .border(1.dp, if (selected) Navy else Hairline, PillShape)
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = Space.m)
            .testTag("category_chip_${category.name}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (selected) SurfaceWhite.copy(alpha = 0.16f) else color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(getCategoryIcon(category.iconKey), contentDescription = null, tint = if (selected) SurfaceWhite else color, modifier = Modifier.size(15.dp))
        }
        Spacer(modifier = Modifier.width(Space.s))
        Text(
            category.name,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) SurfaceWhite else Ink,
            maxLines = 1
        )
    }
}

@Composable
private fun NewCategoryChip(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(38.dp)
            .clip(PillShape)
            .drawBehind {
                drawRoundRect(
                    color = Color(0xFFC2B3E3),
                    cornerRadius = CornerRadius(size.height / 2),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                )
            }
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 14.dp)
            .testTag("new_category_chip"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(Space.xs))
        Text("New", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = Accent)
    }
}
