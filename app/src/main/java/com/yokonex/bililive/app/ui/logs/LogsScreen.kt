package com.yokonex.bililive.app.ui.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
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
    onFilterSelected: (LogEventFilter) -> Unit = {},
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
                title = "当前筛选",
                value = "${uiState.selectedFilter.label} · ${uiState.logs.count { it.success }} 条成功",
                supportingText = "共 ${uiState.logs.size} 条，失败或跳过的事件会保留原因，便于回放定位。",
            )
        }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.availableFilters, key = { it.name }) { filter ->
                    FilterChip(
                        selected = filter == uiState.selectedFilter,
                        onClick = { onFilterSelected(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }
        }
        items(uiState.logs, key = { it.id }) { log ->
            EventLogItem(eventLog = log)
        }
    }
}
