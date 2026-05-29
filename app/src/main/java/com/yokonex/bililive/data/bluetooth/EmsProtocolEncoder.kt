package com.yokonex.bililive.data.bluetooth

import com.yokonex.bililive.domain.model.WaveformDefinition
import com.yokonex.bililive.domain.model.WaveformSignalMode
import com.yokonex.bililive.domain.model.WaveformStep

class EmsProtocolEncoder {
    fun createWaveformPackets(
        waveform: WaveformDefinition,
        protocol: String,
    ): List<ByteArray> =
        waveform.steps.map { step ->
            createStepPacket(
                step = step,
                protocol = protocol,
                signalMode = waveform.signalMode,
            )
        }

    fun createStepPacket(
        step: WaveformStep,
        protocol: String,
        signalMode: WaveformSignalMode,
    ): ByteArray =
        when {
            protocol == "ems_v1" -> createV1MixedPacket(step)
            signalMode == WaveformSignalMode.REALTIME -> createV2RealtimePacket(step)
            else -> createV2FixedPacket(step)
        }

    fun createStopPacket(protocol: String): ByteArray =
        when (protocol) {
            "ems_v1" -> createPacket(
                0x35,
                0x11,
                0x03,
                0x00,
                0x00,
                0x01,
                0x01,
                0x00,
                0x00,
            )

            else -> createV2FixedPacket(
                WaveformStep(
                    durationMs = 0,
                    channelA = 0,
                    channelB = 0,
                ),
            )
        }

    fun createBatteryQueryPacket(): ByteArray =
        createPacket(0x35, 0x71, 0x04)

    private fun createV1MixedPacket(step: WaveformStep): ByteArray {
        val channel = resolveV1Channel(step)
        val enabled = if (channel == 0x00) 0x00 else 0x01
        val useChannelB = when (channel) {
            0x02 -> true
            0x03 -> step.channelB > step.channelA
            else -> false
        }
        val strength = if (useChannelB) step.channelB else step.channelA
        val mode = if (useChannelB) step.channelBMode else step.channelAMode
        val frequency = if (useChannelB) step.channelBFrequency else step.channelAFrequency
        val pulseWidth = if (useChannelB) step.channelBPulseWidth else step.channelAPulseWidth
        return createPacket(
            0x35,
            0x11,
            channel,
            enabled,
            high(strength),
            low(strength),
            mode,
            if (mode == 0x11) frequency else 0x00,
            if (mode == 0x11) pulseWidth else 0x00,
        )
    }

    private fun createV2FixedPacket(step: WaveformStep): ByteArray =
        createPacket(
            0x35,
            0x11,
            0x01,
            high(step.channelA),
            low(step.channelA),
            step.channelAMode,
            high(step.channelB),
            low(step.channelB),
            step.channelBMode,
        )

    private fun createV2RealtimePacket(step: WaveformStep): ByteArray =
        createPacket(
            0x35,
            0x11,
            0x02,
            high(step.channelA),
            low(step.channelA),
            step.channelAFrequency,
            step.channelAPulseWidth,
            high(step.channelB),
            low(step.channelB),
            step.channelBFrequency,
            step.channelBPulseWidth,
        )

    private fun createPacket(vararg values: Int): ByteArray {
        val normalized = values.map(::clampToByte)
        val checksum = normalized.fold(0) { total, item ->
            (total + item) and 0xFF
        }
        return (normalized + checksum).map(Int::toByte).toByteArray()
    }

    private fun resolveV1Channel(step: WaveformStep): Int {
        val aEnabled = step.channelA > 0
        val bEnabled = step.channelB > 0
        return when {
            aEnabled && bEnabled -> 0x03
            aEnabled -> 0x01
            bEnabled -> 0x02
            else -> 0x00
        }
    }

    private fun high(value: Int): Int =
        (value.coerceIn(0, 0xFFFF) shr 8) and 0xFF

    private fun low(value: Int): Int =
        value.coerceIn(0, 0xFFFF) and 0xFF

    private fun clampToByte(value: Int): Int =
        value.coerceIn(0, 255)
}
