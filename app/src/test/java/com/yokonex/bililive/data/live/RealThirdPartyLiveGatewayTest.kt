package com.yokonex.bililive.data.live

import com.yokonex.bililive.domain.model.EventPayload
import com.yokonex.bililive.domain.model.LiveEventType
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealThirdPartyLiveGatewayTest {

    @Test
    fun events_connectsToRoomAndEmitsMappedLiveEvent() = runTest {
        val liveRoomClient = FakeLiveRoomClient(
            LiveRoomSessionInfo(
                displayRoomId = "7777",
                realRoomId = "22608112",
                ownerUid = 1001L,
                token = "token-demo",
                websocketHosts = listOf("broadcastlv.chat.bilibili.com"),
            ),
        )
        val socketConnection = FakeBinarySocketConnection(
            incomingPackets = listOf(
                BilibiliDanmakuProtocol.encodePacket(
                    operation = BilibiliDanmakuProtocol.OP_AUTH_REPLY,
                    body = """{"code":0}""".encodeToByteArray(),
                ),
                BilibiliDanmakuProtocol.encodePacket(
                    operation = BilibiliDanmakuProtocol.OP_SEND_SMS_REPLY,
                    body = """{"cmd":"DANMU_MSG","info":[[0,0,0,0,1714113037],"测试弹幕",["1001","测试用户"]]}""".encodeToByteArray(),
                ),
            ),
        )
        val gateway = RealThirdPartyLiveGateway(
            liveRoomClient = liveRoomClient,
            socketConnectionFactory = { _ -> socketConnection },
        )

        val event = gateway.events("7777").first()
        val payload = event.payload as EventPayload.DanmakuPayload

        assertEquals(LiveEventType.DANMAKU, event.type)
        assertEquals("22608112", event.roomId)
        assertEquals("1001", event.userId)
        assertEquals("测试用户", event.userName)
        assertEquals("测试弹幕", payload.message)
        assertTrue(socketConnection.sentPackets.isNotEmpty())
        val authPacketBody = BilibiliDanmakuProtocol.decodePackets(socketConnection.sentPackets.first()).first().body.decodeToString()
        assertTrue(authPacketBody.contains(""""roomid":22608112"""))
        assertTrue(authPacketBody.contains(""""key":"token-demo""""))
    }
}

private class FakeLiveRoomClient(
    private val sessionInfo: LiveRoomSessionInfo,
) : LiveRoomClient {
    override suspend fun createSessionInfo(roomId: String): LiveRoomSessionInfo {
        assertEquals("7777", roomId)
        return sessionInfo
    }

    override suspend fun getAnchorName(roomId: String): String? = null
}

private class FakeBinarySocketConnection(
    incomingPackets: List<ByteArray>,
) : BinarySocketConnection {
    private val packets = ArrayDeque(incomingPackets)
    val sentPackets = mutableListOf<ByteArray>()

    override suspend fun send(packet: ByteArray) {
        sentPackets += packet
    }

    override suspend fun receive(): ByteArray {
        return packets.removeFirstOrNull() ?: run {
            awaitCancellation()
        }
    }

    override suspend fun close() = Unit
}
