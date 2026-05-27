package com.yokonex.bililive.data.bluetooth

import com.yokonex.bililive.domain.model.WaveformDefinition
import kotlinx.coroutines.delay

class EmsWaveformRuntime(
    private val bleManager: AndroidBleManager,
    private val protocolEncoder: EmsProtocolEncoder,
) {
    suspend fun play(
        waveform: WaveformDefinition,
        protocol: String,
    ) {
        val packets = protocolEncoder.createWaveformPackets(waveform, protocol)
        for ((index, packet) in packets.withIndex()) {
            bleManager.write(packet)
            val step = waveform.steps[index]
            delay(step.durationMs.toLong())
        }
        bleManager.write(protocolEncoder.createStopPacket(protocol))
    }
}
