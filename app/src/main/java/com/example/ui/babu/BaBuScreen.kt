package com.example.ui.babu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.CustomerProfile
import com.example.ui.AppMainUiState
import com.example.ui.components.EmptyLedgerCard
import com.example.ui.components.RupeeAmountText
import com.example.ui.theme.CashGreen
import com.example.ui.theme.CreditCrimson
import com.example.utils.MoneyUtils

enum class CustomerSortMode(val label: String) {
    PENDING_DESC("Pending Ba"),
    RECENT("Recent"),
    NAME_ASC("Name A-Z")
}

@Composable
fun BaBuScreen(
    uiState: AppMainUiState,
    openAddCustomerInitially: Boolean = false,
    onConsumedInitialDialog: () -> Unit = {},
    onOpenCustomerDetail: (String) -> Unit,
    onAddCustomer: (name: String, phone: String, veng: String, initialBaPaise: Long, notes: String) -> Unit,
    onEditCustomer: (original: CustomerProfile, name: String, veng: String, notes: String) -> Unit
) {
    val snap = uiState.snapshot ?: return
    val canWrite = snap.currentUserRole.canWriteTransactions()

    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(CustomerSortMode.PENDING_DESC) }
    var showCustomerDialog by remember { mutableStateOf(openAddCustomerInitially) }
    var editingCustomer by remember { mutableStateOf<CustomerProfile?>(null) }

    if (openAddCustomerInitially) onConsumedInitialDialog()

    val filteredCustomers = remember(snap.customers, searchQuery, sortMode) {
        val q = searchQuery.trim().lowercase()
        val list = if (q.isEmpty()) snap.customers else {
            snap.customers.filter {
                it.customerName.lowercase().contains(q) ||
                    it.phoneNumber.lowercase().contains(q) ||
                    it.vengOrKhua.lowercase().contains(q)
            }
        }
        when (sortMode) {
            CustomerSortMode.PENDING_DESC -> list.sortedByDescending { it.currentPendingBaPaise }
            CustomerSortMode.RECENT -> list.sortedByDescending { it.lastTransactionIso }
            CustomerSortMode.NAME_ASC -> list.sortedBy { it.customerName.lowercase() }
        }
    }

    val totalPendingBa = snap.customers.sumOf { it.currentPendingBaPaise }

    Scaffold(
        floatingActionButton = {
            if (canWrite) {
                FloatingActionButton(
                    onClick = {
                        editingCustomer = null
                        showCustomerDialog = true
                    },
                    modifier = Modifier.testTag("fab_new_customer")
                ) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = "Add Customer")
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
                    Text("Ba Bu – Customer Credit Ledger", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Each customer has an isolated JSON file keyed by unique phone number.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Pending Customer Ba", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "${snap.customers.size} registered • ${snap.customers.count { it.currentPendingBaPaise > 0 }} with balance",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        RupeeAmountText(
                            paise = totalPendingBa,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search name, phone, or Veng / Khua...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("babu_search_input")
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CustomerSortMode.entries.forEach { mode ->
                        FilterChip(
                            selected = sortMode == mode,
                            onClick = { sortMode = mode },
                            label = { Text("Sort: ${mode.label}") }
                        )
                    }
                }
            }

            if (filteredCustomers.isEmpty()) {
                item {
                    EmptyLedgerCard(
                        title = "No Ba Bu customers yet",
                        subtitle = "Register your credit customers with their unique phone number to track Ba & partial payments.",
                        actionLabel = if (canWrite) "+ New Customer" else null,
                        onAction = if (canWrite) {
                            {
                                editingCustomer = null
                                showCustomerDialog = true
                            }
                        } else null
                    )
                }
            } else {
                items(filteredCustomers, key = { it.phoneNumber }) { customer ->
                    CustomerRowCard(
                        customer = customer,
                        canWrite = canWrite,
                        onClick = { onOpenCustomerDetail(customer.phoneNumber) },
                        onEdit = {
                            editingCustomer = customer
                            showCustomerDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showCustomerDialog) {
        CustomerEditorDialog(
            editingCustomer = editingCustomer,
            onDismiss = { showCustomerDialog = false },
            onSaveNew = { name, phone, veng, initialBaPaise, notes ->
                showCustomerDialog = false
                onAddCustomer(name, phone, veng, initialBaPaise, notes)
            },
            onSaveEdit = { orig, name, veng, notes ->
                showCustomerDialog = false
                onEditCustomer(orig, name, veng, notes)
            }
        )
    }
}

@Composable
private fun CustomerRowCard(
    customer: CustomerProfile,
    canWrite: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("customer_card_${customer.phoneNumber}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(customer.customerName, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Phone, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${customer.phoneNumber}${if (customer.vengOrKhua.isNotBlank()) " • ${customer.vengOrKhua}" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (customer.lastTransactionDateDisplay.isNotBlank()) {
                    Text(
                        "Last activity: ${customer.lastTransactionDateDisplay}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Pending Ba", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RupeeAmountText(
                    paise = customer.currentPendingBaPaise,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (customer.currentPendingBaPaise > 0L) CreditCrimson else CashGreen
                )
            }
            if (canWrite) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit customer")
                }
            }
        }
    }
}

@Composable
private fun CustomerEditorDialog(
    editingCustomer: CustomerProfile?,
    onDismiss: () -> Unit,
    onSaveNew: (name: String, phone: String, veng: String, initialBaPaise: Long, notes: String) -> Unit,
    onSaveEdit: (orig: CustomerProfile, name: String, veng: String, notes: String) -> Unit
) {
    var name by remember(editingCustomer) { mutableStateOf(editingCustomer?.customerName ?: "") }
    var phone by remember(editingCustomer) { mutableStateOf(editingCustomer?.phoneNumber ?: "") }
    var veng by remember(editingCustomer) { mutableStateOf(editingCustomer?.vengOrKhua ?: "") }
    var initialBa by remember(editingCustomer) { mutableStateOf("") }
    var notes by remember(editingCustomer) { mutableStateOf(editingCustomer?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingCustomer == null) "Register Customer" else "Edit Customer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Customer Name *") },
                    singleLine = true,
                    modifier = Modifier.testTag("customer_name_input")
                )
                if (editingCustomer == null) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number (Unique) *") },
                        placeholder = { Text("10-digit mobile or 91XXXXXXXXXX") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.testTag("customer_phone_input")
                    )
                }
                OutlinedTextField(
                    value = veng,
                    onValueChange = { veng = it },
                    label = { Text("Veng / Khua") },
                    singleLine = true,
                    modifier = Modifier.testTag("customer_veng_input")
                )
                if (editingCustomer == null) {
                    OutlinedTextField(
                        value = initialBa,
                        onValueChange = { initialBa = it },
                        label = { Text("Initial Ba (₹ Optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (editingCustomer == null) {
                        val initPaise = if (initialBa.isBlank()) 0L else MoneyUtils.parseRupeesToPaise(initialBa) ?: return@Button
                        onSaveNew(name, phone, veng, initPaise, notes)
                    } else {
                        onSaveEdit(editingCustomer, name, veng, notes)
                    }
                },
                enabled = name.isNotBlank() && (editingCustomer != null || phone.filter { it.isDigit() }.length >= 10),
                modifier = Modifier.testTag("save_customer_button")
            ) {
                Text(if (editingCustomer == null) "Register" else "Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
