package com.example.domain

import com.example.data.github.GitHubStoragePort
import com.example.data.repository.ShopJsonCodec
import com.example.data.repository.ShopPaths
import com.example.domain.model.AuditLogEntry
import com.example.domain.model.AuditLogFile
import com.example.domain.model.BaTransactionsFile
import com.example.domain.model.BackupFilePayload
import com.example.domain.model.BackupMetadata
import com.example.domain.model.BackupSettings
import com.example.domain.model.BackupType
import com.example.domain.model.CategoriesFile
import com.example.domain.model.ClosingHistoryFile
import com.example.domain.model.CustomerProfile
import com.example.domain.model.DailySalesFile
import com.example.domain.model.LocalShopBinding
import com.example.domain.model.MonthlyArchiveFile
import com.example.domain.model.ShopConfig
import com.example.domain.model.ShopSettings
import com.example.domain.model.ShopUsersFile
import com.example.domain.model.YearlyReportFile
import com.example.security.TokenCryptoManager
import com.example.utils.DateUtils
import com.example.utils.IdGenerator
import java.time.Instant

data class BackupRunResult(
    val success: Boolean,
    val metadata: BackupMetadata? = null,
    val errorMessage: String? = null
)

class BackupService(
    private val storage: GitHubStoragePort,
    private val codec: ShopJsonCodec
) {

    suspend fun createBackup(
        token: String,
        binding: LocalShopBinding,
        actorLogin: String,
        backupType: BackupType,
        nowInstant: Instant = Instant.now()
    ): BackupRunResult {
        val owner = binding.repoOwner
        val repo = binding.repoName
        val nowParts = DateUtils.nowParts(binding.timezoneId, nowInstant)
        val backupId = IdGenerator.backupId()

        return storage.withRepoLock(owner, repo) {
            try {
                val shopConfig = storage.getFile(token, owner, repo, ShopPaths.SHOP_CONFIG)
                    ?.content?.let { codec.shopConfigFromJson(it) }
                    ?: throw IllegalStateException("Missing shop.json for backup.")
                val settings = storage.getFile(token, owner, repo, ShopPaths.SETTINGS)
                    ?.content?.let { codec.settingsFromJson(it) } ?: ShopSettings()
                val users = storage.getFile(token, owner, repo, ShopPaths.USERS)
                    ?.content?.let { codec.usersFromJson(it) }
                    ?: ShopUsersFile(ownerLogin = binding.ownerGithubLogin, users = emptyList())
                val categories = storage.getFile(token, owner, repo, ShopPaths.CATEGORIES)
                    ?.content?.let { codec.categoriesFromJson(it) } ?: CategoriesFile()
                val backupSettings = storage.getFile(token, owner, repo, ShopPaths.BACKUP_SETTINGS)
                    ?.content?.let { codec.backupSettingsFromJson(it) } ?: BackupSettings()
                val closingHistory = storage.getFile(token, owner, repo, ShopPaths.CLOSING_HISTORY)
                    ?.content?.let { codec.closingHistoryFromJson(it) } ?: ClosingHistoryFile()

                val customers = loadAllCustomers(token, owner, repo)
                val activeSales = loadAllActiveSalesFiles(token, owner, repo)
                val baTxFiles = loadAllBaTransactionFiles(token, owner, repo)
                val archives = loadAllMonthlyArchives(token, owner, repo)
                val yearlyReports = loadAllYearlyReports(token, owner, repo)

                val targetPath = resolveNonOverwritingPath(token, owner, repo, backupType, nowParts.year, nowParts.month, nowParts.day, backupId)

                val totalSalesCount = activeSales.sumOf { it.sales.size }
                val totalBaTxCount = baTxFiles.sumOf { it.transactions.size }
                val coreFingerprint = "${shopConfig.shopId}|${customers.size}|$totalSalesCount|$totalBaTxCount|${archives.size}|${nowParts.isoTimestamp}"
                val checksum = TokenCryptoManager.sha256Hex(coreFingerprint)

                val metadata = BackupMetadata(
                    backupId = backupId,
                    backupType = backupType,
                    shopId = shopConfig.shopId,
                    shopName = shopConfig.shopName,
                    repoFullName = "$owner/$repo",
                    createdAtIso = nowParts.isoTimestamp,
                    dateKey = nowParts.dateKey,
                    createdBy = actorLogin,
                    customerCount = customers.size,
                    activeSalesCount = totalSalesCount,
                    baTransactionsCount = totalBaTxCount,
                    checksumSha256 = checksum,
                    filePath = targetPath
                )

                val payload = BackupFilePayload(
                    metadata = metadata,
                    shopConfig = shopConfig,
                    settings = settings,
                    users = users,
                    categories = categories,
                    backupSettings = backupSettings,
                    closingHistory = closingHistory,
                    customers = customers,
                    activeSalesFiles = activeSales,
                    baTransactionFiles = baTxFiles,
                    monthlyArchives = archives,
                    yearlyReports = yearlyReports
                )

                val json = codec.backupPayloadToJson(payload)
                val commitSha = storage.putFile(
                    token = token,
                    owner = owner,
                    repo = repo,
                    path = targetPath,
                    content = json,
                    commitMessage = "SLR Note Bu Backup (${backupType.label}): $backupId",
                    expectedSha = null,
                    validateJson = true
                )

                // Verify backup integrity via read-back
                val readBack = storage.getFile(token, owner, repo, targetPath)
                    ?.content?.let { codec.backupPayloadFromJson(it) }
                    ?: throw IllegalStateException("Backup verification read-back failed.")
                if (readBack.metadata.checksumSha256 != checksum || readBack.metadata.shopId != shopConfig.shopId) {
                    throw IllegalStateException("Backup checksum verification failed.")
                }

                val finalMetadata = metadata.copy(githubCommitSha = commitSha)
                val updatedBackupSettings = backupSettings.copy(
                    lastDailyBackupDateKey = if (backupType == BackupType.DAILY_AUTO) nowParts.dateKey else backupSettings.lastDailyBackupDateKey,
                    lastBackupStatus = "Success (${backupType.label})",
                    lastBackupCommitSha = commitSha,
                    lastBackupAtIso = nowParts.isoTimestamp
                )
                storage.updateJsonFileWithRetry(
                    token = token,
                    owner = owner,
                    repo = repo,
                    path = ShopPaths.BACKUP_SETTINGS,
                    commitMessage = "SLR Note Bu: Update backup status ($backupId)"
                ) {
                    codec.backupSettingsToJson(updatedBackupSettings) to Unit
                }

                appendAuditLog(
                    token = token,
                    owner = owner,
                    repo = repo,
                    entry = AuditLogEntry(
                        auditId = IdGenerator.auditId(),
                        userId = actorLogin,
                        action = "BACKUP_CREATED",
                        recordId = backupId,
                        dateKey = nowParts.dateKey,
                        dateDisplay = nowParts.dateDisplay,
                        isoTimestamp = nowParts.isoTimestamp,
                        description = "${backupType.label} saved to $targetPath (SHA ${commitSha.take(7)})"
                    ),
                    year = nowParts.year,
                    month = nowParts.month
                )

                BackupRunResult(success = true, metadata = finalMetadata)
            } catch (e: Exception) {
                BackupRunResult(success = false, errorMessage = e.message ?: "Backup failed")
            }
        }
    }

    suspend fun restoreFromBackup(
        token: String,
        binding: LocalShopBinding,
        actorLogin: String,
        backupFilePath: String,
        nowInstant: Instant = Instant.now()
    ): Result<BackupMetadata> {
        val owner = binding.repoOwner
        val repo = binding.repoName
        val nowParts = DateUtils.nowParts(binding.timezoneId, nowInstant)

        return try {
            // 1. Validate selected backup before anything else
            val selectedEntry = storage.getFile(token, owner, repo, backupFilePath)
                ?: return Result.failure(IllegalStateException("Selected backup file not found: $backupFilePath"))
            val payload = codec.backupPayloadFromJson(selectedEntry.content)
                ?: return Result.failure(IllegalStateException("Invalid backup JSON format."))

            // 2. Verify shop and repository identity
            val currentShop = storage.getFile(token, owner, repo, ShopPaths.SHOP_CONFIG)
                ?.content?.let { codec.shopConfigFromJson(it) }
            if (currentShop != null && payload.shopConfig.shopId != currentShop.shopId) {
                return Result.failure(
                    IllegalStateException("Backup shop ID (${payload.shopConfig.shopId}) does not match active shop (${currentShop.shopId}).")
                )
            }

            // 3. Create mandatory Pre-Restore Backup of current state
            val preRestore = createBackup(token, binding, actorLogin, BackupType.PRE_RESTORE, nowInstant)
            if (!preRestore.success) {
                return Result.failure(IllegalStateException("Pre-restore safety backup failed: ${preRestore.errorMessage}"))
            }

            // 4. Restore intended data safely with SHA updates
            storage.withRepoLock(owner, repo) {
                storage.updateJsonFileWithRetry(token, owner, repo, ShopPaths.SHOP_CONFIG, "Restore shop config from ${payload.metadata.backupId}") {
                    codec.shopConfigToJson(payload.shopConfig) to Unit
                }
                storage.updateJsonFileWithRetry(token, owner, repo, ShopPaths.SETTINGS, "Restore settings from ${payload.metadata.backupId}") {
                    codec.settingsToJson(payload.settings) to Unit
                }
                storage.updateJsonFileWithRetry(token, owner, repo, ShopPaths.CATEGORIES, "Restore categories from ${payload.metadata.backupId}") {
                    codec.categoriesToJson(payload.categories) to Unit
                }
                storage.updateJsonFileWithRetry(token, owner, repo, ShopPaths.CLOSING_HISTORY, "Restore closing history from ${payload.metadata.backupId}") {
                    codec.closingHistoryToJson(payload.closingHistory) to Unit
                }
                payload.customers.forEach { customer ->
                    storage.updateJsonFileWithRetry(
                        token, owner, repo,
                        ShopPaths.customerFile(customer.phoneNumber),
                        "Restore customer ${customer.customerName}"
                    ) {
                        codec.customerToJson(customer) to Unit
                    }
                }
                payload.activeSalesFiles.forEach { salesFile ->
                    storage.updateJsonFileWithRetry(
                        token, owner, repo,
                        ShopPaths.activeDailySalesFile(salesFile.year, salesFile.month),
                        "Restore active sales ${salesFile.monthKey}"
                    ) {
                        codec.dailySalesToJson(salesFile) to Unit
                    }
                }
                payload.baTransactionFiles.forEach { baFile ->
                    storage.updateJsonFileWithRetry(
                        token, owner, repo,
                        ShopPaths.baTransactionsFile(baFile.year, baFile.month),
                        "Restore Ba transactions ${baFile.monthKey}"
                    ) {
                        codec.baTransactionsToJson(baFile) to Unit
                    }
                }
                payload.monthlyArchives.forEach { archive ->
                    storage.updateJsonFileWithRetry(
                        token, owner, repo,
                        ShopPaths.monthlyArchiveFile(archive.year, archive.month),
                        "Restore monthly archive ${archive.monthKey}"
                    ) {
                        codec.monthlyArchiveToJson(archive) to Unit
                    }
                }
                payload.yearlyReports.forEach { yearly ->
                    storage.updateJsonFileWithRetry(
                        token, owner, repo,
                        ShopPaths.yearlyReportFile(yearly.year),
                        "Restore yearly report ${yearly.year}"
                    ) {
                        codec.yearlyReportToJson(yearly) to Unit
                    }
                }

                appendAuditLog(
                    token = token,
                    owner = owner,
                    repo = repo,
                    entry = AuditLogEntry(
                        auditId = IdGenerator.auditId(),
                        userId = actorLogin,
                        action = "BACKUP_RESTORE",
                        recordId = payload.metadata.backupId,
                        dateKey = nowParts.dateKey,
                        dateDisplay = nowParts.dateDisplay,
                        isoTimestamp = nowParts.isoTimestamp,
                        description = "Restored database from $backupFilePath (pre-restore backup: ${preRestore.metadata?.backupId})"
                    ),
                    year = nowParts.year,
                    month = nowParts.month
                )
            }
            Result.success(payload.metadata)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listBackupHistory(
        token: String,
        binding: LocalShopBinding
    ): List<BackupMetadata> {
        val owner = binding.repoOwner
        val repo = binding.repoName
        val paths = collectJsonFilePaths(token, owner, repo, ShopPaths.BACKUPS_DIR)
        return paths.mapNotNull { path ->
            val content = storage.getFile(token, owner, repo, path)?.content ?: return@mapNotNull null
            codec.backupPayloadFromJson(content)?.metadata?.copy(filePath = path)
        }.sortedByDescending { it.createdAtIso }
    }

    suspend fun collectAllRepoFilesForZip(
        token: String,
        owner: String,
        repo: String,
        baseDir: String = ShopPaths.ROOT
    ): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val entries = storage.listDirectory(token, owner, repo, baseDir)
        for (entry in entries) {
            if (entry.isDirectory) {
                result.putAll(collectAllRepoFilesForZip(token, owner, repo, entry.path))
            } else if (entry.name != ".gitkeep") {
                val file = storage.getFile(token, owner, repo, entry.path)
                if (file != null) {
                    result[file.path] = file.content
                }
            }
        }
        return result
    }

    private suspend fun collectJsonFilePaths(
        token: String,
        owner: String,
        repo: String,
        dir: String
    ): List<String> {
        val out = mutableListOf<String>()
        val entries = storage.listDirectory(token, owner, repo, dir)
        for (entry in entries) {
            if (entry.isDirectory) {
                out.addAll(collectJsonFilePaths(token, owner, repo, entry.path))
            } else if (entry.name.endsWith(".json", ignoreCase = true)) {
                out.add(entry.path)
            }
        }
        return out
    }

    private suspend fun resolveNonOverwritingPath(
        token: String,
        owner: String,
        repo: String,
        backupType: BackupType,
        year: Int,
        month: Int,
        day: Int,
        backupId: String
    ): String {
        return when (backupType) {
            BackupType.DAILY_AUTO -> {
                val base = ShopPaths.dailyBackupFile(year, month, day)
                if (storage.getFile(token, owner, repo, base) == null) base
                else ShopPaths.dailyBackupFile(year, month, day, "-${backupId.takeLast(6)}")
            }
            BackupType.MONTHLY_ARCHIVE -> "${ShopPaths.monthlyBackupDir(year)}/%02d-${backupId.takeLast(6)}.json".format(month)
            BackupType.MANUAL -> "${ShopPaths.MANUAL_BACKUPS_DIR}/${DateUtils.dateKey(year, month, day)}-$backupId.json"
            BackupType.PRE_RESTORE -> "${ShopPaths.PRE_RESTORE_BACKUPS_DIR}/${DateUtils.dateKey(year, month, day)}-$backupId.json"
            BackupType.ANNUAL -> {
                val base = "${ShopPaths.ANNUAL_BACKUPS_DIR}/$year-annual-backup.json"
                if (storage.getFile(token, owner, repo, base) == null) base
                else "${ShopPaths.ANNUAL_BACKUPS_DIR}/$year-annual-${backupId.takeLast(6)}.json"
            }
        }
    }

    private suspend fun loadAllCustomers(token: String, owner: String, repo: String): List<CustomerProfile> {
        return storage.listDirectory(token, owner, repo, ShopPaths.CUSTOMERS_DIR)
            .filter { !it.isDirectory && it.name.endsWith(".json") }
            .mapNotNull { entry ->
                storage.getFile(token, owner, repo, entry.path)?.content?.let { codec.customerFromJson(it) }
            }
    }

    private suspend fun loadAllActiveSalesFiles(token: String, owner: String, repo: String): List<DailySalesFile> {
        return storage.listDirectory(token, owner, repo, ShopPaths.DAILY_SALES_ACTIVE_DIR)
            .filter { !it.isDirectory && it.name.endsWith(".json") }
            .mapNotNull { entry ->
                storage.getFile(token, owner, repo, entry.path)?.content?.let { codec.dailySalesFromJson(it) }
            }
    }

    private suspend fun loadAllBaTransactionFiles(token: String, owner: String, repo: String): List<BaTransactionsFile> {
        val paths = collectJsonFilePaths(token, owner, repo, "${ShopPaths.BABU_DIR}/transactions")
        return paths.mapNotNull { p ->
            storage.getFile(token, owner, repo, p)?.content?.let { codec.baTransactionsFromJson(it) }
        }
    }

    private suspend fun loadAllMonthlyArchives(token: String, owner: String, repo: String): List<MonthlyArchiveFile> {
        val paths = collectJsonFilePaths(token, owner, repo, ShopPaths.MONTHLY_ARCHIVE_DIR)
        return paths.mapNotNull { p ->
            storage.getFile(token, owner, repo, p)?.content?.let { codec.monthlyArchiveFromJson(it) }
        }
    }

    private suspend fun loadAllYearlyReports(token: String, owner: String, repo: String): List<YearlyReportFile> {
        return storage.listDirectory(token, owner, repo, ShopPaths.YEARLY_REPORTS_DIR)
            .filter { !it.isDirectory && it.name.endsWith(".json") }
            .mapNotNull { entry ->
                storage.getFile(token, owner, repo, entry.path)?.content?.let { codec.yearlyReportFromJson(it) }
            }
    }

    private suspend fun appendAuditLog(
        token: String,
        owner: String,
        repo: String,
        entry: AuditLogEntry,
        year: Int,
        month: Int
    ) {
        val monthKey = DateUtils.monthKey(year, month)
        storage.updateJsonFileWithRetry(
            token = token,
            owner = owner,
            repo = repo,
            path = ShopPaths.auditLogFile(year, month),
            commitMessage = "SLR Note Bu Audit: ${entry.action} (${entry.recordId})"
        ) { current ->
            val file = current?.let { codec.auditLogFromJson(it) } ?: AuditLogFile(monthKey = monthKey)
            val updated = file.copy(entries = (listOf(entry) + file.entries).take(1000))
            codec.auditLogToJson(updated) to Unit
        }
    }
}
