package com.example.ui.archive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.repository.ShopJsonCodec
import com.example.domain.model.ClosingStatus
import com.example.domain.model.FinancialReportSummary
import com.example.domain.model.PaymentMode
import com.example.ui.AppMainUiState
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.EmptyLedgerCard
import com.example.ui.components.PaymentModeBadge
import com.example.ui.components.RupeeAmountText
import com.example.ui.theme.CashGreen
import com.example.ui.theme.CreditCrimson
import com.example.ui.theme.OfflineIndigo
import com.example.utils.DateUtils
import com.example.utils.ExportUtils
import com.example.utils.MoneyUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyArchiveScreen(
    uiState: AppMainUiState,
    codec: ShopJsonCodec,
    onBack: () -> Unit,
    onLoadArchive: (Int, Int) -> Unit,
    onTriggerClosing: (Int, Int) -> Unit,
    onAddCorrection: (year: Int, month: Int, description: String, amountPaise: Long, mode: PaymentMode) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val snap = uiState.snapshot ?: return
    val today = remember(snap.shopConfig.timezoneId) { DateUtils.todayDate(snap.shopConfig.timezoneId) }
    val prevMonth = remember(today) { today.minusMonths(1) }

    var yearInput by remember { mutableStateOf(prevMonth.year.toString()) }
    var monthInput by remember { mutableStateOf(prevMonth.monthValue.toString()) }
    var itemSearch by remember { mutableStateOf("") }
    var showClosingConfirm by remember { mutableStateOf(false) }
    var showCorrectionDialog by remember { mutableStateOf(false) }

    val y = yearInput.toIntOrNull() ?: prevMonth.year
    val m = (monthInput.toIntOrNull() ?: prevMonth.monthValue).coerceIn(1, 12)

    LaunchedEffect(y, m) {
        onLoadArchive(y, m)
    }

    val archive = uiState.selectedArchive?.takeIf { it.year == y && it.month == m }
    val canClose = snap.currentUserRole.canRunBackupOrClose()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monthly Archive Viewer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Month & Year Picker
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = yearInput,
                        onValueChange = { yearInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Year") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = monthInput,
                        onValueChange = { monthInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Month (1-12)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { onLoadArchive(y, m) }) { Text("Load") }
                }
            }

            // Admin Monthly Closing & History Card
            if (canClose) {
                item {
                    val closingRec = snap.closingHistory.records.firstOrNull { it.year == y && it.month == m }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.LockClock, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Monthly Closing Status (${DateUtils.monthName(m)} $y)", style = MaterialTheme.typography.titleSmall)
                            }
                            if (closingRec != null) {
                                Text(
                                    "Status: ${closingRec.closingStatus.name} • Verified: ${closingRec.verificationStatus} • ${closingRec.closingDateIso.take(10)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (!closingRec.errorMessage.isNullOrBlank()) {
                                    Text(closingRec.errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                Text("This month has not been closed yet.", style = MaterialTheme.typography.bodySmall)
                            }

                            if (closingRec?.closingStatus != ClosingStatus.COMPLETED) {
                                Button(
                                    onClick = { showClosingConfirm = true },
                                    modifier = Modifier.testTag("trigger_monthly_closing_button")
                                ) {
                                    Text(if (closingRec?.closingStatus == ClosingStatus.FAILED) "Retry Failed Monthly Closing" else "Archive & Close Month Now")
                                }
                            }
                        }
                    }
                }
            }

            if (archive == null) {
                item {
                    EmptyLedgerCard(
                        title = "No archive found for ${DateUtils.monthName(m)} $y",
                        subtitle = "Monthly archives are created automatically when a new month begins, or manually by Admin above."
                    )
                }
            } else {
                // Archive Summary Card
                item {
                    val corrTotal = archive.corrections.sumOf { it.adjustmentAmountPaise }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${archive.monthName} ${archive.year} Archive", style = MaterialTheme.typography.titleLarge)
                            Text("Archived at ${archive.archivedAtIso.take(16).replace('T', ' ')} by @${archive.archivedBy}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Sales", style = MaterialTheme.typography.titleSmall)
                                RupeeAmountText(paise = archive.totalSalesPaise + corrTotal, style = MaterialTheme.typography.titleLarge)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Cash Sales", color = CashGreen)
                                RupeeAmountText(paise = archive.cashSalesPaise, color = CashGreen)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Other Offline Payments", color = OfflineIndigo)
                                RupeeAmountText(paise = archive.otherOfflineSalesPaise, color = OfflineIndigo)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Credit Sales", color = CreditCrimson)
                                RupeeAmountText(paise = archive.creditSalesPaise, color = CreditCrimson)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Items Sold: ${archive.totalProductsSold}")
                                Text("Transactions: ${archive.transactionCount}")
                            }
                        }
                    }
                }

                // Exports + Correction Record
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                val report = FinancialReportSummary(
                                    title = "Monthly Sales Archive",
                                    subtitle = "${archive.monthName} ${archive.year}",
                                    totalSalesPaise = archive.totalSalesPaise,
                                    cashSalesPaise = archive.cashSalesPaise,
                                    otherOfflineSalesPaise = archive.otherOfflineSalesPaise,
                                    creditSalesPaise = archive.creditSalesPaise,
                                    baAddedPaise = 0L,
                                    baCollectedPaise = 0L,
                                    remainingPendingBaPaise = 0L,
                                    numberOfSales = archive.transactionCount,
                                    numberOfActiveCustomers = 0,
                                    numberOfPayments = 0,
                                    bestSellingItems = archive.itemSummary
                                )
                                val f = ExportUtils.writeReportPdf(context, "SLR_Archive_${archive.monthKey}.pdf", snap.shopConfig.shopName, report)
                                ExportUtils.shareFile(context, f, "application/pdf", "Export Archive PDF")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("PDF")
                        }
                        OutlinedButton(
                            onClick = {
                                val csv = ExportUtils.buildArchiveCsv(archive)
                                val f = ExportUtils.writeTextExport(context, "SLR_Archive_${archive.monthKey}.csv", csv)
                                ExportUtils.shareFile(context, f, "text/csv", "Export Archive CSV")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("CSV")
                        }
                        OutlinedButton(
                            onClick = {
                                val json = codec.monthlyArchiveToJson(archive)
                                val f = ExportUtils.writeTextExport(context, "${archive.monthKey}-${archive.monthName}.json", json)
                                ExportUtils.shareFile(context, f, "application/json", "Export Archive JSON")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("JSON")
                        }
                    }
                    if (canClose) {
                        OutlinedButton(
                            onClick = { showCorrectionDialog = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Icon(Icons.Rounded.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Correction Record (Preserves Original Archive)")
                        }
                    }
                }

                // Item Summary Search & Table
                item {
                    OutlinedTextField(
                        value = itemSearch,
                        onValueChange = { itemSearch = it },
                        label = { Text("Search Sold Items in Archive") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    val filteredItems = archive.itemSummary.filter {
                        itemSearch.isBlank() || it.itemName.lowercase().contains(itemSearch.trim().lowercase())
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Monthly Item Summary (${filteredItems.size})", style = MaterialTheme.typography.titleMedium)
                            filteredItems.forEachIndexed { idx, item ->
                                if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${item.itemName} (Qty: ${item.quantitySold})", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                    RupeeAmountText(paise = item.totalAmountPaise)
                                }
                            }
                        }
                    }
                }

                if (archive.corrections.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Correction Records", style = MaterialTheme.typography.titleSmall)
                                archive.corrections.forEach { c ->
                                    Text("${c.description} • ${MoneyUtils.formatInr(c.adjustmentAmountPaise)} (${c.targetPaymentMode.label}) by @${c.createdBy}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                item { Text("Archived Sales (${archive.sales.size})", style = MaterialTheme.typography.titleMedium) }
                items(archive.sales, key = { it.saleId }) { sale ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("${sale.dateDisplay} • ${sale.timeDisplay}", style = MaterialTheme.typography.labelMedium)
                                PaymentModeBadge(sale.paymentMode)
                            }
                            sale.items.forEach { it ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${it.itemName} × ${it.quantity}", style = MaterialTheme.typography.bodySmall)
                                    RupeeAmountText(paise = it.itemTotalPaise, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClosingConfirm) {
        ConfirmActionDialog(
            title = "Archive & Close ${DateUtils.monthName(m)} $y?",
            message = "This will archive all ${DateUtils.monthName(m)} $y sales to GitHub, verify totals & item counts, and ONLY THEN clear the active Daily Sales list for that month. Ba Bu customers and balances will NOT be touched.",
            confirmLabel = "Archive & Close",
            onConfirm = {
                showClosingConfirm = false
                onTriggerClosing(y, m)
            },
            onDismiss = { showClosingConfirm = false }
        )
    }

    if (showCorrectionDialog) {
        var desc by remember { mutableStateOf("") }
        var amt by remember { mutableStateOf("") }
        var mode by remember { mutableStateOf(PaymentMode.CASH) }
        AlertDialog(
            onDismissRequest = { showCorrectionDialog = false },
            title = { Text("Add Archive Correction") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Reason / Description *") })
                    OutlinedTextField(
                        value = amt,
                        onValueChange = { amt = it },
                        label = { Text("Adjustment (₹, e.g. 500 or -200) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PaymentMode.entries.forEach { pm ->
                            FilterChip(selected = mode == pm, onClick = { mode = pm }, label = { Text(pm.label.substringBefore(" ")) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val paise = MoneyUtils.parseRupeesToPaise(amt, allowNegative = true) ?: return@Button
                    if (desc.isBlank()) return@Button
                    showCorrectionDialog = false
                    onAddCorrection(y, m, desc, paise, mode)
                }) { Text("Save Correction") }
            },
            dismissButton = { TextButton(onClick = { showCorrectionDialog = false }) { Text("Cancel") } }
        )
    }
}
