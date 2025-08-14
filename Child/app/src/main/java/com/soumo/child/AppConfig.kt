package com.soumo.child

/**
 * Application configuration constants
 * Centralized configuration for better maintainability
 */
object AppConfig {
    
    // WebRTC Configuration
    object WebRTC {
        const val ICE_TIMEOUT_MS = 30000L
        const val RENEGOTIATION_TIMEOUT_MS = 10000L
        const val DATA_CHANNEL_BUFFER_SIZE = 65536
        
        // STUN Server Configuration (Google's public free STUN servers)
        const val STUN_SERVER = "stun:stun.l.google.com:19302"
    }
    
    // Location Configuration
    object Location {
        const val UPDATE_INTERVAL_MS = 5000L
        const val MIN_UPDATE_INTERVAL_MS = 2000L
        const val MAX_UPDATE_AGE_MS = 10000L
        const val HIGH_ACCURACY_PRIORITY = true
    }
    
    // Firebase Configuration
    object Firebase {
        // Realtime Database root URL (e.g., https://<project-id>-default-rtdb.<region>.firebasedatabase.app)
        const val DATABASE_URL = "YOUR_RTDATABASE_URL"
        const val CALLS_PATH = "calls"
        const val OFFER_PATH = "offer"
        const val ANSWER_PATH = "answer"
        const val CANDIDATES_PATH = "candidates"
    }
    
    // Permission Request Codes
    object Permissions {
        const val CAMERA_REQUEST = 1001
        const val MICROPHONE_REQUEST = 1002
        const val NOTIFICATION_REQUEST = 1003
        const val LOCATION_REQUEST = 1004
    }
    
    // Data Channel Configuration
    object DataChannel {
        const val LABEL = "cmd"
        const val ORDERED = true
        const val MAX_RETRANSMITS = 3
        const val MAX_PACKET_LIFE_TIME = 1000
    }
    
    // Screen Capture Configuration
    object ScreenCapture {
        const val NOTIFICATION_ID = 1001
        const val FOREGROUND_SERVICE_TYPE = "mediaProjection"
        const val NOTIFICATION_CHANNEL_ID = "screen_capture"
        const val NOTIFICATION_CHANNEL_NAME = "Screen Capture"
    }
    
    // Logging Configuration
    object Logging {
        const val TAG_PREFIX = "ChildApp"
        const val ENABLE_VERBOSE_LOGGING = true
        const val ENABLE_WEBRTC_LOGGING = true
    }
    
    // Device ID Configuration
    object DeviceId {
        const val LENGTH = 12
        const val FORMAT_GROUP_SIZE = 4
        const val FORMAT_SEPARATOR = "-"
    }
    
    // Error Messages
    object ErrorMessages {
        const val CAMERA_START_FAILED = "Failed to start camera"
        const val CAMERA_STOP_FAILED = "Failed to stop camera"
        const val MIC_START_FAILED = "Failed to start microphone"
        const val MIC_STOP_FAILED = "Failed to stop microphone"
        const val SCREEN_START_FAILED = "Failed to start screen capture"
        const val SCREEN_STOP_FAILED = "Failed to stop screen capture"
        const val LOCATION_START_FAILED = "Failed to start location tracking"
        const val LOCATION_STOP_FAILED = "Failed to stop location tracking"
        const val PERMISSION_DENIED = "Permission denied"
        const val CONNECTION_FAILED = "Connection failed"
        const val UNKNOWN_COMMAND = "Unknown command"
    }
    
    // Success Messages
    object SuccessMessages {
        const val CAMERA_STARTED = "Camera started successfully"
        const val CAMERA_STOPPED = "Camera stopped successfully"
        const val MIC_STARTED = "Microphone started successfully"
        const val MIC_STOPPED = "Microphone stopped successfully"
        const val SCREEN_STARTED = "Screen capture started successfully"
        const val SCREEN_STOPPED = "Screen capture stopped successfully"
        const val LOCATION_STARTED = "Location tracking started successfully"
        const val LOCATION_STOPPED = "Location tracking stopped successfully"
    }

    // WebRTC Constraint Keys and Track Labels
    object WebRTCKeys {
        const val ECHO_CANCELLATION = "googEchoCancellation"
        const val AUTO_GAIN_CONTROL = "googAutoGainControl"
        const val NOISE_SUPPRESSION = "googNoiseSuppression"
        const val HIGHPASS_FILTER = "googHighpassFilter"
        const val TYPING_NOISE_DETECTION = "googTypingNoiseDetection"
        const val AUDIO_MIRRORING = "googAudioMirroring"
        const val MIC_TRACK_LABEL = "MIC_TRACK"
    }
} 