package com.alfread.statusdownloader.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

/** Parsing teks notifikasi WhatsApp (Indonesia & Inggris). */
object MessageParser {
    private val DELETED = Regex(
        "(?i)^[^\\p{L}\\p{N}]*(this message was deleted|pesan ini (telah )?(dihapus|ditarik))[.!]?\\s*$"
    )
    private val NOISE = Regex(
        "(?i)(^\\d+ (new )?(messages?|pesan( baru)?)( from .*)?$)|checking for new messages|" +
            "memeriksa pesan baru|whatsapp web is currently active|whatsapp web sedang aktif|backup in progress"
    )
    private val IMG = Regex("(photo|foto|image|gambar)( \\(\\d+\\))?")
    private val VID = Regex("(video|gif)( \\(\\d+:\\d+\\))?")
    private val VOICE = Regex("(voice message|pesan suara)( \\(\\d+:\\d+\\))?")
    private val AUD = Regex("audio( \\(\\d+:\\d+\\))?")
    private val DOC = Regex("(document|dokumen)( \\(\\d+ (pages?|halaman)\\))?")
    private val STICKER = Regex("(sticker|stiker)")

    fun isDeleted(text: String) = DELETED.containsMatchIn(text.trim())
    fun isNoise(text: String) = NOISE.containsMatchIn(text.trim())

    fun cleanTitle(title: String) =
        title.replace(Regex("\\s*\\(\\d+ (messages?|pesan)\\)$", RegexOption.IGNORE_CASE), "").trim()

    fun fileName(text: String) = text.replace(Regex("^[^\\p{L}\\p{N}]+"), "").trim()

    fun detectType(raw: String): MsgType {
        val t = raw.trim()
        val l = t.lowercase()
        return when {
            t.startsWith("📷") || IMG.matches(l) -> MsgType.IMAGE
            t.startsWith("🎥") || t.startsWith("📹") || VID.matches(l) -> MsgType.VIDEO
            t.startsWith("🎤") || VOICE.matches(l) -> MsgType.VOICE
            t.startsWith("🎧") || t.startsWith("🎵") || AUD.matches(l) -> MsgType.AUDIO
            t.startsWith("📄") || t.startsWith("📎") || DOC.matches(l) -> MsgType.DOCUMENT
            STICKER.matches(l) -> MsgType.STICKER
            t.startsWith("📍") || t.startsWith("👤") -> MsgType.OTHER
            else -> MsgType.TEXT
        }
    }
}

data class BackedUp(val type: MsgType, val original: File, val backup: File, val modified: Long)

/** Menyalin media WhatsApp yang baru masuk ke penyimpanan Alfread. */
object MediaBackup {

    fun backupRoot(ctx: Context): File =
        (ctx.getExternalFilesDir("backup") ?: File(ctx.filesDir, "backup")).also { it.mkdirs() }

    private fun folders(label: String): Map<MsgType, List<String>> = mapOf(
        MsgType.IMAGE to listOf("$label Images"),
        MsgType.VIDEO to listOf("$label Video", "$label Animated Gifs"),
        MsgType.VOICE to listOf("$label Voice Notes"),
        MsgType.AUDIO to listOf("$label Audio"),
        MsgType.DOCUMENT to listOf("$label Documents")
    )

    fun scan(ctx: Context, since: Long): List<BackedUp> {
        val root = Environment.getExternalStorageDirectory()
        val out = mutableListOf<BackedUp>()
        for (business in listOf(false, true)) {
            val pkg = if (business) "com.whatsapp.w4b" else "com.whatsapp"
            val label = if (business) "WhatsApp Business" else "WhatsApp"
            val bases = listOf(File(root, "Android/media/$pkg/$label/Media"), File(root, "$label/Media"))
            for (base in bases) {
                for ((type, names) in folders(label)) {
                    for (name in names) {
                        val dir = File(base, name)
                        if (!dir.isDirectory) continue
                        val destDir = File(backupRoot(ctx), type.name.lowercase()).apply { mkdirs() }
                        dir.walkTopDown()
                            .maxDepth(3)
                            .onEnter { it.name != "Sent" && it.name != "Private" && !it.name.startsWith(".") }
                            .filter { it.isFile && !it.name.startsWith(".") && it.length() > 0 && it.lastModified() >= since }
                            .forEach { f ->
                                val dest = File(destDir, "${f.lastModified()}_${f.name}")
                                if (!dest.exists()) runCatching { f.copyTo(dest) }.onFailure { dest.delete() }
                                if (dest.exists()) out += BackedUp(type, f, dest, f.lastModified())
                            }
                    }
                }
            }
        }
        return out
    }

    /** Pindai sejak pemindaian terakhir (dipanggil saat aplikasi dibuka). */
    fun runScan(ctx: Context): List<BackedUp> {
        val p = ctx.getSharedPreferences("backup", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = p.getLong("last", 0L)
        if (last == 0L) {
            p.edit().putLong("last", now).apply()
            return emptyList()
        }
        val res = scan(ctx, last - 5_000)
        p.edit().putLong("last", now).apply()
        return res
    }
}

/** Penyimpanan catatan chat (dipakai bersama oleh service notifikasi dan UI). */
object ChatRepository {
    private const val MAX = 4000
    private val lock = Any()
    private var appCtx: Context? = null
    private var loaded = false
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    fun init(ctx: Context) {
        synchronized(lock) {
            if (loaded) return
            appCtx = ctx.applicationContext
            _messages.value = readFile()
            loaded = true
        }
    }

    private fun file(): File? = appCtx?.let { File(it.filesDir, "messages.json") }

    private fun readFile(): List<ChatMessage> {
        val f = file() ?: return emptyList()
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ChatMessage(
                    id = o.getString("id"),
                    chat = o.getString("chat"),
                    sender = o.optString("sender", ""),
                    text = o.optString("text", ""),
                    type = runCatching { MsgType.valueOf(o.getString("type")) }.getOrDefault(MsgType.TEXT),
                    time = o.getLong("time"),
                    origTime = o.optLong("orig", o.getLong("time")),
                    app = o.optString("app", "com.whatsapp"),
                    deleted = o.optBoolean("deleted", false),
                    deletedAt = o.optLong("deletedAt", 0L),
                    mediaPath = if (o.has("media") && !o.isNull("media")) o.getString("media") else null
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeFile(list: List<ChatMessage>) {
        val f = file() ?: return
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(
                JSONObject()
                    .put("id", m.id).put("chat", m.chat).put("sender", m.sender).put("text", m.text)
                    .put("type", m.type.name).put("time", m.time).put("orig", m.origTime)
                    .put("app", m.app).put("deleted", m.deleted).put("deletedAt", m.deletedAt)
                    .put("media", m.mediaPath ?: JSONObject.NULL)
            )
        }
        val tmp = File(f.parentFile, "messages.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(f)
    }

    private fun persistAsync() {
        ioScope.launch { writeMutex.withLock { runCatching { writeFile(_messages.value) } } }
    }

    private fun mutate(block: (List<ChatMessage>) -> List<ChatMessage>) {
        synchronized(lock) { _messages.value = block(_messages.value).takeLast(MAX) }
        persistAsync()
    }

    private fun deleteFiles(paths: List<String>) {
        if (paths.isEmpty()) return
        ioScope.launch { paths.forEach { runCatching { File(it).delete() } } }
    }

    fun add(msg: ChatMessage): Boolean {
        var added = false
        mutate { list ->
            val dup = list.any {
                it.app == msg.app && it.chat == msg.chat && it.origTime == msg.origTime && it.text == msg.text
            }
            if (dup) list else { added = true; list + msg }
        }
        return added
    }

    /**
     * Tandai pesan sebagai dihapus. Prioritas: cocokkan waktu asli pesan; jika tidak ada
     * dan [fallbackLatest] true, tandai pesan terakhir dari chat yang sama.
     */
    fun markDeleted(app: String, chat: String, times: List<Long>, fallbackLatest: Boolean, now: Long): Int {
        var count = 0
        mutate { list ->
            val out = list.toMutableList()
            for (t in times) {
                val idx = out.indexOfLast {
                    !it.deleted && it.app == app && it.chat == chat && abs(it.origTime - t) <= 1500
                }
                if (idx >= 0) { out[idx] = out[idx].copy(deleted = true, deletedAt = now); count++ }
            }
            if (count == 0 && fallbackLatest) {
                val idx = out.indexOfLast {
                    !it.deleted && it.app == app && it.chat == chat && now - it.time < 3L * 24 * 3600 * 1000
                }
                if (idx >= 0) { out[idx] = out[idx].copy(deleted = true, deletedAt = now); count++ }
            }
            out
        }
        return count
    }

    fun linkMedia(files: List<BackedUp>) {
        if (files.isEmpty()) return
        mutate { list ->
            val used = list.mapNotNull { it.mediaPath }.toMutableSet()
            list.map { m ->
                if (m.mediaPath != null || !m.type.isMedia) return@map m
                val wanted = MessageParser.fileName(m.text).lowercase()
                val cands = files.filter { f ->
                    f.type == m.type && f.backup.absolutePath !in used &&
                        f.modified >= m.time - 20_000 && f.modified <= m.time + 180_000
                }
                val pick = (if (m.type == MsgType.DOCUMENT) cands.firstOrNull { it.original.name.lowercase() == wanted } else null)
                    ?: cands.minByOrNull { abs(it.modified - m.time) }
                if (pick == null) m else {
                    used += pick.backup.absolutePath
                    m.copy(mediaPath = pick.backup.absolutePath)
                }
            }
        }
    }

    fun isLinked(id: String) = _messages.value.any { it.id == id && it.mediaPath != null }

    fun delete(id: String, deleteFile: Boolean) {
        val path = _messages.value.firstOrNull { it.id == id }?.mediaPath
        mutate { list -> list.filterNot { it.id == id } }
        if (deleteFile && path != null) deleteFiles(listOf(path))
    }

    fun clearAll() {
        val paths = _messages.value.mapNotNull { it.mediaPath }
        mutate { emptyList() }
        deleteFiles(paths)
    }

    fun purge(days: Int) {
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        val old = _messages.value.filter { it.time < cutoff }
        if (old.isEmpty()) return
        mutate { list -> list.filter { it.time >= cutoff } }
        deleteFiles(old.mapNotNull { it.mediaPath })
    }
}
