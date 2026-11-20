package com.example.ui.backups

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.ShopPaths
import com.example.domain.model.BackupMetadata
import com.example.domain.model.BackupType
import com.example.ui.AppMainUiState
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.EmptyLedgerCard
import com.example.utils.ExportUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupManagementScreen(
    uiState: AppMainUiState,
    onBack: () -> Unit,
    onLoadHistory: () -> Unit,
    onBackupNow: (BackupType) -> Unit,
    onRestoreBackup: (String) -> Unit,
    onReadRawBackupJson: suspend (String) -> String?,
    onCollectAllZipFiles: suspend (String) -> Map<String, String>
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snap = uiState.snapshot ?: return
    val canBackupOrRestore = snap.currentUserRole.canRunBackupOrClose()
    var restoreTarget by remember { mutableStateOf<BackupMetadata?>(null) }

    LaunchedEffect(Unit) {
        onLoadHistory()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Automatic Daily Backup Schedule", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Runs daily at ${snap.backupSettings.preferredBackupTime} (${snap.shopConfig.timezoneId}) via background WorkManager even when app is closed.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Last Status: ${snap.backupSettings.lastBackupStatus}${snap.backupSettings.lastBackupAtIso?.let { " • ${it.take(16).replace('T', ' ')}" } ?: ""}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canBackupOrRestore) {
                        Button(
                            onClick = { onBackupNow(BackupType.MANUAL) },
                            modifier = Modifier.fillMaxWidth().testTag("backup_now_button")
                        ) {
                            Icon(Icons.Rounded.Backup, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Backup Now")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val files = onCollectAllZipFiles(ShopPaths.ROOT)
                                val zipFile = ExportUtils.writeZipExport(
                                    context,
                                    "SLR_Note_Bu_${snap.shopConfig.shopName.replace(" ", "_")}_Complete_DB.zip",
                                    files
                                )
                                ExportUtils.shareFile(context, zipFile, "application/zip", "Export Complete Shop Database (ZIP)")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("export_complete_zip_button")
                    ) {
                        Icon(Icons.Rounded.FolderZip, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export Complete Database (ZIP)")
                    }
                }
            }

            item {
                Text("Backup History (${uiState.backupHistory.size})", style = MaterialTheme.typography.titleMedium)
            }

            if (uiState.backupHistory.isEmpty()) {
                item {
                    EmptyLedgerCard(
                        title = "No backups found yet",
                        subtitle = "Create a Manual Backup now or wait for the automatic daily backup at ${snap.backupSettings.preferredBackupTime}."
                    )
                }
            } else {
                items(uiState.backupHistory, key = { it.backupId }) { bkp ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(bkp.backupType.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                Text(bkp.createdAtIso.take(16).replace('T', ' '), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(bkp.filePath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "Customers: ${bkp.customerCount} • Active Sales: ${bkp.activeSalesCount} • Ba Tx: ${bkp.baTransactionsCount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text("SHA-256: ${bkp.checksumSha256.take(16)}...", style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val raw = onReadRawBackupJson(bkp.filePath) ?: return@launch
                                            val f = ExportUtils.writeTextExport(context, "${bkp.backupId}.json", raw)
                                            ExportUtils.shareFile(context, f, "application/json", "Download Backup ${bkp.backupId}")
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Download")
                                }
                                if (canBackupOrRestore) {
                                    Button(
                                        onClick = { restoreTarget = bkp },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Restore")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (restoreTarget != null) {
        val b = restoreTarget!!
        ConfirmActionDialog(
            title = "Restore Backup ${b.backupId}?",
            message = "Before restoring, SLR Note Bu will automatically create a Pre-Restore safety backup of your current database, validate the backup checksum and shop ID, and then restore data while preserving an audit record.",
            confirmLabel = "Create Safety Backup & Restore",
            onConfirm = {
                restoreTarget = null
                onRestoreBackup(b.filePath)
            },
            onDismiss = { restoreTarget = null }
        )
    }
}
