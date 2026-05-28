package com.yokonex.bililive.app.ui.live

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

data class BatteryOptimizationStatus(
    val supported: Boolean,
    val ignoringBatteryOptimizations: Boolean,
)

interface BatteryOptimizationStatusProvider {
    fun currentStatus(): BatteryOptimizationStatus
}

class AndroidBatteryOptimizationStatusProvider(
    private val context: Context,
) : BatteryOptimizationStatusProvider {
    override fun currentStatus(): BatteryOptimizationStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return BatteryOptimizationStatus(
                supported = false,
                ignoringBatteryOptimizations = true,
            )
        }
        val powerManager = context.getSystemService(PowerManager::class.java)
            ?: return BatteryOptimizationStatus(
                supported = false,
                ignoringBatteryOptimizations = true,
            )
        return BatteryOptimizationStatus(
            supported = true,
            ignoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName),
        )
    }
}

object BatteryOptimizationNavigator {
    fun openIgnoreRequest(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            openSettings(context)
            return
        }
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
