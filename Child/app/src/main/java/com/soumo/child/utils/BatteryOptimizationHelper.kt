package com.soumo.child.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri

/** Helper object to manage battery optimization and auto-start settings.
 * This includes checking if the app is ignoring battery optimizations,
 * requesting to disable battery optimizations, and opening relevant settings screens.
 * It also provides manufacturer-specific instructions for enabling auto-start.
 */

object BatteryOptimizationHelper {
    
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = context.packageName
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }
    
    fun requestDisableBatteryOptimization(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:${context.packageName}".toUri()
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d("BatteryOptimization", "Requested to disable battery optimization")
        } catch (e: Exception) {
            Log.e("BatteryOptimization", "Error requesting battery optimization disable", e)
        }
    }
    
    fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d("BatteryOptimization", "Opened battery optimization settings")
        } catch (e: Exception) {
            Log.e("BatteryOptimization", "Error opening battery optimization settings", e)
        }
    }
    
    fun openAutoStartSettings(context: Context) {
        try {
            // Common auto-start settings for various manufacturers
            val autoStartIntents = listOf(
                Intent("miui.intent.action.OP_AUTO_START").apply {
                    setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                },
                Intent("huawei.intent.action.STARTUP_MANAGER").apply {
                    setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
                },
                Intent("oppo.intent.action.OP_AUTO_START").apply {
                    setClassName("com.coloros.safecenter", "com.coloros.safecenter.sysclear.ui.mainpage.MainActivity")
                },
                Intent("vivo.intent.action.VIVO_AUTO_START").apply {
                    setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                },
                Intent("samsung.intent.action.AUTO_START").apply {
                    setClassName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
                }
            )
            
            for (intent in autoStartIntents) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Log.d("AutoStart", "Opened auto-start settings for manufacturer")
                    return
                } catch (_: Exception) {
                    Log.d("AutoStart", "Failed to open auto-start settings for this manufacturer")
                }
            }
            
            // Fallback to general app settings
            val generalIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(generalIntent)
            Log.d("AutoStart", "Opened general app settings as fallback")
            
        } catch (e: Exception) {
            Log.e("AutoStart", "Error opening auto-start settings", e)
        }
    }
    
    fun getDeviceManufacturer(): String {
        return Build.MANUFACTURER.lowercase()
    }
    
    fun shouldShowAutoStartInstructions(): Boolean {
        val manufacturer = getDeviceManufacturer()
        return manufacturer in listOf("xiaomi", "huawei", "oppo", "vivo", "samsung", "oneplus")
    }
    
    fun getAutoStartInstructions(): String {
        val manufacturer = getDeviceManufacturer()
        return when (manufacturer) {
            "xiaomi" -> "Go to Security > Permissions > Autostart and enable for this app"
            "huawei" -> "Go to Settings > Apps > Advanced > Ignore battery optimization and enable for this app"
            "oppo" -> "Go to Settings > Apps > Running in background and enable for this app"
            "vivo" -> "Go to Settings > Apps > Running in background and enable for this app"
            "samsung" -> "Go to Settings > Apps > Battery > Allow background activity and enable for this app"
            "oneplus" -> "Go to Settings > Apps > Special app access > Battery optimization and set to 'Don't optimize'"
            else -> "Go to Settings > Apps > Battery optimization and disable for this app"
        }
    }
} 