package com.yokonex.bililive.data.storage

import com.yokonex.bililive.domain.model.WaveformDefinition
import com.yokonex.bililive.domain.model.WaveformExecutionMode
import com.yokonex.bililive.domain.model.WaveformStep

object DefaultWaveforms {
    val all: List<WaveformDefinition> = listOf(
        WaveformDefinition(
            id = "soft_pulse",
            name = "柔和脉冲",
            builtin = true,
            steps = listOf(
                WaveformStep(
                    durationMs = 600,
                    channelA = 35,
                    channelB = 35,
                ),
            ),
            executionMode = WaveformExecutionMode.ONCE,
            loopCount = 1,
        ),
        WaveformDefinition(
            id = "dual_tap",
            name = "双通道点按",
            builtin = true,
            steps = listOf(
                WaveformStep(
                    durationMs = 400,
                    channelA = 45,
                    channelB = 25,
                ),
                WaveformStep(
                    durationMs = 400,
                    channelA = 25,
                    channelB = 45,
                ),
            ),
            executionMode = WaveformExecutionMode.ONCE,
            loopCount = 1,
        ),
    )
}

