package com.yokonex.bililive.domain.usecase

import com.yokonex.bililive.data.bluetooth.BluetoothRepository
import com.yokonex.bililive.data.websocket.CommandSocketClient
import com.yokonex.bililive.domain.model.LiveEvent
import com.yokonex.bililive.domain.model.LiveEventType
import com.yokonex.bililive.domain.model.OutputAction
import com.yokonex.bililive.domain.model.OutputMode
import com.yokonex.bililive.domain.model.TriggerRule
import com.yokonex.bililive.domain.rule.RuleMatcher

class ProcessLiveEventUseCase(
    private val ruleRepository: RuleRepository,
    private val outputModeProvider: OutputModeProvider,
    private val bluetoothRepository: BluetoothRepository,
    private val commandSocketClient: CommandSocketClient,
    private val eventLogRepository: EventLogRepository,
) {
    suspend operator fun invoke(event: LiveEvent) {
        val outputMode = outputModeProvider.getCurrentMode()
        val matchedRule = ruleRepository
            .getEnabledRules()
            .firstOrNull { rule -> RuleMatcher.matches(rule, event) }

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

        runCatching { executeAction(action) }
            .onSuccess {
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

    private suspend fun executeAction(action: OutputAction) {
        when (action) {
            is OutputAction.BluetoothWaveformAction -> {
                bluetoothRepository.playWaveform(action.waveformId)
            }

            is OutputAction.WebSocketCommandAction -> {
                commandSocketClient.sendCommand(action.commandSlot)
            }
        }
    }
}

interface RuleRepository {
    suspend fun getEnabledRules(): List<TriggerRule>
}

interface OutputModeProvider {
    suspend fun getCurrentMode(): OutputMode
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
