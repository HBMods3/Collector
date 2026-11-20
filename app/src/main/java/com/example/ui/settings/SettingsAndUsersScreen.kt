package com.example.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.UserRole
import com.example.ui.AppMainUiState
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.RoleBadge
import com.example.ui.theme.ThemeMode
import com.example.utils.MoneyUtils

@Composable
fun SettingsScreen(
    uiState: AppMainUiState,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSaveShopSettings: (
        shopName: String,
        address: String,
        phone: String,
        timezone: String,
        backupTime: String,
        autoDailyBackup: Boolean,
        autoMonthlyClosing: Boolean,
        allowDbEdit: Boolean
    ) -> Unit,
    onAddQuickItem: (name: String, pricePaise: Long) -> Unit,
    onOpenBackups: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenExplorer: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenAuditLogs: () -> Unit,
    onSwitchShop: () -> Unit,
    onCreateOrConnectShop: () -> Unit,
    onDisconnectShop: () -> Unit,
    onLogoutAccount: (String) -> Unit
) {
    val snap = uiState.snapshot ?: return
    val isOwner = snap.currentUserRole.canManageSecurity()
    val canWrite = snap.currentUserRole.canWriteTransactions()

    var shopName by remember(snap.shopConfig) { mutableStateOf(snap.shopConfig.shopName) }
    var address by remember(snap.shopConfig) { mutableStateOf(snap.shopConfig.addressOrVeng) }
    var phone by remember(snap.shopConfig) { mutableStateOf(snap.shopConfig.contactPhone) }
    var timezone by remember(snap.shopConfig) { mutableStateOf(snap.shopConfig.timezoneId) }
    var backupTime by remember(snap.backupSettings) { mutableStateOf(snap.backupSettings.preferredBackupTime) }
    var autoDailyBackup by remember(snap.backupSettings) { mutableStateOf(snap.backupSettings.autoDailyBackupEnabled) }
    var autoMonthlyClose by remember(snap.settings) { mutableStateOf(snap.settings.autoMonthlyClosingEnabled) }
    var allowDbEdit by remember(snap.settings) { mutableStateOf(snap.settings.allowDatabaseExplorerEdit) }

    var showQuickItemDialog by remember { mutableStateOf(false) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Settings & Administration", style = MaterialTheme.typography.headlineSmall)
        }

        // Tools Navigation Cards
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    SettingsNavRow("Backup & Restore Management", "Automatic daily backup, ZIP export & restore", Icons.Rounded.Backup, "nav_backups", onOpenBackups)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    SettingsNavRow("Monthly Archive Viewer", "View closed months, item summaries & closing history", Icons.Rounded.Archive, "nav_archive", onOpenArchive)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    SettingsNavRow("GitHub Database Explorer", "Inspect & edit JSON files, view commits & restore", Icons.Rounded.Code, "nav_explorer", onOpenExplorer)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    SettingsNavRow("Authorized Users & Roles", "${snap.users.users.size} user(s) • Your role: ${snap.currentUserRole.label}", Icons.Rounded.Group, "nav_users", onOpenUsers)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    SettingsNavRow("Audit Logs", "${snap.recentAuditLogs.size} events recorded this month", Icons.Rounded.History, "nav_audit_logs", onOpenAuditLogs)
                }
            }
        }

        // Theme Selector
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Appearance Theme", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = uiState.themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            }
        }

        // Quick Item Presets
        if (canWrite) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Quick-Select Shop Items (${snap.categories.quickItems.size})", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { showQuickItemDialog = true }) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add")
                            }
                        }
                        Text(
                            snap.categories.quickItems.joinToString(" • ") { "${it.name} (${MoneyUtils.formatInr(it.defaultPricePaise)})" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Owner-Only Shop & Automation Configuration
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Shop & Automation Settings", style = MaterialTheme.typography.titleMedium)
                        RoleBadge(snap.currentUserRole)
                    }
                    if (!isOwner) {
                        Text(
                            "Only the Database Owner (@${snap.shopConfig.ownerGithubLogin}) can modify shop security & automation settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(value = shopName, onValueChange = { shopName = it }, enabled = isOwner, label = { Text("Shop Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = address, onValueChange = { address = it }, enabled = isOwner, label = { Text("Address / Veng") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, enabled = isOwner, label = { Text("Shop Contact Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = timezone, onValueChange = { timezone = it }, enabled = isOwner, label = { Text("Timezone") }, singleLine = true, modifier = Modifier.weight(1.3f))
                        OutlinedTextField(value = backupTime, onValueChange = { backupTime = it }, enabled = isOwner, label = { Text("Backup (HH:mm)") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    SwitchRow("Automatic Daily Backup", autoDailyBackup, isOwner) { autoDailyBackup = it }
                    SwitchRow("Automatic Monthly Sales Closing", autoMonthlyClose, isOwner) { autoMonthlyClose = it }
                    SwitchRow("Allow JSON Database Editing", allowDbEdit, isOwner) { allowDbEdit = it }

                    if (isOwner) {
                        Button(
                            onClick = {
                                onSaveShopSettings(shopName, address, phone, timezone, backupTime, autoDailyBackup, autoMonthlyClose, allowDbEdit)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("save_shop_settings_button")
                        ) {
                            Text("Save Settings to GitHub")
                        }
                    }
                }
            }
        }

        // GitHub Connection & Multi-Shop Account Actions
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("GitHub & Multi-Shop Connection", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Account: @${uiState.activeAccount?.login ?: "—"} • Repo: ${snap.binding.repoOwner}/${snap.binding.repoName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onSwitchShop, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Switch Shop (${uiState.shops.size} connected)")
                    }
                    OutlinedButton(onClick = onCreateOrConnectShop, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add / Connect Another Shop or Account")
                    }
                    OutlinedButton(
                        onClick = { showDisconnectConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Disconnect This Repository")
                    }
                    if (uiState.activeAccount != null) {
                        OutlinedButton(
                            onClick = { showLogoutConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Logout @${uiState.activeAccount.login}")
                        }
                    }
                }
            }
        }
    }

    if (showQuickItemDialog) {
        var itemName by remember { mutableStateOf("") }
        var itemPrice by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showQuickItemDialog = false },
            title = { Text("Add Quick Item Preset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = itemName, onValueChange = { itemName = it }, label = { Text("Item Name *") }, singleLine = true)
                    OutlinedTextField(
                        value = itemPrice,
                        onValueChange = { itemPrice = it },
                        label = { Text("Default Price (₹) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val paise = MoneyUtils.parseRupeesToPaise(itemPrice) ?: return@Button
                    if (itemName.isBlank()) return@Button
                    showQuickItemDialog = false
                    onAddQuickItem(itemName, paise)
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showQuickItemDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDisconnectConfirm) {
        ConfirmActionDialog(
            title = "Disconnect Shop?",
            message = "This disconnects ${snap.shopConfig.shopName} from this phone. All data remains safely stored in ${snap.binding.repoOwner}/${snap.binding.repoName} on GitHub.",
            confirmLabel = "Disconnect",
            isDestructive = true,
            onConfirm = {
                showDisconnectConfirm = false
                onDisconnectShop()
            },
            onDismiss = { showDisconnectConfirm = false }
        )
    }

    if (showLogoutConfirm && uiState.activeAccount != null) {
        ConfirmActionDialog(
            title = "Logout @${uiState.activeAccount.login}?",
            message = "This removes the encrypted OAuth session and shop bindings for @${uiState.activeAccount.login} from this device.",
            confirmLabel = "Logout",
            isDestructive = true,
            onConfirm = {
                showLogoutConfirm = false
                onLogoutAccount(uiState.activeAccount.accountId)
            },
            onDismiss = { showLogoutConfirm = false }
        )
    }
}

@Composable
private fun SettingsNavRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    uiState: AppMainUiState,
    onBack: () -> Unit,
    onSaveUser: (githubLogin: String, displayName: String, role: UserRole, active: Boolean) -> Unit
) {
    BackHandler(onBack = onBack)
    val snap = uiState.snapshot ?: return
    val isOwner = snap.currentUserRole.canManageSecurity()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Management") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (isOwner) {
                        IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Rounded.Add, contentDescription = "Add User") }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Database Owner: @${snap.users.ownerLogin}. Only the Owner can assign roles, manage users, or transfer ownership.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(snap.users.users, key = { it.githubLogin }) { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.displayName, style = MaterialTheme.typography.titleSmall)
                            Text("@${user.githubLogin} • ${if (user.active) "Active" else "Disabled"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        RoleBadge(user.role)
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var login by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var role by remember { mutableStateOf(UserRole.SHOPKEEPER) }
        var active by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Authorize GitHub User") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = login, onValueChange = { login = it }, label = { Text("GitHub Username *") }, singleLine = true)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display Name") }, singleLine = true)
                    Text("Role", style = MaterialTheme.typography.labelLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        UserRole.entries.forEach { r ->
                            FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r.label) })
                        }
                    }
                    SwitchRow("Active Access", active, true) { active = it }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (login.isBlank()) return@Button
                    showAddDialog = false
                    onSaveUser(login, name, role, active)
                }) { Text("Save User") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogsScreen(
    uiState: AppMainUiState,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val snap = uiState.snapshot ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audit Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(snap.recentAuditLogs, key = { it.auditId }) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text("${entry.dateDisplay} ${entry.isoTimestamp.substringAfter('T').take(5)}", style = MaterialTheme.typography.labelSmall)
                        }
                        Text(entry.description, style = MaterialTheme.typography.bodySmall)
                        Text("User: @${entry.userId} • Ref: ${entry.recordId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!entry.previousValue.isNullOrBlank() || !entry.updatedValue.isNullOrBlank()) {
                            Text(
                                "Prev: ${entry.previousValue ?: "—"} → New: ${entry.updatedValue ?: "—"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
