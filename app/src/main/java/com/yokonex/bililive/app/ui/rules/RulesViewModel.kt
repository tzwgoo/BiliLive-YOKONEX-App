package com.yokonex.bililive.app.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yokonex.bililive.AppServices
import com.yokonex.bililive.data.storage.JsonRuleStore
import com.yokonex.bililive.domain.model.TriggerRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RulesViewModel(
    private val ruleStore: JsonRuleStore? = AppServices.container?.ruleStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RulesUiState())
    val uiState: StateFlow<RulesUiState> = _uiState.asStateFlow()

    init {
        ruleStore?.let { store ->
            viewModelScope.launch {
                store.rules.collect { rules ->
                    _uiState.update { currentState ->
                        currentState.copy(rules = rules.map(::toUiRuleItem))
                    }
                }
            }
        }
    }

    fun toggleRule(ruleId: String) {
        val store = ruleStore
        if (store != null) {
            viewModelScope.launch {
                store.toggleRule(ruleId)
            }
            return
        }
        _uiState.update { currentState ->
            currentState.copy(
                rules = currentState.rules.map { rule ->
                    if (rule.id == ruleId) {
                        rule.copy(enabled = !rule.enabled)
                    } else {
                        rule
                    }
                },
            )
        }
    }
}

data class RulesUiState(
    val rules: List<UiRuleItem> = sampleRules(),
)

data class UiRuleItem(
    val id: String,
    val name: String,
    val summary: String,
    val actionLabel: String,
    val enabled: Boolean,
)

private fun sampleRules(): List<UiRuleItem> = listOf(
    UiRuleItem(
        id = "rule_gift_combo",
        name = "高价值礼物",
        summary = "礼物总价值 >= 100 时触发强刺激波形。",
        actionLabel = "蓝牙 dual_tap",
        enabled = true,
    ),
    UiRuleItem(
        id = "rule_like_burst",
        name = "连击点赞",
        summary = "点赞数达到 30 的倍数时发送轻脉冲。",
        actionLabel = "蓝牙 soft_pulse",
        enabled = true,
    ),
    UiRuleItem(
        id = "rule_keyword",
        name = "关键词弹幕",
        summary = "包含“上强度”时发送 WebSocket 指令槽位 3。",
        actionLabel = "WebSocket slot_03",
        enabled = false,
    ),
)

private fun toUiRuleItem(rule: TriggerRule): UiRuleItem {
    val summary = buildString {
        when (rule.eventType) {
            com.yokonex.bililive.domain.model.LiveEventType.GIFT -> {
                append("礼物事件")
                rule.conditions.minPrice?.let { append("，总价值 >= $it") }
                rule.conditions.maxPrice?.let { append("，总价值 <= $it") }
            }

            com.yokonex.bililive.domain.model.LiveEventType.LIKE -> {
                append("点赞事件")
                rule.conditions.likeMultiple?.let { append("，达到 $it 的倍数时触发") }
            }

            com.yokonex.bililive.domain.model.LiveEventType.DANMAKU -> {
                append("弹幕事件")
                if (rule.conditions.keywords.isNotEmpty()) {
                    append("，关键词：")
                    append(rule.conditions.keywords.joinToString("、"))
                }
            }

            com.yokonex.bililive.domain.model.LiveEventType.SYSTEM -> {
                append("系统事件")
            }
        }
        if (rule.cooldownSeconds > 0) {
            append("，冷却 ${rule.cooldownSeconds} 秒")
        }
    }
    val actionLabel = when {
        rule.actionBindings.bluetoothAction != null && rule.actionBindings.websocketAction != null ->
            "蓝牙 ${rule.actionBindings.bluetoothAction.waveformId} / WebSocket 槽位 ${rule.actionBindings.websocketAction.commandSlot}"

        rule.actionBindings.bluetoothAction != null ->
            "蓝牙 ${rule.actionBindings.bluetoothAction.waveformId}"

        rule.actionBindings.websocketAction != null ->
            "WebSocket 槽位 ${rule.actionBindings.websocketAction.commandSlot}"

        else -> "未配置动作"
    }
    return UiRuleItem(
        id = rule.id,
        name = rule.name,
        summary = summary,
        actionLabel = actionLabel,
        enabled = rule.enabled,
    )
}
