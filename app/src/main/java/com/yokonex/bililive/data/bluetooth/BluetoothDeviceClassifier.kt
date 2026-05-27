package com.yokonex.bililive.data.bluetooth

import com.yokonex.bililive.data.bluetooth.model.BluetoothDevice

class BluetoothDeviceClassifier {
    fun classify(
        deviceId: String,
        name: String,
        serviceUuids: Set<String>,
    ): BluetoothDevice {
        val normalized = name.uppercase()
        val protocol = when {
            normalized.startsWith("YYC-DJ-V2") -> "ems_v2"
            normalized.startsWith("YYC-DJ") -> "ems_v1"
            else -> "unknown"
        }

        return BluetoothDevice(
            id = deviceId,
            name = name,
            protocol = protocol,
            serviceUuids = serviceUuids,
        )
    }
}

