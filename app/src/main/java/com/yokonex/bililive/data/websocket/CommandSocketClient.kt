package com.yokonex.bililive.data.websocket

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
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
import kotlinx.serialization.json.contentOrNull
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
    val runtimeInfo: StateFlow<CommandSocketRuntimeInfo>

    suspend fun connect(
        wsUrl: String,
        uid: String,
        token: String,
    )

    suspend fun disconnect()

    suspend fun sendCommand(
        commandSlot: String,
        repeatCount: Int = 1,
    )
}

enum class CommandSocketState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

data class CommandSocketRuntimeInfo(
    val userId: String = "",
    val uid: String = "",
    val isReady: Boolean = false,
    val sdkEvent: String = "",
    val networkState: String = "",
    val totalSessions: Int? = null,
    val wsConnections: Int? = null,
    val lastHeartbeatTimestamp: Long? = null,
    val lastErrorMessage: String? = null,
)

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
    private val _runtimeInfo = MutableStateFlow(CommandSocketRuntimeInfo())
    override val runtimeInfo: StateFlow<CommandSocketRuntimeInfo> = _runtimeInfo.asStateFlow()

    private var connection: SocketConnection? = null
    private var messageChannel: Channel<JsonObject>? = null
    private var wsUrl: String = ""
    private var uid: String = ""
    private var token: String = ""
    private var userId: String = ""
    private var loggedIn: Boolean = false
    private var pingJob = scope.launchSilently { }
    private var readerJob = scope.launchSilently { }
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
            try {
                establishConnectionLocked()
                loginLocked()
            } catch (error: Exception) {
                handleConnectionFailureLocked(error.message ?: "下游指令通道连接失败")
                throw error
            }
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
            readerJob.cancel()
            connection?.close()
            messageChannel?.close()
            connection = null
            messageChannel = null
            loggedIn = false
            _connectionState.value = CommandSocketState.DISCONNECTED
            _runtimeInfo.value = CommandSocketRuntimeInfo()
        }
    }

    override suspend fun sendCommand(
        commandSlot: String,
        repeatCount: Int,
    ) {
        mutex.withLock {
            ensureConnectedLocked()
            repeat(repeatCount.coerceAtLeast(1)) {
                connection?.send(payloadFactory.buildSendCommand(userId, commandSlot))
                while (true) {
                    val message = receiveMessageLocked()
                    when (message["type"]?.jsonPrimitive?.content.orEmpty()) {
                        "commandResult" -> {
                            val success = message["success"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                            if (!success) {
                                val failureMessage = message["message"]?.jsonPrimitive?.contentOrNull ?: "下游指令通道发送失败"
                                updateLastError(failureMessage)
                                throw RuntimeException(failureMessage)
                            }
                            break
                        }
                        "error" -> {
                            val failureMessage = message["message"]?.jsonPrimitive?.contentOrNull ?: "下游指令通道返回错误"
                            updateLastError(failureMessage)
                            throw RuntimeException(failureMessage)
                        }
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
        readerJob.cancel()
        connection?.close()
        messageChannel?.close()
        _connectionState.value = CommandSocketState.CONNECTING
        loggedIn = false
        lastReceivedAt = timeProvider()
        _runtimeInfo.value = CommandSocketRuntimeInfo(
            uid = uid,
        )
        connection = connectionFactory(wsUrl)
        messageChannel = Channel(capacity = Channel.UNLIMITED)
        startReaderLoopLocked()
    }

    private suspend fun loginLocked() {
        val currentConnection = connection ?: error("下游指令通道尚未连接")
        currentConnection.send(payloadFactory.buildLogin(uid, token))
        val loginResult = try {
            withTimeout(loginTimeoutMillis) {
                waitForLoginResultLocked()
            }
        } catch (error: Exception) {
            val failureMessage = error.message ?: "下游指令通道登录超时"
            updateLastError(failureMessage)
            _connectionState.value = CommandSocketState.ERROR
            throw RuntimeException(failureMessage, error)
        }
        val success = loginResult["success"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        if (!success) {
            val failureMessage = loginResult["message"]?.jsonPrimitive?.contentOrNull ?: "下游指令通道登录失败"
            updateLastError(failureMessage)
            _connectionState.value = CommandSocketState.ERROR
            throw RuntimeException(failureMessage)
        }
        loggedIn = true
        _connectionState.value = CommandSocketState.CONNECTED
        updateLastError(null)
        startPingLoop()
        currentConnection.send(payloadFactory.buildGetStatus())
    }

    private suspend fun waitForLoginResultLocked(): JsonObject {
        while (true) {
            val message = receiveMessageLocked()
            when (message["type"]?.jsonPrimitive?.content.orEmpty()) {
                "loginResult" -> return message
                "error" -> {
                    val failureMessage = message["message"]?.jsonPrimitive?.contentOrNull ?: "下游指令通道登录失败"
                    updateLastError(failureMessage)
                    throw RuntimeException(failureMessage)
                }
            }
        }
    }

    private suspend fun receiveMessageLocked(): JsonObject =
        messageChannel?.receiveCatching()?.getOrNull()
            ?: throw RuntimeException("下游指令通道已断开")

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

    private fun startReaderLoopLocked() {
        val currentConnection = connection ?: return
        val currentChannel = messageChannel ?: return
        readerJob = scope.launchSilently(start = CoroutineStart.UNDISPATCHED) {
            try {
                while (true) {
                    val rawMessage = currentConnection.receive()
                    val message = json.parseToJsonElement(rawMessage).jsonObject
                    lastReceivedAt = timeProvider()
                    handleIncomingMessage(message)
                    currentChannel.send(message)
                }
            } catch (cancelled: CancellationException) {
                currentChannel.close(cancelled)
                throw cancelled
            } catch (error: Exception) {
                currentChannel.close(error)
                mutex.withLock {
                    if (connection === currentConnection) {
                        handleConnectionFailureLocked(error.message ?: "下游指令通道已断开")
                    }
                }
            }
        }
    }

    private fun handleIncomingMessage(message: JsonObject) {
        when (message["type"]?.jsonPrimitive?.content.orEmpty()) {
            "connected" -> {
                updateRuntimeInfo { current ->
                    current.copy(
                        uid = if (current.uid.isBlank()) uid else current.uid,
                    )
                }
            }

            "loginResult" -> {
                val data = message["data"]?.jsonObject
                val returnedUserId = data?.get("userId")?.jsonPrimitive?.contentOrNull.orEmpty()
                if (returnedUserId.isNotBlank()) {
                    userId = returnedUserId
                }
                updateRuntimeInfo { current ->
                    current.copy(
                        userId = returnedUserId.ifBlank { current.userId },
                        uid = data?.get("uid")?.jsonPrimitive?.contentOrNull ?: current.uid.ifBlank { uid },
                        isReady = data?.get("isReady")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: current.isReady,
                        lastErrorMessage = if ((message["success"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false)) {
                            null
                        } else {
                            message["message"]?.jsonPrimitive?.contentOrNull ?: current.lastErrorMessage
                        },
                    )
                }
            }

            "status" -> {
                val messageUserId = message["userId"]?.jsonPrimitive?.contentOrNull
                if (messageUserId.isNullOrBlank()) {
                    val data = message["data"]?.jsonObject
                    updateRuntimeInfo { current ->
                        current.copy(
                            totalSessions = data?.get("totalSessions")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: current.totalSessions,
                            wsConnections = data?.get("wsConnections")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: current.wsConnections,
                        )
                    }
                } else {
                    val data = message["data"]?.jsonObject
                    val sdkEvent = data?.get("event")?.jsonPrimitive?.contentOrNull.orEmpty()
                    val isReady = data?.get("isReady")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                    updateRuntimeInfo { current ->
                        current.copy(
                            userId = messageUserId,
                            uid = data?.get("user")?.jsonPrimitive?.contentOrNull ?: current.uid,
                            sdkEvent = sdkEvent,
                            isReady = isReady,
                        )
                    }
                    if (sdkEvent == "KICKED_OUT") {
                        _connectionState.value = CommandSocketState.ERROR
                        updateLastError("IM 会话被踢下线")
                    }
                }
            }

            "network" -> {
                val messageUserId = message["userId"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val networkState = message["data"]?.jsonObject?.get("state")?.jsonPrimitive?.contentOrNull.orEmpty()
                updateRuntimeInfo { current ->
                    current.copy(
                        userId = messageUserId.ifBlank { current.userId },
                        networkState = networkState,
                    )
                }
            }

            "heartbeat" -> {
                val timestamp = message["data"]?.jsonObject?.get("timestamp")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                updateRuntimeInfo { current ->
                    current.copy(
                        lastHeartbeatTimestamp = timestamp ?: current.lastHeartbeatTimestamp,
                    )
                }
            }

            "error" -> {
                val failureMessage = message["message"]?.jsonPrimitive?.contentOrNull ?: "下游指令通道返回错误"
                updateLastError(failureMessage)
            }
        }
    }

    private fun updateRuntimeInfo(transform: (CommandSocketRuntimeInfo) -> CommandSocketRuntimeInfo) {
        _runtimeInfo.value = transform(_runtimeInfo.value)
    }

    private fun updateLastError(message: String?) {
        updateRuntimeInfo { current ->
            current.copy(lastErrorMessage = message)
        }
    }

    private suspend fun handleConnectionFailureLocked(message: String) {
        pingJob.cancel()
        readerJob.cancel()
        connection?.close()
        messageChannel?.close()
        connection = null
        messageChannel = null
        loggedIn = false
        _connectionState.value = CommandSocketState.ERROR
        updateLastError(message)
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

private fun CoroutineScope.launchSilently(
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit,
) = launch(start = start) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }
