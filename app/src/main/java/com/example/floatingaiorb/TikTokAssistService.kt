package com.example.floatingaiorb

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * On-device action helper. It navigates visible accessibility controls only after the user
 * explicitly asks for an action. Public sends are staged and confirmed before the final tap.
 */
class TikTokAssistService : AccessibilityService() {
    companion object {
        const val ACTION_ASSIST = "com.example.floatingaiorb.ASSIST_TIKTOK_COMMENT"
        private const val ACTION_CONFIRM = "com.example.floatingaiorb.CONFIRM_SEND"
        fun requestAssist(context: Context) = context.sendBroadcast(Intent(ACTION_ASSIST).setPackage(context.packageName))
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingType = ""
    private var pendingPayload = ""
    private var waitingForSend = false
    private var lastAttempt = 0L
    private val prefs by lazy { getSharedPreferences("orb", Context.MODE_PRIVATE) }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CONFIRM) confirmSend()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        reloadPending()
        val filter = IntentFilter(ACTION_CONFIRM)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(receiver, filter)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        reloadPending()
        if (pendingType.isBlank() || waitingForSend) return
        val now = System.currentTimeMillis()
        if (now - lastAttempt < 450) return
        lastAttempt = now
        handler.postDelayed({ assistVisibleScreen() }, 350)
    }

    override fun onInterrupt() = Unit
    override fun onDestroy() { runCatching { unregisterReceiver(receiver) }; super.onDestroy() }

    private fun reloadPending() {
        pendingType = prefs.getString("pending_action_type", "").orEmpty()
        pendingPayload = prefs.getString("pending_action_payload", "").orEmpty()
    }

    private fun assistVisibleScreen() {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString().orEmpty()
        when {
            pendingType.startsWith("tiktok") && !CommandEngine.isTikTokPackage(this, pkg) -> return
            pendingType.startsWith("whatsapp") && !CommandEngine.isWhatsAppPackage(this, pkg) -> return
        }
        when (pendingType) {
            "tiktok_search" -> doTikTokSearch(root)
            "tiktok_comment" -> doComment(root)
            "whatsapp_reply" -> doWhatsAppReply(root)
        }
    }

    private fun doTikTokSearch(root: AccessibilityNodeInfo) {
        val searchButton = findAny(root, listOf("search", "cari"))
        if (searchButton != null && findEditable(root) == null) {
            clickUp(searchButton)
            handler.postDelayed({ assistVisibleScreen() }, 700)
            return
        }
        val input = findEditable(root) ?: findAny(root, listOf("search", "cari", "telusuri")) ?: return
        setText(input, pendingPayload)
        waitingForSend = true
        val searchKey = findAny(root, listOf("search", "cari"))
        if (searchKey != null) clickUp(searchKey)
        else showActionConfirmation("Pencarian TikTok", pendingPayload, "Tindakan ini akan menjalankan pencarian menggunakan teks yang kamu berikan.")
    }

    private fun doComment(root: AccessibilityNodeInfo) {
        val commentButton = findAny(root, listOf("comment", "komentar", "comments"))
        if (commentButton != null && findEditable(root) == null) {
            clickUp(commentButton)
            handler.postDelayed({ assistVisibleScreen() }, 700)
            return
        }
        val input = findEditable(root) ?: findAny(root, listOf("add comment", "tambahkan komentar", "comment")) ?: return
        if (setText(input, pendingPayload)) {
            waitingForSend = true
            showActionConfirmation("Siap kirim komentar", pendingPayload, "Teks sudah dimasukkan. Pengiriman publik tetap menunggu konfirmasi kamu.")
        }
    }

    private fun doWhatsAppReply(root: AccessibilityNodeInfo) {
        val input = findEditable(root) ?: findAny(root, listOf("type a message", "ketik pesan", "message")) ?: return
        if (setText(input, pendingPayload)) {
            waitingForSend = true
            showActionConfirmation("Siap kirim pesan WhatsApp", pendingPayload, "Pesan sudah masuk ke kolom chat. Pengiriman tetap menunggu konfirmasi kamu.")
        }
    }

    private fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun showActionConfirmation(title: String, payload: String, message: String) {
        val intent = Intent(this, ConfirmSendActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("title", title)
            putExtra("comment", payload)
            putExtra("message", message)
        }
        startActivity(intent)
    }

    fun confirmSend() {
        val root = rootInActiveWindow ?: return
        val send = findAny(root, listOf("send", "kirim", "post", "search", "cari"))
        if (send != null) {
            clickUp(send)
            clearPending()
        } else {
            clearPending()
        }
    }

    private fun clearPending() {
        prefs.edit().remove("pending_action_type").remove("pending_action_payload").remove("pending_tiktok_comment").apply()
        pendingType = ""
        pendingPayload = ""
        waitingForSend = false
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
        val q = ArrayDeque<AccessibilityNodeInfo>(); q.add(root)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            val s = listOfNotNull(n.text?.toString(), n.contentDescription?.toString(), n.viewIdResourceName)
                .joinToString(" ").lowercase(Locale.getDefault())
            if (words.any { s.contains(it) } && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let(q::add)
        }
        return null
    }

    private fun clickUp(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        repeat(6) {
            if (n?.isClickable == true) return n!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n?.parent
        }
        return false
    }
}
