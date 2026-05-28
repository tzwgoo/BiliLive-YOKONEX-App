package com.yokonex.bililive.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMonitorServiceTest {

    @Test
    fun shouldStopMonitoringService_ignoresIdleBeforeForegroundStarted() {
        assertFalse(shouldStopMonitoringService(ServiceStatus.Idle, hasEnteredForeground = false))
    }

    @Test
    fun shouldStopMonitoringService_stopsWhenReturnedToIdleAfterStart() {
        assertTrue(shouldStopMonitoringService(ServiceStatus.Idle, hasEnteredForeground = true))
    }
}
