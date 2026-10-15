package com.example.ui.screens

import com.example.domain.InputGuard
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.CategoryInfo
import com.example.ui.components.CategoryGrid
import com.example.ui.theme.Negative
import com.example.ui.theme.Accent
import com.example.ui.util.getCategoryIcon
import java.util.Locale

/**
 * Add or edit a monthly budget. When [editingCategory] is set the category is fixed and a
 * Remove button is shown; otherwise the user picks one of [availableCategories].
 * [onSave] returns an error message, or null on success.
 */
@Composable
fun BudgetEditorDialog(
    editingCategory: CategoryInfo?,
    currentLimit: Double?,
    availableCategories: List<CategoryInfo>,
    onSave: (category: String, rawLimit: String) -> String?,
    onRemove: (category: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by rememberSaveable {
        mutableStateOf(editingCategory?.name ?: availableCategories.firstOrNull()?.name.orEmpty())
    }
    var amount by rememberSaveable {
        mutableStateOf(currentLimit?.let { String.format(Locale.US, "%.0f", it) }.orEmpty())
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("budget_editor_dialog"),
        title = {
            Text(
                text = if (editingCategory != null) "Budget: ${editingCategory.name}" else "New monthly budget",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            // Scrollable: the category grid plus the amount field can exceed small screens.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (editingCategory == null) {
                    if (availableCategories.isEmpty()) {
                        Text(
                            "Every expense category already has a budget.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text("Category", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                        Spacer(modifier = Modifier.height(6.dp))
                        CategoryGrid(
                            categories = availableCategories,
                            selected = selected,
                            onSelect = { selected = it }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = InputGuard.amount(it)
                        error = null
                    },
                    label = { Text("Monthly limit") },
                    prefix = { Text("₹ ", fontWeight = FontWeight.Bold, color = Accent) },
                    placeholder = { Text("5000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("budget_limit_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    error = onSave(selected, amount)
                    if (error == null) onDismiss()
                },
                enabled = editingCategory != null || availableCategories.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                modifier = Modifier.testTag("save_budget_button")
            ) { Text("Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                if (editingCategory != null) {
                    TextButton(
                        onClick = {
                            onRemove(editingCategory.name)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("remove_budget_button")
                    ) { Text("Remove", color = Negative) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
