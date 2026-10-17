package com.example.ui.screens

import com.example.domain.InputGuard
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.example.data.model.CategoryInfo
import com.example.data.model.TransactionType
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentSoft
import com.example.ui.util.CategoryColorChoices
import com.example.ui.util.CategoryIconChoices
import com.example.ui.util.getCategoryIcon

/**
 * Create a custom category, or with [editing] change the icon and colour of an existing one
 * (its name and type stay as they are). [onSave] returns an error message to show, or null on
 * success.
 */
@Composable
fun CategoryEditorDialog(
    initialType: TransactionType,
    onSave: (name: String, type: TransactionType, iconKey: String, colorHex: Long) -> String?,
    onDismiss: () -> Unit,
    editing: CategoryInfo? = null
) {
    var name by rememberSaveable { mutableStateOf(editing?.name ?: "") }
    var type by rememberSaveable { mutableStateOf(editing?.type ?: initialType) }
    var iconKey by rememberSaveable { mutableStateOf(editing?.iconKey ?: CategoryIconChoices.first()) }
    var colorHex by rememberSaveable { mutableStateOf(editing?.colorHex ?: CategoryColorChoices.first()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    // A built-in's original style, for "Reset"; null for custom categories.
    val builtInDefault = editing?.takeIf { !it.isCustom }?.let { e -> CategoryInfo.builtIns.firstOrNull { it.name == e.name } }
    // Keep the current icon / colour selectable even if it is not one of the offered choices.
    val iconChoices = (listOfNotNull(editing?.iconKey, builtInDefault?.iconKey) + CategoryIconChoices).distinct()
    val colorChoices = (CategoryColorChoices + listOfNotNull(editing?.colorHex, builtInDefault?.colorHex)).distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("category_editor_dialog"),
        title = { Text(if (editing != null) editing.name else "New category", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (editing == null) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = InputGuard.label(it, InputGuard.MAX_CATEGORY)
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
                } else {
                    Text(
                        "${editing.type.displayName()} category · choose its icon and colour",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Icon", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                Spacer(modifier = Modifier.height(6.dp))
                // Plain rows instead of a lazy grid: the list is short and lives inside a dialog.
                iconChoices.chunked(5).forEach { rowKeys ->
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
                colorChoices.chunked(6).forEach { rowColors ->
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
                Text(if (editing != null) "Save" else "Create", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (builtInDefault != null) {
                    TextButton(
                        onClick = {
                            iconKey = builtInDefault.iconKey
                            colorHex = builtInDefault.colorHex
                        },
                        modifier = Modifier.testTag("reset_category_style_button")
                    ) { Text("Reset") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/**
 * Every category with its icon and colour, expense first then income; tapping one opens the
 * editor for it. Stays open so several can be changed in a row.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPaletteDialog(
    categories: List<CategoryInfo>,
    onPick: (CategoryInfo) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("category_palette_dialog"),
        title = { Text("Colours & icons", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                TransactionType.entries.forEach { type ->
                    val ofType = categories.filter { it.type == type }
                    if (ofType.isEmpty()) return@forEach
                    Text(
                        type.displayName(),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ofType.forEach { category ->
                            val color = Color(category.colorHex)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(color.copy(alpha = 0.10f))
                                    .clickable { onPick(category) }
                                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                                    .testTag("palette_category_${category.name}")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(color.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(getCategoryIcon(category.iconKey), contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(category.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = Accent, fontWeight = FontWeight.Bold) }
        }
    )
}
