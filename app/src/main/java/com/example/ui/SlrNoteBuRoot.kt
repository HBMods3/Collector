package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.repository.ShopJsonCodec
import com.example.ui.archive.MonthlyArchiveScreen
import com.example.ui.auth.AuthAndShopWizardScreen
import com.example.ui.babu.BaBuScreen
import com.example.ui.babu.CustomerDetailScreen
import com.example.ui.backups.BackupManagementScreen
import com.example.ui.components.BusyOverlay
import com.example.ui.components.ShopSwitcherSheet
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.explorer.GitHubExplorerScreen
import com.example.ui.navigation.NavRoutes
import com.example.ui.reports.ReportsScreen
import com.example.ui.sales.DailySalesScreen
import com.example.ui.settings.AuditLogsScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.UserManagementScreen

private data class TopNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

private val topNavItems = listOf(
    TopNavItem(NavRoutes.HOME, "Home", Icons.Rounded.Home, Icons.Outlined.Home, "nav_tab_home"),
    TopNavItem(NavRoutes.DAILY_SALE, "Daily Sale", Icons.Rounded.ReceiptLong, Icons.Outlined.ReceiptLong, "nav_tab_daily_sale"),
    TopNavItem(NavRoutes.BABU, "Ba Bu", Icons.Rounded.MenuBook, Icons.Outlined.MenuBook, "nav_tab_babu"),
    TopNavItem(NavRoutes.REPORTS, "Reports", Icons.Rounded.Assessment, Icons.Outlined.Assessment, "nav_tab_reports"),
    TopNavItem(NavRoutes.SETTINGS, "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings, "nav_tab_settings")
)

@Composable
fun SlrNoteBuRoot(
    viewModel: MainViewModel,
    codec: ShopJsonCodec
) {
    val uiState by viewModel.uiState.collectAsState()
    val deviceFlowState by viewModel.deviceFlowState.collectAsState()
    val explorerState by viewModel.explorerState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.snackbarMessage, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    var showShopSwitcher by remember { mutableStateOf(false) }
    var openNewSaleOnNav by remember { mutableStateOf(false) }
    var openNewCustomerOnNav by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        val mustShowWizard = uiState.activeAccount == null || uiState.activeShopBinding == null || uiState.showShopWizard

        if (mustShowWizard) {
            AuthAndShopWizardScreen(
                uiState = uiState,
                deviceFlowState = deviceFlowState,
                onSaveClientId = viewModel::saveGithubClientId,
                onStartOAuth = viewModel::startGitHubDeviceOAuth,
                onCancelOAuth = viewModel::cancelGitHubDeviceOAuth,
                onSwitchAccount = viewModel::switchAccount,
                onRefreshRepos = viewModel::loadUserRepositories,
                onCreateShopNewRepo = viewModel::createShopWithNewPrivateRepo,
                onConnectExistingRepo = viewModel::connectExistingRepoAsShop,
                onCancelWizardIfShopExists = if (uiState.activeShopBinding != null && uiState.showShopWizard) {
                    { viewModel.openShopWizard(false) }
                } else null
            )
        } else if (uiState.snapshot == null) {
            // Initial shop sync state
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.safeDrawing
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (uiState.isBusy) {
                        CircularProgressIndicator()
                        Text(
                            uiState.busyMessage.ifBlank { "Loading shop from GitHub..." },
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    } else {
                        Text(
                            "Could not load ${uiState.activeShopBinding?.shopName ?: "shop"} from GitHub.",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                            Button(onClick = { viewModel.syncActiveShop(checkAutoClose = true) }) {
                                Text("Retry Sync")
                            }
                            TextButton(onClick = { viewModel.openShopWizard(true) }) {
                                Text("Switch / Setup Shop")
                            }
                        }
                    }
                }
            }
        } else {
            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            val isTopLevelRoute = topNavItems.any { it.route == currentRoute }

            fun navigateTopLevel(route: String) {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 600.dp

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        if (!useRail && isTopLevelRoute) {
                            NavigationBar {
                                topNavItems.forEach { item ->
                                    val selected = currentRoute == item.route
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateTopLevel(item.route) },
                                        icon = {
                                            Icon(
                                                if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = item.label
                                            )
                                        },
                                        label = { Text(item.label) },
                                        modifier = Modifier.testTag(item.tag)
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useRail && isTopLevelRoute) {
                            NavigationRail {
                                topNavItems.forEach { item ->
                                    val selected = currentRoute == item.route
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { navigateTopLevel(item.route) },
                                        icon = {
                                            Icon(
                                                if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = item.label
                                            )
                                        },
                                        label = { Text(item.label) },
                                        modifier = Modifier.testTag(item.tag)
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                            if (uiState.tokenExpiredBanner) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "GitHub OAuth token expired or revoked.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { viewModel.openShopWizard(true) }) {
                                            Text("Reconnect")
                                        }
                                    }
                                }
                            }

                            NavHost(
                                navController = navController,
                                startDestination = NavRoutes.HOME,
                                modifier = Modifier.weight(1f)
                            ) {
                                composable(NavRoutes.HOME) {
                                    DashboardScreen(
                                        uiState = uiState,
                                        onSwitchShop = { showShopSwitcher = true },
                                        onSyncNow = { viewModel.syncActiveShop(checkAutoClose = true) },
                                        onQuickNewSale = {
                                            openNewSaleOnNav = true
                                            navigateTopLevel(NavRoutes.DAILY_SALE)
                                        },
                                        onQuickAddBa = { navigateTopLevel(NavRoutes.BABU) },
                                        onQuickReceivePayment = { navigateTopLevel(NavRoutes.BABU) },
                                        onQuickNewCustomer = {
                                            openNewCustomerOnNav = true
                                            navigateTopLevel(NavRoutes.BABU)
                                        },
                                        onNavigateDailySales = { navigateTopLevel(NavRoutes.DAILY_SALE) },
                                        onNavigateBaBu = { navigateTopLevel(NavRoutes.BABU) },
                                        onNavigateMonthlyArchive = { navController.navigate(NavRoutes.MONTHLY_ARCHIVE) },
                                        onNavigateReports = { navigateTopLevel(NavRoutes.REPORTS) },
                                        onNavigateBackups = { navController.navigate(NavRoutes.BACKUPS) },
                                        onOpenCustomer = { phone ->
                                            navController.navigate(NavRoutes.customerDetail(phone))
                                        }
                                    )
                                }

                                composable(NavRoutes.DAILY_SALE) {
                                    DailySalesScreen(
                                        uiState = uiState,
                                        openNewSaleSheetInitially = openNewSaleOnNav,
                                        onConsumedInitialSheet = { openNewSaleOnNav = false },
                                        onAddSale = viewModel::addSale,
                                        onEditSale = viewModel::editSale,
                                        onDeleteSale = viewModel::deleteSale
                                    )
                                }

                                composable(NavRoutes.BABU) {
                                    BaBuScreen(
                                        uiState = uiState,
                                        openAddCustomerInitially = openNewCustomerOnNav,
                                        onConsumedInitialDialog = { openNewCustomerOnNav = false },
                                        onOpenCustomerDetail = { phone ->
                                            navController.navigate(NavRoutes.customerDetail(phone))
                                        },
                                        onAddCustomer = viewModel::addCustomer,
                                        onEditCustomer = viewModel::updateCustomer
                                    )
                                }

                                composable(
                                    route = NavRoutes.CUSTOMER_DETAIL,
                                    arguments = listOf(navArgument("phone") { type = NavType.StringType })
                                ) { entry ->
                                    val phone = entry.arguments?.getString("phone") ?: ""
                                    CustomerDetailScreen(
                                        phoneNumber = phone,
                                        uiState = uiState,
                                        onBack = { navController.popBackStack() },
                                        onAddBaCredit = viewModel::addBaCredit,
                                        onRecordPayment = viewModel::recordBaPayment,
                                        onCorrectOrReverseTx = viewModel::correctOrReverseBaTransaction
                                    )
                                }

                                composable(NavRoutes.REPORTS) {
                                    ReportsScreen(
                                        uiState = uiState,
                                        codec = codec,
                                        onLoadArchiveForReport = viewModel::loadMonthlyArchive,
                                        onLoadYearlyReport = viewModel::loadYearlyReport,
                                        onOpenMonthlyArchive = { navController.navigate(NavRoutes.MONTHLY_ARCHIVE) }
                                    )
                                }

                                composable(NavRoutes.MONTHLY_ARCHIVE) {
                                    MonthlyArchiveScreen(
                                        uiState = uiState,
                                        codec = codec,
                                        onBack = { navController.popBackStack() },
                                        onLoadArchive = viewModel::loadMonthlyArchive,
                                        onTriggerClosing = viewModel::triggerMonthlyClosing,
                                        onAddCorrection = viewModel::addArchiveCorrection
                                    )
                                }

                                composable(NavRoutes.BACKUPS) {
                                    BackupManagementScreen(
                                        uiState = uiState,
                                        onBack = { navController.popBackStack() },
                                        onLoadHistory = viewModel::loadBackupHistory,
                                        onBackupNow = viewModel::createBackupNow,
                                        onRestoreBackup = viewModel::restoreFromBackup,
                                        onReadRawBackupJson = viewModel::readRawBackupJson,
                                        onCollectAllZipFiles = viewModel::collectFilesForZip
                                    )
                                }

                                composable(NavRoutes.EXPLORER) {
                                    GitHubExplorerScreen(
                                        uiState = uiState,
                                        explorerState = explorerState,
                                        onBack = { navController.popBackStack() },
                                        onOpenDir = viewModel::explorerOpenDir,
                                        onOpenFile = viewModel::explorerOpenFile,
                                        onCloseFile = viewModel::explorerCloseFile,
                                        onSaveFile = viewModel::explorerSaveFile,
                                        onCreateFile = viewModel::explorerCreateFile,
                                        onDeleteFile = viewModel::explorerDeleteFile,
                                        onCollectFolderZip = viewModel::collectFilesForZip
                                    )
                                }

                                composable(NavRoutes.SETTINGS) {
                                    SettingsScreen(
                                        uiState = uiState,
                                        onSetThemeMode = viewModel::setThemeMode,
                                        onSaveShopSettings = viewModel::updateShopAndBackupSettings,
                                        onAddQuickItem = viewModel::addQuickItemPreset,
                                        onOpenBackups = { navController.navigate(NavRoutes.BACKUPS) },
                                        onOpenArchive = { navController.navigate(NavRoutes.MONTHLY_ARCHIVE) },
                                        onOpenExplorer = { navController.navigate(NavRoutes.EXPLORER) },
                                        onOpenUsers = { navController.navigate(NavRoutes.USER_MANAGEMENT) },
                                        onOpenAuditLogs = { navController.navigate(NavRoutes.AUDIT_LOGS) },
                                        onSwitchShop = { showShopSwitcher = true },
                                        onCreateOrConnectShop = { viewModel.openShopWizard(true) },
                                        onDisconnectShop = viewModel::disconnectActiveShop,
                                        onLogoutAccount = viewModel::logoutAccount
                                    )
                                }

                                composable(NavRoutes.USER_MANAGEMENT) {
                                    UserManagementScreen(
                                        uiState = uiState,
                                        onBack = { navController.popBackStack() },
                                        onSaveUser = viewModel::saveAuthorizedUser
                                    )
                                }

                                composable(NavRoutes.AUDIT_LOGS) {
                                    AuditLogsScreen(
                                        uiState = uiState,
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        BusyOverlay(isBusy = uiState.isBusy, message = uiState.busyMessage)

        if (showShopSwitcher) {
            ShopSwitcherSheet(
                shops = uiState.shops,
                activeShopId = uiState.activeShopBinding?.shopId,
                onSelectShop = viewModel::switchShop,
                onAddShop = { viewModel.openShopWizard(true) },
                onDismiss = { showShopSwitcher = false }
            )
        }
    }
}
