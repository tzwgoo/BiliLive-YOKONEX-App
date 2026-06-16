package com.yokonex.bililive.domain.usecase

import com.yokonex.bililive.data.bluetooth.BluetoothRepository
import com.yokonex.bililive.data.websocket.CommandSocketClient
import com.yokonex.bililive.domain.model.CooldownScope
import com.yokonex.bililive.domain.model.EventPayload
import com.yokonex.bililive.domain.model.LiveEvent
import com.yokonex.bililive.domain.model.OutputAction
import com.yokonex.bililive.domain.model.GiftTriggerMode
import com.yokonex.bililive.domain.model.LiveEventType
import com.yokonex.bililive.domain.model.OutputMode
import com.yokonex.bililive.domain.model.TriggerRule
import com.yokonex.bililive.domain.rule.RuleMatcher

class ProcessLiveEventUseCase(
    private val ruleRepository: RuleRepository,
    private val outputModeProvider: OutputModeProvider,
    private val giftTriggerModeProvider: GiftTriggerModeProvider,
    private val bluetoothRepository: BluetoothRepository,
    private val commandSocketClient: CommandSocketClient,
    private val eventLogRepository: EventLogRepository,
) {
    private val lastTriggeredAt = mutableMapOf<String, Long>()
    private val likeProgress = mutableMapOf<String, Int>()
    private val userTriggerHistory = mutableMapOf<String, MutableList<Long>>()

    suspend operator fun invoke(event: LiveEvent) {
        val outputMode = outputModeProvider.getCurrentMode()
        val matchedRule = findMatchedRule(
            rules = ruleRepository.getEnabledRules(),
            event = event,
        )

        if (matchedRule == null) {
            eventLogRepository.record(
                ProcessedEventRecord(
                    eventId = event.id,
                    eventType = event.type.name,
                    summary = buildEventSummary(event),
                    rawPayloadJson = event.payload.toString(),
                    matchedRuleId = null,
                    outputMode = outputMode,
                    outputSuccess = false,
                    outputMessage = "no_matching_rule",
                    createdAt = event.timestamp,
                ),
            )
            return
        }

        if (isOnCooldown(matchedRule, event)) {
            eventLogRepository.record(
                ProcessedEventRecord(
                    eventId = event.id,
                    eventType = event.type.name,
                    summary = buildEventSummary(event),
                    rawPayloadJson = event.payload.toString(),
                    matchedRuleId = matchedRule.id,
                    outputMode = outputMode,
                    outputSuccess = false,
                    outputMessage = "cooldown_skipped",
                    createdAt = event.timestamp,
                ),
            )
            return
        }

        if (isBlockedByUserLimit(matchedRule, event)) {
            eventLogRepository.record(
                ProcessedEventRecord(
                    eventId = event.id,
                    eventType = event.type.name,
                    summary = buildEventSummary(event),
                    rawPayloadJson = event.payload.toString(),
                    matchedRuleId = matchedRule.id,
                    outputMode = outputMode,
                    outputSuccess = false,
                    outputMessage = "user_limit_skipped",
                    createdAt = event.timestamp,
                ),
            )
            return
        }

        val action = resolveAction(matchedRule, outputMode, event)
        if (action == null) {
            eventLogRepository.record(
                ProcessedEventRecord(
                    eventId = event.id,
                    eventType = event.type.name,
                    summary = buildEventSummary(event),
                    rawPayloadJson = event.payload.toString(),
                    matchedRuleId = matchedRule.id,
                    outputMode = outputMode,
                    outputSuccess = false,
                    outputMessage = "no_action_binding",
                    createdAt = event.timestamp,
                ),
            )
            return
        }

        val repeatCount = resolveRepeatCount(event)
        runCatching { executeAction(action, event.type, repeatCount) }
            .onSuccess {
                rememberTrigger(matchedRule, event)
                rememberUserLimitHit(matchedRule, event)
                eventLogRepository.record(
                    ProcessedEventRecord(
                        eventId = event.id,
                        eventType = event.type.name,
                        summary = buildEventSummary(event),
                        rawPayloadJson = event.payload.toString(),
                        matchedRuleId = matchedRule.id,
                        outputMode = outputMode,
                        outputSuccess = true,
                        outputMessage = "ok",
                        createdAt = event.timestamp,
                    ),
                )
            }
            .onFailure { error ->
                eventLogRepository.record(
                    ProcessedEventRecord(
                        eventId = event.id,
                        eventType = event.type.name,
                        summary = buildEventSummary(event),
                        rawPayloadJson = event.payload.toString(),
                        matchedRuleId = matchedRule.id,
                        outputMode = outputMode,
                        outputSuccess = false,
                        outputMessage = error.message ?: "unknown_error",
                        createdAt = event.timestamp,
                    ),
                )
            }
    }

    private fun findMatchedRule(
        rules: List<TriggerRule>,
        event: LiveEvent,
    ): TriggerRule? {
        val likePayload = event.payload as? EventPayload.LikePayload
        // 细分事件规则需要优先于基础族规则，否则 SC / 上舰会被普通礼物规则提前命中。
        return rules
            .sortedWith(
                compareByDescending<TriggerRule> { rule -> rule.eventType == event.type }
                    .thenByDescending { rule -> rule.eventType.category == event.type.category }
                    .thenBy { rule -> rule.name },
            )
            .firstOrNull { rule ->
            if (likePayload != null && rule.eventType == LiveEventType.LIKE) {
                shouldTriggerLikeRule(
                    rule = rule,
                    roomId = event.roomId,
                    payload = likePayload,
                )
            } else {
                RuleMatcher.matches(rule, event)
            }
        }
    }

    private fun isOnCooldown(
        rule: TriggerRule,
        event: LiveEvent,
    ): Boolean {
        if (rule.cooldownSeconds <= 0) {
            return false
        }
        val cooldownKey = buildCooldownKey(rule, event)
        val lastTriggered = lastTriggeredAt[cooldownKey] ?: return false
        return eventTimeMillis(event) < lastTriggered + rule.cooldownSeconds * 1_000L
    }

    private fun rememberTrigger(
        rule: TriggerRule,
        event: LiveEvent,
    ) {
        if (rule.cooldownSeconds > 0) {
            lastTriggeredAt[buildCooldownKey(rule, event)] = eventTimeMillis(event)
        }
    }

    private fun isBlockedByUserLimit(
        rule: TriggerRule,
        event: LiveEvent,
    ): Boolean {
        if (!event.type.isDanmakuFamily) {
            return false
        }
        val windowSeconds = rule.conditions.userLimitWindowSeconds.coerceAtLeast(0)
        val maxTriggers = rule.conditions.userLimitMaxTriggers.coerceAtLeast(0)
        if (windowSeconds <= 0 || maxTriggers <= 0) {
            return false
        }
        val historyKey = buildUserLimitKey(rule, event)
        val history = userTriggerHistory[historyKey] ?: return false
        val currentTimeMillis = eventTimeMillis(event)
        val windowStartMillis = currentTimeMillis - windowSeconds * 1_000L
        history.removeAll { timestamp -> timestamp < windowStartMillis }
        return history.size >= maxTriggers
    }

    private fun rememberUserLimitHit(
        rule: TriggerRule,
        event: LiveEvent,
    ) {
        if (!event.type.isDanmakuFamily) {
            return
        }
        val windowSeconds = rule.conditions.userLimitWindowSeconds.coerceAtLeast(0)
        val maxTriggers = rule.conditions.userLimitMaxTriggers.coerceAtLeast(0)
        if (windowSeconds <= 0 || maxTriggers <= 0) {
            return
        }
        val historyKey = buildUserLimitKey(rule, event)
        val history = userTriggerHistory.getOrPut(historyKey) { mutableListOf() }
        val currentTimeMillis = eventTimeMillis(event)
        val windowStartMillis = currentTimeMillis - windowSeconds * 1_000L
        history.removeAll { timestamp -> timestamp < windowStartMillis }
        history += currentTimeMillis
    }

    private fun buildEventSummary(event: LiveEvent): String =
        when (val payload = event.payload) {
            is EventPayload.GiftPayload ->
                buildString {
                    append(event.userName)
                    append(' ')
                    append(
                        when (event.type) {
                            LiveEventType.SUPER_CHAT -> "发送"
                            LiveEventType.GUARD_BUY -> "开通"
                            LiveEventType.GUARD_RENEW -> "续费"
                            else -> "送出"
                        },
                    )
                    append(' ')
                    append(payload.giftName)
                    append(" x")
                    append(payload.giftNum)
                    if (payload.message.isNotBlank()) {
                        append("：")
                        append(payload.message)
                    }
                }

            is EventPayload.LikePayload ->
                "${event.userName} 点赞 ${payload.likeCount}"

            is EventPayload.DanmakuPayload ->
                "${event.userName} 发送${event.type.displayLabel} ${payload.message}"

            is EventPayload.SystemPayload ->
                payload.message
        }

    private suspend fun executeAction(
        action: OutputAction,
        eventType: LiveEventType,
        repeatCount: Int,
    ) {
        when (action) {
            is OutputAction.BluetoothWaveformAction -> {
                bluetoothRepository.enqueueWaveform(
                    waveformId = action.waveformId,
                    eventType = eventType,
                    repeatCount = repeatCount,
                )
            }

            is OutputAction.WebSocketCommandAction -> {
                commandSocketClient.sendCommand(
                    commandSlot = action.commandSlot,
                    repeatCount = repeatCount,
                )
            }
        }
    }

    private suspend fun resolveRepeatCount(event: LiveEvent): Int {
        val giftPayload = event.payload as? EventPayload.GiftPayload
            ?: return 1
        return when (giftTriggerModeProvider.getCurrentMode()) {
            GiftTriggerMode.SINGLE -> 1
            GiftTriggerMode.BY_QUANTITY -> giftPayload.giftNum.coerceAtLeast(1)
        }
    }

    private fun shouldTriggerLikeRule(
        rule: TriggerRule,
        roomId: String,
        payload: EventPayload.LikePayload,
    ): Boolean {
        val multiple = rule.conditions.likeMultiple ?: return true
        if (multiple <= 0) {
            return false
        }
        val progressKey = "${rule.id}:$roomId:$multiple"
        var lastObservedLikeCount = likeProgress[progressKey] ?: 0
        val effectiveLikeCount = resolveEffectiveLikeCount(
            reportedLikeCount = payload.likeCount,
            likeDelta = payload.likeDelta,
            lastObservedLikeCount = lastObservedLikeCount,
        )
        if (payload.likeCount > 0 && effectiveLikeCount < lastObservedLikeCount) {
            lastObservedLikeCount = 0
        }
        likeProgress[progressKey] = effectiveLikeCount
        return (effectiveLikeCount / multiple) > (lastObservedLikeCount / multiple)
    }

    private fun resolveEffectiveLikeCount(
        reportedLikeCount: Int,
        likeDelta: Int,
        lastObservedLikeCount: Int,
    ): Int {
        if (reportedLikeCount > 0) {
            return reportedLikeCount
        }
        if (likeDelta > 0) {
            return lastObservedLikeCount + likeDelta
        }
        return lastObservedLikeCount
    }

    private fun buildCooldownKey(
        rule: TriggerRule,
        event: LiveEvent,
    ): String =
        when (rule.cooldownScope) {
            CooldownScope.GLOBAL -> rule.id
            CooldownScope.PER_USER -> "${rule.id}:${event.roomId}:${event.userId.ifBlank { "anonymous" }}"
        }

    private fun buildUserLimitKey(
        rule: TriggerRule,
        event: LiveEvent,
    ): String =
        "${rule.id}:${event.roomId}:${event.userId.ifBlank { event.userName.ifBlank { "anonymous" } }}"

    private fun resolveAction(
        rule: TriggerRule,
        outputMode: OutputMode,
        event: LiveEvent,
    ): OutputAction? {
        val action = RuleMatcher.resolveAction(rule, outputMode) ?: return null
        if (outputMode != OutputMode.BLUETOOTH || action !is OutputAction.BluetoothWaveformAction) {
            return action
        }
        val guardLevel = (event.payload as? EventPayload.GiftPayload)?.guardLevel ?: return action
        val overrideWaveformId = rule.actionBindings.guardWaveformIds[guardLevel]
            ?.takeIf(String::isNotBlank)
            ?: return action
        // 礼物类事件允许按舰队等级覆盖波形，未配置时继续沿用规则主波形。
        return action.copy(waveformId = overrideWaveformId)
    }

    private fun eventTimeMillis(event: LiveEvent): Long {
        val timestamp = event.timestamp
        // 兼容历史测试数据里的“秒级时间”和运行时真实的“毫秒时间”，统一后再做冷却与限流比较。
        return when {
            timestamp >= 1_000_000_000_000L -> timestamp
            timestamp > 0L -> timestamp * 1_000L
            else -> 0L
        }
    }
}

interface RuleRepository {
    suspend fun getEnabledRules(): List<TriggerRule>
}

interface OutputModeProvider {
    suspend fun getCurrentMode(): OutputMode
}

interface GiftTriggerModeProvider {
    suspend fun getCurrentMode(): GiftTriggerMode
}

interface EventLogRepository {
    suspend fun record(record: ProcessedEventRecord)
}

data class ProcessedEventRecord(
    val eventId: String,
    val eventType: String,
    val summary: String,
    val rawPayloadJson: String,
    val matchedRuleId: String?,
    val outputMode: OutputMode,
    val outputSuccess: Boolean,
    val outputMessage: String,
    val createdAt: Long,
)
