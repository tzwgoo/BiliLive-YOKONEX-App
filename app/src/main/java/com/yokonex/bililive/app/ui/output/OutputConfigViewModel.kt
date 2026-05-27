package com.yokonex.bililive.app.ui.output

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yokonex.bililive.AppServices
import com.yokonex.bililive.data.bluetooth.BluetoothRepository
import com.yokonex.bililive.data.websocket.CommandSocketClient
import com.yokonex.bililive.data.websocket.CommandSocketState
import com.yokonex.bililive.data.storage.SettingsStore
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
}

data class OutputConfigUiState(
    val outputMode: OutputMode = OutputMode.BLUETOOTH,
    val bluetoothDevices: List<UiBluetoothDevice> = sampleBluetoothDevices(),
    val socketEndpoint: String = "ws://192.168.1.21:9001/live",
    val socketUid: String = "",
    val socketToken: String = "demo-token",
    val websocketStatus: String = "未连接",
)

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
        connected = true,
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
