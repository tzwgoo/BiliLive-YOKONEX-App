package com.yokonex.bililive.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yokonex.bililive.app.ui.components.WorkspaceShell
import com.yokonex.bililive.app.ui.dashboard.DashboardViewModel
import com.yokonex.bililive.app.ui.dashboard.DashboardWorkspaceScreen
import com.yokonex.bililive.app.ui.events.EventStudioScreen
import com.yokonex.bililive.app.ui.live.LiveConfigViewModel
import com.yokonex.bililive.app.ui.logs.LogsViewModel
import com.yokonex.bililive.app.ui.output.OutputConfigViewModel
import com.yokonex.bililive.app.ui.rules.RulesViewModel
import com.yokonex.bililive.app.ui.waveforms.WaveformStudioScreen
import com.yokonex.bililive.app.ui.waveforms.WaveformsViewModel

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route ?: "dashboard"
    val navigationItems = appNavigationItems()

    WorkspaceShell(
        items = navigationItems,
        currentRoute = currentRoute,
        onNavigate = { route ->
            if (route != currentRoute) {
                navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
        ) {
            composable("dashboard") {
                // 主控台复用多个 ViewModel 的状态，将监听、连接和日志聚合成桌面端同款工作区。
                val dashboardViewModel: DashboardViewModel = viewModel()
                val liveConfigViewModel: LiveConfigViewModel = viewModel()
                val outputConfigViewModel: OutputConfigViewModel = viewModel()
                val logsViewModel: LogsViewModel = viewModel()
                val dashboardState by dashboardViewModel.uiState.collectAsState()
                val liveConfigState by liveConfigViewModel.uiState.collectAsState()
                val outputState by outputConfigViewModel.uiState.collectAsState()
                val logsState by logsViewModel.uiState.collectAsState()

                DashboardWorkspaceScreen(
                    dashboardState = dashboardState,
                    liveConfigState = liveConfigState,
                    outputState = outputState,
                    logsState = logsState,
                    onRoomIdChange = liveConfigViewModel::updateRoomId,
                    onToggleMonitoring = liveConfigViewModel::toggleMonitoring,
                    onOutputModeChange = outputConfigViewModel::selectMode,
                    onBluetoothMixModeChange = outputConfigViewModel::updateBluetoothMixMode,
                    onSocketEndpointChange = outputConfigViewModel::updateSocketEndpoint,
                    onSocketUidChange = outputConfigViewModel::updateSocketUid,
                    onSocketTokenChange = outputConfigViewModel::updateSocketToken,
                    onConnectCommandChannel = outputConfigViewModel::connectCommandChannel,
                    onDisconnectCommandChannel = outputConfigViewModel::disconnectCommandChannel,
                    onScanBluetoothDevices = outputConfigViewModel::scanBluetoothDevices,
                    onConnectBluetoothDevice = outputConfigViewModel::connectBluetoothDevice,
                    onDisconnectBluetoothDevice = outputConfigViewModel::disconnectBluetoothDevice,
                    onSelectLogFilter = logsViewModel::selectFilter,
                    contentPadding = innerPadding,
                )
            }

            composable("events") {
                // 事件配置页按“通用 / IM / 蓝牙”三段式装配，保持和 Vue 前端一致的编辑路径。
                val liveConfigViewModel: LiveConfigViewModel = viewModel()
                val rulesViewModel: RulesViewModel = viewModel()
                val liveConfigState by liveConfigViewModel.uiState.collectAsState()
                val rulesState by rulesViewModel.uiState.collectAsState()

                EventStudioScreen(
                    liveConfigState = liveConfigState,
                    rulesState = rulesState,
                    onGiftTriggerModeChange = liveConfigViewModel::updateGiftTriggerMode,
                    onLikeMultipleChange = liveConfigViewModel::updateLikeMultiple,
                    onDanmakuEnabledChange = liveConfigViewModel::updateDanmakuEnabled,
                    onDanmakuKeywordsChange = liveConfigViewModel::updateDanmakuKeywords,
                    onDanmakuCooldownSecondsChange = liveConfigViewModel::updateDanmakuCooldownSeconds,
                    onDanmakuUserLimitWindowSecondsChange = liveConfigViewModel::updateDanmakuUserLimitWindowSeconds,
                    onDanmakuUserLimitMaxTriggersChange = liveConfigViewModel::updateDanmakuUserLimitMaxTriggers,
                    onDanmakuMinGuardLevelChange = liveConfigViewModel::updateDanmakuMinGuardLevel,
                    onRuleToggle = rulesViewModel::toggleRule,
                    onGiftPriceRangeChange = rulesViewModel::updateGiftPriceRange,
                    onRuleLikeMultipleChange = rulesViewModel::updateLikeMultiple,
                    onRuleKeywordsChange = rulesViewModel::updateKeywords,
                    onCooldownSecondsChange = rulesViewModel::updateCooldownSeconds,
                    onCooldownScopeChange = rulesViewModel::updateCooldownScope,
                    onMinGuardLevelChange = rulesViewModel::updateMinGuardLevel,
                    onUserLimitWindowSecondsChange = rulesViewModel::updateUserLimitWindowSeconds,
                    onUserLimitMaxTriggersChange = rulesViewModel::updateUserLimitMaxTriggers,
                    onBluetoothWaveformChange = rulesViewModel::updateBluetoothWaveform,
                    onGuardWaveformChange = rulesViewModel::updateGuardWaveform,
                    onWebsocketSlotChange = rulesViewModel::updateWebsocketSlot,
                    contentPadding = innerPadding,
                )
            }

            composable("waveforms") {
                // 波形库工作区同时依赖波形编辑状态和蓝牙连接态，用于复刻桌面端双栏编辑体验。
                val waveformsViewModel: WaveformsViewModel = viewModel()
                val outputConfigViewModel: OutputConfigViewModel = viewModel()
                val waveformsState by waveformsViewModel.uiState.collectAsState()
                val outputState by outputConfigViewModel.uiState.collectAsState()

                WaveformStudioScreen(
                    uiState = waveformsState,
                    outputState = outputState,
                    onSelectWaveform = waveformsViewModel::selectWaveform,
                    onCreateWaveform = waveformsViewModel::createWaveform,
                    onCloseEditor = waveformsViewModel::closeEditor,
                    onDuplicateSelectedWaveform = waveformsViewModel::duplicateSelectedWaveform,
                    onSaveDraft = waveformsViewModel::saveDraft,
                    onWaveformNameChange = waveformsViewModel::updateWaveformName,
                    onUpdateStepDuration = waveformsViewModel::updateStepDuration,
                    onAppendStep = waveformsViewModel::appendStep,
                    onRemoveLastStep = waveformsViewModel::removeLastStep,
                    onDuplicateStep = waveformsViewModel::duplicateStep,
                    onDeleteStep = waveformsViewModel::deleteStep,
                    onStrengthDrag = waveformsViewModel::updateDraftStrength,
                    onInsertStep = waveformsViewModel::insertStep,
                    onRequestDeleteWaveform = waveformsViewModel::requestDeleteSelectedWaveform,
                    onDismissDeleteRequest = waveformsViewModel::dismissDeleteRequest,
                    onConfirmDeleteWaveform = waveformsViewModel::confirmDeleteSelectedWaveform,
                    onConfirmPendingSelection = waveformsViewModel::confirmPendingSelection,
                    onDismissPendingSelection = waveformsViewModel::dismissPendingSelection,
                    contentPadding = innerPadding,
                )
            }
        }
    }
}

internal fun appNavigationItems(): List<NavigationItemSpec> = listOf(
    NavigationItemSpec("dashboard", "主页", NavigationIcon.Dashboard),
    NavigationItemSpec("events", "事件配置", NavigationIcon.Events),
    NavigationItemSpec("waveforms", "波形库", NavigationIcon.Waveforms),
)

internal data class NavigationItemSpec(
    val route: String,
    val label: String,
    val iconKey: NavigationIcon,
)

internal enum class NavigationIcon {
    Dashboard,
    Events,
    Waveforms,
}
