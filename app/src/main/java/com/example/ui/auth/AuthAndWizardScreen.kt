package com.example.ui.auth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddBusiness
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.github.GitHubRepoDto
import com.example.domain.model.GitHubAccountSession
import com.example.ui.AppMainUiState
import com.example.ui.DeviceFlowUiState
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthAndShopWizardScreen(
    uiState: AppMainUiState,
    deviceFlowState: DeviceFlowUiState,
    onSaveClientId: (String) -> Unit,
    onStartOAuth: () -> Unit,
    onCancelOAuth: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    onRefreshRepos: () -> Unit,
    onCreateShopNewRepo: (shopName: String, repoName: String, address: String, phone: String, timezone: String) -> Unit,
    onConnectExistingRepo: (repo: GitHubRepoDto, shopName: String, address: String, phone: String, timezone: String) -> Unit,
    onCancelWizardIfShopExists: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activeAccount = uiState.activeAccount
    var showClientIdConfig by remember(uiState.effectiveClientId) {
        mutableStateOf(uiState.effectiveClientId.isBlank())
    }
    var clientIdInput by remember(uiState.effectiveClientId) {
        mutableStateOf(uiState.effectiveClientId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (activeAccount == null) "SLR Note Bu • GitHub Sign-In"
                        else "Shop Setup Wizard"
                    )
                },
                navigationIcon = {
                    if (onCancelWizardIfShopExists != null) {
                        IconButton(onClick = onCancelWizardIfShopExists) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_shop_hero),
                    contentDescription = "SLR Note Bu Shop Ledger",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )

                // Step 1: GitHub Authentication
                GitHubLoginCard(
                    activeAccount = activeAccount,
                    accounts = uiState.accounts,
                    effectiveClientId = uiState.effectiveClientId,
                    deviceFlowState = deviceFlowState,
                    showClientIdConfig = showClientIdConfig,
                    clientIdInput = clientIdInput,
                    onClientIdInputChange = { clientIdInput = it },
                    onToggleClientIdConfig = { showClientIdConfig = !showClientIdConfig },
                    onSaveClientId = onSaveClientId,
                    onStartOAuth = onStartOAuth,
                    onCancelOAuth = onCancelOAuth,
                    onSwitchAccount = onSwitchAccount,
                    context = context
                )

                // Step 2: Shop Creation / Repo Binding Wizard (shown when authenticated)
                if (activeAccount != null) {
                    ShopCreationWizardCard(
                        account = activeAccount,
                        availableRepos = uiState.availableRepos,
                        onRefreshRepos = onRefreshRepos,
                        onCreateShopNewRepo = onCreateShopNewRepo,
                        onConnectExistingRepo = onConnectExistingRepo
                    )
                }
            }
        }
    }
}

@Composable
private fun GitHubLoginCard(
    activeAccount: GitHubAccountSession?,
    accounts: List<GitHubAccountSession>,
    effectiveClientId: String,
    deviceFlowState: DeviceFlowUiState,
    showClientIdConfig: Boolean,
    clientIdInput: String,
    onClientIdInputChange: (String) -> Unit,
    onToggleClientIdConfig: () -> Unit,
    onSaveClientId: (String) -> Unit,
    onStartOAuth: () -> Unit,
    onCancelOAuth: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    context: Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text("Step 1 • Connect GitHub Account", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "SLR Note Bu stores each shop's sales, Ba Bu ledger, monthly archives, and backups inside your own private GitHub repository using encrypted OAuth sessions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (accounts.isNotEmpty()) {
                Text("Connected Accounts", style = MaterialTheme.typography.labelLarge)
                accounts.forEach { acc ->
                    val isActive = acc.accountId == activeAccount?.accountId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSwitchAccount(acc.accountId) },
                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = acc.avatarUrl,
                                contentDescription = acc.login,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(18.dp))
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(acc.displayName, style = MaterialTheme.typography.titleSmall)
                                Text("@${acc.login}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (isActive) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = "Active", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            // Device Flow State Machine UI
            when (deviceFlowState) {
                is DeviceFlowUiState.Idle -> {
                    Button(
                        onClick = onStartOAuth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_oauth_login_button")
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (activeAccount == null) "Login with GitHub (OAuth)"
                            else "Connect Another GitHub Account"
                        )
                    }
                }
                is DeviceFlowUiState.RequestingCode -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Requesting GitHub verification code...")
                    }
                }
                is DeviceFlowUiState.WaitingForUser -> {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Enter this code on GitHub:", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = deviceFlowState.deviceCode.userCode,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "Waiting for approval (${deviceFlowState.secondsRemaining}s remaining)...",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newPlainText("GitHub Code", deviceFlowState.deviceCode.userCode))
                                }) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy Code")
                                }
                                Button(onClick = {
                                    val customTabs = CustomTabsIntent.Builder().build()
                                    customTabs.launchUrl(context, Uri.parse(deviceFlowState.deviceCode.verificationUri))
                                }) {
                                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open GitHub")
                                }
                            }
                            TextButton(onClick = onCancelOAuth) { Text("Cancel") }
                        }
                    }
                }
                is DeviceFlowUiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(deviceFlowState.message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = onStartOAuth) { Text("Try Again") }
                        }
                    }
                }
            }

            // OAuth Client ID Setup Panel
            TextButton(onClick = onToggleClientIdConfig) {
                Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (effectiveClientId.isBlank()) "Configure GitHub OAuth Client ID (Required)"
                    else "GitHub OAuth App Config (Configured)"
                )
            }

            if (showClientIdConfig) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Create a GitHub OAuth App at github.com/settings/developers, enable 'Device Authorization Flow', and set GITHUB_CLIENT_ID in AI Studio Secrets or enter the Client ID below:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = clientIdInput,
                        onValueChange = onClientIdInputChange,
                        label = { Text("GitHub OAuth Client ID") },
                        placeholder = { Text("Ov23li...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_client_id_input")
                    )
                    Button(
                        onClick = { onSaveClientId(clientIdInput) },
                        enabled = clientIdInput.isNotBlank()
                    ) {
                        Text("Save OAuth Client ID")
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopCreationWizardCard(
    account: GitHubAccountSession,
    availableRepos: List<GitHubRepoDto>,
    onRefreshRepos: () -> Unit,
    onCreateShopNewRepo: (shopName: String, repoName: String, address: String, phone: String, timezone: String) -> Unit,
    onConnectExistingRepo: (repo: GitHubRepoDto, shopName: String, address: String, phone: String, timezone: String) -> Unit
) {
    var createNewMode by remember { mutableStateOf(true) }
    var shopName by remember { mutableStateOf("") }
    var repoName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var timezone by remember { mutableStateOf("Asia/Kolkata") }
    var selectedExistingRepo by remember { mutableStateOf<GitHubRepoDto?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AddBusiness, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Step 2 • Shop Creation Wizard (@${account.login})", style = MaterialTheme.typography.titleMedium)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = createNewMode,
                    onClick = { createNewMode = true },
                    label = { Text("Create New Private Repo") }
                )
                FilterChip(
                    selected = !createNewMode,
                    onClick = {
                        createNewMode = false
                        onRefreshRepos()
                    },
                    label = { Text("Use Existing Repo") }
                )
            }

            OutlinedTextField(
                value = shopName,
                onValueChange = {
                    shopName = it
                    if (createNewMode && repoName.isBlank()) {
                        // Suggest clean slug
                    }
                },
                label = { Text("Shop Name *") },
                placeholder = { Text("e.g., Zoram General Store") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wizard_shop_name_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Veng / Khua / Address") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = { contactPhone = it },
                    label = { Text("Shop Phone") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = timezone,
                onValueChange = { timezone = it },
                label = { Text("Shop Timezone") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            if (createNewMode) {
                val suggestedSlug = shopName.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                OutlinedTextField(
                    value = repoName,
                    onValueChange = { repoName = it },
                    label = { Text("Private Repository Name *") },
                    placeholder = { Text(if (suggestedSlug.isNotBlank()) "slr-note-bu-$suggestedSlug" else "slr-note-bu-shop") },
                    supportingText = { Text("Created as a PRIVATE GitHub repository owned by @${account.login}") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wizard_repo_name_input")
                )
                Button(
                    onClick = {
                        val finalRepo = repoName.ifBlank { "slr-note-bu-$suggestedSlug" }
                        onCreateShopNewRepo(shopName, finalRepo, address, contactPhone, timezone)
                    },
                    enabled = shopName.isNotBlank() && (repoName.isNotBlank() || suggestedSlug.isNotBlank()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_shop_submit_button")
                ) {
                    Text("Create Private Repo & Initialize Database")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Private Repository", style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = onRefreshRepos) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh repositories")
                    }
                }
                val privateRepos = availableRepos.filter { it.isPrivate }
                if (privateRepos.isEmpty()) {
                    Text(
                        "No private repositories loaded yet. Tap refresh or create a new private repository above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        privateRepos.take(15).forEach { r ->
                            val selected = selectedExistingRepo?.id == r.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedExistingRepo = r },
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selected, onClick = { selectedExistingRepo = r })
                                    Column {
                                        Text(r.fullName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Text("Private • Branch: ${r.defaultBranch ?: "main"}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val repo = selectedExistingRepo ?: return@Button
                        onConnectExistingRepo(repo, shopName, address, contactPhone, timezone)
                    },
                    enabled = selectedExistingRepo != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect Selected Repository & Open Dashboard")
                }
            }
        }
    }
}
