package com.yokonex.bililive

import android.Manifest
import android.os.Build

object PermissionRequirements {
    fun requiredPermissions(sdkInt: Int = Build.VERSION.SDK_INT): List<String> = buildList {
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (sdkInt >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    fun missingPermissions(
        sdkInt: Int = Build.VERSION.SDK_INT,
        isGranted: (String) -> Boolean,
    ): List<String> = requiredPermissions(sdkInt).filterNot(isGranted)

    fun shouldRequestPermissions(grantedStates: Map<String, Boolean>): Boolean =
        grantedStates.any { (_, isGranted) -> !isGranted }
}
