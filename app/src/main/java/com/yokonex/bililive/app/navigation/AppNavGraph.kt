package com.yokonex.bililive.app.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yokonex.bililive.app.ui.dashboard.DashboardScreen
import com.yokonex.bililive.app.ui.dashboard.DashboardViewModel
import com.yokonex.bililive.app.ui.live.LiveConfigScreen
import com.yokonex.bililive.app.ui.live.LiveConfigViewModel
import com.yokonex.bililive.app.ui.logs.LogsScreen
import com.yokonex.bililive.app.ui.logs.LogsViewModel
import com.yokonex.bililive.app.ui.output.OutputConfigScreen
import com.yokonex.bililive.app.ui.output.OutputConfigViewModel
import com.yokonex.bililive.app.ui.rules.RulesScreen
import com.yokonex.bililive.app.ui.rules.RulesViewModel

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val navigationItems = listOf(
        NavigationItem("dashboard", "控制台", "控"),
        NavigationItem("live", "连接", "连"),
        NavigationItem("output", "输出", "出"),
        NavigationItem("rules", "规则", "规"),
        NavigationItem("logs", "日志", "记"),
    )
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    navigationItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Text(item.iconText) },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "dashboard",
            ) {
                composable("dashboard") {
                    val dashboardViewModel: DashboardViewModel = viewModel()
                    val uiState by dashboardViewModel.uiState.collectAsState()
                    DashboardScreen(
                        uiState = uiState,
                        onToggleMonitoring = dashboardViewModel::toggleMonitoring,
                        onOutputModeChange = dashboardViewModel::selectOutputMode,
                        contentPadding = innerPadding,
                    )
                }
                composable("live") {
                    val liveConfigViewModel: LiveConfigViewModel = viewModel()
                    val uiState by liveConfigViewModel.uiState.collectAsState()
                    LiveConfigScreen(
                        uiState = uiState,
                        onRoomIdChange = liveConfigViewModel::updateRoomId,
                        onAutoReconnectChange = liveConfigViewModel::toggleAutoReconnect,
                        onReconnectIntervalChange = liveConfigViewModel::updateReconnectInterval,
                        contentPadding = innerPadding,
                    )
                }
                composable("output") {
                    val outputConfigViewModel: OutputConfigViewModel = viewModel()
                    val uiState by outputConfigViewModel.uiState.collectAsState()
                    OutputConfigScreen(
                        uiState = uiState,
                        onOutputModeChange = outputConfigViewModel::selectMode,
                        onSocketEndpointChange = outputConfigViewModel::updateSocketEndpoint,
                        onSocketUidChange = outputConfigViewModel::updateSocketUid,
                        onSocketTokenChange = outputConfigViewModel::updateSocketToken,
                        contentPadding = innerPadding,
                    )
                }
                composable("rules") {
                    val rulesViewModel: RulesViewModel = viewModel()
                    val uiState by rulesViewModel.uiState.collectAsState()
                    RulesScreen(
                        uiState = uiState,
                        onRuleToggle = rulesViewModel::toggleRule,
                        contentPadding = innerPadding,
                    )
                }
                composable("logs") {
                    val logsViewModel: LogsViewModel = viewModel()
                    val uiState by logsViewModel.uiState.collectAsState()
                    LogsScreen(
                        uiState = uiState,
                        contentPadding = innerPadding,
                    )
                }
            }
        }
    }
}

private data class NavigationItem(
    val route: String,
    val label: String,
    val iconText: String,
)
