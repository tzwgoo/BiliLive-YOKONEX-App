package com.yokonex.bililive.data.bluetooth.model

data class BluetoothRuntimeStatus(
    val connected: Boolean = false,
    val deviceName: String = "",
    val waveformName: String = "",
    val batteryLevel: Int? = null,
    val channelAStrength: Int = 0,
    val channelBStrength: Int = 0,
)
