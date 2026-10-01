package com.alfread.statusdownloader.notification

import android.app.Notification
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.os.Bundle
import com.alfread.statusdownloader.data.DeletedMessage
import com.alfread.statusdownloader.data.DeletedMessageStore
import com.alfread.statusdownloader.data.DeletedMessageType
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class WhatsAppNotificationListener : NotificationListenerService() {

    private lateinit var store: DeletedMessageStore

    override fun onCreate() {
        super.onCreate()
        store = DeletedMessageStore(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return

        val n = sbn.notification ?: return
        val extras = n.extras ?: Bundle.EMPTY

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val content = bigText.ifBlank { text }

        if (title.isBlank() && content.isBlank()) return

        val deleted = isDeletedMarker(content)
        val type = detectType(content, extras)

        // Avoid repeatedly saving identical notification updates.
        val old = store.load()
        val now = System.currentTimeMillis()
        val duplicate = old.any {
            it.sender == title &&
            it.text == content &&
            it.packageName == pkg &&
            now - it.time < 2500L
        }
        if (duplicate) return

        var list = old.toMutableList()

        if (deleted) {
            // WhatsApp normally exposes only the deletion marker. We cannot
            // recover server-side content; mark the most recent archived item
            // from this conversation as possibly deleted.
            val idx = list.indexOfFirst {
                it.packageName == pkg &&
                it.sender == title &&
                !it.deletedMarker &&
                now - it.time in 0..300000L
            }
            if (idx >= 0) {
                val previous = list[idx]
                list[idx] = previous.copy(deletedMarker = true)
            }
        }

        val mediaPath = saveNotificationPicture(n, now)

        list.add(
            DeletedMessage(
                id = UUID.randomUUID().toString(),
                packageName = pkg,
                sender = title.ifBlank { "WhatsApp" },
                text = content,
                type = type,
                time = now,
                deletedMarker = deleted,
                mediaPath = mediaPath
            )
        )

        store.save(list.sortedByDescending { it.time })
    }

    private fun isDeletedMarker(text: String): Boolean {
        val t = text.lowercase()
        return t.contains("this message was deleted") ||
            t.contains("message was deleted") ||
            t.contains("pesan ini telah dihapus") ||
            t.contains("pesan ini dihapus") ||
            t.contains("pesan telah dihapus") ||
            t.contains("anda menghapus pesan")
    }

    private fun detectType(text: String, extras: Bundle): DeletedMessageType {
        val t = text.lowercase()
        return when {
            t.contains("voice message") || t.contains("pesan suara") ||
                t.contains("voice note") || t.contains("vn") || t.contains("audio") ->
                DeletedMessageType.VOICE
            t.contains("photo") || t.contains("foto") || t.contains("image") ||
                t.contains("gambar") || extras.containsKey(Notification.EXTRA_PICTURE) ->
                DeletedMessageType.PHOTO
            t.contains("video") -> DeletedMessageType.VIDEO
            t.contains("document") || t.contains("dokumen") || t.contains("pdf") ||
                t.contains(".doc") || t.contains(".xls") || t.contains(".zip") ->
                DeletedMessageType.DOCUMENT
            t.isNotBlank() -> DeletedMessageType.CHAT
            else -> DeletedMessageType.UNKNOWN
        }
    }

    private fun saveNotificationPicture(notification: Notification, time: Long): String? {
        val bitmap = notification.extras?.getParcelable<Bitmap>(Notification.EXTRA_PICTURE) ?: return null
        return runCatching {
            val dir = File(filesDir, "notification_media")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "wa_${time}_${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.absolutePath
        }.getOrNull()
    }
}
