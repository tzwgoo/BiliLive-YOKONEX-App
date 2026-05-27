package com.yokonex.bililive.data.bluetooth.model

data class BluetoothDevice(
    val id: String,
    val name: String,
    val protocol: String,
    val serviceUuids: Set<String> = emptySet(),
)

