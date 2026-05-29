package com.yokonex.bililive.domain.usecase

import com.yokonex.bililive.data.bluetooth.BluetoothRepository
import com.yokonex.bililive.data.websocket.CommandSocketClient
import com.yokonex.bililive.domain.model.LiveEvent
import com.yokonex.bililive.domain.model.LiveEventType
import com.yokonex.bililive.domain.model.OutputAction
import com.yokonex.bililive.domain.model.GiftTriggerMode
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

        if (isOnCooldown(matchedRule, event.timestamp)) {
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

        val action = RuleMatcher.resolveAction(matchedRule, outputMode)
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
                rememberTrigger(matchedRule, event.timestamp)
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
        val likePayload = event.payload as? com.yokonex.bililive.domain.model.EventPayload.LikePayload
        return rules.firstOrNull { rule ->
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
        eventTimestamp: Long,
    ): Boolean {
        if (rule.cooldownSeconds <= 0) {
            return false
        }
        val lastTriggered = lastTriggeredAt[rule.id] ?: return false
        return eventTimestamp < lastTriggered + rule.cooldownSeconds
    }

    private fun rememberTrigger(
        rule: TriggerRule,
        eventTimestamp: Long,
    ) {
        if (rule.cooldownSeconds > 0) {
            lastTriggeredAt[rule.id] = eventTimestamp
        }
    }

    private fun buildEventSummary(event: LiveEvent): String =
        when (val payload = event.payload) {
            is com.yokonex.bililive.domain.model.EventPayload.GiftPayload ->
                "${event.userName} 送出 ${payload.giftName} x${payload.giftNum}"

            is com.yokonex.bililive.domain.model.EventPayload.LikePayload ->
                "${event.userName} 点赞 ${payload.likeCount}"

            is com.yokonex.bililive.domain.model.EventPayload.DanmakuPayload ->
                "${event.userName} 发送弹幕 ${payload.message}"

            is com.yokonex.bililive.domain.model.EventPayload.SystemPayload ->
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
        val giftPayload = event.payload as? com.yokonex.bililive.domain.model.EventPayload.GiftPayload
            ?: return 1
        return when (giftTriggerModeProvider.getCurrentMode()) {
            GiftTriggerMode.SINGLE -> 1
            GiftTriggerMode.BY_QUANTITY -> giftPayload.giftNum.coerceAtLeast(1)
        }
    }

    private fun shouldTriggerLikeRule(
        rule: TriggerRule,
        roomId: String,
        payload: com.yokonex.bililive.domain.model.EventPayload.LikePayload,
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
