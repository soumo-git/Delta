package com.soumo.child.components.chat

/**
 * Implement this in your BackgroundService or MainActivity (the object that owns the DataChannel).
 * ChatMonitor will call send() for live updates.
 */
interface DataChannelClient {
    /**
     * Send a UTF-8 JSON string over the existing WebRTC DataChannel.
     * Return true if accepted for send; false if channel not ready.
     */
    fun send(jsonPayload: String): Boolean
}
