package com.yokonex.bililive.domain.model

sealed interface OutputAction {
    data class BluetoothWaveformAction(
        val waveformId: String,
    ) : OutputAction

    data class WebSocketCommandAction(
        val commandSlot: String,
    ) : OutputAction
}

enum class OutputMode {
    BLUETOOTH,
    WEBSOCKET,
}

