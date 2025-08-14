package com.soumo.child.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import org.webrtc.*

class CameraController(
    private val context: Context,
    private val factory: PeerConnectionFactory,
    private val pc: PeerConnection?
) {
    private val eglCtx = EglBase.create().eglBaseContext
    private val helper = SurfaceTextureHelper.create("CamThread", eglCtx)
    private val source = factory.createVideoSource(false)
    private val track = factory.createVideoTrack("CAM_TRACK", source)

    private var capturer: VideoCapturer? = null
    private var useFrontCamera: Boolean = true // Track current camera direction

    init {
        // Add track before SDP so it's included
        pc?.addTrack(track)
        track.setEnabled(false) // muted until CAMERA_ON
        Log.d("CameraCtrl", "✅ VideoTrack added to PeerConnection")
    }

    fun startCamera() {
        if (capturer != null) return // Already running

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("CameraCtrl", "❌ Missing camera permission")
            return
        }

        val enumerator = Camera2Enumerator(context)
        val deviceName = if (useFrontCamera) {
            enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
        } else {
            enumerator.deviceNames.firstOrNull { enumerator.isBackFacing(it) }
        } ?: enumerator.deviceNames.firstOrNull()

        if (deviceName == null) {
            Log.e("CameraCtrl", "❌ No camera found")
            return
        }

        capturer = enumerator.createCapturer(deviceName, null)
        capturer?.initialize(helper, context, source.capturerObserver)
        capturer?.startCapture(640, 480, 30)

        track.setEnabled(true)
        Log.d("CameraCtrl", "✅ Camera started (${if (useFrontCamera) "front" else "rear"})")
    }

    fun stopCamera() {
        try { capturer?.stopCapture() } catch (_: Exception) {}
        capturer?.dispose()
        capturer = null

        track.setEnabled(false)
        Log.d("CameraCtrl", "🛑 Camera stopped")
    }

    fun switchCamera() {
        // Toggle camera direction
        useFrontCamera = !useFrontCamera
        stopCamera()
        startCamera()
    }
}
