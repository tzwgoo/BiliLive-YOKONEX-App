package com.yokonex.bililive.data.bluetooth

import com.yokonex.bililive.domain.model.WaveformDefinition
import com.yokonex.bililive.domain.model.WaveformSignalMode
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
            byteArrayOf(
                0x35,
                0x11,
                0x03,
                0x00,
                0x00,
                0x01,
                0x01,
                0x00,
                0x00,
                0x4B,
            ),
            packet,
        )
    }

    @Test
    fun encoder_buildsWaveformPacketForV1() {
        val waveform = WaveformDefinition(
            id = "classic-v1",
            name = "经典 V1",
            builtin = true,
            steps = listOf(
                WaveformStep(
                    durationMs = 180,
                    channelA = 48,
                    channelB = 24,
                    channelAMode = 0x11,
                    channelAFrequency = 10,
                    channelAPulseWidth = 5,
                    channelBMode = 0x06,
                    channelBFrequency = 12,
                    channelBPulseWidth = 7,
                ),
            ),
        )

        val packets = encoder.createWaveformPackets(
            waveform = waveform,
            protocol = "ems_v1",
        )

        assertEquals(1, packets.size)
        assertArrayEquals(
            byteArrayOf(
                0x35,
                0x11,
                0x03,
                0x01,
                0x00,
                0x30,
                0x11,
                0x0A,
                0x05,
                0x9A.toByte(),
            ),
            packets.first(),
        )
    }

    @Test
    fun encoder_buildsFixedWaveformPacketForV2() {
        val waveform = WaveformDefinition(
            id = "soft-pulse",
            name = "柔和脉冲",
            builtin = true,
            steps = listOf(
                WaveformStep(
                    durationMs = 600,
                    channelA = 35,
                    channelB = 40,
                    channelAMode = 0x06,
                    channelAFrequency = 10,
                    channelAPulseWidth = 5,
                    channelBMode = 0x07,
                    channelBFrequency = 12,
                    channelBPulseWidth = 6,
                ),
            ),
            signalMode = WaveformSignalMode.FIXED,
        )

        val packets = encoder.createWaveformPackets(
            waveform = waveform,
            protocol = "ems_v2",
        )

        assertEquals(1, packets.size)
        assertArrayEquals(
            byteArrayOf(
                0x35,
                0x11,
                0x01,
                0x00,
                0x23,
                0x06,
                0x00,
                0x28,
                0x07,
                0x9F.toByte(),
            ),
            packets.first(),
        )
    }

    @Test
    fun encoder_buildsRealtimeWaveformPacketForV2() {
        val waveform = WaveformDefinition(
            id = "realtime-v2",
            name = "实时模式",
            builtin = false,
            steps = listOf(
                WaveformStep(
                    durationMs = 300,
                    channelA = 45,
                    channelB = 30,
                    channelAMode = 0x06,
                    channelAFrequency = 11,
                    channelAPulseWidth = 4,
                    channelBMode = 0x07,
                    channelBFrequency = 13,
                    channelBPulseWidth = 6,
                ),
            ),
            signalMode = WaveformSignalMode.REALTIME,
        )

        val packets = encoder.createWaveformPackets(
            waveform = waveform,
            protocol = "ems_v2",
        )

        assertEquals(1, packets.size)
        assertArrayEquals(
            byteArrayOf(
                0x35,
                0x11,
                0x02,
                0x00,
                0x2D,
                0x0B,
                0x04,
                0x00,
                0x1E,
                0x0D,
                0x06,
                0xB5.toByte(),
            ),
            packets.first(),
        )
    }

    @Test
    fun encoder_buildsBatteryQueryPacketForV2() {
        val packet = encoder.createBatteryQueryPacket()

        assertArrayEquals(
            byteArrayOf(0x35, 0x71, 0x04, 0xAA.toByte()),
            packet,
        )
    }
}
