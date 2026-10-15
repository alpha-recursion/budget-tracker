package com.example.ui.screens

import com.example.domain.InputGuard
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.TransactionType
import com.example.domain.sms.SmsBank
import com.example.domain.sms.SmsTemplate
import com.example.domain.sms.SmsTemplateBuilder
import com.example.domain.sms.TemplateBuildResult
import com.example.ui.components.PwPrimaryButton
import com.example.ui.theme.Ink
import com.example.ui.theme.Negative
import com.example.ui.theme.PositiveText
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentSoft
import com.example.ui.util.Formatters

/**
 * Teach the app a bank SMS format from one example. Detected parts are pre-selected; the owner
 * confirms or corrects them and the result is previewed live before saving.
 */
@Composable
fun TeachFormatDialog(
    initialText: String,
    onSave: (SmsTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    val suggestions = remember(text) { if (text.isBlank()) null else SmsTemplateBuilder.suggest(text) }

    // Choices are indexes into the detected lists (-1 = none) and reset when the text changes.
    var amountIndex by rememberSaveable(text) { mutableIntStateOf(0) }
    var dateIndex by rememberSaveable(text) { mutableIntStateOf(0) }
    var refIndex by rememberSaveable(text) { mutableIntStateOf(0) }
    var type by rememberSaveable(text) { mutableStateOf(suggestions?.type ?: TransactionType.EXPENSE) }
    var bank by rememberSaveable(text) { mutableStateOf(suggestions?.bank ?: SmsBank.UNKNOWN) }
    var payeeText by rememberSaveable(text) { mutableStateOf(suggestions?.payee?.value.orEmpty()) }
    LaunchedEffect(suggestions) {
        if (suggestions != null) {
            if (dateIndex >= suggestions.dates.size) dateIndex = -1
            if (refIndex >= suggestions.refs.size) refIndex = -1
        }
    }

    val result: TemplateBuildResult? = remember(suggestions, amountIndex, dateIndex, refIndex, type, bank, payeeText) {
        val s = suggestions ?: return@remember null
        val amount = s.amounts.getOrNull(amountIndex)
            ?: return@remember TemplateBuildResult.Failure("No amount found. The SMS needs Rs, INR or ₹ before the amount.")
        val date = s.dates.getOrNull(dateIndex)
        val ref = s.refs.getOrNull(refIndex)
        val payee = if (payeeText.isBlank()) {
            null
        } else {
            SmsTemplateBuilder.locate(s.text, payeeText, listOfNotNull(amount, date?.span, ref))
                ?: return@remember TemplateBuildResult.Failure("\"${payeeText.trim()}\" doesn't appear in the SMS. Type it exactly as written, or leave it empty.")
        }
        SmsTemplateBuilder.build(s.text, amount, payee, date, ref, type, bank)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .testTag("teach_format_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Teach a message format", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    "Paste one bank SMS. Check the parts below, then save. Similar messages will be recognised in future scans.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = InputGuard.sms(it) },
                    label = { Text("Example SMS") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("teach_sms_input")
                )

                if (suggestions != null) {
                    ChoiceRow(
                        label = "Amount",
                        options = suggestions.amounts.map { "₹" + it.value },
                        selected = amountIndex,
                        allowNone = false,
                        onSelect = { amountIndex = it }
                    )
                    ChoiceRow(
                        label = "Money",
                        options = listOf("Paid (expense)", "Received (income)"),
                        selected = if (type == TransactionType.EXPENSE) 0 else 1,
                        allowNone = false,
                        onSelect = { type = if (it == 0) TransactionType.EXPENSE else TransactionType.INCOME }
                    )
                    OutlinedTextField(
                        value = payeeText,
                        onValueChange = { payeeText = InputGuard.payee(it) },
                        label = { Text(if (type == TransactionType.EXPENSE) "Paid to (as written)" else "Received from (as written)") },
                        placeholder = { Text("Optional") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("teach_payee_input")
                    )
                    ChoiceRow(
                        label = "Date",
                        options = suggestions.dates.map { it.span.value },
                        selected = dateIndex,
                        allowNone = true,
                        onSelect = { dateIndex = it }
                    )
                    ChoiceRow(
                        label = "UPI reference",
                        options = suggestions.refs.map { it.value },
                        selected = refIndex,
                        allowNone = true,
                        onSelect = { refIndex = it }
                    )
                    val banks = listOf(SmsBank.KOTAK, SmsBank.BOI, SmsBank.UNKNOWN)
                    ChoiceRow(
                        label = "Bank",
                        options = listOf("Kotak", "Bank of India", "Other"),
                        selected = banks.indexOf(bank),
                        allowNone = false,
                        onSelect = { bank = banks[it] }
                    )

                    when (result) {
                        is TemplateBuildResult.Success -> PreviewCard(result)
                        is TemplateBuildResult.Failure -> Text(
                            result.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("teach_error")
                        )
                        null -> Unit
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    PwPrimaryButton(
                        text = "Save format",
                        onClick = { (result as? TemplateBuildResult.Success)?.let { onSave(it.template) } },
                        enabled = result is TemplateBuildResult.Success,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_format_button")
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    options: List<String>,
    selected: Int,
    allowNone: Boolean,
    onSelect: (Int) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
        Spacer(modifier = Modifier.height(4.dp))
        if (options.isEmpty() && !allowNone) {
            Text("Not found in this SMS", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            return@Column
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            options.forEachIndexed { index, option ->
                FilterChip(
                    selected = selected == index,
                    onClick = { onSelect(index) },
                    label = { Text(option) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentSoft,
                        selectedLabelColor = Accent
                    )
                )
            }
            if (allowNone) {
                FilterChip(
                    selected = selected !in options.indices,
                    onClick = { onSelect(-1) },
                    label = { Text(if (options.isEmpty()) "None found" else "None") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentSoft,
                        selectedLabelColor = Accent
                    )
                )
            }
        }
    }
}

@Composable
private fun PreviewCard(result: TemplateBuildResult.Success) {
    val p = result.preview
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AccentSoft),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("teach_preview")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("✓ This SMS reads as:", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                Formatters.formatSignedCurrency(p.amount, p.type.name),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (p.type == TransactionType.INCOME) PositiveText else Ink
            )
            val details = listOfNotNull(
                p.counterparty,
                p.dayMs?.let { Formatters.formatDate(it) },
                p.upiRef?.let { "Ref $it" }
            )
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
