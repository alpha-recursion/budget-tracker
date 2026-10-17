package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.data.model.CategoryInfo
import com.example.data.model.TransactionType
import com.example.domain.sms.ImportCandidate
import com.example.domain.sms.ImportGroup
import com.example.domain.sms.StatementSummary
import com.example.ui.SmsImportPhase
import com.example.ui.SmsImportState
import com.example.ui.components.CategoryPickerDialog
import com.example.ui.components.PwPrimaryButton
import com.example.domain.Lending
import com.example.domain.PersonBalance
import com.example.ui.LendingChoice
import com.example.ui.components.PwChip
import com.example.ui.components.PwSecondaryButton
import com.example.ui.components.Space
import com.example.ui.theme.Ink
import com.example.ui.theme.Negative
import com.example.ui.theme.PositiveText
import com.example.ui.theme.Hairline
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentSoft
import com.example.ui.theme.Warning
import com.example.ui.util.DateConversions
import com.example.ui.util.Formatters
import com.example.ui.util.getCategoryIcon
import java.util.Calendar

private const val DAY_MS = 86_400_000L

@Composable
fun SmsImportScreen(
    state: SmsImportState,
    categories: List<CategoryInfo>,
    hasPermission: () -> Boolean,
    onScan: (sinceMs: Long) -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleGroup: (ImportGroup) -> Unit,
    onSetCategory: (groupKey: String, category: String) -> Unit,
    onSetSelfTransfer: (groupKey: String, Boolean) -> Unit,
    onImport: () -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens "Teach this format" with the given SMS text; null hides the button. */
    onTeach: ((String) -> Unit)? = null,
    people: List<PersonBalance> = emptyList(),
    onSetLending: (groupKey: String, LendingChoice?) -> Unit = { _, _ -> },
    /** Imports a Google Pay statement PDF the owner picked; null hides the option. */
    onPickStatement: ((Uri) -> Unit)? = null
) {
    var granted by remember { mutableStateOf(hasPermission()) }
    // The user may grant access from system settings and come back.
    LifecycleResumeEffect(Unit) {
        granted = hasPermission()
        onPauseOrDispose { }
    }

    Box(modifier = modifier.fillMaxSize().testTag("sms_import_screen")) {
        when {
            // A statement needs no SMS access, so only choosing a scan period asks for it.
            state.phase == SmsImportPhase.CHOOSE_PERIOD && !granted -> PermissionStep(
                onGranted = { granted = true },
                error = state.error,
                onPickStatement = onPickStatement
            )
            state.phase == SmsImportPhase.CHOOSE_PERIOD -> PeriodStep(
                error = state.error,
                onScan = onScan,
                onPickStatement = onPickStatement
            )
            state.phase == SmsImportPhase.SCANNING -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Accent)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    if (state.fromStatement) "Reading the statement…" else "Reading your bank SMS…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> ReviewStep(
                state = state,
                categories = categories,
                onToggleItem = onToggleItem,
                onToggleGroup = onToggleGroup,
                onSetCategory = onSetCategory,
                onSetSelfTransfer = onSetSelfTransfer,
                onImport = onImport,
                onStartOver = onStartOver,
                onScan = onScan,
                onTeach = onTeach,
                people = people,
                onSetLending = onSetLending
            )
        }
    }
}

/** Import from a Google Pay statement PDF instead of (or as well as) bank SMS. */
@Composable
private fun StatementImportCard(onPick: (Uri) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onPick)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Hairline.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("statement_import_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Google Pay statement (PDF)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Text(
                "Download it in Google Pay (Profile → Transaction statement), then pick the file here. " +
                    "No SMS access needed; payments already imported from SMS are skipped.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { picker.launch(arrayOf("application/pdf")) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("pick_statement_button")
            ) { Text("Choose PDF", color = Accent) }
        }
    }
}

@Composable
private fun PermissionStep(onGranted: () -> Unit, error: String? = null, onPickStatement: ((Uri) -> Unit)? = null) {
    val context = LocalContext.current
    var wasDenied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) onGranted() else wasDenied = true
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.l),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.testTag("sms_permission_step")
    ) {
        item {
            Icon(Icons.Default.Sms, contentDescription = null, tint = Accent, modifier = Modifier.size(40.dp))
        }
        item {
            SectionHeader(
                title = "Import spending from your bank SMS",
                subtitle = "Pennywise needs permission to read SMS to find your Kotak and BOI transaction alerts."
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Only Kotak and Bank of India transaction alerts are used; other messages are ignored.",
                    "Everything stays on this phone. Nothing is uploaded.",
                    "You review every transaction before it is added."
                ).forEach { line ->
                    Text("•  $line", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            PwPrimaryButton(
                text = "Allow SMS access",
                onClick = { launcher.launch(Manifest.permission.READ_SMS) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("grant_sms_button")
            )
        }
        if (wasDenied) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Permission not granted", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "If Android didn't ask, or said \"Restricted setting\": open App info, tap ⋮ " +
                                "(top right) → \"Allow restricted settings\", then go to Permissions → SMS → Allow.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null)
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Open App info", color = Accent) }
                    }
                }
            }
        }
        if (error != null) {
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }
        if (onPickStatement != null) {
            item { StatementImportCard(onPick = onPickStatement) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodStep(error: String?, onScan: (Long) -> Unit, onPickStatement: ((Uri) -> Unit)? = null) {
    // Days to look back, or null when a custom start date is chosen.
    var days by rememberSaveable { mutableStateOf<Int?>(30) }
    var customStart by rememberSaveable { mutableStateOf<Long?>(null) }
    var showPicker by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.l),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.testTag("sms_period_step")
    ) {
        item {
            SectionHeader(
                title = "Scan past messages",
                subtitle = "Find Kotak and BOI transactions you haven't added yet. Already-imported messages are skipped."
            )
        }
        item {
            // Scrollable: four chips are wider than a small phone screen.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                listOf(7, 14, 30).forEach { option ->
                    FilterChip(
                        selected = days == option,
                        onClick = {
                            days = option
                            customStart = null
                        },
                        label = { Text("$option days") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentSoft,
                            selectedLabelColor = Accent
                        ),
                        modifier = Modifier.testTag("scan_period_$option")
                    )
                }
                FilterChip(
                    selected = days == null,
                    onClick = { showPicker = true },
                    label = {
                        Text(customStart?.let { "Since " + Formatters.formatShortDate(it) } ?: "Since date…")
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentSoft,
                        selectedLabelColor = Accent
                    )
                )
            }
        }
        if (error != null) {
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }
        item {
            PwPrimaryButton(
                text = "Scan messages",
                onClick = {
                    val now = System.currentTimeMillis()
                    onScan(customStart ?: (now - (days ?: 30) * DAY_MS))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("start_scan_button")
            )
        }
        if (onPickStatement != null) {
            item { Spacer(modifier = Modifier.height(Space.s)) }
            item { StatementImportCard(onPick = onPickStatement) }
        }
    }

    if (showPicker) {
        val todayUtc = remember { DateConversions.localToUtcDay(System.currentTimeMillis()) }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = DateConversions.localToUtcDay(
                customStart ?: (System.currentTimeMillis() - 30 * DAY_MS)
            ),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtc
            }
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { utcDay ->
                        // Start of the chosen day in local time.
                        customStart = DateConversions.utcDayToLocal(utcDay, keepTimeOfMs = localMidnight())
                        days = null
                    }
                    showPicker = false
                }) { Text("Select", color = Accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun localMidnight(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
private fun ReviewStep(
    state: SmsImportState,
    categories: List<CategoryInfo>,
    onToggleItem: (String) -> Unit,
    onToggleGroup: (ImportGroup) -> Unit,
    onSetCategory: (String, String) -> Unit,
    onSetSelfTransfer: (String, Boolean) -> Unit,
    onImport: () -> Unit,
    onStartOver: () -> Unit,
    onScan: (Long) -> Unit,
    onTeach: ((String) -> Unit)?,
    people: List<PersonBalance>,
    onSetLending: (String, LendingChoice?) -> Unit
) {
    val plan = state.plan ?: return
    val importing = state.phase == SmsImportPhase.IMPORTING

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .testTag("sms_review_list"),
            contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.l),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { ReviewSummary(state) }

            if (plan.candidateCount == 0) {
                item {
                    Text(
                        if (plan.statement != null) "Nothing new in this statement: everything in it is already in Pennywise."
                        else "No new Kotak or BOI transactions in this period.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.newestOlderAlertAt?.let { newest ->
                    item { OlderAlertHint(newestAt = newest, onScan = onScan) }
                }
            }

            item {
                val statement = plan.statement
                if (statement != null) StatementSummaryCard(statement) else ScanDetailsCard(plan = plan, onTeach = onTeach)
            }

            items(plan.groups, key = { it.key }) { group ->
                GroupCard(
                    group = group,
                    selected = state.selected,
                    category = state.categoryByGroup[group.key] ?: group.suggestedCategory,
                    isSelfTransfer = group.key in state.selfTransferGroups,
                    categories = categories,
                    enabled = !importing,
                    onToggleItem = onToggleItem,
                    onToggleGroup = { onToggleGroup(group) },
                    onSetCategory = { onSetCategory(group.key, it) },
                    onSetSelfTransfer = { onSetSelfTransfer(group.key, it) },
                    lending = state.lendingByGroup[group.key],
                    people = people,
                    onSetLending = { onSetLending(group.key, it) }
                )
            }
        }

        Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
            Column(modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.m)) {
                if (state.error != null) {
                    Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PwSecondaryButton(
                        text = if (state.fromStatement) "Start over" else "Scan again",
                        onClick = onStartOver,
                        enabled = !importing,
                        modifier = Modifier.weight(1f)
                    )
                    PwPrimaryButton(
                        text = "Import ${state.selectedCount}",
                        onClick = onImport,
                        enabled = state.selectedCount > 0,
                        loading = importing,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_selected_button")
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewSummary(state: SmsImportState) {
    val plan = state.plan ?: return
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AccentSoft),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Found ${plan.candidateCount} new transaction${if (plan.candidateCount == 1) "" else "s"}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            if (plan.alreadyImportedCount > 0) {
                Text(
                    "${plan.alreadyImportedCount} already imported earlier (skipped)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Ticked: ${Formatters.formatCurrency(state.selectedSpent)} spent · " +
                "${Formatters.formatCurrency(state.selectedReceived)} received" +
                if (state.selectedLending > 0) " · ${Formatters.formatCurrency(state.selectedLending)} lending" else "",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Pick a category once per payee; it's remembered for next time. Unticked items will appear again " +
                    if (state.fromStatement) "if you import this statement again." else "on the next scan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** What the statement contained, and whether it adds up to the totals printed on it. */
@Composable
private fun StatementSummaryCard(summary: StatementSummary) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Hairline.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("statement_summary_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(summary.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            summary.period?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${summary.rows} rows · ${Formatters.formatCurrency(summary.paidTotal)} paid · " +
                    "${Formatters.formatCurrency(summary.receivedTotal)} received" +
                    if (summary.selfTransfers > 0) " · ${summary.selfTransfers} self transfers" else "",
                style = MaterialTheme.typography.bodySmall
            )
            if (summary.totalsMatch) {
                Badge(text = "Matches the statement's totals", color = PositiveText)
            } else {
                Badge(text = "Doesn't match the statement's totals: check before importing", color = Warning)
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: ImportGroup,
    selected: Set<String>,
    category: String,
    isSelfTransfer: Boolean,
    categories: List<CategoryInfo>,
    enabled: Boolean,
    onToggleItem: (String) -> Unit,
    onToggleGroup: () -> Unit,
    onSetCategory: (String) -> Unit,
    onSetSelfTransfer: (Boolean) -> Unit,
    lending: LendingChoice?,
    people: List<PersonBalance>,
    onSetLending: (LendingChoice?) -> Unit
) {
    var askPerson by remember { mutableStateOf(false) }
    val selectedInGroup = group.items.count { it.dedupeKey in selected }
    val groupState = when (selectedInGroup) {
        0 -> ToggleableState.Off
        group.items.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val isIncome = group.type == TransactionType.INCOME

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Hairline.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sms_group_${group.key}")
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 12.dp)
            ) {
                TriStateCheckbox(
                    state = groupState,
                    onClick = onToggleGroup,
                    enabled = enabled,
                    colors = CheckboxDefaults.colors(checkedColor = Accent)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        group.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        (if (group.cancelsPayment) "Refunded" else if (isIncome) "Received" else "Paid") +
                            " · ${group.items.size} message" + if (group.items.size == 1) "" else "s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (group.nameVariants.isNotEmpty()) {
                        // Same vendor, name cut differently by the bank.
                        Text(
                            "Also as: " + group.nameVariants.joinToString(", "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (group.cancelsPayment) {
                // The bank gave a recorded payment back: nothing to categorise.
                Text(
                    "The bank reversed a payment you already imported. Importing this removes " +
                        "that payment; nothing is added as income.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            } else {
                // What this money was: normal spending/income (with a category), lending, or a
                // transfer between the owner's own accounts.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    PwChip(
                        text = if (isIncome) "Income" else "Spending",
                        selected = !isSelfTransfer && lending == null,
                        onClick = {
                            if (!enabled) return@PwChip
                            onSetLending(null)
                            onSetSelfTransfer(false)
                        }
                    )
                    PwChip(
                        text = "Lending",
                        selected = lending != null,
                        onClick = { if (enabled) askPerson = true },
                        modifier = Modifier.testTag("sms_lending_chip_${group.key}")
                    )
                    PwChip(
                        text = "Self transfer",
                        selected = isSelfTransfer,
                        onClick = { if (enabled) onSetSelfTransfer(true) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    lending != null -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Text(
                            "${lending.kind.label} · ${Lending.displayName(lending.person)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { askPerson = true }, enabled = enabled) {
                            Text("Change", color = Accent, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    isSelfTransfer -> Text(
                        "Not counted as spending or income.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    else -> CategoryButton(
                        title = group.title,
                        current = category,
                        options = CategoryInfo.forType(categories, group.type),
                        enabled = enabled,
                        onSelect = onSetCategory,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .fillMaxWidth()
                    )
                }
                if (askPerson) {
                    LendingPersonDialog(
                        type = group.type,
                        payee = group.title,
                        people = people,
                        amount = group.items.filter { it.dedupeKey in selected }.sumOf { it.parsed.amount }.takeIf { it > 0 },
                        initialPerson = lending?.person,
                        initialKind = lending?.kind,
                        onConfirm = { person, kind ->
                            onSetLending(LendingChoice(person, kind))
                            askPerson = false
                        },
                        onDismiss = { askPerson = false }
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp),
                color = Hairline.copy(alpha = 0.5f)
            )
            group.items.forEach { item ->
                CandidateRow(
                    item = item,
                    checked = item.dedupeKey in selected,
                    enabled = enabled,
                    onToggle = { onToggleItem(item.dedupeKey) }
                )
            }
        }
    }
}

@Composable
private fun CandidateRow(item: ImportCandidate, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val isIncome = item.parsed.type == TransactionType.INCOME
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(end = 12.dp)
            .testTag("sms_item_${item.dedupeKey}")
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = Accent)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(Formatters.formatDate(item.dateMs), style = MaterialTheme.typography.bodyMedium)
            if (item.isUnmatchedReversal) {
                Badge(text = "Reversal: original payment not found", color = Warning)
            } else if (item.needsReview) {
                Badge(text = "Check: unverified format", color = Warning)
            }
            if (item.possibleDuplicate) {
                Badge(text = "Maybe already added by hand", color = Warning)
            }
        }
        Text(
            Formatters.formatSignedCurrency(item.parsed.amount, item.parsed.type.name),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = if (isIncome) PositiveText else Ink,
            maxLines = 1
        )
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = color
    )
}

@Composable
private fun CategoryButton(
    title: String,
    current: String,
    options: List<CategoryInfo>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val info = CategoryInfo.resolve(current, options)
    OutlinedButton(
        onClick = { showPicker = true },
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = modifier
    ) {
        Icon(
            getCategoryIcon(info.iconKey),
            contentDescription = null,
            tint = if (enabled) Color(info.colorHex) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            current,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose category")
    }
    if (showPicker) {
        CategoryPickerDialog(
            title = "Category for $title",
            categories = options,
            selected = current,
            onSelect = onSelect,
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun OlderAlertHint(newestAt: Long, onScan: (Long) -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("older_alert_hint")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Your newest Kotak/BOI transaction SMS is from ${Formatters.formatDate(newestAt)}, before this period.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onScan(startOfDay(newestAt)) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { Text("Scan since ${Formatters.formatShortDate(newestAt)}") }
        }
    }
}

@Composable
private fun ScanDetailsCard(plan: com.example.domain.sms.ImportPlan, onTeach: ((String) -> Unit)?) {
    val stats = plan.stats
    // Open automatically when something was not understood, since that is what needs attention.
    var expanded by rememberSaveable { mutableStateOf(stats.unrecognised.isNotEmpty() && plan.candidateCount == 0) }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Hairline.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scan_details_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Text(
                    "Scan details",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                Text(if (expanded) "Hide" else "Show", color = Accent, style = MaterialTheme.typography.labelLarge)
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                StatLine("SMS read in this period", stats.messagesRead)
                StatLine("From bank-style senders", stats.fromBankSenders)
                StatLine("Kotak / BOI transactions recognised", stats.recognised)
                StatLine("Already imported earlier", plan.alreadyImportedCount)
                if (stats.unrecognised.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Looks like a transaction but wasn't understood:",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    stats.unrecognised.forEach { sms ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "${sms.sender} · ${Formatters.formatDate(sms.receivedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(sms.body, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        if (onTeach != null) {
                            TextButton(onClick = { onTeach(sms.body) }) {
                                Text("Teach this format", color = Accent, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: Int) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value.toString(), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
    }
}

private fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
