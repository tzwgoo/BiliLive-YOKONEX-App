package com.yokonex.bililive.service

import android.content.Context
import android.os.PowerManager

internal interface WakeLockHandle {
    val isHeld: Boolean

    fun acquire()

    fun release()
}

internal interface WakeLockFactory {
    fun create(tag: String): WakeLockHandle?
}

internal class MonitoringWakeLockController(
    private val wakeLockFactory: WakeLockFactory,
    private val wakeLockTag: String = DEFAULT_WAKE_LOCK_TAG,
) {
    private var wakeLock: WakeLockHandle? = null

    fun ensureAcquired() {
        val currentWakeLock = wakeLock ?: wakeLockFactory.create(wakeLockTag)?.also { created ->
            wakeLock = created
        } ?: return
        if (!currentWakeLock.isHeld) {
            currentWakeLock.acquire()
        }
    }

    fun release() {
        val currentWakeLock = wakeLock ?: return
        if (currentWakeLock.isHeld) {
            currentWakeLock.release()
        }
        wakeLock = null
    }

    private companion object {
        private const val DEFAULT_WAKE_LOCK_TAG = "BiliLive-YOKONEX:LiveMonitor"
    }
}

internal class AndroidWakeLockFactory(
    context: Context,
) : WakeLockFactory {
    private val powerManager = context.getSystemService(PowerManager::class.java)

    override fun create(tag: String): WakeLockHandle? {
        val manager = powerManager ?: return null
        return AndroidWakeLockHandle(
            manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag).apply {
                setReferenceCounted(false)
            },
        )
    }
}

private class AndroidWakeLockHandle(
    private val wakeLock: PowerManager.WakeLock,
) : WakeLockHandle {
    override val isHeld: Boolean
        get() = wakeLock.isHeld

    override fun acquire() {
        wakeLock.acquire()
    }

    override fun release() {
        wakeLock.release()
    }
}
