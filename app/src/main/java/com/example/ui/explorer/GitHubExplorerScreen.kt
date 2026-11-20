package com.example.ui.explorer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.github.RemoteDirEntry
import com.example.data.repository.ShopPaths
import com.example.ui.AppMainUiState
import com.example.ui.ExplorerUiState
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.utils.ExportUtils
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubExplorerScreen(
    uiState: AppMainUiState,
    explorerState: ExplorerUiState,
    onBack: () -> Unit,
    onOpenDir: (String) -> Unit,
    onOpenFile: (path: String, ref: String?) -> Unit,
    onCloseFile: () -> Unit,
    onSaveFile: (path: String, content: String, sha: String?, message: String) -> Unit,
    onCreateFile: (path: String, initialContent: String) -> Unit,
    onDeleteFile: (RemoteDirEntry) -> Unit,
    onCollectFolderZip: suspend (String) -> Map<String, String>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snap = uiState.snapshot ?: return
    val canEdit = snap.currentUserRole.canEditDatabase() && snap.settings.allowDatabaseExplorerEdit

    BackHandler {
        if (explorerState.selectedFile != null) {
            onCloseFile()
        } else if (explorerState.currentPath != ShopPaths.ROOT && explorerState.currentPath.contains("/")) {
            onOpenDir(explorerState.currentPath.substringBeforeLast("/"))
        } else {
            onBack()
        }
    }

    LaunchedEffect(Unit) {
        onOpenDir(ShopPaths.ROOT)
    }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<RemoteDirEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (explorerState.selectedFile != null) explorerState.selectedFile.path.substringAfterLast("/")
                        else "GitHub DB Explorer"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (explorerState.selectedFile != null) onCloseFile()
                        else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (explorerState.selectedFile == null) {
                        IconButton(onClick = {
                            scope.launch {
                                val files = onCollectFolderZip(explorerState.currentPath)
                                val zip = ExportUtils.writeZipExport(
                                    context,
                                    "SLR_Folder_${explorerState.currentPath.replace("/", "_")}.zip",
                                    files
                                )
                                ExportUtils.shareFile(context, zip, "application/zip", "Download Folder ZIP")
                            }
                        }) {
                            Icon(Icons.Rounded.FolderZip, contentDescription = "Download Folder ZIP")
                        }
                        if (canEdit) {
                            IconButton(onClick = { showNewFileDialog = true }) {
                                Icon(Icons.Rounded.CreateNewFolder, contentDescription = "New File")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (explorerState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (explorerState.error != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(explorerState.error, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            val selectedFile = explorerState.selectedFile
            if (selectedFile != null) {
                JsonFileEditorView(
                    filePath = selectedFile.path,
                    initialContent = selectedFile.content,
                    fileSha = selectedFile.sha,
                    commits = explorerState.commits,
                    canEdit = canEdit,
                    onSave = { newContent, msg ->
                        onSaveFile(selectedFile.path, newContent, selectedFile.sha, msg)
                    },
                    onDownload = {
                        val f = ExportUtils.writeTextExport(context, selectedFile.path.substringAfterLast("/"), selectedFile.content)
                        ExportUtils.shareFile(context, f, "application/json", "Download ${selectedFile.path}")
                    },
                    onRestoreCommit = { commitSha ->
                        onOpenFile(selectedFile.path, commitSha)
                    }
                )
            } else {
                // Quick folder shortcuts
                val shortcuts = listOf(
                    "Root" to ShopPaths.ROOT,
                    "Ba Bu" to ShopPaths.BABU_DIR,
                    "Customers" to ShopPaths.CUSTOMERS_DIR,
                    "Daily Sales" to ShopPaths.DAILY_SALES_ACTIVE_DIR,
                    "Monthly Archive" to ShopPaths.MONTHLY_ARCHIVE_DIR,
                    "Yearly Reports" to ShopPaths.YEARLY_REPORTS_DIR,
                    "Backups" to ShopPaths.BACKUPS_DIR,
                    "Config" to ShopPaths.CONFIG_DIR,
                    "Audit Logs" to ShopPaths.AUDIT_LOGS_DIR
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    shortcuts.forEach { (label, path) ->
                        AssistChip(
                            onClick = { onOpenDir(path) },
                            label = { Text(label) }
                        )
                    }
                }

                // Breadcrumb + Up Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = explorerState.currentPath,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    if (explorerState.currentPath != ShopPaths.ROOT && explorerState.currentPath.contains("/")) {
                        TextButton(onClick = { onOpenDir(explorerState.currentPath.substringBeforeLast("/")) }) {
                            Text("Up Level")
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(explorerState.entries, key = { it.path }) { entry ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (entry.isDirectory) onOpenDir(entry.path)
                                    else onOpenFile(entry.path, null)
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (entry.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description,
                                    contentDescription = null,
                                    tint = if (entry.isDirectory) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (entry.isDirectory) "Folder" else "${entry.size} bytes • SHA ${entry.sha.take(7)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!entry.isDirectory && canEdit) {
                                    IconButton(onClick = { deleteTarget = entry }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete file", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewFileDialog) {
        var fileName by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("{\n  \n}") }
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Create File in ${explorerState.currentPath}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = fileName, onValueChange = { fileName = it }, label = { Text("File Name (e.g. notes.json)") }, singleLine = true)
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Initial Content") }, minLines = 4)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (fileName.isBlank()) return@Button
                    val full = "${explorerState.currentPath.trimEnd('/')}/${fileName.trim()}"
                    showNewFileDialog = false
                    onCreateFile(full, body)
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewFileDialog = false }) { Text("Cancel") } }
        )
    }

    if (deleteTarget != null) {
        val target = deleteTarget!!
        ConfirmActionDialog(
            title = "Delete ${target.name}?",
            message = "Permanently remove ${target.path} from GitHub? Previous versions remain recoverable via GitHub commit history.",
            confirmLabel = "Delete",
            isDestructive = true,
            onConfirm = {
                deleteTarget = null
                onDeleteFile(target)
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun JsonFileEditorView(
    filePath: String,
    initialContent: String,
    fileSha: String,
    commits: List<com.example.data.github.RemoteCommitEntry>,
    canEdit: Boolean,
    onSave: (String, String) -> Unit,
    onDownload: () -> Unit,
    onRestoreCommit: (String) -> Unit
) {
    var content by remember(initialContent, fileSha) { mutableStateOf(initialContent) }
    var commitMessage by remember { mutableStateOf("SLR Note Bu: Update ${filePath.substringAfterLast("/")}") }

    val jsonValidationError = remember(content, filePath) {
        if (!filePath.endsWith(".json", ignoreCase = true)) null
        else {
            val t = content.trim()
            try {
                when {
                    t.startsWith("{") -> { JSONObject(t); null }
                    t.startsWith("[") -> { JSONArray(t); null }
                    else -> "JSON must start with { or ["
                }
            } catch (e: Exception) {
                e.message ?: "Invalid JSON syntax"
            }
        }
    }

    val isDark = MaterialTheme.colorScheme.background.red < 0.3f
    val jsonTransformation = remember(isDark) { JsonSyntaxVisualTransformation(isDark) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(filePath, style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDownload) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Download")
            }
            if (jsonValidationError == null) {
                Surface(color = Color(0xFF147A50).copy(alpha = 0.14f), shape = MaterialTheme.shapes.small) {
                    Text("Valid JSON", modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), color = Color(0xFF147A50), style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                    Text("JSON Error: $jsonValidationError", modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            BasicTextField(
                value = content,
                onValueChange = { if (canEdit) content = it },
                readOnly = !canEdit,
                textStyle = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                visualTransformation = jsonTransformation,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp, max = 420.dp)
                    .padding(12.dp)
                    .testTag("explorer_json_editor")
            )
        }

        if (canEdit) {
            OutlinedTextField(
                value = commitMessage,
                onValueChange = { commitMessage = it },
                label = { Text("GitHub Commit Message") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onSave(content, commitMessage) },
                enabled = jsonValidationError == null && content != initialContent,
                modifier = Modifier.fillMaxWidth().testTag("explorer_save_button")
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Validate & Save to GitHub")
            }
        }

        // Commit History
        Text("Commit History (${commits.size})", style = MaterialTheme.typography.titleMedium)
        commits.forEach { c ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(c.message, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text("${c.sha.take(7)} • ${c.authorName} • ${c.dateIso.take(16).replace('T', ' ')}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (canEdit) {
                        TextButton(onClick = { onRestoreCommit(c.sha) }) {
                            Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Load")
                        }
                    }
                }
            }
        }
    }
}

private class JsonSyntaxVisualTransformation(private val isDark: Boolean) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val keyColor = if (isDark) Color(0xFF6FE0BC) else Color(0xFF0B5C47)
        val strColor = if (isDark) Color(0xFFF4C56A) else Color(0xFF8C5300)
        val numColor = if (isDark) Color(0xFF9AB6FF) else Color(0xFF284B9B)

        val annotated = buildAnnotatedString {
            append(raw)
            // Highlight JSON keys
            Regex("\"([^\"]+)\"\\s*:").findAll(raw).forEach { m ->
                addStyle(SpanStyle(color = keyColor, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last)
            }
            // Highlight string values
            Regex(":\\s*(\"[^\"]*\")").findAll(raw).forEach { m ->
                val g = m.groups[1] ?: return@forEach
                addStyle(SpanStyle(color = strColor), g.range.first, g.range.last + 1)
            }
            // Highlight numbers & booleans
            Regex(":\\s*(-?\\d+(?:\\.\\d+)?|true|false|null)").findAll(raw).forEach { m ->
                val g = m.groups[1] ?: return@forEach
                addStyle(SpanStyle(color = numColor, fontWeight = FontWeight.Medium), g.range.first, g.range.last + 1)
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}
