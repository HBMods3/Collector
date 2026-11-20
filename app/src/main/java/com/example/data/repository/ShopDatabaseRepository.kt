package com.example.data.repository

import com.example.data.github.GitHubException
import com.example.data.github.GitHubStoragePort
import com.example.data.github.RemoteCommitEntry
import com.example.data.github.RemoteDirEntry
import com.example.data.github.RemoteFileEntry
import com.example.domain.AccountingCalculator
import com.example.domain.BackupService
import com.example.domain.MonthlyClosingResult
import com.example.domain.MonthlyClosingService
import com.example.domain.model.ArchiveCorrectionRecord
import com.example.domain.model.AuditLogEntry
import com.example.domain.model.AuditLogFile
import com.example.domain.model.AuthorizedUser
import com.example.domain.model.BaItemDetail
import com.example.domain.model.BaPaymentMethod
import com.example.domain.model.BaTransaction
import com.example.domain.model.BaTransactionType
import com.example.domain.model.BaTransactionsFile
import com.example.domain.model.BackupSettings
import com.example.domain.model.BackupType
import com.example.domain.model.CategoriesFile
import com.example.domain.model.ClosingHistoryFile
import com.example.domain.model.ClosingStatus
import com.example.domain.model.CustomerProfile
import com.example.domain.model.DailySalesFile
import com.example.domain.model.LocalShopBinding
import com.example.domain.model.MonthlyArchiveFile
import com.example.domain.model.PaymentMode
import com.example.domain.model.QuickItemPreset
import com.example.domain.model.SaleLineItem
import com.example.domain.model.SaleRecord
import com.example.domain.model.ShopConfig
import com.example.domain.model.ShopSettings
import com.example.domain.model.ShopSnapshot
import com.example.domain.model.ShopUsersFile
import com.example.domain.model.UserRole
import com.example.domain.model.YearlyReportFile
import com.example.utils.DateUtils
import com.example.utils.IdGenerator
import com.example.utils.MoneyUtils
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap

class ShopDatabaseRepository(
    val storage: GitHubStoragePort,
    val codec: ShopJsonCodec
) {
    val closingService = MonthlyClosingService(storage, codec)
    val backupService = BackupService(storage, codec)

    private val snapshotCache = ConcurrentHashMap<String, ShopSnapshot>()

    fun getCachedSnapshot(shopId: String): ShopSnapshot? = snapshotCache[shopId]

    fun clearShopCache(shopId: String? = null) {
        if (shopId == null) snapshotCache.clear() else snapshotCache.remove(shopId)
    }

    fun normalizePhone(rawPhone: String): String {
        val digits = rawPhone.filter { it.isDigit() }
        return when {
            digits.length == 10 -> "91$digits"
            digits.startsWith("91") && digits.length == 12 -> digits
            else -> digits
        }
    }

    suspend fun initializeNewShopDatabase(
        token: String,
        binding: LocalShopBinding,
        actorLogin: String,
        actorDisplayName: String,
        addressOrVeng: String,
        contactPhone: String
    ): ShopSnapshot {
        val owner = binding.repoOwner
        val repo = binding.repoName
        val nowParts = DateUtils.nowParts(binding.timezoneId)

        return storage.withRepoLock(owner, repo) {
            val shopConfig = ShopConfig(
                shopId = binding.shopId,
                shopName = binding.shopName,
                addressOrVeng = addressOrVeng,
                contactPhone = contactPhone,
                ownerGithubLogin = actorLogin,
                repoOwner = owner,
                repoName = repo,
                timezoneId = binding.timezoneId,
                createdAtIso = nowParts.isoTimestamp,
                updatedAtIso = nowParts.isoTimestamp
            )
            val settings = ShopSettings(updatedAtIso = nowParts.isoTimestamp)
            val users = ShopUsersFile(
                ownerLogin = actorLogin,
                users = listOf(
                    AuthorizedUser(
                        githubLogin = actorLogin,
                        displayName = actorDisplayName.ifBlank { actorLogin },
                        role = UserRole.OWNER,
                        active = true,
                        addedAtIso = nowParts.isoTimestamp,
                        addedBy = actorLogin
                    )
                )
            )
            val categories = CategoriesFile()
            val backupSettings = BackupSettings()
            val closingHistory = ClosingHistoryFile()
            val currentSales = DailySalesFile(
                monthKey = nowParts.monthKey,
                year = nowParts.year,
                month = nowParts.month,
                sales = emptyList(),
                updatedAtIso = nowParts.isoTimestamp
            )
            val currentBaTx = BaTransactionsFile(
                monthKey = nowParts.monthKey,
                year = nowParts.year,
                month = nowParts.month,
                transactions = emptyList(),
                updatedAtIso = nowParts.isoTimestamp
            )
            val yearly = AccountingCalculator.computeYearlyReport(
                year = nowParts.year,
                updatedAtIso = nowParts.isoTimestamp,
                activeSalesFiles = listOf(currentSales),
                monthlyArchives = emptyList(),
                baTransactions = emptyList()
            )
            val initAudit = AuditLogFile(
                monthKey = nowParts.monthKey,
                entries = listOf(
                    AuditLogEntry(
                        auditId = IdGenerator.auditId(),
                        userId = actorLogin,
                        action = "SHOP_INITIALIZED",
                        recordId = binding.shopId,
                        dateKey = nowParts.dateKey,
                        dateDisplay = nowParts.dateDisplay,
                        isoTimestamp = nowParts.isoTimestamp,
                        description = "Initialized SLR Note Bu database for ${binding.shopName}"
                    )
                )
            )

            val readme = """
                # SLR Note Bu — Shop Database
                - **Shop:** ${binding.shopName}
                - **Shop ID:** ${binding.shopId}
                - **Database Owner:** @$actorLogin
                - **Currency:** INR (₹) stored in integer paise for exact financial accuracy
                - **Timezone:** ${binding.timezoneId}

                Managed securely by **SLR Note Bu**. Do not manually alter balances without creating an audit entry.
            """.trimIndent()

            // Create core config, active folders, and .gitkeep markers
            putOrUpdate(token, owner, repo, ShopPaths.SHOP_CONFIG, codec.shopConfigToJson(shopConfig), "Init shop.json")
            putOrUpdate(token, owner, repo, ShopPaths.SETTINGS, codec.settingsToJson(settings), "Init settings.json")
            putOrUpdate(token, owner, repo, ShopPaths.USERS, codec.usersToJson(users), "Init users.json")
            putOrUpdate(token, owner, repo, ShopPaths.CATEGORIES, codec.categoriesToJson(categories), "Init categories.json")
            putOrUpdate(token, owner, repo, ShopPaths.BACKUP_SETTINGS, codec.backupSettingsToJson(backupSettings), "Init backup-settings.json")
            putOrUpdate(token, owner, repo, ShopPaths.CLOSING_HISTORY, codec.closingHistoryToJson(closingHistory), "Init closing-history.json")
            putOrUpdate(token, owner, repo, ShopPaths.activeDailySalesFile(nowParts.year, nowParts.month), codec.dailySalesToJson(currentSales), "Init active daily sales")
            putOrUpdate(token, owner, repo, ShopPaths.baTransactionsFile(nowParts.year, nowParts.month), codec.baTransactionsToJson(currentBaTx), "Init Ba transactions")
            putOrUpdate(token, owner, repo, ShopPaths.yearlyReportFile(nowParts.year), codec.yearlyReportToJson(yearly), "Init yearly report")
            putOrUpdate(token, owner, repo, ShopPaths.auditLogFile(nowParts.year, nowParts.month), codec.auditLogToJson(initAudit), "Init audit log")
            putOrUpdate(token, owner, repo, ShopPaths.README, readme, "Init SLR Note Bu README.md", validateJson = false)

            val keepFolders = listOf(
                "${ShopPaths.CUSTOMERS_DIR}/.gitkeep",
                "${ShopPaths.MONTHLY_ARCHIVE_DIR}/${nowParts.year}/.gitkeep",
                "${ShopPaths.REPORTS_DAILY_DIR}/.gitkeep",
                "${ShopPaths.REPORTS_MONTHLY_DIR}/.gitkeep",
                "${ShopPaths.REPORTS_YEARLY_DIR}/.gitkeep",
                "${ShopPaths.dailyBackupDir(nowParts.year, nowParts.month)}/.gitkeep",
                "${ShopPaths.monthlyBackupDir(nowParts.year)}/.gitkeep",
                "${ShopPaths.MANUAL_BACKUPS_DIR}/.gitkeep",
                "${ShopPaths.PRE_RESTORE_BACKUPS_DIR}/.gitkeep",
                "${ShopPaths.ANNUAL_BACKUPS_DIR}/.gitkeep"
            )
            keepFolders.forEach { path ->
                if (storage.getFile(token, owner, repo, path) == null) {
                    storage.putFile(token, owner, repo, path, "", "Init folder $path", null, validateJson = false)
                }
            }

            loadShopSnapshot(token, binding, actorLogin)
        }
    }

    suspend fun inspectExistingShopConfig(
        token: String,
        owner: String,
        repo: String
    ): ShopConfig? {
        return storage.getFile(token, owner, repo, ShopPaths.SHOP_CONFIG)
            ?.content?.let { codec.shopConfigFromJson(it) }
    }

    suspend fun loadShopSnapshot(
        token: String,
        binding: LocalShopBinding,
        actorLogin: String
    ): ShopSnapshot {
        val owner = binding.repoOwner
        val repo = binding.repoName
        val nowParts = DateUtils.nowParts(binding.timezoneId)

        val shopConfig = storage.getFile(token, owner, repo, ShopPaths.SHOP_CONFIG)
            ?.content?.let { codec.shopConfigFromJson(it) }
            ?: throw IllegalStateException("Repository $owner/$repo does not contain SLR-Note-Bu/config/shop.json")
        val settings = storage.getFile(token, owner, repo, ShopPaths.SETTINGS)
            ?.content?.let { codec.settingsFromJson(it) } ?: ShopSettings()
        val users = storage.getFile(token, owner, repo, ShopPaths.USERS)
            ?.content?.let { codec.usersFromJson(it) }
            ?: ShopUsersFile(ownerLogin = shopConfig.ownerGithubLogin, users = emptyList())
        val categories = storage.getFile(token, owner, repo, ShopPaths.CATEGORIES)
            ?.content?.let { codec.categoriesFromJson(it) } ?: CategoriesFile()
        val backupSettings = storage.getFile(token, owner, repo, ShopPaths.BACKUP_SETTINGS)
            ?.content?.let { codec.backupSettingsFromJson(it) } ?: BackupSettings()
        val closingHistory = storage.getFile(token, owner, repo, ShopPaths.CLOSING_HISTORY)
            ?.content?.let { codec.closingHistoryFromJson(it) } ?: ClosingHistoryFile()

        // Active sales files
        val activeEntries = storage.listDirectory(token, owner, repo, ShopPaths.DAILY_SALES_ACTIVE_DIR)
            .filter { !it.isDirectory && it.name.endsWith(".json") }
        val allActiveSales = activeEntries.mapNotNull { entry ->
            storage.getFile(token, owner, repo, entry.path)?.content?.let { codec.dailySalesFromJson(it) }
        }
        val currentMonthSales = allActiveSales.firstOrNull { it.monthKey == nowParts.monthKey }
            ?: DailySalesFile(monthKey = nowParts.monthKey, year = nowParts.year, month = nowParts.month)

        // Customers
        val customers = storage.listDirectory(token, owner, repo, ShopPaths.CUSTOMERS_DIR)
            .filter { !it.isDirectory && it.name.endsWith(".json") }
            .mapNotNull { entry ->
                storage.getFile(token, owner, repo, entry.path)?.content?.let { codec.customerFromJson(it) }
            }
            .sortedBy { it.customerName.lowercase() }

        // Ba transactions across recent/all months for this shop
        val baTransactions = loadAllBaTransactions(token, owner, repo)

        // Yearly report for current year
        val yearlyReport = storage.getFile(token, owner, repo, ShopPaths.yearlyReportFile(nowParts.year))
            ?.content?.let { codec.yearlyReportFromJson(it) }

        // Recent audit logs
        val auditLogs = storage.getFile(token, owner, repo, ShopPaths.auditLogFile(nowParts.year, nowParts.month))
            ?.content?.let { codec.auditLogFromJson(it)?.entries } ?: emptyList()

        val userRole = resolveRole(users, shopConfig, actorLogin)

        val snapshot = ShopSnapshot(
            binding = binding.copy(shopName = shopConfig.shopName, timezoneId = shopConfig.timezoneId),
            shopConfig = shopConfig,
            settings = settings,
            users = users,
            categories = categories,
            backupSettings = backupSettings,
            closingHistory = closingHistory,
            currentActiveSales = currentMonthSales,
            allActiveSalesFiles = allActiveSales,
            customers = customers,
            baTransactions = baTransactions,
            yearlyReport = yearlyReport,
            recentAuditLogs = auditLogs,
            currentUserRole = userRole,
            lastSyncedAtIso = nowParts.isoTimestamp,
            lastSyncSuccess = true
        )
        snapshotCache[binding.shopId] = snapshot
        return snapshot
    }

    private fun resolveRole(users: ShopUsersFile, config: ShopConfig, actorLogin: String): UserRole {
        if (config.ownerGithubLogin.equals(actorLogin, ignoreCase = true) ||
            users.ownerLogin.equals(actorLogin, ignoreCase = true)
        ) {
            return UserRole.OWNER
        }
        val match = users.users.firstOrNull { it.githubLogin.equals(actorLogin, ignoreCase = true) && it.active }
        return match?.role ?: UserRole.VIEWER
    }

    private fun requirePermission(snapshot: ShopSnapshot, check: (UserRole) -> Boolean, actionLabel: String) {
        if (!check(snapshot.currentUserRole)) {
            throw SecurityException("Permission denied: ${snapshot.currentUserRole.label} cannot $actionLabel.")
        }
    }

    // ==================== DAILY SALES ====================

    suspend fun addSale(
        token: String,
        binding: LocalShopBinding,
        actorLogin: String,
        items: List<SaleLineItem>,
        paymentMode: PaymentMode,
        note: String,
        saleDate: LocalDate,
        saleTime: LocalTime,
        linkedCustomerPhone: String? = null,
        clientRequestId: String = IdGenerator.requestId()
    ): ShopSnapshot {
        val current = snapshotCache[binding.shopId] ?: loadShopSnapshot(token, binding, actorLogin)
        requirePermission(current, { it.canWriteTransactions() }, "add sales")
        require(items.isNotEmpty()) { "Add at least one item to the sale." }

        val owner = binding.repoOwner
        val repo = binding.repoName
        val tsParts = DateUtils.partsForLocalDateTime(saleDate, saleTime, current.shopConfig.timezoneId)
        val normalizedItems = items.map {
            require(it.itemName.isNotBlank()) { "Item name cannot be blank." }
            require(it.pricePaise >= 0L && it.quantity > 0) { "Invalid price or quantity." }
            it.copy(itemTotalPaise = it.pricePaise * it.quantity)
        }
        val grandTotal = normalizedItems.sumOf { it.itemTotalPaise }
        val saleId = IdGenerator.saleId()

        // Optional linked customer Ba transaction if CREDIT_BA and customer selected
        var linkedBaId: String? = null
        var linkedCustName: String? = null
        val normalizedPhone = linkedCustomerPhone?.takeIf { it.isNotBlank() }?.let { normalizePhone(it) }
        if (paymentMode == PaymentMode.CREDIT_BA && normalizedPhone != null) {
            val baItems = normalizedItems.map {
                BaItemDetail(it.itemName, it.pricePaise, it.quantity, it.itemTotalPaise)
            }
            val cust = current.customers.firstOrNull { it.phoneNumber == normalizedPhone }
                ?: throw IllegalArgumentException("Linked customer not found.")
            linkedCustName = cust.customerName
            linkedBaId = IdGenerator.baCreditId()
            addBaCreditInternal(
                token = token,
                binding = binding,
                actorLogin = actorLogin,
                customerPhone = normalizedPhone,
                items = baItems,
                note = note.ifBlank { "Credit sale $saleId" },
                date = saleDate,
                time = saleTime,
                txId = linkedBaId,
                clientRequestId = "ba-$clientRequestId"
            )
        }

        val record = SaleRecord(
            saleId = saleId,
            clientRequestId = clientRequestId,
            items = normalizedItems,
            grandTotalPaise = grandTotal,
            paymentMode = paymentMode,
            linkedCustomerPhone = normalizedPhone,
            linkedCustomerName = linkedCustName,
            linkedBaTransactionId = linkedBaId,
            note = note.trim(),
            day = tsParts.day,
            month = tsParts.month,
            year = tsParts.year,
            dateKey = tsParts.dateKey,
            dateDisplay = tsParts.dateDisplay,
            timeDisplay = tsParts.timeDisplay,
            isoTimestamp = tsParts.isoTimestamp,
            createdBy = actorLogin
        )

        storage.withRepoLock(owner, repo) {
            storage.updateJsonFileWithRetry(
                token = token,
                owner = owner,
                repo = repo,
                path = ShopPaths.activeDailySalesFile(tsParts.year, tsParts.month),
                commitMessage = "SLR Note Bu: Add Sale $saleId (${MoneyUtils.formatInr(grandTotal)})"
            ) { raw ->
                val existing = raw?.let { codec.dailySalesFromJson(it) }
                    ?: DailySalesFile(monthKey = tsParts.monthKey, year = tsParts.year, month = tsParts.month)
                // Duplicate prevention
                if (existing.sales.any { it.clientRequestId.isNotBlank() && it.clientRequestId == clientRequestId }) {
                    return@updateJsonFileWithRetry codec.dailySalesToJson(existing) to Unit
                }
                val updated = existing.copy(
                    sales = listOf(record) + existing.sales,
                    updatedAtIso = DateUtils.nowParts(current.shopConfig.timezoneId).isoTimestamp
                )
                codec.dailySalesToJson(updated) to Unit
            }

            appendAuditLog(
                token, owner, repo, current.shopConfig.timezoneId,
                AuditLogEntry(
                    auditId = IdGenerator.auditId(),
                    userId = actorLogin,
                    action = "NEW_SALE",
                    recordId = saleId,
                    dateKey = tsParts.dateKey,
                    dateDisplay = tsParts.dateDisplay,
                    isoTimestamp = DateUtils.nowParts(current.shopConfig.timezoneId).isoTimestamp,
                    description = "Added ${paymentMode.label} sale of ${MoneyUtils.formatInr(grandTotal)} (${normalizedItems.size} items)",
                    updatedValue = MoneyUtils.formatInr(grandTotal)
                )
            )
        }
        return loadShopSnapshot(token, binding, actorLogin)
    }

    suspend fun editSale(
        token: String,
        binding: LocalShopBinding,
        actorLogin: String,
        originalSale: SaleRecord,
        updatedItems: List<SaleLineItem>,
        updatedPaymentMode: PaymentMode,
        updatedNote: String
    ): ShopSnapshot {
        val current = snapshotCache[binding.shopId] ?: loadShopSnapshot(token, binding, actorLogin)
        requirePermission(current, { it.canWriteTransactions() }, "edit sales")
        require(updatedItems.isNotEmpty()) { "Sale must contain at least one item." }

        val owner = binding.repoOwner
        val repo = binding.repoName
        val nowParts = DateUtils.nowParts(current.shopConfig.timezoneId)
        val normalizedItems = updatedItems.map {
            it.copy(itemTotalPaise = it.pricePaise * it.quantity)
        }
        val newGrandTotal = normalizedItems.sumOf { it.itemTotalPaise }
        val updatedRecord = originalSale.copy(
            items = normalizedItems,
            grandTotalPaise = newGrandTotal,
            paymentMode = updatedPaymentMode,
            note = updatedNote.trim(),
            updatedAtIso = nowParts.isoTimestamp
        )

        storage.withRepoLock(owner, repo) {
            storage.updateJsonFileWithRetry(
                token = token,
                owner = owner,
                repo = repo,
                path = ShopPaths.activeDailySalesFile(originalSale.year, originalSale.month),
                commitMessage = "SLR Note Bu: Edit Sale ${originalSale.saleId}"
            ) { raw ->
                val file = raw?.let { codec.dailySalesFromJson(it) }
                    ?: throw IllegalStateException("Sales file not found.")
                val list = file.sales.map { if (it.saleId == originalSale.saleId) updatedRecord else it }
                codec.dailySalesToJson(file.copy(sales = list, updatedAtIso = nowParts.isoTimestamp)) to Unit
            }

            appendAuditLog(
                token, owner, repo, current.shopConfig.timezoneId,
                AuditLogEntry(
                    auditId = IdGenerator.auditId(),
                    userId = actorLogin,
                    action = "EDIT_SALE",
                    recordId = originalSale.saleId,
                    dateKey = nowParts.dateKey,
                    dateDisplay = nowParts.dateDisplay,
                    isoTimestamp = nowParts.isoTimestamp,
                    description = "Edited sale ${originalSale.saleId}",
                    previousValue = "${originalSale.paymentMode.label} ${MoneyUtils.formatInr(originalSale.grandTotalPaise)}",
                    updatedValue = "${updatedPaymentMode.label} ${MoneyUtils.formatInr(newGrandTotal)}"
                )
            )
        }
        return loadShopSnapshot(token, binding, actorLogin)
    }

    suspend fun deleteSale(
        token: String,
        binding: LocalShopBinding,
        actorLogin: String,
        sale: SaleRecord
    ): ShopSnapshot {
        val current = snapshotCache[binding.shopId] ?: loadShopSnapshot(token, binding, actorLogin)
        requirePermission(current, { it.canDeleteOrReverse() }, "delete sales")
        val owner = binding.repoOwner
        val repo = binding.repoName
        val nowParts = DateUtils.nowParts(current.shopConfig.timezoneId)

        storage.withRepoLock(owner, repo) {
            storage.updateJsonFileWithRetry(
                token = token,
                owner = owner,
                repo = repo,
                path = ShopPaths.activeDailySalesFile(sale.year, sale.month),
                commitMessage = "SLR Note Bu: Delete Sale ${sale.saleId}"
            ) { raw ->
                val file = raw?.let { codec.dailySalesFromJson(it) }
                    ?: throw IllegalStateException("Sales file not found.")
                val list = file.sales.map {
                    if (it.saleId == sale.saleId) it.copy(deleted = true, updatedAtIso = nowParts.isoTimestamp) else it
                }
                codec.dailySalesToJson(file.copy(sales = list, updatedAtIso = nowParts.isoTimestamp)) to Unit
            }

            appendAuditLog(
                token, owner, repo, current.shopConfig.timezoneId,
                AuditLogEntry(
                    auditId = IdGenerator.auditId(),
                    userId = actorLogin,
                    action = "DELETE_SALE",
                    recordId = sale.saleId,
                    dateKey = nowParts.dateKey,
                    dateDisplay = nowParts.dateDisplay,
                    isoTimestamp = nowParts.isoTimestamp,
                    description = "Deleted sale ${sale.saleId} (${MoneyUtils.formatInr(sale.grandTotalPaise)})",
                    previousValue = MoneyUtils.formatInr(sale.grandTotalPaise),
                    updatedValue = "DELETED"
                )
            )
        }
        return loadShopSnapshot(token, binding, actorLogin)
    }

    // ==================== BA BU CUSTOMERS ====================

    suspend fun addCustomer(
        token: String,
        binding: LocalShopBinding,
        actorLogin: String,
        customerName: String,
        rawPhoneNumber: String,
        vengOrKhua: String,
        initialBaPaise: Long,
        notes: String
    ): ShopSnapshot {
        val current = snapshotCache[binding.shopId] ?: loadShopSnapshot(token, binding, actorLogin)
        requirePermission(current, { it.canWriteTransactions() }, "add customers")

        val phone = normalizePhone(rawPhoneNumber)
        require(phone.length >= 10) { "Enter a valid phone number (at least 10 digits)." }
        require(customerName.isNotBlank()) { "Customer name is required." }
        require(initialBaPaise >= 0L) { "Initial Ba cannot be negative." }

        val owner = binding.repoOwner
        val repo = binding.repoName
        val customerPath = ShopPaths.customerFile(phone)
        val nowParts = DateUtils.nowParts(current.shopConfig.timezoneId)

        storage.withRepoLock(owner, repo) {
            if (storage.getFile(token, owner, repo, customerPath) != null) {
                throw IllegalArgumentException("A customer with phone $phone already exists.")
            }

            val profile = CustomerProfile(
                phoneNumber = phone,
                customerName = customerName.trim(),
                vengOrKhua = vengOrKhua.trim(),
                notes = notes.trim(),
                totalCreditPaise = 0L,
                totalPaidPaise = 0L,
                currentPendingBaPaise = 0L,
                lastTransactionDateDisplay = if (initialBaPaise > 0L) nowParts.dateDisplay else "",
                lastTransactionIso = if (initialBaPaise > 0L) nowParts.isoTimestamp else "",
                createdAtIso = nowParts.isoTimestamp,
                updatedAtIso = nowParts.isoTimestamp
            )
            storage.putFile(
                token = token,
                owner = owner,
                repo = repo,
                path = customerPath,
                content = codec.customerToJson(profile),
                commitMessage = "SLR Note Bu: Add Customer ${profile.customerName} ($phone)",
                expectedSha = null
            )

            appendAuditLog(
                token, owner, repo, current.shopConfig.timezoneId,
                AuditLogEntry(
                    auditId = IdGenerator.auditId(),
                    userId = actorLogin,
                    action = "NEW_CUSTOMER",
                    recordId = phone,
                    dateKey = nowParts.dateKey,
                    dateDisplay = nowParts.dateDisplay,
                    isoTimestamp = nowParts.isoTimestamp,
                    description = "Registered customer ${profile.customerName} ($phone, ${profile.vengOrKhua})"
                )
            )
        }

        if (initialBaPaise > 0L) {
            addBaCreditInternal(
                token = token,
                binding = binding,
                actorLogin = actorLogin,
                customerPhone = phone,
                items = listOf(BaItemDetail("Opening Ba Balance", initialBaPaise, 1, initialBaPaise)),
                note = "Initial Ba balance at registration",
                date = DateUtils.todayDate(current.shopConfig.timezoneId),
                time = DateUtils.nowTime(current.shopConfig.timezoneId),
                txId = IdGenerator.baCreditId(),
                clientRequestId = IdGenerator.requestId()
            )
        }
        return loadShopSnapshot(token, binding, actorLogin)
    }

    suspend fun updateCustomer(
        token: String,
        binding: LocalShopBinding,
        actorLogin: String,
        original: CustomerProfile,
        newName: String,
        newVeng: String,
        newNotes: String
    ): ShopSnapshot {
        val current = snapshotCache[binding.shopId] ?: loadShopSnapshot(token, binding, actorLogin)
        requirePermission(current, { it.canWriteTransactions() }, "edit customers")
        require(newName.isNotBlank()) { "Customer name cannot be blank." }
        val owner = binding.repoOwner
        val repo = binding.repoName
        val nowParts = DateUtils.nowParts(current.shopConfig.timezoneId)

        storage.withRepoLock(owner, repo) {
            storage.updateJsonFileWithRetry(
                token, owner, repo,
                ShopPaths.customerFile(original.phoneNumber),
                "SLR Note Bu: Update Customer ${original.phoneNumber}"
            ) { raw ->
                val latest = raw?.let { codec.customerFromJson(it) } ?: original
                val updated = latest.copy(
                    customerName = newName.trim(),
                    vengOrKhua = newVeng.trim(),
                    notes = newNotes.trim(),
                    updatedAtIso = nowParts.isoTimestamp
                )
                codec.customerToJson(updated) to Unit
            }
        }
        return loadShopSnapshot(token, binding, actorLogin)
    }

    // ==================== BA BU CREDIT & PAYMENTS ====================

    suspend fun addBaCredit(
        token: String,
        binding: LocalShopBinding,
        actorLogin: String,
        customerPhone: String,
        items: List<BaItemDetail>,
        note: String,
        date: LocalDate,
        time: LocalTime,
        clientRequestId: String = IdGenerator.requestId()
    ): ShopSnapshot {
        val current = snapshotCache[binding.shopId] ?: loadShopSnapshot(token, binding, actorLogin)
        requirePermission(current, { it.canWriteTransactions() }, "add Ba credit")
        addBaCreditInternal(
            token, binding, actorLogin, normalizePhone(customerPhone),
            items, note, date, time, IdGenerator.baCreditId(), clientRequestId
        )
        return loadShopSnapshot(token, binding, actorLogin)
    }

    private suspend fun addBaCreditInternal(
        token: String,
        binding: LocalShopBinding,
        actorLogin: String,
        customerPhone: String,
        items: List<BaItemDetail>,
        note: String,
        date: LocalDate,
        time: LocalTime,
        txId: String,
        clientRequestId: String
    ) {
        require(items.isNotEmpty()) { "Add at least one credit item." }
        val normalizedItems = items.map {
            require(it.itemName.isNotBlank() && it.pricePaise >= 0L && it.quantity > 0) {
                "Invalid credit item: ${it.itemName}"
            }
            it.copy(itemTotalPaise = it.pricePaise * it.quantity)
        }
        val creditTotal = normalizedItems.sumOf { it.itemTotalPaise }
        require(creditTotal > 0L) { "Credit amount must be greater than ₹0." }

        val owner = binding.repoOwner
        val repo = binding.repoName
        val tsParts = DateUtils.partsForLocalDateTime(date, time, binding.timezoneId)
        val nowParts = DateUtils.nowParts(binding.timezoneId)

        storage.withRepoLock(owner, repo) {
            val custEntry = storage.getFile(token, owner, repo, ShopPaths.customerFile(customerPhone))
                ?: throw IllegalStateException("Customer $customerPhone not found.")
            val customer = codec.customerFromJson(custEntry.content)
                ?: throw IllegalStateException("Invalid customer JSON for $customerPhone.")

            val txPath = ShopPaths.baTransactionsFile(tsParts.year, tsParts.month)
            var duplicateFound = false
            storage.updateJsonFileWithRetry(
                token, owner, repo, txPath,
                "SLR Note Bu: Add Ba Credit $txId for ${customer.customerName}"
            ) { raw ->
                val txFile = raw?.let { codec.baTransactionsFromJson(it) }
                    ?: BaTransactionsFile(monthKey = tsParts.monthKey, year = tsParts.year, month = tsParts.month)
                if (txFile.transactions.any { it.clientRequestId.isNotBlank() && it.clientRequestId == clientRequestId }) {
                    duplicateFound = true
                    return@updateJsonFileWithRetry codec.baTransactionsToJson(txFile) to Unit
                }
                val prevBa = customer.currentPendingBaPaise
                val newTx = BaTransaction(
                    transactionId = txId,
                    clientRequestId = clientRequestId,
                    customerPhoneNumber = customer.phoneNumber,
                    customerName = customer.customerName,
                    type = BaTransactionType.CREDIT,
                    items = normalizedItems,
                    previousBalancePaise = prevBa,
                    amountPaise = creditTotal,
                    updatedBalancePaise = prevBa + creditTotal,
                    note = note.trim(),
                    day = tsParts.day,
                    month = tsParts.month,
                    year = tsParts.year,
                    dateKey = tsParts.dateKey,
                    dateDisplay = tsParts.dateDisplay,
                    timeDisplay = tsParts.timeDisplay,
                    isoTimestamp = tsParts.isoTimestamp,
                    createdBy = actorLogin
                )
                codec.baTransactionsToJson(txFile.copy(transactions = txFile.transactions + newTx, updatedAtIso = nowParts.isoTimestamp)) to Unit
            }

            if (!duplicateFound) {
                recalculateAndSaveCustomer(token, owner, repo, customer, nowParts.isoTimestamp)
                appendAuditLog(
                    token, owner, repo, binding.timezoneId,
                    AuditLogEntry(
                        auditId = IdGenerator.auditId(),
                        userId = actorLogin,
                        action = "ADD_BA",
                        recordId = txId,
                        dateKey = tsParts.dateKey,
                        dateDisplay = tsParts.dateDisplay,
                        isoTimestamp = nowParts.isoTimestamp,
                        description = "Added ${MoneyUtils.formatInr(creditTotal)} Ba for ${customer.customerName}",
                        previousValue = MoneyUtils.formatInr(customer.currentPendingBaPaise),
                        updatedValue = MoneyUtils.formatInr(customer.currentPendingBaPaise + creditTotal)
                    )
                )
            }
        }
    }

    suspend fun recordBaPayment(
        token: String,
        binding: LocalShopBinding,
        actorLogin: String,
        customerPhone: String,
        paymentAmountPaise: Long,
        paymentMethod: BaPaymentMethod,
        percentageUsed: Double?,
        note: String,
        date: LocalDate,
        time: LocalTime,
        clientRequestId: String = IdGenerator.requestId()
    ): ShopSnapshot {
        val current = snapshotCache[binding.shopId] ?: loadShopSnapshot(token, binding, actorLogin)
        requirePermission(current, { it.canWriteTransactions() }, "receive Ba payment")

        val phone = normalizePhone(customerPhone)
        require(paymentAmountPaise > 0L) { "Payment amount must be greater than ₹0." }

        val owner = binding.repoOwner
        val repo = binding.repoName
        val tsParts = DateUtils.partsForLocalDateTime(date, time, current.shopConfig.timezoneId)
        val nowParts = DateUtils.nowParts(current.shopConfig.timezoneId)
        val payId = IdGenerator.baPaymentId()

        storage.withRepoLock(owner, repo) {
            val custEntry = storage.getFile(token, owner, repo, ShopPaths.customerFile(phone))
                ?: throw IllegalStateException("Customer $phone not found.")
            val customer = codec.customerFromJson(custEntry.content)
                ?: throw IllegalStateException("Invalid customer JSON.")

            if (paymentAmountPaise > customer.currentPendingBaPaise) {
                throw IllegalArgumentException(
                    "Payment (${MoneyUtils.formatInr(paymentAmountPaise)}) cannot exceed pending Ba (${MoneyUtils.formatInr(customer.currentPendingBaPaise)})."
                )
            }

            val txPath = ShopPaths.baTransactionsFile(tsParts.year, tsParts.month)
            var duplicateFound = false
            storage.updateJsonFileWithRetry(
                token, owner, repo, txPath,
                "SLR Note Bu: Receive Ba Payment $payId (${MoneyUtils.formatInr(paymentAmountPaise)})"
            ) { raw ->
                val txFile = raw?.let { codec.baTransactionsFromJson(it) }
                    ?: BaTransactionsFile(monthKey = tsParts.monthKey, year = tsParts.year, month = tsParts.month)
                if (txFile.transactions.any { it.clientRequestId.isNotBlank() && it.clientRequestId == clientRequestId }) {
                    duplicateFound = true
                    return@updateJsonFileWithRetry codec.baTransactionsToJson(txFile) to Unit
                }
                val prevBa = customer.currentPendingBaPaise
                val remaining = prevBa - paymentAmountPaise
                val newTx = BaTransaction(
                    transactionId = payId,
                    clientRequestId = clientRequestId,
                    customerPhoneNumber = customer.phoneNumber,
                    customerName = customer.customerName,
                    type = BaTransactionType.PAYMENT,
                    items = emptyList(),
                    previousBalancePaise = prevBa,
                    amountPaise = paymentAmountPaise,
                    updatedBalancePaise = remaining,
                    paymentMethod = paymentMethod,
                    percentageUsed = percentageUsed,
                    note = note.trim(),
                    day = tsParts.day,
                    month = tsParts.month,
                    year = tsParts.year,
                    dateKey = tsParts.dateKey,
                    dateDisplay = tsParts.dateDisplay,
                    timeDisplay = tsParts.timeDisplay,
                    isoTimestamp = tsParts.isoTimestamp,
                    createdBy = actorLogin
                )
                codec.baTransactionsToJson(txFile.copy(transactions = txFile.transactions + newTx, updatedAtIso = nowParts.isoTimestamp)) to Unit
            }

            if (!duplicateFound) {
                recalculateAndSaveCustomer(token, owner, repo, customer, nowParts.isoTimestamp)
                appendAuditLog(
                    token, owner, repo, current.shopConfig.timezoneId,
                    AuditLogEntry(
                        auditId = IdGenerator.auditId(),
                        userId = actorLogin,
                        action = "RECEIVE_BA_PAYMENT",
                        recordId = payId,
                        dateKey = tsParts.dateKey,
                        dateDisplay = tsParts.dateDisplay,
                        isoTimestamp = nowParts.isoTimestamp,
                        description = "Received ${MoneyUtils.formatInr(paymentAmountPaise)} (${paymentMethod.label}) from ${customer.customerName}",
                        previousValue = MoneyUtils.formatInr(customer.currentPendingBaPaise),
                        updatedValue = MoneyUtils.formatInr(customer.currentPendingBaPaise - paymentAmountPaise)
                    )
                )
            }
        }
        return loadShopSnapshot(token, binding, actorLogin)
    }

    suspend fun correctOrReverseBaTransaction(
        token: String,
        binding: LocalShopBinding,
        actorLogin: String,
        transaction: BaTransaction,
        newAmountPaise: Long?, // null = reverse completely; non-null = update amount
        reason: String
    ): ShopSnapshot {
        val current = snapshotCache[binding.shopId] ?: loadShopSnapshot(token, binding, actorLogin)
        requirePermission(current, { it.canDeleteOrReverse() }, "correct or reverse transactions")
        if (newAmountPaise != null) require(newAmountPaise > 0L) { "Corrected amount must be greater than ₹0." }

        val owner = binding.repoOwner
        val repo = binding.repoName
        val nowParts = DateUtils.nowParts(current.shopConfig.timezoneId)

        storage.withRepoLock(owner, repo) {
            val txPath = ShopPaths.baTransactionsFile(transaction.year, transaction.month)
            storage.updateJsonFileWithRetry(
                token, owner, repo, txPath,
                "SLR Note Bu: ${if (newAmountPaise == null) "Reverse" else "Correct"} Ba Tx ${transaction.transactionId}"
            ) { raw ->
                val txFile = raw?.let { codec.baTransactionsFromJson(it) }
                    ?: throw IllegalStateException("Transaction file missing.")
                val updatedList = txFile.transactions.map { tx ->
                    if (tx.transactionId != transaction.transactionId) tx
                    else if (newAmountPaise == null) {
                        tx.copy(reversed = true, reversalReason = reason.ifBlank { "Reversed by $actorLogin" })
                    } else {
                        val updatedItems = if (tx.type == BaTransactionType.CREDIT && tx.items.size == 1) {
                            listOf(tx.items.first().copy(pricePaise = newAmountPaise, quantity = 1, itemTotalPaise = newAmountPaise))
                        } else tx.items
                        tx.copy(
                            amountPaise = newAmountPaise,
                            items = updatedItems,
                            note = "${tx.note} [Corrected: ${reason.ifBlank { "Adjusted" }}]".trim()
                        )
                    }
                }
                codec.baTransactionsToJson(txFile.copy(transactions = updatedList, updatedAtIso = nowParts.isoTimestamp)) to Unit
            }

            val custEntry = storage.getFile(token, owner, repo, ShopPaths.customerFile(transaction.customerPhoneNumber))
            val customer = custEntry?.content?.let { codec.customerFromJson(it) }
            if (customer != null) {
                recalculateAndSaveCustomer(token, owner, repo, customer, nowParts.isoTimestamp)
            }

            appendAuditLog(
                token, owner, repo, current.shopConfig.timezoneId,
                AuditLogEntry(
                    auditId = IdGenerator.auditId(),
                    userId = actorLogin,
                    action = if (newAmountPaise == null) "REVERSE_TRANSACTION" else "EDIT_BA_TRANSACTION",
                    recordId = transaction.transactionId,
                    dateKey = nowParts.dateKey,
                    dateDisplay = nowParts.dateDisplay,
                    isoTimestamp = nowParts.isoTimestamp,
                    description = "${if (newAmountPaise == null) "Reversed" else "Corrected"} ${transaction.type.label} for ${transaction.customerName}: $reason",
                    previousValue = MoneyUtils.formatInr(transaction.amountPaise),
                    updatedValue = newAmountPaise?.let { MoneyUtils.formatInr(it) } ?: "REVERSED"
                )
            )
        }
        return loadShopSnapshot(token, binding, actorLogin)
    }

    private suspend fun recalculateAndSaveCustomer(
        token: String,
        owner: String,
        repo: String,
        customer: CustomerProfile,
        nowIso: String
    ) {
        val allTx = loadAllBaTransactions(token, owner, repo)
            .filter { it.customerPhoneNumber == customer.phoneNumber }
        val (recalcCustomer, recalcTx) = AccountingCalculator.recalculateCustomerLedger(customer, allTx, nowIso)

        // Persist updated running balances back to any affected monthly transaction files
        recalcTx.groupBy { it.year to it.month }.forEach { (ym, monthTxList) ->
            val path = ShopPaths.baTransactionsFile(ym.first, ym.second)
            val byId = monthTxList.associateBy { it.transactionId }
            storage.updateJsonFileWithRetry(
                token, owner, repo, path,
                "SLR Note Bu: Sync recalculated balances for ${customer.customerName}"
            ) { raw ->
                val file = raw?.let { codec.baTransactionsFromJson(it) }
                    ?: BaTransactionsFile(DateUtils.monthKey(ym.first, ym.second), ym.first, ym.second)
                val merged = file.transactions.map { byId[it.transactionId] ?: it }
                codec.baTransactionsToJson(file.copy(transactions = merged, updatedAtIso = nowIso)) to Unit
            }
        }

        storage.updateJsonFileWithRetry(
            token, owner, repo,
            ShopPaths.customerFile(customer.phoneNumber),
            "SLR Note Bu: Update balance for ${customer.customerName}"
        ) {
            codec.customerToJson(recalcCustomer) to Unit
        }
    }

    // ==================== MONTHLY ARCHIVE & REPORTS ====================

    suspend fun loadMonthlyArchive(
        token: String,
        binding: LocalShopBinding,
        year: Int,
        month: Int
    ): MonthlyArchiveFile? {
        return storage.getFile(token, binding.repoOwner, binding.repoName, ShopPaths.monthlyArchiveFile(year, month))
            ?.content?.let { codec.monthlyArchiveFromJson(it) }
    }

    suspend fun loadYearlyReport(
        token: String,
        binding: LocalShopBinding,
        year: Int
    ): YearlyReportFile? {
        return storage.getFile(token, binding.repoOwner, binding.repoName, ShopPaths.yearlyReportFile(year))
            ?.content?.let { codec.yearlyReportFromJson(it) }
    }

    suspend fun addArchiveCorrection(
        token: String,
        binding: LocalShopBinding,
        actorLogin: String,
        year: Int,
        month: Int,
        description: String,
        adjustmentPaise: Long,
        paymentMode: PaymentMode
    ): MonthlyArchiveFile {
        val current = snapshotCache[binding.shopId] ?: loadShopSnapshot(token, binding, actorLogin)
        requirePermission(current, { it.canRunBackupOrClose() }, "create archive corrections")
        val owner = binding.repoOwner
        val repo = binding.repoName
        val nowParts = DateUtils.nowParts(current.shopConfig.timezoneId)
        val corr = ArchiveCorrectionRecord(
            correctionId = IdGenerator.correctionId(),
            createdAtIso = nowParts.isoTimestamp,
            createdBy = actorLogin,
            description = description.trim(),
            adjustmentAmountPaise = adjustmentPaise,
            targetPaymentMode = paymentMode
        )

        val updatedArchive = storage.withRepoLock(owner, repo) {
            val (_, archive) = storage.updateJsonFileWithRetry(
                token, owner, repo,
                ShopPaths.monthlyArchiveFile(year, month),
                "SLR Note Bu: Archive correction ${corr.correctionId}"
            ) { raw ->
                val arch = raw?.let { codec.monthlyArchiveFromJson(it) }
                    ?: throw IllegalStateException("Archive not found for $year-$month.")
                val updated = arch.copy(corrections = arch.corrections + corr)
                codec.monthlyArchiveToJson(updated) to updated
            }
            closingService.updateYearlyReport(token, owner, repo, year, nowParts.isoTimestamp)
            appendAuditLog(
                token, owner, repo, current.shopConfig.timezoneId,
                AuditLogEntry(
                    auditId = IdGenerator.auditId(),
                    userId = actorLogin,
                    action = "ARCHIVE_CORRECTION",
                    recordId = corr.correctionId,
                    dateKey = nowParts.dateKey,
                    dateDisplay = nowParts.dateDisplay,
                    isoTimestamp = nowParts.isoTimestamp,
                    description = "Added correction to $year-$month archive: $description (${MoneyUtils.formatInr(adjustmentPaise)})"
                )
            )
            archive
        }
        return updatedArchive
    }

    suspend fun runMonthlyClosingIfDue(
        token: String,
        binding: LocalShopBinding,
        actorLogin: String
    ): MonthlyClosingResult? {
        val today = DateUtils.todayDate(binding.timezoneId)
        val prevMonthDate = today.minusMonths(1)
        val prevYear = prevMonthDate.year
        val prevMonth = prevMonthDate.monthValue
        val prevKey = DateUtils.monthKey(prevYear, prevMonth)

        val settings = storage.getFile(token, binding.repoOwner, binding.repoName, ShopPaths.SETTINGS)
            ?.content?.let { codec.settingsFromJson(it) } ?: ShopSettings()
        if (!settings.autoMonthlyClosingEnabled) return null

        val history = storage.getFile(token, binding.repoOwner, binding.repoName, ShopPaths.CLOSING_HISTORY)
            ?.content?.let { codec.closingHistoryFromJson(it) } ?: ClosingHistoryFile()
        if (history.records.any { it.monthKey == prevKey && it.closingStatus == ClosingStatus.COMPLETED && it.verificationStatus }) {
            return null
        }

        val prevSales = storage.getFile(token, binding.repoOwner, binding.repoName, ShopPaths.activeDailySalesFile(prevYear, prevMonth))
            ?.content?.let { codec.dailySalesFromJson(it) }
        // Close if previous month file exists (even if 0 sales, initialize cleanly)
        if (prevSales == null && history.records.isEmpty()) {
            // Only auto-close if shop existed in prev month or has active file
            return null
        }
        return closingService.closeMonthAndArchive(token, binding, actorLogin, prevYear, prevMonth)
    }

    // ==================== SETTINGS, USERS & QUICK ITEMS ====================

    suspend fun updateShopAndBackupSettings(
        token: String,
        binding: LocalShopBinding,
        actorLogin: String,
        newShopName: String,
        newAddress: String,
        newPhone: String,
        newTimezone: String,
        newBackupTime: String,
        autoDailyBackup: Boolean,
        autoMonthlyClosing: Boolean,
        allowDbEdit: Boolean
    ): ShopSnapshot {
        val current = snapshotCache[binding.shopId] ?: loadShopSnapshot(token, binding, actorLogin)
        requirePermission(current, { it.canManageSecurity() }, "modify shop and security settings")
        val owner = binding.repoOwner
        val repo = binding.repoName
        val nowParts = DateUtils.nowParts(newTimezone)

        storage.withRepoLock(owner, repo) {
            storage.updateJsonFileWithRetry(token, owner, repo, ShopPaths.SHOP_CONFIG, "Update shop config") { raw ->
                val cfg = raw?.let { codec.shopConfigFromJson(it) } ?: current.shopConfig
                val updated = cfg.copy(
                    shopName = newShopName.trim(),
                    addressOrVeng = newAddress.trim(),
                    contactPhone = newPhone.trim(),
                    timezoneId = newTimezone.trim(),
                    updatedAtIso = nowParts.isoTimestamp
                )
                codec.shopConfigToJson(updated) to Unit
            }
            storage.updateJsonFileWithRetry(token, owner, repo, ShopPaths.SETTINGS, "Update shop settings") { raw ->
                val st = raw?.let { codec.settingsFromJson(it) } ?: current.settings
                val updated = st.copy(
                    allowDatabaseExplorerEdit = allowDbEdit,
                    autoMonthlyClosingEnabled = autoMonthlyClosing,
                    updatedAtIso = nowParts.isoTimestamp
                )
                codec.settingsToJson(updated) to Unit
            }
            storage.updateJsonFileWithRetry(token, owner, repo, ShopPaths.BACKUP_SETTINGS, "Update backup settings") { raw ->
                val bs = raw?.let { codec.backupSettingsFromJson(it) } ?: current.backupSettings
                val updated = bs.copy(
                    autoDailyBackupEnabled = autoDailyBackup,
                    preferredBackupTime = newBackupTime.trim()
                )
                codec.backupSettingsToJson(updated) to Unit
            }
        }
        return loadShopSnapshot(token, binding.copy(shopName = newShopName.trim(), timezoneId = newTimezone.trim()), actorLogin)
    }

    suspend fun addOrUpdateUser(
        token: String,
        binding: LocalShopBinding,
        actorLogin: String,
        githubLogin: String,
        displayName: String,
        role: UserRole,
        active: Boolean
    ): ShopSnapshot {
        val current = snapshotCache[binding.shopId] ?: loadShopSnapshot(token, binding, actorLogin)
        requirePermission(current, { it.canManageSecurity() }, "manage authorized users")
        require(githubLogin.isNotBlank()) { "GitHub username is required." }

        val owner = binding.repoOwner
        val repo = binding.repoName
        val nowParts = DateUtils.nowParts(current.shopConfig.timezoneId)
        val cleanLogin = githubLogin.trim().removePrefix("@")

        storage.withRepoLock(owner, repo) {
            storage.updateJsonFileWithRetry(token, owner, repo, ShopPaths.USERS, "Update user permissions for @$cleanLogin") { raw ->
                val file = raw?.let { codec.usersFromJson(it) } ?: current.users
                val newUser = AuthorizedUser(
                    githubLogin = cleanLogin,
                    displayName = displayName.trim().ifBlank { cleanLogin },
                    role = role,
                    active = active,
                    addedAtIso = nowParts.isoTimestamp,
                    addedBy = actorLogin
                )
                val updatedList = file.users.filterNot { it.githubLogin.equals(cleanLogin, true) } + newUser
                val newOwner = if (role == UserRole.OWNER) cleanLogin else file.ownerLogin
                codec.usersToJson(file.copy(ownerLogin = newOwner, users = updatedList)) to Unit
            }

            appendAuditLog(
                token, owner, repo, current.shopConfig.timezoneId,
                AuditLogEntry(
                    auditId = IdGenerator.auditId(),
                    userId = actorLogin,
                    action = "USER_PERMISSION_CHANGE",
                    recordId = cleanLogin,
                    dateKey = nowParts.dateKey,
                    dateDisplay = nowParts.dateDisplay,
                    isoTimestamp = nowParts.isoTimestamp,
                    description = "Set @$cleanLogin role to ${role.label} (active=$active)"
                )
            )
        }
        return loadShopSnapshot(token, binding, actorLogin)
    }

    suspend fun addQuickItemPreset(
        token: String,
        binding: LocalShopBinding,
        actorLogin: String,
        name: String,
        defaultPricePaise: Long
    ): ShopSnapshot {
        val current = snapshotCache[binding.shopId] ?: loadShopSnapshot(token, binding, actorLogin)
        requirePermission(current, { it.canWriteTransactions() }, "add quick item preset")
        val owner = binding.repoOwner
        val repo = binding.repoName
        val preset = QuickItemPreset(
            id = "qi_${System.currentTimeMillis()}",
            name = name.trim(),
            defaultPricePaise = defaultPricePaise
        )
        storage.withRepoLock(owner, repo) {
            storage.updateJsonFileWithRetry(token, owner, repo, ShopPaths.CATEGORIES, "Add quick item ${preset.name}") { raw ->
                val cat = raw?.let { codec.categoriesFromJson(it) } ?: CategoriesFile()
                codec.categoriesToJson(cat.copy(quickItems = cat.quickItems + preset)) to Unit
            }
        }
        return loadShopSnapshot(token, binding, actorLogin)
    }

    // ==================== GITHUB DATABASE EXPLORER ====================

    suspend fun listRepoDirectory(token: String, binding: LocalShopBinding, path: String): List<RemoteDirEntry> {
        return storage.listDirectory(token, binding.repoOwner, binding.repoName, path)
    }

    suspend fun readRepoFile(token: String, binding: LocalShopBinding, path: String, ref: String? = null): RemoteFileEntry? {
        return storage.getFile(token, binding.repoOwner, binding.repoName, path, ref)
    }

    suspend fun saveExplorerFile(
        token: String,
        binding: LocalShopBinding,
        actorLogin: String,
        path: String,
        content: String,
        expectedSha: String?,
        commitMessage: String
    ): String {
        val current = snapshotCache[binding.shopId] ?: loadShopSnapshot(token, binding, actorLogin)
        requirePermission(current, { it.canEditDatabase() }, "edit database files")
        if (!current.settings.allowDatabaseExplorerEdit) {
            throw SecurityException("Database Explorer editing is disabled in Shop Settings.")
        }
        val sha = storage.putFile(
            token = token,
            owner = binding.repoOwner,
            repo = binding.repoName,
            path = path,
            content = content,
            commitMessage = commitMessage.ifBlank { "SLR Note Bu Explorer: Update $path" },
            expectedSha = expectedSha,
            validateJson = path.endsWith(".json", ignoreCase = true)
        )
        val nowParts = DateUtils.nowParts(current.shopConfig.timezoneId)
        appendAuditLog(
            token, binding.repoOwner, binding.repoName, current.shopConfig.timezoneId,
            AuditLogEntry(
                auditId = IdGenerator.auditId(),
                userId = actorLogin,
                action = "DATABASE_FILE_MODIFIED",
                recordId = path,
                dateKey = nowParts.dateKey,
                dateDisplay = nowParts.dateDisplay,
                isoTimestamp = nowParts.isoTimestamp,
                description = "Modified $path via GitHub Database Explorer (SHA ${sha.take(7)})"
            )
        )
        return sha
    }

    suspend fun deleteExplorerFile(
        token: String,
        binding: LocalShopBinding,
        actorLogin: String,
        path: String,
        sha: String
    ) {
        val current = snapshotCache[binding.shopId] ?: loadShopSnapshot(token, binding, actorLogin)
        requirePermission(current, { it.canEditDatabase() }, "delete database files")
        if (!current.settings.allowDatabaseExplorerEdit) {
            throw SecurityException("Database Explorer editing is disabled in Shop Settings.")
        }
        storage.deleteFile(token, binding.repoOwner, binding.repoName, path, sha, "SLR Note Bu Explorer: Delete $path")
        val nowParts = DateUtils.nowParts(current.shopConfig.timezoneId)
        appendAuditLog(
            token, binding.repoOwner, binding.repoName, current.shopConfig.timezoneId,
            AuditLogEntry(
                auditId = IdGenerator.auditId(),
                userId = actorLogin,
                action = "DATABASE_FILE_DELETED",
                recordId = path,
                dateKey = nowParts.dateKey,
                dateDisplay = nowParts.dateDisplay,
                isoTimestamp = nowParts.isoTimestamp,
                description = "Deleted $path via GitHub Database Explorer"
            )
        )
    }

    suspend fun listFileCommits(token: String, binding: LocalShopBinding, path: String?): List<RemoteCommitEntry> {
        return storage.listCommits(token, binding.repoOwner, binding.repoName, path)
    }

    // ==================== HELPERS ====================

    private suspend fun loadAllBaTransactions(token: String, owner: String, repo: String): List<BaTransaction> {
        val result = mutableListOf<BaTransaction>()
        val yearDirs = storage.listDirectory(token, owner, repo, "${ShopPaths.BABU_DIR}/transactions")
            .filter { it.isDirectory }
        for (yearDir in yearDirs) {
            val monthDirs = storage.listDirectory(token, owner, repo, yearDir.path).filter { it.isDirectory }
            for (monthDir in monthDirs) {
                val fileEntry = storage.getFile(token, owner, repo, "${monthDir.path}/transactions.json")
                val parsed = fileEntry?.content?.let { codec.baTransactionsFromJson(it) }
                if (parsed != null) result.addAll(parsed.transactions)
            }
        }
        return result.sortedByDescending { it.isoTimestamp }
    }

    private suspend fun putOrUpdate(
        token: String,
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String,
        validateJson: Boolean = true
    ) {
        val existing = storage.getFile(token, owner, repo, path)
        storage.putFile(token, owner, repo, path, content, message, existing?.sha, validateJson)
    }

    private suspend fun appendAuditLog(
        token: String,
        owner: String,
        repo: String,
        timezoneId: String,
        entry: AuditLogEntry
    ) {
        val nowParts = DateUtils.nowParts(timezoneId)
        storage.updateJsonFileWithRetry(
            token, owner, repo,
            ShopPaths.auditLogFile(nowParts.year, nowParts.month),
            "SLR Note Bu Audit: ${entry.action}"
        ) { raw ->
            val file = raw?.let { codec.auditLogFromJson(it) } ?: AuditLogFile(monthKey = nowParts.monthKey)
            val updated = file.copy(entries = (listOf(entry) + file.entries).take(1000))
            codec.auditLogToJson(updated) to Unit
        }
    }
}
