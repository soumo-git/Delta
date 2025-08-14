package com.soumo.child.calllog

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.util.Log
import org.webrtc.DataChannel
import org.json.JSONObject
import java.nio.ByteBuffer

class CallLogSharing(
    private val context: Context,
    private val dataChannel: DataChannel
) {
    private val handler = Handler(Looper.getMainLooper())
    private val sentTimestamps = mutableSetOf<Long>()
    private var lastSentTimestamp: Long = 0L // In-memory only, resets on new connection
    private var callLogObserver: ContentObserver? = null

    @SuppressLint("MissingPermission")
    fun startSharing() {
        Log.d(TAG, "Starting Call Log sharing...")
        sendLatestCallLogs()
        // Register observer for new call logs
        if (callLogObserver == null) {
            callLogObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    Log.d(TAG, "Call log content changed, checking for new logs...")
                    sendLatestCallLogs()
                }
            }
            context.contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI,
                true,
                callLogObserver!!
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendLatestCallLogs() {
        var maxTimestamp = lastSentTimestamp
        val cursor: Cursor? = try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null,
                if (lastSentTimestamp > 0L) "date > ?" else null,
                if (lastSentTimestamp > 0L) arrayOf(lastSentTimestamp.toString()) else null,
                "date DESC"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query CallLog content provider", e)
            null
        }
        if (cursor == null) {
            Log.w(TAG, "Cursor is null. No call logs retrieved.")
            return
        }
        try {
            val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
            val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
            val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
            if (numberIdx == -1 || dateIdx == -1 || durationIdx == -1 || typeIdx == -1) {
                Log.e(TAG, "One or more required columns not found in CallLog cursor")
                return
            }
            var count = 0
            while (cursor.moveToNext() && count < 200) {
                val timestamp = cursor.getLong(dateIdx)
                if (sentTimestamps.contains(timestamp)) continue
                val callJson = JSONObject().apply {
                    put("type", "calllog")
                    put("timestamp", timestamp)
                    put("number", cursor.getString(numberIdx) ?: "")
                    put("name", if (nameIdx != -1) cursor.getString(nameIdx) ?: "" else "")
                    put("duration", cursor.getLong(durationIdx))
                    put("call_type", when (cursor.getInt(typeIdx)) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE -> "missed"
                        CallLog.Calls.VOICEMAIL_TYPE -> "voicemail"
                        CallLog.Calls.REJECTED_TYPE -> "rejected"
                        CallLog.Calls.BLOCKED_TYPE -> "blocked"
                        CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> "external"
                        else -> "unknown"
                    })
                }
                sendViaDataChannel(callJson)
                sentTimestamps.add(timestamp)
                if (timestamp > maxTimestamp) maxTimestamp = timestamp
                count++
            }
            lastSentTimestamp = maxTimestamp
            Log.d(TAG, "Call log sharing completed. Sent ${sentTimestamps.size} unique logs. Latest timestamp: $maxTimestamp")
        } catch (e: Exception) {
            Log.e(TAG, "Error while sharing call logs", e)
        } finally {
            cursor.close()
        }
    }

    fun stopSharing() {
        Log.d(TAG, "Stopping Call Log sharing. Clearing internal logs.")
        sentTimestamps.clear()
        // Unregister observer
        if (callLogObserver != null) {
            context.contentResolver.unregisterContentObserver(callLogObserver!!)
            callLogObserver = null
        }
    }
    private fun sendViaDataChannel(json: JSONObject) {
        try {
            if (dataChannel.state() != DataChannel.State.OPEN) {
                Log.w(TAG, "DataChannel is not open. Skipping send.")
                return
            }
            val message = json.toString()
            val maxChunkSize = 8192
            val bytes = message.toByteArray(Charsets.UTF_8)
            for (i in bytes.indices step maxChunkSize) {
                val end = (i + maxChunkSize).coerceAtMost(bytes.size)
                val chunk = ByteBuffer.wrap(bytes, i, end - i)
                val sent = dataChannel.send(DataChannel.Buffer(chunk, false))
                if (!sent) {
                    Log.w(TAG, "Failed to send call log chunk [$i - $end]")
                    break
                }
            }
            Log.d(TAG, "Call log sent over DataChannel (${bytes.size} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending call log via DataChannel", e)
        }
    }
    companion object {
        private const val TAG = "CallLogSharing"
    }
} 