package com.yokonex.bililive.data.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothDeviceClassifierTest {

    private val classifier = BluetoothDeviceClassifier()

    @Test
    fun classifier_detectsEmsV2FromNamePrefix() {
        val device = classifier.classify(
            deviceId = "device-1",
            name = "YYC-DJ-V2-001",
            serviceUuids = emptySet(),
        )

        assertEquals("ems_v2", device.protocol)
    }

    @Test
    fun classifier_detectsEmsV1FromNamePrefix() {
        val device = classifier.classify(
            deviceId = "device-2",
            name = "YYC-DJ-CLASSIC",
            serviceUuids = emptySet(),
        )

        assertEquals("ems_v1", device.protocol)
    }

    @Test
    fun classifier_detectsEmsV2FromServiceUuid() {
        val device = classifier.classify(
            deviceId = "device-3",
            name = "Custom EMS Device",
            serviceUuids = setOf("0000ff30-0000-1000-8000-00805f9b34fb"),
        )

        assertEquals("ems_v2", device.protocol)
    }
}
