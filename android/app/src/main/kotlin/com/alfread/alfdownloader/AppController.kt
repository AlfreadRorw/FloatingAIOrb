package com.alfread.alfdownloader

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alfread.alfdownloader.data.HistoryStore
import com.alfread.alfdownloader.data.SettingsStore
import com.alfread.alfdownloader.model.*
import com.alfread.alfdownloader.network.Api
import com.alfread.alfdownloader.notify.Notifier
import com.alfread.alfdownloader.termux.TermuxRunner
import com.alfread.alfdownloader.ui.Tab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val URL_RE = Regex("""https?://[^\s<>"']+""")

fun extractUrl(text: String): String? =
    URL_RE.find(text)?.value?.trimEnd('.', ',', ')', ']', '!', '?')

fun looksLikeUrl(text: String): Boolean {
    val t = text.trim()
    return t.length > 10 && (t.startsWith("http://") || t.startsWith("https://")) && !t.contains(' ')
}

class AppController(private val context: Context, private val scope: CoroutineScope) {
    private val settings = SettingsStore(context)
    private val history = HistoryStore(context)
    val termux = TermuxRunner(context)

    var prefs by mutableStateOf(settings.load())
    private var api = Api(prefs.serverUrl)

    var tab by mutableStateOf(Tab.DOWNLOAD)
    var online by mutableStateOf(false)
    var starting by mutableStateOf(false)
    var health by mutableStateOf<HealthResponse?>(null)
    var lastError by mutableStateOf<String?>(null)
    var serverFailed by mutableStateOf(false)      // gagal menyala setelah semua percobaan
    var serverStage by mutableStateOf("")          // teks tahap: "Menjalankan Termux…" dst.
    var serverLog by mutableStateOf<List<String>>(emptyList())

    var url by mutableStateOf("")
    var info by mutableStateOf<MediaInfo?>(null)
    var infoLoading by mutableStateOf(false)
    var infoError by mutableStateOf<String?>(null)
    var quality by mutableStateOf(prefs.defaultQuality)
    var audioFormat by mutableStateOf(prefs.audioFormat)
    var subtitles by mutableStateOf(prefs.subtitles)
    var embedThumb by mutableStateOf(prefs.embedThumbnail)
    var playlist by mutableStateOf(prefs.playlist)

    var jobs by mutableStateOf<List<Job>>(emptyList())
    var library by mutableStateOf(history.read())
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var pendingAuto by mutableStateOf(false)

    private var startDeadline = 0L
    private var startedAt = 0L
    private var retried = 0
    private var lastClip: String? = null
    private var baseline = false
    private var configPushed = false
    private val seen = mutableSetOf<String>()

    fun toast(text: String) { message = text }

    fun update(block: (Prefs) -> Prefs) {
        val old = prefs
        prefs = block(old).also { settings.save(it) }
        if (old.serverUrl != prefs.serverUrl) {
            api = Api(prefs.serverUrl)
            online = false
        }
        if (old.maxConcurrent != prefs.maxConcurrent) configPushed = false
    }

    // ---------- server ----------
    fun startServer(manual: Boolean = true) {
        if (!termux.isInstalled()) {
            if (manual) toast("Termux belum terpasang")
            return
        }
        serverFailed = false
        termux.startServer(prefs.serverDir)
            .onSuccess {
                starting = true
                startedAt = System.currentTimeMillis()
                startDeadline = startedAt + 75_000   // Termux dingin + muat yt-dlp bisa lama
                retried = 0
                serverStage = "Menjalankan Termux…"
                if (manual) toast("Menyalakan server…")
            }
            .onFailure {
                serverFailed = true
                toast(it.message ?: "Gagal menjalankan Termux")
            }
    }

    /** Pasang server + semua dependensi otomatis dari dalam APK (sesi Termux terbuka agar terlihat). */
    fun installServer() {
        termux.installServer()
            .onSuccess {
                toast("Memasang server di Termux… tunggu sampai selesai, lalu kembali ke ALF.")
                starting = true
                startedAt = System.currentTimeMillis()
                startDeadline = startedAt + 600_000
                serverStage = "Memasang server (sekali saja)…"
                serverFailed = false
            }
            .onFailure { toast(it.message ?: "Gagal memasang server") }
    }

    fun loadServerLog() {
        scope.launch {
            serverLog = runCatching { api.log(120) }.getOrElse { listOf("Server offline — log tidak bisa dibaca.") }
        }
    }

    fun updateYtdlp() {
        scope.launch {
            runCatching { api.updateYtdlp() }
                .onSuccess { toast("Memperbarui yt-dlp di latar belakang…") }
                .onFailure { toast("Gagal menghubungi server") }
        }
    }

    fun scopeCheck() {
        scope.launch { poll(); toast(if (online) "Server online" else (lastError ?: "Server offline").take(120)) }
    }

    fun stopServer() {
        scope.launch {
            runCatching { api.shutdown() }
            delay(600)
            poll()
            toast("Server dimatikan")
        }
    }

    suspend fun boot() {
        if (prefs.autoStart) {
            delay(400)
            val up = runCatching { api.health().ok }.getOrDefault(false)
            if (!up) startServer(false)
        }
        while (true) {
            poll()
            delay(if (jobs.any { it.isActive }) 1000 else 2500)
        }
    }

    suspend fun poll() {
        val h = runCatching { api.health() }
        online = h.getOrNull()?.ok == true
        if (online) {
            health = h.getOrNull()
            lastError = null
            serverFailed = false
            if (h.getOrNull()?.ready == false) {
                starting = true; serverStage = "Memuat yt-dlp…"
                startDeadline = maxOf(startDeadline, System.currentTimeMillis() + 60_000)
            } else { starting = false; serverStage = "" }
            if (!configPushed) {
                runCatching { api.setConcurrent(prefs.maxConcurrent) }.onSuccess { configPushed = true }
            }
            runCatching { api.jobs() }.getOrNull()?.let { jobs = it; onJobs(it) }
        } else {
            lastError = h.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
            configPushed = false
            val now = System.currentTimeMillis()
            if (starting) {
                val elapsed = now - startedAt
                serverStage = when {
                    elapsed < 6_000 -> "Menjalankan Termux…"
                    elapsed < 20_000 -> "Menunggu server merespons…"
                    else -> "Masih menunggu… (pertama kali bisa lama)"
                }
                // Coba ulang sekali bila Termux dingin tidak menangkap perintah pertama
                if (retried == 0 && elapsed in 14_000..40_000 && termux.isInstalled()) {
                    retried = 1
                    termux.startServer(prefs.serverDir)
                }
                if (now > startDeadline) {
                    starting = false
                    serverFailed = true
                    serverStage = ""
                    toast("Server belum merespons. Lihat kartu bantuan di layar Unduh.")
                }
            }
        }
    }

    private fun onJobs(list: List<Job>) {
        val finished = list.filter { it.isFinal }
        if (!baseline) {
            baseline = true
            finished.forEach { seen += it.id; if (it.status == "completed") history.add(it) }
            library = history.read()
            return
        }
        finished.filter { seen.add(it.id) }.forEach { job ->
            if (job.status != "cancelled") history.add(job)
            library = history.read()
            if (job.status == "completed") toast("Selesai: ${job.title ?: "unduhan"}")
            if (prefs.notifications && job.status != "cancelled") Notifier.show(context, job)
        }
    }

    // ---------- link ----------
    private fun readClipboardUrl(): String? = runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip?.takeIf { it.itemCount > 0 }
        clip?.getItemAt(0)?.coerceToText(context)?.toString()?.let { extractUrl(it) }
    }.getOrNull()

    fun onResume() {
        if (!prefs.autoPaste || url.isNotBlank()) return
        val t = readClipboardUrl() ?: return
        if (t == lastClip) return
        lastClip = t
        url = t
        tab = Tab.DOWNLOAD
        if (prefs.autoDownloadClipboard) pendingAuto = true
    }

    fun pasteNow() {
        val t = readClipboardUrl()
        if (t == null) toast("Clipboard tidak berisi link") else { url = t; lastClip = t }
    }

    fun handleShared(text: String?) {
        val u = text?.let { extractUrl(it) }
        if (u == null) { toast("Tidak ada link di teks yang dibagikan"); return }
        lastClip = u
        url = u
        tab = Tab.DOWNLOAD
        if (prefs.autoDownloadShared) pendingAuto = true
    }

    suspend fun fetchInfo() {
        infoLoading = true
        infoError = null
        try {
            info = api.info(url.trim(), playlist)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            info = null
            infoError = (e.message ?: "Gagal membaca link").take(160)
        } finally {
            infoLoading = false
        }
    }

    // ---------- jobs ----------
    private fun request(u: String, q: String = quality, a: String = audioFormat) = CreateJobRequest(
        url = u, quality = q, audioFormat = a, subtitles = subtitles, embedThumbnail = embedThumb,
        embedMetadata = prefs.embedMetadata, playlist = playlist, speedLimitKb = prefs.speedLimitKb
    )

    fun download() {
        val u = url.trim()
        if (!looksLikeUrl(u)) { toast("Tempel link yang valid dulu"); return }
        if (!online) {
            pendingAuto = true
            if (!starting) startServer(false)
            toast("Menyalakan server, unduhan akan dimulai otomatis")
            return
        }
        submit(request(u)) {
            if (prefs.clearAfterAdd) { url = ""; info = null; infoError = null }
        }
    }

    fun redownload(job: Job) {
        if (!online) { toast("Server belum aktif"); return }
        submit(request(job.url, job.quality, job.audioFormat)) { tab = Tab.DOWNLOAD }
    }

    private fun submit(req: CreateJobRequest, onOk: () -> Unit) {
        scope.launch {
            busy = true
            try {
                api.createJob(req)
                toast("Ditambahkan ke antrean")
                onOk()
                poll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast((e.message ?: "Gagal membuat tugas").take(160))
            } finally {
                busy = false
            }
        }
    }

    fun cancel(id: String) { scope.launch { runCatching { api.cancel(id) }; poll() } }
    fun retry(id: String) { scope.launch { runCatching { api.retry(id) }; poll() } }

    fun removeFromLibrary(id: String) {
        history.remove(id)
        library = history.read()
        scope.launch { runCatching { api.delete(id) } }
    }

    fun clearLibrary() {
        history.clear()
        library = emptyList()
        scope.launch { runCatching { api.clearFinished() } }
    }

    // ---------- helpers ----------
    fun copy(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ALF", text))
        toast("Disalin")
    }

    fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        runCatching {
            context.startActivity(Intent.createChooser(send, "Bagikan").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openFolder() {
        val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2FALF%20Downloader")
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "vnd.android.document/directory")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(view) }.onFailure {
            runCatching {
                context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { toast("Buka folder: Download/ALF Downloader") }
        }
    }
}
