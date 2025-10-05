package com.soumo.child.signaling

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import com.google.firebase.database.ValueEventListener
import com.soumo.child.configuration.AppConfig
import org.webrtc.SessionDescription

enum class PeerRole { OFFERER, ANSWERER }

/**
 * Firebase handshake helper.
 * • Listens **only** to the messages relevant for the local role.
 *   - ANSWERER listens for `/offer`
 *   - OFFERER  listens for `/answer`
 *   - Both listen for `/candidates`
 */
class SignalingClient(
    roomId: String,
    role: PeerRole,
    private val onOfferReceived:  (SessionDescription) -> Unit,
    private val onAnswerReceived: (SessionDescription) -> Unit
) {
    private val db = FirebaseDatabase
        .getInstance(AppConfig.Firebase.DATABASE_URL)
        .reference.child("calls").child(roomId)
    
    private var offerListener: ValueEventListener? = null
    private var answerListener: ValueEventListener? = null

    init {
        if (role == PeerRole.ANSWERER) listenForOffer()    // answerer waits for offer
        if (role == PeerRole.OFFERER)  listenForAnswer()   // offerer waits for answer
    }

    /* ─── send helpers ─────────────────────────────── */
    fun sendOffer(sdp: SessionDescription) {
        println("🔍 Sending offer to Firebase. path: calls/${db.key}")
        db.child("offer").setValue(sdp.serialize())
    }
    fun sendAnswer(sdp: SessionDescription) = db.child("answer").setValue(sdp.serialize())

    /* ─── receive helpers ──────────────────────────── */
    private fun listenForOffer() {
        offerListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val mapType = object : GenericTypeIndicator<Map<String, Any>>() {}
                s.getValue(mapType)?.let { onOfferReceived(it.toSessionDescription()) }
            }
            override fun onCancelled(e: DatabaseError) {
                println("🔍 Firebase offer listener cancelled: ${e.message}")
            }
        }
        offerListener?.let { db.child("offer").addValueEventListener(it) }
    }

    private fun listenForAnswer() {
        answerListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val mapType = object : GenericTypeIndicator<Map<String, Any>>() {}
                s.getValue(mapType)?.let { onAnswerReceived(it.toSessionDescription()) }
            }
            override fun onCancelled(e: DatabaseError) {
                println("🔍 Firebase answer listener cancelled: ${e.message}")
            }
        }
        answerListener?.let { db.child("answer").addValueEventListener(it) }
    }


    /* ─── cleanup ──────────────────────────────────── */
    fun cleanup() {
        try {
            offerListener?.let { db.child("offer").removeEventListener(it) }
            answerListener?.let { db.child("answer").removeEventListener(it) }
            offerListener = null
            answerListener = null
            // Clean up Firebase data
            db.removeValue()
        } catch (_: Exception) { /** ignore */}
    }
}
