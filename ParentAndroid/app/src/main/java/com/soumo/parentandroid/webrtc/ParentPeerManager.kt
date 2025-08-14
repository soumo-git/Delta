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
class ParentPeerManager(
    private val context: Context,
    private val externalScope: CoroutineScope
) {
    sealed class ConnectionState {
        data object Idle : ConnectionState()
        data object Connecting : ConnectionState()
        data object Connected : ConnectionState()
        data class Failed(val reason: String?) : ConnectionState()
        data object Closed : ConnectionState()
    }

    private val logTag = "ParentPeerManager"

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var eglBase: EglBase? = null

    private var firebaseJob: Job? = null

    private var offerPathListenerAttachedForId: String? = null
    private var currentChildId: String? = null

    // DataChannel provided by child (offerer)
    private var dataChannel: DataChannel? = null

    // Observability
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _dataChannelEvents = MutableSharedFlow<String>(
        extraBufferCapacity = 1024,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val dataChannelEvents: SharedFlow<String> = _dataChannelEvents

    // Inbound audio level (0.0 – 1.0). Updated from WebRTC stats.
    private val _inboundAudioLevel = MutableStateFlow(0f)
    val inboundAudioLevel: StateFlow<Float>
        get() = _inboundAudioLevel

    private var statsPollingJob: Job? = null

    // Streams (camera/screen/mic) can be surfaced later through callbacks if needed
    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?>
        get() = _remoteVideoTrack

    fun eglContext(): EglBase.Context? = eglBase?.eglBaseContext

    fun initializePeerConnectionFactory() {
        if (peerConnectionFactory != null) return

        eglBase = EglBase.create()

        val initOptions = PeerConnectionFactory.InitializationOptions
            .builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val encoderFactory = DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
        Log.d(logTag, "PeerConnectionFactory initialized")
    }

    fun createPeerConnection(): PeerConnection? {
        val factory = peerConnectionFactory ?: return null
        val rtcConfig = PeerConnection.RTCConfiguration(
            AppConf.STUN_SERVERS.map { stun -> PeerConnection.IceServer.builder(stun).createIceServer() }
        )
        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) { /* Non-trickle: ignore */ }
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {}
            override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
                Log.d(logTag, "ICE state: $s")
            }
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {
                Log.d(logTag, "ICE gathering: $s")
            }
            override fun onAddStream(ms: org.webrtc.MediaStream?) {}
            override fun onRemoveStream(ms: org.webrtc.MediaStream?) {}
            override fun onDataChannel(channel: DataChannel) {
                dataChannel = channel
                Log.d(logTag, "DataChannel received: ${channel.label()}")
                channel.registerObserver(object : DataChannel.Observer {
                    override fun onStateChange() {
                        Log.d(logTag, "DataChannel state: ${channel.state()}")
                        if (channel.state() == DataChannel.State.CLOSED) {
                            dataChannel = null
                        }
                    }
                    override fun onMessage(buffer: DataChannel.Buffer?) {
                        if (buffer == null) return
                        // Copy remaining bytes into a fresh array BEFORE reading strings
                        val remaining = buffer.data.remaining()
                        if (remaining <= 0) return
                        val bytes = ByteArray(remaining)
                        buffer.data.get(bytes)
                        val text = try {
                            String(bytes, Charsets.UTF_8)
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
                    kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
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

