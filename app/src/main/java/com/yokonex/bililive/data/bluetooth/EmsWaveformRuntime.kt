package com.yokonex.bililive.data.bluetooth

import com.yokonex.bililive.domain.model.WaveformDefinition
import com.yokonex.bililive.domain.model.WaveformStep
import kotlinx.coroutines.delay

class EmsWaveformRuntime(
    private val bleManager: AndroidBleManager,
    private val protocolEncoder: EmsProtocolEncoder,
) {
    suspend fun play(
        waveform: WaveformDefinition,
        protocol: String,
        onStepStarted: (WaveformStep) -> Unit = { },
        onCompleted: () -> Unit = { },
    ) {
        val packets = protocolEncoder.createWaveformPackets(waveform, protocol)
        for ((index, packet) in packets.withIndex()) {
            val step = waveform.steps[index]
            onStepStarted(step)
            bleManager.write(packet)
            delay(step.durationMs.toLong())
        }
        bleManager.write(protocolEncoder.createStopPacket(protocol))
        onCompleted()
    }
}
