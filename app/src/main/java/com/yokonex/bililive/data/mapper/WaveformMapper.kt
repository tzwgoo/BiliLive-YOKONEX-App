package com.yokonex.bililive.data.mapper

import com.yokonex.bililive.data.storage.entity.WaveformEntity
import com.yokonex.bililive.domain.model.WaveformDefinition

object WaveformMapper {
    fun toEntity(definition: WaveformDefinition): WaveformEntity =
        WaveformEntity(
            id = definition.id,
            name = definition.name,
            builtin = definition.builtin,
            payloadJson = buildString {
                append("mode=")
                append(definition.executionMode.name)
                append(";loops=")
                append(definition.loopCount)
                append(";steps=")
                append(
                    definition.steps.joinToString("|") { step ->
                        listOf(
                            step.durationMs,
                            step.channelA,
                            step.channelB,
                            step.channelAMode,
                            step.channelAFrequency,
                            step.channelBMode,
                            step.channelBFrequency,
                        ).joinToString(",")
                    },
                )
            },
        )
}
