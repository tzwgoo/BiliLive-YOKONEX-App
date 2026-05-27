package com.yokonex.bililive.data.mapper

import com.yokonex.bililive.domain.model.WaveformDefinition
import com.yokonex.bililive.domain.model.WaveformExecutionMode
import com.yokonex.bililive.domain.model.WaveformSignalMode
import com.yokonex.bililive.domain.model.WaveformStep
import org.junit.Assert.assertEquals
import org.junit.Test

class WaveformMapperTest {

    @Test
    fun mapper_roundTripsWaveformPayloadWithSignalAndPulseWidth() {
        val waveform = WaveformDefinition(
            id = "custom-wave",
            name = "自定义波形",
            builtin = false,
            steps = listOf(
                WaveformStep(
                    durationMs = 320,
                    channelA = 42,
                    channelAMode = 0x06,
                    channelAFrequency = 11,
                    channelAPulseWidth = 4,
                    channelB = 28,
                    channelBMode = 0x07,
                    channelBFrequency = 13,
                    channelBPulseWidth = 6,
                ),
            ),
            executionMode = WaveformExecutionMode.LOOP,
            loopCount = 3,
            signalMode = WaveformSignalMode.REALTIME,
        )

        val restored = WaveformMapper.fromEntity(WaveformMapper.toEntity(waveform))

        assertEquals(waveform, restored)
    }
}
