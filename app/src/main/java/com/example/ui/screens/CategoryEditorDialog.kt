package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.unit.dp
import com.example.data.model.TransactionType
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentSoft
import com.example.ui.util.CategoryColorChoices
import com.example.ui.util.CategoryIconChoices
import com.example.ui.util.getCategoryIcon

/**
 * Create a custom category. [onSave] returns an error message to show, or null on success.
 */
@Composable
fun CategoryEditorDialog(
    initialType: TransactionType,
    onSave: (name: String, type: TransactionType, iconKey: String, colorHex: Long) -> String?,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(initialType) }
    var iconKey by rememberSaveable { mutableStateOf(CategoryIconChoices.first()) }
    var colorHex by rememberSaveable { mutableStateOf(CategoryColorChoices.first()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("category_editor_dialog"),
        title = { Text("New category", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("Name") },
                    placeholder = { Text("e.g., Bike & Fuel") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_name_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(option.displayName()) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentSoft,
                                selectedLabelColor = Accent
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Icon", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                Spacer(modifier = Modifier.height(6.dp))
                // Plain rows instead of a lazy grid: the list is short and lives inside a dialog.
                CategoryIconChoices.chunked(5).forEach { rowKeys ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        rowKeys.forEach { key ->
                            val selected = key == iconKey
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 3.dp)
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) Color(colorHex).copy(alpha = 0.18f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        BorderStroke(2.dp, if (selected) Color(colorHex) else Color.Transparent),
                                        CircleShape
                                    )
                                    .clickable { iconKey = key },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getCategoryIcon(key),
                                    contentDescription = key,
                                    tint = if (selected) Color(colorHex) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Colour", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                Spacer(modifier = Modifier.height(6.dp))
                CategoryColorChoices.chunked(5).forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        rowColors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 3.dp)
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(color))
                                    .border(
                                        BorderStroke(
                                            3.dp,
                                            if (color == colorHex) MaterialTheme.colorScheme.onSurface else Color.Transparent
                                        ),
                                        CircleShape
                                    )
                                    .clickable { colorHex = color }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { error = onSave(name, type, iconKey, colorHex) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                modifier = Modifier.testTag("save_category_button")
            ) {
                Text("Create", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
