package com.yokonex.bililive.app.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yokonex.bililive.domain.model.GiftTriggerMode
import com.yokonex.bililive.app.ui.components.StatusCard

@Composable
fun LiveConfigScreen(
    uiState: LiveConfigUiState,
    onRoomIdChange: (String) -> Unit,
    onAutoReconnectChange: (Boolean) -> Unit,
    onReconnectIntervalChange: (String) -> Unit,
    onGiftTriggerModeChange: (GiftTriggerMode) -> Unit,
    onLikeMultipleChange: (String) -> Unit,
    onDanmakuEnabledChange: (Boolean) -> Unit,
    onDanmakuKeywordsChange: (String) -> Unit,
    onDanmakuCooldownSecondsChange: (String) -> Unit,
    onRefreshBatteryOptimizationStatus: () -> Unit,
    onRequestIgnoreBatteryOptimization: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onToggleMonitoring: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var roomIdFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = uiState.roomId,
                selection = TextRange(uiState.roomId.length),
            ),
        )
    }
    var danmakuKeywordsFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = uiState.danmakuKeywords,
                selection = TextRange(uiState.danmakuKeywords.length),
            ),
        )
    }

    LaunchedEffect(uiState.roomId) {
        if (uiState.roomId != roomIdFieldValue.text) {
            roomIdFieldValue = TextFieldValue(
                text = uiState.roomId,
                selection = TextRange(uiState.roomId.length),
            )
        }
    }
    LaunchedEffect(uiState.danmakuKeywords) {
        if (uiState.danmakuKeywords != danmakuKeywordsFieldValue.text) {
            danmakuKeywordsFieldValue = TextFieldValue(
                text = uiState.danmakuKeywords,
                selection = TextRange(uiState.danmakuKeywords.length),
            )
        }
    }
    DisposableEffect(lifecycleOwner, onRefreshBatteryOptimizationStatus) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshBatteryOptimizationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
                    text = "直播配置",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "维护房间号、消息源和自动重连策略，并在这里统一控制监听启停。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            StatusCard(
                title = "监听状态",
                value = uiState.monitoringStatus,
                supportingText = uiState.monitoringSupportingText,
            )
        }
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusCard(
                    title = "息屏保活",
                    value = uiState.batteryOptimizationStatus,
                    supportingText = uiState.batteryOptimizationHint,
                )
                if (uiState.shouldShowBatteryOptimizationAction) {
                    Button(
                        onClick = onRequestIgnoreBatteryOptimization,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("申请关闭电池优化")
                    }
                    TextButton(
                        onClick = onOpenBatteryOptimizationSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("打开系统电池优化设置")
                    }
                }
            }
        }
        item {
            Button(
                onClick = onToggleMonitoring,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(uiState.monitoringButtonLabel)
            }
        }
        item {
            OutlinedTextField(
                value = roomIdFieldValue,
                onValueChange = { nextValue ->
                    val sanitized = nextValue.text.filter(Char::isDigit).take(12)
                    val selection = nextValue.selection.end.coerceAtMost(sanitized.length)
                    roomIdFieldValue = nextValue.copy(
                        text = sanitized,
                        selection = TextRange(selection),
                    )
                    onRoomIdChange(sanitized)
                },
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
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "礼物参数",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "礼物档位按单个礼物价值匹配，触发次数按这里的全局模式决定。当前：${uiState.giftTriggerModeLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = uiState.giftTriggerMode == GiftTriggerMode.SINGLE,
                        onClick = { onGiftTriggerModeChange(GiftTriggerMode.SINGLE) },
                        label = { Text("单次触发") },
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.giftTriggerMode == GiftTriggerMode.BY_QUANTITY,
                        onClick = { onGiftTriggerModeChange(GiftTriggerMode.BY_QUANTITY) },
                        label = { Text("按数量触发") },
                    )
                }
            }
        }
        item {
            Text(
                text = "点赞参数",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            OutlinedTextField(
                value = uiState.likeMultiple,
                onValueChange = onLikeMultipleChange,
                label = { Text("点赞倍数阈值") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "弹幕参数",
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(
                    checked = uiState.danmakuEnabled,
                    onCheckedChange = onDanmakuEnabledChange,
                )
            }
        }
        item {
            OutlinedTextField(
                value = danmakuKeywordsFieldValue,
                onValueChange = { nextValue ->
                    danmakuKeywordsFieldValue = nextValue
                    onDanmakuKeywordsChange(nextValue.text)
                },
                label = { Text("弹幕关键词（逗号分隔）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = uiState.danmakuCooldownSeconds,
                onValueChange = onDanmakuCooldownSecondsChange,
                label = { Text("弹幕冷却（秒）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}
