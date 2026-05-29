package com.yokonex.bililive.data.bluetooth.model

import com.yokonex.bililive.domain.model.LiveEventType

data class BluetoothRuntimeStatus(
    val connected: Boolean = false,
    val deviceName: String = "",
    val waveformName: String = "",
    val batteryLevel: Int? = null,
    val channelAStrength: Int = 0,
    val channelBStrength: Int = 0,
    val leaderEventType: LiveEventType? = null,
    val activeLayerCount: Int = 0,
    val outputCap: Int = 130,
    val mixedChannelAStrength: Int = 0,
    val mixedChannelBStrength: Int = 0,
    val mixModeEnabled: Boolean = false,
)
