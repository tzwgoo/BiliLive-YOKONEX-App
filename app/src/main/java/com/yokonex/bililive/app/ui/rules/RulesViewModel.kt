package com.yokonex.bililive.app.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yokonex.bililive.AppServices
import com.yokonex.bililive.data.mapper.WaveformMapper
import com.yokonex.bililive.data.storage.DefaultWaveforms
import com.yokonex.bililive.data.storage.JsonRuleStore
import com.yokonex.bililive.data.storage.dao.WaveformDao
import com.yokonex.bililive.domain.model.LiveEventType
import com.yokonex.bililive.domain.model.OutputAction
import com.yokonex.bililive.domain.model.TriggerRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RulesViewModel(
    private val ruleStore: JsonRuleStore? = AppServices.container?.ruleStore,
    private val waveformDao: WaveformDao? = AppServices.container?.waveformDao,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RulesUiState())
    val uiState: StateFlow<RulesUiState> = _uiState.asStateFlow()

    private var currentRules: List<TriggerRule> = sampleDomainRules()
    private var currentWaveforms: List<UiWaveformOption> = sampleWaveformOptions()

    init {
        ruleStore?.let { store ->
            viewModelScope.launch {
                store.rules.collect { rules ->
                    currentRules = rules
                    syncUiState()
                }
            }
        }
        waveformDao?.let { dao ->
            viewModelScope.launch {
                dao.observeAll().collect { waveforms ->
                    currentWaveforms = waveforms
                        .map(WaveformMapper::fromEntity)
                        .map { waveform ->
                            UiWaveformOption(
                                id = waveform.id,
                                name = waveform.name,
                            )
                        }
                    syncUiState()
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
        currentRules = currentRules.map { rule ->
            if (rule.id == ruleId) {
                rule.copy(enabled = !rule.enabled)
            } else {
                rule
            }
        }
        syncUiState()
    }

    fun updateGiftPriceRange(
        ruleId: String,
        minPriceText: String,
        maxPriceText: String,
    ) {
        updateRule(ruleId) { rule ->
            rule.copy(
                conditions = rule.conditions.copy(
                    minPrice = minPriceText.toIntOrNull(),
                    maxPrice = maxPriceText.toIntOrNull(),
                ),
            )
        }
    }

    fun updateBluetoothWaveform(
        ruleId: String,
        waveformId: String,
    ) {
        updateRule(ruleId) { rule ->
            rule.copy(
                actionBindings = rule.actionBindings.copy(
                    bluetoothAction = waveformId.takeIf(String::isNotBlank)
                        ?.let(OutputAction::BluetoothWaveformAction),
                ),
            )
        }
    }

    private fun updateRule(
        ruleId: String,
        transform: (TriggerRule) -> TriggerRule,
    ) {
        val updatedRule = currentRules.firstOrNull { it.id == ruleId }?.let(transform) ?: return
        val store = ruleStore
        if (store != null) {
            viewModelScope.launch {
                store.updateRule(updatedRule)
            }
            return
        }
        currentRules = currentRules.map { rule ->
            if (rule.id == ruleId) {
                updatedRule
            } else {
                rule
            }
        }
        syncUiState()
    }

    private fun syncUiState() {
        _uiState.update { currentState ->
            currentState.copy(
                rules = currentRules.map { rule ->
                    toUiRuleItem(
                        rule = rule,
                        waveformOptions = currentWaveforms,
                    )
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
    val canEditGiftPriceRange: Boolean,
    val minPriceText: String,
    val maxPriceText: String,
    val selectedWaveformId: String,
    val waveformOptions: List<UiWaveformOption>,
    val imSlotLabel: String,
)

data class UiWaveformOption(
    val id: String,
    val name: String,
)

private fun sampleRules(): List<UiRuleItem> =
    sampleDomainRules().map { rule ->
        toUiRuleItem(rule, sampleWaveformOptions())
    }

private fun sampleDomainRules(): List<TriggerRule> = listOf(
    TriggerRule(
        id = "rule_gift_combo",
        name = "高价值礼物",
        enabled = true,
        eventType = LiveEventType.GIFT,
        conditions = com.yokonex.bililive.domain.model.RuleConditions(
            minPrice = 100,
            maxPrice = 999,
        ),
        actionBindings = com.yokonex.bililive.domain.model.ActionBindings(
            bluetoothAction = OutputAction.BluetoothWaveformAction("ems-preset-06"),
            websocketAction = OutputAction.WebSocketCommandAction("2"),
        ),
    ),
    TriggerRule(
        id = "rule_like_default",
        name = "点赞默认规则",
        enabled = true,
        eventType = LiveEventType.LIKE,
        conditions = com.yokonex.bililive.domain.model.RuleConditions(
            likeMultiple = 100,
        ),
        actionBindings = com.yokonex.bililive.domain.model.ActionBindings(
            bluetoothAction = OutputAction.BluetoothWaveformAction("ems-preset-01"),
            websocketAction = OutputAction.WebSocketCommandAction("3"),
        ),
    ),
    TriggerRule(
        id = "rule_danmaku_default",
        name = "弹幕默认规则",
        enabled = false,
        eventType = LiveEventType.DANMAKU,
        cooldownSeconds = 0,
        conditions = com.yokonex.bililive.domain.model.RuleConditions(
            keywords = emptyList(),
        ),
        actionBindings = com.yokonex.bililive.domain.model.ActionBindings(
            bluetoothAction = OutputAction.BluetoothWaveformAction("ems-preset-03"),
            websocketAction = OutputAction.WebSocketCommandAction("3"),
        ),
    ),
)

private fun sampleWaveformOptions(): List<UiWaveformOption> =
    DefaultWaveforms.all.map { waveform ->
        UiWaveformOption(
            id = waveform.id,
            name = waveform.name,
        )
    }

private fun toUiRuleItem(
    rule: TriggerRule,
    waveformOptions: List<UiWaveformOption>,
): UiRuleItem {
    val summary = buildString {
        when (rule.eventType) {
            LiveEventType.GIFT -> {
                append("礼物事件")
                rule.conditions.minPrice?.let { append("，总价值 >= $it") }
                rule.conditions.maxPrice?.let { append("，总价值 <= $it") }
            }

            LiveEventType.LIKE -> {
                append("点赞事件")
                rule.conditions.likeMultiple?.let { append("，达到 $it 的倍数时触发") }
            }

            LiveEventType.DANMAKU -> {
                append("弹幕事件")
                if (rule.conditions.keywords.isNotEmpty()) {
                    append("，关键词：")
                    append(rule.conditions.keywords.joinToString("、"))
                }
            }

            LiveEventType.SYSTEM -> append("系统事件")
        }
        if (rule.cooldownSeconds > 0) {
            append("，冷却 ${rule.cooldownSeconds} 秒")
        }
    }
    val waveformId = rule.actionBindings.bluetoothAction?.waveformId.orEmpty()
    val waveformName = waveformOptions.firstOrNull { option -> option.id == waveformId }?.name ?: waveformId
    val imSlotLabel = rule.actionBindings.websocketAction?.commandSlot?.toFixedSlotLabel().orEmpty()
    return UiRuleItem(
        id = rule.id,
        name = rule.name,
        summary = summary,
        actionLabel = if (waveformName.isBlank()) "未配置波形" else "蓝牙波形：$waveformName",
        enabled = rule.enabled,
        canEditGiftPriceRange = rule.eventType == LiveEventType.GIFT,
        minPriceText = rule.conditions.minPrice?.toString().orEmpty(),
        maxPriceText = rule.conditions.maxPrice?.toString().orEmpty(),
        selectedWaveformId = waveformId,
        waveformOptions = waveformOptions,
        imSlotLabel = imSlotLabel,
    )
}

private fun String.toFixedSlotLabel(): String =
    buildString {
        append("固定槽位 ")
        append(padStart(2, '0'))
    }
