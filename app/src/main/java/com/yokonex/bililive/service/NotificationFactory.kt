package com.yokonex.bililive.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.yokonex.bililive.R
import com.yokonex.bililive.domain.model.OutputMode

class NotificationFactory(
    private val context: Context,
) {
    fun createMonitoringNotification(
        roomId: String,
        outputMode: OutputMode,
        statusText: String = "直播监听中",
    ): Notification {
        ensureChannel()
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(statusText)
            .setContentText("房间 $roomId · 模式 ${outputMode.name}")
            .setOngoing(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Live Monitor",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "live_monitor"
        const val NOTIFICATION_ID = 1001
    }
}

