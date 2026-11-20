package com.example.data.repository

import com.example.utils.DateUtils

object ShopPaths {
    const val ROOT = "SLR-Note-Bu"

    const val CONFIG_DIR = "$ROOT/config"
    const val SHOP_CONFIG = "$CONFIG_DIR/shop.json"
    const val SETTINGS = "$CONFIG_DIR/settings.json"
    const val USERS = "$CONFIG_DIR/users.json"
    const val CATEGORIES = "$CONFIG_DIR/categories.json"
    const val BACKUP_SETTINGS = "$CONFIG_DIR/backup-settings.json"
    const val CLOSING_HISTORY = "$CONFIG_DIR/closing-history.json"

    const val BABU_DIR = "$ROOT/babu"
    const val CUSTOMERS_DIR = "$BABU_DIR/customers"
    fun customerFile(phoneNormalized: String): String = "$CUSTOMERS_DIR/$phoneNormalized.json"
    fun baTransactionsDir(year: Int, month: Int): String = "$BABU_DIR/transactions/%04d/%02d".format(year, month)
    fun baTransactionsFile(year: Int, month: Int): String = "${baTransactionsDir(year, month)}/transactions.json"

    const val DAILY_SALES_ACTIVE_DIR = "$ROOT/daily-sales/active"
    fun activeDailySalesFile(year: Int, month: Int): String = "$DAILY_SALES_ACTIVE_DIR/%04d-%02d.json".format(year, month)

    const val MONTHLY_ARCHIVE_DIR = "$ROOT/monthly-archive"
    fun monthlyArchiveFile(year: Int, month: Int): String =
        "$MONTHLY_ARCHIVE_DIR/%04d/%s".format(year, DateUtils.monthArchiveFileName(month))

    const val YEARLY_REPORTS_DIR = "$ROOT/yearly-reports"
    fun yearlyReportFile(year: Int): String = "$YEARLY_REPORTS_DIR/%04d.json".format(year)

    const val REPORTS_DAILY_DIR = "$ROOT/reports/daily"
    const val REPORTS_MONTHLY_DIR = "$ROOT/reports/monthly"
    const val REPORTS_YEARLY_DIR = "$ROOT/reports/yearly"

    const val BACKUPS_DIR = "$ROOT/backups"
    fun dailyBackupDir(year: Int, month: Int): String = "$BACKUPS_DIR/daily/%04d/%02d".format(year, month)
    fun dailyBackupFile(year: Int, month: Int, day: Int, suffix: String = ""): String {
        val base = "%02d-backup".format(day)
        return "${dailyBackupDir(year, month)}/$base$suffix.json"
    }
    fun monthlyBackupDir(year: Int): String = "$BACKUPS_DIR/monthly/%04d".format(year)
    const val MANUAL_BACKUPS_DIR = "$BACKUPS_DIR/manual"
    const val PRE_RESTORE_BACKUPS_DIR = "$BACKUPS_DIR/pre-restore"
    const val ANNUAL_BACKUPS_DIR = "$BACKUPS_DIR/annual"

    const val AUDIT_LOGS_DIR = "$ROOT/audit-logs"
    fun auditLogFile(year: Int, month: Int): String = "$AUDIT_LOGS_DIR/%04d/audit-%04d-%02d.json".format(year, year, month)

    const val README = "$ROOT/README.md"
}
