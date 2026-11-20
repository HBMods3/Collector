package com.example.domain

import com.example.data.github.GitHubStoragePort
import com.example.data.repository.ShopJsonCodec
import com.example.data.repository.ShopPaths
import com.example.domain.model.AuditLogEntry
import com.example.domain.model.AuditLogFile
import com.example.domain.model.BaTransaction
import com.example.domain.model.ClosingHistoryFile
import com.example.domain.model.ClosingStatus
import com.example.domain.model.DailySalesFile
import com.example.domain.model.LocalShopBinding
import com.example.domain.model.MonthlyArchiveFile
import com.example.domain.model.MonthlyClosingRecord
import com.example.utils.DateUtils
import com.example.utils.IdGenerator
import com.example.utils.MoneyUtils
import java.time.Instant

data class MonthlyClosingResult(
    val success: Boolean,
    val alreadyClosed: Boolean = false,
    val record: MonthlyClosingRecord? = null,
    val errorMessage: String? = null
)

class MonthlyClosingService(
    private val storage: GitHubStoragePort,
    private val codec: ShopJsonCodec
) {
    /**
     * Executes the 8-step verified monthly closing & clearing protocol:
     * 1. Guard against duplicate closing via closing-history.json.
     * 2. Read all active Daily Sales for (targetYear, targetMonth).
     * 3. Calculate totals & build MonthlyArchiveFile.
     * 4. Upload monthly archive to GitHub.
     * 5. Read back saved archive from GitHub.
     * 6. Verify item counts, transaction counts, and paise totals against original records.
     * 7. Verify GitHub commit SHA.
     * 8. ONLY THEN clear active sales file for (targetYear, targetMonth), initialize next month's empty
     *    active file, update yearly report, record COMPLETED in closing-history.json, and write audit log.
     *
     * Ba Bu customers, balances, credit history, and payment history are NEVER touched.
     */
    suspend fun closeMonthAndArchive(
        token: String,
        binding: LocalShopBinding,
        actorLogin: String,
        targetYear: Int,
        targetMonth: Int,
        nowInstant: Instant = Instant.now()
    ): MonthlyClosingResult {
        val owner = binding.repoOwner
        val repo = binding.repoName
        val targetMonthKey = DateUtils.monthKey(targetYear, targetMonth)
        val archivePath = ShopPaths.monthlyArchiveFile(targetYear, targetMonth)
        val activePath = ShopPaths.activeDailySalesFile(targetYear, targetMonth)
        val nowParts = DateUtils.nowParts(binding.timezoneId, nowInstant)

        return storage.withRepoLock(owner, repo) {
            // Step 1: Check closing-history.json to prevent closing same month twice
            val historyEntry = storage.getFile(token, owner, repo, ShopPaths.CLOSING_HISTORY)
            val history = historyEntry?.content?.let { codec.closingHistoryFromJson(it) } ?: ClosingHistoryFile()
            val alreadyCompleted = history.records.firstOrNull {
                it.monthKey == targetMonthKey && it.closingStatus == ClosingStatus.COMPLETED && it.verificationStatus
            }
            if (alreadyCompleted != null) {
                return@withRepoLock MonthlyClosingResult(
                    success = true,
                    alreadyClosed = true,
                    record = alreadyCompleted
                )
            }

            // Step 2: Read all previous month's active Daily Sales
            val activeFileEntry = storage.getFile(token, owner, repo, activePath)
            val activeFile = activeFileEntry?.content?.let { codec.dailySalesFromJson(it) }
                ?: DailySalesFile(monthKey = targetMonthKey, year = targetYear, month = targetMonth, sales = emptyList())
            val originalSales = activeFile.sales.filter { !it.deleted }

            // Step 3: Build complete Monthly Archive (merge with any existing archive corrections if retrying)
            val existingArchive = storage.getFile(token, owner, repo, archivePath)
                ?.content?.let { codec.monthlyArchiveFromJson(it) }
            val archive = AccountingCalculator.buildMonthlyArchive(
                year = targetYear,
                month = targetMonth,
                archivedAtIso = nowParts.isoTimestamp,
                archivedBy = actorLogin,
                sales = originalSales
            ).copy(corrections = existingArchive?.corrections ?: emptyList())

            try {
                // Step 4: Upload archive to GitHub
                val archiveJson = codec.monthlyArchiveToJson(archive)
                val existingArchiveEntry = storage.getFile(token, owner, repo, archivePath)
                val commitSha = storage.putFile(
                    token = token,
                    owner = owner,
                    repo = repo,
                    path = archivePath,
                    content = archiveJson,
                    commitMessage = "SLR Note Bu: Monthly Sales Archive for $targetMonthKey",
                    expectedSha = existingArchiveEntry?.sha,
                    validateJson = true
                )
                if (commitSha.isBlank()) {
                    throw IllegalStateException("GitHub commit SHA verification returned empty.")
                }

                // Step 5: Read back saved archive from GitHub
                val readBackEntry = storage.getFile(token, owner, repo, archivePath)
                    ?: throw IllegalStateException("Read-back verification failed: $archivePath missing after commit.")
                val readBackArchive = codec.monthlyArchiveFromJson(readBackEntry.content)
                    ?: throw IllegalStateException("Read-back verification failed: invalid archive JSON.")

                // Step 6 & 7: Verify item counts and totals against original records
                val verified = AccountingCalculator.verifyMonthlyArchive(originalSales, readBackArchive)
                if (!verified) {
                    throw IllegalStateException("Archive verification mismatch: totals or item counts do not match original active sales.")
                }

                // Step 8a: Clear previous month's active Daily Sales ONLY after verification passes
                val clearedOldMonth = DailySalesFile(
                    monthKey = targetMonthKey,
                    year = targetYear,
                    month = targetMonth,
                    sales = emptyList(),
                    updatedAtIso = nowParts.isoTimestamp
                )
                storage.updateJsonFileWithRetry(
                    token = token,
                    owner = owner,
                    repo = repo,
                    path = activePath,
                    commitMessage = "SLR Note Bu: Clear verified active Daily Sales for $targetMonthKey"
                ) {
                    codec.dailySalesToJson(clearedOldMonth) to Unit
                }

                // Step 8b: Ensure next month's active Daily Sales file is initialized empty
                val (nextYear, nextMonth) = if (targetMonth == 12) (targetYear + 1) to 1 else targetYear to (targetMonth + 1)
                val nextMonthKey = DateUtils.monthKey(nextYear, nextMonth)
                val nextActivePath = ShopPaths.activeDailySalesFile(nextYear, nextMonth)
                val nextExisting = storage.getFile(token, owner, repo, nextActivePath)
                if (nextExisting == null) {
                    val emptyNext = DailySalesFile(
                        monthKey = nextMonthKey,
                        year = nextYear,
                        month = nextMonth,
                        sales = emptyList(),
                        updatedAtIso = nowParts.isoTimestamp
                    )
                    storage.putFile(
                        token = token,
                        owner = owner,
                        repo = repo,
                        path = nextActivePath,
                        content = codec.dailySalesToJson(emptyNext),
                        commitMessage = "SLR Note Bu: Initialize clean Daily Sales for $nextMonthKey",
                        expectedSha = null
                    )
                }

                // Step 8c: Update yearly report for targetYear
                updateYearlyReport(token, owner, repo, targetYear, nowParts.isoTimestamp)

                // Step 8d: Record COMPLETED in closing-history.json
                val closingRecord = MonthlyClosingRecord(
                    year = targetYear,
                    month = targetMonth,
                    monthKey = targetMonthKey,
                    closingStatus = ClosingStatus.COMPLETED,
                    closingDateIso = nowParts.isoTimestamp,
                    totalSalesPaise = archive.totalSalesPaise,
                    cashSalesPaise = archive.cashSalesPaise,
                    otherOfflineSalesPaise = archive.otherOfflineSalesPaise,
                    creditSalesPaise = archive.creditSalesPaise,
                    transactionCount = archive.transactionCount,
                    totalItemsSold = archive.totalProductsSold,
                    archivePath = archivePath,
                    githubCommitSha = commitSha,
                    verificationStatus = true
                )
                storage.updateJsonFileWithRetry(
                    token = token,
                    owner = owner,
                    repo = repo,
                    path = ShopPaths.CLOSING_HISTORY,
                    commitMessage = "SLR Note Bu: Record monthly closing for $targetMonthKey"
                ) { current ->
                    val hist = current?.let { codec.closingHistoryFromJson(it) } ?: ClosingHistoryFile()
                    val updatedList = hist.records.filterNot { it.monthKey == targetMonthKey } + closingRecord
                    codec.closingHistoryToJson(hist.copy(records = updatedList.sortedByDescending { it.monthKey })) to Unit
                }

                // Step 8e: Audit log
                appendAuditLog(
                    token = token,
                    owner = owner,
                    repo = repo,
                    entry = AuditLogEntry(
                        auditId = IdGenerator.auditId(),
                        userId = actorLogin,
                        action = "MONTHLY_CLOSING",
                        recordId = targetMonthKey,
                        dateKey = nowParts.dateKey,
                        dateDisplay = nowParts.dateDisplay,
                        isoTimestamp = nowParts.isoTimestamp,
                        description = "Archived and closed $targetMonthKey (${archive.transactionCount} sales, ${MoneyUtils.formatInr(archive.totalSalesPaise)})",
                        previousValue = "${originalSales.size} active sales",
                        updatedValue = "Archived to $archivePath (SHA ${commitSha.take(7)})"
                    ),
                    year = nowParts.year,
                    month = nowParts.month
                )

                MonthlyClosingResult(success = true, alreadyClosed = false, record = closingRecord)
            } catch (e: Exception) {
                // On ANY failure, DO NOT clear active sales! Record FAILED status for Admin retry.
                val failedRecord = MonthlyClosingRecord(
                    year = targetYear,
                    month = targetMonth,
                    monthKey = targetMonthKey,
                    closingStatus = ClosingStatus.FAILED,
                    closingDateIso = nowParts.isoTimestamp,
                    totalSalesPaise = archive.totalSalesPaise,
                    cashSalesPaise = archive.cashSalesPaise,
                    otherOfflineSalesPaise = archive.otherOfflineSalesPaise,
                    creditSalesPaise = archive.creditSalesPaise,
                    transactionCount = archive.transactionCount,
                    totalItemsSold = archive.totalProductsSold,
                    archivePath = archivePath,
                    githubCommitSha = null,
                    verificationStatus = false,
                    errorMessage = e.message ?: "Monthly closing failed"
                )
                try {
                    storage.updateJsonFileWithRetry(
                        token = token,
                        owner = owner,
                        repo = repo,
                        path = ShopPaths.CLOSING_HISTORY,
                        commitMessage = "SLR Note Bu: Record failed monthly closing attempt for $targetMonthKey"
                    ) { current ->
                        val hist = current?.let { codec.closingHistoryFromJson(it) } ?: ClosingHistoryFile()
                        val updatedList = hist.records.filterNot { it.monthKey == targetMonthKey } + failedRecord
                        codec.closingHistoryToJson(hist.copy(records = updatedList.sortedByDescending { it.monthKey })) to Unit
                    }
                } catch (_: Exception) {
                }
                MonthlyClosingResult(
                    success = false,
                    alreadyClosed = false,
                    record = failedRecord,
                    errorMessage = e.message ?: "Monthly closing failed; active sales preserved safely."
                )
            }
        }
    }

    suspend fun updateYearlyReport(
        token: String,
        owner: String,
        repo: String,
        year: Int,
        nowIso: String
    ) {
        val archives = mutableListOf<MonthlyArchiveFile>()
        val activeFiles = mutableListOf<DailySalesFile>()
        val baTx = mutableListOf<BaTransaction>()

        for (m in 1..12) {
            storage.getFile(token, owner, repo, ShopPaths.monthlyArchiveFile(year, m))
                ?.content?.let { codec.monthlyArchiveFromJson(it) }?.let { archives.add(it) }
            storage.getFile(token, owner, repo, ShopPaths.activeDailySalesFile(year, m))
                ?.content?.let { codec.dailySalesFromJson(it) }?.let { activeFiles.add(it) }
            storage.getFile(token, owner, repo, ShopPaths.baTransactionsFile(year, m))
                ?.content?.let { codec.baTransactionsFromJson(it) }?.let { baTx.addAll(it.transactions) }
        }

        val yearly = AccountingCalculator.computeYearlyReport(
            year = year,
            updatedAtIso = nowIso,
            activeSalesFiles = activeFiles,
            monthlyArchives = archives,
            baTransactions = baTx
        )

        storage.updateJsonFileWithRetry(
            token = token,
            owner = owner,
            repo = repo,
            path = ShopPaths.yearlyReportFile(year),
            commitMessage = "SLR Note Bu: Update Yearly Report $year"
        ) {
            codec.yearlyReportToJson(yearly) to Unit
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
