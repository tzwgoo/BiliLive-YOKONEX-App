package com.yokonex.bililive.data.bluetooth

import com.yokonex.bililive.domain.model.WaveformDefinition

class EmsProtocolEncoder {
    fun createWaveformPackets(
        waveform: WaveformDefinition,
        protocol: String,
    ): List<ByteArray> {
        return waveform.steps.map { step ->
            when (protocol) {
                "ems_v2" -> byteArrayOf(
                    0xBB.toByte(),
                    0x66.toByte(),
                    clampToByte(step.durationMs and 0xFF),
                    clampToByte(step.durationMs shr 8),
                    clampToByte(step.channelA),
                    clampToByte(step.channelB),
                    clampToByte(step.channelAMode),
                    clampToByte(step.channelAFrequency),
                    clampToByte(step.channelBMode),
                    clampToByte(step.channelBFrequency),
                )

                else -> byteArrayOf(
                    0xAA.toByte(),
                    0x55.toByte(),
                    clampToByte(step.durationMs / 10),
                    clampToByte(step.channelA),
                    clampToByte(step.channelB),
                    clampToByte(step.channelAMode),
                    clampToByte(step.channelAFrequency),
                    clampToByte(step.channelBMode),
                    clampToByte(step.channelBFrequency),
                )
            }
        }
    }

    fun createStopPacket(protocol: String): ByteArray =
        when (protocol) {
            "ems_v2" -> byteArrayOf(0xBB.toByte(), 0x66.toByte(), 0x00, 0x00)
            else -> byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x00, 0x00)
        }

    private fun clampToByte(value: Int): Byte =
        value.coerceIn(0, 255).toByte()
}
