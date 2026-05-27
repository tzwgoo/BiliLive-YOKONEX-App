package com.yokonex.bililive.app.ui.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokonex.bililive.app.ui.components.EventLogItem
import com.yokonex.bililive.app.ui.components.StatusCard

@Composable
fun LogsScreen(
    uiState: LogsUiState,
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
                    text = "事件日志",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "查看最近一次规则匹配、输出执行和跳过原因。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            StatusCard(
                title = "最近 10 分钟",
                value = "${uiState.logs.count { it.success }} 条成功",
                supportingText = "失败或跳过的事件会保留原因，便于回放定位。",
            )
        }
        items(uiState.logs, key = { it.id }) { log ->
            EventLogItem(eventLog = log)
        }
    }
}
