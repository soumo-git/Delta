package com.soumo.child.components.chat

import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest

interface ChatExtractor {
    fun extractContactId(root: AccessibilityNodeInfo): String?
    fun extractContactName(root: AccessibilityNodeInfo): String?
    fun extractVisibleMessages(root: AccessibilityNodeInfo): List<MessageData>
    fun extractTypingState(root: AccessibilityNodeInfo): TypingState?
}

/**
 * Generic fallback extractor — shallow tree walk, collects text nodes with heuristics.
 * Keep traversal bounded to avoid CPU blowups.
 */
class GenericChatExtractor(private val pkg: String) : ChatExtractor {
    override fun extractContactId(root: AccessibilityNodeInfo): String? {
        // best-effort: use package + top toolbar title
        return extractContactName(root)?.let { "$pkg|$it" }
    }
    override fun extractContactName(root: AccessibilityNodeInfo): String? {
        // heuristics: find top toolbar title nodes
        root.findAccessibilityNodeInfosByText("") // placeholder
        // implement robust logic: find first node with contentDescription or className Toolbar/TextView in top area
        return null
    }
    override fun extractVisibleMessages(root: AccessibilityNodeInfo): List<MessageData> {
        val msgs = ArrayList<MessageData>()
        // limited BFS: only first N nodes
        val maxNodes = 200
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        var seen = 0
        while (q.isNotEmpty() && seen < maxNodes) {
            val n = q.removeFirst()
            seen++
            val txt = n.text?.toString()
            val className = n.className?.toString()
            if (!txt.isNullOrBlank() && (className?.contains("TextView") == true || className?.contains("View") == true)) {
                // naive: assume visible message
                val direction = if (isOutgoingNode(n)) MessageDirection.OUT else MessageDirection.IN
                val md = MessageData(
                    msgId = sha256Hex("$pkg|${txt.hashCode()}|$seen"),
                    text = txt,
                    direction = direction,
                    ts = System.currentTimeMillis()
                )
                msgs.add(md)
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { child ->
                    q.add(child)
                }
            }
        }
        return msgs
    }
    override fun extractTypingState(root: AccessibilityNodeInfo): TypingState? {
        // generic: look for "typing" text
        val matches = root.findAccessibilityNodeInfosByText("typing")
        if (!matches.isNullOrEmpty()) return TypingState(true)
        return null
    }

    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun isOutgoingNode(node: AccessibilityNodeInfo): Boolean {
        // heuristic: many apps mark outgoing messages with drawable or different structure; fallback random
        return node.className?.toString()?.contains("outgoing") ?: false
    }
}
