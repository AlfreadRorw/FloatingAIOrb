package com.alfread.alfdownloader.model

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val ok: Boolean = false,
    val service: String = "",
    val version: String? = null,
    val ytdlp: String? = null,
    val freeBytes: Long = 0L,
    val downloadDir: String? = null,
    val maxConcurrent: Int = 2,
    val ready: Boolean = true,
    val error: String? = null,
    val uptime: Int = 0
)

@Serializable
data class LogResponse(val lines: List<String> = emptyList())

@Serializable
data class MediaInfo(
    val id: String = "",
    val title: String = "",
    val uploader: String = "",
    val duration: Double? = null,
    val thumbnail: String? = null,
    val webpageUrl: String = "",
    val extractor: String? = null,
    val viewCount: Long? = null,
    val uploadDate: String? = null,
    val heights: List<Int> = emptyList(),
    val isPlaylist: Boolean = false,
    val entryCount: Int = 0
)

@Serializable
data class CreateJobRequest(
    val url: String,
    val quality: String = "best",
    val audioFormat: String = "mp3",
    val subtitles: Boolean = false,
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val playlist: Boolean = false,
    val speedLimitKb: Int = 0
)

@Serializable
data class Job(
    val id: String = "",
    val url: String = "",
    val title: String? = null,
    val thumbnail: String? = null,
    val status: String = "queued",
    val progress: Double = 0.0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBps: Double = 0.0,
    val etaSeconds: Int? = null,
    val filename: String? = null,
    val error: String? = null,
    val createdAt: String = "",
    val finishedAt: String? = null,
    val quality: String = "best",
    val audioFormat: String = "mp3",
    val sizeBytes: Long = 0L,
    val playlistIndex: Int = 0,
    val playlistCount: Int = 0
)

val Job.isActive: Boolean
    get() = status in setOf("queued", "starting", "downloading", "processing", "cancelling")

val Job.isFinal: Boolean
    get() = status in setOf("completed", "error", "cancelled")

@Serializable
data class CreateJobResponse(val id: String = "")

@Serializable
data class ConfigRequest(val maxConcurrent: Int)

@Serializable
data class Prefs(
    val serverUrl: String = "http://127.0.0.1:8080",
    val serverDir: String = "~/alf-downloader-server",
    // otomatisasi
    val autoStart: Boolean = true,
    val autoPaste: Boolean = true,
    val autoInfo: Boolean = true,
    val autoDownloadShared: Boolean = true,
    val autoDownloadClipboard: Boolean = false,
    val clearAfterAdd: Boolean = true,
    val notifications: Boolean = true,
    val haptics: Boolean = true,
    // dock
    val dockStyle: Int = 0,      // 0 floating, 1 bar
    val dockLabels: Int = 0,     // 0 terpilih, 1 selalu, 2 sembunyi
    val dockSize: Int = 1,       // 0 kecil, 1 sedang, 2 besar
    val dockOpacity: Float = 0.92f,
    val dockBadge: Boolean = true,
    // jendela mengambang
    val floatingEnabled: Boolean = false,
    val bubbleX: Int = -1,
    val bubbleY: Int = -1,
    val bubbleSide: Int = 1, // 0 kiri, 1 kanan
    val bubbleLengthDp: Int = 112,
    val bubbleThicknessDp: Int = 7,
    val nativeWindowWidthDp: Int = 360,
    val nativeWindowHeightDp: Int = 560,
    val useShizuku: Boolean = true,
    val nativeAnchor: Int = 0,            // 0 tengah, 1 kiri atas, 2 kanan atas, 3 kiri bawah, 4 kanan bawah
    val collapseOnLaunch: Boolean = true, // panel otomatis menciut setelah membuka aplikasi
    val autoPasteOnExpand: Boolean = true,// otomatis tempel link dari clipboard saat panel dibuka
    // tema & tampilan panel mengambang
    val panelTheme: Int = 2,             // indeks PanelThemes
    val panelUseThemeAccent: Boolean = true,
    val panelOpacity: Float = 0.96f,
    val panelCornerDp: Int = 24,
    val panelWidthDp: Int = 288,
    val panelBlur: Boolean = true,
    val barDimWhenIdle: Boolean = true,
    val barGlow: Boolean = true,
    // server / Termux
    val keepServerAlive: Boolean = true, // watchdog: nyalakan ulang server bila mati
    val startOnBoot: Boolean = true,     // jalankan bar + server setelah HP menyala
    val pinnedPackages: List<String> = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
    // bingkai & kunci jendela aplikasi (freeform)
    val windowFrame: Boolean = true,     // bingkai + bar judul bertema di sekeliling jendela aplikasi
    val windowLock: Boolean = true,      // jendela tetap di atas; tidak "hilang" saat mengetuk di luar
    val captionHeightDp: Int = 42,       // tinggi bar judul bawaan ROM yang ditutup bar judul tema
    val frameGlow: Boolean = true,
    // tampilan
    val accent: Int = 0,
    val fontIndex: Int = 1,              // indeks AlfFonts (1 = Poppins)
    // default unduhan
    val defaultQuality: String = "best",
    val audioFormat: String = "mp3",
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val subtitles: Boolean = false,
    val playlist: Boolean = false,
    val maxConcurrent: Int = 2,
    val speedLimitKb: Int = 0
)
