package com.yokonex.bililive.data.mapper

import com.yokonex.bililive.data.storage.entity.RuleEntity
import com.yokonex.bililive.domain.model.ActionBindings
import com.yokonex.bililive.domain.model.CooldownScope
import com.yokonex.bililive.domain.model.KeywordMatchMode
import com.yokonex.bililive.domain.model.LiveEventType
import com.yokonex.bililive.domain.model.OutputAction
import com.yokonex.bililive.domain.model.RuleConditions
import com.yokonex.bililive.domain.model.TriggerRule

object RuleMapper {
    fun toEntity(rule: TriggerRule): RuleEntity =
        RuleEntity(
            id = rule.id,
            name = rule.name,
            enabled = rule.enabled,
            eventType = rule.eventType.name,
            cooldownSeconds = rule.cooldownSeconds,
            cooldownScope = rule.cooldownScope.name,
            conditionsJson = buildString {
                append("minPrice=")
                append(rule.conditions.minPrice ?: "")
                append(";maxPrice=")
                append(rule.conditions.maxPrice ?: "")
                append(";likeMultiple=")
                append(rule.conditions.likeMultiple ?: "")
                append(";minGuardLevel=")
                append(rule.conditions.minGuardLevel)
                append(";userLimitWindowSeconds=")
                append(rule.conditions.userLimitWindowSeconds)
                append(";userLimitMaxTriggers=")
                append(rule.conditions.userLimitMaxTriggers)
                append(";keywords=")
                append(rule.conditions.keywords.joinToString(","))
                append(";matchMode=")
                append(rule.conditions.matchMode.name)
            },
            actionBindingsJson = buildString {
                append("bluetooth=")
                append(rule.actionBindings.bluetoothAction?.waveformId.orEmpty())
                append(";websocket=")
                append(rule.actionBindings.websocketAction?.commandSlot.orEmpty())
                listOf(0, 3, 2, 1).forEach { guardLevel ->
                    append(";guardWaveform")
                    append(guardLevel)
                    append("=")
                    append(rule.actionBindings.guardWaveformIds[guardLevel].orEmpty())
                }
            },
        )

    fun fromEntity(entity: RuleEntity): TriggerRule {
        val conditions = entity.conditionsJson.parseKeyValuePayload()
        val actions = entity.actionBindingsJson.parseKeyValuePayload()
        val keywords = conditions["keywords"]
            ?.split(",")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()
        return TriggerRule(
            id = entity.id,
            name = entity.name,
            enabled = entity.enabled,
            eventType = runCatching { LiveEventType.valueOf(entity.eventType) }.getOrDefault(LiveEventType.SYSTEM),
            cooldownSeconds = entity.cooldownSeconds,
            cooldownScope = entity.cooldownScope
                ?.let { runCatching { CooldownScope.valueOf(it) }.getOrNull() }
                ?: CooldownScope.GLOBAL,
            conditions = RuleConditions(
                minPrice = conditions["minPrice"]?.toIntOrNull(),
                maxPrice = conditions["maxPrice"]?.toIntOrNull(),
                likeMultiple = conditions["likeMultiple"]?.toIntOrNull(),
                minGuardLevel = conditions["minGuardLevel"]?.toIntOrNull()?.coerceIn(0, 3) ?: 0,
                userLimitWindowSeconds = conditions["userLimitWindowSeconds"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                userLimitMaxTriggers = conditions["userLimitMaxTriggers"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                keywords = keywords,
                matchMode = conditions["matchMode"]
                    ?.let { runCatching { KeywordMatchMode.valueOf(it) }.getOrNull() }
                    ?: KeywordMatchMode.ANY,
            ),
            actionBindings = ActionBindings(
                bluetoothAction = actions["bluetooth"]
                    ?.takeIf(String::isNotBlank)
                    ?.let(OutputAction::BluetoothWaveformAction),
                websocketAction = actions["websocket"]
                    ?.takeIf(String::isNotBlank)
                    ?.let(OutputAction::WebSocketCommandAction),
                guardWaveformIds = listOf(0, 3, 2, 1)
                    .mapNotNull { guardLevel ->
                        actions["guardWaveform$guardLevel"]
                            ?.takeIf(String::isNotBlank)
                            ?.let { waveformId -> guardLevel to waveformId }
                    }
                    .toMap(),
            ),
        )
    }
}

private fun String.parseKeyValuePayload(): Map<String, String> =
    split(";")
        .mapNotNull { entry ->
            val key = entry.substringBefore("=", "")
            if (key.isBlank()) {
                null
            } else {
                key to entry.substringAfter("=", "")
            }
        }
        .toMap()
