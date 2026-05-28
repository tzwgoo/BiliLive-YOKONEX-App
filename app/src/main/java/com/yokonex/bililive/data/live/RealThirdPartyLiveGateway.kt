package com.yokonex.bililive.data.live

import com.yokonex.bililive.domain.model.LiveEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import okio.ByteString
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.channelFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

interface BinarySocketConnection {
    suspend fun send(packet: ByteArray)

    suspend fun receive(): ByteArray

    suspend fun close()
}

class RealThirdPartyLiveGateway(
    private val liveRoomClient: LiveRoomClient = DefaultBilibiliLiveRoomClient(),
    private val socketConnectionFactory: (String) -> BinarySocketConnection = { url ->
        OkHttpBinarySocketConnection(url)
    },
    private val parserFactory: (String) -> ThirdPartyMessageParser = { roomId ->
        ThirdPartyMessageParser(roomId)
    },
) : ThirdPartyLiveGateway {
    override fun events(roomId: String): Flow<LiveEvent> = channelFlow {
        val sessionInfo = liveRoomClient.createSessionInfo(roomId)
        val parser = parserFactory(sessionInfo.realRoomId)
        val session = BilibiliLiveDanmakuSession(
            sessionInfo = sessionInfo,
            socketConnectionFactory = socketConnectionFactory,
        )

        try {
            session.packets().collect { packet ->
                send(parser.parse(packet.body.decodeToString()))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }
}

internal class OkHttpBinarySocketConnection(
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
