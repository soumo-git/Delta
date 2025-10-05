package com.soumo.child.components.microphone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpSender

class MicrophoneController(
    private val context: Context,
    private val factory: PeerConnectionFactory,
    private val pc: PeerConnection?
) {
    private val audioConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "false"))
        mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googAudioMirroring", "false"))
    }

    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var rtpSender: RtpSender? = null
    private var isRecording = false
    private var onRenegotiationNeeded: (() -> Unit)? = null

    init {
        Log.d("MicrophoneCtrl", "🎤 → MicrophoneController initialized (track not added to PeerConnection yet)")
    }

    fun setRenegotiationCallback(callback: () -> Unit) {
        onRenegotiationNeeded = callback
    }

    fun startMicrophone() {
        if (isRecording) return // Already recording

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("MicrophoneCtrl", "❌ Missing microphone permission")
            return
        }

        try {
            // Create audio source and track only when needed
            audioSource = factory.createAudioSource(audioConstraints)
            audioTrack = factory.createAudioTrack("MIC_TRACK", audioSource)

            // Add track to PeerConnection
            rtpSender = pc?.addTrack(audioTrack)

            // Enable the track
            audioTrack?.setEnabled(true)
            isRecording = true

            Log.d("MicrophoneCtrl", "✅ Microphone started - track added to PeerConnection")

            // Trigger renegotiation since we added a new track
            Log.d("MicrophoneCtrl", "Triggering WebRTC renegotiation for new audio track...")
            triggerRenegotiation()

        } catch (e: Exception) {
            Log.e("MicrophoneCtrl", "❌ Failed to start microphone: ${e.message}")
            cleanup()
        }
    }

    fun stopMicrophone() {
        try {
            // Disable the track first
            audioTrack?.setEnabled(false)

            // Remove track from PeerConnection
            rtpSender?.let { sender ->
                pc?.removeTrack(sender)
                Log.d("MicrophoneCtrl", "✅ Audio track removed from PeerConnection")

                // Trigger renegotiation since we removed a track
                Log.d("MicrophoneCtrl", "Triggering WebRTC renegotiation for removed audio track...")
                triggerRenegotiation()
            }

            isRecording = false
            cleanup()
            Log.d("MicrophoneCtrl", "🛑 Microphone stopped - track removed from PeerConnection")
        } catch (e: Exception) {
            Log.e("MicrophoneCtrl", "❌ Failed to stop microphone: ${e.message}")
        }
    }

    private fun triggerRenegotiation() {
        try {
            Log.d("MicrophoneCtrl", "Calling renegotiation callback...")
            onRenegotiationNeeded?.invoke()
        } catch (e: Exception) {
            Log.e("MicrophoneCtrl", "Error triggering renegotiation", e)
        }
    }

    private fun cleanup() {
        try {
            audioTrack?.dispose()
            audioSource?.dispose()
            audioTrack = null
            audioSource = null
            rtpSender = null
        } catch (e: Exception) {
            Log.w("MicrophoneCtrl", "Warning during cleanup: ${e.message}")
        }
    }
}
