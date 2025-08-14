package com.soumo.parentandroid.ui

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import android.view.LayoutInflater
import android.widget.FrameLayout
import java.util.Locale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.app.Activity
import android.view.WindowManager
import com.soumo.parentandroid.auth.AuthManager
import kotlinx.coroutines.launch
import com.soumo.parentandroid.AppConf
import android.webkit.WebView
import android.webkit.WebViewClient
import android.graphics.BitmapFactory

@SuppressLint("ConfigurationScreenWidthHeight", "ContextCastToActivity")
@Composable
fun DashboardScreenUI(
    authManagerProvider: (() -> AuthManager)? = null,
    onSignedOut: () -> Unit = {},
    userEmailProvider: (() -> String?)? = null,
    peerManagerProvider: (() -> com.soumo.parentandroid.webrtc.ParentPeerManager)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val getAuthManager = remember(authManagerProvider) { authManagerProvider ?: { AuthManager(context) } }
    val getPeerManager = remember(peerManagerProvider) { peerManagerProvider }
    val commandHandler = remember(getPeerManager) {
        val pm = getPeerManager?.invoke()
        if (pm != null) com.soumo.parentandroid.CommandHandler(pm, scope) else null
    }
    val getUserEmail = remember(userEmailProvider, getAuthManager) { userEmailProvider ?: { getAuthManager().currentUser()?.email } }

    // Sidebar state
    var sidebarOpen by remember { mutableStateOf(false) }
    val sidebarWidthFraction = 0.7f
    val sidebarAlpha = 0.7f
    var userMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDeletePassword by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var deleteError by remember { mutableStateOf<String?>(null) }

    // Screen metrics
    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp.dp
    val sidebarWidthDp = screenWidthDp * sidebarWidthFraction

    // Panels for actions (stacked list)
    data class PanelState(
        val id: Long,
        val title: String
    )
    val panels = remember { mutableStateListOf<PanelState>() }
    // Track per-feature activity/loading via observable maps keyed by title
    val panelLoading = remember { mutableStateMapOf<String, Boolean>() }
    val panelActive = remember { mutableStateMapOf<String, Boolean>() }
    // Store last known location for map rendering
    var lastLat by remember { mutableStateOf<Double?>(null) }
    var lastLng by remember { mutableStateOf<Double?>(null) }
    var cameraFullscreen by remember { mutableStateOf(false) }
    var showStealthDialog by remember { mutableStateOf(false) }
    var stealthActive by remember { mutableStateOf(false) }
    var stealthProcessing by remember { mutableStateOf(false) }

    // Data lists for SMS and Calls
    data class SmsItem(
        val id: String,
        val timestamp: Long,
        val address: String,
        val body: String,
        val smsType: String
    )
    data class CallItem(
        val id: String,
        val timestamp: Long,
        val number: String,
        val name: String,
        val durationSeconds: Long,
        val callType: String
    )
    val smsItems = remember { mutableStateListOf<SmsItem>() }
    val callItems = remember { mutableStateListOf<CallItem>() }

    // Subscribe to child responses to update loading/active flags
    DisposableEffect(commandHandler) {
        val handler = commandHandler
        handler?.startListening { msg ->
            val m = msg.trim()
            fun setState(feature: String, loading: Boolean? = null, active: Boolean? = null) {
                val k = feature.lowercase()
                loading?.let { panelLoading[k] = it }
                active?.let { panelActive[k] = it }
            }
                when {
                m.startsWith(AppConf.RSP_CAMERA_STARTED) -> setState("camera", loading = false, active = true)
                m.startsWith(AppConf.RSP_CAMERA_STOPPED) -> setState("camera", loading = false, active = false)
                m.startsWith(AppConf.RSP_CAMERA_ERROR_PREFIX) -> setState("camera", loading = false)
                m.startsWith(AppConf.RSP_CAMERA_PERMISSION_REQUESTED) -> setState("camera", loading = false)

                m.startsWith(AppConf.RSP_MIC_STARTED) -> setState("mic", loading = false, active = true)
                m.startsWith(AppConf.RSP_MIC_STOPPED) -> setState("mic", loading = false, active = false)
                m.startsWith(AppConf.RSP_MIC_ERROR_PREFIX) -> setState("mic", loading = false)
                m.startsWith(AppConf.RSP_MIC_PERMISSION_REQUESTED) -> setState("mic", loading = false)

                m.startsWith(AppConf.RSP_SCREEN_STOPPED) -> setState("screen", loading = false, active = false)
                m.startsWith(AppConf.RSP_SCREEN_ERROR_PREFIX) -> setState("screen", loading = false)
                m.startsWith(AppConf.RSP_SCREEN_CAPTURE_ERROR_PREFIX) -> setState("screen", loading = false)
                m.startsWith(AppConf.RSP_SCREEN_PERMISSION_REQUESTED) -> setState("screen", loading = false)

                m.startsWith(AppConf.RSP_LOCATION_STARTED) -> setState("location", loading = false, active = true)
                m.startsWith(AppConf.RSP_LOCATION_STOPPED) -> setState("location", loading = false, active = false)
                m.startsWith(AppConf.RSP_LOCATION_ERROR_PREFIX) -> setState("location", loading = false)
                m.startsWith(AppConf.RSP_LOCATION_PERMISSION_REQUESTED) -> setState("location", loading = false)

                // Stealth ACKs
                m.startsWith(AppConf.RSP_STEALTH_ON_ACK) -> {
                    stealthProcessing = false
                    stealthActive = true
                }
                m.startsWith(AppConf.RSP_STEALTH_OFF_ACK) -> {
                    stealthProcessing = false
                    stealthActive = false
                }

                m.startsWith(AppConf.RSP_SMS_STARTED) -> setState("sms", loading = false, active = true)
                m.startsWith(AppConf.RSP_SMS_STOPPED) -> setState("sms", loading = false, active = false)
                m.startsWith(AppConf.RSP_SMS_ERROR_PREFIX) -> setState("sms", loading = false)
                m.startsWith(AppConf.RSP_SMS_PERMISSION_REQUESTED) -> setState("sms", loading = false)

                m.startsWith(AppConf.RSP_CALLLOG_STARTED) -> setState("calls", loading = false, active = true)
                m.startsWith(AppConf.RSP_CALLLOG_STOPPED) -> setState("calls", loading = false, active = false)
                m.startsWith(AppConf.RSP_CALLLOG_ERROR_PREFIX) -> setState("calls", loading = false)
                m.startsWith(AppConf.RSP_CALLLOG_PERMISSION_REQUESTED) -> setState("calls", loading = false)
            }
            // Handle JSON payloads like LOCATION_UPDATE, SMS, CALLLOG
            if (m.startsWith("{")) {
                try {
                    val json = org.json.JSONObject(m)
                    when (json.optString("type")) {
                        AppConf.TYPE_LOCATION_UPDATE -> {
                        val coords = json.optJSONArray("coords")
                        if (coords != null && coords.length() >= 2) {
                            lastLat = coords.optDouble(0)
                            lastLng = coords.optDouble(1)
                        }
                        }
                        "sms" -> {
                            val timestamp = json.optLong("timestamp")
                            val address = json.optString("address")
                            val body = json.optString("body")
                            val smsType = json.optString("sms_type")
                            val newItem = SmsItem(
                                id = "${'$'}timestamp|${'$'}address|${'$'}{body.hashCode()}|${'$'}smsType",
                                timestamp = timestamp,
                                address = address,
                                body = body,
                                smsType = smsType
                            )
                            smsItems.add(0, newItem)
                            // Optional: cap list size
                            if (smsItems.size > 1000) smsItems.removeAt(smsItems.lastIndex)
                        }
                        "calllog" -> {
                            val timestamp = json.optLong("timestamp")
                            val number = json.optString("number")
                            val name = json.optString("name")
                            val duration = json.optLong("duration")
                            val callType = json.optString("call_type")
                            val newItem = CallItem(
                                id = "${'$'}timestamp|${'$'}number|${'$'}callType",
                                timestamp = timestamp,
                                number = number,
                                name = name,
                                durationSeconds = duration,
                                callType = callType
                            )
                            callItems.add(0, newItem)
                            if (callItems.size > 500) callItems.removeAt(callItems.lastIndex)
                        }
                        else -> {}
                    }
                } catch (_: Exception) {}
            }
        }
        onDispose { handler?.stopListening() }
    }

    // Keep screen on while camera is active or fullscreen
    val keepScreenOn = (panelActive["camera"] == true) || cameraFullscreen
    val hostActivity = LocalContext.current as? Activity
    DisposableEffect(keepScreenOn) {
        if (keepScreenOn) {
            hostActivity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            hostActivity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    fun openPanel(title: String) {
        val normalizedTitle = title.replaceFirstChar { it.uppercase() }
        if (panels.any { it.title.equals(normalizedTitle, ignoreCase = true) }) return
        val id = System.currentTimeMillis()
        val panel = PanelState(id = id, title = normalizedTitle)
        panels.add(panel)
        panelLoading[normalizedTitle.lowercase()] = false
        panelActive[normalizedTitle.lowercase()] = false
    }

    // Sidebar + content
    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
    ) {
        val openProgress by animateFloatAsState(targetValue = if (sidebarOpen) 1f else 0f)

        // Edge swipe opener (left edge only) - avoid top area where menu button lives
        if (!sidebarOpen) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(24.dp)
                    .padding(top = 56.dp)
                    .align(Alignment.CenterStart)
                    .zIndex(0f)
                    .pointerInput("edge-open") {
                        var dx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dx = 0f },
                            onHorizontalDrag = { _, dragAmount -> dx += dragAmount },
                            onDragEnd = {
                                if (dx > 75f) sidebarOpen = true
                            }
                        )
                    }
            )
        }
        // Menu button moved later in composition to ensure it's on top of scrollable content

        // Scrim/close area when open (right side of sidebar)
        if (sidebarOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = sidebarWidthDp)
                    .pointerInput("close-drag") {
                        var dx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dx = 0f },
                            onHorizontalDrag = { _, dragAmount -> dx += dragAmount },
                            onDragEnd = {
                                if (dx < -48f) sidebarOpen = false
                            }
                        )
                    }
                    .clickable(onClick = { sidebarOpen = false }, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() })
            )
        }

        // Sidebar panel
        val sidebarOffset = (-sidebarWidthDp.value * (1f - openProgress)).dp
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(sidebarWidthFraction)
                .offset(x = sidebarOffset)
                .alpha(sidebarAlpha)
                .zIndex(1f),
            color = Color(0xFF0B0B0B),
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput("sidebar-drag-close") {
                        var dx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dx = 0f },
                            onHorizontalDrag = { _, dragAmount -> dx += dragAmount },
                            onDragEnd = {
                                if (dx < -48f) sidebarOpen = false
                            }
                        )
                    }
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    // App logo
                    AndroidView(
                        factory = { ctx ->
                            android.widget.ImageView(ctx).apply {
                                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                val resId = ctx.resources.getIdentifier(
                                    "ic_launcher_foreground_bitmap",
                                    "drawable",
                                    ctx.packageName
                                )
                                if (resId != 0) {
                                    setImageResource(resId)
                                } else {
                                    // Fallback: try assets
                                    val bmp = try {
                                        ctx.assets.open("Icon - PNG.png").use { ins -> BitmapFactory.decodeStream(ins) }
                                    } catch (_: Exception) { null }
                                    if (bmp != null) setImageBitmap(bmp) else setImageResource(com.soumo.parentandroid.R.drawable.ic_launcher_foreground)
                                }
                            }
                        },
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                    )

                    Spacer(Modifier.size(16.dp))

                    // Stealth control button with dynamic states
                    val rotation by rememberInfiniteTransition(label = "spin").animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
                        label = "spinAnim"
                    )
                    if (stealthProcessing) {
                        Button(
                            onClick = { },
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1A1A1A),
                                contentColor = Color(0xFFDDDDDD)
                            )
                        ) {
                            // Rotating sniper glyph
                            Text(
                                "◎",
                                modifier = Modifier.graphicsLayer { rotationZ = rotation },
                                fontSize = 18.sp,
                                color = Color(0xFFDDDDDD)
                            )
                        }
                    } else if (stealthActive) {
                        SidebarButton(text = "Stop stealth") {
                            val ch = commandHandler
                            if (ch != null) {
                                stealthProcessing = true
                                ch.stealthOff()
                            }
                        }
                    } else {
                        SidebarButton(text = "Stealth Child") {
                            showStealthDialog = true
                        }
                    }
                    DividerLine()

                    SidebarButton(text = "Monitor camera") { openPanel("camera"); sidebarOpen = false }
                    SidebarButton(text = "Monitor mic") { openPanel("mic"); sidebarOpen = false }
                    SidebarButton(text = "Monitor screen") { openPanel("screen"); sidebarOpen = false }
                    SidebarButton(text = "Monitor location") { openPanel("location"); sidebarOpen = false }
                    SidebarButton(text = "Monitor SMS") { openPanel("sms"); sidebarOpen = false }
                    SidebarButton(text = "Monitor call logs") { openPanel("calls"); sidebarOpen = false }
                }

                // User profile (bottom)
                Column(horizontalAlignment = Alignment.Start) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1A1A1A))
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(text = getUserEmail() ?: "User", color = Color.White, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { userMenuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null, tint = Color.White)
                        }
                        DropdownMenu(expanded = userMenuExpanded, onDismissRequest = { userMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text(getUserEmail() ?: "") }, onClick = { })
                            DropdownMenuItem(text = { Text("Sign out") }, onClick = {
                                userMenuExpanded = false
                                getAuthManager().signOut()
                                onSignedOut()
                            })
                            DropdownMenuItem(text = { Text("Delete account") }, onClick = {
                                userMenuExpanded = false
                                showDeleteConfirm = true
                            })
                        }
                    }
                }
            }
        }

        // Removed full-screen global swipe overlay to avoid intercepting taps

        // Main content area (scrollable) with stacked panels
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = (sidebarWidthDp * openProgress))
                .verticalScroll(rememberScrollState())
                .padding(top = 35.dp)
                .pointerInput("main-open-swipe") {
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onHorizontalDrag = { _, dragAmount -> dx += dragAmount },
                        onDragEnd = {
                            if (!sidebarOpen && dx > 24f) sidebarOpen = true
                        }
                    )
                }
                .zIndex(0f)
        ) {
            Spacer(Modifier.size(8.dp))
            if (panels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 96.dp, bottom = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Swipe left",
                            color = Color(0xFFBBBBBB),
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = "or tap the menu icon 'top left corner' to open the sidebar",
                            color = Color(0xFF888888),
                            fontSize = 13.sp
                        )
                    }
                }
            }
            panels.forEach { panel ->
                val key = panel.title.lowercase()
                val isLoading = panelLoading[key] == true
                val isActive = panelActive[key] == true

                // Optional mic wave or location map rendering beneath actions
                val extraContent: (@Composable () -> Unit)? = if (key == "mic") {
                    {
                        val pm = getPeerManager?.invoke()
                        val level by (pm?.inboundAudioLevel ?: kotlinx.coroutines.flow.MutableStateFlow(0f)).collectAsState(initial = 0f)
                        // Simple bar wave
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .height(48.dp)
                        ) {
                            val bars = 24
                            val barWidth = size.width / (bars * 1.5f)
                            val gap = barWidth * 0.5f
                            val maxH = size.height
                            val amplitude = (maxH * (0.2f + level.coerceIn(0f, 1f))).coerceAtMost(maxH)
                            for (i in 0 until bars) {
                                val x = i * (barWidth + gap)
                                val h = (amplitude * (0.5f + 0.5f * kotlin.math.sin(i / 2f)))
                                drawRect(
                                    color = Color(0xFF63FFBC),
                                    topLeft = androidx.compose.ui.geometry.Offset(x, (maxH - h) / 2f),
                                    size = androidx.compose.ui.geometry.Size(barWidth, h)
                                )
                            }
                        }
                    }
                } else if (key == "location") {
                    {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF111111))
                        ) {
                            val lat = lastLat
                            val lng = lastLng
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        settings.domStorageEnabled = true
                                        settings.loadsImagesAutomatically = true
                                        webViewClient = WebViewClient()
                                        setBackgroundColor(0x00000000)
                                    }
                                },
                                update = { wv ->
                                    if (lat != null && lng != null) {
                                        val html = """
                                            <!DOCTYPE html>
                                            <html>
                                              <head>
                                                <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
                                                <style>html,body{margin:0;padding:0;background:#111} img{display:block;width:100%;height:100%;object-fit:cover;border:0}</style>
                                              </head>
                                              <body>
                                                <img src=\"https://maps.locationiq.com/v3/staticmap?key=pk.eyJ1IjoiYXV0byIsImEiOiJja2V5a2V5a2V5In0&center=${lat},${lng}&zoom=15&size=600x300&markers=icon:large-red-cutout|${lat},${lng}\"/>
                                              </body>
                                            </html>
                                        """.trimIndent()
                                        wv.loadDataWithBaseURL("https://maps.locationiq.com", html, "text/html", "utf-8", null)
                                    } else {
                                        if (wv.url != "about:blank") wv.loadUrl("about:blank")
                                    }
                                }
                            )
                        }
                    }
                } else if (key == "camera") {
                    {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF111111))
                        ) {
                            // XML SurfaceViewRenderer overlay via AndroidView
                            AndroidView(
                                factory = { ctx ->
                                    val parent = FrameLayout(ctx)
                                    LayoutInflater.from(ctx).inflate(
                                        com.soumo.parentandroid.R.layout.webrtc_surface,
                                        parent,
                                        true
                                    )
                                    parent
                                },
                                update = { root ->
                                    val pm = getPeerManager?.invoke()
                                    val egl = pm?.eglContext()
                                    val vt = pm?.remoteVideoTrack?.value
                                    val svr = root.findViewById<org.webrtc.SurfaceViewRenderer>(com.soumo.parentandroid.R.id.remote_view)
                                    if (egl != null) {
                                        val tag = svr.tag
                                        if (tag != "inited") {
                                            try { svr.init(egl, null) } catch (_: Exception) {}
                                            svr.setMirror(false)
                                            svr.setEnableHardwareScaler(true)
                                            svr.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                                            svr.tag = "inited"
                                        }
                                        // Ensure we do not attach duplicate sinks across updates
                                        if (vt != null) {
                                            try { vt.removeSink(svr) } catch (_: Exception) {}
                                            vt.addSink(svr)
                                        } else {
                                            try { svr.clearImage() } catch (_: Exception) {}
                                        }
                                    }
                                }
                            )

                            // Bottom controls inside camera panel
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Button(
                                    onClick = {
                                        val ch = commandHandler
                                        if (ch != null) ch.cameraSwitch()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF1A1A1A),
                                        contentColor = Color.White
                                    )
                                ) { Text("◎") }
                            }

                            IconButton(
                                onClick = { cameraFullscreen = true },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                            ) {
                                Text("⤢", color = Color.White, fontSize = 18.sp)
                            }
                        }
                    }
                } else if (key == "sms") {
                    {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF111111))
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                items(smsItems) { item ->
                                    SmsRow(
                                        address = if (item.address.isNotBlank()) item.address else "Unknown",
                                        body = item.body,
                                        smsType = item.smsType,
                                        timestamp = item.timestamp
                                    )
                                }
                            }
                        }
                    }
                } else if (key == "calls") {
                    {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF111111))
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                items(callItems) { item ->
                                    val title = when {
                                        item.name.isNotBlank() -> item.name
                                        item.number.isNotBlank() -> item.number
                                        else -> "Unknown"
                                    }
                                    CallLogRow(
                                        title = title,
                                        callType = item.callType,
                                        durationSeconds = item.durationSeconds,
                                        timestamp = item.timestamp
                                    )
                                }
                            }
                        }
                    }
                } else null

                FloatingPanel(
                    title = panel.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    isLoading = isLoading,
                    isActive = isActive,
                    extraContent = extraContent,
                    // Draw audio wave for mic panel using inboundAudioLevel
                    // (we pass content by wrapping FloatingPanel or extend later; for now, status reflects via Stop/Start)
                    onStart = {
                        if (commandHandler == null) return@FloatingPanel
                        panelLoading[key] = true
                        when (key) {
                            "camera" -> commandHandler.cameraOn()
                            "mic" -> commandHandler.micOn()
                            "screen" -> commandHandler.screenOn()
                            "location" -> commandHandler.locationOn()
                            "sms" -> commandHandler.smsOn()
                            "calls" -> commandHandler.callLogOn()
                            else -> false
                        }
                    },
                    onStop = {
                        if (commandHandler == null) return@FloatingPanel
                        panelLoading[key] = true
                        when (key) {
                            "camera" -> commandHandler.cameraOff()
                            "mic" -> commandHandler.micOff()
                            "screen" -> commandHandler.screenOff()
                            "location" -> commandHandler.locationOff()
                            "sms" -> commandHandler.smsOff()
                            "calls" -> commandHandler.callLogOff()
                            else -> false
                        }
                    },
                    onClose = { panels.remove(panel) }
                )
            }
            Spacer(Modifier.size(24.dp))
        }

        // No absolute-position floating panels in this layout

        // Render menu button last so it's on top for hit testing; hide when sidebar is open
        if (!sidebarOpen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .zIndex(3f)
            ) {
                IconButton(onClick = { sidebarOpen = true }) {
                    Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = Color.White)
                }
            }
        }
    }

    // Account deletion dialogs
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete account?") },
            text = { Text("This will permanently delete your account and all associated data. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    showDeletePassword = true
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Stealth confirmation dialog
    if (showStealthDialog) {
        AlertDialog(
            onDismissRequest = { showStealthDialog = false },
            title = { Text("Enable stealth mode?") },
            text = { Text("This will attempt to hide the child app. You can stop stealth later from the sidebar.") },
            confirmButton = {
                TextButton(onClick = {
                    showStealthDialog = false
                    val ch = commandHandler
                    if (ch != null) {
                        stealthProcessing = true
                        ch.stealthOn()
                    }
                }) { Text("Stealth") }
            },
            dismissButton = {
                TextButton(onClick = { showStealthDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Fullscreen camera dialog
    if (cameraFullscreen) {
        Dialog(
            onDismissRequest = { cameraFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val parent = FrameLayout(ctx)
                        LayoutInflater.from(ctx).inflate(
                            com.soumo.parentandroid.R.layout.webrtc_surface,
                            parent,
                            true
                        )
                        parent
                    },
                    update = { root ->
                        val pm = getPeerManager?.invoke()
                        val egl = pm?.eglContext()
                        val vt = pm?.remoteVideoTrack?.value
                        val svr = root.findViewById<org.webrtc.SurfaceViewRenderer>(com.soumo.parentandroid.R.id.remote_view)
                        if (egl != null) {
                            val tag = svr.tag
                            if (tag != "inited") {
                                try { svr.init(egl, null) } catch (_: Exception) {}
                                svr.setMirror(false)
                                svr.setEnableHardwareScaler(true)
                                svr.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                                svr.tag = "inited"
                            }
                            if (vt != null) {
                                try { vt.removeSink(svr) } catch (_: Exception) {}
                                vt.addSink(svr)
                            } else {
                                try { svr.clearImage() } catch (_: Exception) {}
                            }
                        }
                    }
                )

                IconButton(
                    onClick = { cameraFullscreen = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                ) {
                    Text("✕", color = Color.White, fontSize = 18.sp)
                }
            }
        }
    }
    if (showDeletePassword) {
        AlertDialog(
            onDismissRequest = { showDeletePassword = false },
            title = { Text("Confirm password") },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it },
                        label = { Text("Password") },
                        singleLine = true
                    )
                    deleteError?.let { Text(it, color = Color.Red, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val (ok, err) = getAuthManager().deleteAccountWithPassword(deletePassword)
                        if (ok) {
                            showDeletePassword = false
                            onSignedOut()
                        } else {
                            deleteError = err
                        }
                    }
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePassword = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SmsRow(
    address: String,
    body: String,
    smsType: String,
    timestamp: Long
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)) {
        Text(address, color = Color(0xFFEEEEEE), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.size(2.dp))
        Text(body, color = Color(0xFFBBBBBB), fontSize = 13.sp)
        Spacer(Modifier.size(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(smsType.replaceFirstChar { it.uppercase() }, color = Color(0xFF88CCAA), fontSize = 12.sp)
            Text(formatTimestamp(timestamp), color = Color(0xFF888888), fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun CallLogRow(
    title: String,
    callType: String,
    durationSeconds: Long,
    timestamp: Long
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)) {
        Text(title, color = Color(0xFFEEEEEE), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.size(2.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                val typeAndDur = "${'$'}{callType.replaceFirstChar { it.uppercase() }} • ${'$'}{formatDuration(durationSeconds)}"
                Text(typeAndDur, color = Color(0xFFBBBBBB), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                formatTimestamp(timestamp),
                color = Color(0xFF888888),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatTimestamp(ts: Long): String {
    return try {
        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
            .format(java.util.Date(ts))
    } catch (_: Exception) {
        ts.toString()
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}

@Composable
private fun SidebarButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1A1A1A),
            contentColor = Color(0xFFDDDDDD)
        )
    ) {
        Text(text)
    }
}

@Composable
private fun DividerLine() {
    Canvas(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 12.dp)) {
        drawRect(Color(0x22FFFFFF), size = this.size.copy(height = 1.dp.toPx()))
    }
}

@Composable
private fun FloatingPanel(
    title: String,
    modifier: Modifier = Modifier,
    onStart: () -> Unit = {},
    onStop: () -> Unit = {},
    isLoading: Boolean = false,
    isActive: Boolean = false,
    extraContent: (@Composable () -> Unit)? = null,
    onClose: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = Color(0xEE0B0B0B),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            if (extraContent != null) {
                extraContent()
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                when {
                    isLoading -> {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFF00AA66),
                            strokeWidth = 3.dp
                        )
                    }
                    isActive -> {
                        Button(onClick = onStop, colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF660000), contentColor = Color.White
                        )) { Text("Stop") }
                    }
                    else -> {
                Button(onClick = onStart, colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF006600), contentColor = Color.White
                )) { Text("Start") }
                    }
                }
            }
        }
    }
}

