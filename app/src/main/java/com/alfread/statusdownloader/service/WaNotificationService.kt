package com.alfread.statusdownloader.service

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.alfread.statusdownloader.data.ChatMessage
import com.alfread.statusdownloader.data.ChatRepository
import com.alfread.statusdownloader.data.MediaBackup
import com.alfread.statusdownloader.data.MessageParser
import com.alfread.statusdownloader.data.Permissions
import com.alfread.statusdownloader.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Membaca notifikasi WhatsApp di perangkat ini, menyimpan salinan pesan,
 * dan menandai pesan yang dihapus pengirim. Data tidak dikirim ke mana pun.
 */
class WaNotificationService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ChatRepository.init(applicationContext)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        runCatching { handle(sbn) }
    }

    @Suppress("DEPRECATION")
    private fun handle(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val category = n.category
        if (category != null && category != Notification.CATEGORY_MESSAGE) return

        val settings = SettingsStore(this).load()
        if (!settings.chatLogEnabled) return

        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isEmpty() || text.isEmpty()) return
        if (title.equals("WhatsApp", true) || title.equals("WhatsApp Business", true)) return
        if (MessageParser.isNoise(text)) return

        val conv = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
        val isGroup = !conv.isNullOrEmpty() || extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        if (isGroup && !settings.logGroups) return

        val chat = MessageParser.cleanTitle(if (!conv.isNullOrEmpty()) conv else title)
        var sender = chat
        var body = text
        if (isGroup) {
            val i = text.indexOf(": ")
            if (i in 1..40) {
                sender = text.substring(0, i)
                body = text.substring(i + 2).trim()
            } else if (title != chat) {
                sender = title
            }
        }

        // Riwayat pesan di notifikasi (berisi waktu asli tiap pesan)
        val bundles = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            ?.mapNotNull { it as? Bundle }.orEmpty()
        val now = System.currentTimeMillis()

        val deletedTimes = bundles
            .filter { MessageParser.isDeleted(it.getCharSequence("text")?.toString().orEmpty()) }
            .map { it.getLong("time") }
            .filter { it > 0 }
        val mainDeleted = MessageParser.isDeleted(body)
        if (mainDeleted || deletedTimes.isNotEmpty()) {
            ChatRepository.markDeleted(pkg, chat, deletedTimes, mainDeleted, now)
        }
        if (mainDeleted) return

        val origTime = bundles.lastOrNull()?.getLong("time")?.takeIf { it > 0 } ?: sbn.postTime
        val type = MessageParser.detectType(body)
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            chat = chat,
            sender = sender,
            text = body,
            type = type,
            time = sbn.postTime,
            origTime = origTime,
            app = pkg,
            deleted = false,
            deletedAt = 0L,
            mediaPath = null
        )
        val added = ChatRepository.add(msg)

        if (added && type.isMedia && settings.autoBackupMedia && Permissions.hasAccess(this)) {
            val since = sbn.postTime - 30_000
            val id = msg.id
            val ctx = applicationContext
            scope.launch {
                // Media butuh beberapa detik untuk terunduh; coba beberapa kali.
                for (wait in longArrayOf(6_000, 20_000, 60_000)) {
                    delay(wait)
                    ChatRepository.linkMedia(MediaBackup.scan(ctx, since))
                    if (ChatRepository.isLinked(id)) break
                }
            }
        }
    }
}
