package com.example.ui.sales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.CustomerProfile
import com.example.domain.model.PaymentMode
import com.example.domain.model.QuickItemPreset
import com.example.domain.model.SaleLineItem
import com.example.domain.model.SaleRecord
import com.example.ui.AppMainUiState
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.EmptyLedgerCard
import com.example.ui.components.PaymentModeBadge
import com.example.ui.components.RupeeAmountText
import com.example.ui.theme.CashGreen
import com.example.ui.theme.CreditCrimson
import com.example.ui.theme.OfflineIndigo
import com.example.utils.DateUtils
import com.example.utils.MoneyUtils
import java.time.LocalDate
import java.time.LocalTime

enum class SalesFilterMode(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    CUSTOM_DATE("Custom Date"),
    THIS_MONTH("This Month"),
    CUSTOM_MONTH("Custom Month"),
    THIS_YEAR("This Year")
}

@Composable
fun DailySalesScreen(
    uiState: AppMainUiState,
    openNewSaleSheetInitially: Boolean = false,
    onConsumedInitialSheet: () -> Unit = {},
    onAddSale: (List<SaleLineItem>, PaymentMode, String, LocalDate, LocalTime, String?) -> Unit,
    onEditSale: (SaleRecord, List<SaleLineItem>, PaymentMode, String) -> Unit,
    onDeleteSale: (SaleRecord) -> Unit
) {
    val snap = uiState.snapshot ?: return
    val tz = snap.shopConfig.timezoneId
    val today = remember(tz) { DateUtils.todayDate(tz) }

    var filterMode by remember { mutableStateOf(SalesFilterMode.TODAY) }
    var customDateText by remember { mutableStateOf(today.toString()) }
    var customYear by remember { mutableStateOf(today.year) }
    var customMonth by remember { mutableStateOf(today.monthValue) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var showSaleSheet by remember { mutableStateOf(openNewSaleSheetInitially) }
    var editingSale by remember { mutableStateOf<SaleRecord?>(null) }
    var deletingSale by remember { mutableStateOf<SaleRecord?>(null) }

    if (openNewSaleSheetInitially) {
        onConsumedInitialSheet()
    }

    val allValidSales = remember(snap.allActiveSalesFiles, uiState.monthlyArchivesForYear) {
        val active = snap.allActiveSalesFiles.flatMap { it.sales }.filter { !it.deleted }
        val archived = uiState.monthlyArchivesForYear.flatMap { it.sales }.filter { !it.deleted }
        (active + archived).distinctBy { it.saleId }.sortedByDescending { it.isoTimestamp }
    }

    val filteredSales = remember(allValidSales, filterMode, customDateText, customYear, customMonth, searchQuery, today) {
        val byDate = when (filterMode) {
            SalesFilterMode.TODAY -> {
                val k = DateUtils.dateKey(today.year, today.monthValue, today.dayOfMonth)
                allValidSales.filter { it.dateKey == k }
            }
            SalesFilterMode.YESTERDAY -> {
                val y = today.minusDays(1)
                val k = DateUtils.dateKey(y.year, y.monthValue, y.dayOfMonth)
                allValidSales.filter { it.dateKey == k }
            }
            SalesFilterMode.CUSTOM_DATE -> allValidSales.filter { it.dateKey == customDateText.trim() }
            SalesFilterMode.THIS_MONTH -> allValidSales.filter { it.year == today.year && it.month == today.monthValue }
            SalesFilterMode.CUSTOM_MONTH -> allValidSales.filter { it.year == customYear && it.month == customMonth }
            SalesFilterMode.THIS_YEAR -> allValidSales.filter { it.year == today.year }
        }
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) byDate
        else byDate.filter { sale ->
            sale.items.any { it.itemName.lowercase().contains(q) } ||
                sale.note.lowercase().contains(q) ||
                (sale.linkedCustomerName?.lowercase()?.contains(q) == true)
        }
    }

    val cashTotal = filteredSales.filter { it.paymentMode == PaymentMode.CASH }.sumOf { it.grandTotalPaise }
    val otherTotal = filteredSales.filter { it.paymentMode == PaymentMode.OTHER_OFFLINE }.sumOf { it.grandTotalPaise }
    val creditTotal = filteredSales.filter { it.paymentMode == PaymentMode.CREDIT_BA }.sumOf { it.grandTotalPaise }
    val grandTotal = cashTotal + otherTotal + creditTotal

    val canWrite = snap.currentUserRole.canWriteTransactions()
    val canDelete = snap.currentUserRole.canDeleteOrReverse()

    Scaffold(
        floatingActionButton = {
            if (canWrite) {
                FloatingActionButton(
                    onClick = {
                        editingSale = null
                        showSaleSheet = true
                    },
                    modifier = Modifier.testTag("fab_new_sale")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add New Sale")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Daily Sale – Hralhna Chhinchhiahna",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Active month: ${DateUtils.monthName(today.monthValue)} ${today.year} • Credit sales are tracked separately from cash.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SalesFilterMode.entries.forEach { mode ->
                        FilterChip(
                            selected = filterMode == mode,
                            onClick = {
                                filterMode = mode
                                if (mode == SalesFilterMode.CUSTOM_DATE || mode == SalesFilterMode.CUSTOM_MONTH) {
                                    showDatePickerDialog = true
                                }
                            },
                            label = {
                                val extra = when (mode) {
                                    SalesFilterMode.CUSTOM_DATE -> if (filterMode == mode) " ($customDateText)" else ""
                                    SalesFilterMode.CUSTOM_MONTH -> if (filterMode == mode) " (${DateUtils.monthName(customMonth).take(3)} $customYear)" else ""
                                    else -> ""
                                }
                                Text("${mode.label}$extra")
                            }
                        )
                    }
                }
            }

            // Search bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search item name or note...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sales_search_input")
                )
            }

            // Separate Mode Totals Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Filtered Total (${filteredSales.size} sales)", style = MaterialTheme.typography.titleSmall)
                            RupeeAmountText(paise = grandTotal, style = MaterialTheme.typography.titleLarge)
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Cash", style = MaterialTheme.typography.labelSmall, color = CashGreen)
                                RupeeAmountText(paise = cashTotal, style = MaterialTheme.typography.titleSmall, color = CashGreen)
                            }
                            Column {
                                Text("Other Offline", style = MaterialTheme.typography.labelSmall, color = OfflineIndigo)
                                RupeeAmountText(paise = otherTotal, style = MaterialTheme.typography.titleSmall, color = OfflineIndigo)
                            }
                            Column {
                                Text("Credit / Ba", style = MaterialTheme.typography.labelSmall, color = CreditCrimson)
                                RupeeAmountText(paise = creditTotal, style = MaterialTheme.typography.titleSmall, color = CreditCrimson)
                            }
                        }
                    }
                }
            }

            if (filteredSales.isEmpty()) {
                item {
                    EmptyLedgerCard(
                        title = "No sales for this filter",
                        subtitle = "Tap + to record a Cash, Other Offline, or Credit / Ba sale.",
                        actionLabel = if (canWrite) "+ New Sale" else null,
                        onAction = if (canWrite) {
                            {
                                editingSale = null
                                showSaleSheet = true
                            }
                        } else null
                    )
                }
            } else {
                items(filteredSales, key = { it.saleId }) { sale ->
                    SaleRecordCard(
                        sale = sale,
                        canEdit = canWrite && DateUtils.monthKey(sale.year, sale.month) == snap.currentActiveSales.monthKey,
                        canDelete = canDelete && DateUtils.monthKey(sale.year, sale.month) == snap.currentActiveSales.monthKey,
                        onEdit = {
                            editingSale = sale
                            showSaleSheet = true
                        },
                        onDelete = { deletingSale = sale }
                    )
                }
            }
        }
    }

    if (showSaleSheet) {
        SaleEditorSheet(
            editingSale = editingSale,
            quickItems = snap.categories.quickItems,
            customers = snap.customers,
            timezoneId = tz,
            onDismiss = { showSaleSheet = false },
            onSaveNew = { items, mode, note, date, time, customerPhone ->
                showSaleSheet = false
                onAddSale(items, mode, note, date, time, customerPhone)
            },
            onSaveEdit = { orig, items, mode, note ->
                showSaleSheet = false
                onEditSale(orig, items, mode, note)
            }
        )
    }

    if (deletingSale != null) {
        val s = deletingSale!!
        ConfirmActionDialog(
            title = "Delete Sale Record?",
            message = "Delete sale ${s.saleId} (${MoneyUtils.formatInr(s.grandTotalPaise)})? This action will be recorded in the audit log.",
            confirmLabel = "Delete",
            isDestructive = true,
            onConfirm = {
                deletingSale = null
                onDeleteSale(s)
            },
            onDismiss = { deletingSale = null }
        )
    }

    if (showDatePickerDialog) {
        CustomFilterDialog(
            isMonthMode = filterMode == SalesFilterMode.CUSTOM_MONTH,
            initialDateText = customDateText,
            initialYear = customYear,
            initialMonth = customMonth,
            onApplyDate = {
                customDateText = it
                showDatePickerDialog = false
            },
            onApplyMonth = { y, m ->
                customYear = y
                customMonth = m
                showDatePickerDialog = false
            },
            onDismiss = { showDatePickerDialog = false }
        )
    }
}

@Composable
private fun SaleRecordCard(
    sale: SaleRecord,
    canEdit: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${sale.dateDisplay} • ${sale.timeDisplay}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PaymentModeBadge(sale.paymentMode, modifier = Modifier.padding(top = 4.dp))
                }
                RupeeAmountText(paise = sale.grandTotalPaise, style = MaterialTheme.typography.titleLarge)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            sale.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${item.itemName} (${MoneyUtils.formatInr(item.pricePaise)} × ${item.quantity})",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    RupeeAmountText(paise = item.itemTotalPaise, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (!sale.linkedCustomerName.isNullOrBlank()) {
                Text(
                    "Linked Ba Bu Customer: ${sale.linkedCustomerName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CreditCrimson
                )
            }
            if (sale.note.isNotBlank()) {
                Text(
                    "Note: ${sale.note}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (canEdit || canDelete) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (canEdit) {
                        TextButton(onClick = onEdit) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Edit")
                        }
                    }
                    if (canDelete) {
                        TextButton(onClick = onDelete) {
                            Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

private data class DraftLineItem(
    val name: String = "",
    val priceText: String = "",
    val qtyText: String = "1"
) {
    fun toSaleLineItem(): SaleLineItem? {
        val p = MoneyUtils.parseRupeesToPaise(priceText) ?: return null
        val q = qtyText.toIntOrNull() ?: return null
        if (name.isBlank() || q <= 0 || p < 0L) return null
        return SaleLineItem(name.trim(), p, q, p * q)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SaleEditorSheet(
    editingSale: SaleRecord?,
    quickItems: List<QuickItemPreset>,
    customers: List<CustomerProfile>,
    timezoneId: String,
    onDismiss: () -> Unit,
    onSaveNew: (List<SaleLineItem>, PaymentMode, String, LocalDate, LocalTime, String?) -> Unit,
    onSaveEdit: (SaleRecord, List<SaleLineItem>, PaymentMode, String) -> Unit
) {
    val draftItems = remember(editingSale) {
        mutableStateListOf<DraftLineItem>().apply {
            if (editingSale != null && editingSale.items.isNotEmpty()) {
                editingSale.items.forEach {
                    add(DraftLineItem(it.itemName, MoneyUtils.paiseToPlainRupeeString(it.pricePaise), it.quantity.toString()))
                }
            } else {
                add(DraftLineItem())
            }
        }
    }
    var paymentMode by remember(editingSale) { mutableStateOf(editingSale?.paymentMode ?: PaymentMode.CASH) }
    var note by remember(editingSale) { mutableStateOf(editingSale?.note ?: "") }
    var dateText by remember { mutableStateOf(editingSale?.dateKey ?: DateUtils.todayDate(timezoneId).toString()) }
    var timeText by remember {
        val t = DateUtils.nowTime(timezoneId)
        mutableStateOf("%02d:%02d".format(t.hour, t.minute))
    }
    var selectedCustomer by remember { mutableStateOf<CustomerProfile?>(null) }
    var customerMenuExpanded by remember { mutableStateOf(false) }

    val parsedItems = draftItems.mapNotNull { it.toSaleLineItem() }
    val grandTotalPaise = parsedItems.sumOf { it.itemTotalPaise }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (editingSale == null) "Add New Sale" else "Edit Sale",
                style = MaterialTheme.typography.headlineSmall
            )

            // Quick-select chips
            if (quickItems.isNotEmpty()) {
                Text("Quick Add Item", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickItems.forEach { preset ->
                        AssistChip(
                            onClick = {
                                val emptyIdx = draftItems.indexOfFirst { it.name.isBlank() && it.priceText.isBlank() }
                                val entry = DraftLineItem(
                                    name = preset.name,
                                    priceText = MoneyUtils.paiseToPlainRupeeString(preset.defaultPricePaise),
                                    qtyText = "1"
                                )
                                if (emptyIdx >= 0) draftItems[emptyIdx] = entry else draftItems.add(entry)
                            },
                            label = { Text("${preset.name} (${MoneyUtils.formatInr(preset.defaultPricePaise)})") }
                        )
                    }
                }
            }

            draftItems.forEachIndexed { idx, row ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Product #${idx + 1}", style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val rowTotal = row.toSaleLineItem()?.itemTotalPaise ?: 0L
                                RupeeAmountText(paise = rowTotal, style = MaterialTheme.typography.labelLarge)
                                if (draftItems.size > 1) {
                                    IconButton(onClick = { draftItems.removeAt(idx) }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Remove item")
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = row.name,
                            onValueChange = { draftItems[idx] = row.copy(name = it) },
                            label = { Text("Item Name *") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sale_item_name_$idx")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = row.priceText,
                                onValueChange = { draftItems[idx] = row.copy(priceText = it) },
                                label = { Text("Price (₹) *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1.4f)
                                    .testTag("sale_item_price_$idx")
                            )
                            OutlinedTextField(
                                value = row.qtyText,
                                onValueChange = { draftItems[idx] = row.copy(qtyText = it.filter { c -> c.isDigit() }) },
                                label = { Text("Qty *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.8f)
                                    .testTag("sale_item_qty_$idx")
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { draftItems.add(DraftLineItem()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Add Another Product")
            }

            // Payment mode (strictly Cash, Other Offline Payment, Credit / Ba)
            Text("Payment Mode", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PaymentMode.entries.forEach { mode ->
                    FilterChip(
                        selected = paymentMode == mode,
                        onClick = { paymentMode = mode },
                        label = { Text(mode.label) }
                    )
                }
            }

            if (editingSale == null && paymentMode == PaymentMode.CREDIT_BA && customers.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = customerMenuExpanded,
                    onExpandedChange = { customerMenuExpanded = !customerMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCustomer?.let { "${it.customerName} (${it.phoneNumber})" } ?: "Optional: Link Ba Bu Customer",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = customerMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = customerMenuExpanded,
                        onDismissRequest = { customerMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Walk-in Credit (Not linked to customer)") },
                            onClick = {
                                selectedCustomer = null
                                customerMenuExpanded = false
                            }
                        )
                        customers.forEach { cust ->
                            DropdownMenuItem(
                                text = { Text("${cust.customerName} • ${cust.phoneNumber}") },
                                onClick = {
                                    selectedCustomer = cust
                                    customerMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (editingSale == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { timeText = it },
                        label = { Text("Time (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(0.9f)
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Optional Note") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Grand Total (${parsedItems.size} valid items)", style = MaterialTheme.typography.titleSmall)
                    RupeeAmountText(
                        paise = grandTotalPaise,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Button(
                onClick = {
                    val items = draftItems.mapNotNull { it.toSaleLineItem() }
                    if (items.isEmpty() || items.size != draftItems.size) return@Button
                    if (editingSale == null) {
                        val parsedDate = DateUtils.parseLocalDate(dateText.trim()) ?: DateUtils.todayDate(timezoneId)
                        val parsedTime = try {
                            val p = timeText.trim().split(":")
                            LocalTime.of(p[0].toInt().coerceIn(0, 23), p[1].toInt().coerceIn(0, 59))
                        } catch (_: Exception) {
                            DateUtils.nowTime(timezoneId)
                        }
                        onSaveNew(items, paymentMode, note, parsedDate, parsedTime, selectedCustomer?.phoneNumber)
                    } else {
                        onSaveEdit(editingSale, items, paymentMode, note)
                    }
                },
                enabled = parsedItems.isNotEmpty() && parsedItems.size == draftItems.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp)
                    .testTag("save_sale_button")
            ) {
                Text(if (editingSale == null) "Save Sale to GitHub" else "Update Sale")
            }
        }
    }
}

@Composable
private fun CustomFilterDialog(
    isMonthMode: Boolean,
    initialDateText: String,
    initialYear: Int,
    initialMonth: Int,
    onApplyDate: (String) -> Unit,
    onApplyMonth: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var dateInput by remember { mutableStateOf(initialDateText) }
    var yearInput by remember { mutableStateOf(initialYear.toString()) }
    var monthInput by remember { mutableStateOf(initialMonth.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isMonthMode) "Select Month & Year" else "Select Date") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!isMonthMode) {
                    OutlinedTextField(
                        value = dateInput,
                        onValueChange = { dateInput = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true
                    )
                } else {
                    OutlinedTextField(
                        value = yearInput,
                        onValueChange = { yearInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Year (e.g. 2026)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = monthInput,
                        onValueChange = { monthInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Month (1 - 12)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (!isMonthMode) onApplyDate(dateInput.trim())
                else {
                    val y = yearInput.toIntOrNull() ?: initialYear
                    val m = (monthInput.toIntOrNull() ?: initialMonth).coerceIn(1, 12)
                    onApplyMonth(y, m)
                }
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
