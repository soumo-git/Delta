package com.soumo.parentandroid.auth

import android.content.Context
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.soumo.parentandroid.AppConf
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class AuthManager(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance(AppConf.FIREBASE_DB_URL).reference

    fun currentUser(): FirebaseUser? = auth.currentUser
    fun signOut() = auth.signOut()

    private fun safeKey(email: String) =
        email.replace(Regex("[.#$\\[\\]]"), "_")

    // -------------------
    // Send OTP (server generates OTP and writes to Firebase)
    // -------------------
    fun sendOtp(email: String, callback: (Boolean, String?) -> Unit) {
        val queue = Volley.newRequestQueue(context.applicationContext)
        val payload = JSONObject().apply {
            put("email", email)
        }
        val request = JsonObjectRequest(
            Request.Method.POST,
            AppConf.RENDER_API_URL,
            payload,
            { _ -> callback(true, null) },
            { error -> callback(false, error.message) }
        )
        queue.add(request)
    }

    // -------------------
    // Pre-Verify OTP (non-destructive)
    // -------------------
    suspend fun preVerifyOtp(email: String, enteredOtp: String): Pair<Boolean, String> {
        val node = db.child(AppConf.FIREBASE_OTP_PATH).child(safeKey(email))
        val snapshot = node.get().await()
        if (!snapshot.exists()) return false to "OTP not found"

        val otp = snapshot.child("otp").value?.toString() ?: return false to "Invalid OTP"
        val expiresAt = snapshot.child("expiresAt").value?.toString()?.toLongOrNull() ?: 0
        val attempts = snapshot.child("attempts").value?.toString()?.toIntOrNull() ?: 0
        if (attempts >= 5) return false to "Too many attempts. OTP locked."
        val isValid = enteredOtp == otp && System.currentTimeMillis() < expiresAt
        return if (isValid) true to "OTP verified successfully" else false to "Wrong OTP"
    }

    // -------------------
    // Handle Sign Up after OTP verified already
    // -------------------
    suspend fun handleSignUpAfterVerified(
        name: String,
        email: String,
        pass: String,
        confirm: String,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {
        if (pass != confirm) {
            onError("Passwords do not match")
            return
        }
        try {
            // Best-effort cleanup of OTP node
            db.child(AppConf.FIREBASE_OTP_PATH).child(safeKey(email)).removeValue()
            auth.createUserWithEmailAndPassword(email, pass).await()
            auth.currentUser?.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(name).build()
            )?.await()
            currentUser()?.let(onSuccess)
                ?: onError("User not found after sign up")
        } catch (e: Exception) {
            onError(e.message ?: "Sign up failed")
        }
    }

    /* -------------------
    // Handle Sign In
    // -------------------*/
    suspend fun handleSignIn(
        email: String,
        password: String,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            auth.signInWithEmailAndPassword(email, password).await()
            currentUser()?.let(onSuccess)
                ?: onError("User not found after sign in")
        } catch (e: Exception) {
            onError(e.message ?: "Sign in failed")
        }
    }

    // -------------------
    // Delete account (requires recent login)
    // -------------------
    suspend fun deleteAccountWithPassword(password: String): Pair<Boolean, String?> {
        val user = auth.currentUser ?: return false to "No user signed in"
        val email = user.email ?: return false to "No email on account"
        return try {
            val credential = EmailAuthProvider.getCredential(email, password)
            user.reauthenticate(credential).await()
            user.delete().await()
            true to null
        } catch (e: Exception) {
            false to (e.message ?: "Delete failed")
        }
    }

}