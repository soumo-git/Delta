package com.soumo.child.components.chat

import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest

class InstagramExtractor : ChatExtractor {
    override fun extractContactId(root: AccessibilityNodeInfo): String? {
        // Instagram DM thread title id (placeholder)
        val title = safeFindByViewId(root, "com.instagram.android:id/action_bar_title")
        if (!title.isNullOrEmpty()) return title[0].text?.toString()
        return findFirstTopText(root) ?: "ig_unknown"
    }

    override fun extractContactName(root: AccessibilityNodeInfo): String? = extractContactId(root)

    override fun extractVisibleMessages(root: AccessibilityNodeInfo): List<MessageData> {
        // IG uses RecyclerView for messages; view ids are volatile.
        val msgs = mutableListOf<MessageData>()
        val list = safeFindByViewId(root, "com.instagram.android:id/recycler_view") // TODO_VIEW_ID_IG_RECYCLER
        if (!list.isNullOrEmpty()) {
            val parent = list[0]
            for (i in 0 until parent.childCount) {
                val child = parent.getChild(i) ?: continue
                val textNodes = child.findAccessibilityNodeInfosByText("") // fallback: collect any text
                val text = textNodes?.firstOrNull { !it.text.isNullOrBlank() }?.text?.toString()
                if (!text.isNullOrBlank()) {
                    val dir = inferDirectionByClass(child)
                    msgs.add(MessageData(msgId = sha256Hex("$text|$dir"), text = text, direction = dir, ts = System.currentTimeMillis()))
                }
            }
            return msgs
        }
        return GenericChatExtractor("com.instagram.android").extractVisibleMessages(root)
    }

    override fun extractTypingState(root: AccessibilityNodeInfo): TypingState? {
        // IG typing indicator sometimes shows "Typing..." text
        val matches = root.findAccessibilityNodeInfosByText("typing")
        if (!matches.isNullOrEmpty()) return TypingState(true)
        return null
    }

    // helpers
    private fun inferDirectionByClass(node: AccessibilityNodeInfo): MessageDirection {
        val cls = node.className?.toString() ?: ""
        return if (cls.contains("outgoing", true) || cls.contains("right", true)) MessageDirection.OUT else MessageDirection.IN
    }

    private fun findFirstTopText(root: AccessibilityNodeInfo): String? {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        var seen = 0
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
