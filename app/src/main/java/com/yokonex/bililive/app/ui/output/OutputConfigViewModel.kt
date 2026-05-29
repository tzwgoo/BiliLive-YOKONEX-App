package com.yokonex.bililive.app.ui.output

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yokonex.bililive.AppServices
import com.yokonex.bililive.data.bluetooth.BluetoothRepository
import com.yokonex.bililive.data.bluetooth.model.BluetoothConnectionState
import com.yokonex.bililive.data.websocket.CommandSocketClient
import com.yokonex.bililive.data.websocket.CommandSocketState
import com.yokonex.bililive.data.storage.SettingsStore
import com.yokonex.bililive.domain.model.LiveEventType
import com.yokonex.bililive.domain.model.OutputMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OutputConfigViewModel(
    private val settingsStore: SettingsStore? = AppServices.container?.settingsStore,
    private val bluetoothRepository: BluetoothRepository? = AppServices.container?.bluetoothRepository,
    private val commandSocketClient: CommandSocketClient? = AppServices.container?.commandSocketClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OutputConfigUiState())
    val uiState: StateFlow<OutputConfigUiState> = _uiState.asStateFlow()

    init {
        settingsStore?.let { store ->
            viewModelScope.launch {
                store.outputMode.collect { mode ->
                    _uiState.update { currentState ->
                        currentState.copy(outputMode = mode)
                    }
                }
            }
            viewModelScope.launch {
                store.websocketEndpoint.collect { endpoint ->
                    _uiState.update { currentState ->
                        currentState.copy(socketEndpoint = endpoint)
                    }
                }
            }
            viewModelScope.launch {
                store.websocketUid.collect { uid ->
                    _uiState.update { currentState ->
                        currentState.copy(socketUid = uid)
                    }
                }
            }
            viewModelScope.launch {
                store.websocketToken.collect { token ->
                    _uiState.update { currentState ->
                        currentState.copy(socketToken = token)
                    }
                }
            }
        }
        bluetoothRepository?.let { repository ->
            viewModelScope.launch {
                repository.devices.collect { devices ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            bluetoothDevices = devices.map { device ->
                                UiBluetoothDevice(
                                    id = device.id,
                                    name = device.name,
                                    protocol = device.protocol,
                                    connected = device.connected,
                                )
                            },
                        )
                    }
                }
            }
            viewModelScope.launch {
                repository.connectionState.collect { state ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            bluetoothStatus = state.toDisplayLabel(),
                            connectingBluetoothDeviceId = if (state == BluetoothConnectionState.CONNECTING) {
                                currentState.connectingBluetoothDeviceId
                            } else {
                                null
                            },
                            bluetoothErrorMessage = if (state == BluetoothConnectionState.ERROR) {
                                currentState.bluetoothErrorMessage
                            } else {
                                null
                            },
                        )
                    }
                }
            }
            viewModelScope.launch {
                repository.runtimeStatus.collect { status ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            connectedBluetoothDeviceName = if (status.connected) status.deviceName else "",
                            bluetoothBatteryLevel = if (status.connected) status.batteryLevel else null,
                            channelAStrength = if (status.connected) status.mixedChannelAStrength else 0,
                            channelBStrength = if (status.connected) status.mixedChannelBStrength else 0,
                            bluetoothLeaderLabel = status.leaderEventType.toDisplayLabel(),
                            bluetoothOutputCapLabel = status.outputCap.toString(),
                            bluetoothMixModeLabel = if (status.mixModeEnabled) "混波" else "串行",
                            activeBluetoothLayerCount = status.activeLayerCount,
                        )
                    }
                }
            }
            viewModelScope.launch {
                runCatching { repository.scan() }
            }
        }
        commandSocketClient?.let { client ->
            viewModelScope.launch {
                client.connectionState.collect { state ->
                    _uiState.update { currentState ->
                        currentState.copy(websocketStatus = state.toDisplayLabel())
                    }
                }
            }
        }
    }

    fun selectMode(mode: OutputMode) {
        if (settingsStore == null) {
            _uiState.update { currentState ->
                currentState.copy(outputMode = mode)
            }
            return
        }
        viewModelScope.launch {
            settingsStore.updateOutputMode(mode)
        }
    }

    fun updateSocketEndpoint(endpoint: String) {
        if (settingsStore == null) {
            _uiState.update { currentState ->
                currentState.copy(socketEndpoint = endpoint)
            }
            return
        }
        viewModelScope.launch {
            settingsStore.updateWebSocketEndpoint(endpoint)
        }
    }

    fun updateSocketUid(uid: String) {
        if (settingsStore == null) {
            _uiState.update { currentState ->
                currentState.copy(socketUid = uid)
            }
            return
        }
        viewModelScope.launch {
            settingsStore.updateWebSocketUid(uid)
        }
    }

    fun updateSocketToken(token: String) {
        if (settingsStore == null) {
            _uiState.update { currentState ->
                currentState.copy(socketToken = token)
            }
            return
        }
        viewModelScope.launch {
            settingsStore.updateWebSocketToken(token)
        }
    }

    fun connectCommandChannel() {
        val client = commandSocketClient
        if (client == null) {
            _uiState.update { currentState ->
                currentState.copy(websocketStatus = "已连接")
            }
            return
        }
        viewModelScope.launch {
            runCatching {
                client.connect(
                    wsUrl = uiState.value.socketEndpoint,
                    uid = uiState.value.socketUid,
                    token = uiState.value.socketToken,
                )
            }
        }
    }

    fun disconnectCommandChannel() {
        val client = commandSocketClient
        if (client == null) {
            _uiState.update { currentState ->
                currentState.copy(websocketStatus = "未连接")
            }
            return
        }
        viewModelScope.launch {
            runCatching { client.disconnect() }
        }
    }

    fun scanBluetoothDevices() {
        val repository = bluetoothRepository ?: return
        viewModelScope.launch {
            runCatching { repository.scan() }
        }
    }

    fun connectBluetoothDevice(deviceId: String) {
        val repository = bluetoothRepository
        if (repository == null) {
            _uiState.update { currentState ->
                currentState.copy(
                    bluetoothStatus = "已连接",
                    connectingBluetoothDeviceId = null,
                    bluetoothErrorMessage = null,
                    bluetoothDevices = currentState.bluetoothDevices.map { device ->
                        device.copy(connected = device.id == deviceId)
                    },
                )
            }
            return
        }
        val currentState = uiState.value
        if (!currentState.canConnectBluetooth(deviceId)) {
            return
        }
        _uiState.update { state ->
            state.copy(
                connectingBluetoothDeviceId = deviceId,
                bluetoothErrorMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching { repository.connect(deviceId) }
                .onFailure { error ->
                    _uiState.update { state ->
                        state.copy(
                            connectingBluetoothDeviceId = null,
                            bluetoothErrorMessage = error.message ?: "蓝牙连接失败",
                        )
                    }
                }
        }
    }

    fun disconnectBluetoothDevice() {
        val repository = bluetoothRepository
        if (repository == null) {
            _uiState.update { currentState ->
                currentState.copy(
                    bluetoothStatus = "未连接",
                    bluetoothDevices = currentState.bluetoothDevices.map { device ->
                        device.copy(connected = false)
                    },
                    connectingBluetoothDeviceId = null,
                    bluetoothErrorMessage = null,
                    bluetoothBatteryLevel = null,
                    channelAStrength = 0,
                    channelBStrength = 0,
                )
            }
            return
        }
        viewModelScope.launch {
            runCatching { repository.disconnect() }
        }
    }
}

data class OutputConfigUiState(
    val outputMode: OutputMode = OutputMode.BLUETOOTH,
    val bluetoothDevices: List<UiBluetoothDevice> = sampleBluetoothDevices(),
    val socketEndpoint: String = "ws://103.236.55.92:43001/",
    val socketUid: String = "",
    val socketToken: String = "demo-token",
    val websocketStatus: String = "未连接",
    val bluetoothStatus: String = "未连接",
    val connectingBluetoothDeviceId: String? = null,
    val bluetoothErrorMessage: String? = null,
    val connectedBluetoothDeviceName: String = "",
    val bluetoothBatteryLevel: Int? = null,
    val channelAStrength: Int = 0,
    val channelBStrength: Int = 0,
    val bluetoothLeaderLabel: String = "",
    val bluetoothOutputCapLabel: String = "130",
    val bluetoothMixModeLabel: String = "串行",
    val activeBluetoothLayerCount: Int = 0,
) {
    val canConnectSocket: Boolean
        get() = websocketStatus != "连接中" &&
            websocketStatus != "已连接" &&
            socketEndpoint.isNotBlank() &&
            socketUid.isNotBlank() &&
            socketToken.isNotBlank()

    val canDisconnectSocket: Boolean
        get() = websocketStatus == "连接中" || websocketStatus == "已连接"

    val canDisconnectBluetooth: Boolean
        get() = bluetoothDevices.any(UiBluetoothDevice::connected)

    fun canConnectBluetooth(deviceId: String): Boolean =
        bluetoothStatus != "连接中" &&
            connectingBluetoothDeviceId == null &&
            bluetoothDevices.none { device -> device.connected && device.id == deviceId }
}

data class UiBluetoothDevice(
    val id: String,
    val name: String,
    val protocol: String,
    val connected: Boolean,
)

private fun sampleBluetoothDevices(): List<UiBluetoothDevice> = listOf(
    UiBluetoothDevice(
        id = "ble_1",
        name = "YYC-DJ-V2-Alpha",
        protocol = "ems_v2",
        connected = false,
    ),
    UiBluetoothDevice(
        id = "ble_2",
        name = "YYC-DJ-Classic",
        protocol = "ems_v1",
        connected = false,
    ),
)

private fun CommandSocketState.toDisplayLabel(): String =
    when (this) {
        CommandSocketState.DISCONNECTED -> "未连接"
        CommandSocketState.CONNECTING -> "连接中"
        CommandSocketState.CONNECTED -> "已连接"
        CommandSocketState.ERROR -> "连接异常"
    }

private fun BluetoothConnectionState.toDisplayLabel(): String =
    when (this) {
        BluetoothConnectionState.DISCONNECTED -> "未连接"
        BluetoothConnectionState.SCANNING -> "扫描中"
        BluetoothConnectionState.CONNECTING -> "连接中"
        BluetoothConnectionState.CONNECTED -> "已连接"
        BluetoothConnectionState.ERROR -> "连接异常"
    }

private fun LiveEventType?.toDisplayLabel(): String =
    when (this) {
        LiveEventType.GIFT -> "礼物主层"
        LiveEventType.DANMAKU -> "弹幕主层"
        LiveEventType.LIKE -> "点赞主层"
        LiveEventType.SYSTEM -> "系统主层"
        null -> ""
    }
