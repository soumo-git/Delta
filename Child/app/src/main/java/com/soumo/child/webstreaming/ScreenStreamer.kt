package com.soumo.child.webstreaming

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.soumo.child.service.ScreenCaptureService
import org.webrtc.*

class ScreenStreamer(
    private val context: Context,
    private val peerConnectionFactory: PeerConnectionFactory,
    private val peerConnection: PeerConnection
) {
    
    private var onStatusUpdate: ((String) -> Unit)? = null
    private var onRenegotiationNeeded: (() -> Unit)? = null
    private var activityResultLauncher: ActivityResultLauncher<Intent>? = null
    
    fun setStatusCallback(callback: (String) -> Unit) {
        onStatusUpdate = callback
    }
    
    fun setRenegotiationCallback(callback: () -> Unit) {
        onRenegotiationNeeded = callback
    }
    
    fun setActivityResultLauncher(launcher: ActivityResultLauncher<Intent>) {
        activityResultLauncher = launcher
    }

    companion object {
        const val TAG = "ScreenStreamer"
        const val REQUEST_CODE_SCREEN_CAPTURE = 1001
    }

    /** Permission intent returned by MediaProjectionManager */
    private var permissionData: Intent? = null

    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private val eglContext = EglBase.create().eglBaseContext
    /** Step 1 – ask the user for screen‑capture permission */
    fun requestPermission(activity: Activity) {
        val mgr = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mgr.createScreenCaptureIntent()
        
        try {
            activityResultLauncher?.launch(intent)
            Log.d(TAG, "Screen capture permission request started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request screen capture permission", e)
            onStatusUpdate?.invoke("SCREEN_PERMISSION_ERROR")
        }
    }

    /** Call this from the Activity's onActivityResult */
    fun onPermissionResult(resultCode: Int, data: Intent?) {
        Log.d(TAG, "onPermissionResult called - resultCode: $resultCode, data: $data")
        
        if (resultCode != Activity.RESULT_OK) {
            Log.e(TAG, "Screen capture permission denied - resultCode: $resultCode")
            onStatusUpdate?.invoke("SCREEN_PERMISSION_DENIED")
            return
        }
        
        if (data == null) {
            Log.e(TAG, "Screen capture permission data is null")
            onStatusUpdate?.invoke("SCREEN_PERMISSION_ERROR")
            return
        }
        
        try {
            permissionData = data
            Log.d(TAG, "Screen capture permission granted, starting capture...")
            onStatusUpdate?.invoke("SCREEN_PERMISSION_GRANTED")
            
            // Start foreground service first
            ScreenCaptureService.startService(context)
            Log.d(TAG, "Foreground service started")
            
            // Add a small delay to ensure service is running
            Handler(Looper.getMainLooper()).postDelayed({
                startCapture()
            }, 500) // 500ms delay
        } catch (e: Exception) {
            Log.e(TAG, "Error processing permission result", e)
            onStatusUpdate?.invoke("SCREEN_PERMISSION_ERROR")
        }
    }

    /** Internal: configure capturer, source, track, and add to PeerConnection */
    private fun startCapture() {
        try {
            Log.d(TAG, "Starting screen capture...")
            
            if (permissionData == null) {
                Log.e(TAG, "Permission data is null")
                onStatusUpdate?.invoke("SCREEN_PERMISSION_ERROR")
                return
            }
            
            // Create surface texture helper
            surfaceTextureHelper = SurfaceTextureHelper.create(
                "ScreenCaptureThread",
                eglContext
            )
            
            if (surfaceTextureHelper == null) {
                Log.e(TAG, "Failed to create SurfaceTextureHelper")
                onStatusUpdate?.invoke("SCREEN_CAPTURE_ERROR")
                return
            }
            
            Log.d(TAG, "SurfaceTextureHelper created successfully")

            // Create screen capturer
            videoCapturer = ScreenCapturerAndroid(
                permissionData,
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        Log.d(TAG, "MediaProjection stopped by system")
                        stop()
                    }
                }
            )
            
            Log.d(TAG, "ScreenCapturerAndroid created")

            // Create video source with screen sharing optimizations
            videoSource = peerConnectionFactory.createVideoSource(/* isScreencast = */ true)
            
            // Configure video source for screen sharing
            videoSource?.adaptOutputFormat(1280, 720, 20)
            if (videoSource == null) {
                Log.e(TAG, "Failed to create video source")
                onStatusUpdate?.invoke("SCREEN_CAPTURE_ERROR")
                return
            }
            
            Log.d(TAG, "Video source created")
            
            // Initialize capturer
            videoCapturer?.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
            Log.d(TAG, "Video capturer initialized")
            
            // Start capture with AnyDesk-level resolution and frame rate
            try {
                // Try Full HD first (AnyDesk quality)
                videoCapturer?.startCapture(1920, 1080, 30)
                Log.d(TAG, "Video capture started with Full HD quality (1920x1080@30fps)")
            } catch (e: Exception) {
                Log.w(TAG, "Full HD quality failed, trying HD quality", e)
                try {
                    videoCapturer?.startCapture(1280, 720, 25)
                    Log.d(TAG, "Video capture started with HD quality (1280x720@25fps)")
                } catch (e2: Exception) {
                    Log.w(TAG, "HD quality failed, using medium quality", e2)
                    try {
                        videoCapturer?.startCapture(854, 480, 20)
                        Log.d(TAG, "Video capture started with medium quality (854x480@20fps)")
                    } catch (e3: Exception) {
                        Log.w(TAG, "Medium quality failed, using low quality", e3)
                        videoCapturer?.startCapture(640, 360, 15)
                        Log.d(TAG, "Video capture started with low quality (640x360@15fps)")
                    }
                }
            }

            // Create and add video track
            videoTrack = peerConnectionFactory.createVideoTrack("SCREEN_TRACK", videoSource)
            if (videoTrack == null) {
                Log.e(TAG, "Failed to create video track")
                onStatusUpdate?.invoke("SCREEN_CAPTURE_ERROR")
                return
            }
            
            val sender = peerConnection.addTrack(videoTrack)
            
            // Configure encoding parameters for AnyDesk-level quality
            try {
                val parameters = sender.parameters
                if (parameters.encodings.isNotEmpty()) {
                    val encoding = parameters.encodings[0]
                    
                    // AnyDesk-level quality settings
                    encoding.maxBitrateBps = 8000000   // 8 Mbps for crisp quality
                    encoding.minBitrateBps = 1000000   // 1 Mbps minimum
                    encoding.maxFramerate = 30        // 30 FPS for smooth motion
                    encoding.scaleResolutionDownBy = 1.0 // No downscaling
                    
                    sender.parameters = parameters
                    Log.d(TAG, "AnyDesk-level encoding configured: maxBitrate=8Mbps, minBitrate=1Mbps, maxFramerate=30fps")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to configure encoding parameters", e)
            }
            
            Log.d(TAG, "Screen capture started and track added successfully")
            Log.d(TAG, "Track details:")
            Log.d(TAG, "- Track ID: ${videoTrack?.id()}")
            Log.d(TAG, "- Track kind: ${videoTrack?.kind()}")
            Log.d(TAG, "- Track enabled: ${videoTrack?.enabled()}")
            Log.d(TAG, "- Sender: $sender")
            
            // Trigger renegotiation since we added a new track
            Log.d(TAG, "Triggering WebRTC renegotiation for new screen track...")
            triggerRenegotiation()
            
            onStatusUpdate?.invoke("SCREEN_STARTED")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen capture", e)
            onStatusUpdate?.invoke("SCREEN_CAPTURE_ERROR")
            
            // Clean up on error
            try {
                videoCapturer?.stopCapture()
                videoCapturer?.dispose()
                videoSource?.dispose()
                videoTrack?.dispose()
                surfaceTextureHelper?.dispose()
            } catch (cleanupError: Exception) {
                Log.w(TAG, "Error during cleanup", cleanupError)
            }
        }
    }
    
    /** Trigger WebRTC renegotiation when new track is added */
    private fun triggerRenegotiation() {
        try {
            Log.d(TAG, "Calling renegotiation callback...")
            onRenegotiationNeeded?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering renegotiation", e)
        }
    }

    /** Stop and release everything */
    fun stop() {
        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            Log.w(TAG, "stopCapture error", e)
        }
        
        // Stop foreground service
        try {
            ScreenCaptureService.stopService(context)
            Log.d(TAG, "Foreground service stopped")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping foreground service", e)
        }
        
        videoCapturer?.dispose()
        videoSource?.dispose()
        videoTrack?.dispose()
        surfaceTextureHelper?.dispose()

        videoCapturer = null
        videoSource = null
        videoTrack = null
        surfaceTextureHelper = null
        permissionData = null

        Log.d(TAG, "ScreenStreamer stopped and resources released")
        onStatusUpdate?.invoke("SCREEN_STOPPED")
    }
}
