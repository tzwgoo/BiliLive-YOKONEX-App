package com.yokonex.bililive.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.yokonex.bililive.domain.model.OutputMode

class LiveMonitorService : Service() {
    private lateinit var notificationFactory: NotificationFactory

    override fun onCreate() {
        super.onCreate()
        notificationFactory = NotificationFactory(this)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        startForeground(
            NotificationFactory.NOTIFICATION_ID,
            notificationFactory.createMonitoringNotification(
                roomId = intent?.getStringExtra(EXTRA_ROOM_ID).orEmpty(),
                outputMode = intent?.getStringExtra(EXTRA_OUTPUT_MODE)
                    ?.let { name -> runCatching { OutputMode.valueOf(name) }.getOrNull() }
                    ?: OutputMode.BLUETOOTH,
            ),
        )
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_ROOM_ID = "extra_room_id"
        const val EXTRA_OUTPUT_MODE = "extra_output_mode"
    }
}
