package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.backup.BackupData
import com.example.data.model.CategoryInfo
import com.example.data.model.TransactionType
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.VioletOutlineLight
import com.example.ui.theme.VioletPrimary
import com.example.ui.util.getCategoryIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    categories: List<CategoryInfo>,
    onCreateCategory: (name: String, type: TransactionType, iconKey: String, colorHex: Long) -> String?,
    onDeleteCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
    pendingRestore: BackupData? = null,
    onExportBackup: (Uri) -> Unit = {},
    onPickBackup: (Uri) -> Unit = {},
    onConfirmRestore: () -> Unit = {},
    onCancelRestore: () -> Unit = {},
    onEraseAll: () -> Unit = {},
    onOpenSmsImport: () -> Unit = {}
) {
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmErase by rememberSaveable { mutableStateOf(false) }
    val customCategories = categories.filter { it.isCustom }

    // System file pickers: the user chooses where the backup goes (Drive, Downloads, ...).
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(onExportBackup) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onPickBackup) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                title = "Your categories",
                subtitle = "Add categories like \"Bike & Fuel\" or \"Rent to Parents\""
            )
        }

        if (customCategories.isEmpty()) {
            item {
                Text(
                    text = "No custom categories yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(customCategories, key = { it.name }) { category ->
                CustomCategoryRow(
                    category = category,
                    onDelete = { pendingDelete = category.name }
                )
            }
        }

        item {
            OutlinedButton(
                onClick = { showEditor = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_category_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = VioletPrimary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add category", color = VioletPrimary, fontWeight = FontWeight.SemiBold)
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader(
                title = "Import",
                subtitle = "Add Kotak & BOI transactions from your SMS"
            )
        }

        item {
            OutlinedButton(
                onClick = onOpenSmsImport,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_import_sms_button")
            ) {
                Icon(Icons.Default.Sms, contentDescription = null, tint = VioletPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import from SMS", color = VioletPrimary, fontWeight = FontWeight.SemiBold)
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader(
                title = "Your data",
                subtitle = "Everything stays on this phone. Back up regularly to keep it safe."
            )
        }

        item {
            Button(
                onClick = {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    exportLauncher.launch("pennywise-backup-$date.json")
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_backup_button")
            ) {
                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Back up to a file", fontWeight = FontWeight.SemiBold)
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    // Some file managers label .json files as plain text or binary.
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("import_backup_button")
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = VioletPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Restore from a backup", color = VioletPrimary, fontWeight = FontWeight.SemiBold)
            }
        }

        item {
            TextButton(
                onClick = { confirmErase = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("erase_all_button")
            ) {
                Text("Erase all data", color = ExpenseRed)
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    if (showEditor) {
        CategoryEditorDialog(
            initialType = TransactionType.EXPENSE,
            onSave = { name, type, iconKey, colorHex ->
                val error = onCreateCategory(name, type, iconKey, colorHex)
                if (error == null) showEditor = false
                error
            },
            onDismiss = { showEditor = false }
        )
    }

    pendingRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = onCancelRestore,
            title = { Text("Restore this backup?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "It contains ${backup.transactions.size} transactions, ${backup.budgets.size} budgets " +
                        "and ${backup.categories.size} custom categories. Everything currently in the app " +
                        "will be replaced."
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmRestore,
                    colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                    modifier = Modifier.testTag("confirm_restore_button")
                ) { Text("Replace & restore", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = onCancelRestore) { Text("Cancel") } }
        )
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Erase all data?", fontWeight = FontWeight.Bold) },
            text = {
                Text("All transactions, budgets and custom categories will be deleted. Back up first if you might need them.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEraseAll()
                        confirmErase = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                    modifier = Modifier.testTag("confirm_erase_button")
                ) { Text("Erase everything", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Cancel") } }
        )
    }

    pendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"$name\"?", fontWeight = FontWeight.Bold) },
            text = { Text("Its transactions will move to \"Other\" and its budget will be removed.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCategory(name)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) { Text("Delete", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
internal fun SectionHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CustomCategoryRow(category: CategoryInfo, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, VioletOutlineLight.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(category.colorHex).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getCategoryIcon(category.iconKey),
                    contentDescription = null,
                    tint = Color(category.colorHex),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = category.type.displayName(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete ${category.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
