package com.yokonex.bililive.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
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
                    text = "状态",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "首页只展示当前监听和输出状态，配置与控制动作分流到对应页面处理。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            BoxWithConstraints {
                val cardWidth = (maxWidth - 12.dp) / 2
                val cardHeight = 176.dp
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatusCard(
                            title = "监听状态",
                            value = uiState.serviceStatusLabel,
                            supportingText = "房间 ${uiState.roomId}",
                            modifier = Modifier
                                .width(cardWidth)
                                .height(cardHeight),
                        )
                        StatusCard(
                            title = "当前主播",
                            value = uiState.anchorName,
                            supportingText = "房间 ${uiState.roomId}",
                            modifier = Modifier
                                .width(cardWidth)
                                .height(cardHeight),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatusCard(
                            title = "输出模式",
                            value = uiState.outputModeLabel,
                            supportingText = if (uiState.outputMode == OutputMode.BLUETOOTH) {
                                "当前输出走蓝牙设备"
                            } else {
                                "当前输出走 IM 指令通道"
                            },
                            modifier = Modifier
                                .width(cardWidth)
                                .height(cardHeight),
                        )
                        if (uiState.outputMode == OutputMode.BLUETOOTH) {
                            val batteryLabel = uiState.bluetoothBatteryLevel?.let { "$it%" } ?: "--"
                            StatusCard(
                                title = "蓝牙设备",
                                value = if (uiState.bluetoothConnected) uiState.bluetoothDeviceName else "未连接",
                                supportingText = if (uiState.bluetoothConnected) {
                                    "电量 $batteryLabel · A ${uiState.channelAStrength} · B ${uiState.channelBStrength}"
                                } else {
                                    "未连接"
                                },
                                modifier = Modifier
                                    .width(cardWidth)
                                    .height(cardHeight),
                            )
                        } else {
                            StatusCard(
                                title = "IM 状态",
                                value = uiState.imStatus,
                                supportingText = "指令通道状态",
                                modifier = Modifier
                                    .width(cardWidth)
                                    .height(cardHeight),
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(
                text = "最近事件",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        items(uiState.recentEventSections, key = { it.title }) { section ->
            DashboardEventSectionCard(section = section)
        }
    }
}

@Composable
private fun DashboardEventSectionCard(
    section: DashboardEventSection,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = section.supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (section.events.isEmpty()) {
                Text(
                    text = "暂无${section.title}事件",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    section.events.forEach { eventLog ->
                        EventLogItem(eventLog = eventLog)
                    }
                }
            }
        }
    }
}
