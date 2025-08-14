package com.soumo.child.signaling

import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

fun SessionDescription.serialize(): Map<String, String> {
    return mapOf(
        "type" to this.type.canonicalForm(),
        "sdp" to this.description
    )
}

fun Map<*, *>.toSessionDescription(): SessionDescription {
    val type = this["type"] as? String ?: return SessionDescription(SessionDescription.Type.OFFER, "")
    val sdp = this["sdp"] as? String ?: return SessionDescription(SessionDescription.Type.OFFER, "")
    return SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)
}

fun IceCandidate.serialize(): Map<String, Any> {
    return mapOf(
        "sdpMid" to this.sdpMid,
        "sdpMLineIndex" to this.sdpMLineIndex,
        "sdp" to this.sdp
    )
}

fun Map<*, *>.toIceCandidate(): IceCandidate {
    val sdpMid = this["sdpMid"] as? String ?: ""
    val sdpMLineIndex = (this["sdpMLineIndex"] as? Long)?.toInt() ?: 0
    val sdp = this["sdp"] as? String ?: ""
    return IceCandidate(sdpMid, sdpMLineIndex, sdp)
}
