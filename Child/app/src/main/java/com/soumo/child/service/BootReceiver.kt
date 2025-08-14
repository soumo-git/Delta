package com.soumo.child.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "Received intent: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            "android.intent.action.MY_PACKAGE_REPLACED",
            "android.intent.action.PACKAGE_REPLACED" -> {
                Log.d("BootReceiver", "Device boot/restart completed, starting BackgroundService")
                
                // Start service immediately with retries
                startServiceWithRetry(context)
            }
        }
    }
    
    private fun startServiceWithRetry(context: Context) {
        // Try immediate start
        try {
            BackgroundService.startService(context)
            Log.d("BootReceiver", "BackgroundService started immediately")
            
            // Schedule persistent job for Android 8+
            com.soumo.child.PersistentJobService.scheduleJob(context)
            Log.d("BootReceiver", "PersistentJobService scheduled")

        } catch (e: Exception) {
            Log.e("BootReceiver", "Immediate start failed, scheduling retry", e)
            
            // Schedule retry with exponential backoff
            scheduleRetry(context, 5000)
        }
    }
    
    private fun scheduleRetry(context: Context, delayMs: Long) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                BackgroundService.startService(context)
                Log.d("BootReceiver", "BackgroundService started after retry")
            } catch (e: Exception) {
                Log.e("BootReceiver", "Retry failed, scheduling next retry", e)
                if (delayMs < 60000) { // Max 1 minute delay
                    scheduleRetry(context, delayMs * 2)
                }
            }
        }, delayMs)
    }
}