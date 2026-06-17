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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LiveMonitorService : Service() {
    private lateinit var notificationFactory: NotificationFactory
    private lateinit var wakeLockController: MonitoringWakeLockController
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var hasEnteredForeground: Boolean = false
    private var stopRequestedByUser: Boolean = false

    override fun onCreate() {
        super.onCreate()
        notificationFactory = NotificationFactory(this)
        wakeLockController = MonitoringWakeLockController(AndroidWakeLockFactory(applicationContext))
        serviceScope.launch {
            AppServices.container?.serviceCoordinator?.status?.collect { status ->
                if (status is ServiceStatus.Error || status is ServiceStatus.Idle || status is ServiceStatus.Stopping) {
                    wakeLockController.release()
                }
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
        val settingsStore = AppServices.container?.settingsStore
        if (intent?.action == ACTION_STOP) {
            stopRequestedByUser = true
            runBlocking {
                settingsStore?.updateMonitoringActive(false)
            }
            MonitoringRecoveryScheduler.cancel(applicationContext)
            serviceScope.launch {
                AppServices.container?.serviceCoordinator?.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        val persistedRoomId = runBlocking { settingsStore?.roomId?.first().orEmpty() }
        val persistedOutputMode = runBlocking { settingsStore?.outputMode?.first() ?: OutputMode.BLUETOOTH }
        val roomId = intent?.getStringExtra(EXTRA_ROOM_ID)
            ?.takeIf(String::isNotBlank)
            ?: persistedRoomId
        val outputMode = intent?.getStringExtra(EXTRA_OUTPUT_MODE)
            ?.let { name -> runCatching { OutputMode.valueOf(name) }.getOrNull() }
            ?: persistedOutputMode
        stopRequestedByUser = false
        runBlocking {
            // 这里记录的是“用户希望监听继续存在”的意图，供异常拉起和开机恢复共用。
            settingsStore?.updateMonitoringActive(roomId.isNotBlank())
        }
        MonitoringRecoveryScheduler.cancel(applicationContext)
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
        wakeLockController.ensureAcquired()
        serviceScope.launch {
            AppServices.container?.serviceCoordinator?.start()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        val shouldRecover = hasEnteredForeground &&
            !stopRequestedByUser &&
            runBlocking { AppServices.container?.settingsStore?.monitoringActive?.first() == true }
        super.onDestroy()
        wakeLockController.release()
        serviceScope.cancel()
        if (shouldRecover) {
            // 服务被系统回收时补一层兜底拉起，避免仅依赖 START_STICKY 导致恢复时机不稳定。
            MonitoringRecoveryScheduler.schedule(applicationContext)
        }
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
