package com.yokonex.bililive.data.mapper

import com.yokonex.bililive.data.storage.entity.RuleEntity
import com.yokonex.bililive.domain.model.ActionBindings
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
            conditionsJson = buildString {
                append("minPrice=")
                append(rule.conditions.minPrice ?: "")
                append(";maxPrice=")
                append(rule.conditions.maxPrice ?: "")
                append(";likeMultiple=")
                append(rule.conditions.likeMultiple ?: "")
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
            conditions = RuleConditions(
                minPrice = conditions["minPrice"]?.toIntOrNull(),
                maxPrice = conditions["maxPrice"]?.toIntOrNull(),
                likeMultiple = conditions["likeMultiple"]?.toIntOrNull(),
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
