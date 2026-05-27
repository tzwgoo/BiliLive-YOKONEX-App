package com.yokonex.bililive.data.mapper

import com.yokonex.bililive.data.storage.entity.WaveformEntity
import com.yokonex.bililive.domain.model.WaveformDefinition
import com.yokonex.bililive.domain.model.WaveformExecutionMode
import com.yokonex.bililive.domain.model.WaveformSignalMode
import com.yokonex.bililive.domain.model.WaveformStep

object WaveformMapper {
    fun toEntity(definition: WaveformDefinition): WaveformEntity =
        WaveformEntity(
            id = definition.id,
            name = definition.name,
            builtin = definition.builtin,
            payloadJson = buildString {
                append("mode=")
                append(definition.executionMode.name)
                append(";signal=")
                append(definition.signalMode.name)
                append(";loops=")
                append(definition.loopCount)
                append(";steps=")
                append(
                    definition.steps.joinToString("|") { step ->
                        listOf(
                            step.durationMs,
                            step.channelA,
                            step.channelAMode,
                            step.channelAFrequency,
                            step.channelAPulseWidth,
                            step.channelB,
                            step.channelBMode,
                            step.channelBFrequency,
                            step.channelBPulseWidth,
                        ).joinToString(",")
                    },
                )
            },
        )

    fun fromEntity(entity: WaveformEntity): WaveformDefinition {
        val parts = entity.payloadJson
            .split(";")
            .mapNotNull { entry ->
                val key = entry.substringBefore("=", "")
                if (key.isBlank()) {
                    null
                } else {
                    key to entry.substringAfter("=", "")
                }
            }
            .toMap()
        val steps = parts["steps"]
            ?.split("|")
            ?.mapNotNull { encodedStep ->
                val values = encodedStep.split(",")
                if (values.size < 7) {
                    return@mapNotNull null
                }
                if (values.size >= 9) {
                    WaveformStep(
                        durationMs = values[0].toIntOrNull() ?: 200,
                        channelA = values[1].toIntOrNull() ?: 0,
                        channelAMode = values[2].toIntOrNull() ?: 0x01,
                        channelAFrequency = values[3].toIntOrNull() ?: 10,
                        channelAPulseWidth = values[4].toIntOrNull() ?: 5,
                        channelB = values[5].toIntOrNull() ?: 0,
                        channelBMode = values[6].toIntOrNull() ?: 0x01,
                        channelBFrequency = values[7].toIntOrNull() ?: 10,
                        channelBPulseWidth = values[8].toIntOrNull() ?: 5,
                    )
                } else {
                    WaveformStep(
                        durationMs = values[0].toIntOrNull() ?: 200,
                        channelA = values[1].toIntOrNull() ?: 0,
                        channelB = values[2].toIntOrNull() ?: 0,
                        channelAMode = values[3].toIntOrNull() ?: 0x01,
                        channelAFrequency = values[4].toIntOrNull() ?: 10,
                        channelBMode = values[5].toIntOrNull() ?: 0x01,
                        channelBFrequency = values[6].toIntOrNull() ?: 10,
                    )
                }
            }
            .orEmpty()

        return WaveformDefinition(
            id = entity.id,
            name = entity.name,
            builtin = entity.builtin,
            steps = if (steps.isEmpty()) {
                listOf(
                    WaveformStep(
                        durationMs = 200,
                        channelA = 40,
                        channelB = 40,
                    ),
                )
            } else {
                steps
            },
            executionMode = parts["mode"]
                ?.let { runCatching { WaveformExecutionMode.valueOf(it) }.getOrNull() }
                ?: WaveformExecutionMode.ONCE,
            loopCount = parts["loops"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            signalMode = parts["signal"]
                ?.let { runCatching { WaveformSignalMode.valueOf(it) }.getOrNull() }
                ?: WaveformSignalMode.FIXED,
        )
    }
}
