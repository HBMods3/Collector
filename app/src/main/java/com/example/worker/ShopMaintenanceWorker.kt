package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.SlrNoteBuApp
import com.example.domain.model.BackupType
import com.example.domain.model.ClosingStatus
import com.example.utils.DateUtils
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Runs periodically in the background even when SLR Note Bu is closed.
 * Checks every connected shop's local timezone for:
 * 1. Scheduled Daily Automatic Backup (default 23:55 local time)
 * 2. Automatic Monthly Sales Closing at the start of a new month
 */
class ShopMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? SlrNoteBuApp ?: return Result.success()
        val container = app.container
        val shops = container.sessionStore.getAllShops()
        if (shops.isEmpty()) return Result.success()

        val accounts = container.sessionStore.getActiveAccount()
        var hadTransientFailure = false

        for (binding in shops) {
            val token = container.sessionStore.getDecryptedTokenForAccount(binding.accountId) ?: continue
            val actorLogin = binding.ownerGithubLogin.ifBlank { accounts?.login ?: "auto-scheduler" }

            try {
                // 1. Check Automatic Monthly Closing first
                container.shopRepo.runMonthlyClosingIfDue(token, binding, actorLogin)

                // 2. Check Daily Automatic Backup
                val nowLocal = DateUtils.currentDateTime(binding.timezoneId)
                val todayKey = DateUtils.dateKey(nowLocal.year, nowLocal.monthValue, nowLocal.dayOfMonth)
                val backupSettings = container.shopRepo.storage
                    .getFile(token, binding.repoOwner, binding.repoName, com.example.data.repository.ShopPaths.BACKUP_SETTINGS)
                    ?.content?.let { container.codec.backupSettingsFromJson(it) }

                if (backupSettings != null && backupSettings.autoDailyBackupEnabled) {
                    val alreadyBackedUpToday = backupSettings.lastDailyBackupDateKey == todayKey
                    val targetTime = parseTimeSafe(backupSettings.preferredBackupTime)
                    if (!alreadyBackedUpToday && !nowLocal.toLocalTime().isBefore(targetTime)) {
                        val res = container.shopRepo.backupService.createBackup(
                            token = token,
                            binding = binding,
                            actorLogin = "auto-backup",
                            backupType = BackupType.DAILY_AUTO
                        )
                        if (!res.success) hadTransientFailure = true
                    }
                }

                // 3. Check Annual Backup on Jan 1 for previous year if closed
                if (nowLocal.monthValue == 1 && nowLocal.dayOfMonth == 1) {
                    val prevYear = nowLocal.year - 1
                    val closingHist = container.shopRepo.storage
                        .getFile(token, binding.repoOwner, binding.repoName, com.example.data.repository.ShopPaths.CLOSING_HISTORY)
                        ?.content?.let { container.codec.closingHistoryFromJson(it) }
                    val decClosed = closingHist?.records?.any {
                        it.year == prevYear && it.month == 12 && it.closingStatus == ClosingStatus.COMPLETED
                    } == true
                    val annualPath = "${com.example.data.repository.ShopPaths.ANNUAL_BACKUPS_DIR}/$prevYear-annual-backup.json"
                    if (decClosed && container.shopRepo.storage.getFile(token, binding.repoOwner, binding.repoName, annualPath) == null) {
                        container.shopRepo.backupService.createBackup(
                            token = token,
                            binding = binding,
                            actorLogin = "auto-annual",
                            backupType = BackupType.ANNUAL
                        )
                    }
                }
            } catch (_: Exception) {
                hadTransientFailure = true
            }
        }

        return if (hadTransientFailure) Result.retry() else Result.success()
    }

    private fun parseTimeSafe(hhmm: String): LocalTime {
        return try {
            val parts = hhmm.split(":")
            LocalTime.of(parts[0].toInt().coerceIn(0, 23), parts[1].toInt().coerceIn(0, 59))
        } catch (_: Exception) {
            LocalTime.of(23, 55)
        }
    }

    companion object {
        private const val WORK_NAME = "slr_note_bu_daily_monthly_maintenance"

        fun schedule(context: Context) {
            try {
                if (!WorkManager.isInitialized()) {
                    WorkManager.initialize(
                        context.applicationContext,
                        androidx.work.Configuration.Builder().build()
                    )
                }
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val request = PeriodicWorkRequestBuilder<ShopMaintenanceWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            } catch (_: Throwable) {
                // Safe no-op if WorkManager provider is unavailable in isolated test hosts
            }
        }
    }
}
