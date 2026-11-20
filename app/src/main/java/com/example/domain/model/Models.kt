package com.example.domain.model

import com.squareup.moshi.JsonClass

// ==================== ENUMS ====================

enum class PaymentMode(val label: String) {
    CASH("Cash"),
    OTHER_OFFLINE("Other Offline Payment"),
    CREDIT_BA("Credit / Ba")
}

enum class BaPaymentMethod(val label: String) {
    CASH("Cash"),
    OTHER_OFFLINE("Other Offline Payment")
}

enum class BaTransactionType(val label: String) {
    CREDIT("Credit / Ba Added"),
    PAYMENT("Ba Payment"),
    ADJUSTMENT("Correction / Adjustment")
}

enum class UserRole(val label: String, val level: Int) {
    OWNER("Owner / Admin", 4),
    MANAGER("Manager", 3),
    SHOPKEEPER("Shopkeeper", 2),
    VIEWER("Viewer (Read-Only)", 1);

    fun canManageSecurity(): Boolean = this == OWNER
    fun canEditDatabase(): Boolean = this == OWNER
    fun canDeleteOrReverse(): Boolean = this == OWNER || this == MANAGER
    fun canWriteTransactions(): Boolean = this != VIEWER
    fun canRunBackupOrClose(): Boolean = this == OWNER || this == MANAGER
}

enum class BackupType(val folderName: String, val label: String) {
    DAILY_AUTO("daily", "Daily Automatic"),
    MONTHLY_ARCHIVE("monthly", "Monthly Archive"),
    MANUAL("manual", "Manual Backup"),
    PRE_RESTORE("pre-restore", "Pre-Restore Safety"),
    ANNUAL("annual", "Annual Backup")
}

enum class ClosingStatus {
    COMPLETED,
    FAILED,
    IN_PROGRESS
}

// ==================== LOCAL SESSION & MULTI-SHOP REGISTRY ====================

@JsonClass(generateAdapter = true)
data class GitHubAccountSession(
    val accountId: String,
    val login: String,
    val displayName: String,
    val avatarUrl: String,
    val encryptedToken: String,
    val scope: String,
    val connectedAtIso: String
)

@JsonClass(generateAdapter = true)
data class LocalShopBinding(
    val shopId: String,
    val shopName: String,
    val accountId: String,
    val ownerGithubLogin: String,
    val repoOwner: String,
    val repoName: String,
    val defaultBranch: String = "main",
    val timezoneId: String = "Asia/Kolkata",
    val createdAtIso: String
)

// ==================== CONFIG JSON FILES (SLR-Note-Bu/config/) ====================

@JsonClass(generateAdapter = true)
data class ShopConfig(
    val shopId: String,
    val shopName: String,
    val addressOrVeng: String = "",
    val contactPhone: String = "",
    val ownerGithubLogin: String,
    val repoOwner: String,
    val repoName: String,
    val currencyCode: String = "INR",
    val currencySymbol: String = "₹",
    val timezoneId: String = "Asia/Kolkata",
    val createdAtIso: String,
    val updatedAtIso: String
)

@JsonClass(generateAdapter = true)
data class ShopSettings(
    val allowDatabaseExplorerEdit: Boolean = true,
    val autoMonthlyClosingEnabled: Boolean = true,
    val smsReminderTemplate: String = "Hello {customerName}, your current pending Ba at {shopName} is {pendingAmount}. Please clear your outstanding balance when convenient. Thank you.",
    val defaultPaymentMode: PaymentMode = PaymentMode.CASH,
    val updatedAtIso: String = ""
)

@JsonClass(generateAdapter = true)
data class AuthorizedUser(
    val githubLogin: String,
    val displayName: String,
    val role: UserRole,
    val active: Boolean = true,
    val addedAtIso: String,
    val addedBy: String
)

@JsonClass(generateAdapter = true)
data class ShopUsersFile(
    val ownerLogin: String,
    val users: List<AuthorizedUser>
)

@JsonClass(generateAdapter = true)
data class QuickItemPreset(
    val id: String,
    val name: String,
    val defaultPricePaise: Long,
    val category: String = "General"
)

@JsonClass(generateAdapter = true)
data class CategoriesFile(
    val categories: List<String> = listOf("Groceries", "Staples", "Beverages", "Household", "General"),
    val quickItems: List<QuickItemPreset> = listOf(
        QuickItemPreset("qi_rice", "Rice (1 kg)", 5500L, "Staples"),
        QuickItemPreset("qi_oil", "Cooking Oil (1 L)", 16000L, "Groceries"),
        QuickItemPreset("qi_sugar", "Sugar (1 kg)", 5000L, "Staples"),
        QuickItemPreset("qi_atta", "Atta (1 kg)", 4500L, "Staples"),
        QuickItemPreset("qi_tea", "Tea Leaves", 8000L, "Beverages"),
        QuickItemPreset("qi_soap", "Washing Soap", 3500L, "Household")
    )
)

@JsonClass(generateAdapter = true)
data class BackupSettings(
    val autoDailyBackupEnabled: Boolean = true,
    val preferredBackupTime: String = "23:55",
    val lastDailyBackupDateKey: String? = null,
    val lastBackupStatus: String = "Not yet run",
    val lastBackupCommitSha: String? = null,
    val lastBackupAtIso: String? = null
)

@JsonClass(generateAdapter = true)
data class MonthlyClosingRecord(
    val year: Int,
    val month: Int,
    val monthKey: String,
    val closingStatus: ClosingStatus,
    val closingDateIso: String,
    val totalSalesPaise: Long,
    val cashSalesPaise: Long,
    val otherOfflineSalesPaise: Long,
    val creditSalesPaise: Long,
    val transactionCount: Int,
    val totalItemsSold: Int,
    val archivePath: String,
    val githubCommitSha: String?,
    val verificationStatus: Boolean,
    val errorMessage: String? = null
)

@JsonClass(generateAdapter = true)
data class ClosingHistoryFile(
    val records: List<MonthlyClosingRecord> = emptyList()
)

// ==================== DAILY SALES JSON (SLR-Note-Bu/daily-sales/active/YYYY-MM.json) ====================

@JsonClass(generateAdapter = true)
data class SaleLineItem(
    val itemName: String,
    val pricePaise: Long,
    val quantity: Int,
    val itemTotalPaise: Long
)

@JsonClass(generateAdapter = true)
data class SaleRecord(
    val saleId: String,
    val clientRequestId: String = "",
    val items: List<SaleLineItem>,
    val grandTotalPaise: Long,
    val paymentMode: PaymentMode,
    val linkedCustomerPhone: String? = null,
    val linkedCustomerName: String? = null,
    val linkedBaTransactionId: String? = null,
    val note: String = "",
    val day: Int,
    val month: Int,
    val year: Int,
    val dateKey: String,
    val dateDisplay: String,
    val timeDisplay: String,
    val isoTimestamp: String,
    val createdBy: String,
    val updatedAtIso: String? = null,
    val deleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class DailySalesFile(
    val monthKey: String,
    val year: Int,
    val month: Int,
    val sales: List<SaleRecord> = emptyList(),
    val updatedAtIso: String = ""
)

// ==================== BA BU CUSTOMERS & TRANSACTIONS ====================

@JsonClass(generateAdapter = true)
data class CustomerProfile(
    val phoneNumber: String,
    val customerName: String,
    val vengOrKhua: String = "",
    val notes: String = "",
    val totalCreditPaise: Long = 0L,
    val totalPaidPaise: Long = 0L,
    val currentPendingBaPaise: Long = 0L,
    val lastTransactionDateDisplay: String = "",
    val lastTransactionIso: String = "",
    val createdAtIso: String,
    val updatedAtIso: String
)

@JsonClass(generateAdapter = true)
data class BaItemDetail(
    val itemName: String,
    val pricePaise: Long,
    val quantity: Int,
    val itemTotalPaise: Long
)

@JsonClass(generateAdapter = true)
data class BaTransaction(
    val transactionId: String,
    val clientRequestId: String = "",
    val customerPhoneNumber: String,
    val customerName: String,
    val type: BaTransactionType,
    val items: List<BaItemDetail> = emptyList(),
    val previousBalancePaise: Long,
    val amountPaise: Long,
    val updatedBalancePaise: Long,
    val paymentMethod: BaPaymentMethod? = null,
    val percentageUsed: Double? = null,
    val note: String = "",
    val day: Int,
    val month: Int,
    val year: Int,
    val dateKey: String,
    val dateDisplay: String,
    val timeDisplay: String,
    val isoTimestamp: String,
    val createdBy: String,
    val reversed: Boolean = false,
    val reversalReason: String? = null,
    val correctedFromId: String? = null
)

@JsonClass(generateAdapter = true)
data class BaTransactionsFile(
    val monthKey: String,
    val year: Int,
    val month: Int,
    val transactions: List<BaTransaction> = emptyList(),
    val updatedAtIso: String = ""
)

// ==================== MONTHLY ARCHIVES & YEARLY REPORTS ====================

@JsonClass(generateAdapter = true)
data class DailyTotalEntry(
    val dateKey: String,
    val dateDisplay: String,
    val totalSalesPaise: Long,
    val cashSalesPaise: Long,
    val otherOfflineSalesPaise: Long,
    val creditSalesPaise: Long,
    val transactionCount: Int,
    val itemsSoldCount: Int
)

@JsonClass(generateAdapter = true)
data class ItemSummaryEntry(
    val itemName: String,
    val quantitySold: Int,
    val totalAmountPaise: Long
)

@JsonClass(generateAdapter = true)
data class ArchiveCorrectionRecord(
    val correctionId: String,
    val createdAtIso: String,
    val createdBy: String,
    val description: String,
    val adjustmentAmountPaise: Long,
    val targetPaymentMode: PaymentMode
)

@JsonClass(generateAdapter = true)
data class MonthlyArchiveFile(
    val year: Int,
    val month: Int,
    val monthName: String,
    val monthKey: String,
    val archivedAtIso: String,
    val archivedBy: String,
    val totalSalesPaise: Long,
    val cashSalesPaise: Long,
    val otherOfflineSalesPaise: Long,
    val creditSalesPaise: Long,
    val transactionCount: Int,
    val totalProductsSold: Int,
    val dailyTotals: List<DailyTotalEntry>,
    val itemSummary: List<ItemSummaryEntry>,
    val sales: List<SaleRecord>,
    val corrections: List<ArchiveCorrectionRecord> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MonthlySummaryRow(
    val month: Int,
    val monthName: String,
    val totalSalesPaise: Long,
    val cashSalesPaise: Long,
    val otherOfflineSalesPaise: Long,
    val creditSalesPaise: Long,
    val baAddedPaise: Long,
    val baCollectedPaise: Long,
    val transactionCount: Int
)

@JsonClass(generateAdapter = true)
data class YearlyReportFile(
    val year: Int,
    val updatedAtIso: String,
    val totalYearlySalesPaise: Long,
    val cashSalesPaise: Long,
    val otherOfflineSalesPaise: Long,
    val creditSalesPaise: Long,
    val totalBaAddedPaise: Long,
    val totalBaCollectedPaise: Long,
    val monthlyBreakdown: List<MonthlySummaryRow>,
    val bestSellingItems: List<ItemSummaryEntry>
)

// ==================== BACKUPS & AUDIT LOGS ====================

@JsonClass(generateAdapter = true)
data class BackupMetadata(
    val backupId: String,
    val backupType: BackupType,
    val shopId: String,
    val shopName: String,
    val repoFullName: String,
    val createdAtIso: String,
    val dateKey: String,
    val createdBy: String,
    val customerCount: Int,
    val activeSalesCount: Int,
    val baTransactionsCount: Int,
    val checksumSha256: String,
    val filePath: String,
    val githubCommitSha: String? = null
)

@JsonClass(generateAdapter = true)
data class BackupFilePayload(
    val metadata: BackupMetadata,
    val shopConfig: ShopConfig,
    val settings: ShopSettings,
    val users: ShopUsersFile,
    val categories: CategoriesFile,
    val backupSettings: BackupSettings,
    val closingHistory: ClosingHistoryFile,
    val customers: List<CustomerProfile>,
    val activeSalesFiles: List<DailySalesFile>,
    val baTransactionFiles: List<BaTransactionsFile>,
    val monthlyArchives: List<MonthlyArchiveFile>,
    val yearlyReports: List<YearlyReportFile>
)

@JsonClass(generateAdapter = true)
data class AuditLogEntry(
    val auditId: String,
    val userId: String,
    val action: String,
    val recordId: String,
    val dateKey: String,
    val dateDisplay: String,
    val isoTimestamp: String,
    val description: String,
    val previousValue: String? = null,
    val updatedValue: String? = null
)

@JsonClass(generateAdapter = true)
data class AuditLogFile(
    val monthKey: String,
    val entries: List<AuditLogEntry> = emptyList()
)

// ==================== COMPUTED FINANCIAL REPORT MODELS ====================

data class FinancialReportSummary(
    val title: String,
    val subtitle: String,
    val totalSalesPaise: Long,
    val cashSalesPaise: Long,
    val otherOfflineSalesPaise: Long,
    val creditSalesPaise: Long,
    val baAddedPaise: Long,
    val baCollectedPaise: Long,
    val remainingPendingBaPaise: Long,
    val numberOfSales: Int,
    val numberOfActiveCustomers: Int,
    val numberOfPayments: Int,
    val bestSellingItems: List<ItemSummaryEntry>,
    val monthlyComparison: List<MonthlySummaryRow> = emptyList()
)

data class DashboardMetrics(
    val todayTotalSalesPaise: Long = 0L,
    val todayCashSalesPaise: Long = 0L,
    val todayOtherOfflinePaise: Long = 0L,
    val todayCreditSalesPaise: Long = 0L,
    val todayBaCollectionPaise: Long = 0L,
    val totalPendingBaPaise: Long = 0L,
    val currentMonthSalesPaise: Long = 0L,
    val currentYearSalesPaise: Long = 0L,
    val recentSales: List<SaleRecord> = emptyList(),
    val recentCredits: List<BaTransaction> = emptyList(),
    val recentPayments: List<BaTransaction> = emptyList(),
    val topPendingCustomers: List<CustomerProfile> = emptyList(),
    val monthlyDailyTotals: List<DailyTotalEntry> = emptyList()
)

// ==================== IN-MEMORY SHOP SNAPSHOT ====================

data class ShopSnapshot(
    val binding: LocalShopBinding,
    val shopConfig: ShopConfig,
    val settings: ShopSettings,
    val users: ShopUsersFile,
    val categories: CategoriesFile,
    val backupSettings: BackupSettings,
    val closingHistory: ClosingHistoryFile,
    val currentActiveSales: DailySalesFile,
    val allActiveSalesFiles: List<DailySalesFile>,
    val customers: List<CustomerProfile>,
    val baTransactions: List<BaTransaction>,
    val yearlyReport: YearlyReportFile?,
    val recentAuditLogs: List<AuditLogEntry>,
    val currentUserRole: UserRole,
    val lastSyncedAtIso: String,
    val lastSyncSuccess: Boolean = true,
    val syncErrorMessage: String? = null
)
