package com.example.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CategoryInfo
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentSoft
import com.example.ui.util.LocalCategoryUsage
import com.example.ui.util.getCategoryIcon
import com.example.ui.util.orderByUsage

/**
 * Category picker shown as a grid of icon tiles (4 per row), most-used first. Replaces the
 * sideways-scrolling list: every option is visible and tap targets are large.
 */
@Composable
fun CategoryGrid(
    categories: List<CategoryInfo>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCreateNew: (() -> Unit)? = null,
    columns: Int = 4
) {
    val ordered = orderByUsage(categories, LocalCategoryUsage.current)
    val tiles: List<CategoryInfo?> = ordered + if (onCreateNew != null) listOf(null) else emptyList()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("category_selector"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tiles.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { category ->
                    if (category == null) {
                        NewTile(onClick = { onCreateNew?.invoke() }, modifier = Modifier.weight(1f))
                    } else {
                        CategoryTile(
                            category = category,
                            isSelected = selected.equals(category.name, ignoreCase = true),
                            onClick = { onSelect(category.name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // Keep tile widths equal on the last, partly filled row.
                repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: CategoryInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = Color(category.colorHex)
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .heightIn(min = 76.dp)
            .clip(shape)
            .background(if (isSelected) color.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(BorderStroke(if (isSelected) 2.dp else 0.dp, if (isSelected) color else Color.Transparent), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .testTag("category_option_${category.name}")
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (isSelected) 0.25f else 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getCategoryIcon(category.iconKey),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) color else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun NewTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AccentSoft)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag("new_category_chip"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Accent, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text("New", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Accent)
        }
    }
}

/** The grid in a dialog, for places without room for it inline (e.g. the SMS import preview). */
@Composable
fun CategoryPickerDialog(
    title: String,
    categories: List<CategoryInfo>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            CategoryGrid(
                categories = categories,
                selected = selected,
                onSelect = {
                    onSelect(it)
                    onDismiss()
                },
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
