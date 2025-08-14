package com.soumo.parentandroid

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.google.firebase.FirebaseApp
import com.soumo.parentandroid.auth.AuthManager
import com.soumo.parentandroid.ui.DashboardScreenUI
import com.soumo.parentandroid.ui.SignInScreen
import com.soumo.parentandroid.ui.SignUpScreen
import com.soumo.parentandroid.ui.UserInfoScreen
import com.soumo.parentandroid.webrtc.ParentPeerManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var authManager: AuthManager
    private var parentPeerManager: ParentPeerManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        authManager = AuthManager(this)

        setContent {
            var currentScreen by remember { mutableStateOf("loading") }
            var currentUser by remember { mutableStateOf(authManager.currentUser()) }
            var signInError by remember { mutableStateOf<String?>(null) }
            var signUpError by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()
            var isConnecting by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                currentUser = authManager.currentUser()
                currentScreen = if (currentUser != null) "userInfo" else "signIn"
            }

            when (currentScreen) {
                "signIn" -> SignInScreen(
                    onSignIn = { email, pass ->
                        scope.launch {
                            signInError = null
                            authManager.handleSignIn(
                                email = email,
                                password = pass,
                                onSuccess = { user ->
                                    currentUser = user
                                    signInError = null
                                    currentScreen = "userInfo"
                                },
                                onError = { error ->
                                    signInError = error
                                    Log.w("Auth", "Sign in failed: $error")
                                }
                            )
                        }
                    },
                    onSwitchToSignUp = {
                        signInError = null
                        currentScreen = "signUp"
                    },
                    errorMessage = signInError
                )

                "signUp" -> SignUpScreen(
                    onSignUp = { name, email, otp, pass, confirm ->
                        scope.launch {
                            signUpError = null
                            authManager.handleSignUpAfterVerified(
                                name = name,
                                email = email,
                                pass = pass,
                                confirm = confirm,
                                onSuccess = { user ->
                                    currentUser = user
                                    signUpError = null
                                    currentScreen = "userInfo"
                                },
                                onError = { error ->
                                    signUpError = error
                                    Log.w("Auth", "Sign up failed: $error")
                                }
                            )
                        }
                    },
                    onSendOtp = { email, callback ->
                        authManager.sendOtp(email) { success, _ ->
                            callback(success) // start timer only if success
                        }
                    },
                    onResendOtp = { email, callback ->
                        authManager.sendOtp(email) { success, _ ->
                            callback(success) // restart timer only if success
                        }
                    },
                    onVerifyOtp = { email, otp, callback ->
                        scope.launch {
                            val (ok, msg) = authManager.preVerifyOtp(email, otp)
                            callback(ok, msg)
                        }
                    },
                    onBack = {
                        signUpError = null
                        currentScreen = "signIn"
                    },
                    errorMessage = signUpError
                )

                "userInfo" -> currentUser?.let { user ->
                    UserInfoScreen(
                        user = user,
                        onContinue = {
                            currentScreen = "connection" // Navigate to connection screen here
                        },
                        onSignOut = {
                            authManager.signOut()
                            currentScreen = "signIn"
                        }
                    )
                }

                "connection" -> {
                    // Show ConnectionScreenUI here
                    com.soumo.parentandroid.ui.ConnectionScreenUI(
                        onContinue = { childId ->
                            // Start WebRTC parent flow: listen offer -> create non-trickle answer -> open DataChannel
                            val mgr = parentPeerManager ?: ParentPeerManager(this@MainActivity, scope).also {
                                it.initializePeerConnectionFactory()
                                it.createPeerConnection()
                                parentPeerManager = it
                            }
                            // Use raw child id (already numeric 12-digits)
                            isConnecting = true
                            mgr.start(childId)
                            // Observe connection state and auto-recover on failure/close
                            scope.launch {
                                mgr.connectionState.collect { st ->
                                    Log.d("ParentConn", "State: $st")
                                    when (st) {
                                        is ParentPeerManager.ConnectionState.Connected -> {
                                            isConnecting = false
                                            currentScreen = "dashboard"
                                        }
                                        is ParentPeerManager.ConnectionState.Failed -> {
                                            isConnecting = false
                                            // Stay on connection screen and let Firebase offer watcher re-trigger
                                        }
                                        is ParentPeerManager.ConnectionState.Closed -> {
                                            isConnecting = false
                                            // Allow re-offer; PeerManager now recreates PC on close
                                        }
                                        else -> {}
                                    }
                                }
                            }
                            scope.launch {
                                mgr.dataChannelEvents.collect { msg ->
                                    Log.d("ParentDC", "From child: $msg")
                                }
                            }
                        },
                        isConnecting = isConnecting
                    )
                }

                "dashboard" -> {
                    DashboardScreenUI(
                        authManagerProvider = { authManager },
                        onSignedOut = {
                            currentScreen = "signIn"
                        },
                        userEmailProvider = { authManager.currentUser()?.email },
                        peerManagerProvider = { parentPeerManager ?: throw IllegalStateException("Peer not ready") }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        try {
            parentPeerManager?.gracefulShutdown()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}