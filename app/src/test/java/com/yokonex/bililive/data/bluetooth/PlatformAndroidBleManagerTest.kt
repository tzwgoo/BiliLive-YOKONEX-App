package com.yokonex.bililive.data.bluetooth

import android.bluetooth.BluetoothProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformAndroidBleManagerTest {

    @Test
    fun resolveConnectionStateAction_prefersConnectedStateEvenWhenStatusIsBusy() {
        val action = resolveConnectionStateAction(
            status = 201,
            newState = BluetoothProfile.STATE_CONNECTED,
        )

        assertEquals(GattConnectionAction.DISCOVER_SERVICES, action)
    }

    @Test
    fun buildConnectionFailureMessage_forStatus201_returnsHelpfulHint() {
        val message = buildConnectionFailureMessage(status = 201)

        assertTrue(message.contains("尚未完全释放"))
    }

    @Test
    fun calculateReconnectDelayMillis_returnsRemainingCooldownWindow() {
        val delayMillis = calculateReconnectDelayMillis(
            nowMillis = 1_000L,
            lastDisconnectAtMillis = 600L,
            cooldownMillis = 800L,
        )

        assertEquals(400L, delayMillis)
    }
}
