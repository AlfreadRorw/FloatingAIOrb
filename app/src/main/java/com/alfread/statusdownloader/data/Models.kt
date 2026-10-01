package com.alfread.statusdownloader.data

import java.io.File

enum class MediaKind { IMAGE, VIDEO }

enum class WaSource(val label: String) {
    WHATSAPP("WhatsApp"),
    BUSINESS("WA Business")
}

data class StatusItem(
    val file: File,
    val kind: MediaKind,
    val modified: Long,
    val size: Long,
    val source: WaSource
) {
    val key: String get() = file.absolutePath
}

data class HistoryRecord(
    val id: String,
    val originalName: String,
    val savedPath: String,
    val kind: MediaKind,
    val size: Long,
    val time: Long,
    val origin: String
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val includeBusiness: Boolean = true,
    val moveMode: Boolean = false,
    val gridColumns: Int = 3,
    val prefix: String = "Alfread_",
    val newestFirst: Boolean = true,
    val chatLogEnabled: Boolean = true,
    val autoBackupMedia: Boolean = true,
    val logGroups: Boolean = true,
    val retentionDays: Int = 30
)

enum class StatusFilter { ALL, IMAGE, VIDEO }

data class StatusUiState(
    val hasPermission: Boolean = false,
    val loading: Boolean = false,
    val items: List<StatusItem> = emptyList(),
    val filter: StatusFilter = StatusFilter.ALL,
    val selected: Set<String> = emptySet(),
    val busy: Set<String> = emptySet()
)

enum class MsgType {
    TEXT, IMAGE, VIDEO, VOICE, AUDIO, DOCUMENT, STICKER, OTHER;

    val isMedia: Boolean get() = this == IMAGE || this == VIDEO || this == VOICE || this == AUDIO || this == DOCUMENT
}

data class ChatMessage(
    val id: String,
    val chat: String,
    val sender: String,
    val text: String,
    val type: MsgType,
    val time: Long,        // waktu notifikasi diterima
    val origTime: Long,    // waktu asli pesan (dari WhatsApp), dipakai mencocokkan penghapusan
    val app: String,       // package WhatsApp / WA Business
    val deleted: Boolean,
    val deletedAt: Long,
    val mediaPath: String?
)
