package com.yokonex.bililive.service

import com.yokonex.bililive.data.live.ThirdPartyLiveGateway
import com.yokonex.bililive.data.websocket.CommandSocketClient
import com.yokonex.bililive.data.websocket.CommandSocketRuntimeInfo
import com.yokonex.bililive.data.websocket.CommandSocketState
import com.yokonex.bililive.domain.model.EventPayload
import com.yokonex.bililive.domain.model.LiveEvent
import com.yokonex.bililive.domain.model.LiveEventType
import com.yokonex.bililive.domain.model.OutputMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceCoordinatorTest {

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start_inWebSocketMode_connectsSocketAndProcessesEvents() = runTest {
        val commandSocketClient = FakeCommandSocketClient()
        val processedEvents = mutableListOf<LiveEvent>()
        val coordinator = ServiceCoordinator(
            configProvider = StaticMonitoringConfigProvider(
                MonitoringConfig(
                    roomId = "22608112",
                    outputMode = OutputMode.WEBSOCKET,
                    websocketEndpoint = "ws://127.0.0.1:9001/live",
                    websocketUid = "game_123456",
                    websocketToken = "demo-token",
                ),
            ),
            liveGateway = FakeThirdPartyLiveGateway(
                events = listOf(
                    LiveEvent(
                        id = "evt-1",
                        type = LiveEventType.DANMAKU,
                        timestamp = 1_714_113_037,
                        userId = "1001",
                        userName = "测试用户",
                        roomId = "22608112",
                        payload = EventPayload.DanmakuPayload("测试弹幕"),
                    ),
                ),
            ),
            commandSocketClient = commandSocketClient,
            eventProcessor = { event -> processedEvents += event },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        coordinator.start()
        advanceUntilIdle()

        assertEquals(ServiceStatus.Running, coordinator.status.value)
        assertEquals(listOf("ws://127.0.0.1:9001/live|game_123456|demo-token"), commandSocketClient.connectCalls)
        assertEquals(listOf("evt-1"), processedEvents.map(LiveEvent::id))
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start_withoutRoomId_marksErrorStatus() = runTest {
        val coordinator = ServiceCoordinator(
            configProvider = StaticMonitoringConfigProvider(
                MonitoringConfig(
                    roomId = "",
                    outputMode = OutputMode.BLUETOOTH,
                ),
            ),
            liveGateway = FakeThirdPartyLiveGateway(events = emptyList()),
            commandSocketClient = FakeCommandSocketClient(),
            eventProcessor = { },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        coordinator.start()
        advanceUntilIdle()

        assertTrue(coordinator.status.value is ServiceStatus.Error)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start_whenGatewayFails_entersReconnectingAndRetries() = runTest {
        val gateway = FlakyThirdPartyLiveGateway()
        val coordinator = ServiceCoordinator(
            configProvider = StaticMonitoringConfigProvider(
                MonitoringConfig(
                    roomId = "22608112",
                    outputMode = OutputMode.BLUETOOTH,
                    autoReconnectEnabled = true,
                    reconnectIntervalMillis = 3_000,
                ),
            ),
            liveGateway = gateway,
            commandSocketClient = FakeCommandSocketClient(),
            eventProcessor = { },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        coordinator.start()
        runCurrent()
        assertEquals(ServiceStatus.Reconnecting, coordinator.status.value)

        advanceTimeBy(3_000)
        runCurrent()

        assertTrue(gateway.attempts >= 2)
        assertEquals(ServiceStatus.Running, coordinator.status.value)
        coordinator.stop()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start_whenAutoReconnectDisabled_stopsAtErrorAfterGatewayFailure() = runTest {
        val gateway = FlakyThirdPartyLiveGateway()
        val coordinator = ServiceCoordinator(
            configProvider = StaticMonitoringConfigProvider(
                MonitoringConfig(
                    roomId = "22608112",
                    outputMode = OutputMode.BLUETOOTH,
                    autoReconnectEnabled = false,
                    reconnectIntervalMillis = 3_000,
                ),
            ),
            liveGateway = gateway,
            commandSocketClient = FakeCommandSocketClient(),
            eventProcessor = { },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        coordinator.start()
        runCurrent()

        assertEquals(1, gateway.attempts)
        assertEquals(ServiceStatus.Error("socket dropped"), coordinator.status.value)
    }
}

private class StaticMonitoringConfigProvider(
    private val config: MonitoringConfig,
) : MonitoringConfigProvider {
    override suspend fun current(): MonitoringConfig = config
}

private class FakeThirdPartyLiveGateway(
    private val events: List<LiveEvent>,
) : ThirdPartyLiveGateway {
    override fun events(roomId: String) = flow {
        events.forEach { emit(it) }
        awaitCancellation()
    }
}

private class FlakyThirdPartyLiveGateway : ThirdPartyLiveGateway {
    var attempts: Int = 0

    override fun events(roomId: String) = flow {
        attempts += 1
        if (attempts == 1) {
            throw IllegalStateException("socket dropped")
        }
        emit(
            LiveEvent(
                id = "evt-recovered",
                type = LiveEventType.SYSTEM,
                timestamp = 1_714_113_999,
                userId = "",
                userName = "",
                roomId = roomId,
                payload = EventPayload.SystemPayload("recovered"),
            ),
        )
        awaitCancellation()
    }
}

private class FakeCommandSocketClient : CommandSocketClient {
    private val _connectionState = MutableStateFlow(CommandSocketState.DISCONNECTED)
    override val connectionState: StateFlow<CommandSocketState> = _connectionState
    private val _runtimeInfo = MutableStateFlow(CommandSocketRuntimeInfo())
    override val runtimeInfo: StateFlow<CommandSocketRuntimeInfo> = _runtimeInfo
    val connectCalls = mutableListOf<String>()

    override suspend fun connect(
        wsUrl: String,
        uid: String,
        token: String,
    ) {
        connectCalls += "$wsUrl|$uid|$token"
        _connectionState.value = CommandSocketState.CONNECTED
        _runtimeInfo.value = CommandSocketRuntimeInfo(
            userId = uid.removePrefix("game_"),
            uid = uid,
            isReady = true,
            sdkEvent = "SDK_READY",
            networkState = "CONNECTED",
        )
    }

    override suspend fun disconnect() {
        _connectionState.value = CommandSocketState.DISCONNECTED
        _runtimeInfo.value = CommandSocketRuntimeInfo()
    }

    override suspend fun sendCommand(
        commandSlot: String,
        repeatCount: Int,
    ) = Unit
}
