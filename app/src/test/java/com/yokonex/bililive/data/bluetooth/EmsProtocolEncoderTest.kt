package com.yokonex.bililive.data.bluetooth

import com.yokonex.bililive.domain.model.WaveformDefinition
import com.yokonex.bililive.domain.model.WaveformStep
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EmsProtocolEncoderTest {

    private val encoder = EmsProtocolEncoder()

    @Test
    fun encoder_buildsStopPacketForV1() {
        val packet = encoder.createStopPacket(protocol = "ems_v1")

        assertArrayEquals(
            byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x00, 0x00),
            packet,
        )
    }

    @Test
    fun encoder_buildsWaveformPacketsForV2() {
        val waveform = WaveformDefinition(
            id = "soft-pulse",
            name = "柔和脉冲",
            builtin = true,
            steps = listOf(
                WaveformStep(
                    durationMs = 600,
                    channelA = 35,
                    channelB = 40,
                    channelAMode = 0x01,
                    channelAFrequency = 10,
                    channelBMode = 0x02,
                    channelBFrequency = 12,
                ),
            ),
        )

        val packets = encoder.createWaveformPackets(
            waveform = waveform,
            protocol = "ems_v2",
        )

        assertEquals(1, packets.size)
        assertArrayEquals(
            byteArrayOf(
                0xBB.toByte(),
                0x66.toByte(),
                0x58,
                0x02,
                0x23,
                0x28,
                0x01,
                0x0A,
                0x02,
                0x0C,
            ),
            packets.first(),
        )
    }
}
