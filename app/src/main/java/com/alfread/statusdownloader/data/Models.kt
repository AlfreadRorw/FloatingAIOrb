package com.alfread.statusdownloader.data

import java.io.File

enum class MediaKind { IMAGE, VIDEO }

enum class DeletedMessageType {
    CHAT, VOICE, PHOTO, VIDEO, DOCUMENT, UNKNOWN
}

data class DeletedMessage(
    val id: String,
    val packageName: String,
    val sender: String,
    val text: String,
    val type: DeletedMessageType,
    val time: Long,
    val deletedMarker: Boolean = false,
    val mediaPath: String? = null
)

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
    val newestFirst: Boolean = true
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
