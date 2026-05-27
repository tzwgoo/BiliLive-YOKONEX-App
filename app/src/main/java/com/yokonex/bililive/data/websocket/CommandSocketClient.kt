package com.yokonex.bililive.data.websocket

import kotlinx.coroutines.flow.StateFlow

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
