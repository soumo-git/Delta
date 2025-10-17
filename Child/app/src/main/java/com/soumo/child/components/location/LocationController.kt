package com.soumo.child.components.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.soumo.child.commands.LocationHandler
import org.json.JSONObject
import org.webrtc.DataChannel
import java.nio.ByteBuffer

/**
 * LocationController is responsible for retrieving device location updates
 * and sending them as JSON over the provided WebRTC DataChannel.
 *
 * It implements LocationHandler so it can be wired directly into
 * CommandHandlerImpl's location flow.
 */
class LocationController(
    private val context: Context,
    private val dataChannel: DataChannel
) : LocationHandler {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @Volatile
    private var locationCallback: LocationCallback? = null

    @Volatile
    private var isTracking: Boolean = false

    @Volatile
    private var lastSentLocation: Location? = null

    @Volatile
    private var lastSentAtMs: Long = 0L

    /** Start continuous high-accuracy location tracking and send updates over DataChannel. */
    @SuppressLint("MissingPermission")
    override fun startLocationTracking() {
        if (isTracking) {
            Log.d(TAG, "Location tracking already active")
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            Log.w(TAG, "ACCESS_FINE_LOCATION not granted; cannot start tracking")
            return
        }

        // Send last known location quickly if available
        try {
            fusedClient.lastLocation
                .addOnSuccessListener { loc ->
                    loc?.let { sendLocationJson(it, source = "lastKnown") }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to get last known location", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting last location", e)
        }

        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setMaxUpdateDelayMillis(MAX_UPDATE_DELAY_MS)
            .setMinUpdateDistanceMeters(MIN_DISPLACEMENT_METERS)
            .setWaitForAccurateLocation(false)
            .build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                try {
                    result.lastLocation?.let { maybeSend(it, source = "update") }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling location update", e)
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(req, cb, Looper.getMainLooper())
            locationCallback = cb
            isTracking = true
            Log.d(TAG, "Location tracking started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing location permission when starting updates", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location updates", e)
        }
    }

    /** Stop location updates and clean up callback. */
    override fun stopLocationTracking() {
        if (!isTracking) {
            Log.d(TAG, "Location tracking already stopped")
            return
        }
        try {
            locationCallback?.let { fusedClient.removeLocationUpdates(it) }
            locationCallback = null
            isTracking = false
            Log.d(TAG, "Location tracking stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop location updates", e)
        }
    }

    /** Send only if moved enough or sufficient time elapsed to mimic smooth real-time like maps. */
    private fun maybeSend(location: Location, source: String) {
        val now = System.currentTimeMillis()
        val movedEnough = lastSentLocation?.distanceTo(location)?.let { it >= MIN_SEND_DISTANCE_METERS } ?: true
        val timeElapsed = now - lastSentAtMs >= MAX_SEND_INTERVAL_MS
        if (movedEnough || timeElapsed) {
            sendLocationJson(location, source)
            lastSentLocation = location
            lastSentAtMs = now
        }
    }

    /** Serialize a Location to JSON and send via DataChannel.
     * Matches ParentElectronApp schema: { type: 'LOCATION_UPDATE', coords: [lat, lng], accuracy, timestamp }
     */
    private fun sendLocationJson(location: Location, source: String) {
        if (dataChannel.state() != DataChannel.State.OPEN) {
            Log.w(TAG, "DataChannel not open; skipping location send")
            return
        }
        try {
            val json = JSONObject().apply {
                put("type", "LOCATION_UPDATE")
                put("coords", org.json.JSONArray().apply {
                    put(location.latitude)
                    put(location.longitude)
                })
                if (location.hasAccuracy()) put("accuracy", location.accuracy)
                put("timestamp", location.time)
            }
            sendViaDataChannel(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize/send location", e)
        }
    }

    private fun sendViaDataChannel(json: JSONObject) {
        try {
            if (dataChannel.state() != DataChannel.State.OPEN) {
                Log.w(TAG, "DataChannel is not open. Skipping send.")
                return
            }
            val message = json.toString()
            val bytes = message.toByteArray(Charsets.UTF_8)
            val maxChunk = 8192
            var sentAll = true
            for (i in bytes.indices step maxChunk) {
                val end = (i + maxChunk).coerceAtMost(bytes.size)
                val chunk = ByteBuffer.wrap(bytes, i, end - i)
                val ok = dataChannel.send(DataChannel.Buffer(chunk, false))
                if (!ok) {
                    sentAll = false
                    Log.w(TAG, "Failed to send location chunk [$i-$end]")
                    break
                }
            }
            if (sentAll) {
                Log.d(TAG, "Location JSON sent (${bytes.size} bytes)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending location via DataChannel", e)
        }
    }

    companion object {
        private const val TAG = "LocationController"
        // Real-time tracking like Google Maps
        private const val UPDATE_INTERVAL_MS = 1_000L
        private const val MIN_UPDATE_INTERVAL_MS = 500L
        private const val MAX_UPDATE_DELAY_MS = 2_000L
        private const val MIN_DISPLACEMENT_METERS = 5f
        private const val MIN_SEND_DISTANCE_METERS = 3f
        private const val MAX_SEND_INTERVAL_MS = 2_000L
    }
}

