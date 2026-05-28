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
    private val heartbeatIntervalMillis: Long = 30_000L,
    private val heartbeatTimeoutMillis: Long = 60_000L,
    private val webHeartbeatIntervalMillis: Long = 60_000L,
    private val webHeartbeatSender: suspend (String) -> Unit = { },
    private val timestampProvider: () -> Long = System::currentTimeMillis,
) {
    fun packets(): Flow<DecodedPacket> = channelFlow {
        val host = sessionInfo.websocketHosts.firstOrNull().orEmpty().ifBlank { DEFAULT_WS_HOST }
        val connection = socketConnectionFactory("wss://$host/sub")
        var heartbeatJob: kotlinx.coroutines.Job? = null
        var webHeartbeatJob: kotlinx.coroutines.Job? = null
        var lastHeartbeatAckAt = timestampProvider()
        var lastHeartbeatSentAt = 0L

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
                                lastHeartbeatAckAt = timestampProvider()
                                if (heartbeatJob == null) {
                                    heartbeatJob = launch {
                                        while (true) {
                                            val now = timestampProvider()
                                            if (
                                                lastHeartbeatSentAt > 0L &&
                                                now - lastHeartbeatAckAt >= heartbeatTimeoutMillis
                                            ) {
                                                connection.close()
                                                break
                                            }
                                            connection.send(
                                                BilibiliDanmakuProtocol.encodePacket(
                                                    operation = BilibiliDanmakuProtocol.OP_HEARTBEAT,
                                                ),
                                            )
                                            lastHeartbeatSentAt = now
                                            delay(heartbeatIntervalMillis)
                                        }
                                    }
                                }
                                if (webHeartbeatJob == null) {
                                    webHeartbeatJob = launch {
                                        while (true) {
                                            runCatching {
                                                webHeartbeatSender(sessionInfo.realRoomId)
                                            }
                                            delay(webHeartbeatIntervalMillis)
                                        }
                                    }
                                }
                            }
                        }

                        BilibiliDanmakuProtocol.OP_HEARTBEAT_REPLY -> {
                            lastHeartbeatAckAt = timestampProvider()
                        }

                        BilibiliDanmakuProtocol.OP_SEND_SMS_REPLY -> send(packet)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            heartbeatJob?.cancel()
            webHeartbeatJob?.cancel()
            connection.close()
        }
    }

    private fun buildAuthBody(sessionInfo: LiveRoomSessionInfo): String = """
        {"uid":0,"roomid":${sessionInfo.realRoomId},"protover":3,"platform":"web","type":2,"key":"${sessionInfo.token}"}
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
