package com.yokonex.bililive.app.ui.dashboard

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokonex.bililive.app.ui.components.EventLogItem
import com.yokonex.bililive.app.ui.components.StatusCard
import com.yokonex.bililive.domain.model.OutputMode

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onToggleMonitoring: () -> Unit,
    onOutputModeChange: (OutputMode) -> Unit,
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
                    text = "直播控制台",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "把监听状态、输出模式和最近触发结果集中在一页里。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            StatusCard(
                title = "监听状态",
                value = uiState.serviceStatusLabel,
                supportingText = "房间 ${uiState.roomId}",
            )
        }
        item {
            StatusCard(
                title = "输出模式",
                value = if (uiState.outputMode == OutputMode.BLUETOOTH) "蓝牙 EMS" else "WebSocket 指令",
                supportingText = "切换后新命中的规则会走对应输出通道。",
            )
        }
        item {
            Text(
                text = "输出模式",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilterChip(
                    selected = uiState.outputMode == OutputMode.BLUETOOTH,
                    onClick = { onOutputModeChange(OutputMode.BLUETOOTH) },
                    label = { Text("蓝牙") },
                )
                FilterChip(
                    selected = uiState.outputMode == OutputMode.WEBSOCKET,
                    onClick = { onOutputModeChange(OutputMode.WEBSOCKET) },
                    label = { Text("WebSocket") },
                )
            }
        }
        item {
            Button(
                onClick = onToggleMonitoring,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(uiState.startButtonLabel)
            }
        }
        item {
            Text(
                text = "最近事件",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        items(uiState.recentEvents, key = { it.id }) { eventLog ->
            EventLogItem(eventLog = eventLog)
        }
    }
}
