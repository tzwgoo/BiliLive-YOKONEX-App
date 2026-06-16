package com.yokonex.bililive.app.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yokonex.bililive.app.ui.components.WorkspaceCard
import com.yokonex.bililive.app.ui.components.WorkspaceMetricCard
import com.yokonex.bililive.app.ui.components.WorkspacePageHeader
import com.yokonex.bililive.app.ui.components.workspaceFilterChipColors
import com.yokonex.bililive.app.ui.components.workspaceOutlinedTextFieldColors
import com.yokonex.bililive.app.ui.components.workspaceSwitchColors
import com.yokonex.bililive.app.ui.live.LiveConfigUiState
import com.yokonex.bililive.app.ui.rules.RulesUiState
import com.yokonex.bililive.app.ui.rules.UiGuardWaveformItem
import com.yokonex.bililive.app.ui.rules.UiRuleItem
import com.yokonex.bililive.app.ui.rules.UiWaveformOption
import com.yokonex.bililive.domain.model.CooldownScope
import com.yokonex.bililive.domain.model.GiftTriggerMode
import com.yokonex.bililive.domain.model.LiveEventType

@Composable
fun EventStudioScreen(
    liveConfigState: LiveConfigUiState,
    rulesState: RulesUiState,
    onGiftTriggerModeChange: (GiftTriggerMode) -> Unit,
    onLikeMultipleChange: (String) -> Unit,
    onDanmakuEnabledChange: (Boolean) -> Unit,
    onDanmakuKeywordsChange: (String) -> Unit,
    onDanmakuCooldownSecondsChange: (String) -> Unit,
    onDanmakuUserLimitWindowSecondsChange: (String) -> Unit,
    onDanmakuUserLimitMaxTriggersChange: (String) -> Unit,
    onDanmakuMinGuardLevelChange: (Int) -> Unit,
    onRuleToggle: (String) -> Unit,
    onGiftPriceRangeChange: (String, String, String) -> Unit,
    onRuleLikeMultipleChange: (String, String) -> Unit,
    onRuleKeywordsChange: (String, String) -> Unit,
    onCooldownSecondsChange: (String, String) -> Unit,
    onCooldownScopeChange: (String, CooldownScope) -> Unit,
    onMinGuardLevelChange: (String, Int) -> Unit,
    onUserLimitWindowSecondsChange: (String, String) -> Unit,
    onUserLimitMaxTriggersChange: (String, String) -> Unit,
    onBluetoothWaveformChange: (String, String) -> Unit,
    onGuardWaveformChange: (String, Int, String) -> Unit,
    onWebsocketSlotChange: (String, String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var activeTab by rememberSaveable { mutableStateOf(EventStudioTab.SHARED) }
    var selectedSharedKey by rememberSaveable { mutableStateOf(SHARED_LIKE_KEY) }
    var selectedImEventKey by rememberSaveable {
        mutableStateOf(rulesState.rules.firstOrNull()?.eventType?.name.orEmpty())
    }
    var selectedBluetoothEventKey by rememberSaveable {
        mutableStateOf(rulesState.rules.firstOrNull()?.eventType?.name.orEmpty())
    }

    // Android 端仍沿用统一 TriggerRule 存储，这里按工作区把同一批规则投影成桌面端的编辑视角。
    val imGroups = rulesState.rules.groupBy { it.eventType }
    val bluetoothGroups = rulesState.rules.groupBy { it.eventType }

    if (selectedImEventKey.isBlank() && imGroups.isNotEmpty()) {
        selectedImEventKey = imGroups.keys.first().name
    }
    if (selectedBluetoothEventKey.isBlank() && bluetoothGroups.isNotEmpty()) {
        selectedBluetoothEventKey = bluetoothGroups.keys.first().name
    }
    if (
        selectedImEventKey.isNotBlank() &&
        parseEventType(selectedImEventKey)?.let { eventType -> eventType in imGroups.keys } == false &&
        imGroups.isNotEmpty()
    ) {
        selectedImEventKey = imGroups.keys.first().name
    }
    if (
        selectedBluetoothEventKey.isNotBlank() &&
        parseEventType(selectedBluetoothEventKey)?.let { eventType -> eventType in bluetoothGroups.keys } == false &&
        bluetoothGroups.isNotEmpty()
    ) {
        selectedBluetoothEventKey = bluetoothGroups.keys.first().name
    }

    val currentImRules = imGroups[parseEventType(selectedImEventKey)] ?: emptyList()
    val currentBluetoothRules = bluetoothGroups[parseEventType(selectedBluetoothEventKey)] ?: emptyList()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        val wide = maxWidth >= 1100.dp
        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    eventStudioContent(
                        activeTab = activeTab,
                        selectedSharedKey = selectedSharedKey,
                        liveConfigState = liveConfigState,
                        rulesState = rulesState,
                        currentImRules = currentImRules,
                        currentBluetoothRules = currentBluetoothRules,
                        onGiftTriggerModeChange = onGiftTriggerModeChange,
                        onLikeMultipleChange = onLikeMultipleChange,
                        onDanmakuEnabledChange = onDanmakuEnabledChange,
                        onDanmakuKeywordsChange = onDanmakuKeywordsChange,
                        onDanmakuCooldownSecondsChange = onDanmakuCooldownSecondsChange,
                        onDanmakuUserLimitWindowSecondsChange = onDanmakuUserLimitWindowSecondsChange,
                        onDanmakuUserLimitMaxTriggersChange = onDanmakuUserLimitMaxTriggersChange,
                        onDanmakuMinGuardLevelChange = onDanmakuMinGuardLevelChange,
                        onRuleToggle = onRuleToggle,
                        onGiftPriceRangeChange = onGiftPriceRangeChange,
                        onRuleLikeMultipleChange = onRuleLikeMultipleChange,
                        onRuleKeywordsChange = onRuleKeywordsChange,
                        onCooldownSecondsChange = onCooldownSecondsChange,
                        onCooldownScopeChange = onCooldownScopeChange,
                        onMinGuardLevelChange = onMinGuardLevelChange,
                        onUserLimitWindowSecondsChange = onUserLimitWindowSecondsChange,
                        onUserLimitMaxTriggersChange = onUserLimitMaxTriggersChange,
                        onBluetoothWaveformChange = onBluetoothWaveformChange,
                        onGuardWaveformChange = onGuardWaveformChange,
                        onWebsocketSlotChange = onWebsocketSlotChange,
                    )
                }
                EventStudioSidebar(
                    activeTab = activeTab,
                    selectedSharedKey = selectedSharedKey,
                    selectedImEventKey = selectedImEventKey,
                    selectedBluetoothEventKey = selectedBluetoothEventKey,
                    imGroups = imGroups,
                    bluetoothGroups = bluetoothGroups,
                    onTabChange = { activeTab = it },
                    onSelectShared = { selectedSharedKey = it },
                    onSelectImEvent = { selectedImEventKey = it },
                    onSelectBluetoothEvent = { selectedBluetoothEventKey = it },
                    modifier = Modifier.width(300.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    EventStudioSidebar(
                        activeTab = activeTab,
                        selectedSharedKey = selectedSharedKey,
                        selectedImEventKey = selectedImEventKey,
                        selectedBluetoothEventKey = selectedBluetoothEventKey,
                        imGroups = imGroups,
                        bluetoothGroups = bluetoothGroups,
                        onTabChange = { activeTab = it },
                        onSelectShared = { selectedSharedKey = it },
                        onSelectImEvent = { selectedImEventKey = it },
                        onSelectBluetoothEvent = { selectedBluetoothEventKey = it },
                    )
                }
                eventStudioContent(
                    activeTab = activeTab,
                    selectedSharedKey = selectedSharedKey,
                    liveConfigState = liveConfigState,
                    rulesState = rulesState,
                    currentImRules = currentImRules,
                    currentBluetoothRules = currentBluetoothRules,
                    onGiftTriggerModeChange = onGiftTriggerModeChange,
                    onLikeMultipleChange = onLikeMultipleChange,
                    onDanmakuEnabledChange = onDanmakuEnabledChange,
                    onDanmakuKeywordsChange = onDanmakuKeywordsChange,
                    onDanmakuCooldownSecondsChange = onDanmakuCooldownSecondsChange,
                    onDanmakuUserLimitWindowSecondsChange = onDanmakuUserLimitWindowSecondsChange,
                    onDanmakuUserLimitMaxTriggersChange = onDanmakuUserLimitMaxTriggersChange,
                    onDanmakuMinGuardLevelChange = onDanmakuMinGuardLevelChange,
                    onRuleToggle = onRuleToggle,
                    onGiftPriceRangeChange = onGiftPriceRangeChange,
                    onRuleLikeMultipleChange = onRuleLikeMultipleChange,
                    onRuleKeywordsChange = onRuleKeywordsChange,
                    onCooldownSecondsChange = onCooldownSecondsChange,
                    onCooldownScopeChange = onCooldownScopeChange,
                    onMinGuardLevelChange = onMinGuardLevelChange,
                    onUserLimitWindowSecondsChange = onUserLimitWindowSecondsChange,
                    onUserLimitMaxTriggersChange = onUserLimitMaxTriggersChange,
                    onBluetoothWaveformChange = onBluetoothWaveformChange,
                    onGuardWaveformChange = onGuardWaveformChange,
                    onWebsocketSlotChange = onWebsocketSlotChange,
                )
            }
        }
    }
}

private fun LazyListScope.eventStudioContent(
    activeTab: EventStudioTab,
    selectedSharedKey: String,
    liveConfigState: LiveConfigUiState,
    rulesState: RulesUiState,
    currentImRules: List<UiRuleItem>,
    currentBluetoothRules: List<UiRuleItem>,
    onGiftTriggerModeChange: (GiftTriggerMode) -> Unit,
    onLikeMultipleChange: (String) -> Unit,
    onDanmakuEnabledChange: (Boolean) -> Unit,
    onDanmakuKeywordsChange: (String) -> Unit,
    onDanmakuCooldownSecondsChange: (String) -> Unit,
    onDanmakuUserLimitWindowSecondsChange: (String) -> Unit,
    onDanmakuUserLimitMaxTriggersChange: (String) -> Unit,
    onDanmakuMinGuardLevelChange: (Int) -> Unit,
    onRuleToggle: (String) -> Unit,
    onGiftPriceRangeChange: (String, String, String) -> Unit,
    onRuleLikeMultipleChange: (String, String) -> Unit,
    onRuleKeywordsChange: (String, String) -> Unit,
    onCooldownSecondsChange: (String, String) -> Unit,
    onCooldownScopeChange: (String, CooldownScope) -> Unit,
    onMinGuardLevelChange: (String, Int) -> Unit,
    onUserLimitWindowSecondsChange: (String, String) -> Unit,
    onUserLimitMaxTriggersChange: (String, String) -> Unit,
    onBluetoothWaveformChange: (String, String) -> Unit,
    onGuardWaveformChange: (String, Int, String) -> Unit,
    onWebsocketSlotChange: (String, String) -> Unit,
) {
    item {
        WorkspacePageHeader(title = "事件配置")
    }
    item {
        BoxWithConstraints {
            if (maxWidth < 560.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WorkspaceMetricCard(
                        label = "IM 档位",
                        value = rulesState.rules.size.toString(),
                    )
                    WorkspaceMetricCard(
                        label = "蓝牙规则",
                        value = rulesState.rules.count { it.selectedWaveformId.isNotBlank() }.toString(),
                    )
                    WorkspaceMetricCard(
                        label = "可选波形",
                        value = rulesState.rules.firstOrNull()?.waveformOptions?.size?.toString() ?: "0",
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WorkspaceMetricCard(
                        label = "IM 档位",
                        value = rulesState.rules.size.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    WorkspaceMetricCard(
                        label = "蓝牙规则",
                        value = rulesState.rules.count { it.selectedWaveformId.isNotBlank() }.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    WorkspaceMetricCard(
                        label = "可选波形",
                        value = rulesState.rules.firstOrNull()?.waveformOptions?.size?.toString() ?: "0",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    item {
        WorkspaceCard {
            Text(
                text = "当前页面的改动会实时写回本地配置，结构和桌面端保持一致：通用参数单独维护，IM 和蓝牙规则按事件拆分编辑。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    when (activeTab) {
        EventStudioTab.SHARED -> {
            item {
                SharedSettingsEditor(
                    selectedSharedKey = selectedSharedKey,
                    liveConfigState = liveConfigState,
                    onGiftTriggerModeChange = onGiftTriggerModeChange,
                    onLikeMultipleChange = onLikeMultipleChange,
                    onDanmakuEnabledChange = onDanmakuEnabledChange,
                    onDanmakuKeywordsChange = onDanmakuKeywordsChange,
                    onDanmakuCooldownSecondsChange = onDanmakuCooldownSecondsChange,
                    onDanmakuUserLimitWindowSecondsChange = onDanmakuUserLimitWindowSecondsChange,
                    onDanmakuUserLimitMaxTriggersChange = onDanmakuUserLimitMaxTriggersChange,
                    onDanmakuMinGuardLevelChange = onDanmakuMinGuardLevelChange,
                )
            }
        }

        EventStudioTab.IM -> {
            if (currentImRules.isEmpty()) {
                item {
                    EmptyEditorCard("请先从右侧选择 IM 事件")
                }
            } else {
                items(currentImRules, key = { it.id }) { rule ->
                    EventRuleEditorCard(
                        rule = rule,
                        mode = RuleEditorMode.IM,
                        onRuleToggle = onRuleToggle,
                        onGiftPriceRangeChange = onGiftPriceRangeChange,
                        onLikeMultipleChange = onRuleLikeMultipleChange,
                        onKeywordsChange = onRuleKeywordsChange,
                        onCooldownSecondsChange = onCooldownSecondsChange,
                        onCooldownScopeChange = onCooldownScopeChange,
                        onMinGuardLevelChange = onMinGuardLevelChange,
                        onUserLimitWindowSecondsChange = onUserLimitWindowSecondsChange,
                        onUserLimitMaxTriggersChange = onUserLimitMaxTriggersChange,
                        onBluetoothWaveformChange = onBluetoothWaveformChange,
                        onGuardWaveformChange = onGuardWaveformChange,
                        onWebsocketSlotChange = onWebsocketSlotChange,
                    )
                }
            }
        }

        EventStudioTab.BLUETOOTH -> {
            if (currentBluetoothRules.isEmpty()) {
                item {
                    EmptyEditorCard("请先从右侧选择蓝牙事件")
                }
            } else {
                items(currentBluetoothRules, key = { it.id }) { rule ->
                    EventRuleEditorCard(
                        rule = rule,
                        mode = RuleEditorMode.BLUETOOTH,
                        onRuleToggle = onRuleToggle,
                        onGiftPriceRangeChange = onGiftPriceRangeChange,
                        onLikeMultipleChange = onRuleLikeMultipleChange,
                        onKeywordsChange = onRuleKeywordsChange,
                        onCooldownSecondsChange = onCooldownSecondsChange,
                        onCooldownScopeChange = onCooldownScopeChange,
                        onMinGuardLevelChange = onMinGuardLevelChange,
                        onUserLimitWindowSecondsChange = onUserLimitWindowSecondsChange,
                        onUserLimitMaxTriggersChange = onUserLimitMaxTriggersChange,
                        onBluetoothWaveformChange = onBluetoothWaveformChange,
                        onGuardWaveformChange = onGuardWaveformChange,
                        onWebsocketSlotChange = onWebsocketSlotChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun EventStudioSidebar(
    activeTab: EventStudioTab,
    selectedSharedKey: String,
    selectedImEventKey: String,
    selectedBluetoothEventKey: String,
    imGroups: Map<LiveEventType, List<UiRuleItem>>,
    bluetoothGroups: Map<LiveEventType, List<UiRuleItem>>,
    onTabChange: (EventStudioTab) -> Unit,
    onSelectShared: (String) -> Unit,
    onSelectImEvent: (String) -> Unit,
    onSelectBluetoothEvent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    WorkspaceCard(modifier = modifier) {
        EventChipGroup(modifier = Modifier.fillMaxWidth()) {
            EventStudioTab.entries.forEach { tab ->
                FilterChip(
                    selected = activeTab == tab,
                    onClick = { onTabChange(tab) },
                    label = { Text(tab.label) },
                    colors = workspaceFilterChipColors(),
                )
            }
        }
        Text(
            text = "事件列表",
            style = MaterialTheme.typography.titleSmall,
        )
        when (activeTab) {
            EventStudioTab.SHARED -> {
                EventSidebarItem(
                    label = "点赞触发",
                    description = "公共倍率与礼物触发模式",
                    selected = selectedSharedKey == SHARED_LIKE_KEY,
                    onClick = { onSelectShared(SHARED_LIKE_KEY) },
                )
                EventSidebarItem(
                    label = "弹幕触发",
                    description = "关键词、冷却、限流与舰队门槛",
                    selected = selectedSharedKey == SHARED_DANMAKU_KEY,
                    onClick = { onSelectShared(SHARED_DANMAKU_KEY) },
                )
            }

            EventStudioTab.IM -> {
                // Android 端仍沿用统一 TriggerRule 存储，所以这里只按事件类型投影成桌面端的 IM 工作区。
                imGroups.forEach { (eventType, rules) ->
                    EventSidebarItem(
                        label = eventType.displayLabel,
                        description = "${rules.size} 条 IM 规则",
                        selected = selectedImEventKey == eventType.name,
                        onClick = { onSelectImEvent(eventType.name) },
                    )
                }
            }

            EventStudioTab.BLUETOOTH -> {
                bluetoothGroups.forEach { (eventType, rules) ->
                    EventSidebarItem(
                        label = eventType.displayLabel,
                        description = "${rules.size} 条波形规则",
                        selected = selectedBluetoothEventKey == eventType.name,
                        onClick = { onSelectBluetoothEvent(eventType.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EventSidebarItem(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    BoxWithConstraints {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (selected) Color(0x26D98AA8) else Color(0x0DFFFFFF),
                    shape = RoundedCornerShape(18.dp),
                )
                .border(
                    width = 1.dp,
                    color = if (selected) Color(0x55D98AA8) else Color(0x14FFFFFF),
                    shape = RoundedCornerShape(18.dp),
                )
                .clickable(onClick = onClick)
                .padding(if (maxWidth < 420.dp) 12.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SharedSettingsEditor(
    selectedSharedKey: String,
    liveConfigState: LiveConfigUiState,
    onGiftTriggerModeChange: (GiftTriggerMode) -> Unit,
    onLikeMultipleChange: (String) -> Unit,
    onDanmakuEnabledChange: (Boolean) -> Unit,
    onDanmakuKeywordsChange: (String) -> Unit,
    onDanmakuCooldownSecondsChange: (String) -> Unit,
    onDanmakuUserLimitWindowSecondsChange: (String) -> Unit,
    onDanmakuUserLimitMaxTriggersChange: (String) -> Unit,
    onDanmakuMinGuardLevelChange: (Int) -> Unit,
) {
    when (selectedSharedKey) {
        SHARED_DANMAKU_KEY -> {
            WorkspaceCard {
                BoxWithConstraints {
                    val compact = maxWidth < 620.dp
                    Text(
                        text = "弹幕触发配置",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("触发开关", style = MaterialTheme.typography.labelLarge)
                        Switch(
                            checked = liveConfigState.danmakuEnabled,
                            onCheckedChange = onDanmakuEnabledChange,
                            colors = workspaceSwitchColors(),
                        )
                    }
                    if (compact) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = liveConfigState.danmakuCooldownSeconds,
                                onValueChange = onDanmakuCooldownSecondsChange,
                                label = { Text("弹幕冷却秒数") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = workspaceOutlinedTextFieldColors(),
                            )
                            OutlinedTextField(
                                value = liveConfigState.danmakuUserLimitWindowSeconds,
                                onValueChange = onDanmakuUserLimitWindowSecondsChange,
                                label = { Text("每用户限流窗口") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = workspaceOutlinedTextFieldColors(),
                            )
                            OutlinedTextField(
                                value = liveConfigState.danmakuUserLimitMaxTriggers,
                                onValueChange = onDanmakuUserLimitMaxTriggersChange,
                                label = { Text("窗口内最大触发次数") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = workspaceOutlinedTextFieldColors(),
                            )
                            GuardLevelField(
                                selectedLevel = liveConfigState.danmakuMinGuardLevel,
                                onLevelSelected = onDanmakuMinGuardLevelChange,
                                label = "最低舰队等级",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedTextField(
                                value = liveConfigState.danmakuCooldownSeconds,
                                onValueChange = onDanmakuCooldownSecondsChange,
                                label = { Text("弹幕冷却秒数") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = workspaceOutlinedTextFieldColors(),
                            )
                            OutlinedTextField(
                                value = liveConfigState.danmakuUserLimitWindowSeconds,
                                onValueChange = onDanmakuUserLimitWindowSecondsChange,
                                label = { Text("每用户限流窗口") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = workspaceOutlinedTextFieldColors(),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedTextField(
                                value = liveConfigState.danmakuUserLimitMaxTriggers,
                                onValueChange = onDanmakuUserLimitMaxTriggersChange,
                                label = { Text("窗口内最大触发次数") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = workspaceOutlinedTextFieldColors(),
                            )
                            GuardLevelField(
                                selectedLevel = liveConfigState.danmakuMinGuardLevel,
                                onLevelSelected = onDanmakuMinGuardLevelChange,
                                label = "最低舰队等级",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = liveConfigState.danmakuKeywords,
                        onValueChange = onDanmakuKeywordsChange,
                        label = { Text("弹幕关键词") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = workspaceOutlinedTextFieldColors(),
                    )
                    Text(
                        text = "弹幕关键词命中后，会复用当前保存的通用规则同时驱动 IM 和蓝牙事件链路。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        else -> {
            WorkspaceCard {
                Text(
                    text = "点赞触发配置",
                    style = MaterialTheme.typography.titleMedium,
                )
                EventChipGroup(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = liveConfigState.giftTriggerMode == GiftTriggerMode.BY_QUANTITY,
                        onClick = { onGiftTriggerModeChange(GiftTriggerMode.BY_QUANTITY) },
                        label = { Text("按礼物数量触发") },
                        colors = workspaceFilterChipColors(),
                    )
                    FilterChip(
                        selected = liveConfigState.giftTriggerMode == GiftTriggerMode.SINGLE,
                        onClick = { onGiftTriggerModeChange(GiftTriggerMode.SINGLE) },
                        label = { Text("单次触发") },
                        colors = workspaceFilterChipColors(),
                    )
                }
                OutlinedTextField(
                    value = liveConfigState.likeMultiple,
                    onValueChange = onLikeMultipleChange,
                    label = { Text("点赞倍率") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = workspaceOutlinedTextFieldColors(),
                )
                Text(
                    text = "该配置会同时影响 IM 指令和蓝牙触发链路。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private enum class RuleEditorMode {
    IM,
    BLUETOOTH,
}

@Composable
private fun EventRuleEditorCard(
    rule: UiRuleItem,
    mode: RuleEditorMode,
    onRuleToggle: (String) -> Unit,
    onGiftPriceRangeChange: (String, String, String) -> Unit,
    onLikeMultipleChange: (String, String) -> Unit,
    onKeywordsChange: (String, String) -> Unit,
    onCooldownSecondsChange: (String, String) -> Unit,
    onCooldownScopeChange: (String, CooldownScope) -> Unit,
    onMinGuardLevelChange: (String, Int) -> Unit,
    onUserLimitWindowSecondsChange: (String, String) -> Unit,
    onUserLimitMaxTriggersChange: (String, String) -> Unit,
    onBluetoothWaveformChange: (String, String) -> Unit,
    onGuardWaveformChange: (String, Int, String) -> Unit,
    onWebsocketSlotChange: (String, String) -> Unit,
) {
    var minPriceText by rememberSaveable(rule.id, "min", rule.minPriceText) { mutableStateOf(rule.minPriceText) }
    var maxPriceText by rememberSaveable(rule.id, "max", rule.maxPriceText) { mutableStateOf(rule.maxPriceText) }
    var likeMultipleText by rememberSaveable(rule.id, "like", rule.likeMultipleText) { mutableStateOf(rule.likeMultipleText) }
    var keywordsText by rememberSaveable(rule.id, "keywords", rule.keywordsText) { mutableStateOf(rule.keywordsText) }
    var cooldownText by rememberSaveable(rule.id, "cooldown", rule.cooldownSecondsText) { mutableStateOf(rule.cooldownSecondsText) }
    var userWindowText by rememberSaveable(rule.id, "window", rule.userLimitWindowSecondsText) { mutableStateOf(rule.userLimitWindowSecondsText) }
    var userMaxText by rememberSaveable(rule.id, "maxTriggers", rule.userLimitMaxTriggersText) { mutableStateOf(rule.userLimitMaxTriggersText) }

    WorkspaceCard {
        BoxWithConstraints {
            val compact = maxWidth < 620.dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = rule.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onRuleToggle(rule.id) },
                    colors = workspaceSwitchColors(),
                )
            }

            if (mode == RuleEditorMode.IM) {
                StudioDropdownField(
                    label = "指令槽位",
                    selectedValue = rule.imSlotLabel.ifBlank { "未绑定固定槽位" },
                    options = rule.commandSlotOptions.map { option -> option.label to option.id },
                    onSelected = { optionId -> onWebsocketSlotChange(rule.id, optionId) },
                )
            } else {
                StudioDropdownField(
                    label = "EMS 波形",
                    selectedValue = rule.waveformOptions.firstOrNull { it.id == rule.selectedWaveformId }?.name ?: "未配置波形",
                    options = rule.waveformOptions.map { option -> option.name to option.id },
                    onSelected = { optionId -> onBluetoothWaveformChange(rule.id, optionId) },
                )
                Text(
                    text = rule.actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (rule.canEditGiftPriceRange) {
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = minPriceText,
                            onValueChange = { value ->
                                minPriceText = value.filter(Char::isDigit)
                                onGiftPriceRangeChange(rule.id, minPriceText, maxPriceText)
                            },
                            label = { Text("最低价格") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = workspaceOutlinedTextFieldColors(),
                        )
                        OutlinedTextField(
                            value = maxPriceText,
                            onValueChange = { value ->
                                maxPriceText = value.filter(Char::isDigit)
                                onGiftPriceRangeChange(rule.id, minPriceText, maxPriceText)
                            },
                            label = { Text("最高价格") },
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
                            value = minPriceText,
                            onValueChange = { value ->
                                minPriceText = value.filter(Char::isDigit)
                                onGiftPriceRangeChange(rule.id, minPriceText, maxPriceText)
                            },
                            label = { Text("最低价格") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = workspaceOutlinedTextFieldColors(),
                        )
                        OutlinedTextField(
                            value = maxPriceText,
                            onValueChange = { value ->
                                maxPriceText = value.filter(Char::isDigit)
                                onGiftPriceRangeChange(rule.id, minPriceText, maxPriceText)
                            },
                            label = { Text("最高价格") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = workspaceOutlinedTextFieldColors(),
                        )
                    }
                }
            }

            if (rule.canEditLikeMultiple) {
                OutlinedTextField(
                    value = likeMultipleText,
                    onValueChange = { value ->
                        likeMultipleText = value.filter(Char::isDigit)
                        onLikeMultipleChange(rule.id, likeMultipleText)
                    },
                    label = { Text("点赞倍率") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = workspaceOutlinedTextFieldColors(),
                )
            }

            if (rule.canEditKeywords) {
                OutlinedTextField(
                    value = keywordsText,
                    onValueChange = { value ->
                        keywordsText = value
                        onKeywordsChange(rule.id, keywordsText)
                    },
                    label = { Text("关键词（逗号分隔）") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = workspaceOutlinedTextFieldColors(),
                )
            }

            OutlinedTextField(
                value = cooldownText,
                onValueChange = { value ->
                    cooldownText = value.filter(Char::isDigit)
                    onCooldownSecondsChange(rule.id, cooldownText)
                },
                label = { Text("冷却秒数") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = workspaceOutlinedTextFieldColors(),
            )

            if (rule.canEditCooldownScope) {
                StudioDropdownField(
                    label = "冷却范围",
                    selectedValue = rule.cooldownScope.toDisplayLabel(),
                    options = CooldownScope.entries.map { option -> option.toDisplayLabel() to option.name },
                    onSelected = { optionName ->
                        CooldownScope.entries.firstOrNull { it.name == optionName }?.let { scope ->
                            onCooldownScopeChange(rule.id, scope)
                        }
                    },
                )
            }

            if (rule.canEditMinGuardLevel) {
                GuardLevelField(
                    selectedLevel = rule.minGuardLevel,
                    onLevelSelected = { level -> onMinGuardLevelChange(rule.id, level) },
                    label = "最低舰队等级",
                )
            }

            if (rule.canEditUserLimitWindowSeconds || rule.canEditUserLimitMaxTriggers) {
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (rule.canEditUserLimitWindowSeconds) {
                            OutlinedTextField(
                                value = userWindowText,
                                onValueChange = { value ->
                                    userWindowText = value.filter(Char::isDigit)
                                    onUserLimitWindowSecondsChange(rule.id, userWindowText)
                                },
                                label = { Text("每用户限流窗口") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = workspaceOutlinedTextFieldColors(),
                            )
                        }
                        if (rule.canEditUserLimitMaxTriggers) {
                            OutlinedTextField(
                                value = userMaxText,
                                onValueChange = { value ->
                                    userMaxText = value.filter(Char::isDigit)
                                    onUserLimitMaxTriggersChange(rule.id, userMaxText)
                                },
                                label = { Text("窗口内最大次数") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = workspaceOutlinedTextFieldColors(),
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (rule.canEditUserLimitWindowSeconds) {
                            OutlinedTextField(
                                value = userWindowText,
                                onValueChange = { value ->
                                    userWindowText = value.filter(Char::isDigit)
                                    onUserLimitWindowSecondsChange(rule.id, userWindowText)
                                },
                                label = { Text("每用户限流窗口") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = workspaceOutlinedTextFieldColors(),
                            )
                        }
                        if (rule.canEditUserLimitMaxTriggers) {
                            OutlinedTextField(
                                value = userMaxText,
                                onValueChange = { value ->
                                    userMaxText = value.filter(Char::isDigit)
                                    onUserLimitMaxTriggersChange(rule.id, userMaxText)
                                },
                                label = { Text("窗口内最大次数") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = workspaceOutlinedTextFieldColors(),
                            )
                        }
                    }
                }
            }

            if (mode == RuleEditorMode.BLUETOOTH && rule.canEditGuardWaveforms) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "舰队专属波形（可选覆盖）",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    rule.guardWaveforms.forEach { item ->
                        GuardWaveformField(
                            item = item,
                            waveformOptions = rule.waveformOptions,
                            onSelected = { waveformId ->
                                onGuardWaveformChange(rule.id, item.guardLevel, waveformId)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyEditorCard(message: String) {
    WorkspaceCard {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioDropdownField(
    label: String,
    selectedValue: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(label, selectedValue) { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedValue,
            onValueChange = { },
            label = { Text(label) },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            colors = workspaceOutlinedTextFieldColors(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (text, value) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelected(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun GuardWaveformField(
    item: UiGuardWaveformItem,
    waveformOptions: List<UiWaveformOption>,
    onSelected: (String) -> Unit,
) {
    StudioDropdownField(
        label = "${item.label}波形",
        selectedValue = waveformOptions.firstOrNull { it.id == item.waveformId }?.name ?: "跟随默认",
        options = listOf("跟随默认" to "") + waveformOptions.map { option -> option.name to option.id },
        onSelected = onSelected,
    )
}

@Composable
private fun GuardLevelField(
    selectedLevel: Int,
    onLevelSelected: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    StudioDropdownField(
        label = label,
        selectedValue = selectedLevel.toGuardLevelLabel(),
        options = listOf(
            "不限" to "0",
            "舰长及以上" to "3",
            "提督及以上" to "2",
            "总督" to "1",
        ),
        onSelected = { value -> onLevelSelected(value.toIntOrNull() ?: 0) },
        modifier = modifier,
    )
}

private enum class EventStudioTab(val label: String) {
    SHARED("通用"),
    IM("IM"),
    BLUETOOTH("蓝牙"),
}

private fun parseEventType(name: String): LiveEventType? =
    runCatching { LiveEventType.valueOf(name) }.getOrNull()

private fun CooldownScope.toDisplayLabel(): String =
    when (this) {
        CooldownScope.GLOBAL -> "全局冷却"
        CooldownScope.PER_USER -> "按用户冷却"
    }

private fun Int.toGuardLevelLabel(): String =
    when (this) {
        1 -> "总督"
        2 -> "提督及以上"
        3 -> "舰长及以上"
        else -> "不限"
    }

private const val SHARED_LIKE_KEY = "like"
private const val SHARED_DANMAKU_KEY = "danmaku"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventChipGroup(
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
