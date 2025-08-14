package com.soumo.child.id

import android.content.Context
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import androidx.core.content.edit

object DeviceIdManager {
    private const val PREF_NAME = "phantom_prefs"
    private const val DEVICE_ID_KEY = "device_id"
    private var cachedId: String? = null
    private val lock = Any()

    suspend fun generateUniqueDeviceId(context: Context): String {
        // Check cache first
        cachedId?.let {
            Log.d("DeviceID", "Using cached ID: $it")
            return it
        }
        
        // Check SharedPreferences first
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.getString(DEVICE_ID_KEY, null)?.let {
            Log.d("DeviceID", "Loaded existing from prefs: $it")
            synchronized(lock) {
                cachedId = it
            }
            return it
        }

        // Generate new ID if none exists
        val newId = generateNewId(context, prefs)
        synchronized(lock) {
            cachedId = newId
        }
        return newId
    }

    private suspend fun generateNewId(context: Context, prefs: android.content.SharedPreferences): String {
        // ✅ Only pass URL to getInstance, NOT to .child()
        val db = FirebaseDatabase
            .getInstance(AppConfig.Firebase.DATABASE_URL)
            .reference.child("devices") // ✅ this is a valid path now

        while (true) {
            val candidate = (100_000_000_000..999_999_999_999).random().toString()
            Log.d("DeviceID", "Checking ID: $candidate")

            try {
                val snapshot = db.child(candidate).get().await()
                if (!snapshot.exists()) {
                    prefs.edit { putString(DEVICE_ID_KEY, candidate) }
                    db.child(candidate).setValue(true)
                    Log.d("DeviceID", "Registered new ID: $candidate")
                    return candidate
                }
            } catch (e: Exception) {
                Log.e("DeviceID", "Firebase error: ${e.localizedMessage}")
                break
            }
        }

        throw IllegalStateException("Failed to generate device ID.")
    }

    fun format(id: String) = id.chunked(3).joinToString("-")
    
    fun clearCache() {
        synchronized(lock) {
            cachedId = null
            Log.d("DeviceID", "Cache cleared")
        }
    }
}
