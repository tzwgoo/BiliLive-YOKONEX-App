package com.yokonex.bililive.app.ui.output

import androidx.lifecycle.ViewModel
import com.yokonex.bililive.domain.model.OutputMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class OutputConfigViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(OutputConfigUiState())
    val uiState: StateFlow<OutputConfigUiState> = _uiState.asStateFlow()

    fun selectMode(mode: OutputMode) {
        _uiState.update { currentState ->
            currentState.copy(outputMode = mode)
        }
    }

    fun updateSocketEndpoint(endpoint: String) {
        _uiState.update { currentState ->
            currentState.copy(socketEndpoint = endpoint)
        }
    }

    fun updateSocketToken(token: String) {
        _uiState.update { currentState ->
            currentState.copy(socketToken = token)
        }
    }
}

data class OutputConfigUiState(
    val outputMode: OutputMode = OutputMode.BLUETOOTH,
    val bluetoothDevices: List<UiBluetoothDevice> = sampleBluetoothDevices(),
    val socketEndpoint: String = "ws://192.168.1.21:9001/live",
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
