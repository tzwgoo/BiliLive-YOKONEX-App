package com.yokonex.bililive.app.ui.live

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LiveConfigViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LiveConfigUiState())
    val uiState: StateFlow<LiveConfigUiState> = _uiState.asStateFlow()

    fun updateRoomId(roomId: String) {
        _uiState.update { currentState ->
            currentState.copy(roomId = roomId.filter(Char::isDigit).take(12))
        }
    }

    fun toggleAutoReconnect(enabled: Boolean) {
        _uiState.update { currentState ->
            currentState.copy(autoReconnect = enabled)
        }
    }

    fun updateReconnectInterval(value: String) {
        _uiState.update { currentState ->
            currentState.copy(reconnectIntervalSeconds = value.filter(Char::isDigit).take(3))
        }
    }
}

data class LiveConfigUiState(
    val roomId: String = "22445566",
    val autoReconnect: Boolean = true,
    val reconnectIntervalSeconds: String = "8",
    val providerName: String = "第三方直播消息流",
    val connectionStatus: String = "等待连接",
)
