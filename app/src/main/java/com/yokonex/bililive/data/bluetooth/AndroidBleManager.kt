package com.yokonex.bililive.data.bluetooth

import com.yokonex.bililive.data.bluetooth.model.BluetoothDevice
import com.yokonex.bililive.data.bluetooth.model.BluetoothTelemetry
import kotlinx.coroutines.flow.StateFlow

interface AndroidBleManager {
    val telemetry: StateFlow<BluetoothTelemetry>

    suspend fun scan(): List<BluetoothDevice>

    suspend fun connect(deviceId: String)

    suspend fun disconnect()

    suspend fun write(packet: ByteArray)
}
