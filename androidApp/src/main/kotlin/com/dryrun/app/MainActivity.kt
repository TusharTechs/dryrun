package com.dryrun.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.dryrun.app.notifications.LocalNotifier
import com.dryrun.app.notifications.NotificationPermissionHost

class MainActivity : ComponentActivity() {

    private var pendingPermissionResult: ((Boolean) -> Unit)? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingPermissionResult?.invoke(granted)
            pendingPermissionResult = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        NotificationPermissionHost.requester = { onResult ->
            pendingPermissionResult = onResult
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val notifier = LocalNotifier(applicationContext)
        setContent { App(notifier) }
    }

    override fun onDestroy() {
        NotificationPermissionHost.requester = null
        super.onDestroy()
    }
}
