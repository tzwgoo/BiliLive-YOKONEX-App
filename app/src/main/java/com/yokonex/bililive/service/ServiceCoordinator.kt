package com.yokonex.bililive.service

import com.yokonex.bililive.data.live.ThirdPartyLiveGateway
import com.yokonex.bililive.data.websocket.CommandSocketClient
import com.yokonex.bililive.data.websocket.CommandSocketState
import com.yokonex.bililive.domain.model.LiveEvent
import com.yokonex.bililive.domain.model.OutputMode
import com.yokonex.bililive.domain.usecase.MonitoringCoordinator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class ServiceCoordinator(
    private val configProvider: MonitoringConfigProvider = StaticPreviewMonitoringConfigProvider(),
    private val liveGateway: ThirdPartyLiveGateway = PreviewThirdPartyLiveGateway(),
    private val commandSocketClient: CommandSocketClient = PreviewCommandSocketClient(),
    private val eventProcessor: suspend (LiveEvent) -> Unit = { },
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MonitoringCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _status = MutableStateFlow<ServiceStatus>(ServiceStatus.Idle)
    val status: StateFlow<ServiceStatus> = _status.asStateFlow()

    private var monitoringJob: Job? = null

    override suspend fun start() {
        if (monitoringJob != null) {
            return
        }
        _status.value = ServiceStatus.Starting
        val config = runCatching { configProvider.current() }
            .getOrElse { error ->
                _status.value = ServiceStatus.Error(error.message ?: "读取监听配置失败")
                return
            }
        if (config.roomId.isBlank()) {
            _status.value = ServiceStatus.Error("房间号不能为空")
            return
        }
        monitoringJob = scope.launch {
            try {
                prepareOutput(config)
                _status.value = ServiceStatus.Running
                while (true) {
                    try {
                        liveGateway.events(config.roomId).collect { event ->
                            eventProcessor(event)
                        }
                        break
                    } catch (_: Exception) {
                        _status.value = ServiceStatus.Reconnecting
                        delay(config.reconnectIntervalMillis)
                        _status.value = ServiceStatus.Running
                    }
                }
            } catch (error: Exception) {
                _status.value = ServiceStatus.Error(error.message ?: "监听服务启动失败")
            } finally {
                if (_status.value !is ServiceStatus.Error && _status.value !is ServiceStatus.Stopping) {
                    _status.value = ServiceStatus.Idle
                }
                monitoringJob = null
            }
        }
    }

    override suspend fun stop() {
        val job = monitoringJob
        if (job == null) {
            _status.value = ServiceStatus.Idle
            return
        }
        _status.value = ServiceStatus.Stopping
        job.cancelAndJoin()
        monitoringJob = null
        runCatching { commandSocketClient.disconnect() }
        _status.value = ServiceStatus.Idle
    }

    fun notifyError(message: String) {
        _status.value = ServiceStatus.Error(message)
    }

    private suspend fun prepareOutput(config: MonitoringConfig) {
        if (config.outputMode != OutputMode.WEBSOCKET) {
            return
        }
        if (config.websocketEndpoint.isBlank()) {
            throw IllegalArgumentException("WebSocket 服务地址不能为空")
        }
        if (config.websocketUid.isBlank()) {
            throw IllegalArgumentException("WebSocket UID 不能为空")
        }
        if (config.websocketToken.isBlank()) {
            throw IllegalArgumentException("WebSocket 令牌不能为空")
        }
        commandSocketClient.connect(
            wsUrl = config.websocketEndpoint,
            uid = config.websocketUid,
            token = config.websocketToken,
        )
    }
}

data class MonitoringConfig(
    val roomId: String,
    val outputMode: OutputMode,
    val websocketEndpoint: String = "",
    val websocketUid: String = "",
    val websocketToken: String = "",
    val reconnectIntervalMillis: Long = 3_000,
)

interface MonitoringConfigProvider {
    suspend fun current(): MonitoringConfig
}

sealed interface ServiceStatus {
    data object Idle : ServiceStatus
    data object Starting : ServiceStatus
    data object Running : ServiceStatus
    data object Reconnecting : ServiceStatus
    data object Stopping : ServiceStatus
    data class Error(val message: String) : ServiceStatus
}

private class StaticPreviewMonitoringConfigProvider : MonitoringConfigProvider {
    override suspend fun current(): MonitoringConfig =
        MonitoringConfig(
            roomId = "22608112",
            outputMode = OutputMode.BLUETOOTH,
        )
}

private class PreviewThirdPartyLiveGateway : ThirdPartyLiveGateway {
    override fun events(roomId: String): Flow<LiveEvent> = flow {
        awaitCancellation()
    }
}

private class PreviewCommandSocketClient : CommandSocketClient {
    private val state = MutableStateFlow(CommandSocketState.DISCONNECTED)
    override val connectionState: StateFlow<CommandSocketState> = state.asStateFlow()

    override suspend fun connect(
        wsUrl: String,
        uid: String,
        token: String,
    ) {
        state.value = CommandSocketState.CONNECTED
    }

    override suspend fun disconnect() {
        state.value = CommandSocketState.DISCONNECTED
    }

    override suspend fun sendCommand(commandSlot: String) = Unit
}
