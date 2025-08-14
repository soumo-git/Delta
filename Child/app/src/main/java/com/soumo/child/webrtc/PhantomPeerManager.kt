package com.soumo.child.webrtc

import android.content.Context
import org.webrtc.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class PhantomPeerManager(
    private val context: Context,
    private val observer: PeerConnection.Observer
) {
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null

    fun initializePeerConnectionFactory() {
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        val options = PeerConnectionFactory.Options()
        val encoderFactory = DefaultVideoEncoderFactory(
            EglBase.create().eglBaseContext, true, true
        )
        val decoderFactory = DefaultVideoDecoderFactory(EglBase.create().eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    fun createPeerConnection(iceServers: List<PeerConnection.IceServer>) {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, observer)
    }

    fun createOffer(sdpObserver: SdpObserver) {
        peerConnection?.createOffer(sdpObserver, MediaConstraints())
    }

    fun createAnswer(sdpObserver: SdpObserver) {
        peerConnection?.createAnswer(sdpObserver, MediaConstraints())
    }

    fun setLocalDescription(sdp: SessionDescription, sdpObserver: SdpObserver) {
        peerConnection?.setLocalDescription(sdpObserver, sdp)
    }

    fun setRemoteDescription(sdp: SessionDescription, sdpObserver: SdpObserver) {
        peerConnection?.setRemoteDescription(sdpObserver, sdp)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun getPeerConnection(): PeerConnection? = peerConnection
    fun getFactory(): PeerConnectionFactory = peerConnectionFactory
    
    // Non-Trickle ICE: Wait for ICE gathering to complete and return SDP with all candidates
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun createNonTrickleOffer(): SessionDescription? {
        return createNonTrickleSdp(isOffer = true)
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun createNonTrickleAnswer(): SessionDescription? {
        return createNonTrickleSdp(isOffer = false)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun createNonTrickleSdp(isOffer: Boolean): SessionDescription? {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("TrickleIce", "false"))
        }
        return suspendCancellableCoroutine { cont ->
            val pc = peerConnection ?: return@suspendCancellableCoroutine cont.resume(null) {}
            val sdpObserver = object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    pc.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            // Wait for ICE gathering to complete
                            waitForIceGatheringComplete(pc) {
                                cont.resume(pc.localDescription) {}
                            }
                        }
                        override fun onSetFailure(p0: String?) { cont.resume(null) {} }
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) { cont.resume(null) {} }
                    }, sdp)
                }
                override fun onCreateFailure(error: String?) { cont.resume(null) {} }
                override fun onSetSuccess() {}
                override fun onSetFailure(p0: String?) {}
            }
            if (isOffer) {
                pc.createOffer(sdpObserver, constraints)
            } else {
                pc.createAnswer(sdpObserver, constraints)
            }
        }
    }

    private fun waitForIceGatheringComplete(pc: PeerConnection, onComplete: () -> Unit) {
        if (pc.iceGatheringState() == PeerConnection.IceGatheringState.COMPLETE) {
            onComplete()
            return
        }
        
        // Simple polling approach without recursion
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val checkComplete = object : Runnable {
            override fun run() {
                if (pc.iceGatheringState() == PeerConnection.IceGatheringState.COMPLETE) {
                    onComplete()
                } else {
                    // Poll again after a short delay
                    handler.postDelayed(this, 100)
                }
            }
        }
        checkComplete.run()
    }

}