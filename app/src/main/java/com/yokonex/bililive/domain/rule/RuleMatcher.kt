package com.yokonex.bililive.domain.rule

import com.yokonex.bililive.domain.model.EventPayload
import com.yokonex.bililive.domain.model.KeywordMatchMode
import com.yokonex.bililive.domain.model.LiveEvent
import com.yokonex.bililive.domain.model.OutputAction
import com.yokonex.bililive.domain.model.OutputMode
import com.yokonex.bililive.domain.model.TriggerRule

object RuleMatcher {

    fun matches(rule: TriggerRule, event: LiveEvent): Boolean {
        if (!rule.enabled || rule.eventType != event.type) {
            return false
        }

        return when (val payload = event.payload) {
            is EventPayload.GiftPayload -> matchGift(rule, payload)
            is EventPayload.LikePayload -> matchLike(rule, payload)
            is EventPayload.DanmakuPayload -> matchDanmaku(rule, payload)
            is EventPayload.SystemPayload -> false
        }
    }

    fun resolveAction(rule: TriggerRule, mode: OutputMode): OutputAction? =
        when (mode) {
            OutputMode.BLUETOOTH -> rule.actionBindings.bluetoothAction
            OutputMode.WEBSOCKET -> rule.actionBindings.websocketAction
        }

    private fun matchGift(
        rule: TriggerRule,
        payload: EventPayload.GiftPayload,
    ): Boolean {
        val price = payload.totalPrice
        val minOk = rule.conditions.minPrice?.let { price >= it } ?: true
        val maxOk = rule.conditions.maxPrice?.let { price <= it } ?: true
        return minOk && maxOk
    }

    private fun matchLike(
        rule: TriggerRule,
        payload: EventPayload.LikePayload,
    ): Boolean {
        val multiple = rule.conditions.likeMultiple ?: return true
        if (multiple <= 0) {
            return false
        }
        return payload.likeCount % multiple == 0
    }

    private fun matchDanmaku(
        rule: TriggerRule,
        payload: EventPayload.DanmakuPayload,
    ): Boolean {
        val keywords = rule.conditions.keywords
        if (keywords.isEmpty()) {
            return true
        }

        val message = payload.message.lowercase()
        return when (rule.conditions.matchMode) {
            KeywordMatchMode.ANY -> keywords.any { keyword ->
                message.contains(keyword.lowercase())
            }

            KeywordMatchMode.ALL -> keywords.all { keyword ->
                message.contains(keyword.lowercase())
            }
        }
    }
}
