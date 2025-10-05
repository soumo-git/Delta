package com.soumo.child.signaling

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