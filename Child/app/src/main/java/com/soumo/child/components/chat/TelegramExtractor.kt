package com.soumo.child.components.chat

import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest

class TelegramExtractor : ChatExtractor {
    override fun extractContactId(root: AccessibilityNodeInfo): String? {
        val titleNodes = safeFindByViewId(root, "org.telegram.messenger:id/action_bar_title") // TODO
        if (!titleNodes.isNullOrEmpty()) return titleNodes[0].text?.toString()
        return findFirstTopText(root)
    }

    override fun extractContactName(root: AccessibilityNodeInfo): String? = extractContactId(root)

    override fun extractVisibleMessages(root: AccessibilityNodeInfo): List<MessageData> {
        val msgs = mutableListOf<MessageData>()
        val list = safeFindByViewId(root, "org.telegram.messenger:id/message_list") // TODO
        if (!list.isNullOrEmpty()) {
            val parent = list[0]
            for (i in 0 until parent.childCount) {
                val child = parent.getChild(i) ?: continue
                // Typical telegram message text id might be "message_text"
                val textNodes = safeFindByViewId(child, "org.telegram.messenger:id/message_text")
                val text = textNodes?.firstOrNull()?.text?.toString()
                if (!text.isNullOrBlank()) {
                    val dir = if (child.findAccessibilityNodeInfosByText("You").isNullOrEmpty()) MessageDirection.IN else MessageDirection.OUT
                    msgs.add(MessageData(msgId = sha256Hex("$text|$dir"), text = text, direction = dir, ts = System.currentTimeMillis()))
                }
            }
            return msgs
        }
        return GenericChatExtractor("org.telegram.messenger").extractVisibleMessages(root)
    }

    override fun extractTypingState(root: AccessibilityNodeInfo): TypingState? {
        val matches = root.findAccessibilityNodeInfosByText("is typing")
        if (!matches.isNullOrEmpty()) return TypingState(true)
        return null
    }

    private fun findFirstTopText(root: AccessibilityNodeInfo): String? {
        val q = ArrayDeque<AccessibilityNodeInfo>(); q.add(root); var seen = 0
        while (q.isNotEmpty() && seen++ < 40) {
            val n = q.removeFirst()
            val txt = n.text?.toString()
            if (!txt.isNullOrBlank()) return txt
            for (i in 0 until n.childCount) n.getChild(i)?.let { q.add(it) }
        }
        return null
    }

    private fun safeFindByViewId(node: AccessibilityNodeInfo, id: String): List<AccessibilityNodeInfo>? {
        return try { node.findAccessibilityNodeInfosByViewId(id) } catch (_: Throwable) { null }
    }

    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
