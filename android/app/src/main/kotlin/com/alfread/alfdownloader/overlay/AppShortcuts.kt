package com.alfread.alfdownloader.overlay

import android.content.Context
import android.content.Intent

data class AppShortcut(
    val id: String,
    val label: String,
    val packages: List<String>,
    val webUrl: String
)

val MiniAppShortcuts = listOf(
    AppShortcut("tiktok", "TikTok", listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), "https://www.tiktok.com"),
    AppShortcut("whatsapp", "WhatsApp", listOf("com.whatsapp", "com.whatsapp.w4b"), "https://web.whatsapp.com"),
    AppShortcut("youtube", "YouTube", listOf("com.google.android.youtube"), "https://m.youtube.com"),
    AppShortcut("instagram", "Instagram", listOf("com.instagram.android"), "https://www.instagram.com")
)

fun AppShortcut.installedPackage(context: Context): String? =
    packages.firstOrNull { runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess }

fun launchNativeApp(context: Context, packageName: String): Boolean = runCatching {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    true
}.getOrDefault(false)
