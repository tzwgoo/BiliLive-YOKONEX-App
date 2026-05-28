package com.yokonex.bililive.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringWakeLockControllerTest {

    @Test
    fun ensureAcquired_acquiresWakeLockOnlyOnce() {
        val wakeLock = FakeWakeLockHandle()
        val controller = MonitoringWakeLockController(
            wakeLockFactory = FakeWakeLockFactory(wakeLock),
        )

        controller.ensureAcquired()
        controller.ensureAcquired()

        assertTrue(wakeLock.isHeld)
        assertEquals(1, wakeLock.acquireCount)
    }

    @Test
    fun release_releasesHeldWakeLock() {
        val wakeLock = FakeWakeLockHandle()
        val controller = MonitoringWakeLockController(
            wakeLockFactory = FakeWakeLockFactory(wakeLock),
        )

        controller.ensureAcquired()
        controller.release()

        assertFalse(wakeLock.isHeld)
        assertEquals(1, wakeLock.releaseCount)
    }
}

private class FakeWakeLockFactory(
    private val wakeLock: FakeWakeLockHandle,
) : WakeLockFactory {
    override fun create(tag: String): WakeLockHandle = wakeLock
}

private class FakeWakeLockHandle : WakeLockHandle {
    override var isHeld: Boolean = false
    var acquireCount: Int = 0
    var releaseCount: Int = 0

    override fun acquire() {
        acquireCount += 1
        isHeld = true
    }

    override fun release() {
        releaseCount += 1
        isHeld = false
    }
}
