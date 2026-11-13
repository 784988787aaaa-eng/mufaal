package com.example.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.components.OfflineSecurityBadge
import com.example.ui.screens.IssueLicenseScreen
import com.example.ui.screens.IssuedLicensesScreen
import com.example.ui.screens.KeyVaultScreen
import com.example.ui.screens.LicenseResultScreen
import com.example.ui.screens.VaultLockScreen
import com.example.ui.theme.SmartLedgerLicenseTheme
import com.example.ui.viewmodel.LicenseStudioViewModel
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
    @Serializable
    data object Issue : AppRoute

    @Serializable
    data object History : AppRoute

    @Serializable
    data object Vault : AppRoute

    @Serializable
    data class Result(val licenseId: String) : AppRoute
}

private data class TopLevelTab(
    val route: AppRoute,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopLevelTabs = listOf(
    TopLevelTab(
        route = AppRoute.Issue,
        label = "إصدار",
        selectedIcon = Icons.Filled.AddCircle,
        unselectedIcon = Icons.Outlined.AddCircleOutline,
        testTag = "nav_tab_issue"
    ),
    TopLevelTab(
        route = AppRoute.History,
        label = "السجل",
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_tab_history"
    ),
    TopLevelTab(
        route = AppRoute.Vault,
        label = "الخزنة",
        selectedIcon = Icons.Filled.Key,
        unselectedIcon = Icons.Outlined.Key,
        testTag = "nav_tab_vault"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartLedgerApp(
    viewModel: LicenseStudioViewModel,
    biometricAvailable: Boolean,
    onRequestBiometricPrompt: () -> Unit
) {
    val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        SmartLedgerLicenseTheme(darkTheme = darkTheme) {
            if (!authState.isUnlocked) {
                VaultLockScreen(
                    authState = authState,
                    biometricAvailable = biometricAvailable,
                    onSetupPin = viewModel::setupInitialPin,
                    onVerifyPin = viewModel::unlockWithPin,
                    onRequestBiometricPrompt = onRequestBiometricPrompt,
                    onClearError = viewModel::clearPinError
                )
            } else {
                UnlockedStudioShell(
                    viewModel = viewModel,
                    biometricAvailable = biometricAvailable,
                    isDarkTheme = darkTheme
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnlockedStudioShell(
    viewModel: LicenseStudioViewModel,
    biometricAvailable: Boolean,
    isDarkTheme: Boolean
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }

    val vaultMetadata by viewModel.vaultMetadata.collectAsStateWithLifecycle()
    val issueForm by viewModel.issueForm.collectAsStateWithLifecycle()
    val filteredLicenses by viewModel.filteredLicenses.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val planFilter by viewModel.planFilter.collectAsStateWithLifecycle()
    val selectedLicense by viewModel.selectedLicense.collectAsStateWithLifecycle()
    val selectedSelfVerified by viewModel.selectedSelfVerified.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val isResultScreen = currentDestination?.hasRoute<AppRoute.Result>() == true

    fun navigateToTopLevel(route: AppRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (!isResultScreen) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        title = {
                            Column {
                                Text(
                                    text = "مُفعّل الدفتر الذكي",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = if (vaultMetadata.hasKeyPair) {
                                        "مفتاح LOCAL2 نشط • أوفلاين"
                                    } else {
                                        "الخزنة بانتظار توليد المفتاح"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        actions = {
                            OfflineSecurityBadge(text = "أوفلاين 100%")
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = viewModel::lockVault,
                                modifier = Modifier.testTag("topbar_lock_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "قفل الخزنة"
                                )
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (!isResultScreen && !useNavigationRail) {
                    NavigationBar {
                        TopLevelTabs.forEach { tab ->
                            val selected = currentDestination?.hasRoute(tab.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
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
                if (!isResultScreen && useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Spacer(Modifier.weight(1f))
                        TopLevelTabs.forEach { tab ->
                            val selected = currentDestination?.hasRoute(tab.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = AppRoute.Issue,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    composable<AppRoute.Issue> {
                        IssueLicenseScreen(
                            formState = issueForm,
                            vaultMetadata = vaultMetadata,
                            onDeviceCodeChange = viewModel::updateDeviceCode,
                            onSmartPaste = viewModel::smartPasteFromText,
                            onCheckDigitOverrideChange = viewModel::setCheckDigitOverride,
                            onPlanChange = viewModel::setPlan,
                            onSelectTrialDays = viewModel::selectTrialDays,
                            onSelectCustomTrialDate = viewModel::selectCustomTrialDate,
                            onCustomerNameChange = viewModel::updateCustomerName,
                            onCustomerPhoneChange = viewModel::updateCustomerPhone,
                            onNotesChange = viewModel::updateNotes,
                            onIncludeCustomerHintChange = viewModel::setIncludeCustomerHint,
                            onGenerateKeyQuickAction = viewModel::generateKeyPair,
                            onOpenVaultTab = { navigateToTopLevel(AppRoute.Vault) },
                            onIssueClick = {
                                viewModel.issueAndSignLicense { issued ->
                                    navController.navigate(AppRoute.Result(issued.licenseId))
                                }
                            }
                        )
                    }

                    composable<AppRoute.History> {
                        IssuedLicensesScreen(
                            licenses = filteredLicenses,
                            stats = stats,
                            searchQuery = searchQuery,
                            planFilter = planFilter,
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onPlanFilterChange = viewModel::setPlanFilter,
                            onOpenLicense = { entity ->
                                viewModel.openLicenseResult(entity)
                                navController.navigate(AppRoute.Result(entity.licenseId))
                            },
                            onUpgradeToLifetime = { existing ->
                                viewModel.prefillUpgradeToLifetime(existing)
                                navigateToTopLevel(AppRoute.Issue)
                            },
                            onDeleteLicense = viewModel::deleteLicense,
                            onExportEncryptedBackup = viewModel::exportEncryptedBackupProxy,
                            onRestoreEncryptedBackup = viewModel::restoreRegistryBackup
                        )
                    }

                    composable<AppRoute.Vault> {
                        KeyVaultScreen(
                            metadata = vaultMetadata,
                            gatewayCapability = viewModel.gatewayCapability,
                            biometricAvailable = biometricAvailable,
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = viewModel::toggleTheme,
                            onGenerateKeyPair = viewModel::generateKeyPair,
                            onImportKeyPairPem = viewModel::importKeyPairPem,
                            onExportEncryptedKeyBackup = viewModel::exportEncryptedKeyBackup,
                            onRestoreEncryptedKeyBackup = viewModel::restoreEncryptedKeyBackup,
                            onSetBiometricEnabled = viewModel::setBiometricEnabled,
                            onChangePin = viewModel::changePin,
                            onLockVaultNow = viewModel::lockVault
                        )
                    }

                    composable<AppRoute.Result> { entry ->
                        val route = entry.toRoute<AppRoute.Result>()
                        LaunchedEffect(route.licenseId) {
                            if (selectedLicense?.licenseId != route.licenseId) {
                                viewModel.loadLicenseById(route.licenseId)
                            }
                        }
                        LicenseResultScreen(
                            license = selectedLicense,
                            selfVerified = selectedSelfVerified,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

private suspend fun LicenseStudioViewModel.exportEncryptedBackupProxy(password: String): Result<String> {
    return exportRegistryBackup(password)
}
