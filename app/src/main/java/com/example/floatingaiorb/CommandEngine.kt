package com.example.floatingaiorb

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import java.util.Locale

object CommandEngine {
    data class Command(val type: Type, val appName: String? = null, val packageName: String? = null, val comment: String? = null)
    enum class Type { OPEN_APP, TIKTOK_COMMENT, UNKNOWN }

    private val knownApps = mapOf(
        "tiktok" to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        "whatsapp" to listOf("com.whatsapp"),
        "instagram" to listOf("com.instagram.android"),
        "youtube" to listOf("com.google.android.youtube"),
        "kamera" to listOf("com.android.camera", "com.google.android.GoogleCamera"),
        "chrome" to listOf("com.android.chrome"),
        "spotify" to listOf("com.spotify.music"),
        "telegram" to listOf("org.telegram.messenger")
    )

    fun parse(text: String): Command {
        var t = text.lowercase(Locale.getDefault()).trim().replace("tik tok", "tiktok")
        t = t.replace(Regex("\\s+"), " ")
        val aliases = mapOf("yt" to "youtube", "wa" to "whatsapp", "ig" to "instagram")
        aliases.forEach { (from, to) -> t = t.replace(Regex("\\b${Regex.escape(from)}\\b"), to) }
        val comment = Regex("(?:buka|bukain|bukakan|jalanin)?\\s*tiktok.*?(?:komentar|comment).*?(?:tulis|ketik)\\s+(.+)", RegexOption.IGNORE_CASE).find(text)
        if (comment != null) return Command(Type.TIKTOK_COMMENT, "TikTok", knownApps["tiktok"]?.firstOrNull(), comment.groupValues[1].trim())
        val verbs = listOf("buka", "bukain", "bukakan", "tolong buka", "tolong bukain", "jalanin", "jalankan", "nyalain", "masuk")
        val open = knownApps.entries.firstOrNull { (name, _) ->
            t == name || verbs.any { verb -> t.startsWith("$verb $name") } || t.contains("buka aplikasi $name")
        }
        if (open != null) return Command(Type.OPEN_APP, open.key.replaceFirstChar { it.titlecase(Locale.getDefault()) }, open.value.firstOrNull())
        return Command(Type.UNKNOWN)
    }

    fun execute(context: Context, command: Command): Boolean {
        return when (command.type) {
            Type.OPEN_APP -> {
                val pkg = resolveInstalledPackage(context, knownApps[command.appName?.lowercase(Locale.getDefault())].orEmpty()) ?: command.packageName
                val intent = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
                if (intent == null) return false
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }.isSuccess
            }
            Type.TIKTOK_COMMENT -> {
                val pkg = resolveInstalledPackage(context, knownApps["tiktok"].orEmpty()) ?: return false
                val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                context.getSharedPreferences("orb", Context.MODE_PRIVATE).edit()
                    .putString("pending_tiktok_comment", command.comment.orEmpty()).putString("tiktok_package", pkg).apply()
                TikTokAssistService.requestAssist(context)
                true
            }
            else -> false
        }
    }

    fun resolveInstalledPackage(context: Context, candidates: List<String>): String? {
        val pm = context.packageManager
        for (pkg in candidates) {
            if (runCatching { pm.getApplicationInfo(pkg, 0) }.isSuccess && pm.getLaunchIntentForPackage(pkg) != null) return pkg
        }
        val apps = runCatching { pm.getInstalledApplications(PackageManager.GET_META_DATA) }.getOrElse { emptyList() }
        for (app in apps) {
            val label = runCatching { pm.getApplicationLabel(app).toString() }.getOrNull().orEmpty()
            if (label.equals("TikTok", true) && pm.getLaunchIntentForPackage(app.packageName) != null) return app.packageName
        }
        return null
    }

    fun isTikTokPackage(context: Context, packageName: String): Boolean {
        if (knownApps["tiktok"].orEmpty().contains(packageName)) return true
        val label = runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString() }.getOrNull()
        return label?.equals("TikTok", true) == true
    }

    fun accessibilityEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == context.packageName && it.resolveInfo.serviceInfo.name.endsWith("TikTokAssistService") }
    }
}
