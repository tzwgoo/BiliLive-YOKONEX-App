package com.yokonex.bililive.data.bluetooth

import com.yokonex.bililive.data.bluetooth.model.ActiveWaveformLayer
import com.yokonex.bililive.data.bluetooth.model.BluetoothDevice
import com.yokonex.bililive.data.bluetooth.model.BluetoothTelemetry
import com.yokonex.bililive.domain.model.LiveEventType
import com.yokonex.bililive.domain.model.WaveformDefinition
import com.yokonex.bililive.domain.model.WaveformStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothMixRuntimeTest {

    @Test
    fun nextFrame_prefersGiftLeaderAndRaisesCapTo180() = runTest {
        val runtime = createRuntime()

        runtime.enqueueLayer(giftLayer(startedAtElapsedMs = 0L))
        runtime.enqueueLayer(danmakuLayer(startedAtElapsedMs = 0L))

        val frame = runtime.nextFrame(nowElapsedMs = 50L)

        assertEquals(LiveEventType.GIFT, frame?.leaderEventType)
        assertEquals(180, frame?.cap)
    }

    @Test
    fun nextFrame_appliesDanmakuAndLikeWeightsWithNormalCap130() = runTest {
        val runtime = createRuntime()

        runtime.enqueueLayer(danmakuLayer(channelA = 100, channelB = 60))
        runtime.enqueueLayer(likeLayer(channelA = 50, channelB = 50))

        val frame = runtime.nextFrame(nowElapsedMs = 50L)

        assertEquals(130, frame?.cap)
        assertEquals(110, frame?.channelA)
        assertEquals(70, frame?.channelB)
    }

    @Test
    fun tick_sendsAbMappingForV1InsteadOfAlternatingChannels() = runTest {
        val bleManager = FakeMixBleManager()
        val runtime = createRuntime(
            bleManager = bleManager,
            protocol = "ems_v1",
        )

        runtime.enqueueLayer(giftLayer(channelA = 90, channelB = 130))
        runtime.tick(nowElapsedMs = 50L)

        val packet = bleManager.writes.single()
        assertEquals(0x03, packet[2].toInt() and 0xFF)
    }

    @Test
    fun tick_sendsStopOnceWhenAllLayersFinished() = runTest {
        val bleManager = FakeMixBleManager()
        val runtime = createRuntime(bleManager = bleManager)

        runtime.enqueueLayer(shortGiftLayer(repeatCount = 1))
        runtime.tick(nowElapsedMs = 500L)
        runtime.tick(nowElapsedMs = 600L)

        assertEquals(1, bleManager.stopWrites.size)
    }
}

private fun createRuntime(
    bleManager: FakeMixBleManager = FakeMixBleManager(),
    protocol: String = "ems_v2",
): BluetoothMixRuntime {
    bleManager.protocol = protocol
    return BluetoothMixRuntime(
        bleManager = bleManager,
        protocolEncoder = EmsProtocolEncoder(),
        protocol = protocol,
    )
}

private fun giftLayer(
    startedAtElapsedMs: Long = 0L,
    channelA: Int = 120,
    channelB: Int = 80,
    repeatCount: Int = 1,
): ActiveWaveformLayer =
    createLayer(
        id = "gift",
        eventType = LiveEventType.GIFT,
        startedAtElapsedMs = startedAtElapsedMs,
        repeatCount = repeatCount,
        channelA = channelA,
        channelB = channelB,
    )

private fun danmakuLayer(
    startedAtElapsedMs: Long = 0L,
    channelA: Int = 100,
    channelB: Int = 60,
    repeatCount: Int = 1,
): ActiveWaveformLayer =
    createLayer(
        id = "danmaku",
        eventType = LiveEventType.DANMAKU,
        startedAtElapsedMs = startedAtElapsedMs,
        repeatCount = repeatCount,
        channelA = channelA,
        channelB = channelB,
    )

private fun likeLayer(
    startedAtElapsedMs: Long = 0L,
    channelA: Int = 50,
    channelB: Int = 50,
    repeatCount: Int = 1,
): ActiveWaveformLayer =
    createLayer(
        id = "like",
        eventType = LiveEventType.LIKE,
        startedAtElapsedMs = startedAtElapsedMs,
        repeatCount = repeatCount,
        channelA = channelA,
        channelB = channelB,
    )

private fun shortGiftLayer(repeatCount: Int): ActiveWaveformLayer =
    createLayer(
        id = "short-gift",
        eventType = LiveEventType.GIFT,
        startedAtElapsedMs = 0L,
        repeatCount = repeatCount,
        channelA = 120,
        channelB = 90,
        durationMs = 100,
    )

private fun createLayer(
    id: String,
    eventType: LiveEventType,
    startedAtElapsedMs: Long,
    repeatCount: Int,
    channelA: Int,
    channelB: Int,
    durationMs: Int = 100,
): ActiveWaveformLayer =
    ActiveWaveformLayer(
        id = id,
        eventType = eventType,
        waveform = WaveformDefinition(
            id = id,
            name = id,
            builtin = false,
            steps = listOf(
                WaveformStep(
                    durationMs = durationMs,
                    channelA = channelA,
                    channelB = channelB,
                ),
            ),
        ),
        startedAtElapsedMs = startedAtElapsedMs,
        repeatCount = repeatCount,
        priority = MixPolicy.priorityOf(eventType),
        weight = MixPolicy.weightOf(eventType),
    )

private class FakeMixBleManager : AndroidBleManager {
    var protocol: String = "ems_v2"
    val writes = mutableListOf<ByteArray>()
    val stopWrites: List<ByteArray>
        get() = writes.filter { packet ->
            packet.contentEquals(EmsProtocolEncoder().createStopPacket(protocol))
        }

    private val telemetryState = MutableStateFlow(BluetoothTelemetry())
    override val telemetry: StateFlow<BluetoothTelemetry> = telemetryState.asStateFlow()

    override suspend fun scan(): List<BluetoothDevice> = emptyList()

    override suspend fun connect(deviceId: String) = Unit

    override suspend fun disconnect() = Unit

    override suspend fun write(packet: ByteArray) {
        writes += packet
    }
}
