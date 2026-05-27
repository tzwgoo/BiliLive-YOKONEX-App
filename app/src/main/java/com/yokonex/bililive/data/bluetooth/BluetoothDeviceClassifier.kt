package com.yokonex.bililive.data.bluetooth

import com.yokonex.bililive.data.bluetooth.model.BluetoothDevice

class BluetoothDeviceClassifier {
    fun classify(
        deviceId: String,
        name: String,
        serviceUuids: Set<String>,
    ): BluetoothDevice {
        val normalized = name.uppercase()
        val normalizedServiceUuids = serviceUuids.map(String::lowercase).toSet()
        val protocol = when {
            normalized.startsWith("YYC-DJ-V2") -> "ems_v2"
            normalized.startsWith("YYC-DJ") -> "ems_v1"
            EMS_SERVICE_UUID in normalizedServiceUuids -> "ems_v2"
            else -> "unknown"
        }

        return BluetoothDevice(
            id = deviceId,
            name = name,
            protocol = protocol,
            serviceUuids = serviceUuids,
        )
    }

    private companion object {
        const val EMS_SERVICE_UUID = "0000ff30-0000-1000-8000-00805f9b34fb"
    }
}
