package com.example.floatingaiorb

import android.accessibilityservice.AccessibilityService
import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/** Accessibility assistant for user-requested TikTok actions. Public sending requires confirmation. */
class TikTokAssistService : AccessibilityService() {
    companion object {
        const val ACTION_ASSIST = "com.example.floatingaiorb.ASSIST_TIKTOK_ACTION"
        const val ACTION_CONFIRM = "com.example.floatingaiorb.CONFIRM_SEND"
        fun requestAssist(context: Context) = context.sendBroadcast(Intent(ACTION_ASSIST).setPackage(context.packageName))
    }
    private val handler = Handler(Looper.getMainLooper())
    private var receiverRegistered = false
    private var waitingForConfirm = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { if (intent?.action == ACTION_CONFIRM) confirmSend() }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        runCatching {
            val filter = IntentFilter(ACTION_CONFIRM)
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(receiver, filter)
            receiverRegistered = true
        }
        handler.postDelayed({ assist() }, 350)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString().orEmpty()
        if (!CommandEngine.isTikTokPackage(this, pkg)) return
        if (!hasPendingAction() || waitingForConfirm) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ assist() }, 450)
    }

    override fun onInterrupt() {}
    override fun onDestroy() { if (receiverRegistered) runCatching { unregisterReceiver(receiver) }; super.onDestroy() }

    private fun hasPendingAction(): Boolean = getSharedPreferences("orb", MODE_PRIVATE).getString("tiktok_action", "").orEmpty().isNotBlank()

    private fun assist() {
        val prefs = getSharedPreferences("orb", MODE_PRIVATE)
        val action = prefs.getString("tiktok_action", "").orEmpty()
        if (action.isBlank()) return
        val root = rootInActiveWindow ?: return
        when (action) {
            "search" -> doSearch(root, prefs.getString("pending_tiktok_query", "").orEmpty())
            "comment" -> doComment(root, prefs.getString("pending_tiktok_comment", "").orEmpty())
            "reply" -> doReply(root, prefs.getString("pending_tiktok_target", "").orEmpty(), prefs.getString("pending_tiktok_reply", "").orEmpty())
        }
    }

    private fun doSearch(root: AccessibilityNodeInfo, query: String) {
        if (query.isBlank()) return clearAction()
        val input = findEditable(root)
        if (input != null) {
            setText(input, query)
            submitSearch(input, root)
            handler.postDelayed({ clearAction() }, 1200)
            return
        }
        val searchButton = findAny(root, listOf("search", "cari", "search tab"))
        if (searchButton != null) { clickUp(searchButton); handler.postDelayed({ assist() }, 850) }
    }

    private fun submitSearch(input: AccessibilityNodeInfo, root: AccessibilityNodeInfo): Boolean {
        // ACTION_IME_ENTER is exposed through AccessibilityAction from API 30.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val action = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER
            if (input.performAction(action.id)) return true
        }
        // Fallback for older Android versions and apps that ignore the IME action.
        val submit = findAny(root, listOf("search", "cari", "go", "submit", "done", "selesai"))
        return submit?.let { clickUp(it) } ?: false
    }

    private fun doComment(root: AccessibilityNodeInfo, comment: String) {
        if (comment.isBlank()) return clearAction()
        val input = findEditable(root)
        if (input != null) {
            setText(input, comment)
            waitingForConfirm = true
            showConfirm(comment)
            return
        }
        val button = findAny(root, listOf("comment", "komentar", "comments"))
        if (button != null) { clickUp(button); handler.postDelayed({ assist() }, 750) }
    }

    private fun doReply(root: AccessibilityNodeInfo, target: String, reply: String) {
        if (reply.isBlank()) return clearAction()
        val input = findEditable(root)
        if (input != null) {
            setText(input, reply)
            waitingForConfirm = true
            showConfirm("Balas $target: $reply")
            return
        }
        // Best effort navigation: Inbox > target chat > composer.
        val inbox = findAny(root, listOf("inbox", "kotak masuk", "pesan", "messages"))
        if (inbox != null) { clickUp(inbox); handler.postDelayed({ assist() }, 900); return }
        if (target.isNotBlank()) {
            val person = findAny(root, listOf(target))
            if (person != null) { clickUp(person); handler.postDelayed({ assist() }, 850); return }
        }
    }

    private fun setText(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun showConfirm(text: String) {
        startActivity(Intent(this, ConfirmSendActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("comment", text)
        })
    }

    private fun confirmSend() {
        val root = rootInActiveWindow ?: return
        val send = findAny(root, listOf("send", "kirim", "post", "sent"))
        if (send != null) clickUp(send)
        clearAction()
        waitingForConfirm = false
    }

    private fun clearAction() {
        getSharedPreferences("orb", MODE_PRIVATE).edit()
            .remove("tiktok_action").remove("pending_tiktok_comment").remove("pending_tiktok_query")
            .remove("pending_tiktok_target").remove("pending_tiktok_reply").apply()
        waitingForConfirm = false
    }

    private fun findEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val q = ArrayDeque<AccessibilityNodeInfo>(); q.add(root)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            if (n.isEditable && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let(q::add)
        }
        return null
    }

    private fun findAny(root: AccessibilityNodeInfo, words: List<String>): AccessibilityNodeInfo? {
        val wanted = words.map { it.lowercase(Locale.getDefault()) }
        val q = ArrayDeque<AccessibilityNodeInfo>(); q.add(root)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            val s = listOfNotNull(n.text?.toString(), n.contentDescription?.toString(), n.viewIdResourceName)
                .joinToString(" ").lowercase(Locale.getDefault())
            if (n.isVisibleToUser && wanted.any { w -> s.contains(w) }) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let(q::add)
        }
        return null
    }

    private fun clickUp(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        repeat(8) {
            if (n?.isClickable == true) return n!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n?.parent
        }
        return false
    }
}
