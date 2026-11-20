package com.example.ui.babu

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreditScore
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.domain.model.BaItemDetail
import com.example.domain.model.BaPaymentMethod
import com.example.domain.model.BaTransaction
import com.example.domain.model.BaTransactionType
import com.example.domain.model.QuickItemPreset
import com.example.ui.AppMainUiState
import com.example.ui.components.EmptyLedgerCard
import com.example.ui.components.RupeeAmountText
import com.example.ui.theme.CashGreen
import com.example.ui.theme.CreditCrimson
import com.example.utils.DateUtils
import com.example.utils.MoneyUtils
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    phoneNumber: String,
    uiState: AppMainUiState,
    onBack: () -> Unit,
    onAddBaCredit: (phone: String, items: List<BaItemDetail>, note: String, date: LocalDate, time: LocalTime) -> Unit,
    onRecordPayment: (phone: String, amountPaise: Long, method: BaPaymentMethod, pct: Double?, note: String, date: LocalDate, time: LocalTime) -> Unit,
    onCorrectOrReverseTx: (tx: BaTransaction, newAmountPaise: Long?, reason: String) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val snap = uiState.snapshot ?: return
    val customer = snap.customers.firstOrNull { it.phoneNumber == phoneNumber }
    val history = remember(snap.baTransactions, phoneNumber) {
        snap.baTransactions
            .filter { it.customerPhoneNumber == phoneNumber }
            .sortedByDescending { it.isoTimestamp }
    }

    var showAddCreditSheet by remember { mutableStateOf(false) }
    var showReceivePaymentSheet by remember { mutableStateOf(false) }
    var correctingTx by remember { mutableStateOf<BaTransaction?>(null) }

    val canWrite = snap.currentUserRole.canWriteTransactions()
    val canCorrect = snap.currentUserRole.canDeleteOrReverse()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(customer?.customerName ?: "Customer Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (customer == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                EmptyLedgerCard(
                    title = "Customer not found",
                    subtitle = "No customer record found for $phoneNumber.",
                    actionLabel = "Back to Ba Bu",
                    onAction = onBack
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Profile Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(customer.customerName, style = MaterialTheme.typography.headlineSmall)
                                Text(
                                    "${customer.phoneNumber}${if (customer.vengOrKhua.isNotBlank()) " • ${customer.vengOrKhua}" else ""}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (customer.notes.isNotBlank()) {
                                    Text(
                                        customer.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Surface(
                            color = if (customer.currentPendingBaPaise > 0L) CreditCrimson.copy(alpha = 0.1f) else CashGreen.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Current Pending Ba", style = MaterialTheme.typography.titleMedium)
                                RupeeAmountText(
                                    paise = customer.currentPendingBaPaise,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = if (customer.currentPendingBaPaise > 0L) CreditCrimson else CashGreen
                                )
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Total Credit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                RupeeAmountText(paise = customer.totalCreditPaise, style = MaterialTheme.typography.titleSmall)
                            }
                            Column {
                                Text("Total Paid", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                RupeeAmountText(paise = customer.totalPaidPaise, style = MaterialTheme.typography.titleSmall, color = CashGreen)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Last Transaction", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    customer.lastTransactionDateDisplay.ifBlank { "—" },
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }
                    }
                }
            }

            // Action Buttons: + Add Ba, Receive Ba Payment, Send SMS Reminder
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canWrite) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { showAddCreditSheet = true },
                                colors = ButtonDefaults.buttonColors(containerColor = CreditCrimson),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("add_ba_credit_button")
                            ) {
                                Icon(Icons.Rounded.CreditScore, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("+ Add Ba")
                            }
                            Button(
                                onClick = { showReceivePaymentSheet = true },
                                enabled = customer.currentPendingBaPaise > 0L,
                                colors = ButtonDefaults.buttonColors(containerColor = CashGreen),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("receive_ba_payment_button")
                            ) {
                                Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pay Ba")
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val recentItems = history
                                .filter { !it.reversed && it.type == BaTransactionType.CREDIT }
                                .take(2)
                                .flatMap { it.items }
                                .take(3)
                                .joinToString(", ") { "${it.itemName} x${it.quantity}" }
                            val itemSuffix = if (recentItems.isNotBlank()) " Recent items: $recentItems." else ""
                            val body = "Hello ${customer.customerName}, your current pending Ba at ${snap.shopConfig.shopName} is ${MoneyUtils.formatInr(customer.currentPendingBaPaise)}.$itemSuffix Please clear your outstanding balance when convenient. Thank you."
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("smsto:${customer.phoneNumber}")
                                putExtra("sms_body", body)
                            }
                            context.startActivity(Intent.createChooser(intent, "Send SMS Reminder"))
                        },
                        enabled = customer.currentPendingBaPaise > 0L,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("send_sms_reminder_button")
                    ) {
                        Icon(Icons.Rounded.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Send SMS Reminder")
                    }
                }
            }

            item {
                Text("Transaction History (${history.size})", style = MaterialTheme.typography.titleMedium)
            }

            if (history.isEmpty()) {
                item {
                    EmptyLedgerCard(
                        title = "No Ba transactions yet",
                        subtitle = "Add credit items or record partial/full payments to build this customer's ledger."
                    )
                }
            } else {
                items(history, key = { it.transactionId }) { tx ->
                    BaTransactionHistoryCard(
                        tx = tx,
                        canCorrect = canCorrect && !tx.reversed,
                        onCorrectOrReverse = { correctingTx = tx }
                    )
                }
            }
        }
    }

    if (showAddCreditSheet && customer != null) {
        AddBaCreditSheet(
            customerName = customer.customerName,
            currentPendingBa = customer.currentPendingBaPaise,
            quickItems = snap.categories.quickItems,
            timezoneId = snap.shopConfig.timezoneId,
            onDismiss = { showAddCreditSheet = false },
            onSubmit = { items, note, date, time ->
                showAddCreditSheet = false
                onAddBaCredit(customer.phoneNumber, items, note, date, time)
            }
        )
    }

    if (showReceivePaymentSheet && customer != null) {
        ReceiveBaPaymentSheet(
            customerName = customer.customerName,
            pendingBaPaise = customer.currentPendingBaPaise,
            timezoneId = snap.shopConfig.timezoneId,
            onDismiss = { showReceivePaymentSheet = false },
            onConfirmPayment = { amountPaise, method, pct, note, date, time ->
                showReceivePaymentSheet = false
                onRecordPayment(customer.phoneNumber, amountPaise, method, pct, note, date, time)
            }
        )
    }

    if (correctingTx != null) {
        CorrectOrReverseTxDialog(
            tx = correctingTx!!,
            onDismiss = { correctingTx = null },
            onConfirm = { newAmount, reason ->
                val target = correctingTx!!
                correctingTx = null
                onCorrectOrReverseTx(target, newAmount, reason)
            }
        )
    }
}

@Composable
private fun BaTransactionHistoryCard(
    tx: BaTransaction,
    canCorrect: Boolean,
    onCorrectOrReverse: () -> Unit
) {
    val isPayment = tx.type == BaTransactionType.PAYMENT
    val accent = when {
        tx.reversed -> MaterialTheme.colorScheme.onSurfaceVariant
        isPayment -> CashGreen
        else -> CreditCrimson
    }
    val containerColor = when {
        tx.reversed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        isPayment -> CashGreen.copy(alpha = 0.08f)
        else -> CreditCrimson.copy(alpha = 0.07f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (tx.reversed) "${tx.type.label} (REVERSED)" else tx.type.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = accent,
                        textDecoration = if (tx.reversed) TextDecoration.LineThrough else TextDecoration.None
                    )
                    Text(
                        "${tx.dateDisplay} • ${tx.timeDisplay}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RupeeAmountText(
                    paise = tx.amountPaise,
                    style = MaterialTheme.typography.titleMedium,
                    color = accent
                )
            }

            if (tx.items.isNotEmpty()) {
                tx.items.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${item.itemName} (${MoneyUtils.formatInr(item.pricePaise)} × ${item.quantity})",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        RupeeAmountText(paise = item.itemTotalPaise, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (tx.paymentMethod != null) {
                val pctInfo = tx.percentageUsed?.let { " (${it.toInt()}%)" } ?: ""
                Text(
                    "Method: ${tx.paymentMethod.label}$pctInfo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (tx.note.isNotBlank()) {
                Text("Note: ${tx.note}", style = MaterialTheme.typography.bodySmall)
            }
            if (tx.reversed && !tx.reversalReason.isNullOrBlank()) {
                Text("Reversal reason: ${tx.reversalReason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Prev: ${MoneyUtils.formatInr(tx.previousBalancePaise)} → Bal: ${MoneyUtils.formatInr(tx.updatedBalancePaise)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (canCorrect) {
                    TextButton(onClick = onCorrectOrReverse) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Correct / Reverse")
                    }
                }
            }
        }
    }
}

private data class DraftBaItem(val name: String = "", val priceText: String = "", val qtyText: String = "1") {
    fun toBaItem(): BaItemDetail? {
        val p = MoneyUtils.parseRupeesToPaise(priceText) ?: return null
        val q = qtyText.toIntOrNull() ?: return null
        if (name.isBlank() || q <= 0 || p < 0L) return null
        return BaItemDetail(name.trim(), p, q, p * q)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddBaCreditSheet(
    customerName: String,
    currentPendingBa: Long,
    quickItems: List<QuickItemPreset>,
    timezoneId: String,
    onDismiss: () -> Unit,
    onSubmit: (List<BaItemDetail>, String, LocalDate, LocalTime) -> Unit
) {
    val rows = remember { mutableStateListOf(DraftBaItem()) }
    var note by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(DateUtils.todayDate(timezoneId).toString()) }
    var timeText by remember {
        val t = DateUtils.nowTime(timezoneId)
        mutableStateOf("%02d:%02d".format(t.hour, t.minute))
    }

    val validItems = rows.mapNotNull { it.toBaItem() }
    val creditTotal = validItems.sumOf { it.itemTotalPaise }
    val newBa = currentPendingBa + creditTotal

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("+ Add Ba for $customerName", style = MaterialTheme.typography.headlineSmall)

            if (quickItems.isNotEmpty()) {
                Text("Frequent Items", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickItems.forEach { preset ->
                        AssistChip(
                            onClick = {
                                val emptyIdx = rows.indexOfFirst { it.name.isBlank() && it.priceText.isBlank() }
                                val entry = DraftBaItem(preset.name, MoneyUtils.paiseToPlainRupeeString(preset.defaultPricePaise), "1")
                                if (emptyIdx >= 0) rows[emptyIdx] = entry else rows.add(entry)
                            },
                            label = { Text("${preset.name} (${MoneyUtils.formatInr(preset.defaultPricePaise)})") }
                        )
                    }
                }
            }

            rows.forEachIndexed { idx, row ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Credit Item #${idx + 1}", style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RupeeAmountText(paise = row.toBaItem()?.itemTotalPaise ?: 0L, style = MaterialTheme.typography.labelLarge)
                                if (rows.size > 1) {
                                    IconButton(onClick = { rows.removeAt(idx) }) { Icon(Icons.Rounded.Close, contentDescription = "Remove") }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = row.name,
                            onValueChange = { rows[idx] = row.copy(name = it) },
                            label = { Text("Item Name *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("ba_item_name_$idx")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = row.priceText,
                                onValueChange = { rows[idx] = row.copy(priceText = it) },
                                label = { Text("Price (₹) *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1.4f).testTag("ba_item_price_$idx")
                            )
                            OutlinedTextField(
                                value = row.qtyText,
                                onValueChange = { rows[idx] = row.copy(qtyText = it.filter { c -> c.isDigit() }) },
                                label = { Text("Qty *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(0.8f).testTag("ba_item_qty_$idx")
                            )
                        }
                    }
                }
            }

            OutlinedButton(onClick = { rows.add(DraftBaItem()) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Add Another Credit Item")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = dateText, onValueChange = { dateText = it }, label = { Text("Date") }, singleLine = true, modifier = Modifier.weight(1.2f))
                OutlinedTextField(value = timeText, onValueChange = { timeText = it }, label = { Text("Time") }, singleLine = true, modifier = Modifier.weight(0.9f))
            }

            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Optional Note") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Surface(color = CreditCrimson.copy(alpha = 0.1f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Previous Ba:", style = MaterialTheme.typography.bodySmall)
                        RupeeAmountText(paise = currentPendingBa, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("This Credit:", style = MaterialTheme.typography.titleSmall)
                        RupeeAmountText(paise = creditTotal, style = MaterialTheme.typography.titleSmall, color = CreditCrimson)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Updated Pending Ba:", style = MaterialTheme.typography.titleMedium)
                        RupeeAmountText(paise = newBa, style = MaterialTheme.typography.titleMedium, color = CreditCrimson)
                    }
                }
            }

            Button(
                onClick = {
                    val items = rows.mapNotNull { it.toBaItem() }
                    val d = DateUtils.parseLocalDate(dateText.trim()) ?: DateUtils.todayDate(timezoneId)
                    val t = try {
                        val p = timeText.trim().split(":")
                        LocalTime.of(p[0].toInt().coerceIn(0, 23), p[1].toInt().coerceIn(0, 59))
                    } catch (_: Exception) {
                        DateUtils.nowTime(timezoneId)
                    }
                    onSubmit(items, note, d, t)
                },
                enabled = validItems.isNotEmpty() && validItems.size == rows.size && creditTotal > 0L,
                colors = ButtonDefaults.buttonColors(containerColor = CreditCrimson),
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp).testTag("save_ba_credit_button")
            ) {
                Text("Save Ba Credit")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiveBaPaymentSheet(
    customerName: String,
    pendingBaPaise: Long,
    timezoneId: String,
    onDismiss: () -> Unit,
    onConfirmPayment: (amountPaise: Long, method: BaPaymentMethod, pct: Double?, note: String, date: LocalDate, time: LocalTime) -> Unit
) {
    var amountInput by remember { mutableStateOf("") }
    var selectedPct by remember { mutableStateOf<Double?>(null) }
    var customPctInput by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf(BaPaymentMethod.CASH) }
    var note by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(DateUtils.todayDate(timezoneId).toString()) }
    var timeText by remember {
        val t = DateUtils.nowTime(timezoneId)
        mutableStateOf("%02d:%02d".format(t.hour, t.minute))
    }
    var showConfirmStep by remember { mutableStateOf(false) }

    val parsedAmountPaise = MoneyUtils.parseRupeesToPaise(amountInput)
    val isAmountValid = parsedAmountPaise != null && parsedAmountPaise > 0L && parsedAmountPaise <= pendingBaPaise
    val remainingPaise = if (parsedAmountPaise != null) (pendingBaPaise - parsedAmountPaise).coerceAtLeast(0L) else pendingBaPaise

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Receive Ba Payment • $customerName", style = MaterialTheme.typography.headlineSmall)

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Previous Pending Ba", style = MaterialTheme.typography.titleSmall)
                    RupeeAmountText(paise = pendingBaPaise, style = MaterialTheme.typography.titleLarge, color = CreditCrimson)
                }
            }

            // PRIMARY FEATURE: Exact Amount Payment Input
            OutlinedTextField(
                value = amountInput,
                onValueChange = {
                    amountInput = it
                    selectedPct = null
                },
                label = { Text("Enter Payment Amount (₹) *") },
                placeholder = { Text("e.g., 500") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = amountInput.isNotBlank() && !isAmountValid,
                supportingText = {
                    if (parsedAmountPaise != null && parsedAmountPaise > pendingBaPaise) {
                        Text("Payment cannot exceed pending Ba (${MoneyUtils.formatInr(pendingBaPaise)})", color = MaterialTheme.colorScheme.error)
                    } else if (parsedAmountPaise != null && parsedAmountPaise <= 0L) {
                        Text("Enter an amount greater than ₹0", color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Partial and repeated payments are supported.")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ba_payment_amount_input")
            )

            // OPTIONAL Percentage Chips
            Text("Optional Quick Percentage", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(10.0, 25.0, 50.0, 75.0, 100.0).forEach { pct ->
                    FilterChip(
                        selected = selectedPct == pct,
                        onClick = {
                            selectedPct = pct
                            val calc = MoneyUtils.calculatePercentagePaymentPaise(pendingBaPaise, pct)
                            amountInput = MoneyUtils.paiseToPlainRupeeString(calc)
                        },
                        label = { Text("${pct.toInt()}%") }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customPctInput,
                    onValueChange = {
                        customPctInput = it
                        val pct = it.toDoubleOrNull()
                        if (pct != null && pct > 0 && pct <= 100) {
                            selectedPct = pct
                            val calc = MoneyUtils.calculatePercentagePaymentPaise(pendingBaPaise, pct)
                            amountInput = MoneyUtils.paiseToPlainRupeeString(calc)
                        }
                    },
                    label = { Text("Custom % (Optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // Offline Payment Method
            Text("Payment Method", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BaPaymentMethod.entries.forEach { method ->
                    FilterChip(
                        selected = paymentMethod == method,
                        onClick = { paymentMethod = method },
                        label = { Text(method.label) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = dateText, onValueChange = { dateText = it }, label = { Text("Payment Date") }, singleLine = true, modifier = Modifier.weight(1.2f))
                OutlinedTextField(value = timeText, onValueChange = { timeText = it }, label = { Text("Time") }, singleLine = true, modifier = Modifier.weight(0.9f))
            }

            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Optional Payment Note") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            // Live Remaining Ba Preview
            Surface(
                color = CashGreen.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Remaining Ba After Payment", style = MaterialTheme.typography.titleSmall)
                    RupeeAmountText(
                        paise = remainingPaise,
                        style = MaterialTheme.typography.titleLarge,
                        color = CashGreen
                    )
                }
            }

            Button(
                onClick = { showConfirmStep = true },
                enabled = isAmountValid,
                colors = ButtonDefaults.buttonColors(containerColor = CashGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp)
                    .testTag("review_ba_payment_button")
            ) {
                Text("Review & Confirm Payment")
            }
        }
    }

    if (showConfirmStep && isAmountValid) {
        val amt = parsedAmountPaise!!
        val d = DateUtils.parseLocalDate(dateText.trim()) ?: DateUtils.todayDate(timezoneId)
        val t = try {
            val p = timeText.trim().split(":")
            LocalTime.of(p[0].toInt().coerceIn(0, 23), p[1].toInt().coerceIn(0, 59))
        } catch (_: Exception) {
            DateUtils.nowTime(timezoneId)
        }
        AlertDialog(
            onDismissRequest = { showConfirmStep = false },
            title = { Text("Confirm Ba Payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Customer: $customerName", fontWeight = FontWeight.SemiBold)
                    Text("Previous Pending Ba: ${MoneyUtils.formatInr(pendingBaPaise)}")
                    Text("Payment Amount: ${MoneyUtils.formatInr(amt)} (${paymentMethod.label})", color = CashGreen, fontWeight = FontWeight.Bold)
                    Text("Remaining Pending Ba: ${MoneyUtils.formatInr(remainingPaise)}", fontWeight = FontWeight.SemiBold)
                    Text("Date & Time: ${DateUtils.formatLocalDate(d)} • ${DateUtils.formatLocalTime(t)}")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmStep = false
                        onConfirmPayment(amt, paymentMethod, selectedPct, note, d, t)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CashGreen),
                    modifier = Modifier.testTag("confirm_ba_payment_button")
                ) {
                    Text("Confirm Payment")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmStep = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CorrectOrReverseTxDialog(
    tx: BaTransaction,
    onDismiss: () -> Unit,
    onConfirm: (newAmountPaise: Long?, reason: String) -> Unit
) {
    var correctedAmount by remember { mutableStateOf(MoneyUtils.paiseToPlainRupeeString(tx.amountPaise)) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Correct or Reverse Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Original: ${tx.type.label} of ${MoneyUtils.formatInr(tx.amountPaise)} on ${tx.dateDisplay}. Subsequent balances will be recalculated automatically.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = correctedAmount,
                    onValueChange = { correctedAmount = it },
                    label = { Text("Corrected Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Audit Reason *") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onConfirm(null, reason.ifBlank { "Reversed incorrect entry" }) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Rounded.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reverse")
                }
                Button(
                    onClick = {
                        val paise = MoneyUtils.parseRupeesToPaise(correctedAmount) ?: return@Button
                        if (paise > 0L) onConfirm(paise, reason.ifBlank { "Corrected amount" })
                    }
                ) {
                    Text("Save Correction")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
