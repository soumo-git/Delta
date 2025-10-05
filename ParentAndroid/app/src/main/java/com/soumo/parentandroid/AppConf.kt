package com.soumo.parentandroid

object AppConf {
    // 🔹 Backend API (match Electron app)
    const val RENDER_API_URL = "USE YOUR OWN RENDER API URL"

    // 🔹 Firebase
    const val FIREBASE_DB_URL = "USE YOUR OWN FIREBASE DATABASE URL"
    const val FIREBASE_OTP_PATH = "otp"
    const val FIREBASE_CALLS_PATH = "calls"

    // 🔹 WebRTC
    val STUN_SERVERS = listOf(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302",
        "stun:stun2.l.google.com:19302",
        "stun:stun3.l.google.com:19302",
        "stun:stun4.l.google.com:19302"
    )

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

    // JSON message types
    const val TYPE_LOCATION_UPDATE = "LOCATION_UPDATE"

}
