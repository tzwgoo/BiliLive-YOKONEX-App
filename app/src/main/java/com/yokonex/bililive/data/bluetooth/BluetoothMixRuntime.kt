package com.yokonex.bililive.data.bluetooth

import com.yokonex.bililive.data.bluetooth.model.ActiveWaveformLayer
import com.yokonex.bililive.data.bluetooth.model.MixFrame
import com.yokonex.bililive.domain.model.LiveEventType
import com.yokonex.bililive.domain.model.WaveformSignalMode
import com.yokonex.bililive.domain.model.WaveformStep
import kotlin.math.roundToInt

class BluetoothMixRuntime(
    private val bleManager: AndroidBleManager,
    private val protocolEncoder: EmsProtocolEncoder,
    private val protocol: String,
    private val signalMode: WaveformSignalMode = WaveformSignalMode.FIXED,
) {
    private var activeLayers: List<ActiveWaveformLayer> = emptyList()
    private var hasSentStop: Boolean = false

    fun enqueueLayer(layer: ActiveWaveformLayer) {
        val existing = activeLayers.toMutableList()
        if (existing.size >= MixPolicy.MAX_ACTIVE_LAYERS) {
            val candidate = existing.minWithOrNull(
                compareBy<ActiveWaveformLayer>({ it.priority }, { -it.startedAtElapsedMs }),
            )
            if (candidate != null) {
                if (candidate.priority >= layer.priority) {
                    return
                }
                existing.remove(candidate)
            }
        }
        existing += layer
        activeLayers = existing
    }

    fun nextFrame(nowElapsedMs: Long): MixFrame? {
        val alive = activeLayers.mapNotNull { layer ->
            layer.currentStepAt(nowElapsedMs)?.let { step -> layer to step }
        }
        if (alive.isEmpty()) {
            return null
        }

        val leader = alive.maxWithOrNull(
            compareBy<Pair<ActiveWaveformLayer, WaveformStep>>(
                { it.first.priority },
                { -it.first.startedAtElapsedMs },
            ),
        ) ?: return null

        val cap = if (leader.first.eventType.isGiftFamily) {
            MixPolicy.GIFT_LEADER_CAP
        } else {
            MixPolicy.NORMAL_CAP
        }
        val leaderId = leader.first.id
        val mixedA = alive.sumOf { (layer, step) ->
            step.channelA * if (layer.id == leaderId) 1.0 else layer.weight
        }
        val mixedB = alive.sumOf { (layer, step) ->
            step.channelB * if (layer.id == leaderId) 1.0 else layer.weight
        }

        return MixFrame(
            channelA = mixedA.roundToInt().coerceIn(0, cap),
            channelB = mixedB.roundToInt().coerceIn(0, cap),
            channelAMode = leader.second.channelAMode,
            channelAFrequency = leader.second.channelAFrequency,
            channelAPulseWidth = leader.second.channelAPulseWidth,
            channelBMode = leader.second.channelBMode,
            channelBFrequency = leader.second.channelBFrequency,
            channelBPulseWidth = leader.second.channelBPulseWidth,
            cap = cap,
            leaderEventType = leader.first.eventType,
        )
    }

    suspend fun tick(nowElapsedMs: Long): MixFrame? {
        activeLayers = activeLayers.filter { layer ->
            layer.currentStepAt(nowElapsedMs) != null
        }
        val frame = nextFrame(nowElapsedMs)
        if (frame == null) {
            if (!hasSentStop) {
                bleManager.write(protocolEncoder.createStopPacket(protocol))
                hasSentStop = true
            }
            return null
        }

        hasSentStop = false
        bleManager.write(
            protocolEncoder.createStepPacket(
                step = frame.toWaveformStep(),
                protocol = protocol,
                signalMode = signalMode,
            ),
        )
        return frame
    }

    fun hasActiveLayers(): Boolean = activeLayers.isNotEmpty()

    fun activeLayerCount(): Int = activeLayers.size

    fun clearLayers() {
        activeLayers = emptyList()
        hasSentStop = false
    }

    private fun MixFrame.toWaveformStep(): WaveformStep =
        WaveformStep(
            durationMs = MixPolicy.DEFAULT_TICK_MS.toInt(),
            channelA = channelA,
            channelAMode = channelAMode,
            channelAFrequency = channelAFrequency,
            channelAPulseWidth = channelAPulseWidth,
            channelB = channelB,
            channelBMode = channelBMode,
            channelBFrequency = channelBFrequency,
            channelBPulseWidth = channelBPulseWidth,
        )
}
