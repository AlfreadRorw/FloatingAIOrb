package com.example.floatingaiorb

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.util.Locale

/**
 * Assistive accessibility helper. It never mass-comments and requires a visible user
 * confirmation before the final public Send action. TikTok UI changes frequently, so this
 * service searches by accessibility labels/text rather than fixed screen coordinates.
 */
class TikTokAssistService : AccessibilityService() {
    companion object {
        const val ACTION_ASSIST = "com.example.floatingaiorb.ASSIST_TIKTOK_COMMENT"
        fun requestAssist(context: Context) {
            context.sendBroadcast(Intent(ACTION_ASSIST).setPackage(context.packageName))
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private var pendingComment: String = ""
    private var waitingForSend = false
    private val confirmReceiver = object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) { if (intent?.action == "com.example.floatingaiorb.CONFIRM_SEND") confirmSend() } }

    override fun onServiceConnected() {
        super.onServiceConnected()
        pendingComment = getSharedPreferences("orb", Context.MODE_PRIVATE).getString("pending_tiktok_comment", "").orEmpty()
        val filter = IntentFilter("com.example.floatingaiorb.CONFIRM_SEND")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(confirmReceiver, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(confirmReceiver, filter)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString().orEmpty()
        if (pkg != "com.zhiliaoapp.musically") return
        pendingComment = getSharedPreferences("orb", Context.MODE_PRIVATE).getString("pending_tiktok_comment", "").orEmpty()
        if (pendingComment.isBlank() || waitingForSend) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ assist() }, 500)
    }
    override fun onInterrupt() {}
    override fun onDestroy() { runCatching { unregisterReceiver(confirmReceiver) }; super.onDestroy() }

    private fun assist() {
        val root = rootInActiveWindow ?: return
        // First try to open comments if the user asked for a comment flow.
        val commentButton = findAny(root, listOf("comment", "komentar", "comments"))
        if (commentButton != null && !findEditable(root)) {
            clickUp(commentButton); handler.postDelayed({ assist() }, 700); return
        }
        val input = findEditable(root) ?: findAny(root, listOf("add comment", "tambahkan komentar", "comment"))
        if (input == null) return
        val args = android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, pendingComment) }
        if (input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            waitingForSend = true
            showSendConfirmation()
        }
    }

    private fun showSendConfirmation() {
        val intent = Intent(this, ConfirmSendActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra("comment", pendingComment)
        startActivity(intent)
    }

    fun confirmSend() {
        val root = rootInActiveWindow ?: return
        val send = findAny(root, listOf("send", "kirim", "post"))
        if (send != null) {
            clickUp(send)
            clearPending()
        }
    }

    private fun clearPending() {
        getSharedPreferences("orb", Context.MODE_PRIVATE).edit().remove("pending_tiktok_comment").apply()
        pendingComment = ""; waitingForSend = false
    }

    private fun findEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val q = ArrayDeque<AccessibilityNodeInfo>(); q.add(root)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            if (n.isEditable) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let(q::add)
        }
        return null
    }
    private fun findAny(root: AccessibilityNodeInfo, words: List<String>): AccessibilityNodeInfo? {
        val q = ArrayDeque<AccessibilityNodeInfo>(); q.add(root)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            val s = listOfNotNull(n.text?.toString(), n.contentDescription?.toString(), n.viewIdResourceName).joinToString(" ").lowercase(Locale.getDefault())
            if (words.any { s.contains(it) } && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let(q::add)
        }
        return null
    }
    private fun clickUp(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        repeat(6) { if (n?.isClickable == true) return n!!.performAction(AccessibilityNodeInfo.ACTION_CLICK); n = n?.parent }
        return false
    }
}
