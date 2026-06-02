package com.yokonex.bililive.data.websocket

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OkHttpCommandSocketClientTest {

    @Test
    fun deriveUserIdFromGameUid_removesGamePrefix() {
        assertEquals("123456", deriveUserIdFromUid("game_123456"))
    }

    @Test
    fun deriveUserIdFromNumericUid_keepsOriginalValue() {
        assertEquals("123456", deriveUserIdFromUid("123456"))
    }

    @Test
    fun connect_sendsLoginPayloadAndUpdatesState() = runTest {
        val connection = FakeSocketConnection(
            incomingMessages = listOf(
                """{"type":"loginResult","success":true,"data":{"userId":"123456"}}""",
            ),
        )
        val client = OkHttpCommandSocketClient(
            connectionFactory = { _ -> connection },
            loginTimeoutMillis = 50,
            pingIntervalMillis = 0,
            idleTimeoutMillis = 0,
        )

        client.connect(
            wsUrl = "ws://127.0.0.1:43001/",
            uid = "game_123456",
            token = "token-demo",
        )

        assertEquals(CommandSocketState.CONNECTED, client.connectionState.value)
        assertEquals("123456", client.runtimeInfo.value.userId)
        assertEquals(
            """{"type":"login","uid":"game_123456","token":"token-demo"}""",
            connection.sentMessages.first(),
        )
    }

    @Test
    fun connect_whenLoginTimesOut_setsErrorStateAndMessage() = runTest {
        val connection = FakeSocketConnection(
            incomingMessages = emptyList(),
        )
        val client = OkHttpCommandSocketClient(
            connectionFactory = { _ -> connection },
            loginTimeoutMillis = 50,
            pingIntervalMillis = 0,
            idleTimeoutMillis = 0,
        )

        runCatching {
            client.connect(
                wsUrl = "ws://127.0.0.1:43001/",
                uid = "123456",
                token = "token-demo",
            )
        }

        assertEquals(CommandSocketState.ERROR, client.connectionState.value)
        assertFalse(client.runtimeInfo.value.lastErrorMessage.isNullOrBlank())
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun connect_consumesRuntimePushMessagesAndRequestsStatus() = runTest {
        val connection = FakeSocketConnection(
            incomingMessages = listOf(
                """{"type":"loginResult","success":true,"data":{"userId":"123456","uid":"game_123456","isReady":true}}""",
                """{"type":"status","userId":"123456","data":{"isReady":true,"event":"SDK_READY","user":"game_123456"}}""",
                """{"type":"network","userId":"123456","data":{"state":"CONNECTED"}}""",
                """{"type":"heartbeat","data":{"timestamp":1234567890}}""",
            ),
        )
        val client = OkHttpCommandSocketClient(
            connectionFactory = { _ -> connection },
            loginTimeoutMillis = 50,
            pingIntervalMillis = 0,
            idleTimeoutMillis = 0,
        )

        client.connect(
            wsUrl = "ws://127.0.0.1:43001/",
            uid = "123456",
            token = "token-demo",
        )
        advanceUntilIdle()

        assertEquals("123456", client.runtimeInfo.value.userId)
        assertEquals("game_123456", client.runtimeInfo.value.uid)
        assertEquals(true, client.runtimeInfo.value.isReady)
        assertEquals("SDK_READY", client.runtimeInfo.value.sdkEvent)
        assertEquals("CONNECTED", client.runtimeInfo.value.networkState)
        assertEquals(1234567890L, client.runtimeInfo.value.lastHeartbeatTimestamp)
        assertEquals(
            """{"type":"getStatus"}""",
            connection.sentMessages.last(),
        )
    }

    @Test
    fun sendCommand_sendsPayloadAfterLoginAndWaitsForCommandResult() = runTest {
        val connection = FakeSocketConnection(
            incomingMessages = listOf(
                """{"type":"loginResult","success":true,"data":{"userId":"123456"}}""",
                """{"type":"commandResult","success":true,"message":"ok"}""",
            ),
        )
        val client = OkHttpCommandSocketClient(
            connectionFactory = { _ -> connection },
            loginTimeoutMillis = 50,
            pingIntervalMillis = 0,
            idleTimeoutMillis = 0,
        )

        client.connect(
            wsUrl = "ws://127.0.0.1:43001/",
            uid = "123456",
            token = "token-demo",
        )
        client.sendCommand("command_one")

        assertEquals(
            listOf(
                """{"type":"login","uid":"123456","token":"token-demo"}""",
                """{"type":"getStatus"}""",
                """{"type":"sendCommand","userId":"123456","commandId":"command_one"}""",
            ),
            connection.sentMessages,
        )
    }
}

private class FakeSocketConnection(
    incomingMessages: List<String>,
) : SocketConnection {
    private val queue = Channel<String>(capacity = Channel.UNLIMITED)

    init {
        incomingMessages.forEach { message ->
            queue.trySend(message)
        }
    }

    val sentMessages = mutableListOf<String>()

    override suspend fun send(text: String) {
        sentMessages += text
    }

    override suspend fun receive(): String {
        return queue.receive()
    }

    override suspend fun close() = Unit
}
