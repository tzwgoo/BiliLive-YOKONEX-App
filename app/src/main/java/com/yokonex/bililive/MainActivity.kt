package com.yokonex.bililive

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.yokonex.bililive.app.navigation.AppNavGraph
import com.yokonex.bililive.app.ui.theme.BiliLiveTheme

class MainActivity : ComponentActivity() {
    private var permissionRequestLaunched = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionRequestLaunched = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionRequestLaunched = savedInstanceState?.getBoolean(KEY_PERMISSION_REQUEST_LAUNCHED) ?: false
        setContent {
            BiliLiveTheme {
                AppNavGraph()
            }
        }
        requestRuntimePermissionsIfNeeded()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_PERMISSION_REQUEST_LAUNCHED, permissionRequestLaunched)
    }

    private fun requestRuntimePermissionsIfNeeded() {
        if (permissionRequestLaunched) {
            return
        }

        val missingPermissions = PermissionRequirements.missingPermissions { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            return
        }

        permissionRequestLaunched = true
        permissionLauncher.launch(missingPermissions.toTypedArray())
    }

    companion object {
        private const val KEY_PERMISSION_REQUEST_LAUNCHED = "key_permission_request_launched"
    }
}
