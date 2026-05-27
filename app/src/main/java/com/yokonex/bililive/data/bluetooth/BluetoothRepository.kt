package com.yokonex.bililive.data.bluetooth

import com.yokonex.bililive.data.bluetooth.model.BluetoothConnectionState
import com.yokonex.bililive.data.bluetooth.model.BluetoothDevice
import kotlinx.coroutines.flow.StateFlow

interface BluetoothRepository {
    val connectionState: StateFlow<BluetoothConnectionState>

    suspend fun scan(): List<BluetoothDevice>

    suspend fun connect(deviceId: String)

    suspend fun disconnect()

    suspend fun playWaveform(waveformId: String)
}

