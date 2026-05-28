package com.yokonex.bililive

import android.Manifest
import android.os.Build
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionRequirementsTest {

    @Test
    fun requiredPermissions_sdk29_onlyIncludesForegroundServiceCompanionsNotRuntimeOnes() {
        val permissions = PermissionRequirements.requiredPermissions(Build.VERSION_CODES.Q)

        assertTrue(permissions.isEmpty())
    }

    @Test
    fun requiredPermissions_sdk31_includesBluetoothRuntimePermissions() {
        val permissions = PermissionRequirements.requiredPermissions(Build.VERSION_CODES.S)

        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            ),
            permissions,
        )
    }

    @Test
    fun requiredPermissions_sdk33_includesNotificationsAndBluetoothPermissions() {
        val permissions = PermissionRequirements.requiredPermissions(Build.VERSION_CODES.TIRAMISU)

        assertEquals(
            listOf(
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            ),
            permissions,
        )
    }

    @Test
    fun missingPermissions_returnsOnlyDeniedEntries() {
        val missingPermissions = PermissionRequirements.missingPermissions(Build.VERSION_CODES.TIRAMISU) { permission ->
            permission == Manifest.permission.BLUETOOTH_SCAN
        }

        assertEquals(
            listOf(
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.BLUETOOTH_CONNECT,
            ),
            missingPermissions,
        )
    }

    @Test
    fun shouldRequestPermissions_returnsTrueWhenAnyPermissionMissing() {
        val grantedStates = mapOf(
            Manifest.permission.POST_NOTIFICATIONS to true,
            Manifest.permission.BLUETOOTH_SCAN to false,
        )

        assertTrue(PermissionRequirements.shouldRequestPermissions(grantedStates))
    }

    @Test
    fun shouldRequestPermissions_returnsFalseWhenAllPermissionsGranted() {
        val grantedStates = mapOf(
            Manifest.permission.POST_NOTIFICATIONS to true,
            Manifest.permission.BLUETOOTH_SCAN to true,
            Manifest.permission.BLUETOOTH_CONNECT to true,
        )

        assertFalse(PermissionRequirements.shouldRequestPermissions(grantedStates))
    }

    @Test
    fun androidManifest_declaresForegroundServiceTypePermissionsForLiveMonitorService() {
        val manifest = sequenceOf(
            File("app/src/main/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml"),
        ).firstOrNull(File::exists)?.readText(Charsets.UTF_8).orEmpty()

        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC"))
    }
}
