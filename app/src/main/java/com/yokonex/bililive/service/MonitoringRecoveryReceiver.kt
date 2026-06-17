package com.yokonex.bililive.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.yokonex.bililive.AppContainer
import com.yokonex.bililive.AppServices
import com.yokonex.bililive.data.storage.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MonitoringRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        val appContext = context.applicationContext
        if (AppServices.applicationContext == null) {
            AppServices.applicationContext = appContext
        }
        if (AppServices.container == null) {
            AppServices.container = AppContainer(appContext)
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = AppServices.container ?: return@launch
                handleAction(
                    context = appContext,
                    action = intent?.action.orEmpty(),
                    settingsStore = container.settingsStore,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAction(
        context: Context,
        action: String,
        settingsStore: SettingsStore,
    ) {
        val shouldRecover = when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // 开机恢复只在用户明确开启后生效，避免每次开机都强拉起监听服务。
                settingsStore.restoreMonitoringOnBootEnabled.first() && settingsStore.monitoringActive.first()
            }

            MonitoringRecoveryScheduler.ACTION_RECOVER_MONITORING -> {
                settingsStore.monitoringActive.first()
            }

            else -> false
        }
        if (!shouldRecover) {
            MonitoringRecoveryScheduler.cancel(context)
            return
        }
        val roomId = settingsStore.roomId.first()
        if (roomId.isBlank()) {
            MonitoringRecoveryScheduler.cancel(context)
            return
        }
        LiveMonitorService.startService(
            context = context,
            roomId = roomId,
            outputMode = settingsStore.outputMode.first(),
        )
    }
}

object MonitoringRecoveryScheduler {
    const val ACTION_RECOVER_MONITORING = "com.yokonex.bililive.action.RECOVER_MONITORING"

    fun schedule(
        context: Context,
        delayMillis: Long = 2_500L,
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAtMillis = System.currentTimeMillis() + delayMillis
        val pendingIntent = buildPendingIntent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(buildPendingIntent(context))
    }

    private fun buildPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            10_021,
            Intent(context, MonitoringRecoveryReceiver::class.java).apply {
                action = ACTION_RECOVER_MONITORING
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
