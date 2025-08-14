package com.soumo.child

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.soumo.child.service.BackgroundService

/**
 * Starts BackgroundService when the user unlocks the device (ACTION_USER_PRESENT),
 * but only if the service is not already running.
 */
class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_USER_PRESENT == intent.action) {
            Log.d("UnlockReceiver", "User present detected; ensuring service running")
            try {
                // BackgroundService.startService has an isServiceRunning guard
                BackgroundService.startService(context.applicationContext)
            } catch (e: Exception) {
                Log.e("UnlockReceiver", "Failed to start BackgroundService on unlock", e)
            }
        }
    }
}

