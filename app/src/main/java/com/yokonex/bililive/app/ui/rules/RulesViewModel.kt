package com.yokonex.bililive.app.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yokonex.bililive.AppServices
import com.yokonex.bililive.data.mapper.WaveformMapper
import com.yokonex.bililive.data.storage.DefaultWaveforms
import com.yokonex.bililive.data.storage.JsonRuleStore
import com.yokonex.bililive.data.storage.dao.WaveformDao
import com.yokonex.bililive.domain.model.ActionBindings
import com.yokonex.bililive.domain.model.CooldownScope
import com.yokonex.bililive.domain.model.KeywordMatchMode
import com.yokonex.bililive.domain.model.LiveEventType
import com.yokonex.bililive.domain.model.OutputAction
import com.yokonex.bililive.domain.model.RuleConditions
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

    fun updateLikeMultiple(
        ruleId: String,
        value: String,
    ) {
        updateRule(ruleId) { rule ->
            rule.copy(
                conditions = rule.conditions.copy(
                    likeMultiple = value.toIntOrNull()?.coerceAtLeast(1),
                ),
            )
        }
    }

    fun updateKeywords(
        ruleId: String,
        value: String,
    ) {
        updateRule(ruleId) { rule ->
            rule.copy(
                conditions = rule.conditions.copy(
                    keywords = parseKeywords(value),
                ),
            )
        }
    }

    fun updateCooldownSeconds(
        ruleId: String,
        value: String,
    ) {
        updateRule(ruleId) { rule ->
            rule.copy(
                cooldownSeconds = value.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            )
        }
    }

    fun updateCooldownScope(
        ruleId: String,
        scope: CooldownScope,
    ) {
        updateRule(ruleId) { rule ->
            rule.copy(cooldownScope = scope)
        }
    }

    fun updateMinGuardLevel(
        ruleId: String,
        level: Int,
    ) {
        updateRule(ruleId) { rule ->
            rule.copy(
                conditions = rule.conditions.copy(
                    minGuardLevel = level.coerceIn(0, 3),
                ),
            )
        }
    }

    fun updateUserLimitWindowSeconds(
        ruleId: String,
        value: String,
    ) {
        updateRule(ruleId) { rule ->
            rule.copy(
                conditions = rule.conditions.copy(
                    userLimitWindowSeconds = value.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                ),
            )
        }
    }

    fun updateUserLimitMaxTriggers(
        ruleId: String,
        value: String,
    ) {
        updateRule(ruleId) { rule ->
            rule.copy(
                conditions = rule.conditions.copy(
                    userLimitMaxTriggers = value.toIntOrNull()?.coerceAtLeast(0) ?: 0,
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

    fun updateWebsocketSlot(
        ruleId: String,
        commandSlot: String,
    ) {
        updateRule(ruleId) { rule ->
            rule.copy(
                actionBindings = rule.actionBindings.copy(
                    websocketAction = commandSlot.takeIf(String::isNotBlank)
                        ?.let(OutputAction::WebSocketCommandAction),
                ),
            )
        }
    }

    fun updateGuardWaveform(
        ruleId: String,
        guardLevel: Int,
        waveformId: String,
    ) {
        updateRule(ruleId) { rule ->
            val nextOverrides = rule.actionBindings.guardWaveformIds.toMutableMap().apply {
                if (waveformId.isBlank()) {
                    remove(guardLevel)
                } else {
                    put(guardLevel, waveformId)
                }
            }
            rule.copy(
                actionBindings = rule.actionBindings.copy(
                    guardWaveformIds = nextOverrides,
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
        _uiState.update {
            RulesUiState(
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
    val eventType: LiveEventType,
    val summary: String,
    val actionLabel: String,
    val enabled: Boolean,
    val canEditGiftPriceRange: Boolean,
    val canEditLikeMultiple: Boolean,
    val canEditKeywords: Boolean,
    val canEditCooldownSeconds: Boolean,
    val canEditCooldownScope: Boolean,
    val canEditMinGuardLevel: Boolean,
    val canEditUserLimitWindowSeconds: Boolean,
    val canEditUserLimitMaxTriggers: Boolean,
    val canEditGuardWaveforms: Boolean,
    val minPriceText: String,
    val maxPriceText: String,
    val likeMultipleText: String,
    val keywordsText: String,
    val cooldownSecondsText: String,
    val userLimitWindowSecondsText: String,
    val userLimitMaxTriggersText: String,
    val cooldownScope: CooldownScope,
    val minGuardLevel: Int,
    val selectedWaveformId: String,
    val selectedCommandSlot: String,
    val waveformOptions: List<UiWaveformOption>,
    val commandSlotOptions: List<UiCommandSlotOption>,
    val guardWaveforms: List<UiGuardWaveformItem>,
    val imSlotLabel: String,
)

data class UiWaveformOption(
    val id: String,
    val name: String,
)

data class UiCommandSlotOption(
    val id: String,
    val label: String,
)

data class UiGuardWaveformItem(
    val guardLevel: Int,
    val label: String,
    val waveformId: String,
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
        conditions = RuleConditions(
            minPrice = 100,
            maxPrice = 999,
        ),
        actionBindings = ActionBindings(
            bluetoothAction = OutputAction.BluetoothWaveformAction("ems-preset-06"),
            websocketAction = OutputAction.WebSocketCommandAction("command_two"),
            guardWaveformIds = mapOf(
                3 to "ems-preset-04",
                1 to "ems-preset-09",
            ),
        ),
    ),
    TriggerRule(
        id = "rule_super_chat",
        name = "醒目留言规则",
        enabled = false,
        eventType = LiveEventType.SUPER_CHAT,
        conditions = RuleConditions(
            minPrice = 30,
        ),
        actionBindings = ActionBindings(
            bluetoothAction = OutputAction.BluetoothWaveformAction("ems-preset-05"),
            websocketAction = OutputAction.WebSocketCommandAction("command_five"),
        ),
    ),
    TriggerRule(
        id = "rule_like_default",
        name = "点赞默认规则",
        enabled = true,
        eventType = LiveEventType.LIKE,
        conditions = RuleConditions(
            likeMultiple = 100,
        ),
        actionBindings = ActionBindings(
            bluetoothAction = OutputAction.BluetoothWaveformAction("ems-preset-01"),
            websocketAction = OutputAction.WebSocketCommandAction("command_three"),
        ),
    ),
    TriggerRule(
        id = "rule_danmaku_default",
        name = "弹幕默认规则",
        enabled = false,
        eventType = LiveEventType.DANMAKU,
        cooldownSeconds = 5,
        cooldownScope = CooldownScope.PER_USER,
        conditions = RuleConditions(
            userLimitWindowSeconds = 30,
            userLimitMaxTriggers = 2,
            keywords = listOf("开火"),
            matchMode = KeywordMatchMode.ANY,
        ),
        actionBindings = ActionBindings(
            bluetoothAction = OutputAction.BluetoothWaveformAction("ems-preset-03"),
            websocketAction = OutputAction.WebSocketCommandAction("command_three"),
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
    val summary = buildRuleSummary(rule)
    val waveformId = rule.actionBindings.bluetoothAction?.waveformId.orEmpty()
    val waveformName = waveformOptions.firstOrNull { option -> option.id == waveformId }?.name ?: waveformId
    val imSlotLabel = rule.actionBindings.websocketAction?.commandSlot?.toFixedSlotLabel().orEmpty()
    return UiRuleItem(
        id = rule.id,
        name = rule.name,
        eventType = rule.eventType,
        summary = summary,
        actionLabel = if (waveformName.isBlank()) "未配置波形" else "蓝牙波形：$waveformName",
        enabled = rule.enabled,
        canEditGiftPriceRange = rule.eventType.isGiftFamily,
        canEditLikeMultiple = rule.eventType.isLikeFamily,
        canEditKeywords = rule.eventType.isDanmakuFamily,
        canEditCooldownSeconds = rule.eventType != LiveEventType.SYSTEM,
        canEditCooldownScope = rule.eventType.isDanmakuFamily,
        // 最低舰队门槛只对弹幕类事件开放，礼物类事件不再展示这个限制项。
        canEditMinGuardLevel = rule.eventType.isDanmakuFamily,
        canEditUserLimitWindowSeconds = rule.eventType.isDanmakuFamily,
        canEditUserLimitMaxTriggers = rule.eventType.isDanmakuFamily,
        // 舰队专属波形目前只作用在主礼物档位规则上，SC / 上舰 / 续费等独立事件
        // 仍然直接走自身主波形，不再展示这组不会实际生效的覆盖配置。
        canEditGuardWaveforms = rule.eventType.supportsGuardWaveformOverrides(),
        minPriceText = rule.conditions.minPrice?.toString().orEmpty(),
        maxPriceText = rule.conditions.maxPrice?.toString().orEmpty(),
        likeMultipleText = rule.conditions.likeMultiple?.toString().orEmpty(),
        keywordsText = rule.conditions.keywords.joinToString(","),
        cooldownSecondsText = rule.cooldownSeconds.toString(),
        userLimitWindowSecondsText = rule.conditions.userLimitWindowSeconds.toString(),
        userLimitMaxTriggersText = rule.conditions.userLimitMaxTriggers.toString(),
        cooldownScope = rule.cooldownScope,
        minGuardLevel = rule.conditions.minGuardLevel,
        selectedWaveformId = waveformId,
        selectedCommandSlot = rule.actionBindings.websocketAction?.commandSlot.orEmpty(),
        waveformOptions = waveformOptions,
        commandSlotOptions = fixedCommandSlotOptions(),
        guardWaveforms = listOf(0, 3, 2, 1).map { guardLevel ->
            UiGuardWaveformItem(
                guardLevel = guardLevel,
                label = guardLevel.toGuardWaveformLabel(),
                waveformId = rule.actionBindings.guardWaveformIds[guardLevel].orEmpty(),
            )
        },
        imSlotLabel = imSlotLabel,
    )
}

private fun buildRuleSummary(rule: TriggerRule): String =
    buildString {
        append(rule.eventType.displayLabel)
        append("事件")
        when {
            rule.eventType.isGiftFamily -> {
                rule.conditions.minPrice?.let { append("，金额 >= $it") }
                rule.conditions.maxPrice?.let { append("，金额 <= $it") }
            }

            rule.eventType.isLikeFamily -> {
                rule.conditions.likeMultiple?.let { append("，达到 $it 的倍数时触发") }
            }

            rule.eventType.isDanmakuFamily -> {
                if (rule.conditions.keywords.isNotEmpty()) {
                    append("，关键词：")
                    append(rule.conditions.keywords.joinToString("、"))
                }
                if (rule.conditions.userLimitWindowSeconds > 0 && rule.conditions.userLimitMaxTriggers > 0) {
                    append("，用户限流 ${rule.conditions.userLimitWindowSeconds} 秒内最多 ${rule.conditions.userLimitMaxTriggers} 次")
                }
            }
        }
        if (rule.eventType.isDanmakuFamily && rule.conditions.minGuardLevel > 0) {
            append("，最低舰队：")
            append(rule.conditions.minGuardLevel.toGuardLevelLabel())
        }
        if (rule.eventType.supportsGuardWaveformOverrides() && rule.actionBindings.guardWaveformIds.isNotEmpty()) {
            append("，舰队波形覆盖 ${rule.actionBindings.guardWaveformIds.size} 档")
        }
        if (rule.cooldownSeconds > 0) {
            append("，冷却 ${rule.cooldownSeconds} 秒")
            if (rule.cooldownScope == CooldownScope.PER_USER) {
                append("（按用户）")
            }
        }
    }

private fun Int.toGuardLevelLabel(): String =
    when (this) {
        1 -> "总督"
        2 -> "提督"
        3 -> "舰长"
        else -> "不限"
    }

private fun Int.toGuardWaveformLabel(): String =
    when (this) {
        1 -> "总督"
        2 -> "提督"
        3 -> "舰长"
        else -> "普通用户"
    }

private fun LiveEventType.supportsGuardWaveformOverrides(): Boolean =
    this == LiveEventType.GIFT

private fun String.toFixedSlotLabel(): String =
    when (this) {
        "command_one" -> "固定槽位 01"
        "command_two" -> "固定槽位 02"
        "command_three" -> "固定槽位 03"
        "command_four" -> "固定槽位 04"
        "command_five" -> "固定槽位 05"
        "command_six" -> "固定槽位 06"
        "command_seven" -> "固定槽位 07"
        "command_eight" -> "固定槽位 08"
        "command_nine" -> "固定槽位 09"
        "command_ten" -> "固定槽位 10"
        else -> "固定槽位 $this"
    }

private fun fixedCommandSlotOptions(): List<UiCommandSlotOption> =
    listOf(
        "command_one",
        "command_two",
        "command_three",
        "command_four",
        "command_five",
        "command_six",
        "command_seven",
        "command_eight",
        "command_nine",
        "command_ten",
    ).map { slot ->
        UiCommandSlotOption(
            id = slot,
            label = slot.toFixedSlotLabel(),
        )
    }

internal fun parseKeywords(value: String): List<String> =
    value
        .replace("\r", "")
        .replace("\n", ",")
        .split(",", "，")
        .map(String::trim)
        .filter(String::isNotBlank)
