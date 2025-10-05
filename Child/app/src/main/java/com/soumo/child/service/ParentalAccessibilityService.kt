package com.soumo.child.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.soumo.child.components.chat.ChatMonitor
import com.soumo.child.components.chat.FacebookExtractor
import com.soumo.child.components.chat.InstagramExtractor
import com.soumo.child.components.chat.MessengerExtractor
import com.soumo.child.components.chat.SnapchatExtractor
import com.soumo.child.components.chat.TelegramExtractor
import com.soumo.child.components.chat.WhatsAppExtractor
import com.soumo.child.utils.AccessibilityState

/**
 * ParentalAccessibilityService
 *
 * - Initializes ChatMonitor and registers per-app extractors on service connect.
 * - Forwards AccessibilityEvents to ChatMonitor (keeps main thread light).
 * - Shuts down ChatMonitor on unbind/destroy.
 *
 * IMPORTANT: This service must NOT perform heavy extraction inside onAccessibilityEvent().
 * ChatMonitor offloads the heavy work to a background coroutine dispatcher.
 */
class ParentalAccessibilityService : AccessibilityService() {

    @Suppress("PrivatePropertyName")
    private val TAG = "ParentalA11y"

    override fun onServiceConnected() {
        super.onServiceConnected()

        // update basic state counters / heartbeat
        AccessibilityState.isBound.set(true)
        AccessibilityState.onServiceConnectedCount.incrementAndGet()
        AccessibilityState.lastConnectedAtMs.set(System.currentTimeMillis())
        AccessibilityState.lastHeartbeatAtMs.set(System.currentTimeMillis())
        Log.d(TAG, "Service connected")

        // Configure robust defaults for breadth of apps
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = (
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                    )
            // keep notifications flowing quickly but don't hammer
            notificationTimeout = 50
            @Suppress("ControlFlowWithEmptyBody")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // future: add advanced flags if needed
            }
        }

        // Initialize ChatMonitor (singleton) and register per-app extractors.
        // ChatMonitor will handle heavy extraction off the Accessibility thread.
        try {
            ChatMonitor.init(applicationContext)

            // Register the extractors you implemented. If an extractor is missing,
            // ChatMonitor falls back to the generic extractor.
            ChatMonitor.instance.registerExtractor("com.whatsapp", WhatsAppExtractor())
            ChatMonitor.instance.registerExtractor("com.instagram.android", InstagramExtractor())
            ChatMonitor.instance.registerExtractor("org.telegram.messenger", TelegramExtractor())
            ChatMonitor.instance.registerExtractor("com.snapchat.android", SnapchatExtractor())
            ChatMonitor.instance.registerExtractor("com.facebook.orca", MessengerExtractor())
            ChatMonitor.instance.registerExtractor("com.facebook.katana", FacebookExtractor())

            Log.d(TAG, "ChatMonitor initialized and extractors registered")

        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing ChatMonitor", t)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        AccessibilityState.isBound.set(false)
        AccessibilityState.onUnbindCount.incrementAndGet()
        Log.w(TAG, "Service unbound - shutting down ChatMonitor")

        // Best-effort shutdown of ChatMonitor (cancels coroutines); ChatMonitor.init() will
        // recreate on next connection if needed.
        try {
            ChatMonitor.instance.shutdown()
        } catch (t: Throwable) {
            Log.w(TAG, "ChatMonitor shutdown error", t)
        }

        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "Service destroyed - cleaning up")
        try {
            ChatMonitor.instance.shutdown()
        } catch (_: Throwable) {
            // ignore - defensive
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Extremely lightweight: update heartbeat + forward the event to ChatMonitor.
        // Do NOT do heavy traversal or blocking work here.
        try {
            AccessibilityState.lastHeartbeatAtMs.set(System.currentTimeMillis())

            if (event == null) return

            // Forward the event to ChatMonitor for async processing.
            // ChatMonitor will filter and handle only the event types it cares about.
            try {
                ChatMonitor.instance.onAccessibilityEvent(event)
            } catch (t: Throwable) {
                // If ChatMonitor isn't initialized for some reason, log and continue.
                Log.e(TAG, "Error forwarding event to ChatMonitor", t)
            }
        } catch (t: Throwable) {
            AccessibilityState.eventExceptionCount.incrementAndGet()
            Log.e(TAG, "Event handling error", t)
        }
    }
}
