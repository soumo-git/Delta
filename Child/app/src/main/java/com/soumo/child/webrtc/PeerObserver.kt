package com.soumo.child.webrtc

import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.VideoTrack

/**
 * Thin adapter that converts noisy WebRTC callbacks into three concise lambdas.
 *
 *  • **onIce**   – every ICE candidate -> signalling
 *  • **onData**  – each text message arriving on the “cmd” DataChannel
 *  • **onTrack** – remote video (unused on the Child, kept for future)
 */
class PeerObserver(
    private val onIce:   (IceCandidate) -> Unit = {},
    private val onData:  (String)       -> Unit = {},
    private val onTrack: (VideoTrack)   -> Unit = {},
    private val onConnectionStateChange: (PeerConnection.PeerConnectionState) -> Unit = {},
) : PeerConnection.Observer {

    /* ICE → signalling RTDB */
    override fun onIceCandidate(c: IceCandidate) = onIce(c)

    /* DataChannel text → command string */
    override fun onDataChannel(dc: DataChannel) {
        dc.registerObserver(object : DataChannel.Observer {
            override fun onMessage(buf: DataChannel.Buffer) {
                val bytes = ByteArray(buf.data.remaining()).also { buf.data.get(it) }
                onData(String(bytes, Charsets.UTF_8))
            }
            override fun onStateChange() {}
            override fun onBufferedAmountChange(l: Long) {}
        })
    }

    /* Up‑stream video (unlikely on child, but kept) */
    override fun onAddTrack(r: RtpReceiver?, s: Array<out MediaStream>) {
        (r?.track() as? VideoTrack)?.let(onTrack)
    }

    /* Connection state change for reconnection handling */
    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
        onConnectionStateChange(newState)
    }

    /* Unused WebRTC callbacks */
    override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
    override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {}
    override fun onIceConnectionReceivingChange(b: Boolean) {}
    override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
    override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {}
    override fun onAddStream(ms: MediaStream?) {}
    override fun onRemoveStream(ms: MediaStream?) {}
    override fun onRenegotiationNeeded() {}
}
