package com.soumo.child.components.chat

import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest

class SnapchatExtractor : ChatExtractor {
    override fun extractContactId(root: AccessibilityNodeInfo): String? {
        val title = safeFindByViewId(root, "com.snapchat.android:id/toolbar_title")
        if (!title.isNullOrEmpty()) return title[0].text?.toString()
        return findFirstTopText(root)
    }

    override fun extractContactName(root: AccessibilityNodeInfo): String? = extractContactId(root)

    override fun extractVisibleMessages(root: AccessibilityNodeInfo): List<MessageData> {
        // Snapchat's chat UI often includes 'bubble_text' or similar
        val list = safeFindByViewId(root, "com.snapchat.android:id/bubble_text") // TODO
        if (!list.isNullOrEmpty()) {
            return list.mapNotNull {
                val txt = it.text?.toString()
                if (!txt.isNullOrBlank()) {
                    MessageData(msgId = sha256Hex(txt), text = txt, direction = MessageDirection.IN, ts = System.currentTimeMillis())
                } else null
            }
        }
        return GenericChatExtractor("com.snapchat.android").extractVisibleMessages(root)
    }

    override fun extractTypingState(root: AccessibilityNodeInfo): TypingState? {
        val matches = root.findAccessibilityNodeInfosByText("typing")
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
