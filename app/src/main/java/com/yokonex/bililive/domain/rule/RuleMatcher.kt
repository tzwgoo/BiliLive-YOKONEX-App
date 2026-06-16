package com.yokonex.bililive.domain.rule

import com.yokonex.bililive.domain.model.EventPayload
import com.yokonex.bililive.domain.model.KeywordMatchMode
import com.yokonex.bililive.domain.model.LiveEvent
import com.yokonex.bililive.domain.model.LiveEventType
import com.yokonex.bililive.domain.model.OutputAction
import com.yokonex.bililive.domain.model.OutputMode
import com.yokonex.bililive.domain.model.TriggerRule

object RuleMatcher {

    fun matches(rule: TriggerRule, event: LiveEvent): Boolean {
        // 规则层允许“基础类型”兜住同族细分事件，保证旧规则在引入 SC / 舰队事件后仍然可用。
        if (!rule.enabled || !matchesEventType(rule.eventType, event.type)) {
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

    private fun matchesEventType(
        ruleType: LiveEventType,
        eventType: LiveEventType,
    ): Boolean {
        if (ruleType == eventType) {
            return true
        }
        return when (ruleType) {
            LiveEventType.GIFT -> eventType.isGiftFamily
            LiveEventType.DANMAKU -> eventType.isDanmakuFamily
            LiveEventType.LIKE -> eventType.isLikeFamily
            else -> false
        }
    }

    private fun matchGift(
        rule: TriggerRule,
        payload: EventPayload.GiftPayload,
    ): Boolean {
        val price = payload.price
        val minOk = rule.conditions.minPrice?.let { price >= it } ?: true
        val maxOk = rule.conditions.maxPrice?.let { price <= it } ?: true
        val guardOk = meetsMinGuardLevel(
            guardLevel = payload.guardLevel,
            minGuardLevel = rule.conditions.minGuardLevel,
        )
        return minOk && maxOk && guardOk
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
        if (!meetsMinGuardLevel(
                guardLevel = payload.guardLevel,
                minGuardLevel = rule.conditions.minGuardLevel,
            )
        ) {
            return false
        }

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

    // 舰队等级在原桌面版里使用 1=总督、2=提督、3=舰长，数值越小等级越高。
    private fun meetsMinGuardLevel(
        guardLevel: Int,
        minGuardLevel: Int,
    ): Boolean {
        val normalizedMinGuardLevel = minGuardLevel.coerceIn(0, 3)
        val normalizedGuardLevel = guardLevel.coerceAtLeast(0)
        if (normalizedMinGuardLevel <= 0) {
            return true
        }
        if (normalizedGuardLevel <= 0) {
            return false
        }
        return normalizedGuardLevel <= normalizedMinGuardLevel
    }
}
