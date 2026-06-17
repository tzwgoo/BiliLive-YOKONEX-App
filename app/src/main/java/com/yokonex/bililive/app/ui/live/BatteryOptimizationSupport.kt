package com.yokonex.bililive.app.ui.live

import android.content.ComponentName
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

    fun openManufacturerBackgroundSettings(context: Context) {
        val intents = manufacturerIntents(context) + listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            },
            Intent(Settings.ACTION_SETTINGS),
        )
        intents.firstOrNull { intent -> canHandleIntent(context, intent) }
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let(context::startActivity)
    }

    private fun manufacturerIntents(context: Context): List<Intent> {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val packageName = context.packageName
        return when {
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> listOf(
                componentIntent(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                ),
                componentIntent(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity",
                ),
            )

            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("mi") -> listOf(
                componentIntent(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                ),
            )

            manufacturer.contains("oppo") || manufacturer.contains("oneplus") || manufacturer.contains("realme") -> listOf(
                componentIntent(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                ),
                componentIntent(
                    "com.oplus.safecenter",
                    "com.oplus.safecenter.startupapp.StartupAppListActivity",
                ),
            )

            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> listOf(
                componentIntent(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                ),
                componentIntent(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
                ).putExtra("package_name", packageName),
            )

            else -> emptyList()
        }
    }

    private fun componentIntent(
        packageName: String,
        className: String,
    ): Intent =
        Intent().apply {
            component = ComponentName(packageName, className)
        }

    private fun canHandleIntent(
        context: Context,
        intent: Intent,
    ): Boolean =
        context.packageManager.resolveActivity(intent, 0) != null
}

fun backgroundProtectionVendorLabel(): String {
    val manufacturer = Build.MANUFACTURER.lowercase()
    return when {
        manufacturer.contains("huawei") || manufacturer.contains("honor") -> "华为/荣耀后台启动管理"
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("mi") -> "小米自启动管理"
        manufacturer.contains("oppo") || manufacturer.contains("oneplus") || manufacturer.contains("realme") -> "OPPO/一加/真我后台管理"
        manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> "vivo/iQOO后台高耗电管理"
        else -> "厂商后台白名单设置"
    }
}
