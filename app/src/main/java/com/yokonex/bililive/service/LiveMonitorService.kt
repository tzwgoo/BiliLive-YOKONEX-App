package com.yokonex.bililive.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yokonex.bililive.AppServices
import com.yokonex.bililive.domain.model.OutputMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class LiveMonitorService : Service() {
    private lateinit var notificationFactory: NotificationFactory
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var hasEnteredForeground: Boolean = false

    override fun onCreate() {
        super.onCreate()
        notificationFactory = NotificationFactory(this)
        serviceScope.launch {
            AppServices.container?.serviceCoordinator?.status?.collect { status ->
                if (shouldStopMonitoringService(status, hasEnteredForeground)) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            serviceScope.launch {
                AppServices.container?.serviceCoordinator?.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        val roomId = intent?.getStringExtra(EXTRA_ROOM_ID).orEmpty()
        val outputMode = intent?.getStringExtra(EXTRA_OUTPUT_MODE)
            ?.let { name -> runCatching { OutputMode.valueOf(name) }.getOrNull() }
            ?: OutputMode.BLUETOOTH
        ServiceCompat.startForeground(
            this,
            NotificationFactory.NOTIFICATION_ID,
            notificationFactory.createMonitoringNotification(
                roomId = roomId,
                outputMode = outputMode,
            ),
            outputMode.toForegroundServiceType(),
        )
        hasEnteredForeground = true
        serviceScope.launch {
            AppServices.container?.serviceCoordinator?.start()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val ACTION_START = "com.yokonex.bililive.action.START_MONITORING"
        private const val ACTION_STOP = "com.yokonex.bililive.action.STOP_MONITORING"
        const val EXTRA_ROOM_ID = "extra_room_id"
        const val EXTRA_OUTPUT_MODE = "extra_output_mode"

        fun startService(
            context: android.content.Context,
            roomId: String,
            outputMode: OutputMode,
        ) {
            val intent = Intent(context, LiveMonitorService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_OUTPUT_MODE, outputMode.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: android.content.Context) {
            val intent = Intent(context, LiveMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

internal fun shouldStopMonitoringService(
    status: ServiceStatus,
    hasEnteredForeground: Boolean,
): Boolean = hasEnteredForeground && status is ServiceStatus.Idle

private fun OutputMode.toForegroundServiceType(): Int =
    when (this) {
        OutputMode.BLUETOOTH -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        OutputMode.WEBSOCKET -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    }
