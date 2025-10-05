package com.soumo.parentandroid.webrtc

import android.content.Context
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.soumo.parentandroid.AppConf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.webrtc.RTCStatsReport
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

/**
 * Parent-side WebRTC manager:
 * - Listens for offer from child at calls/<ID>/offer
 * - Creates non-trickle answer, waits for ICE gathering, then writes to calls/<ID>/answer
 * - Exposes connection state and DataChannel
 * - No trickle ICE candidates are exchanged
 */
class ParentPeerManager( // Caller provides CoroutineScope for async ops
    private val context: Context, // Application or Activity context
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    sealed class ConnectionState { // Observability of connection state
        data object Idle : ConnectionState() // Before start()
        data object Connecting : ConnectionState() // After start(), before CONNECTED/FAILED/CLOSED
        data object Connected : ConnectionState() // PeerConnection connected, DataChannel may be open
        data class Failed(val reason: String?) : ConnectionState() // Fatal error, must recreate PeerConnection
        data object Closed : ConnectionState() // Closed by us or remote, can recreate PeerConnection
    }

    private val logTag = "ParentPeerManager" // For logging

    private var peerConnectionFactory: PeerConnectionFactory? = null // WebRTC factory
    private var peerConnection: PeerConnection? = null // Active PeerConnection
    private var eglBase: EglBase? = null // For video rendering if needed

    private var firebaseJob: Job? = null // For Firebase listener coroutine

    private var offerPathListenerAttachedForId: String? = null // To prevent duplicate listeners
    private var currentChildId: String? = null // Current child ID being handled

    // DataChannel provided by child (offerer) via onDataChannel callback after connection
    private var dataChannel: DataChannel? = null // Null when not open

    // Observability of connection state
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle) // Initial state
    val connectionState: StateFlow<ConnectionState> = _connectionState // Expose as read-only

    private val _dataChannelEvents = MutableSharedFlow<String>( // Incoming messages from child
        replay = 0, // No replay; only new messages
        extraBufferCapacity = 1024, // Buffer up to 1024 messages
        onBufferOverflow = BufferOverflow.DROP_OLDEST // Drop oldest if overflow (shouldn't happen
    )
    val dataChannelEvents: SharedFlow<String> = _dataChannelEvents // Expose as read-only

    // Inbound audio level (0.0 – 1.0). Updated from WebRTC stats. 0 if no audio.
    private val _inboundAudioLevel = MutableStateFlow(0f) // Initial level
    val inboundAudioLevel: StateFlow<Float> // Expose as read-only
        get() = _inboundAudioLevel // Getter

    private var statsPollingJob: Job? = null // For periodic stats polling

    // Streams (camera/screen/mic) can be surfaced later through callbacks if needed
    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null) // Remote video track if any
    val remoteVideoTrack: StateFlow<VideoTrack?> // Expose as read-only
        get() = _remoteVideoTrack // Getter

    fun eglContext(): EglBase.Context? = eglBase?.eglBaseContext // Expose EGL context if needed

    fun initializePeerConnectionFactory() { // Call once before creating PeerConnection
        if (peerConnectionFactory != null) return // Already initialized

        eglBase = EglBase.create() // Create EGL context for video if needed

        val initOptions = PeerConnectionFactory.InitializationOptions // Init WebRTC
            .builder(context) // Application context
            .createInitializationOptions() // Build options
        PeerConnectionFactory.initialize(initOptions) // Initialize WebRTC

        val encoderFactory = DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true) // Enable hardware acceleration
        val decoderFactory = DefaultVideoDecoderFactory(eglBase!!.eglBaseContext) // Decoder factory for video decoding

        peerConnectionFactory = PeerConnectionFactory.builder() // Build factory
            .setVideoEncoderFactory(encoderFactory) // Set encoder factory
            .setVideoDecoderFactory(decoderFactory) // Set decoder factory
            .setOptions(PeerConnectionFactory.Options().apply {
                // Enable/disable options as needed
                // E.g., disable encryption (not recommended for production)
                // disableEncryption = true
                // Enable/disable network monitoring
                disableNetworkMonitor = false
            })
            .createPeerConnectionFactory() // Create factory
        Log.d(logTag, "PeerConnectionFactory initialized")
    }

    fun createPeerConnection(): PeerConnection? { // Call to create or recreate PeerConnection
        if (peerConnectionFactory == null) {
            Log.e(logTag, "PeerConnectionFactory not initialized")
            return null
        }
        if (peerConnection != null) {
            Log.d(logTag, "PeerConnection already exists")
            return peerConnection // Already exists
        }
        val factory = peerConnectionFactory ?: return null // Shouldn't be null here
        val rtcConfig = PeerConnection.RTCConfiguration( // ICE servers
            AppConf.STUN_SERVERS.map { stun -> PeerConnection.IceServer.builder(stun).createIceServer() }
        )
        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer { // PeerConnection callbacks
            override fun onIceCandidate(c: IceCandidate) { /* Non-trickle: ignore */ } // No trickle ICE
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {} // Not used
            override fun onSignalingChange(s: PeerConnection.SignalingState?) {} // Not used
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) { // ICE connection state changes
                Log.d(logTag, "ICE state: $s") // Log state
            }
            override fun onIceConnectionReceivingChange(b: Boolean) {} // Not used
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) { // ICE gathering state changes
                Log.d(logTag, "ICE gathering: $s") // Log state
            }
            override fun onAddStream(ms: org.webrtc.MediaStream?) {} // Deprecated
            override fun onRemoveStream(ms: org.webrtc.MediaStream?) {} // Deprecated
            override fun onDataChannel(channel: DataChannel) { // DataChannel created by child
                if (dataChannel != null) {
                    Log.w(logTag, "DataChannel already exists; replacing")
                    try { dataChannel?.close() } catch (_: Throwable) {}
                    dataChannel = null
                }
                dataChannel = channel // Store reference
                Log.d(logTag, "DataChannel received: ${channel.label()}") // Log label
                // Set up DataChannel observer for messages and state changes
                channel.registerObserver(object : DataChannel.Observer { // DataChannel events
                    override fun onStateChange() { // State changes
                        Log.d(logTag, "DataChannel state: ${channel.state()}") // Log state
                        // Clear reference if closed
                        if (channel.state() == DataChannel.State.CLOSED) { // Closed
                            Log.d(logTag, "DataChannel closed") // Log closure
                            dataChannel = null // Clear reference
                        }
                    }
                    override fun onMessage(buffer: DataChannel.Buffer?) { // Message received
                        if (buffer == null) return // Null check
                        if (!buffer.binary) { // We expect text messages only
                            Log.w(logTag, "Ignoring binary message on DataChannel") // Warn
                            return
                        }
                        // Copy remaining bytes into a fresh array BEFORE reading strings
                        val remaining = buffer.data.remaining() // Remaining bytes
                        if (remaining <= 0) return // No data
                        val bytes = ByteArray(remaining) // Allocate array
                        buffer.data.get(bytes) // Copy bytes
                        // Decode UTF-8 string safely
                        val text = try {
                            String(bytes, Charsets.UTF_8) // Decode string
                        } catch (_: Throwable) {
                            return
                        }
                        // Respond to child's health-check pings to enable its reconnection logic
                        if (text == "PING_CHILD") {
                            try {
                                val pong = java.nio.ByteBuffer.wrap("PONG_PARENT".toByteArray(Charsets.UTF_8))
                                channel.send(DataChannel.Buffer(pong, false))
                            } catch (_: Throwable) {}
                        }
                        externalScope.launch { _dataChannelEvents.emit(text) }
                    }
                    override fun onBufferedAmountChange(previousAmount: Long) {}
                })
            }
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(r: RtpReceiver?, s: Array<out org.webrtc.MediaStream>) {
                val kind = r?.track()?.kind()
                Log.d(logTag, "onAddTrack: $kind ${r?.track()?.id()}")
                if (kind == "video") {
                    val vt = r.track() as? VideoTrack
                    _remoteVideoTrack.value = vt
                }
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                Log.d(logTag, "Connection state: $newState")
                when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> _connectionState.value = ConnectionState.Connected
                    PeerConnection.PeerConnectionState.FAILED -> _connectionState.value = ConnectionState.Failed("ICE/DTLS failed")
                    PeerConnection.PeerConnectionState.CLOSED -> _connectionState.value = ConnectionState.Closed
                    else -> {}
                }
                if (newState == PeerConnection.PeerConnectionState.CONNECTED) {
                    startStatsPolling()
                } else if (newState == PeerConnection.PeerConnectionState.CLOSED || newState == PeerConnection.PeerConnectionState.FAILED) {
                    stopStatsPolling()
                    _remoteVideoTrack.value = null
                    // Prepare for a new offer by recreating the PeerConnection shell
                    try { peerConnection?.close() } catch (_: Throwable) {}
                    peerConnection = null
                    createPeerConnection()
                }
            }
        })
        return peerConnection
    }

    fun start(childIdRaw: String) {
        currentChildId = childIdRaw
        if (peerConnection == null) createPeerConnection()
        peerConnection ?: run {
            _connectionState.value = ConnectionState.Failed("PeerConnection not created")
            return
        }

        _connectionState.value = ConnectionState.Connecting

        val db = FirebaseDatabase.getInstance(AppConf.FIREBASE_DB_URL)
        val baseRef = db.reference.child(AppConf.FIREBASE_CALLS_PATH).child(childIdRaw)
        val offerRef = baseRef.child("offer")
        val answerRef = baseRef.child("answer")

        // Prevent duplicate listeners for same ID
        if (offerPathListenerAttachedForId == childIdRaw) return
        offerPathListenerAttachedForId = childIdRaw

        // Attach a single-shot style listener for offer updates
        firebaseJob?.cancel()
        firebaseJob = externalScope.launch(Dispatchers.IO) {
            offerRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val mapType = object : com.google.firebase.database.GenericTypeIndicator<Map<String, Any>>() {}
                    val value = snapshot.getValue(mapType) ?: return
                    val type = value["type"] as? String ?: return
                    val sdp = value["sdp"] as? String ?: return
                    if (type.lowercase() != "offer") return
                    handleRemoteOffer(SessionDescription(SessionDescription.Type.OFFER, sdp), answerRef)
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    Log.w(logTag, "Offer listener cancelled: ${error.message}")
                    _connectionState.value = ConnectionState.Failed(error.message)
                }
            })
            // Also clear lingering answers to trigger child restarts as needed
            try { answerRef.removeValue() } catch (_: Throwable) {}
        }
    }

    /**
     * Gracefully close DataChannel/PeerConnection and clear our answer to
     * encourage the child to restart its offer cycle. Call from Activity.onDestroy.
     */
    fun gracefulShutdown() {
        try {
            // Best-effort notify and close DC so child receives CLOSED
            dataChannel?.let { channel ->
                try {
                    val bye = java.nio.ByteBuffer.wrap("PARENT_CLOSING".toByteArray(Charsets.UTF_8))
                    channel.send(DataChannel.Buffer(bye, false))
                } catch (_: Throwable) {}
                try { channel.close() } catch (_: Throwable) {}
            }
            dataChannel = null
            try { peerConnection?.close() } catch (_: Throwable) {}
            peerConnection = null
        } catch (_: Throwable) {}

        // Clear our previous answer so child can post a fresh offer without ambiguity
        try {
            val id = currentChildId
            if (id != null) {
                val db = FirebaseDatabase.getInstance(AppConf.FIREBASE_DB_URL)
                db.reference.child(AppConf.FIREBASE_CALLS_PATH).child(id).child("answer").setValue(null)
            }
        } catch (_: Throwable) {}

        _connectionState.value = ConnectionState.Closed
    }

    private fun handleRemoteOffer(offer: SessionDescription, answerRef: com.google.firebase.database.DatabaseReference) {
        // Ensure we have a fresh PeerConnection if the previous one failed/closed
        val pc = (
            peerConnection ?: createPeerConnection()
        ) ?: return
        Log.d(logTag, "Received offer; setting remote description")

        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                createNonTrickleAnswerAndPublish(pc, answerRef)
            }
            override fun onSetFailure(error: String?) {
                Log.e(logTag, "setRemoteDescription failed: $error")
                _connectionState.value = ConnectionState.Failed(error)
                // Attempt recovery by recreating PC and waiting for next offer
                try { peerConnection?.close() } catch (_: Throwable) {}
                peerConnection = null
                createPeerConnection()
            }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, offer)
    }

    private fun createNonTrickleAnswerAndPublish(
        pc: PeerConnection,
        answerRef: com.google.firebase.database.DatabaseReference
    ) {
        // Non-trickle: add constraint hint and wait for ICE complete
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("TrickleIce", "false"))
        }
        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        waitForIceGatheringComplete(pc) {
                            val local = pc.localDescription
                            if (local == null) {
                                _connectionState.value = ConnectionState.Failed("Local description null after ICE")
                                return@waitForIceGatheringComplete
                            }
                            publishAnswer(answerRef, local)
                        }
                    }
                    override fun onSetFailure(error: String?) {
                        Log.e(logTag, "setLocalDescription failed: $error")
                        _connectionState.value = ConnectionState.Failed(error)
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, sdp)
            }
            override fun onCreateFailure(error: String?) {
                Log.e(logTag, "createAnswer failed: $error")
                _connectionState.value = ConnectionState.Failed(error)
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    private fun publishAnswer(answerRef: com.google.firebase.database.DatabaseReference, local: SessionDescription) {
        externalScope.launch(Dispatchers.IO) {
            try {
                val payload = mapOf(
                    // Non-trickle answer payload
                    "type" to "answer",
                    "sdp" to local.description
                )
                answerRef.setValue(payload).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(logTag, "Answer published to Firebase (Non-Trickle ICE)")
                    } else {
                        Log.e(logTag, "Failed to publish answer: ${task.exception?.message}")
                        _connectionState.value = ConnectionState.Failed(task.exception?.message)
                    }
                }
            } catch (t: Throwable) {
                Log.e(logTag, "Error publishing answer", t)
                _connectionState.value = ConnectionState.Failed(t.message)
            }
        }
    }

    private fun waitForIceGatheringComplete(pc: PeerConnection, onComplete: () -> Unit) {
        if (pc.iceGatheringState() == PeerConnection.IceGatheringState.COMPLETE) {
            onComplete()
            return
        }
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val check = object : Runnable {
            override fun run() {
                if (pc.iceGatheringState() == PeerConnection.IceGatheringState.COMPLETE) {
                    onComplete()
                } else {
                    handler.postDelayed(this, 100)
                }
            }
        }
        handler.post(check)
    }

    fun sendCommand(text: String): Boolean {
        val channel = dataChannel ?: return false
        if (channel.state() != DataChannel.State.OPEN) return false
        return try {
            val buffer = java.nio.ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8))
            channel.send(DataChannel.Buffer(buffer, false))
            true
        } catch (_: Throwable) {
            false
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startStatsPolling() {
        if (statsPollingJob != null) return
        val pc = peerConnection ?: return
        statsPollingJob = externalScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    // Collect stats and extract inbound audio level when available
                    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                        pc.getStats { report: RTCStatsReport ->
                            try {
                                var level: Float? = null
                                for (stat in report.statsMap.values) {
                                    if (stat.type == "inbound-rtp") {
                                        val kind = stat.members["kind"] as? String
                                        if (kind == "audio") {
                                            val audioLevel = (stat.members["audioLevel"] as? Number)?.toDouble()
                                            if (audioLevel != null) {
                                                level = audioLevel.toFloat().coerceIn(0f, 1f)
                                                break
                                            }
                                            val totalEnergy = (stat.members["totalAudioEnergy"] as? Number)?.toDouble()
                                            if (totalEnergy != null) {
                                                // Rough normalization fallback
                                                level = (totalEnergy % 1.0).toFloat().coerceIn(0f, 1f)
                                            }
                                        }
                                    }
                                }
                                level?.let { _inboundAudioLevel.value = it }
                            } catch (_: Throwable) {}
                            cont.resume(Unit) {}
                        }
                    }
                } catch (_: Throwable) {}
                kotlinx.coroutines.delay(250)
            }
        }
    }

    private fun stopStatsPolling() {
        statsPollingJob?.cancel()
        statsPollingJob = null
        _inboundAudioLevel.value = 0f
    }

}

