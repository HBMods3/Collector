package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.github.CreateRepoRequest
import com.example.data.github.DeviceCodeResponse
import com.example.data.github.GitHubException
import com.example.data.github.GitHubRepoDto
import com.example.data.github.RemoteCommitEntry
import com.example.data.github.RemoteDirEntry
import com.example.data.github.RemoteFileEntry
import com.example.domain.AccountingCalculator
import com.example.domain.model.BaItemDetail
import com.example.domain.model.BaPaymentMethod
import com.example.domain.model.BaTransaction
import com.example.domain.model.BackupMetadata
import com.example.domain.model.BackupType
import com.example.domain.model.CustomerProfile
import com.example.domain.model.DashboardMetrics
import com.example.domain.model.GitHubAccountSession
import com.example.domain.model.LocalShopBinding
import com.example.domain.model.MonthlyArchiveFile
import com.example.domain.model.PaymentMode
import com.example.domain.model.SaleLineItem
import com.example.domain.model.SaleRecord
import com.example.domain.model.ShopSnapshot
import com.example.domain.model.UserRole
import com.example.domain.model.YearlyReportFile
import com.example.security.TokenCryptoManager
import com.example.ui.theme.ThemeMode
import com.example.utils.DateUtils
import com.example.utils.IdGenerator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

sealed class DeviceFlowUiState {
    data object Idle : DeviceFlowUiState()
    data object RequestingCode : DeviceFlowUiState()
    data class WaitingForUser(
        val deviceCode: DeviceCodeResponse,
        val secondsRemaining: Int
    ) : DeviceFlowUiState()
    data class Error(val message: String) : DeviceFlowUiState()
}

data class ExplorerUiState(
    val currentPath: String = "SLR-Note-Bu",
    val entries: List<RemoteDirEntry> = emptyList(),
    val selectedFile: RemoteFileEntry? = null,
    val commits: List<RemoteCommitEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class AppMainUiState(
    val accounts: List<GitHubAccountSession> = emptyList(),
    val activeAccount: GitHubAccountSession? = null,
    val shops: List<LocalShopBinding> = emptyList(),
    val activeShopBinding: LocalShopBinding? = null,
    val snapshot: ShopSnapshot? = null,
    val dashboardMetrics: DashboardMetrics = DashboardMetrics(),
    val monthlyArchivesForYear: List<MonthlyArchiveFile> = emptyList(),
    val selectedArchive: MonthlyArchiveFile? = null,
    val selectedYearlyReport: YearlyReportFile? = null,
    val backupHistory: List<BackupMetadata> = emptyList(),
    val availableRepos: List<GitHubRepoDto> = emptyList(),
    val isBusy: Boolean = false,
    val busyMessage: String = "",
    val snackbarMessage: String? = null,
    val errorMessage: String? = null,
    val tokenExpiredBanner: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val effectiveClientId: String = "",
    val showShopWizard: Boolean = false
)

class MainViewModel(private val container: AppContainer) : ViewModel() {

    private val sessionStore = container.sessionStore
    private val repo = container.shopRepo

    private val _deviceFlowState = MutableStateFlow<DeviceFlowUiState>(DeviceFlowUiState.Idle)
    val deviceFlowState: StateFlow<DeviceFlowUiState> = _deviceFlowState.asStateFlow()

    private val _explorerState = MutableStateFlow(ExplorerUiState())
    val explorerState: StateFlow<ExplorerUiState> = _explorerState.asStateFlow()

    private val _uiState = MutableStateFlow(AppMainUiState())
    val uiState: StateFlow<AppMainUiState> = _uiState.asStateFlow()

    private var devicePollingJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                sessionStore.accountsFlow,
                sessionStore.activeAccountIdFlow,
                sessionStore.shopsFlow,
                sessionStore.activeShopIdFlow,
                sessionStore.themeModeFlow
            ) { accounts, activeAccId, shops, activeShopId, themeMode ->
                val activeAcc = accounts.firstOrNull { it.accountId == activeAccId } ?: accounts.firstOrNull()
                val activeShop = shops.firstOrNull { it.shopId == activeShopId } ?: shops.firstOrNull()
                Triple(accounts to activeAcc, shops to activeShop, themeMode)
            }.collect { (accPair, shopPair, theme) ->
                val clientId = sessionStore.getEffectiveGithubClientId()
                val prevShopId = _uiState.value.activeShopBinding?.shopId
                val nextShop = shopPair.second
                _uiState.value = _uiState.value.copy(
                    accounts = accPair.first,
                    activeAccount = accPair.second,
                    shops = shopPair.first,
                    activeShopBinding = nextShop,
                    themeMode = theme,
                    effectiveClientId = clientId,
                    snapshot = if (nextShop == null) null else if (nextShop.shopId == prevShopId) _uiState.value.snapshot else repo.getCachedSnapshot(nextShop.shopId)
                )
                if (nextShop != null && nextShop.shopId != prevShopId) {
                    syncActiveShop(checkAutoClose = true)
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null, errorMessage = null)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { sessionStore.setThemeMode(mode) }
    }

    fun saveGithubClientId(clientId: String) {
        viewModelScope.launch {
            sessionStore.saveCustomGithubClientId(clientId)
            val eff = sessionStore.getEffectiveGithubClientId()
            _uiState.value = _uiState.value.copy(
                effectiveClientId = eff,
                snackbarMessage = if (eff.isNotBlank()) "GitHub OAuth Client ID saved." else "Client ID cleared."
            )
        }
    }

    fun openShopWizard(open: Boolean) {
        _uiState.value = _uiState.value.copy(showShopWizard = open)
        if (open) loadUserRepositories()
    }

    // ==================== GITHUB OAUTH DEVICE FLOW ====================

    fun startGitHubDeviceOAuth() {
        devicePollingJob?.cancel()
        viewModelScope.launch {
            val clientId = sessionStore.getEffectiveGithubClientId()
            if (clientId.isBlank()) {
                _deviceFlowState.value = DeviceFlowUiState.Error(
                    "GitHub OAuth Client ID is not configured. Set GITHUB_CLIENT_ID in AI Studio Secrets or enter it below."
                )
                return@launch
            }
            _deviceFlowState.value = DeviceFlowUiState.RequestingCode
            try {
                val resp = container.gitHubAuthService.requestDeviceCode(clientId = clientId, scope = "repo read:user")
                val body = resp.body()
                if (!resp.isSuccessful || body == null) {
                    _deviceFlowState.value = DeviceFlowUiState.Error(
                        "GitHub OAuth failed (${resp.code()}). Ensure Device Flow is enabled on your GitHub OAuth App."
                    )
                    return@launch
                }
                _deviceFlowState.value = DeviceFlowUiState.WaitingForUser(body, body.expiresIn)
                pollDeviceToken(clientId, body)
            } catch (e: Exception) {
                _deviceFlowState.value = DeviceFlowUiState.Error("Network error starting GitHub OAuth: ${e.message}")
            }
        }
    }

    private fun pollDeviceToken(clientId: String, codeResp: DeviceCodeResponse) {
        devicePollingJob = viewModelScope.launch {
            var intervalSec = codeResp.interval.coerceAtLeast(5)
            var remaining = codeResp.expiresIn
            while (remaining > 0) {
                delay(intervalSec * 1000L)
                remaining -= intervalSec
                val cur = _deviceFlowState.value
                if (cur is DeviceFlowUiState.WaitingForUser) {
                    _deviceFlowState.value = cur.copy(secondsRemaining = remaining.coerceAtLeast(0))
                }
                try {
                    val tokenResp = container.gitHubAuthService.pollDeviceAccessToken(
                        clientId = clientId,
                        deviceCode = codeResp.deviceCode
                    )
                    val body = tokenResp.body() ?: continue
                    val accessToken = body.accessToken
                    if (!accessToken.isNullOrBlank()) {
                        completeGitHubLogin(accessToken, body.scope ?: "repo read:user")
                        _deviceFlowState.value = DeviceFlowUiState.Idle
                        return@launch
                    }
                    when (body.error) {
                        "authorization_pending" -> Unit
                        "slow_down" -> intervalSec += (body.interval ?: 5)
                        "expired_token" -> {
                            _deviceFlowState.value = DeviceFlowUiState.Error("Authorization code expired. Please try again.")
                            return@launch
                        }
                        "access_denied" -> {
                            _deviceFlowState.value = DeviceFlowUiState.Error("GitHub authorization was cancelled.")
                            return@launch
                        }
                        else -> if (body.error != null) {
                            _deviceFlowState.value = DeviceFlowUiState.Error(body.errorDescription ?: body.error)
                            return@launch
                        }
                    }
                } catch (_: Exception) {
                    // Retry on transient network hiccup while polling
                }
            }
            _deviceFlowState.value = DeviceFlowUiState.Error("GitHub authorization timed out. Please retry.")
        }
    }

    fun cancelGitHubDeviceOAuth() {
        devicePollingJob?.cancel()
        _deviceFlowState.value = DeviceFlowUiState.Idle
    }

    private suspend fun completeGitHubLogin(accessToken: String, scope: String) {
        val userResp = container.gitHubApiService.getAuthenticatedUser("Bearer $accessToken")
        val user = userResp.body() ?: throw IllegalStateException("Failed to fetch GitHub user profile (${userResp.code()}).")
        val session = GitHubAccountSession(
            accountId = user.id.toString(),
            login = user.login,
            displayName = user.name?.takeIf { it.isNotBlank() } ?: user.login,
            avatarUrl = user.avatarUrl ?: "",
            encryptedToken = TokenCryptoManager.encrypt(accessToken),
            scope = scope,
            connectedAtIso = DateUtils.nowParts("Asia/Kolkata").isoTimestamp
        )
        sessionStore.upsertAccount(session, setActive = true)
        _uiState.value = _uiState.value.copy(
            tokenExpiredBanner = false,
            snackbarMessage = "Signed in to GitHub as @${user.login}"
        )
        loadUserRepositories()
    }

    fun switchAccount(accountId: String) {
        viewModelScope.launch {
            sessionStore.setActiveAccount(accountId)
            val matchingShop = _uiState.value.shops.firstOrNull { it.accountId == accountId }
            sessionStore.setActiveShop(matchingShop?.shopId)
            loadUserRepositories()
        }
    }

    fun logoutAccount(accountId: String) {
        viewModelScope.launch {
            val shopsToRemove = _uiState.value.shops.filter { it.accountId == accountId }
            shopsToRemove.forEach {
                repo.clearShopCache(it.shopId)
                sessionStore.removeShop(it.shopId)
            }
            sessionStore.removeAccount(accountId)
            _uiState.value = _uiState.value.copy(snackbarMessage = "GitHub account disconnected.")
        }
    }

    fun switchShop(shopId: String) {
        viewModelScope.launch {
            val shop = _uiState.value.shops.firstOrNull { it.shopId == shopId } ?: return@launch
            sessionStore.setActiveAccount(shop.accountId)
            sessionStore.setActiveShop(shopId)
        }
    }

    fun loadUserRepositories() {
        viewModelScope.launch {
            val acc = sessionStore.getActiveAccount() ?: return@launch
            val token = sessionStore.getDecryptedTokenForAccount(acc.accountId) ?: return@launch
            try {
                val resp = container.gitHubApiService.listUserRepos("Bearer $token")
                if (resp.code() == 401) {
                    _uiState.value = _uiState.value.copy(tokenExpiredBanner = true)
                    return@launch
                }
                if (resp.isSuccessful) {
                    val repos = resp.body() ?: emptyList()
                    _uiState.value = _uiState.value.copy(availableRepos = repos)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun createShopWithNewPrivateRepo(
        shopName: String,
        repoName: String,
        addressOrVeng: String,
        contactPhone: String,
        timezoneId: String
    ) {
        runBusy("Creating private GitHub repository & initializing SLR Note Bu...") {
            val acc = sessionStore.getActiveAccount() ?: throw IllegalStateException("Please sign in with GitHub first.")
            val token = sessionStore.getDecryptedTokenForAccount(acc.accountId)
                ?: throw GitHubException.TokenExpiredException()
            val cleanRepoName = repoName.trim().replace(" ", "-")
            require(shopName.isNotBlank() && cleanRepoName.isNotBlank()) { "Shop name and repository name are required." }

            val createResp = container.gitHubApiService.createPrivateRepo(
                "Bearer $token",
                CreateRepoRequest(name = cleanRepoName, description = "SLR Note Bu private database for $shopName", isPrivate = true, autoInit = true)
            )
            if (createResp.code() == 401) throw GitHubException.TokenExpiredException()
            val created = createResp.body()
            if (!createResp.isSuccessful || created == null) {
                throw IllegalStateException("Could not create private repository (${createResp.code()}): ${createResp.errorBody()?.string() ?: ""}")
            }
            // Brief wait for GitHub auto_init commit propagation
            delay(900)
            val binding = LocalShopBinding(
                shopId = IdGenerator.shopId(),
                shopName = shopName.trim(),
                accountId = acc.accountId,
                ownerGithubLogin = acc.login,
                repoOwner = created.owner.login,
                repoName = created.name,
                defaultBranch = created.defaultBranch ?: "main",
                timezoneId = timezoneId.ifBlank { "Asia/Kolkata" },
                createdAtIso = DateUtils.nowParts(timezoneId).isoTimestamp
            )
            val snapshot = repo.initializeNewShopDatabase(token, binding, acc.login, acc.displayName, addressOrVeng, contactPhone)
            sessionStore.upsertShop(binding, setActive = true)
            applySnapshot(snapshot)
            _uiState.value = _uiState.value.copy(showShopWizard = false, snackbarMessage = "Shop '${shopName.trim()}' created & isolated!")
        }
    }

    fun connectExistingRepoAsShop(
        repoDto: GitHubRepoDto,
        shopNameIfNew: String,
        addressOrVeng: String,
        contactPhone: String,
        timezoneId: String
    ) {
        runBusy("Connecting ${repoDto.fullName}...") {
            val acc = sessionStore.getActiveAccount() ?: throw IllegalStateException("Sign in with GitHub first.")
            val token = sessionStore.getDecryptedTokenForAccount(acc.accountId)
                ?: throw GitHubException.TokenExpiredException()
            val existingCfg = repo.inspectExistingShopConfig(token, repoDto.owner.login, repoDto.name)
            val binding = LocalShopBinding(
                shopId = existingCfg?.shopId ?: IdGenerator.shopId(),
                shopName = existingCfg?.shopName ?: shopNameIfNew.ifBlank { repoDto.name },
                accountId = acc.accountId,
                ownerGithubLogin = existingCfg?.ownerGithubLogin ?: acc.login,
                repoOwner = repoDto.owner.login,
                repoName = repoDto.name,
                defaultBranch = repoDto.defaultBranch ?: "main",
                timezoneId = existingCfg?.timezoneId ?: timezoneId.ifBlank { "Asia/Kolkata" },
                createdAtIso = existingCfg?.createdAtIso ?: DateUtils.nowParts(timezoneId).isoTimestamp
            )
            val snapshot = if (existingCfg != null) {
                repo.loadShopSnapshot(token, binding, acc.login)
            } else {
                repo.initializeNewShopDatabase(token, binding, acc.login, acc.displayName, addressOrVeng, contactPhone)
            }
            sessionStore.upsertShop(binding, setActive = true)
            applySnapshot(snapshot)
            _uiState.value = _uiState.value.copy(showShopWizard = false, snackbarMessage = "Connected ${binding.shopName} (${repoDto.fullName})")
        }
    }

    fun disconnectActiveShop() {
        viewModelScope.launch {
            val binding = _uiState.value.activeShopBinding ?: return@launch
            repo.clearShopCache(binding.shopId)
            sessionStore.removeShop(binding.shopId)
            _uiState.value = _uiState.value.copy(snapshot = null, snackbarMessage = "Disconnected shop '${binding.shopName}'.")
        }
    }

    // ==================== SYNC & DASHBOARD ====================

    fun syncActiveShop(checkAutoClose: Boolean = false) {
        runBusy("Syncing with GitHub...") {
            val (token, binding, acc) = requireActiveShopContext()
            if (checkAutoClose) {
                try {
                    val closingRes = repo.runMonthlyClosingIfDue(token, binding, acc.login)
                    if (closingRes != null && closingRes.success && !closingRes.alreadyClosed) {
                        _uiState.value = _uiState.value.copy(
                            snackbarMessage = "Auto-closed ${closingRes.record?.monthKey} sales & started fresh month!"
                        )
                    }
                } catch (_: Exception) {
                }
            }
            val snap = repo.loadShopSnapshot(token, binding, acc.login)
            applySnapshot(snap)
        }
    }

    private suspend fun applySnapshot(snapshot: ShopSnapshot) {
        val today = DateUtils.todayDate(snapshot.shopConfig.timezoneId)
        // Load archived months for current year if any exist for accurate YTD metrics
        val (token, _, _) = try {
            requireActiveShopContext()
        } catch (_: Exception) {
            Triple("", snapshot.binding, null)
        }
        val archives = if (token.isNotBlank()) {
            val list = mutableListOf<MonthlyArchiveFile>()
            for (m in 1..12) {
                repo.loadMonthlyArchive(token, snapshot.binding, today.year, m)?.let { list.add(it) }
            }
            list
        } else emptyList()

        val metrics = AccountingCalculator.computeDashboardMetrics(
            today = today,
            activeSalesFiles = snapshot.allActiveSalesFiles,
            monthlyArchives = archives,
            customers = snapshot.customers,
            baTransactions = snapshot.baTransactions
        )
        _uiState.value = _uiState.value.copy(
            snapshot = snapshot,
            dashboardMetrics = metrics,
            monthlyArchivesForYear = archives,
            tokenExpiredBanner = false
        )
    }

    // ==================== SALES ACTIONS ====================

    fun addSale(
        items: List<SaleLineItem>,
        paymentMode: PaymentMode,
        note: String,
        saleDate: LocalDate,
        saleTime: LocalTime,
        linkedCustomerPhone: String?
    ) {
        runBusy("Saving sale to GitHub...") {
            val (token, binding, acc) = requireActiveShopContext()
            val snap = repo.addSale(token, binding, acc.login, items, paymentMode, note, saleDate, saleTime, linkedCustomerPhone)
            applySnapshot(snap)
            _uiState.value = _uiState.value.copy(snackbarMessage = "Sale saved to GitHub.")
        }
    }

    fun editSale(original: SaleRecord, items: List<SaleLineItem>, mode: PaymentMode, note: String) {
        runBusy("Updating sale...") {
            val (token, binding, acc) = requireActiveShopContext()
            val snap = repo.editSale(token, binding, acc.login, original, items, mode, note)
            applySnapshot(snap)
            _uiState.value = _uiState.value.copy(snackbarMessage = "Sale updated.")
        }
    }

    fun deleteSale(sale: SaleRecord) {
        runBusy("Deleting sale...") {
            val (token, binding, acc) = requireActiveShopContext()
            val snap = repo.deleteSale(token, binding, acc.login, sale)
            applySnapshot(snap)
            _uiState.value = _uiState.value.copy(snackbarMessage = "Sale deleted & logged.")
        }
    }

    // ==================== BA BU & CUSTOMER ACTIONS ====================

    fun addCustomer(name: String, phone: String, veng: String, initialBaPaise: Long, notes: String) {
        runBusy("Registering customer...") {
            val (token, binding, acc) = requireActiveShopContext()
            val snap = repo.addCustomer(token, binding, acc.login, name, phone, veng, initialBaPaise, notes)
            applySnapshot(snap)
            _uiState.value = _uiState.value.copy(snackbarMessage = "Customer '$name' registered.")
        }
    }

    fun updateCustomer(original: CustomerProfile, name: String, veng: String, notes: String) {
        runBusy("Updating customer...") {
            val (token, binding, acc) = requireActiveShopContext()
            val snap = repo.updateCustomer(token, binding, acc.login, original, name, veng, notes)
            applySnapshot(snap)
            _uiState.value = _uiState.value.copy(snackbarMessage = "Customer updated.")
        }
    }

    fun addBaCredit(customerPhone: String, items: List<BaItemDetail>, note: String, date: LocalDate, time: LocalTime) {
        runBusy("Recording Ba credit...") {
            val (token, binding, acc) = requireActiveShopContext()
            val snap = repo.addBaCredit(token, binding, acc.login, customerPhone, items, note, date, time)
            applySnapshot(snap)
            _uiState.value = _uiState.value.copy(snackbarMessage = "Ba credit recorded.")
        }
    }

    fun recordBaPayment(
        customerPhone: String,
        amountPaise: Long,
        method: BaPaymentMethod,
        percentageUsed: Double?,
        note: String,
        date: LocalDate,
        time: LocalTime
    ) {
        runBusy("Recording Ba payment...") {
            val (token, binding, acc) = requireActiveShopContext()
            val snap = repo.recordBaPayment(token, binding, acc.login, customerPhone, amountPaise, method, percentageUsed, note, date, time)
            applySnapshot(snap)
            _uiState.value = _uiState.value.copy(snackbarMessage = "Ba payment recorded & balance updated.")
        }
    }

    fun correctOrReverseBaTransaction(tx: BaTransaction, newAmountPaise: Long?, reason: String) {
        runBusy("Recalculating ledger...") {
            val (token, binding, acc) = requireActiveShopContext()
            val snap = repo.correctOrReverseBaTransaction(token, binding, acc.login, tx, newAmountPaise, reason)
            applySnapshot(snap)
            _uiState.value = _uiState.value.copy(snackbarMessage = if (newAmountPaise == null) "Transaction reversed." else "Transaction corrected.")
        }
    }

    fun addQuickItemPreset(name: String, pricePaise: Long) {
        runBusy("Saving quick item...") {
            val (token, binding, acc) = requireActiveShopContext()
            val snap = repo.addQuickItemPreset(token, binding, acc.login, name, pricePaise)
            applySnapshot(snap)
            _uiState.value = _uiState.value.copy(snackbarMessage = "Quick item added.")
        }
    }

    // ==================== ARCHIVES, REPORTS & BACKUPS ====================

    fun loadMonthlyArchive(year: Int, month: Int) {
        runBusy("Loading monthly archive...") {
            val (token, binding, _) = requireActiveShopContext()
            val arch = repo.loadMonthlyArchive(token, binding, year, month)
            _uiState.value = _uiState.value.copy(selectedArchive = arch)
        }
    }

    fun triggerMonthlyClosing(year: Int, month: Int) {
        runBusy("Archiving & closing ${DateUtils.monthName(month)} $year...") {
            val (token, binding, acc) = requireActiveShopContext()
            val res = repo.closingService.closeMonthAndArchive(token, binding, acc.login, year, month)
            if (!res.success) throw IllegalStateException(res.errorMessage ?: "Monthly closing failed.")
            val snap = repo.loadShopSnapshot(token, binding, acc.login)
            applySnapshot(snap)
            val msg = if (res.alreadyClosed) "${DateUtils.monthName(month)} $year was already closed." else "${DateUtils.monthName(month)} $year archived & active sales cleared!"
            _uiState.value = _uiState.value.copy(snackbarMessage = msg)
        }
    }

    fun addArchiveCorrection(year: Int, month: Int, description: String, amountPaise: Long, mode: PaymentMode) {
        runBusy("Recording archive correction...") {
            val (token, binding, acc) = requireActiveShopContext()
            val updated = repo.addArchiveCorrection(token, binding, acc.login, year, month, description, amountPaise, mode)
            val snap = repo.loadShopSnapshot(token, binding, acc.login)
            applySnapshot(snap)
            _uiState.value = _uiState.value.copy(selectedArchive = updated, snackbarMessage = "Correction recorded & report regenerated.")
        }
    }

    fun loadYearlyReport(year: Int) {
        runBusy("Computing yearly report $year...") {
            val (token, binding, acc) = requireActiveShopContext()
            repo.closingService.updateYearlyReport(token, binding.repoOwner, binding.repoName, year, DateUtils.nowParts(binding.timezoneId).isoTimestamp)
            val yr = repo.loadYearlyReport(token, binding, year)
            val snap = repo.loadShopSnapshot(token, binding, acc.login)
            applySnapshot(snap)
            _uiState.value = _uiState.value.copy(selectedYearlyReport = yr)
        }
    }

    fun loadBackupHistory() {
        runBusy("Loading backup history...") {
            val (token, binding, _) = requireActiveShopContext()
            val list = repo.backupService.listBackupHistory(token, binding)
            _uiState.value = _uiState.value.copy(backupHistory = list)
        }
    }

    fun createBackupNow(type: BackupType = BackupType.MANUAL) {
        runBusy("Creating ${type.label}...") {
            val (token, binding, acc) = requireActiveShopContext()
            val res = repo.backupService.createBackup(token, binding, acc.login, type)
            if (!res.success) throw IllegalStateException(res.errorMessage ?: "Backup failed.")
            val list = repo.backupService.listBackupHistory(token, binding)
            val snap = repo.loadShopSnapshot(token, binding, acc.login)
            applySnapshot(snap)
            _uiState.value = _uiState.value.copy(
                backupHistory = list,
                snackbarMessage = "Backup ${res.metadata?.backupId} verified & saved!"
            )
        }
    }

    fun restoreFromBackup(backupPath: String) {
        runBusy("Creating safety backup & restoring...") {
            val (token, binding, acc) = requireActiveShopContext()
            val result = repo.backupService.restoreFromBackup(token, binding, acc.login, backupPath)
            result.getOrThrow()
            val snap = repo.loadShopSnapshot(token, binding, acc.login)
            applySnapshot(snap)
            val list = repo.backupService.listBackupHistory(token, binding)
            _uiState.value = _uiState.value.copy(backupHistory = list, snackbarMessage = "Database restored safely!")
        }
    }

    suspend fun collectFilesForZip(baseDir: String): Map<String, String> {
        val (token, binding, _) = requireActiveShopContext()
        return repo.backupService.collectAllRepoFilesForZip(token, binding.repoOwner, binding.repoName, baseDir)
    }

    suspend fun readRawBackupJson(path: String): String? {
        val (token, binding, _) = requireActiveShopContext()
        return repo.readRepoFile(token, binding, path)?.content
    }

    // ==================== SETTINGS & USERS ====================

    fun updateShopAndBackupSettings(
        shopName: String,
        address: String,
        phone: String,
        timezone: String,
        backupTime: String,
        autoDailyBackup: Boolean,
        autoMonthlyClosing: Boolean,
        allowDbEdit: Boolean
    ) {
        runBusy("Updating shop settings...") {
            val (token, binding, acc) = requireActiveShopContext()
            val snap = repo.updateShopAndBackupSettings(
                token, binding, acc.login, shopName, address, phone, timezone,
                backupTime, autoDailyBackup, autoMonthlyClosing, allowDbEdit
            )
            sessionStore.upsertShop(snap.binding, setActive = true)
            applySnapshot(snap)
            _uiState.value = _uiState.value.copy(snackbarMessage = "Settings saved to GitHub.")
        }
    }

    fun saveAuthorizedUser(githubLogin: String, displayName: String, role: UserRole, active: Boolean) {
        runBusy("Updating user permissions...") {
            val (token, binding, acc) = requireActiveShopContext()
            val snap = repo.addOrUpdateUser(token, binding, acc.login, githubLogin, displayName, role, active)
            applySnapshot(snap)
            _uiState.value = _uiState.value.copy(snackbarMessage = "Updated @$githubLogin permissions.")
        }
    }

    // ==================== GITHUB DATABASE EXPLORER ====================

    fun explorerOpenDir(path: String = "SLR-Note-Bu") {
        viewModelScope.launch {
            _explorerState.value = _explorerState.value.copy(isLoading = true, error = null)
            try {
                val (token, binding, _) = requireActiveShopContext()
                val entries = repo.listRepoDirectory(token, binding, path)
                _explorerState.value = ExplorerUiState(currentPath = path, entries = entries, isLoading = false)
            } catch (e: Exception) {
                _explorerState.value = _explorerState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun explorerOpenFile(path: String, ref: String? = null) {
        viewModelScope.launch {
            _explorerState.value = _explorerState.value.copy(isLoading = true, error = null)
            try {
                val (token, binding, _) = requireActiveShopContext()
                val file = repo.readRepoFile(token, binding, path, ref)
                val commits = repo.listFileCommits(token, binding, path)
                _explorerState.value = _explorerState.value.copy(
                    selectedFile = file,
                    commits = commits,
                    isLoading = false
                )
            } catch (e: Exception) {
                _explorerState.value = _explorerState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun explorerCloseFile() {
        _explorerState.value = _explorerState.value.copy(selectedFile = null, commits = emptyList())
    }

    fun explorerSaveFile(path: String, content: String, expectedSha: String?, commitMessage: String) {
        runBusy("Validating & committing $path...") {
            val (token, binding, acc) = requireActiveShopContext()
            repo.saveExplorerFile(token, binding, acc.login, path, content, expectedSha, commitMessage)
            val refreshed = repo.readRepoFile(token, binding, path)
            val commits = repo.listFileCommits(token, binding, path)
            _explorerState.value = _explorerState.value.copy(selectedFile = refreshed, commits = commits)
            val snap = repo.loadShopSnapshot(token, binding, acc.login)
            applySnapshot(snap)
            _uiState.value = _uiState.value.copy(snackbarMessage = "Committed $path to GitHub.")
        }
    }

    fun explorerCreateFile(path: String, initialContent: String) {
        runBusy("Creating $path...") {
            val (token, binding, acc) = requireActiveShopContext()
            repo.saveExplorerFile(token, binding, acc.login, path, initialContent, null, "Create $path via Explorer")
            val entries = repo.listRepoDirectory(token, binding, _explorerState.value.currentPath)
            _explorerState.value = _explorerState.value.copy(entries = entries)
            _uiState.value = _uiState.value.copy(snackbarMessage = "Created $path")
        }
    }

    fun explorerDeleteFile(entry: RemoteDirEntry) {
        runBusy("Deleting ${entry.name}...") {
            val (token, binding, acc) = requireActiveShopContext()
            repo.deleteExplorerFile(token, binding, acc.login, entry.path, entry.sha)
            val entries = repo.listRepoDirectory(token, binding, _explorerState.value.currentPath)
            _explorerState.value = _explorerState.value.copy(entries = entries, selectedFile = null)
            _uiState.value = _uiState.value.copy(snackbarMessage = "Deleted ${entry.name}")
        }
    }

    // ==================== CONTEXT & ERROR WRAPPER ====================

    private suspend fun requireActiveShopContext(): Triple<String, LocalShopBinding, GitHubAccountSession> {
        val binding = sessionStore.getActiveShop() ?: throw IllegalStateException("No shop selected.")
        val acc = sessionStore.accountsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
            .value.firstOrNull { it.accountId == binding.accountId }
            ?: sessionStore.getActiveAccount()
            ?: throw IllegalStateException("GitHub account not connected.")
        val token = sessionStore.getDecryptedTokenForAccount(binding.accountId)
            ?: throw GitHubException.TokenExpiredException()
        return Triple(token, binding, acc)
    }

    private fun runBusy(label: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, busyMessage = label, errorMessage = null)
            try {
                block()
            } catch (e: GitHubException.TokenExpiredException) {
                _uiState.value = _uiState.value.copy(
                    tokenExpiredBanner = true,
                    errorMessage = e.message
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Operation failed."
                )
            } finally {
                _uiState.value = _uiState.value.copy(isBusy = false, busyMessage = "")
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(container) as T
        }
    }
}
