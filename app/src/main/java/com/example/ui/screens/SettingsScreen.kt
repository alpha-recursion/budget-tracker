package com.example.ui.screens

import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.ui.theme.SurfaceWhite
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.components.PwCard
import com.example.ui.components.PwDivider
import com.example.ui.components.PwIconBadge
import com.example.ui.components.PwListRow
import com.example.ui.components.Space
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.InkTertiary
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
import com.example.domain.sms.SmsBank
import com.example.domain.sms.SmsTemplate
import com.example.data.model.TransactionType
import com.example.ui.theme.Negative
import com.example.ui.theme.Hairline
import com.example.ui.theme.Accent
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
    onOpenSmsImport: () -> Unit = {},
    smsTemplates: List<SmsTemplate> = emptyList(),
    onTeachFormat: () -> Unit = {},
    onDeleteFormat: (Long) -> Unit = {},
    autoCaptureEnabled: Boolean = false,
    onAutoCaptureChange: (Boolean) -> Unit = {},
    onAutoCapturePermissionDenied: () -> Unit = {}
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
        contentPadding = PaddingValues(start = Space.screen, end = Space.screen, top = Space.s, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.s)
    ) {
        item { GroupLabel("Import") }
        item {
            PwCard(contentPadding = PaddingValues(vertical = Space.xs)) {
                SettingsRow(
                    icon = Icons.Default.Sms,
                    tint = Accent,
                    title = "Import from SMS",
                    subtitle = "Kotak & BOI payments from your messages",
                    onClick = onOpenSmsImport,
                    modifier = Modifier.testTag("settings_import_sms_button")
                )
                PwDivider()
                AutoCaptureRow(
                    enabled = autoCaptureEnabled,
                    onChange = onAutoCaptureChange,
                    onPermissionDenied = onAutoCapturePermissionDenied
                )
            }
        }

        item { GroupLabel("Your categories") }
        item {
            PwCard(contentPadding = PaddingValues(vertical = Space.xs)) {
                customCategories.forEach { category ->
                    PwListRow(
                        title = category.name,
                        subtitle = category.type.displayName(),
                        leading = { PwIconBadge(getCategoryIcon(category.iconKey), Color(category.colorHex)) }
                    ) {
                        IconButton(onClick = { pendingDelete = category.name }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete ${category.name}", tint = InkSecondary)
                        }
                    }
                    PwDivider()
                }
                SettingsRow(
                    icon = Icons.Default.Add,
                    tint = Accent,
                    title = "Add category",
                    subtitle = if (customCategories.isEmpty()) "e.g. Bike & Fuel, Rent to Parents" else null,
                    onClick = { showEditor = true },
                    showChevron = false,
                    modifier = Modifier.testTag("add_category_button")
                )
            }
        }

        item { GroupLabel("SMS formats you taught") }
        item {
            PwCard(contentPadding = PaddingValues(vertical = Space.xs)) {
                smsTemplates.forEach { template ->
                    val bank = when (template.bank) {
                        SmsBank.KOTAK -> "Kotak"
                        SmsBank.BOI -> "Bank of India"
                        SmsBank.UNKNOWN -> "Other bank"
                    }
                    PwListRow(
                        title = "$bank · ${if (template.type == TransactionType.INCOME) "Money received" else "Payment"}",
                        subtitle = template.sample,
                        leading = { PwIconBadge(Icons.Default.Sms, InkSecondary) }
                    ) {
                        IconButton(onClick = { onDeleteFormat(template.id) }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete format", tint = InkSecondary)
                        }
                    }
                    PwDivider()
                }
                SettingsRow(
                    icon = Icons.Default.Add,
                    tint = Accent,
                    title = "Teach a new format",
                    subtitle = if (smsTemplates.isEmpty()) "Kotak and BOI payment alerts already work" else null,
                    onClick = onTeachFormat,
                    showChevron = false,
                    modifier = Modifier.testTag("teach_format_button")
                )
            }
        }

        item { GroupLabel("Your data") }
        item {
            PwCard(contentPadding = PaddingValues(vertical = Space.xs)) {
                SettingsRow(
                    icon = Icons.Default.Upload,
                    tint = Accent,
                    title = "Back up to a file",
                    subtitle = "Save to Drive, Downloads, …",
                    onClick = {
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        exportLauncher.launch("pennywise-backup-$date.json")
                    },
                    modifier = Modifier.testTag("export_backup_button")
                )
                PwDivider()
                SettingsRow(
                    icon = Icons.Default.Download,
                    tint = Accent,
                    title = "Restore from a backup",
                    subtitle = "Replaces what's in the app",
                    onClick = {
                        // Some file managers label .json files as plain text or binary.
                        importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    },
                    modifier = Modifier.testTag("import_backup_button")
                )
                PwDivider()
                SettingsRow(
                    icon = Icons.Outlined.DeleteOutline,
                    tint = Negative,
                    title = "Erase all data",
                    subtitle = "Delete everything on this phone",
                    onClick = { confirmErase = true },
                    showChevron = false,
                    modifier = Modifier.testTag("erase_all_button")
                )
            }
        }
        item {
            Text(
                "Everything stays on this phone. Back up regularly to keep it safe.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary,
                modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.s)
            )
        }
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
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
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
                    colors = ButtonDefaults.buttonColors(containerColor = Negative),
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
                    colors = ButtonDefaults.buttonColors(containerColor = Negative)
                ) { Text("Delete", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

/** Small label above a settings group. */
/**
 * Live capture switch. Shown as on only while the SMS permission is still granted, since
 * without it no SMS reach the app; turning it on asks for SMS (and notification) access.
 */
@Composable
private fun AutoCaptureRow(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    onPermissionDenied: () -> Unit
) {
    val context = LocalContext.current
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    // Re-checked on resume: the permission can be revoked from system settings at any time.
    var canReceive by remember { mutableStateOf(granted(Manifest.permission.RECEIVE_SMS)) }
    LifecycleResumeEffect(Unit) {
        canReceive = granted(Manifest.permission.RECEIVE_SMS)
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        canReceive = result[Manifest.permission.RECEIVE_SMS] == true || granted(Manifest.permission.RECEIVE_SMS)
        if (canReceive) onChange(true) else onPermissionDenied()
    }
    val isOn = enabled && canReceive
    val turnOn = {
        val needed = buildList {
            if (!granted(Manifest.permission.RECEIVE_SMS)) add(Manifest.permission.RECEIVE_SMS)
            // Reviewing captured payments reads the inbox.
            if (!granted(Manifest.permission.READ_SMS)) add(Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !granted(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isEmpty()) onChange(true) else launcher.launch(needed.toTypedArray())
    }
    val toggle = { if (isOn) onChange(false) else turnOn() }
    PwListRow(
        title = "Auto-detect bank SMS",
        subtitle = if (isOn) "On · known payees are added for you" else "Notify me when a Kotak/BOI payment arrives",
        leading = { PwIconBadge(Icons.Default.NotificationsActive, Accent) },
        onClick = toggle,
        modifier = Modifier.testTag("settings_auto_capture_row")
    ) {
        Switch(
            checked = isOn,
            onCheckedChange = { toggle() },
            colors = SwitchDefaults.colors(
                checkedTrackColor = Accent,
                checkedThumbColor = SurfaceWhite,
                uncheckedTrackColor = Hairline,
                uncheckedThumbColor = SurfaceWhite,
                uncheckedBorderColor = Hairline
            ),
            modifier = Modifier.testTag("settings_auto_capture_switch")
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = InkSecondary,
        modifier = Modifier.padding(start = Space.xs, top = Space.m, bottom = Space.xs)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showChevron: Boolean = true
) {
    PwListRow(
        title = title,
        subtitle = subtitle,
        leading = { PwIconBadge(icon, tint) },
        onClick = onClick,
        modifier = modifier
    ) {
        if (showChevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = InkTertiary)
    }
}

/** Title + subtitle used at the top of full-screen steps (e.g. SMS import). */
@Composable
internal fun SectionHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = Ink)
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
        }
    }
}
