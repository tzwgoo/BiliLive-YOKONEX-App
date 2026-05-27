package com.yokonex.bililive.data.live

import com.yokonex.bililive.domain.model.LiveEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

interface RoomApiClient {
    suspend fun resolveRoomConnection(roomId: String): RoomConnectionInfo
}

data class RoomConnectionInfo(
    val realRoomId: String,
    val token: String,
    val websocketHosts: List<String>,
)

interface BinarySocketConnection {
    suspend fun send(packet: ByteArray)

    suspend fun receive(): ByteArray

    suspend fun close()
}

class RealThirdPartyLiveGateway(
    private val roomApiClient: RoomApiClient = BilibiliRoomApiClient(),
    private val socketConnectionFactory: (String) -> BinarySocketConnection = { url ->
        OkHttpBinarySocketConnection(url)
    },
    private val parserFactory: (String) -> ThirdPartyMessageParser = { roomId ->
        ThirdPartyMessageParser(roomId)
    },
) : ThirdPartyLiveGateway {
    override fun events(roomId: String): Flow<LiveEvent> = channelFlow {
        val connectionInfo = roomApiClient.resolveRoomConnection(roomId)
        val parser = parserFactory(connectionInfo.realRoomId)
        val host = connectionInfo.websocketHosts.firstOrNull().orEmpty().ifBlank { DEFAULT_WS_HOST }
        val connection = socketConnectionFactory("wss://$host/sub")
        val heartbeatJob = launch {
            while (true) {
                delay(20_000)
                connection.send(
                    BilibiliDanmakuProtocol.encodePacket(
                        operation = BilibiliDanmakuProtocol.OP_HEARTBEAT,
                    ),
                )
            }
        }

        try {
            connection.send(
                BilibiliDanmakuProtocol.encodePacket(
                    operation = BilibiliDanmakuProtocol.OP_AUTH,
                    body = buildAuthBody(
                        roomId = connectionInfo.realRoomId,
                        token = connectionInfo.token,
                    ).encodeToByteArray(),
                ),
            )

            while (true) {
                val rawPacket = connection.receive()
                val decodedPackets = BilibiliDanmakuProtocol.decodePackets(rawPacket)
                for (packet in decodedPackets) {
                    if (packet.operation != BilibiliDanmakuProtocol.OP_SEND_SMS_REPLY) {
                        continue
                    }
                    send(parser.parse(packet.body.decodeToString()))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        } finally {
            heartbeatJob.cancel()
            connection.close()
        }
    }

    private fun buildAuthBody(
        roomId: String,
        token: String,
    ): String = """
        {"uid":0,"roomid":$roomId,"protover":2,"platform":"web","type":2,"key":"$token"}
    """.trimIndent()

    companion object {
        private const val DEFAULT_WS_HOST = "broadcastlv.chat.bilibili.com"
    }
}

class BilibiliRoomApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : RoomApiClient {
    override suspend fun resolveRoomConnection(roomId: String): RoomConnectionInfo = withContext(Dispatchers.IO) {
        val realRoomId = fetchRealRoomId(roomId)
        fetchDanmuConfig(realRoomId)
    }

    private fun fetchRealRoomId(roomId: String): String {
        val request = Request.Builder()
            .url("https://api.live.bilibili.com/room/v1/Room/room_init?id=$roomId")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val root = json.parseToJsonElement(body).jsonObject
            val realRoomId = root["data"]?.jsonObject?.get("room_id")?.jsonPrimitive?.content.orEmpty()
            return realRoomId.ifBlank { roomId }
        }
    }

    private fun fetchDanmuConfig(realRoomId: String): RoomConnectionInfo {
        val request = Request.Builder()
            .url("https://api.live.bilibili.com/room/v1/Danmu/getConf?room_id=$realRoomId&platform=pc&player=web")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val root = json.parseToJsonElement(body).jsonObject
            val data = root["data"]?.jsonObject ?: JsonObject(emptyMap())
            val token = data["token"]?.jsonPrimitive?.content.orEmpty()
            val hostList = data["host_server_list"]?.jsonArray?.mapNotNull { hostElement ->
                hostElement.jsonObject["host"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            }.orEmpty()
            return RoomConnectionInfo(
                realRoomId = realRoomId,
                token = token,
                websocketHosts = hostList.ifEmpty { listOf(DEFAULT_WS_HOST) },
            )
        }
    }

    companion object {
        private const val DEFAULT_WS_HOST = "broadcastlv.chat.bilibili.com"
    }
}

private class OkHttpBinarySocketConnection(
    url: String,
    client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build(),
) : BinarySocketConnection {
    private val incoming = Channel<ByteArray>(capacity = Channel.UNLIMITED)
    private val opened = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val webSocket: WebSocket

    init {
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response,
                ) {
                    opened.complete(Unit)
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    bytes: ByteString,
                ) {
                    incoming.trySend(bytes.toByteArray())
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String,
                ) {
                    incoming.trySend(text.encodeToByteArray())
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?,
                ) {
                    if (!opened.isCompleted) {
                        opened.completeExceptionally(t)
                    }
                    incoming.close(t)
                }

                override fun onClosing(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String,
                ) {
                    incoming.close()
                }
            },
        )
    }

    override suspend fun send(packet: ByteArray) {
        opened.await()
        if (!webSocket.send(ByteString.of(*packet))) {
            throw RuntimeException("第三方消息流发送数据失败")
        }
    }

    override suspend fun receive(): ByteArray =
        incoming.receiveCatching().getOrNull()
            ?: throw RuntimeException("第三方消息流连接已断开")

    override suspend fun close() {
        webSocket.close(1000, "client close")
        incoming.close()
    }
}
