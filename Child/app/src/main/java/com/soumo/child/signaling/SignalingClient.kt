package com.soumo.child.signaling

import com.google.firebase.database.*
import org.webrtc.IceCandidate
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
    private val onAnswerReceived: (SessionDescription) -> Unit,
    private val onIceCandidate:   (IceCandidate)       -> Unit
) {
    // Use your regional RTDB URL
    private val db = FirebaseDatabase
        .getInstance(AppConfig.Firebase.DATABASE_URL)
        .reference.child(AppConfig.Firebase.CALLS_PATH).child(roomId)
    
    private var offerListener: ValueEventListener? = null
    private var answerListener: ValueEventListener? = null
    private var iceListener: ChildEventListener? = null

    init {
        if (role == PeerRole.ANSWERER) listenForOffer()    // answerer waits for offer
        if (role == PeerRole.OFFERER)  listenForAnswer()   // offerer waits for answer
        // listenForIce() // ICE candidate listening is disabled for Non-Trickle ICE
    }

    /* ─── send helpers ─────────────────────────────── */
    fun sendOffer(sdp: SessionDescription) {
        println("🔍 Sending offer to Firebase path: calls/${db.key}")
        db.child("offer").setValue(sdp.serialize())
    }
    fun sendAnswer(sdp: SessionDescription) = db.child("answer").setValue(sdp.serialize())
    // fun sendIceCandidate(c: IceCandidate)   = db.child("candidates").push().setValue(c.serialize())
    // ICE candidate sending is disabled for Non-Trickle ICE

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
        db.child("offer").addValueEventListener(offerListener!!)
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
        db.child("answer").addValueEventListener(answerListener!!)
    }

    // listenForIce function removed for Non-Trickle ICE
    
    /* ─── cleanup ──────────────────────────────────── */
    fun cleanup() {
        try {
            offerListener?.let { db.child("offer").removeEventListener(it) }
            answerListener?.let { db.child("answer").removeEventListener(it) }
            // iceListener removed for Non-Trickle ICE
            
            offerListener = null
            answerListener = null
            // iceListener = null
            
            // Clean up Firebase data
            db.removeValue()
        } catch (_: Exception) {
            // Log error but don't throw
        }
    }
}
