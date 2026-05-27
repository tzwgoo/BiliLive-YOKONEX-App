package com.yokonex.bililive.app.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yokonex.bililive.AppServices
import com.yokonex.bililive.data.storage.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LiveConfigViewModel(
    private val settingsStore: SettingsStore? = AppServices.container?.settingsStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LiveConfigUiState())
    val uiState: StateFlow<LiveConfigUiState> = _uiState.asStateFlow()

    init {
        settingsStore?.let { store ->
            viewModelScope.launch {
                store.roomId.collect { roomId ->
                    _uiState.update { currentState ->
                        currentState.copy(roomId = roomId)
                    }
                }
            }
            viewModelScope.launch {
                store.reconnectIntervalSeconds.collect { seconds ->
                    _uiState.update { currentState ->
                        currentState.copy(reconnectIntervalSeconds = seconds.toString())
                    }
                }
            }
        }
    }

    fun updateRoomId(roomId: String) {
        val sanitized = roomId.filter(Char::isDigit).take(12)
        if (settingsStore == null) {
            _uiState.update { currentState ->
                currentState.copy(roomId = sanitized)
            }
            return
        }
        viewModelScope.launch {
            settingsStore.updateRoomId(sanitized)
        }
    }

    fun toggleAutoReconnect(enabled: Boolean) {
        _uiState.update { currentState ->
            currentState.copy(autoReconnect = enabled)
        }
    }

    fun updateReconnectInterval(value: String) {
        val sanitized = value.filter(Char::isDigit).take(3)
        if (settingsStore == null) {
            _uiState.update { currentState ->
                currentState.copy(reconnectIntervalSeconds = sanitized)
            }
            return
        }
        viewModelScope.launch {
            settingsStore.updateReconnectIntervalSeconds(sanitized.toIntOrNull() ?: 3)
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
