package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CreditScore
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.AppMainUiState
import com.example.ui.components.HeroSyncCard
import com.example.ui.components.MetricGridCard
import com.example.ui.components.PaymentModeBadge
import com.example.ui.components.RupeeAmountText
import com.example.ui.theme.CashGreen
import com.example.ui.theme.CreditCrimson
import com.example.ui.theme.OfflineIndigo
import com.example.ui.theme.WarningAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    uiState: AppMainUiState,
    onSwitchShop: () -> Unit,
    onSyncNow: () -> Unit,
    onQuickNewSale: () -> Unit,
    onQuickAddBa: () -> Unit,
    onQuickReceivePayment: () -> Unit,
    onQuickNewCustomer: () -> Unit,
    onNavigateDailySales: () -> Unit,
    onNavigateBaBu: () -> Unit,
    onNavigateMonthlyArchive: () -> Unit,
    onNavigateReports: () -> Unit,
    onNavigateBackups: () -> Unit,
    onOpenCustomer: (String) -> Unit
) {
    val snap = uiState.snapshot ?: return
    val m = uiState.dashboardMetrics
    val canWrite = snap.currentUserRole.canWriteTransactions()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeroSyncCard(
                shopName = snap.shopConfig.shopName,
                repoFullName = "${snap.binding.repoOwner}/${snap.binding.repoName}",
                syncSuccess = snap.lastSyncSuccess,
                lastSyncLabel = "Synced ${snap.lastSyncedAtIso.take(16).replace('T', ' ')}",
                role = snap.currentUserRole,
                onSwitchShop = onSwitchShop,
                onSyncNow = onSyncNow
            )
        }

        // 8 Main Financial Cards (2-column grid)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Financial Summary", style = MaterialTheme.typography.titleMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricGridCard(
                        title = "Today's Total Sales",
                        amountPaise = m.todayTotalSalesPaise,
                        icon = Icons.Rounded.Today,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricGridCard(
                        title = "Today's Cash Sales",
                        amountPaise = m.todayCashSalesPaise,
                        icon = Icons.Rounded.Payments,
                        accentColor = CashGreen,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricGridCard(
                        title = "Other Offline Payments",
                        amountPaise = m.todayOtherOfflinePaise,
                        icon = Icons.Rounded.AccountBalanceWallet,
                        accentColor = OfflineIndigo,
                        modifier = Modifier.weight(1f)
                    )
                    MetricGridCard(
                        title = "Today's Credit / Ba Added",
                        amountPaise = m.todayCreditSalesPaise,
                        icon = Icons.Rounded.CreditScore,
                        accentColor = CreditCrimson,
                        subtitle = "Not cash income",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricGridCard(
                        title = "Today's Ba Collection",
                        amountPaise = m.todayBaCollectionPaise,
                        icon = Icons.Rounded.Savings,
                        accentColor = CashGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MetricGridCard(
                        title = "Total Pending Ba",
                        amountPaise = m.totalPendingBaPaise,
                        icon = Icons.Rounded.MenuBook,
                        accentColor = WarningAmber,
                        subtitle = "${snap.customers.count { it.currentPendingBaPaise > 0 }} customers",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricGridCard(
                        title = "Current Month Sales",
                        amountPaise = m.currentMonthSalesPaise,
                        icon = Icons.Rounded.CalendarMonth,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricGridCard(
                        title = "Current Year Sales",
                        amountPaise = m.currentYearSalesPaise,
                        icon = Icons.Rounded.TrendingUp,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Quick Actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quick Actions", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (canWrite) {
                        QuickChip("+ New Sale", Icons.Rounded.AddShoppingCart, "quick_new_sale", onQuickNewSale)
                        QuickChip("+ Add Ba", Icons.Rounded.CreditScore, "quick_add_ba", onQuickAddBa)
                        QuickChip("+ Receive Ba Payment", Icons.Rounded.Payments, "quick_receive_payment", onQuickReceivePayment)
                        QuickChip("+ New Customer", Icons.Rounded.PersonAdd, "quick_new_customer", onQuickNewCustomer)
                    }
                    QuickChip("View Daily Sales", Icons.Rounded.ReceiptLong, "quick_view_sales", onNavigateDailySales)
                    QuickChip("View Ba Bu", Icons.Rounded.MenuBook, "quick_view_babu", onNavigateBaBu)
                    QuickChip("Monthly Archive", Icons.Rounded.Archive, "quick_monthly_archive", onNavigateMonthlyArchive)
                    QuickChip("Financial Reports", Icons.Rounded.Assessment, "quick_reports", onNavigateReports)
                }
            }
        }

        // Monthly Sales Overview Bar Chart
        if (m.monthlyDailyTotals.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Monthly Sales Overview", style = MaterialTheme.typography.titleMedium)
                        val barColor = MaterialTheme.colorScheme.primary
                        val trackColor = MaterialTheme.colorScheme.surfaceVariant
                        val dailySlice = m.monthlyDailyTotals.takeLast(14)
                        val maxVal = (dailySlice.maxOfOrNull { it.totalSalesPaise } ?: 1L).coerceAtLeast(1L)

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                        ) {
                            val barCount = dailySlice.size.coerceAtLeast(1)
                            val slotW = size.width / barCount
                            val barW = (slotW * 0.56f).coerceAtLeast(6f)
                            dailySlice.forEachIndexed { idx, entry ->
                                val x = idx * slotW + (slotW - barW) / 2f
                                drawRoundRect(
                                    color = trackColor,
                                    topLeft = Offset(x, 0f),
                                    size = Size(barW, size.height),
                                    cornerRadius = CornerRadius(6f, 6f)
                                )
                                val h = (entry.totalSalesPaise.toFloat() / maxVal.toFloat()) * size.height
                                drawRoundRect(
                                    color = barColor,
                                    topLeft = Offset(x, size.height - h),
                                    size = Size(barW, h),
                                    cornerRadius = CornerRadius(6f, 6f)
                                )
                            }
                        }
                        Text(
                            "Showing ${dailySlice.size} active day(s) this month",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Backup Status Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateBackups),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Backup,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Automatic Daily Backup", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Schedule: ${snap.backupSettings.preferredBackupTime} (${snap.shopConfig.timezoneId}) • ${snap.backupSettings.lastBackupStatus}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onNavigateBackups) { Text("Manage") }
                }
            }
        }

        // Top Customers with Pending Balances
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Top Pending Customer Ba", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = onNavigateBaBu) { Text("See All") }
                    }
                    if (m.topPendingCustomers.isEmpty()) {
                        Text(
                            "No pending customer balances right now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        m.topPendingCustomers.forEachIndexed { i, cust ->
                            if (i > 0) HorizontalDivider(alpha = 0.4f)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenCustomer(cust.phoneNumber) }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cust.customerName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${cust.phoneNumber}${if (cust.vengOrKhua.isNotBlank()) " • ${cust.vengOrKhua}" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                RupeeAmountText(
                                    paise = cust.currentPendingBaPaise,
                                    color = CreditCrimson
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recent Sales & Recent Ba Payments
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recent Sales", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = onNavigateDailySales) { Text("All Sales") }
                    }
                    if (m.recentSales.isEmpty()) {
                        Text("No sales recorded yet this month.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        m.recentSales.forEachIndexed { idx, sale ->
                            if (idx > 0) HorizontalDivider(alpha = 0.4f)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        sale.items.joinToString(", ") { "${it.itemName} x${it.quantity}" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            "${sale.dateDisplay} • ${sale.timeDisplay}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        PaymentModeBadge(sale.paymentMode)
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                RupeeAmountText(paise = sale.grandTotalPaise)
                            }
                        }
                    }
                }
            }
        }

        if (m.recentCredits.isNotEmpty() || m.recentPayments.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Recent Ba Activity", style = MaterialTheme.typography.titleMedium)
                        (m.recentCredits + m.recentPayments)
                            .sortedByDescending { it.isoTimestamp }
                            .take(6)
                            .forEach { tx ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenCustomer(tx.customerPhoneNumber) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(tx.customerName, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                "${tx.type.label} • ${tx.dateDisplay} ${tx.timeDisplay}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        val isPayment = tx.type == com.example.domain.model.BaTransactionType.PAYMENT
                                        RupeeAmountText(
                                            paise = tx.amountPaise,
                                            color = if (isPayment) CashGreen else CreditCrimson
                                        )
                                    }
                                }
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickChip(
    label: String,
    icon: ImageVector,
    tag: String,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = Modifier.testTag(tag)
    )
}

@Composable
private fun HorizontalDivider(alpha: Float) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = alpha))
}
