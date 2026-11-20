package com.example.ui.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.repository.ShopJsonCodec
import com.example.domain.AccountingCalculator
import com.example.domain.model.FinancialReportSummary
import com.example.ui.AppMainUiState
import com.example.ui.components.RupeeAmountText
import com.example.ui.theme.CashGreen
import com.example.ui.theme.CreditCrimson
import com.example.ui.theme.OfflineIndigo
import com.example.ui.theme.WarningAmber
import com.example.utils.DateUtils
import com.example.utils.ExportUtils

@Composable
fun ReportsScreen(
    uiState: AppMainUiState,
    codec: ShopJsonCodec,
    onLoadArchiveForReport: (Int, Int) -> Unit,
    onLoadYearlyReport: (Int) -> Unit,
    onOpenMonthlyArchive: () -> Unit
) {
    val context = LocalContext.current
    val snap = uiState.snapshot ?: return
    val tz = snap.shopConfig.timezoneId
    val today = remember(tz) { DateUtils.todayDate(tz) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0=Daily, 1=Monthly, 2=Yearly
    var dailyDateInput by remember { mutableStateOf(today.toString()) }
    var monthYearInput by remember { mutableStateOf(today.year.toString()) }
    var monthNumInput by remember { mutableStateOf(today.monthValue.toString()) }
    var yearInput by remember { mutableStateOf(today.year.toString()) }

    val parsedMonthYear = monthYearInput.toIntOrNull() ?: today.year
    val parsedMonthNum = (monthNumInput.toIntOrNull() ?: today.monthValue).coerceIn(1, 12)
    val parsedYear = yearInput.toIntOrNull() ?: today.year

    LaunchedEffect(selectedTab, parsedMonthYear, parsedMonthNum) {
        if (selectedTab == 1) {
            onLoadArchiveForReport(parsedMonthYear, parsedMonthNum)
        }
    }
    LaunchedEffect(selectedTab, parsedYear) {
        if (selectedTab == 2) {
            onLoadYearlyReport(parsedYear)
        }
    }

    val allActiveSales = remember(snap.allActiveSalesFiles) {
        snap.allActiveSalesFiles.flatMap { it.sales }.filter { !it.deleted }
    }

    val currentReport: FinancialReportSummary = when (selectedTab) {
        0 -> {
            val d = DateUtils.parseLocalDate(dailyDateInput.trim()) ?: today
            val archivedForMonth = uiState.monthlyArchivesForYear.firstOrNull { it.year == d.year && it.month == d.monthValue }
            val combinedSales = if (archivedForMonth != null) (allActiveSales + archivedForMonth.sales).distinctBy { it.saleId } else allActiveSales
            AccountingCalculator.computeDailyReport(d, combinedSales, snap.baTransactions, snap.customers)
        }
        1 -> {
            val arch = uiState.selectedArchive?.takeIf { it.year == parsedMonthYear && it.month == parsedMonthNum }
                ?: uiState.monthlyArchivesForYear.firstOrNull { it.year == parsedMonthYear && it.month == parsedMonthNum }
            AccountingCalculator.computeMonthlyReport(parsedMonthYear, parsedMonthNum, allActiveSales, arch, snap.baTransactions, snap.customers)
        }
        else -> {
            val yr = uiState.selectedYearlyReport?.takeIf { it.year == parsedYear }
                ?: AccountingCalculator.computeYearlyReport(
                    year = parsedYear,
                    updatedAtIso = snap.lastSyncedAtIso,
                    activeSalesFiles = snap.allActiveSalesFiles,
                    monthlyArchives = uiState.monthlyArchivesForYear,
                    baTransactions = snap.baTransactions
                )
            FinancialReportSummary(
                title = "Yearly Financial Report",
                subtitle = "Year $parsedYear",
                totalSalesPaise = yr.totalYearlySalesPaise,
                cashSalesPaise = yr.cashSalesPaise,
                otherOfflineSalesPaise = yr.otherOfflineSalesPaise,
                creditSalesPaise = yr.creditSalesPaise,
                baAddedPaise = yr.totalBaAddedPaise,
                baCollectedPaise = yr.totalBaCollectedPaise,
                remainingPendingBaPaise = snap.customers.sumOf { it.currentPendingBaPaise },
                numberOfSales = yr.monthlyBreakdown.sumOf { it.transactionCount },
                numberOfActiveCustomers = snap.customers.size,
                numberOfPayments = snap.baTransactions.count { !it.reversed && it.year == parsedYear && it.type == com.example.domain.model.BaTransactionType.PAYMENT },
                bestSellingItems = yr.bestSellingItems,
                monthlyComparison = yr.monthlyBreakdown
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Financial Reports", style = MaterialTheme.typography.headlineSmall)
                OutlinedButton(onClick = onOpenMonthlyArchive, modifier = Modifier.testTag("open_archive_from_reports")) {
                    Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Archives")
                }
            }
        }

        item {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Daily") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Monthly") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Yearly") })
            }
        }

        // Period selector
        item {
            when (selectedTab) {
                0 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = dailyDateInput,
                            onValueChange = { dailyDateInput = it },
                            label = { Text("Date (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = dailyDateInput == today.toString(),
                            onClick = { dailyDateInput = today.toString() },
                            label = { Text("Today") }
                        )
                    }
                }
                1 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = monthYearInput,
                            onValueChange = { monthYearInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Year") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = monthNumInput,
                            onValueChange = { monthNumInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Month (1-12)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { onLoadArchiveForReport(parsedMonthYear, parsedMonthNum) }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = yearInput,
                            onValueChange = { yearInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Report Year") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { onLoadYearlyReport(parsedYear) }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                            Spacer(Modifier.width(6.dp))
                            Text("Generate")
                        }
                    }
                }
            }
        }

        // Core Financial Accuracy Breakdown
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${currentReport.title} • ${currentReport.subtitle}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Credit sales are separated from cash received; Ba payments are tracked without double-counting as new sales.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    ReportRow("Total Sales", currentReport.totalSalesPaise, MaterialTheme.colorScheme.primary)
                    ReportRow("1. Cash Sales Received", currentReport.cashSalesPaise, CashGreen)
                    ReportRow("2. Other Offline Payments Received", currentReport.otherOfflineSalesPaise, OfflineIndigo)
                    ReportRow("3. Credit Sales / Ba Added", currentReport.creditSalesPaise, CreditCrimson)
                    ReportRow("Total Ledger Ba Added", currentReport.baAddedPaise, CreditCrimson)
                    ReportRow("4. Ba Payments Collected", currentReport.baCollectedPaise, CashGreen)
                    ReportRow("5. Remaining Pending Customer Ba", currentReport.remainingPendingBaPaise, WarningAmber)
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Sales: ${currentReport.numberOfSales}", style = MaterialTheme.typography.labelLarge)
                        Text("Payments: ${currentReport.numberOfPayments}", style = MaterialTheme.typography.labelLarge)
                        Text("Customers: ${currentReport.numberOfActiveCustomers}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Export Buttons: PDF, CSV, JSON
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val f = ExportUtils.writeReportPdf(
                            context,
                            "SLR_${currentReport.subtitle.replace(" ", "_")}.pdf",
                            snap.shopConfig.shopName,
                            currentReport
                        )
                        ExportUtils.shareFile(context, f, "application/pdf", "Export ${currentReport.title} (PDF)")
                    },
                    modifier = Modifier.weight(1f).testTag("export_pdf_button")
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PDF")
                }
                OutlinedButton(
                    onClick = {
                        val csv = ExportUtils.buildReportCsv(currentReport)
                        val f = ExportUtils.writeTextExport(context, "SLR_${currentReport.subtitle.replace(" ", "_")}.csv", csv)
                        ExportUtils.shareFile(context, f, "text/csv", "Export ${currentReport.title} (CSV)")
                    },
                    modifier = Modifier.weight(1f).testTag("export_csv_button")
                ) {
                    Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("CSV")
                }
                OutlinedButton(
                    onClick = {
                        val json = codec.toJson(
                            mapOf(
                                "shopName" to snap.shopConfig.shopName,
                                "title" to currentReport.title,
                                "period" to currentReport.subtitle,
                                "totalSalesPaise" to currentReport.totalSalesPaise,
                                "cashSalesPaise" to currentReport.cashSalesPaise,
                                "otherOfflineSalesPaise" to currentReport.otherOfflineSalesPaise,
                                "creditSalesPaise" to currentReport.creditSalesPaise,
                                "baAddedPaise" to currentReport.baAddedPaise,
                                "baCollectedPaise" to currentReport.baCollectedPaise,
                                "remainingPendingBaPaise" to currentReport.remainingPendingBaPaise,
                                "bestSellingItems" to currentReport.bestSellingItems
                            )
                        )
                        val f = ExportUtils.writeTextExport(context, "SLR_${currentReport.subtitle.replace(" ", "_")}.json", json)
                        ExportUtils.shareFile(context, f, "application/json", "Export ${currentReport.title} (JSON)")
                    },
                    modifier = Modifier.weight(1f).testTag("export_json_button")
                ) {
                    Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("JSON")
                }
            }
        }

        // Yearly Monthly Comparison Chart
        if (selectedTab == 2 && currentReport.monthlyComparison.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Monthly Sales Comparison ($parsedYear)", style = MaterialTheme.typography.titleMedium)
                        val barColor = MaterialTheme.colorScheme.primary
                        val trackColor = MaterialTheme.colorScheme.surfaceVariant
                        val maxMonth = (currentReport.monthlyComparison.maxOfOrNull { it.totalSalesPaise } ?: 1L).coerceAtLeast(1L)

                        Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
                            val slotW = size.width / 12f
                            val barW = slotW * 0.58f
                            currentReport.monthlyComparison.forEachIndexed { idx, row ->
                                val x = idx * slotW + (slotW - barW) / 2f
                                drawRoundRect(trackColor, Offset(x, 0f), Size(barW, size.height), CornerRadius(5f, 5f))
                                val h = (row.totalSalesPaise.toFloat() / maxMonth.toFloat()) * size.height
                                drawRoundRect(barColor, Offset(x, size.height - h), Size(barW, h), CornerRadius(5f, 5f))
                            }
                        }
                        currentReport.monthlyComparison.filter { it.totalSalesPaise > 0L || it.baCollectedPaise > 0L }.forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${row.monthName} (${row.transactionCount} sales)", style = MaterialTheme.typography.bodySmall)
                                RupeeAmountText(paise = row.totalSalesPaise, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        // Best-Selling Items Table
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Best-Selling Items", style = MaterialTheme.typography.titleMedium)
                    if (currentReport.bestSellingItems.isEmpty()) {
                        Text("No item sales in this period.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        currentReport.bestSellingItems.take(20).forEachIndexed { idx, item ->
                            if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.itemName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text("Qty sold: ${item.quantitySold}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                RupeeAmountText(paise = item.totalAmountPaise)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportRow(label: String, paise: Long, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        RupeeAmountText(paise = paise, style = MaterialTheme.typography.titleSmall, color = color)
    }
}
