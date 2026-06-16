package com.yokonex.bililive.app.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokonex.bililive.app.ui.components.EventLogItem
import com.yokonex.bililive.app.ui.components.StatusPill
import com.yokonex.bililive.app.ui.components.WorkspaceCard
import com.yokonex.bililive.app.ui.components.WorkspaceMetricCard
import com.yokonex.bililive.app.ui.components.WorkspacePageHeader
import com.yokonex.bililive.app.ui.components.WorkspaceSectionHeader
import com.yokonex.bililive.app.ui.components.workspaceFilledButtonColors
import com.yokonex.bililive.app.ui.components.workspaceFilterChipColors
import com.yokonex.bililive.app.ui.components.workspaceOutlinedButtonColors
import com.yokonex.bililive.app.ui.components.workspaceOutlinedTextFieldColors
import com.yokonex.bililive.app.ui.live.LiveConfigUiState
import com.yokonex.bililive.app.ui.logs.LogEventFilter
import com.yokonex.bililive.app.ui.logs.LogsUiState
import com.yokonex.bililive.app.ui.output.OutputConfigUiState
import com.yokonex.bililive.domain.model.OutputMode

@Composable
fun DashboardWorkspaceScreen(
    dashboardState: DashboardUiState,
    liveConfigState: LiveConfigUiState,
    outputState: OutputConfigUiState,
    logsState: LogsUiState,
    onRoomIdChange: (String) -> Unit,
    onToggleMonitoring: () -> Unit,
    onOutputModeChange: (OutputMode) -> Unit,
    onBluetoothMixModeChange: (Boolean) -> Unit,
    onSocketEndpointChange: (String) -> Unit,
    onSocketUidChange: (String) -> Unit,
    onSocketTokenChange: (String) -> Unit,
    onConnectCommandChannel: () -> Unit,
    onDisconnectCommandChannel: () -> Unit,
    onScanBluetoothDevices: () -> Unit,
    onConnectBluetoothDevice: (String) -> Unit,
    onDisconnectBluetoothDevice: () -> Unit,
    onSelectLogFilter: (LogEventFilter) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            WorkspacePageHeader(
                title = "直播互动监听控制台",
                statusLabel = dashboardState.serviceStatusLabel,
            )
        }
        item {
            DashboardSummarySection(
                dashboardState = dashboardState,
                outputState = outputState,
            )
        }
        item {
            WorkspaceSectionHeader(
                kicker = "Session Core",
                title = "监听主参数",
            )
        }
        item {
            WorkspaceCard {
                BoxWithConstraints {
                    val compact = maxWidth < 520.dp
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (compact) {
                            OutlinedTextField(
                                value = liveConfigState.roomId,
                                onValueChange = onRoomIdChange,
                                label = { Text("房间号 ID") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = workspaceOutlinedTextFieldColors(),
                            )
                            Button(
                                onClick = onToggleMonitoring,
                                modifier = Modifier.fillMaxWidth(),
                                colors = workspaceFilledButtonColors(),
                            ) {
                                Text(liveConfigState.monitoringButtonLabel)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                OutlinedTextField(
                                    value = liveConfigState.roomId,
                                    onValueChange = onRoomIdChange,
                                    label = { Text("房间号 ID") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = workspaceOutlinedTextFieldColors(),
                                )
                                Button(
                                    onClick = onToggleMonitoring,
                                    colors = workspaceFilledButtonColors(),
                                ) {
                                    Text(liveConfigState.monitoringButtonLabel)
                                }
                            }
                        }
                        Text(
                            text = liveConfigState.monitoringSupportingText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            WorkspaceSectionHeader(
                kicker = "Connections",
                title = "连接与设备",
            )
        }
        item {
            ConnectionAndDevicesSection(
                uiState = outputState,
                onOutputModeChange = onOutputModeChange,
                onBluetoothMixModeChange = onBluetoothMixModeChange,
                onSocketEndpointChange = onSocketEndpointChange,
                onSocketUidChange = onSocketUidChange,
                onSocketTokenChange = onSocketTokenChange,
                onConnectCommandChannel = onConnectCommandChannel,
                onDisconnectCommandChannel = onDisconnectCommandChannel,
                onScanBluetoothDevices = onScanBluetoothDevices,
                onConnectBluetoothDevice = onConnectBluetoothDevice,
                onDisconnectBluetoothDevice = onDisconnectBluetoothDevice,
            )
        }
        item {
            WorkspaceSectionHeader(
                kicker = "Runtime",
                title = "运行快照",
            )
        }
        item {
            RuntimeSnapshotSection(
                dashboardState = dashboardState,
                outputState = outputState,
            )
        }
        item {
            WorkspaceSectionHeader(
                kicker = "Events",
                title = "实时日志",
            )
        }
        item {
            WorkspaceCard {
                // 主控台延续桌面端的顶部快速过滤方式，方便直接切换日志视角。
                FilterChipGroup(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    logsState.availableFilters.forEach { filter ->
                        FilterChip(
                            selected = logsState.selectedFilter == filter,
                            onClick = { onSelectLogFilter(filter) },
                            label = { Text(filter.label) },
                            colors = workspaceFilterChipColors(),
                        )
                    }
                }
                if (logsState.logs.isEmpty()) {
                    Text(
                        text = "暂无实时日志",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        logsState.logs.take(8).forEach { event ->
                            EventLogItem(eventLog = event)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardSummarySection(
    dashboardState: DashboardUiState,
    outputState: OutputConfigUiState,
) {
    BoxWithConstraints {
        val metrics = listOf(
            "监听状态" to dashboardState.serviceStatusLabel,
            "输出方式" to dashboardState.outputModeLabel,
            "当前房间" to dashboardState.roomId,
            "主播昵称" to dashboardState.anchorName,
            "IM 状态" to outputState.websocketStatus,
            "蓝牙状态" to outputState.bluetoothStatus,
        )
        if (maxWidth >= 960.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                metrics.forEach { (label, value) ->
                    WorkspaceMetricCard(
                        label = label,
                        value = value.ifBlank { "-" },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else if (maxWidth >= 560.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                metrics.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowItems.forEach { (label, value) ->
                            WorkspaceMetricCard(
                                label = label,
                                value = value.ifBlank { "-" },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                metrics.forEach { (label, value) ->
                    WorkspaceMetricCard(
                        label = label,
                        value = value.ifBlank { "-" },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionAndDevicesSection(
    uiState: OutputConfigUiState,
    onOutputModeChange: (OutputMode) -> Unit,
    onBluetoothMixModeChange: (Boolean) -> Unit,
    onSocketEndpointChange: (String) -> Unit,
    onSocketUidChange: (String) -> Unit,
    onSocketTokenChange: (String) -> Unit,
    onConnectCommandChannel: () -> Unit,
    onDisconnectCommandChannel: () -> Unit,
    onScanBluetoothDevices: () -> Unit,
    onConnectBluetoothDevice: (String) -> Unit,
    onDisconnectBluetoothDevice: () -> Unit,
) {
    WorkspaceCard {
        BoxWithConstraints {
            val compact = maxWidth < 560.dp
            FilterChipGroup(
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilterChip(
                    selected = uiState.outputMode == OutputMode.BLUETOOTH,
                    onClick = { onOutputModeChange(OutputMode.BLUETOOTH) },
                    label = { Text("蓝牙 EMS") },
                    colors = workspaceFilterChipColors(),
                )
                FilterChip(
                    selected = uiState.outputMode == OutputMode.WEBSOCKET,
                    onClick = { onOutputModeChange(OutputMode.WEBSOCKET) },
                    label = { Text("IM 指令") },
                    colors = workspaceFilterChipColors(),
                )
            }

            if (uiState.outputMode == OutputMode.BLUETOOTH) {
                FilterChipGroup(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilterChip(
                        selected = !uiState.bluetoothMixModeEnabled,
                        onClick = { onBluetoothMixModeChange(false) },
                        label = { Text("串行") },
                        colors = workspaceFilterChipColors(),
                    )
                    FilterChip(
                        selected = uiState.bluetoothMixModeEnabled,
                        onClick = { onBluetoothMixModeChange(true) },
                        label = { Text("混波") },
                        colors = workspaceFilterChipColors(),
                    )
                    StatusPill(label = "最近设备 ${uiState.recentBluetoothDeviceLabel}")
                }
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onScanBluetoothDevices,
                            modifier = Modifier.fillMaxWidth(),
                            colors = workspaceFilledButtonColors(),
                        ) {
                            Text("扫描设备")
                        }
                        OutlinedButton(
                            onClick = onDisconnectBluetoothDevice,
                            enabled = uiState.canDisconnectBluetooth,
                            modifier = Modifier.fillMaxWidth(),
                            colors = workspaceOutlinedButtonColors(),
                        ) {
                            Text("断开设备")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = onScanBluetoothDevices,
                            colors = workspaceFilledButtonColors(),
                        ) {
                            Text("扫描设备")
                        }
                        OutlinedButton(
                            onClick = onDisconnectBluetoothDevice,
                            enabled = uiState.canDisconnectBluetooth,
                            colors = workspaceOutlinedButtonColors(),
                        ) {
                            Text("断开设备")
                        }
                    }
                }
                if (!uiState.bluetoothErrorMessage.isNullOrBlank()) {
                    Text(
                        text = uiState.bluetoothErrorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    uiState.bluetoothDevices.forEach { device ->
                        WorkspaceCard {
                            if (compact) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = "协议 ${device.protocol}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (device.connected) {
                                        StatusPill(label = "已连接")
                                    } else {
                                        Button(
                                            onClick = { onConnectBluetoothDevice(device.id) },
                                            enabled = uiState.canConnectBluetooth(device.id),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = workspaceFilledButtonColors(),
                                        ) {
                                            Text(
                                                if (uiState.connectingBluetoothDeviceId == device.id) {
                                                    "连接中..."
                                                } else {
                                                    "连接"
                                                },
                                            )
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            text = device.name,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            text = "协议 ${device.protocol}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (device.connected) {
                                        StatusPill(label = "已连接")
                                    } else {
                                        Button(
                                            onClick = { onConnectBluetoothDevice(device.id) },
                                            enabled = uiState.canConnectBluetooth(device.id),
                                            colors = workspaceFilledButtonColors(),
                                        ) {
                                            Text(
                                                if (uiState.connectingBluetoothDeviceId == device.id) {
                                                    "连接中..."
                                                } else {
                                                    "连接"
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = uiState.socketEndpoint,
                    onValueChange = onSocketEndpointChange,
                    label = { Text("WS URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = workspaceOutlinedTextFieldColors(),
                )
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = uiState.socketUid,
                            onValueChange = onSocketUidChange,
                            label = { Text("UID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = workspaceOutlinedTextFieldColors(),
                        )
                        OutlinedTextField(
                            value = uiState.socketToken,
                            onValueChange = onSocketTokenChange,
                            label = { Text("TOKEN") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = workspaceOutlinedTextFieldColors(),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = uiState.socketUid,
                            onValueChange = onSocketUidChange,
                            label = { Text("UID") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = workspaceOutlinedTextFieldColors(),
                        )
                        OutlinedTextField(
                            value = uiState.socketToken,
                            onValueChange = onSocketTokenChange,
                            label = { Text("TOKEN") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = workspaceOutlinedTextFieldColors(),
                        )
                    }
                }
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onConnectCommandChannel,
                            enabled = uiState.canConnectSocket,
                            modifier = Modifier.fillMaxWidth(),
                            colors = workspaceFilledButtonColors(),
                        ) {
                            Text("登录指令通道")
                        }
                        OutlinedButton(
                            onClick = onDisconnectCommandChannel,
                            enabled = uiState.canDisconnectSocket,
                            modifier = Modifier.fillMaxWidth(),
                            colors = workspaceOutlinedButtonColors(),
                        ) {
                            Text("退出指令通道")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = onConnectCommandChannel,
                            enabled = uiState.canConnectSocket,
                            colors = workspaceFilledButtonColors(),
                        ) {
                            Text("登录指令通道")
                        }
                        OutlinedButton(
                            onClick = onDisconnectCommandChannel,
                            enabled = uiState.canDisconnectSocket,
                            colors = workspaceOutlinedButtonColors(),
                        ) {
                            Text("退出指令通道")
                        }
                    }
                }
                Text(
                    text = uiState.websocketDetailText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RuntimeSnapshotSection(
    dashboardState: DashboardUiState,
    outputState: OutputConfigUiState,
) {
    BoxWithConstraints {
        val runtimeCards = listOf(
            "监听快照" to dashboardState.serviceSupportingText,
            "蓝牙主层" to outputState.bluetoothLeaderLabel.ifBlank { "无" },
            "蓝牙混波" to "${outputState.bluetoothMixModeLabel} · 层数 ${outputState.activeBluetoothLayerCount}",
            "电流输出" to "A ${outputState.channelAStrength} · B ${outputState.channelBStrength}",
        )
        if (maxWidth >= 900.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                runtimeCards.forEach { (label, value) ->
                    WorkspaceMetricCard(
                        label = label,
                        value = value.ifBlank { "-" },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else if (maxWidth >= 560.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                runtimeCards.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowItems.forEach { (label, value) ->
                            WorkspaceMetricCard(
                                label = label,
                                value = value.ifBlank { "-" },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                runtimeCards.forEach { (label, value) ->
                    WorkspaceMetricCard(
                        label = label,
                        value = value.ifBlank { "-" },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChipGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = { content() },
    )
}
