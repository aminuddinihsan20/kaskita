package com.kaskita.lokal.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import dev.chrisbanes.haze.rememberHazeState
import com.kaskita.lokal.ui.common.FloatingNavBar
import com.kaskita.lokal.ui.common.LocalFloatingBarInset
import com.kaskita.lokal.ui.common.LocalHazeState
import com.kaskita.lokal.ui.common.NavEntry
import com.kaskita.lokal.ui.theme.Motion
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kaskita.lokal.R
import com.kaskita.lokal.domain.TransactionType
import com.kaskita.lokal.ui.AppContainer
import com.kaskita.lokal.ui.common.NoInsets
import com.kaskita.lokal.ui.form.TransactionFormScreen
import com.kaskita.lokal.ui.form.TransactionFormViewModel
import com.kaskita.lokal.ui.home.HomeScreen
import com.kaskita.lokal.ui.home.HomeViewModel
import com.kaskita.lokal.ui.reports.ReportsScreen
import com.kaskita.lokal.ui.reports.ReportsViewModel
import com.kaskita.lokal.ui.settings.AccountDetailScreen
import com.kaskita.lokal.ui.settings.AccountDetailViewModel
import com.kaskita.lokal.ui.settings.AccountsScreen
import com.kaskita.lokal.ui.settings.AccountsViewModel
import com.kaskita.lokal.ui.settings.BackupScreen
import com.kaskita.lokal.ui.settings.BackupViewModel
import com.kaskita.lokal.ui.settings.CategoriesScreen
import com.kaskita.lokal.ui.settings.CategoriesViewModel
import com.kaskita.lokal.ui.settings.AiSetupViewModel
import com.kaskita.lokal.ui.settings.SettingsScreen
import com.kaskita.lokal.ui.settings.SettingsViewModel
import com.kaskita.lokal.ui.transactions.TransactionDetailScreen
import com.kaskita.lokal.ui.transactions.TransactionDetailViewModel
import com.kaskita.lokal.ui.transactions.TransactionsScreen
import com.kaskita.lokal.ui.transactions.TransactionsViewModel
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"

    const val ACCOUNTS = "accounts"
    const val ACCOUNT_DETAIL = "account/{id}"
    fun accountDetail(id: Long) = "account/$id"
    const val CATEGORIES = "categories"
    const val BACKUP = "backup"

    const val DETAIL = "transaction/{id}"
    fun detail(id: Long) = "transaction/$id"

    const val FORM = "form/{type}?id={id}&savings={savings}"
    fun form(type: TransactionType, editId: Long? = null, savings: Boolean = false) =
        "form/${type.name}?id=${editId ?: -1}&savings=$savings"
}

/**
 * For tapped back arrows: ignored unless the current screen is fully resumed, so a double tap cannot pop a second screen
 * (and leave an empty app) before the first pop has finished. Navigation triggered by state (after a save/delete) uses
 * plain popBackStack so it can never be skipped.
 */
private fun NavHostController.popBackStackOnce() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) popBackStack()
}

private fun NavHostController.navigateTopLevel(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private data class TopLevel(val route: String, val labelRes: Int, val icon: ImageVector)

private val topLevelDestinations = listOf(
    TopLevel(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    TopLevel(Routes.TRANSACTIONS, R.string.nav_transactions, Icons.Filled.Receipt),
    TopLevel(Routes.REPORTS, R.string.nav_reports, Icons.Filled.BarChart),
    TopLevel(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

@Composable
fun AppNav(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = topLevelDestinations.any { top -> currentDestination?.hierarchy?.any { it.route == top.route } == true }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // The snackbar belongs to this outer Scaffold, so it would otherwise float over the bottom buttons of the next screen.
    val go: (String) -> Unit = { route ->
        snackbar.currentSnackbarData?.dismiss()
        navController.navigate(route) { launchSingleTop = true } // a double tap must not stack two copies of a screen
    }
    val goTopLevel: (String) -> Unit = { route ->
        snackbar.currentSnackbarData?.dismiss()
        navController.navigateTopLevel(route)
    }

    val hazeState = rememberHazeState()
    val density = LocalDensity.current
    var barHeightPx by remember { mutableIntStateOf(0) }
    val barInset = if (showBottomBar) with(density) { barHeightPx.toDp() } else 0.dp
    val navEntries = topLevelDestinations.map { NavEntry(it.route, stringResource(it.labelRes), it.icon) }
    val selectedRoute = topLevelDestinations.firstOrNull { top -> currentDestination?.hierarchy?.any { it.route == top.route } == true }?.route

    CompositionLocalProvider(LocalHazeState provides hazeState, LocalFloatingBarInset provides barInset + 8.dp) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                contentWindowInsets = NoInsets,
                snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = barInset)) },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    enterTransition = { fadeIn(Motion.effects()) + scaleIn(initialScale = 0.92f, animationSpec = Motion.spatial()) },
                    exitTransition = { fadeOut(Motion.effects()) },
                    popEnterTransition = { fadeIn(Motion.effects()) + scaleIn(initialScale = 0.96f, animationSpec = Motion.spatial()) },
                    popExitTransition = { fadeOut(Motion.effects()) + scaleOut(targetScale = 0.92f, animationSpec = Motion.spatial()) },
                ) {
            composable(Routes.HOME) {
                val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
                HomeScreen(
                    viewModel = vm,
                    zone = container.zone,
                    onAdd = { go(Routes.form(it)) },
                    onSave = { go(Routes.form(TransactionType.TRANSFER, savings = true)) },
                    onOpenTransaction = { go(Routes.detail(it)) },
                    onAllTransactions = { goTopLevel(Routes.TRANSACTIONS) },
                    onCashHistory = { go(Routes.accountDetail(it)) },
                    onOpenSettings = { goTopLevel(Routes.SETTINGS) },
                )
            }
            composable(Routes.TRANSACTIONS) {
                val vm: TransactionsViewModel = viewModel(factory = TransactionsViewModel.factory(container))
                TransactionsScreen(
                    viewModel = vm,
                    zone = container.zone,
                    onOpen = { go(Routes.detail(it)) },
                    onAdd = { go(Routes.form(it)) },
                    onSave = { go(Routes.form(TransactionType.TRANSFER, savings = true)) },
                )
            }
            composable(Routes.REPORTS) {
                val vm: ReportsViewModel = viewModel(factory = ReportsViewModel.factory(container))
                val aiSetup: AiSetupViewModel = viewModel(factory = AiSetupViewModel.factory(container))
                ReportsScreen(vm, aiSetup, container.zone, onOpenTransaction = { go(Routes.detail(it)) })
            }
            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
                val aiSetup: AiSetupViewModel = viewModel(factory = AiSetupViewModel.factory(container))
                SettingsScreen(
                    viewModel = vm,
                    aiSetup = aiSetup,
                    onAccounts = { go(Routes.ACCOUNTS) },
                    onCategories = { go(Routes.CATEGORIES) },
                    onBackup = { go(Routes.BACKUP) },
                )
            }
            composable(Routes.ACCOUNTS) {
                val vm: AccountsViewModel = viewModel(factory = AccountsViewModel.factory(container))
                AccountsScreen(vm, onBack = { navController.popBackStackOnce() }, onOpenAccount = { go(Routes.accountDetail(it)) })
            }
            composable(
                route = Routes.ACCOUNT_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = requireNotNull(entry.arguments?.getLong("id"))
                val vm: AccountDetailViewModel = viewModel(factory = AccountDetailViewModel.factory(container, id))
                AccountDetailScreen(
                    viewModel = vm,
                    zone = container.zone,
                    onBack = { navController.popBackStackOnce() },
                    onOpenTransaction = { go(Routes.detail(it)) },
                )
            }
            composable(Routes.CATEGORIES) {
                val vm: CategoriesViewModel = viewModel(factory = CategoriesViewModel.factory(container))
                CategoriesScreen(vm, onBack = { navController.popBackStackOnce() })
            }
            composable(Routes.BACKUP) {
                val vm: BackupViewModel = viewModel(factory = BackupViewModel.factory(container))
                BackupScreen(vm, container.zone, nowMillis = container::now, onBack = { navController.popBackStackOnce() })
            }

            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = requireNotNull(entry.arguments?.getLong("id"))
                val vm: TransactionDetailViewModel = viewModel(factory = TransactionDetailViewModel.factory(container, id))
                val deletedText = stringResource(R.string.tx_deleted)
                TransactionDetailScreen(
                    viewModel = vm,
                    zone = container.zone,
                    onBack = { navController.popBackStackOnce() },
                    onEdit = { type, txId -> go(Routes.form(type, txId)) },
                    onDeleted = {
                        navController.popBackStack()
                        scope.launch { snackbar.showSnackbar(deletedText) }
                    },
                )
            }

            composable(
                route = Routes.FORM,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("savings") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                val type = TransactionType.valueOf(requireNotNull(entry.arguments?.getString("type")))
                val editId = entry.arguments?.getLong("id")?.takeIf { it >= 0 }
                val savings = entry.arguments?.getBoolean("savings") ?: false
                val vm: TransactionFormViewModel = viewModel(factory = TransactionFormViewModel.factory(container, type, editId, savings))
                TransactionFormScreen(
                    viewModel = vm,
                    isEditing = editId != null,
                    zone = container.zone,
                    onBack = { navController.popBackStackOnce() },
                    onSaved = { message ->
                        navController.popBackStack()
                        scope.launch { snackbar.showSnackbar(message) }
                    },
                )
            }
                }
            }
            if (showBottomBar) {
                FloatingNavBar(
                    entries = navEntries,
                    selectedRoute = selectedRoute,
                    onSelect = goTopLevel,
                    modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { barHeightPx = it.height },
                )
            }
        }
    }
}
