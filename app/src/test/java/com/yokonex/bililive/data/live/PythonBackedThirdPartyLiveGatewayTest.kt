package com.yokonex.bililive.data.live

import com.yokonex.bililive.domain.model.EventPayload
import com.yokonex.bililive.domain.model.LiveEventType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class PythonBackedThirdPartyLiveGatewayTest {

    @Test
    fun events_drainsPythonBridgeQueueIntoFlow() = runTest {
        val bridge = FakePythonThirdPartyBridge(
            drainedBatches = ArrayDeque(
                listOf(
                    listOf(
                        """{"source":"third_party_ws","event_type":"danmaku","cmd":"DANMU_MSG","room_id":22608112,"open_id":"","uname":"测试用户","timestamp":1714113037,"payload":{"msg":"Python 弹幕"}}""",
                    ),
                ),
            ),
        )
        val gateway = PythonBackedThirdPartyLiveGateway(
            bridge = bridge,
            parserFactory = { roomId -> ThirdPartyMessageParser(roomId) { 1_714_113_037_000L } },
            pollIntervalMillis = 1L,
        )

        val event = gateway.events("22608112").first()
        val payload = event.payload as EventPayload.DanmakuPayload

        assertEquals(LiveEventType.DANMAKU, event.type)
        assertEquals("22608112", event.roomId)
        assertEquals("测试用户", event.userName)
        assertEquals("Python 弹幕", payload.message)
        assertEquals(listOf("22608112"), bridge.startedRoomIds)
        assertEquals(1, bridge.stopCount)
    }

    @Test
    fun events_throwsWhenPythonBridgeEntersErrorState() = runTest {
        val bridge = FakePythonThirdPartyBridge(
            drainedBatches = ArrayDeque(listOf(emptyList<String>())),
            statuses = ArrayDeque(
                listOf(
                    PythonBridgeStatus(state = PythonBridgeState.RUNNING),
                    PythonBridgeStatus(state = PythonBridgeState.ERROR, lastError = "python runtime failed"),
                ),
            ),
        )
        val gateway = PythonBackedThirdPartyLiveGateway(
            bridge = bridge,
            parserFactory = { roomId -> ThirdPartyMessageParser(roomId) },
            pollIntervalMillis = 1L,
        )

        try {
            gateway.events("22608112").toList()
            fail("Python bridge 进入错误状态时应该把异常抛给上层")
        } catch (error: IllegalStateException) {
            assertEquals("python runtime failed", error.message)
        }
    }
}

private class FakePythonThirdPartyBridge(
    private val drainedBatches: ArrayDeque<List<String>>,
    private val statuses: ArrayDeque<PythonBridgeStatus> = ArrayDeque(listOf(PythonBridgeStatus(state = PythonBridgeState.RUNNING))),
) : PythonThirdPartyBridge {
    val startedRoomIds = mutableListOf<String>()
    var stopCount: Int = 0

    override fun start(roomId: String) {
        startedRoomIds += roomId
    }

    override fun drainEvents(limit: Int): List<String> = drainedBatches.removeFirstOrNull() ?: emptyList()

    override fun getStatus(): PythonBridgeStatus = statuses.removeFirstOrNull() ?: PythonBridgeStatus(state = PythonBridgeState.RUNNING)

    override fun stop() {
        stopCount += 1
    }
}
