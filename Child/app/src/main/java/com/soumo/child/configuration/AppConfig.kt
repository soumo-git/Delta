package com.soumo.child.configuration

/**
 * Application configuration constants
 * Centralized configuration for better maintainability
 */
object AppConfig {

    // Firebase Configuration
    object Firebase {
        const val DATABASE_URL = "USE YOUR OWN FIREBASE DATABASE_URL"
    }

    // WebRTC Configuration
    object WebRTC {
        // STUN server configuration for NAT traversal
        val STUN_SERVERS = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
            "stun:stun2.l.google.com:19302",
            "stun:stun3.l.google.com:19302",
            "stun:stun4.l.google.com:19302",
            "stun:stun.ekiga.net",
            "stun:stun.ideasip.com",
            "stun:stun.schlund.de",
            "stun:stun.stunprotocol.org",
            "stun:stun.voiparound.com",
            "stun:stun.voipbuster.com",
            "stun:stun.voipstunt.com",
            "stun:stun.voxgratia.org",
            "stun:stun.xten.com"
        )
    }
}