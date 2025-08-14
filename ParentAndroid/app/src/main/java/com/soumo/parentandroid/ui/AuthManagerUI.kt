package com.soumo.parentandroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseUser
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.delay

@Composable
fun SignInScreen(
    onSignIn: (String, String) -> Unit,
    onSwitchToSignUp: () -> Unit,
    errorMessage: String? = null
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    HackerBackground {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(20.dp)
        ) {
            Text("Project Delta", color = Color.White, fontSize = 24.sp)
            Spacer(Modifier.height(16.dp))
            HackerTextField(value = email, label = "Email", onValueChange = { email = it })
            HackerTextField(value = password, label = "Password", onValueChange = { password = it }, isPassword = true)

            if (!errorMessage.isNullOrEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(errorMessage, color = Color.Red, style = hackerStyle())
            }

            Spacer(Modifier.height(12.dp))
            HackerButton("Sign In") { onSignIn(email, password) }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onSwitchToSignUp) {
                Text("Don't have account? Create new - Sign UP", color = Color.White)
            }
        }
    }
}

@Composable
fun SignUpScreen(
    onSignUp: (String, String, String, String, String) -> Unit,
    onSendOtp: (String, (Boolean) -> Unit) -> Unit,
    onResendOtp: (String, (Boolean) -> Unit) -> Unit,
    onVerifyOtp: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onBack: () -> Unit,
    errorMessage: String? = null
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var otpDigits = remember { mutableStateListOf("", "", "", "", "", "") }

    var otpSent by remember { mutableStateOf(false) }
    var otpVerified by remember { mutableStateOf(false) }
    var verificationMsg by remember { mutableStateOf<String?>(null) }
    var remainingTime by remember { mutableIntStateOf(0) }
    var resendEnabled by remember { mutableStateOf(false) }

    val focusRequesters = List(6) { FocusRequester() }

    // Countdown timer effect
    LaunchedEffect(remainingTime) {
        if (remainingTime > 0) {
            delay(1000)
            remainingTime--
        } else {
            resendEnabled = otpSent // Enable resend only if OTP was sent before
        }
    }

    HackerBackground {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(20.dp)
        ) {
            Text("Project Delta", color = Color.White, fontSize = 24.sp)
            Spacer(Modifier.height(16.dp))
            // Back button
            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = onBack) {
                    Text("< Back", color = Color.Green)
                }
            }

            Spacer(Modifier.height(8.dp))
            HackerTextField(value = name, label = "Name", onValueChange = { name = it })
            HackerTextField(value = email, label = "Email", onValueChange = { email = it })

            Spacer(Modifier.height(8.dp))
            // OTP row
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                otpDigits.forEachIndexed { index, digit ->
                    OutlinedTextField(
                        value = digit,
                        onValueChange = {
                            if (it.length <= 1 && it.all { ch -> ch.isDigit() }) {
                                otpDigits[index] = it
                                if (it.isNotEmpty() && index < 5) {
                                    focusRequesters[index + 1].requestFocus()
                                }
                            }
                        },
                        singleLine = true,
                        textStyle = hackerStyle().copy(fontSize = 20.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .width(48.dp)
                            .focusRequester(focusRequesters[index])
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
// Send / Verify + Resend row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (!otpSent) {
                            onSendOtp(email) { success ->
                                if (success) {
                                    otpSent = true
                                    remainingTime = 5 * 60 // 5 min countdown
                                    resendEnabled = false
                                }
                            }
                        } else if (!otpVerified) {
                            val otp = otpDigits.joinToString("")
                            onVerifyOtp(email, otp) { ok, msg ->
                                verificationMsg = msg
                                if (ok) {
                                    otpVerified = true
                                }
                            }
                        } else {
                            // already verified, no action
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text(
                        when {
                            !otpSent -> "Send OTP"
                            otpVerified -> "OTP Verified"
                            else -> "Verify OTP"
                        },
                        color = if (otpVerified) Color.Gray else Color.Green
                    )
                }
                if (otpSent) {
                    Button(
                        onClick = {
                            onResendOtp(email) { success ->
                                if (success) {
                                    otpVerified = false
                                    verificationMsg = null
                                    remainingTime = 5 * 60
                                    resendEnabled = false
                                }
                            }
                        },
                        enabled = resendEnabled && !otpVerified,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Text(
                            if (resendEnabled) "Resend OTP" else "Resend in ${remainingTime / 60}:${(remainingTime % 60).toString().padStart(2, '0')}",
                            color = Color.Green
                        )
                    }
                }
            }

            if (!errorMessage.isNullOrEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(errorMessage, color = Color.Red, style = hackerStyle())
            }
            verificationMsg?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = if (otpVerified) Color.Green else Color.Red, style = hackerStyle())
            }

// Resend row moved beside primary button above

            Spacer(Modifier.height(8.dp))
            HackerTextField(value = password, label = "Password", onValueChange = { password = it }, isPassword = true)
            HackerTextField(value = confirm, label = "Confirm Password", onValueChange = { confirm = it }, isPassword = true)

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (otpVerified) {
                        val otp = otpDigits.joinToString("")
                        onSignUp(name, email, otp, password, confirm)
                    }
                },
                enabled = otpVerified,
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text("Sign Up", color = if (otpVerified) Color.Green else Color.Gray)
            }
        }
    }
}

@Composable
fun UserInfoScreen(user: FirebaseUser, onContinue: () -> Unit, onSignOut: () -> Unit) {
    HackerBackground {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(20.dp)
        ) {
            Text("Name: ${user.displayName}", style = hackerStyle())
            Text("Email: ${user.email}", style = hackerStyle())
            Spacer(Modifier.height(20.dp))
            HackerButton("Continue", onContinue)
            Spacer(Modifier.height(10.dp))
            HackerButton("Sign Out", onSignOut)
        }
    }
}

@Composable
fun HackerBackground(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        color = Color.Black
    ) {
        Column(content = content)
    }
}

@Composable
fun HackerTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.Green) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        textStyle = hackerStyle(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Green,
            unfocusedBorderColor = Color.DarkGray
        )
    )
}

@Composable
fun HackerButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
    ) {
        Text(text, color = Color.Green)
    }
}

fun hackerStyle() =
    TextStyle(color = Color.Green, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
