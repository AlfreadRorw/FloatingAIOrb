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

    private val apps = mapOf(
        "tiktok" to "com.zhiliaoapp.musically",
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "youtube" to "com.google.android.youtube",
        "kamera" to "com.android.camera"
    )

    fun parse(text: String): Command {
        val t = text.lowercase(Locale.getDefault()).trim()
        val open = apps.entries.firstOrNull { t.contains("buka ${it.key}") || t == "${it.key}" }
        if (open != null) return Command(Type.OPEN_APP, open.key, open.value)
        val match = Regex("(?:buka )?tiktok.*?(?:komentar|comment).*?(?:tulis|ketik)\\s+(.+)", RegexOption.IGNORE_CASE).find(text)
        if (match != null) return Command(Type.TIKTOK_COMMENT, "TikTok", apps["tiktok"], match.groupValues[1].trim())
        return Command(Type.UNKNOWN)
    }

    fun execute(context: Context, command: Command): Boolean {
        return when (command.type) {
            Type.OPEN_APP -> {
                val intent = context.packageManager.getLaunchIntentForPackage(command.packageName ?: return false)
                    ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(command.packageName)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }.isSuccess
            }
            Type.TIKTOK_COMMENT -> {
                val intent = context.packageManager.getLaunchIntentForPackage("com.zhiliaoapp.musically") ?: return false
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                context.getSharedPreferences("orb", Context.MODE_PRIVATE).edit()
                    .putString("pending_tiktok_comment", command.comment.orEmpty()).apply()
                TikTokAssistService.requestAssist(context)
                true
            }
            else -> false
        }
    }

    fun accessibilityEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == context.packageName && it.resolveInfo.serviceInfo.name.endsWith("TikTokAssistService") }
    }
}
