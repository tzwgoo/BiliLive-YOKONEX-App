package com.yokonex.bililive.domain.model

data class TriggerRule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val eventType: LiveEventType,
    val cooldownSeconds: Int = 0,
    val cooldownScope: CooldownScope = CooldownScope.GLOBAL,
    val conditions: RuleConditions = RuleConditions(),
    val actionBindings: ActionBindings = ActionBindings(),
)

data class RuleConditions(
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
    val likeMultiple: Int? = null,
    val minGuardLevel: Int = 0,
    val userLimitWindowSeconds: Int = 0,
    val userLimitMaxTriggers: Int = 0,
    val keywords: List<String> = emptyList(),
    val matchMode: KeywordMatchMode = KeywordMatchMode.ANY,
)

enum class CooldownScope {
    GLOBAL,
    PER_USER,
}

enum class KeywordMatchMode {
    ANY,
    ALL,
}

data class ActionBindings(
    val bluetoothAction: OutputAction.BluetoothWaveformAction? = null,
    val websocketAction: OutputAction.WebSocketCommandAction? = null,
    val guardWaveformIds: Map<Int, String> = emptyMap(),
)

