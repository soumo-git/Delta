package com.soumo.child.id

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.soumo.child.configuration.AppConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import kotlin.math.min
import kotlin.random.asKotlinRandom

/**
 * DeviceIdManager handles the generation and retrieval of a unique device ID.
 * Features:
 * - Uses SecureRandom for cryptographic randomness.
 * - Ensures uniqueness by checking Firebase before committing.
 * - Persists in SharedPreferences + in-memory cache.
 * - Stores metadata (timestamp, device info) in Firebase for traceability.
 * - Retries infinitely on network errors with exponential backoff + jitter.
 */
object DeviceIdManager { // Singleton
    private const val PREF_NAME = "phantom_prefs"
    private const val DEVICE_ID_KEY = "device_id"
    var cachedId: String? = null
    private val lock = Any()

    private val secureRandom = SecureRandom().asKotlinRandom() // Thread-safe wrapper

    suspend fun generateUniqueDeviceId(context: Context): String { // Call from a coroutine
        // Return cached if available
        cachedId?.let { return it } // fast path

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) // multi-process safe
        prefs.getString(DEVICE_ID_KEY, null)?.let { // check local storage
            Log.d("DeviceID", "Loaded existing from prefs: $it")
            synchronized(lock) { cachedId = it } // cache it
            return it
        }

        val newId = generateNewId(prefs) // may take time, do not hold lock
        Log.d("DeviceID", "Generated new unique ID: $newId")
        synchronized(lock) { cachedId = newId } // cache it
        Log.d("DeviceID", "Cached new ID: $newId")
        return newId // return it
    }

    private suspend fun generateNewId( // retry loop until success

        prefs: android.content.SharedPreferences // to store locally
    ): String {
        val db = FirebaseDatabase // remote check + store
            .getInstance(AppConfig.Firebase.DATABASE_URL) // use specific RTDB instance
            .reference.child("devices") // under "devices" node

        var attempt = 0 // for logging + backoff
        while (true) { // retry forever until success
            attempt++ // increment attempt count
            val candidate = random12DigitId() // generate candidate ID
            Log.d("DeviceID", "[$attempt] ♾️ Checking candidate ID: $candidate")
            try { // check existence in Firebase
                val snapshot = db.child(candidate).get().await() // suspend until done
                if (!snapshot.exists()) { // unique!
                    Log.d("DeviceID", "[$attempt] 🎉 Candidate is unique: $candidate")
                    // Build metadata to store with ID
                    val metadata = mapOf(
                        "createdAt" to ServerValue.TIMESTAMP, // server timestamp
                        "lastSeenAt" to ServerValue.TIMESTAMP, // server timestamp
                        // Device info for traceability
                        "manufacturer" to Build.MANUFACTURER, // device info
                        "productName" to Build.PRODUCT, // device info
                        "model" to Build.MODEL, // device info
                        "device" to Build.DEVICE, // device info
                        "product" to Build.PRODUCT, // device info
                        "brand" to Build.BRAND, // device info
                        "sdkInt" to Build.VERSION.SDK_INT, // device info
                        "securityPatch" to (Build.VERSION.SECURITY_PATCH ?: "unknown"), // device info
                        "androidVersion" to Build.VERSION.RELEASE // device info
                    )
                    // Store locally + remotely
                    prefs.edit { putString(DEVICE_ID_KEY, candidate) }
                    db.child(candidate).setValue(metadata).await()
                    Log.d("DeviceID", "✨ Registered new ID with metadata: $candidate")
                    return candidate
                } else {
                    Log.w("DeviceID", "😒 Candidate already exists: $candidate, retrying... 👍🫡")
                }
            } catch (e: Exception) {
                val backoff = calculateBackoff(attempt) // calculate backoff
                Log.e(
                    "DeviceID", "❗ Firebase error: ${e.localizedMessage}, retrying in ${backoff}ms"
                )
                delay(backoff) // wait before retrying
            }
        }
    }

    private fun random12DigitId(): String { // generates a random 12-digit string
        // Each digit is 0-9, total 12 digits
        // Ensures leading zeros are possible by generating digit by digit
        return buildString { // efficient string building
            repeat(12) { // 12 digits
                append(secureRandom.nextInt(0, 10)) // digit 0-9
            }
        }
    }

    private fun calculateBackoff(attempt: Int): Long { // in ms
        // Exponential backoff with jitter, capped at 60s + up to 5s random
        val base = min(60_000L, (1000L * (1 shl min(10, attempt)))) // 1s → 60s
        val jitter = secureRandom.nextInt(0, 5000) // up to 5s extra
        return base + jitter // total backoff
    }

    fun format(id: String) = id.chunked(3).joinToString("-") // e.g. 123456789012 -> 123-456-789-012
}
