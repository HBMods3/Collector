package com.example.domain

import com.example.domain.model.BaTransaction
import com.example.domain.model.BaTransactionType
import com.example.domain.model.CustomerProfile
import com.example.domain.model.DailySalesFile
import com.example.domain.model.DailyTotalEntry
import com.example.domain.model.DashboardMetrics
import com.example.domain.model.FinancialReportSummary
import com.example.domain.model.ItemSummaryEntry
import com.example.domain.model.MonthlyArchiveFile
import com.example.domain.model.MonthlySummaryRow
import com.example.domain.model.PaymentMode
import com.example.domain.model.SaleRecord
import com.example.domain.model.YearlyReportFile
import com.example.utils.DateUtils
import java.time.LocalDate

object AccountingCalculator {

    fun computeDashboardMetrics(
        today: LocalDate,
        activeSalesFiles: List<DailySalesFile>,
        monthlyArchives: List<MonthlyArchiveFile>,
        customers: List<CustomerProfile>,
        baTransactions: List<BaTransaction>
    ): DashboardMetrics {
        val todayKey = DateUtils.dateKey(today.year, today.monthValue, today.dayOfMonth)
        val currentMonthKey = DateUtils.monthKey(today.year, today.monthValue)

        val validActiveSales = activeSalesFiles.flatMap { it.sales }.filter { !it.deleted }
        val todaySales = validActiveSales.filter { it.dateKey == todayKey }

        val todayTotal = todaySales.sumOf { it.grandTotalPaise }
        val todayCash = todaySales.filter { it.paymentMode == PaymentMode.CASH }.sumOf { it.grandTotalPaise }
        val todayOther = todaySales.filter { it.paymentMode == PaymentMode.OTHER_OFFLINE }.sumOf { it.grandTotalPaise }
        val todayCreditSales = todaySales.filter { it.paymentMode == PaymentMode.CREDIT_BA }.sumOf { it.grandTotalPaise }

        val validBaTx = baTransactions.filter { !it.reversed }
        val todayBaAdded = validBaTx
            .filter { it.dateKey == todayKey && it.type == BaTransactionType.CREDIT }
            .sumOf { it.amountPaise }
        val todayBaCollected = validBaTx
            .filter { it.dateKey == todayKey && it.type == BaTransactionType.PAYMENT }
            .sumOf { it.amountPaise }

        // Combine credit sales + direct Ba added only if not linked to avoid double counting
        val unlinkedTodayBaAdded = validBaTx
            .filter { tx ->
                tx.dateKey == todayKey &&
                    tx.type == BaTransactionType.CREDIT &&
                    todaySales.none { it.linkedBaTransactionId == tx.transactionId }
            }
            .sumOf { it.amountPaise }
        val totalTodayCreditOrBaAdded = todayCreditSales + unlinkedTodayBaAdded

        val totalPendingBa = customers.sumOf { it.currentPendingBaPaise }

        val currentMonthSalesList = validActiveSales.filter {
            DateUtils.monthKey(it.year, it.month) == currentMonthKey
        }
        val currentMonthArchive = monthlyArchives.firstOrNull { it.monthKey == currentMonthKey }
        val currentMonthTotal = currentMonthSalesList.sumOf { it.grandTotalPaise } +
            (currentMonthArchive?.totalSalesPaise ?: 0L)

        // Yearly sales: active sales in current year + archived months in current year (avoiding double count if archive exists for same month)
        val archivedMonthKeys = monthlyArchives.filter { it.year == today.year }.map { it.monthKey }.toSet()
        val yearActiveTotal = validActiveSales
            .filter { it.year == today.year && DateUtils.monthKey(it.year, it.month) !in archivedMonthKeys }
            .sumOf { it.grandTotalPaise }
        val yearArchivedTotal = monthlyArchives
            .filter { it.year == today.year }
            .sumOf { it.totalSalesPaise + it.corrections.sumOf { c -> c.adjustmentAmountPaise } }
        val currentYearTotal = yearActiveTotal + yearArchivedTotal

        val recentSales = validActiveSales
            .sortedByDescending { it.isoTimestamp }
            .take(5)
        val recentCredits = validBaTx
            .filter { it.type == BaTransactionType.CREDIT }
            .sortedByDescending { it.isoTimestamp }
            .take(5)
        val recentPayments = validBaTx
            .filter { it.type == BaTransactionType.PAYMENT }
            .sortedByDescending { it.isoTimestamp }
            .take(5)
        val topPending = customers
            .filter { it.currentPendingBaPaise > 0L }
            .sortedByDescending { it.currentPendingBaPaise }
            .take(5)

        val dailyTotals = computeDailyTotals(currentMonthSalesList)

        return DashboardMetrics(
            todayTotalSalesPaise = todayTotal,
            todayCashSalesPaise = todayCash,
            todayOtherOfflinePaise = todayOther,
            todayCreditSalesPaise = totalTodayCreditOrBaAdded,
            todayBaCollectionPaise = todayBaCollected,
            totalPendingBaPaise = totalPendingBa,
            currentMonthSalesPaise = currentMonthTotal,
            currentYearSalesPaise = currentYearTotal,
            recentSales = recentSales,
            recentCredits = recentCredits,
            recentPayments = recentPayments,
            topPendingCustomers = topPending,
            monthlyDailyTotals = dailyTotals
        )
    }

    fun computeDailyTotals(sales: List<SaleRecord>): List<DailyTotalEntry> {
        return sales
            .filter { !it.deleted }
            .groupBy { it.dateKey }
            .map { (dateKey, daySales) ->
                DailyTotalEntry(
                    dateKey = dateKey,
                    dateDisplay = daySales.firstOrNull()?.dateDisplay ?: dateKey,
                    totalSalesPaise = daySales.sumOf { it.grandTotalPaise },
                    cashSalesPaise = daySales.filter { it.paymentMode == PaymentMode.CASH }.sumOf { it.grandTotalPaise },
                    otherOfflineSalesPaise = daySales.filter { it.paymentMode == PaymentMode.OTHER_OFFLINE }.sumOf { it.grandTotalPaise },
                    creditSalesPaise = daySales.filter { it.paymentMode == PaymentMode.CREDIT_BA }.sumOf { it.grandTotalPaise },
                    transactionCount = daySales.size,
                    itemsSoldCount = daySales.sumOf { sale -> sale.items.sumOf { it.quantity } }
                )
            }
            .sortedBy { it.dateKey }
    }

    fun computeItemSummary(sales: List<SaleRecord>): List<ItemSummaryEntry> {
        val map = linkedMapOf<String, Pair<Int, Long>>()
        sales.filter { !it.deleted }.forEach { sale ->
            sale.items.forEach { item ->
                val normalizedName = item.itemName.trim()
                val existing = map[normalizedName] ?: (0 to 0L)
                map[normalizedName] = (existing.first + item.quantity) to (existing.second + item.itemTotalPaise)
            }
        }
        return map.entries
            .map { (name, pair) ->
                ItemSummaryEntry(
                    itemName = name,
                    quantitySold = pair.first,
                    totalAmountPaise = pair.second
                )
            }
            .sortedByDescending { it.totalAmountPaise }
    }

    fun buildMonthlyArchive(
        year: Int,
        month: Int,
        archivedAtIso: String,
        archivedBy: String,
        sales: List<SaleRecord>
    ): MonthlyArchiveFile {
        val validSales = sales.filter { !it.deleted }.sortedBy { it.isoTimestamp }
        val totalSales = validSales.sumOf { it.grandTotalPaise }
        val cashSales = validSales.filter { it.paymentMode == PaymentMode.CASH }.sumOf { it.grandTotalPaise }
        val otherSales = validSales.filter { it.paymentMode == PaymentMode.OTHER_OFFLINE }.sumOf { it.grandTotalPaise }
        val creditSales = validSales.filter { it.paymentMode == PaymentMode.CREDIT_BA }.sumOf { it.grandTotalPaise }
        val totalProducts = validSales.sumOf { sale -> sale.items.sumOf { it.quantity } }

        return MonthlyArchiveFile(
            year = year,
            month = month,
            monthName = DateUtils.monthName(month),
            monthKey = DateUtils.monthKey(year, month),
            archivedAtIso = archivedAtIso,
            archivedBy = archivedBy,
            totalSalesPaise = totalSales,
            cashSalesPaise = cashSales,
            otherOfflineSalesPaise = otherSales,
            creditSalesPaise = creditSales,
            transactionCount = validSales.size,
            totalProductsSold = totalProducts,
            dailyTotals = computeDailyTotals(validSales),
            itemSummary = computeItemSummary(validSales),
            sales = validSales
        )
    }

    fun verifyMonthlyArchive(
        originalSales: List<SaleRecord>,
        savedArchive: MonthlyArchiveFile
    ): Boolean {
        val validOriginal = originalSales.filter { !it.deleted }
        val expectedTotal = validOriginal.sumOf { it.grandTotalPaise }
        val expectedCash = validOriginal.filter { it.paymentMode == PaymentMode.CASH }.sumOf { it.grandTotalPaise }
        val expectedOther = validOriginal.filter { it.paymentMode == PaymentMode.OTHER_OFFLINE }.sumOf { it.grandTotalPaise }
        val expectedCredit = validOriginal.filter { it.paymentMode == PaymentMode.CREDIT_BA }.sumOf { it.grandTotalPaise }
        val expectedItems = validOriginal.sumOf { s -> s.items.sumOf { it.quantity } }

        return savedArchive.transactionCount == validOriginal.size &&
            savedArchive.sales.size == validOriginal.size &&
            savedArchive.totalProductsSold == expectedItems &&
            savedArchive.totalSalesPaise == expectedTotal &&
            savedArchive.cashSalesPaise == expectedCash &&
            savedArchive.otherOfflineSalesPaise == expectedOther &&
            savedArchive.creditSalesPaise == expectedCredit
    }

    fun recalculateCustomerLedger(
        customer: CustomerProfile,
        allCustomerTransactions: List<BaTransaction>,
        nowIso: String
    ): Pair<CustomerProfile, List<BaTransaction>> {
        val ordered = allCustomerTransactions.sortedBy { it.isoTimestamp }
        var runningBalance = 0L
        var totalCredit = 0L
        var totalPaid = 0L

        val recalculated = ordered.map { tx ->
            if (tx.reversed) {
                tx
            } else {
                val prev = runningBalance
                when (tx.type) {
                    BaTransactionType.CREDIT -> {
                        runningBalance += tx.amountPaise
                        totalCredit += tx.amountPaise
                    }
                    BaTransactionType.PAYMENT -> {
                        runningBalance = (runningBalance - tx.amountPaise).coerceAtLeast(0L)
                        totalPaid += tx.amountPaise
                    }
                    BaTransactionType.ADJUSTMENT -> {
                        runningBalance = (runningBalance + tx.amountPaise).coerceAtLeast(0L)
                        if (tx.amountPaise >= 0) totalCredit += tx.amountPaise else totalPaid += -tx.amountPaise
                    }
                }
                tx.copy(
                    previousBalancePaise = prev,
                    updatedBalancePaise = runningBalance
                )
            }
        }

        val lastValid = recalculated.lastOrNull { !it.reversed }
        val exactPending = (totalCredit - totalPaid).coerceAtLeast(0L)
        val updatedCustomer = customer.copy(
            totalCreditPaise = totalCredit,
            totalPaidPaise = totalPaid,
            currentPendingBaPaise = exactPending,
            lastTransactionDateDisplay = lastValid?.dateDisplay ?: customer.lastTransactionDateDisplay,
            lastTransactionIso = lastValid?.isoTimestamp ?: customer.lastTransactionIso,
            updatedAtIso = nowIso
        )

        return updatedCustomer to recalculated
    }

    fun computeDailyReport(
        date: LocalDate,
        sales: List<SaleRecord>,
        baTransactions: List<BaTransaction>,
        customers: List<CustomerProfile>
    ): FinancialReportSummary {
        val dateKey = DateUtils.dateKey(date.year, date.monthValue, date.dayOfMonth)
        val daySales = sales.filter { !it.deleted && it.dateKey == dateKey }
        val dayBa = baTransactions.filter { !it.reversed && it.dateKey == dateKey }
        val dayCredits = dayBa.filter { it.type == BaTransactionType.CREDIT }
        val dayPayments = dayBa.filter { it.type == BaTransactionType.PAYMENT }

        return FinancialReportSummary(
            title = "Daily Financial Report",
            subtitle = DateUtils.formatLocalDate(date),
            totalSalesPaise = daySales.sumOf { it.grandTotalPaise },
            cashSalesPaise = daySales.filter { it.paymentMode == PaymentMode.CASH }.sumOf { it.grandTotalPaise },
            otherOfflineSalesPaise = daySales.filter { it.paymentMode == PaymentMode.OTHER_OFFLINE }.sumOf { it.grandTotalPaise },
            creditSalesPaise = daySales.filter { it.paymentMode == PaymentMode.CREDIT_BA }.sumOf { it.grandTotalPaise },
            baAddedPaise = dayCredits.sumOf { it.amountPaise },
            baCollectedPaise = dayPayments.sumOf { it.amountPaise },
            remainingPendingBaPaise = customers.sumOf { it.currentPendingBaPaise },
            numberOfSales = daySales.size,
            numberOfActiveCustomers = (dayCredits.map { it.customerPhoneNumber } + dayPayments.map { it.customerPhoneNumber }).toSet().size,
            numberOfPayments = dayPayments.size,
            bestSellingItems = computeItemSummary(daySales)
        )
    }

    fun computeMonthlyReport(
        year: Int,
        month: Int,
        activeSales: List<SaleRecord>,
        archive: MonthlyArchiveFile?,
        baTransactions: List<BaTransaction>,
        customers: List<CustomerProfile>
    ): FinancialReportSummary {
        val sourceSales = if (archive != null && archive.sales.isNotEmpty()) {
            archive.sales
        } else {
            activeSales.filter { !it.deleted && it.year == year && it.month == month }
        }
        val monthBa = baTransactions.filter { !it.reversed && it.year == year && it.month == month }
        val credits = monthBa.filter { it.type == BaTransactionType.CREDIT }
        val payments = monthBa.filter { it.type == BaTransactionType.PAYMENT }
        val correctionDelta = archive?.corrections?.sumOf { it.adjustmentAmountPaise } ?: 0L

        return FinancialReportSummary(
            title = "Monthly Financial Report",
            subtitle = "${DateUtils.monthName(month)} $year",
            totalSalesPaise = sourceSales.sumOf { it.grandTotalPaise } + correctionDelta,
            cashSalesPaise = sourceSales.filter { it.paymentMode == PaymentMode.CASH }.sumOf { it.grandTotalPaise } +
                (archive?.corrections?.filter { it.targetPaymentMode == PaymentMode.CASH }?.sumOf { it.adjustmentAmountPaise } ?: 0L),
            otherOfflineSalesPaise = sourceSales.filter { it.paymentMode == PaymentMode.OTHER_OFFLINE }.sumOf { it.grandTotalPaise } +
                (archive?.corrections?.filter { it.targetPaymentMode == PaymentMode.OTHER_OFFLINE }?.sumOf { it.adjustmentAmountPaise } ?: 0L),
            creditSalesPaise = sourceSales.filter { it.paymentMode == PaymentMode.CREDIT_BA }.sumOf { it.grandTotalPaise } +
                (archive?.corrections?.filter { it.targetPaymentMode == PaymentMode.CREDIT_BA }?.sumOf { it.adjustmentAmountPaise } ?: 0L),
            baAddedPaise = credits.sumOf { it.amountPaise },
            baCollectedPaise = payments.sumOf { it.amountPaise },
            remainingPendingBaPaise = customers.sumOf { it.currentPendingBaPaise },
            numberOfSales = sourceSales.size,
            numberOfActiveCustomers = (credits.map { it.customerPhoneNumber } + payments.map { it.customerPhoneNumber }).toSet().size,
            numberOfPayments = payments.size,
            bestSellingItems = computeItemSummary(sourceSales)
        )
    }

    fun computeYearlyReport(
        year: Int,
        updatedAtIso: String,
        activeSalesFiles: List<DailySalesFile>,
        monthlyArchives: List<MonthlyArchiveFile>,
        baTransactions: List<BaTransaction>
    ): YearlyReportFile {
        val allSalesForYear = mutableListOf<SaleRecord>()
        val monthlyRows = (1..12).map { month ->
            val archive = monthlyArchives.firstOrNull { it.year == year && it.month == month }
            val sales = if (archive != null) {
                archive.sales
            } else {
                activeSalesFiles
                    .filter { it.year == year && it.month == month }
                    .flatMap { it.sales }
                    .filter { !it.deleted }
            }
            allSalesForYear.addAll(sales)
            val corrTotal = archive?.corrections?.sumOf { it.adjustmentAmountPaise } ?: 0L
            val corrCash = archive?.corrections?.filter { it.targetPaymentMode == PaymentMode.CASH }?.sumOf { it.adjustmentAmountPaise } ?: 0L
            val corrOther = archive?.corrections?.filter { it.targetPaymentMode == PaymentMode.OTHER_OFFLINE }?.sumOf { it.adjustmentAmountPaise } ?: 0L
            val corrCredit = archive?.corrections?.filter { it.targetPaymentMode == PaymentMode.CREDIT_BA }?.sumOf { it.adjustmentAmountPaise } ?: 0L

            val monthBa = baTransactions.filter { !it.reversed && it.year == year && it.month == month }
            MonthlySummaryRow(
                month = month,
                monthName = DateUtils.monthName(month),
                totalSalesPaise = sales.sumOf { it.grandTotalPaise } + corrTotal,
                cashSalesPaise = sales.filter { it.paymentMode == PaymentMode.CASH }.sumOf { it.grandTotalPaise } + corrCash,
                otherOfflineSalesPaise = sales.filter { it.paymentMode == PaymentMode.OTHER_OFFLINE }.sumOf { it.grandTotalPaise } + corrOther,
                creditSalesPaise = sales.filter { it.paymentMode == PaymentMode.CREDIT_BA }.sumOf { it.grandTotalPaise } + corrCredit,
                baAddedPaise = monthBa.filter { it.type == BaTransactionType.CREDIT }.sumOf { it.amountPaise },
                baCollectedPaise = monthBa.filter { it.type == BaTransactionType.PAYMENT }.sumOf { it.amountPaise },
                transactionCount = sales.size
            )
        }

        return YearlyReportFile(
            year = year,
            updatedAtIso = updatedAtIso,
            totalYearlySalesPaise = monthlyRows.sumOf { it.totalSalesPaise },
            cashSalesPaise = monthlyRows.sumOf { it.cashSalesPaise },
            otherOfflineSalesPaise = monthlyRows.sumOf { it.otherOfflineSalesPaise },
            creditSalesPaise = monthlyRows.sumOf { it.creditSalesPaise },
            totalBaAddedPaise = monthlyRows.sumOf { it.baAddedPaise },
            totalBaCollectedPaise = monthlyRows.sumOf { it.baCollectedPaise },
            monthlyBreakdown = monthlyRows,
            bestSellingItems = computeItemSummary(allSalesForYear)
        )
    }
}
