package com.soumo.child.permissions

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import com.soumo.child.R
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

    fun showPermissionRationaleDialog(onGrant: () -> Unit, onCancel: () -> Unit) {
        val view: View = LayoutInflater.from(activity) // Inflate custom dialog view
            .inflate(R.layout.dialog_permission_rationale, null)

        val headerIcon = view.findViewById<ImageView>(R.id.permissions_header_icon_view)
        val cameraMicGroup = view.findViewById<View>(R.id.permissions_camera_mic_icon_group)
        val locationIcon = view.findViewById<ImageView>(R.id.permissions_location_icon_view)
        val communicationGroup = view.findViewById<View>(R.id.permissions_communication_icon_group)
        val notificationIcon = view.findViewById<ImageView>(R.id.permissions_notification_icon_view)
        val infoIcon = view.findViewById<ImageView>(R.id.permissions_info_icon_view)
        val cardsContainer = view.findViewById<LinearLayout>(R.id.permissions_cards_container)
        val cameraCard = view.findViewById<View>(R.id.permissions_card_camera)
        val locationCard = view.findViewById<View>(R.id.permissions_card_location)
        val communicationCard = view.findViewById<View>(R.id.permissions_card_communication)
        val notificationCard = view.findViewById<View>(R.id.permissions_card_notification)

        updatePermissionCardsVisibility(
            cardsContainer,
            cameraCard,
            locationCard,
            communicationCard,
            notificationCard
        )

        startIconPulse(headerIcon, cameraMicGroup, locationIcon, communicationGroup, notificationIcon, infoIcon)

        val dialog = AlertDialog.Builder(activity) // AppCompat/Framework dialog
            .setView(view) // Set custom view
            .setPositiveButton(activity.getString(R.string.permissions_dialog_positive)) { _, _ -> // Continue button
                onGrant() // Proceed with permission flow
            }
            .setNegativeButton(activity.getString(R.string.permissions_dialog_negative)) { dialogInterface, _ -> // Not Now button
                dialogInterface.dismiss() // Close dialog
                onCancel() // Callback for cancel
            }
            .setNeutralButton(activity.getString(R.string.permissions_dialog_neutral)) { dialogInterface, _ -> // Learn more
                dialogInterface.dismiss()
                Toast.makeText(
                    activity,
                    activity.getString(R.string.permissions_learn_more_toast, activity.packageName),
                    Toast.LENGTH_LONG
                ).show()
            }
            .setCancelable(false) // Prevent dismiss by outside tap
            .create()

        styleDialog(dialog)
        dialog.window?.setBackgroundDrawable(
            ContextCompat.getColor(activity, R.color.permissions_dialog_background).toDrawable()
        )
        dialog.show() // Display dialog
    }

    private fun updatePermissionCardsVisibility(
        container: LinearLayout,
        cameraCard: View,
        locationCard: View,
        communicationCard: View,
        notificationCard: View
    ) {
        val cameraDenied = isPermissionDenied(Manifest.permission.CAMERA) ||
            isPermissionDenied(Manifest.permission.RECORD_AUDIO)
        val locationDenied = isPermissionDenied(Manifest.permission.ACCESS_FINE_LOCATION) ||
            isPermissionDenied(Manifest.permission.ACCESS_COARSE_LOCATION) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                isPermissionDenied(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        val communicationDenied = isPermissionDenied(Manifest.permission.READ_CALL_LOG) ||
            isPermissionDenied(Manifest.permission.READ_SMS)
        val notificationDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            isPermissionDenied(Manifest.permission.POST_NOTIFICATIONS)

        cameraCard.visibility = if (cameraDenied) View.VISIBLE else View.GONE
        locationCard.visibility = if (locationDenied) View.VISIBLE else View.GONE
        communicationCard.visibility = if (communicationDenied) View.VISIBLE else View.GONE
        notificationCard.visibility = if (notificationDenied) View.VISIBLE else View.GONE

        val anyVisible = cameraDenied || locationDenied || communicationDenied || notificationDenied
        container.visibility = if (anyVisible) View.VISIBLE else View.GONE
    }

    private fun isPermissionDenied(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
    }

    private fun styleDialog(dialog: AlertDialog) {
        dialog.setOnShowListener {
            val accentColor = ContextCompat.getColor(activity, R.color.permissions_icon_tint)
            val secondaryColor = ContextCompat.getColor(activity, R.color.permissions_text_secondary)

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accentColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(secondaryColor)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(secondaryColor)
        }

        dialog.window?.setBackgroundDrawable(
            ContextCompat.getColor(activity, R.color.permissions_dialog_background).toDrawable()
        )
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
        val infoView = LayoutInflater.from(activity).inflate(R.layout.dialog_dark_info, null)
        infoView.findViewById<ImageView>(R.id.dialog_icon).setImageResource(R.drawable.ic_battery)
        startIconPulse(infoView.findViewById(R.id.dialog_icon))
        infoView.findViewById<TextView>(R.id.dialog_title).text = activity.getString(R.string.dialog_battery_title)
        infoView.findViewById<TextView>(R.id.dialog_message).text = activity.getString(R.string.dialog_battery_message)

        val dialog = AlertDialog.Builder(activity)
            .setView(infoView)
            .setPositiveButton(R.string.dialog_grant) { _, _ -> // On "Grant", try to request
                try {
                    BatteryOptimizationHelper.requestDisableBatteryOptimization(activity) // Request to disable optimizations
                    activity.window.decorView.postDelayed({ // slight delay to ensure previous intent is processed first
                        BatteryOptimizationHelper.openBatteryOptimizationSettings(activity) // Open settings as fallback
                    }, 1500) // 1.5 second delay
                } catch (_: Exception) { // Catch any exception (e.g. ActivityNotFound)
                    BatteryOptimizationHelper.openBatteryOptimizationSettings(activity) // Open settings directly
                }
            }
            .setNegativeButton(R.string.dialog_not_now) { dialog, _ -> // On "Not now", just dismiss
                dialog.dismiss() // User chose not to grant, just dismiss
            }
            .setCancelable(false) // Force user to choose
            .create()

        styleDialog(dialog)
        dialog.show() // Show the dialog
    }

    private fun requestAutoStartPermission() { // Some manufacturers require enabling auto-start manually
        if (BatteryOptimizationHelper.shouldShowAutoStartInstructions()) { // Check if we should show instructions
            val infoView = LayoutInflater.from(activity).inflate(R.layout.dialog_dark_info, null)
            infoView.findViewById<ImageView>(R.id.dialog_icon).setImageResource(android.R.drawable.ic_media_play)
            startIconPulse(infoView.findViewById(R.id.dialog_icon))
            infoView.findViewById<TextView>(R.id.dialog_title).text = activity.getString(R.string.dialog_autostart_title)
            infoView.findViewById<TextView>(R.id.dialog_message).text = activity.getString(R.string.dialog_autostart_message)

            val dialog = AlertDialog.Builder(activity)
                .setView(infoView)
                .setPositiveButton(R.string.dialog_grant) { _, _ ->
                    BatteryOptimizationHelper.openAutoStartSettings(activity) // Open the auto-start settings screen
                }
                .setNegativeButton(R.string.dialog_not_now, null)
                .setCancelable(false)
                .create()

            styleDialog(dialog)
            dialog.show()
        }
    }

    private fun requestAccessibilityPermission() { // Show dialog to enable Accessibility Service
        if (!isAccessibilityServiceEnabled(activity)) { // Check if service is not already enabled
            val infoView = LayoutInflater.from(activity).inflate(R.layout.dialog_dark_info, null)
            infoView.findViewById<ImageView>(R.id.dialog_icon).setImageResource(R.drawable.ic_accessibility)
            startIconPulse(infoView.findViewById(R.id.dialog_icon))
            infoView.findViewById<TextView>(R.id.dialog_title).text = activity.getString(R.string.dialog_accessibility_title)
            infoView.findViewById<TextView>(R.id.dialog_message).text = activity.getString(R.string.dialog_accessibility_message)

            val dialog = AlertDialog.Builder(activity)
                .setView(infoView)
                .setPositiveButton(R.string.dialog_grant) { _, _ -> // On "Grant", open settings
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) // Intent to open Accessibility settings
                    activity.startActivity(intent)
                }
                .setNegativeButton(R.string.dialog_cancel, null) // On "Cancel", do nothing
                .setCancelable(false) // Force user to choose
                .create()

            styleDialog(dialog)
            dialog.show() // Show the dialog
        }
    }

    private fun requestNotificationListenerPermission() { // Show dialog to enable Notification Listener
        if (!isNotificationServiceEnabled(activity)) { // Check if service is not already enabled
            val infoView = LayoutInflater.from(activity).inflate(R.layout.dialog_dark_info, null)
            infoView.findViewById<ImageView>(R.id.dialog_icon).setImageResource(android.R.drawable.ic_popup_reminder)
            startIconPulse(infoView.findViewById(R.id.dialog_icon))
            infoView.findViewById<TextView>(R.id.dialog_title).text = activity.getString(R.string.dialog_notification_title)
            infoView.findViewById<TextView>(R.id.dialog_message).text = activity.getString(R.string.dialog_notification_message)

            val dialog = AlertDialog.Builder(activity)
                .setView(infoView)
                .setPositiveButton(R.string.dialog_grant) { _, _ ->
                    try { // On "Grant", open settings
                        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS") // Intent to open Notification Listener settings
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // New task flag
                        activity.startActivity(intent) // Start the settings activity
                    } catch (e: Exception) { // Catch any exception (e.g. ActivityNotFound)
                        e.printStackTrace() // Log the exception
                        Toast.makeText(activity, R.string.toast_unable_to_open_settings, Toast.LENGTH_LONG).show() // Inform user
                    }
                }
                .setNegativeButton(R.string.dialog_cancel, null) // On "Cancel", do nothing
                .setCancelable(false) // Force user to choose
                .create()

            styleDialog(dialog)
            dialog.show() // Show the dialog
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

    fun onRequestPermissionsResult(
        // Handle permission request results
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
            val infoView = LayoutInflater.from(activity).inflate(R.layout.dialog_dark_info, null)
            infoView.findViewById<ImageView>(R.id.dialog_icon).setImageResource(android.R.drawable.ic_dialog_alert)
            startIconPulse(infoView.findViewById(R.id.dialog_icon))
            infoView.findViewById<TextView>(R.id.dialog_title).text = activity.getString(R.string.dialog_permissions_needed_title)
            infoView.findViewById<TextView>(R.id.dialog_message).text = activity.getString(R.string.dialog_permissions_needed_message)

            val dialog = AlertDialog.Builder(activity)
                .setView(infoView)
                .setPositiveButton(R.string.dialog_retry) { _, _ -> // On "Retry", request again
                    ActivityCompat.requestPermissions(activity, denied.toTypedArray(), REQUEST_FOREGROUND)
                }
                .setNegativeButton(R.string.dialog_cancel, null) // On "Cancel", do nothing
                .setCancelable(false) // Force user to choose
                .create()

            styleDialog(dialog)
            dialog.show() // Show the dialog
        } else { // If user selected "Don't ask again" or similar
            Toast.makeText(activity, "Some permissions were denied permanently. Please enable them from app settings for full functionality.", Toast.LENGTH_LONG).show() // Show toast informing user
            // Optionally, guide user to app settings to enable permissions manually
            ActivityCompat.requestPermissions(activity, denied.toTypedArray(), REQUEST_FOREGROUND) // Retry requesting to show system dialog again
        }
    }

    private fun startIconPulse(vararg views: View?) {
        views.forEach { view ->
            view?.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.icon_pulse))
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