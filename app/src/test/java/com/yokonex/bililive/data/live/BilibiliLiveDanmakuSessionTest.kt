package com.yokonex.bililive.data.live

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BilibiliLiveDanmakuSessionTest {

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun packets_afterAuthSuccess_startsWebHeartbeatLoop() = runTest {
        val socketConnection = RecordingBinarySocketConnection(
            incomingPackets = listOf(
                BilibiliDanmakuProtocol.encodePacket(
                    operation = BilibiliDanmakuProtocol.OP_AUTH_REPLY,
                    body = """{"code":0}""".encodeToByteArray(),
                ),
            ),
        )
        val webHeartbeatCalls = mutableListOf<String>()
        val session = BilibiliLiveDanmakuSession(
            sessionInfo = LiveRoomSessionInfo(
                displayRoomId = "7777",
                realRoomId = "22608112",
                ownerUid = 1001L,
                token = "token-demo",
                websocketHosts = listOf("broadcastlv.chat.bilibili.com"),
            ),
            socketConnectionFactory = { _ -> socketConnection },
            heartbeatIntervalMillis = 30_000,
            webHeartbeatIntervalMillis = 60_000,
            webHeartbeatSender = { roomId ->
                webHeartbeatCalls.add(roomId)
            },
        )

        val collectJob = async(start = CoroutineStart.UNDISPATCHED) {
            session.packets().collect { }
        }

        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()

        assertTrue(webHeartbeatCalls.isNotEmpty())
        assertEquals("22608112", webHeartbeatCalls.first())
        assertTrue(socketConnection.sentPackets.isNotEmpty())

        collectJob.cancelAndJoin()
    }
}

private class RecordingBinarySocketConnection(
    incomingPackets: List<ByteArray>,
) : BinarySocketConnection {
    private val packets = ArrayDeque(incomingPackets)
    val sentPackets = mutableListOf<ByteArray>()

    override suspend fun send(packet: ByteArray) {
        sentPackets += packet
    }

    override suspend fun receive(): ByteArray =
        packets.removeFirstOrNull()
            ?: awaitCancellation()

    override suspend fun close() = Unit
}
