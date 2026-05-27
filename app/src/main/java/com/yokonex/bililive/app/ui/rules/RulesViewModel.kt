package com.yokonex.bililive.app.ui.rules

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class RulesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(RulesUiState())
    val uiState: StateFlow<RulesUiState> = _uiState.asStateFlow()

    fun toggleRule(ruleId: String) {
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
