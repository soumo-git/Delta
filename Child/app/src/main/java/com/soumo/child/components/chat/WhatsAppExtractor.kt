package com.soumo.child.components.chat

import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest

class WhatsAppExtractor : ChatExtractor {
    override fun extractContactId(root: AccessibilityNodeInfo): String? {
        // Common fast path: toolbar title
        val titleNodes = safeFindByViewId(root, "com.whatsapp:id/toolbar_title")
        if (!titleNodes.isNullOrEmpty()) return titleNodes[0].text?.toString()
        // fallback: first text node in top bar
        return findFirstTopText(root)
    }

    override fun extractContactName(root: AccessibilityNodeInfo): String? = extractContactId(root)

    override fun extractVisibleMessages(root: AccessibilityNodeInfo): List<MessageData> {
        val msgs = mutableListOf<MessageData>()
        // Replace with actual WhatsApp recycler/list id
        val list = safeFindByViewId(root, "com.whatsapp:id/message_list") // TODO_VIEW_ID_WHATSAPP_MESSAGE_LIST
        if (!list.isNullOrEmpty()) {
            val parent = list[0]
            for (i in 0 until parent.childCount) {
                val child = parent.getChild(i) ?: continue
                val textNodes = safeFindByViewId(child, "com.whatsapp:id/message_text") // TODO_VIEW_ID_WHATSAPP_MESSAGE_TEXT
                if (!textNodes.isNullOrEmpty()) {
                    val text = textNodes[0].text?.toString()
                    if (!text.isNullOrBlank()) {
                        val outgoingNodes = safeFindByViewId(child, "com.whatsapp:id/outgoing_tag") // TODO_VIEW_ID_WHATSAPP_OUTGOING_TAG
                        val dir = if (outgoingNodes.isNullOrEmpty()) MessageDirection.IN else MessageDirection.OUT
                        msgs.add(MessageData(msgId = sha256Hex("$text|$dir"), text = text, direction = dir, ts = System.currentTimeMillis()))
                    }
                }
            }
            return msgs
        }
        // fallback: shallow scan
        return GenericChatExtractor("com.whatsapp").extractVisibleMessages(root)
    }

    override fun extractTypingState(root: AccessibilityNodeInfo): TypingState? {
        val typing = safeFindByViewId(root, "com.whatsapp:id/typing") // TODO_VIEW_ID_WHATSAPP_TYPING
        if (!typing.isNullOrEmpty()) return TypingState(true)
        return null
    }

    // helpers
    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun findFirstTopText(root: AccessibilityNodeInfo): String? {
        // try to get one of the first visible text nodes, shallow
        val max = 20
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        var seen = 0
        while (q.isNotEmpty() && seen < max) {
            val n = q.removeFirst()
            seen++
            val txt = n.text?.toString()
            if (!txt.isNullOrBlank()) return txt
            for (i in 0 until n.childCount) n.getChild(i)?.let { q.add(it) }
        }
        return null
    }

    private fun safeFindByViewId(node: AccessibilityNodeInfo, id: String): List<AccessibilityNodeInfo>? {
        return try {
            node.findAccessibilityNodeInfosByViewId(id)
        } catch (_: Throwable) {
            null
        }
    }
}
