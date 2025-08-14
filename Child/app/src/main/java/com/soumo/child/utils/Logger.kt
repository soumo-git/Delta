package com.soumo.child.utils

import android.util.Log
import com.soumo.child.AppConfig

/**
 * Enhanced logging utility for consistent logging throughout the app
 */
object Logger {
    
    private const val TAG_PREFIX = AppConfig.Logging.TAG_PREFIX
    private const val MAX_TAG_LENGTH = 23 // Android's limit for tag length
    
    /**
     * Create a tag with prefix and component name
     */
    private fun createTag(component: String): String {
        val tag = "$TAG_PREFIX-$component"
        return if (tag.length > MAX_TAG_LENGTH) {
            tag.substring(0, MAX_TAG_LENGTH)
        } else {
            tag
        }
    }
    
    /**
     * Log verbose message
     */
    fun v(component: String, message: String, throwable: Throwable? = null) {
        if (AppConfig.Logging.ENABLE_VERBOSE_LOGGING) {
            val tag = createTag(component)
            if (throwable != null) {
                Log.v(tag, message, throwable)
            } else {
                Log.v(tag, message)
            }
        }
    }
    
    /**
     * Log debug message
     */
    fun d(component: String, message: String, throwable: Throwable? = null) {
        val tag = createTag(component)
        if (throwable != null) {
            Log.d(tag, message, throwable)
        } else {
            Log.d(tag, message)
        }
    }
    
    /**
     * Log info message
     */
    fun i(component: String, message: String, throwable: Throwable? = null) {
        val tag = createTag(component)
        if (throwable != null) {
            Log.i(tag, message, throwable)
        } else {
            Log.i(tag, message)
        }
    }
    
    /**
     * Log warning message
     */
    fun w(component: String, message: String, throwable: Throwable? = null) {
        val tag = createTag(component)
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }
    
    /**
     * Log error message
     */
    fun e(component: String, message: String, throwable: Throwable? = null) {
        val tag = createTag(component)
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
    
    /**
     * Log WebRTC specific messages
     */
    fun webrtc(level: String, message: String, throwable: Throwable? = null) {
        if (AppConfig.Logging.ENABLE_WEBRTC_LOGGING) {
            when (level.lowercase()) {
                "v" -> v("WebRTC", message, throwable)
                "d" -> d("WebRTC", message, throwable)
                "i" -> i("WebRTC", message, throwable)
                "w" -> w("WebRTC", message, throwable)
                "e" -> e("WebRTC", message, throwable)
                else -> d("WebRTC", message, throwable)
            }
        }
    }
    
    /**
     * Log command execution
     */
    fun command(command: String, status: String, details: String? = null) {
        val message = "Command: $command - Status: $status${details?.let { " - $it" } ?: ""}"
        i("Command", message)
    }
    
    /**
     * Log permission events
     */
    fun permission(permission: String, granted: Boolean, details: String? = null) {
        val status = if (granted) "GRANTED" else "DENIED"
        val message = "Permission: $permission - $status${details?.let { " - $it" } ?: ""}"
        if (granted) {
            i("Permission", message)
        } else {
            w("Permission", message)
        }
    }
    
    /**
     * Log connection events
     */
    fun connection(event: String, details: String? = null) {
        val message = "Connection: $event${details?.let { " - $it" } ?: ""}"
        i("Connection", message)
    }
    
    /**
     * Log stream events
     */
    fun stream(type: String, event: String, details: String? = null) {
        val message = "Stream ($type): $event${details?.let { " - $it" } ?: ""}"
        i("Stream", message)
    }
    
    /**
     * Log location events
     */
    fun location(event: String, details: String? = null) {
        val message = "Location: $event${details?.let { " - $it" } ?: ""}"
        i("Location", message)
    }
    
    /**
     * Log data channel events
     */
    fun dataChannel(event: String, details: String? = null) {
        val message = "DataChannel: $event${details?.let { " - $it" } ?: ""}"
        i("DataChannel", message)
    }
    
    /**
     * Log Firebase events
     */
    fun firebase(event: String, details: String? = null) {
        val message = "Firebase: $event${details?.let { " - $it" } ?: ""}"
        i("Firebase", message)
    }
    
    /**
     * Log performance metrics
     */
    fun performance(metric: String, value: Any, unit: String? = null) {
        val message = "Performance: $metric = $value${unit?.let { " $it" } ?: ""}"
        d("Performance", message)
    }
    
    /**
     * Log error with context
     */
    fun error(component: String, operation: String, error: Throwable, context: String? = null) {
        val message = "Error in $operation${context?.let { " ($it)" } ?: ""}: ${error.message}"
        e(component, message, error)
    }
    
    /**
     * Log success with context
     */
    fun success(component: String, operation: String, context: String? = null) {
        val message = "Success: $operation${context?.let { " ($it)" } ?: ""}"
        i(component, message)
    }
} 