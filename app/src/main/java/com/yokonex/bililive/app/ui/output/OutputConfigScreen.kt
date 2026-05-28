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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    onSocketUidChange: (String) -> Unit,
    onSocketTokenChange: (String) -> Unit,
    onConnectCommandChannel: () -> Unit,
    onDisconnectCommandChannel: () -> Unit,
    onScanBluetoothDevices: () -> Unit,
    onConnectBluetoothDevice: (String) -> Unit,
    onDisconnectBluetoothDevice: () -> Unit,
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
                    text = "设备连接",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "在蓝牙和 IM 指令两条执行通道之间切换，并维护各自的连接参数。",
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
                    label = { Text("IM 指令") },
                )
            }
        }
        if (uiState.outputMode == OutputMode.BLUETOOTH) {
            val batteryLabel = uiState.bluetoothBatteryLevel?.let { "$it%" } ?: "--"
            item {
                StatusCard(
                    title = "蓝牙状态",
                    value = uiState.bluetoothStatus,
                    supportingText = if (uiState.canDisconnectBluetooth) {
                        "设备 ${uiState.connectedBluetoothDeviceName} · 电量 $batteryLabel · A ${uiState.channelAStrength} · B ${uiState.channelBStrength}"
                    } else {
                        "未连接"
                    },
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onScanBluetoothDevices,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("扫描设备")
                    }
                    OutlinedButton(
                        onClick = onDisconnectBluetoothDevice,
                        enabled = uiState.canDisconnectBluetooth,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("断开设备")
                    }
                }
            }
            if (!uiState.bluetoothErrorMessage.isNullOrBlank()) {
                item {
                    Text(
                        text = uiState.bluetoothErrorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            items(uiState.bluetoothDevices, key = { it.id }) { device ->
                StatusCard(
                    title = device.name,
                    value = if (device.connected) "已连接" else "待连接",
                    supportingText = "协议 ${device.protocol}",
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (device.connected) {
                        OutlinedButton(
                            onClick = onDisconnectBluetoothDevice,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("断开当前设备")
                        }
                    } else {
                        Button(
                            onClick = { onConnectBluetoothDevice(device.id) },
                            enabled = uiState.canConnectBluetooth(device.id),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (uiState.connectingBluetoothDeviceId == device.id) {
                                    "连接中..."
                                } else {
                                    "连接设备"
                                },
                            )
                        }
                    }
                }
            }
        } else {
            item {
                StatusCard(
                    title = "IM 状态",
                    value = uiState.websocketStatus,
                    supportingText = "用于向下游执行器发送固定槽位指令。",
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
                    value = uiState.socketUid,
                    onValueChange = onSocketUidChange,
                    label = { Text("登录 UID") },
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
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onConnectCommandChannel,
                        enabled = uiState.canConnectSocket,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("登录指令通道")
                    }
                    OutlinedButton(
                        onClick = onDisconnectCommandChannel,
                        enabled = uiState.canDisconnectSocket,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("退出指令通道")
                    }
                }
            }
        }
    }
}
