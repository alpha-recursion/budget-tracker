package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CategoryInfo
import com.example.domain.GuessSource
import com.example.domain.OtherFix
import com.example.ui.theme.Accent
import com.example.ui.util.Formatters

/**
 * "Sort out Other": payees whose payments are still "Other" but can now be categorised, each
 * with a suggested category the owner can change or untick before anything is updated.
 */
@Composable
fun OtherFixesDialog(
    fixes: List<OtherFix>,
    categories: List<CategoryInfo>,
    onApply: (List<Pair<OtherFix, String>>) -> Unit,
    onDismiss: () -> Unit
) {
    // Per payee (direction + name): the chosen category, and whether it was unticked.
    val chosen = remember(fixes) { mutableStateMapOf<String, String>().apply { fixes.forEach { put(it.key, it.suggestion.name) } } }
    val unticked = remember(fixes) { mutableStateMapOf<String, Boolean>() }
    val selected = fixes.filter { unticked[it.key] != true }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("other_fixes_dialog"),
        title = { Text("Sort out “Other”", fontWeight = FontWeight.Bold) },
        text = {
            if (fixes.isEmpty()) {
                Text(
                    "Nothing to sort out: no “Other” payment matches a keyword or something you've categorised before.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Suggested categories for payments still filed as Other. Untick or change any, then apply; each choice is remembered.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    fixes.forEach { fix ->
                        FixRow(
                            fix = fix,
                            checked = unticked[fix.key] != true,
                            category = chosen[fix.key] ?: fix.suggestion.name,
                            options = CategoryInfo.forType(categories, fix.type),
                            onToggle = { unticked[fix.key] = unticked[fix.key] != true },
                            onCategory = { chosen[fix.key] = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (fixes.isNotEmpty()) {
                Button(
                    onClick = { onApply(selected.map { it to (chosen[it.key] ?: it.suggestion.name) }) },
                    enabled = selected.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    modifier = Modifier.testTag("apply_other_fixes_button")
                ) { Text("Apply ${selected.size}", fontWeight = FontWeight.Bold) }
            } else {
                TextButton(onClick = onDismiss) { Text("OK", color = Accent) }
            }
        },
        dismissButton = {
            if (fixes.isNotEmpty()) TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun FixRow(
    fix: OtherFix,
    checked: Boolean,
    category: String,
    options: List<CategoryInfo>,
    onToggle: () -> Unit,
    onCategory: (String) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val why = when (fix.suggestion.source) {
        GuessSource.KEYWORD -> "matched “${fix.suggestion.matched}”"
        GuessSource.LEARNED -> "like “${fix.suggestion.matched}”"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("other_fix_${fix.title}")) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = Accent))
        Column(modifier = Modifier.weight(1f)) {
            Text(fix.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${fix.transactionIds.size} payment${if (fix.transactionIds.size == 1) "" else "s"} · " +
                    "${Formatters.formatCurrency(fix.total)} · $why",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            TextButton(onClick = { menuOpen = true }, enabled = checked) {
                Text(category, color = Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.name) },
                        onClick = {
                            onCategory(option.name)
                            menuOpen = false
                        }
                    )
                }
            }
        }
    }
}

/** Identifies a payee in this dialog: the same name can appear as paid and as received. */
private val OtherFix.key: String get() = "${type.name}:$title"
