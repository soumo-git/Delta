package com.soumo.child

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.soumo.child.service.BackgroundService

class BootServiceRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootServiceRestart", "Received intent: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                
                // Start service with retry mechanism
                startServiceWithRetry(context)
            }
        }
    }
    
    private fun startServiceWithRetry(context: Context) {
        try {
            BackgroundService.startService(context)
            Log.d("BootServiceRestart", "BackgroundService started successfully")
        } catch (e: Exception) {
            Log.e("BootServiceRestart", "Failed to start BackgroundService, scheduling retry", e)
            // Schedule retry with exponential backoff
            scheduleRetry(context)
        }
    }
    
    private fun scheduleRetry(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, BootServiceRestartReceiver::class.java).apply {
            action = "com.soumo.child.SERVICE_RESTART_RETRY"
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context, 0, intent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        // Retry after 30 seconds - use setAndAllowWhileIdle for compatibility
        val retryTime = System.currentTimeMillis() + 30000
        alarmManager.setAndAllowWhileIdle(
            android.app.AlarmManager.RTC_WAKEUP,
            retryTime,
            pendingIntent
        )
    }
}