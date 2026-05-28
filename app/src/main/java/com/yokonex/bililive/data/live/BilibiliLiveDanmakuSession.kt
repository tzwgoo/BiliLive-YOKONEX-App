package com.yokonex.bililive.data.live

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BilibiliLiveDanmakuSession(
    private val sessionInfo: LiveRoomSessionInfo,
    private val socketConnectionFactory: (String) -> BinarySocketConnection = { url ->
        OkHttpBinarySocketConnection(url)
    },
) {
    fun packets(): Flow<DecodedPacket> = channelFlow {
        val host = sessionInfo.websocketHosts.firstOrNull().orEmpty().ifBlank { DEFAULT_WS_HOST }
        val connection = socketConnectionFactory("wss://$host/sub")
        var heartbeatJob: kotlinx.coroutines.Job? = null

        try {
            connection.send(
                BilibiliDanmakuProtocol.encodePacket(
                    operation = BilibiliDanmakuProtocol.OP_AUTH,
                    body = buildAuthBody(sessionInfo).encodeToByteArray(),
                ),
            )

            while (true) {
                val rawPacket = connection.receive()
                val decodedPackets = BilibiliDanmakuProtocol.decodePackets(rawPacket)
                for (packet in decodedPackets) {
                    when (packet.operation) {
                        BilibiliDanmakuProtocol.OP_AUTH_REPLY -> {
                            if (isVerificationSuccessful(packet.body)) {
                                if (heartbeatJob == null) {
                                    heartbeatJob = launch {
                                        while (true) {
                                            delay(30_000)
                                            connection.send(
                                                BilibiliDanmakuProtocol.encodePacket(
                                                    operation = BilibiliDanmakuProtocol.OP_HEARTBEAT,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        BilibiliDanmakuProtocol.OP_SEND_SMS_REPLY -> send(packet)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            heartbeatJob?.cancel()
            connection.close()
        }
    }

    private fun buildAuthBody(sessionInfo: LiveRoomSessionInfo): String = """
        {"uid":0,"roomid":${sessionInfo.realRoomId},"protover":2,"platform":"web","type":2,"key":"${sessionInfo.token}"}
    """.trimIndent()

    private fun isVerificationSuccessful(body: ByteArray): Boolean {
        val code = runCatching {
            Json.parseToJsonElement(body.decodeToString())
                .jsonObject["code"]
                ?.jsonPrimitive
                ?.content
                ?.toIntOrNull()
        }.getOrNull()
        return code == 0
    }

    private companion object {
        private const val DEFAULT_WS_HOST = "broadcastlv.chat.bilibili.com"
    }
}
