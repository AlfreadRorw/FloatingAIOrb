package com.alfread.alfdownloader.ui

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatBytes(b: Long): String {
    if (b <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = b.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (i == 0) "${v.toInt()} ${units[i]}" else String.format(Locale.US, "%.1f %s", v, units[i])
}

fun formatSpeed(bps: Double): String = if (bps <= 0) "" else formatBytes(bps.toLong()) + "/s"

fun formatDuration(seconds: Double): String = formatClock(seconds.toInt())

fun formatClock(total: Int): String {
    val s = total.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
}

fun formatCount(n: Long): String = when {
    n >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", n / 1e9)
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1e6)
    n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1e3)
    else -> n.toString()
}

fun shortDate(iso: String?): String = runCatching {
    OffsetDateTime.parse(iso)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault()))
}.getOrDefault("")

fun qualityLabel(key: String, audio: String = "mp3"): String = when (key) {
    "best" -> "Terbaik"
    "2160" -> "4K"
    "1080" -> "1080p"
    "720" -> "720p"
    "480" -> "480p"
    "360" -> "360p"
    "audio" -> audio.uppercase()
    else -> key
}
