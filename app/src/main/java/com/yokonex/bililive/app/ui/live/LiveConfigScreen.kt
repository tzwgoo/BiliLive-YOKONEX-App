package com.yokonex.bililive.app.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokonex.bililive.app.ui.components.StatusCard

@Composable
fun LiveConfigScreen(
    uiState: LiveConfigUiState,
    onRoomIdChange: (String) -> Unit,
    onAutoReconnectChange: (Boolean) -> Unit,
    onReconnectIntervalChange: (String) -> Unit,
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
                    text = "直播连接",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "维护房间号、消息源和自动重连策略。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            StatusCard(
                title = "消息源",
                value = uiState.providerName,
                supportingText = "当前状态：${uiState.connectionStatus}",
            )
        }
        item {
            OutlinedTextField(
                value = uiState.roomId,
                onValueChange = onRoomIdChange,
                label = { Text("房间号") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "自动重连",
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(
                    checked = uiState.autoReconnect,
                    onCheckedChange = onAutoReconnectChange,
                )
            }
        }
        item {
            OutlinedTextField(
                value = uiState.reconnectIntervalSeconds,
                onValueChange = onReconnectIntervalChange,
                label = { Text("重连间隔（秒）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}
