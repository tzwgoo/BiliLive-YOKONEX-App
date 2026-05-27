package com.yokonex.bililive.data.websocket

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

interface CommandSocketClient {
    val connectionState: StateFlow<CommandSocketState>

    suspend fun connect(
        wsUrl: String,
        uid: String,
        token: String,
    )

    suspend fun disconnect()

    suspend fun sendCommand(commandSlot: String)
}

enum class CommandSocketState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

fun deriveUserIdFromUid(uid: String): String =
    uid.removePrefix("game_").trim()

interface SocketConnection {
    suspend fun send(text: String)

    suspend fun receive(): String

    suspend fun close()
}

class OkHttpCommandSocketClient(
    private val connectionFactory: (String) -> SocketConnection = { url ->
        OkHttpSocketConnection(url)
    },
    private val payloadFactory: CommandPayloadFactory = CommandPayloadFactory(),
    private val loginTimeoutMillis: Long = 8_000,
    private val pingIntervalMillis: Long = 30_000,
    private val idleTimeoutMillis: Long = 270_000,
    private val timeProvider: () -> Long = System::currentTimeMillis,
) : CommandSocketClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val _connectionState = MutableStateFlow(CommandSocketState.DISCONNECTED)
    override val connectionState: StateFlow<CommandSocketState> = _connectionState.asStateFlow()

    private var connection: SocketConnection? = null
    private var wsUrl: String = ""
    private var uid: String = ""
    private var token: String = ""
    private var userId: String = ""
    private var loggedIn: Boolean = false
    private var pingJob = scope.launchSilently { }
    private var lastReceivedAt: Long = 0L

    override suspend fun connect(
        wsUrl: String,
        uid: String,
        token: String,
    ) {
        mutex.withLock {
            validateUrl(wsUrl)
            this.wsUrl = wsUrl
            this.uid = uid
            this.token = token
            this.userId = deriveUserIdFromUid(uid)
            establishConnectionLocked()
            loginLocked()
        }
    }

    override suspend fun disconnect() {
        mutex.withLock {
            try {
                if (loggedIn && userId.isNotBlank()) {
                    connection?.send(payloadFactory.buildLogout(userId))
                }
            } catch (_: Exception) {
            }
            pingJob.cancel()
            connection?.close()
            connection = null
            loggedIn = false
            _connectionState.value = CommandSocketState.DISCONNECTED
        }
    }

    override suspend fun sendCommand(commandSlot: String) {
        mutex.withLock {
            ensureConnectedLocked()
            connection?.send(payloadFactory.buildSendCommand(userId, commandSlot))
            while (true) {
                val message = receiveJsonLocked()
                when (message["type"]?.jsonPrimitive?.content.orEmpty()) {
                    "commandResult" -> return
                    "pong",
                    "connected",
                    "heartbeat",
                    "status",
                    "network",
                    "message",
                    -> continue

                    "error" -> {
                        throw RuntimeException(message["message"]?.jsonPrimitive?.content ?: "下游指令通道返回错误")
                    }
                }
            }
        }
    }

    private suspend fun ensureConnectedLocked() {
        if (connection == null || shouldRefreshConnectionLocked()) {
            establishConnectionLocked()
            loginLocked()
            return
        }
        if (!loggedIn) {
            loginLocked()
        }
    }

    private suspend fun establishConnectionLocked() {
        pingJob.cancel()
        connection?.close()
        _connectionState.value = CommandSocketState.CONNECTING
        loggedIn = false
        lastReceivedAt = timeProvider()
        connection = connectionFactory(wsUrl)
    }

    private suspend fun loginLocked() {
        val currentConnection = connection ?: error("下游指令通道尚未连接")
        currentConnection.send(payloadFactory.buildLogin(uid, token))
        val loginResult: JsonObject = withTimeout(loginTimeoutMillis) {
            waitForLoginResultLocked()
        }
        val success = loginResult["success"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        if (!success) {
            _connectionState.value = CommandSocketState.ERROR
            throw RuntimeException(loginResult["message"]?.jsonPrimitive?.content ?: "下游指令通道登录失败")
        }
        val returnedUserId = loginResult["data"]?.jsonObject?.get("userId")?.jsonPrimitive?.content.orEmpty()
        if (returnedUserId.isNotBlank()) {
            userId = returnedUserId
        }
        if (userId.isBlank()) {
            _connectionState.value = CommandSocketState.ERROR
            throw RuntimeException("下游指令通道登录成功，但未返回可用的 userId")
        }
        loggedIn = true
        _connectionState.value = CommandSocketState.CONNECTED
        startPingLoop()
    }

    private suspend fun receiveJsonLocked(): JsonObject =
        json.parseToJsonElement(connection?.receive().orEmpty()).jsonObject.also {
            lastReceivedAt = timeProvider()
        }

    private suspend fun waitForLoginResultLocked(): JsonObject {
        while (true) {
            val message = receiveJsonLocked()
            when (message["type"]?.jsonPrimitive?.content.orEmpty()) {
                "loginResult" -> return message
                "connected",
                "heartbeat",
                "pong",
                "status",
                "network",
                "message",
                -> continue

                "error" -> throw RuntimeException(message["message"]?.jsonPrimitive?.content ?: "下游指令通道登录失败")
            }
        }
    }

    private fun startPingLoop() {
        if (pingIntervalMillis <= 0) {
            return
        }
        pingJob.cancel()
        pingJob = scope.launchSilently {
            while (true) {
                delay(pingIntervalMillis)
                mutex.withLock {
                    if (connection == null || !loggedIn) {
                        return@withLock
                    }
                    if (idleTimeoutMillis > 0 && shouldRefreshConnectionLocked()) {
                        connection?.close()
                        connection = null
                        loggedIn = false
                        _connectionState.value = CommandSocketState.DISCONNECTED
                        return@withLock
                    }
                    connection?.send(payloadFactory.buildPing())
                }
            }
        }
    }

    private fun shouldRefreshConnectionLocked(): Boolean {
        if (idleTimeoutMillis <= 0 || lastReceivedAt <= 0L) {
            return false
        }
        return (timeProvider() - lastReceivedAt) >= idleTimeoutMillis
    }

    private fun validateUrl(url: String) {
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            throw RuntimeException("WS URL 必须以 ws:// 或 wss:// 开头")
        }
    }
}

private class OkHttpSocketConnection(
    url: String,
    client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build(),
) : SocketConnection {
    private val incoming = Channel<String>(capacity = Channel.UNLIMITED)
    private val opened = CompletableDeferred<Unit>()
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
                    text: String,
                ) {
                    incoming.trySend(text)
                }

                override fun onClosing(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String,
                ) {
                    incoming.close()
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
            },
        )
    }

    override suspend fun send(text: String) {
        opened.await()
        if (!webSocket.send(text)) {
            throw RuntimeException("下游指令通道发送失败")
        }
    }

    override suspend fun receive(): String =
        incoming.receiveCatching().getOrNull()
            ?: throw RuntimeException("下游指令通道已断开")

    override suspend fun close() {
        webSocket.close(1000, "client close")
        incoming.close()
    }
}

private fun CoroutineScope.launchSilently(block: suspend CoroutineScope.() -> Unit) =
    launch {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }
