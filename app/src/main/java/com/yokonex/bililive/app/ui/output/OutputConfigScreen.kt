package com.yokonex.bililive.app.ui.output

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokonex.bililive.app.ui.components.StatusCard
import com.yokonex.bililive.domain.model.OutputMode

@Composable
fun OutputConfigScreen(
    uiState: OutputConfigUiState,
    onOutputModeChange: (OutputMode) -> Unit,
    onSocketEndpointChange: (String) -> Unit,
    onSocketTokenChange: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "输出配置",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "在蓝牙和 WebSocket 两条执行通道之间切换，并维护各自的连接参数。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Text(
                text = "输出通道",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilterChip(
                    selected = uiState.outputMode == OutputMode.BLUETOOTH,
                    onClick = { onOutputModeChange(OutputMode.BLUETOOTH) },
                    label = { Text("蓝牙 EMS") },
                )
                FilterChip(
                    selected = uiState.outputMode == OutputMode.WEBSOCKET,
                    onClick = { onOutputModeChange(OutputMode.WEBSOCKET) },
                    label = { Text("WebSocket") },
                )
            }
        }
        if (uiState.outputMode == OutputMode.BLUETOOTH) {
            item {
                StatusCard(
                    title = "已配对设备",
                    value = "${uiState.bluetoothDevices.count { it.connected }} 台在线",
                    supportingText = "优先展示已识别的 ems_v1 / ems_v2 设备。",
                )
            }
            items(uiState.bluetoothDevices, key = { it.id }) { device ->
                StatusCard(
                    title = device.name,
                    value = if (device.connected) "已连接" else "待连接",
                    supportingText = "协议 ${device.protocol}",
                )
            }
        } else {
            item {
                StatusCard(
                    title = "Socket 状态",
                    value = uiState.websocketStatus,
                    supportingText = "用于向外部执行器发送指令槽位。",
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.socketEndpoint,
                    onValueChange = onSocketEndpointChange,
                    label = { Text("服务地址") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.socketToken,
                    onValueChange = onSocketTokenChange,
                    label = { Text("授权令牌") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }
    }
}
