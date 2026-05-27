package com.yokonex.bililive.data.bluetooth

import com.yokonex.bililive.data.bluetooth.model.BluetoothDevice

interface AndroidBleManager {
    suspend fun scan(): List<BluetoothDevice>

    suspend fun connect(deviceId: String)

    suspend fun disconnect()

    suspend fun write(packet: ByteArray)
}

