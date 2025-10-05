package com.soumo.child

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateListOf
import com.soumo.child.permissions.PermissionManager
import com.soumo.child.ui.ChildUI
import android.util.Log

/**
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
class MainActivity : AppCompatActivity() {
    // UI log buffer observed by ChildUI
    private val logs = mutableStateListOf<String>()
    private lateinit var permissionManager: PermissionManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionManager = PermissionManager(this)
        setContent {
            ChildUI(
                context = this, // pass activity context
                logs = logs
            )
        }

        // Check if permissions are already granted and start service immediately
        if (permissionManager.allPermissionsGranted()) {
            Log.d("MainActivity", "✅ Permissions already granted, starting BackgroundService on restart...")
            val svc = Intent(this, BackgroundService::class.java)
            startService(svc)
            Log.d("MainActivity", "✅ BackgroundService started on restart")
        } else {
            permissionManager.startPermissionFlow()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionManager.onRequestPermissionsResult(requestCode) { allGranted ->
            if (allGranted) {
                Log.d("MainActivity", "🫡 All permissions granted, starting BackgroundService...")
                val svc = Intent(this, BackgroundService::class.java)
                startService(svc)
                Log.d("MainActivity", "🤫 BackgroundService start command sent")
            } else {
                /**
                 * Send a toast to inform user that, they need to grant permissions for full functionality.
                 * However, we do not keep asking for permissions repeatedly.
                 * The app will work with limited features if any permission is denied.
                 */
                Toast.makeText(this, "App will not work properly if any permission is denied.", Toast.LENGTH_LONG).show()
            }
        }
    }
}