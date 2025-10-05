package com.soumo.child.components.chat

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Chat payload models for DataChannel.
 */

@Serializable
data class ChatPayload(
    val version: Int = 1,
    val type: PayloadType,
    val ts: Long = System.currentTimeMillis(),
    val childId: String, // set by Child app MainActivity/Service
    val body: PayloadBody
)

@Serializable
sealed class PayloadBody

@Serializable
@Suppress("unused") // Sent when the app/window changed in the foreground
data class AppForegroundBody(
    val packageName: String,
    val contactId: String? = null,
    val contactName: String? = null,
    val visibleMessages: List<MessageData> = emptyList()
) : PayloadBody()

@Serializable
data class MessageEventBody(
    val app: String,
    val contactId: String,
    val contactName: String? = null,
    val messages: List<MessageData>
) : PayloadBody()

@Serializable
data class TypingEventBody(
    val app: String,
    val contactId: String,
    val contactName: String? = null,
    val isTyping: Boolean
) : PayloadBody()

@Serializable
data class MessageData(
    val msgId: String,         // sha256 hash
    val text: String? = null,
    val type: MessageType = MessageType.TEXT,
    val direction: MessageDirection,
    val ts: Long,              // epoch millis (device best-effort)
    val extra: Map<String, String> = emptyMap()
)

@Suppress("unused") // keep extra kinds for future media handling; not all projects will use them immediately
enum class MessageType { TEXT, IMAGE, STICKER, MEDIA }

enum class MessageDirection { IN, OUT }

object ChatJson {
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun toJson(payload: ChatPayload) = json.encodeToString(ChatPayload.serializer(), payload)

    @Suppress("unused") // may be used later when receiving payloads from parent or for debugging
    fun fromJson(s: String) = json.decodeFromString(ChatPayload.serializer(), s)
}
