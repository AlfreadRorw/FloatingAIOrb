package com.example.floatingaiorb

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Locale

object CommandEngine {
    data class Command(
        val type: Type,
        val appName: String? = null,
        val packageName: String? = null,
        val comment: String? = null,
        val query: String? = null,
        val target: String? = null,
        val reply: String? = null
    )
    enum class Type { OPEN_APP, TIKTOK_COMMENT, TIKTOK_SEARCH, TIKTOK_REPLY, UNKNOWN }

    private val aliases = mapOf("yt" to "youtube", "wa" to "whatsapp", "ig" to "instagram", "tt" to "tiktok")
    private val knownApps = mapOf(
        "tiktok" to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        "whatsapp" to listOf("com.whatsapp"), "instagram" to listOf("com.instagram.android"),
        "youtube" to listOf("com.google.android.youtube"), "chrome" to listOf("com.android.chrome"),
        "spotify" to listOf("com.spotify.music"), "telegram" to listOf("org.telegram.messenger")
    )

    fun parse(text: String): Command = parse(null, text)

    fun parse(context: Context?, text: String): Command {
        var t = text.lowercase(Locale.getDefault()).trim().replace("tik tok", "tiktok")
        t = t.replace(Regex("\\s+"), " ")
        aliases.forEach { (from, to) -> t = t.replace(Regex("\\b${Regex.escape(from)}\\b"), to) }

        val search = Regex("(?:cari|search|carikan|coba cari)(?: di)? tiktok(?: tentang| untuk| dengan| kata)?\\s+(.+)", RegexOption.IGNORE_CASE).find(text)
        if (search != null) return Command(Type.TIKTOK_SEARCH, "TikTok", resolveKnownPackage("tiktok"), query = search.groupValues[1].trim())

        val reply = Regex("(?:balas|jawab)\\s+(?:chat|pesan)\\s+(?:di\\s+)?tiktok\\s+(?:dari|oleh)\\s+(.+?)\\s+(?:balas|jawab)\\s+(.+)$", RegexOption.IGNORE_CASE).find(text)
        if (reply != null) {
            val target = reply.groupValues[1].trim().ifBlank { null }
            var body = reply.groupValues.getOrNull(2)?.trim().orEmpty()
            if (body.equals("apa saja", true) || body.equals("terserah", true) || body.isBlank()) body = "Hehe iyaaa, kenapa nih? 😄"
            return Command(Type.TIKTOK_REPLY, "TikTok", resolveKnownPackage("tiktok"), target = target, reply = body)
        }

        val comment = Regex("(?:buka|bukain|bukakan)?\\s*tiktok.*?(?:komentar|comment).*?(?:tulis|ketik)\\s+(.+)", RegexOption.IGNORE_CASE).find(text)
        if (comment != null) return Command(Type.TIKTOK_COMMENT, "TikTok", resolveKnownPackage("tiktok"), comment = comment.groupValues[1].trim())

        val openVerb = Regex("^(?:buka|bukain|bukakan|tolong buka|tolong bukain|jalanin|jalankan|nyalain|masuk(?: ke)?)\\s+(?:aplikasi\\s+)?(.+)$", RegexOption.IGNORE_CASE).find(t)
        val candidateName = when {
            openVerb != null -> openVerb.groupValues[1].trim()
            knownApps.containsKey(t) -> t
            else -> ""
        }
        if (candidateName.isNotBlank()) {
            val normalized = candidateName.lowercase(Locale.getDefault())
            val aliasesName = aliases[normalized] ?: normalized
            val pkg = context?.let { resolveInstalledPackage(it, aliasesName, null) }
            if (pkg != null) return Command(Type.OPEN_APP, prettyName(aliasesName), pkg)
            // Fallback: discover launcher-visible apps, avoiding QUERY_ALL_PACKAGES.
            val resolved = findLaunchableAppByLabel(context, aliasesName)
            if (resolved != null) return Command(Type.OPEN_APP, resolved.first, resolved.second)
        }
        return Command(Type.UNKNOWN)
    }

    fun execute(context: Context, command: Command): Boolean = when (command.type) {
        Type.OPEN_APP -> openPackage(context, command.packageName)
        Type.TIKTOK_COMMENT -> assistTikTok(context, command)
        Type.TIKTOK_SEARCH -> assistTikTok(context, command)
        Type.TIKTOK_REPLY -> assistTikTok(context, command)
        else -> false
    }

    private fun openPackage(context: Context, pkg: String?): Boolean {
        val intent = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) } ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun assistTikTok(context: Context, command: Command): Boolean {
        val pkg = resolveInstalledPackage(context, "tiktok", command.packageName) ?: return false
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(launch) }.getOrElse { return false }
        val prefs = context.getSharedPreferences("orb", Context.MODE_PRIVATE).edit().putString("tiktok_package", pkg)
        when (command.type) {
            Type.TIKTOK_COMMENT -> prefs.putString("tiktok_action", "comment").putString("pending_tiktok_comment", command.comment.orEmpty())
            Type.TIKTOK_SEARCH -> prefs.putString("tiktok_action", "search").putString("pending_tiktok_query", command.query.orEmpty())
            Type.TIKTOK_REPLY -> prefs.putString("tiktok_action", "reply").putString("pending_tiktok_target", command.target.orEmpty()).putString("pending_tiktok_reply", command.reply.orEmpty())
            else -> Unit
        }
        prefs.apply()
        TikTokAssistService.requestAssist(context)
        return true
    }

    fun resolveInstalledPackage(context: Context, candidates: List<String>): String? {
        val pm = context.packageManager
        candidates.forEach { pkg -> if (runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull() != null) return pkg }
        val wanted = when { candidates.any { knownApps["tiktok"].orEmpty().contains(it) } -> "tiktok" else -> "" }
        return if (wanted.isNotBlank()) findLaunchableAppByLabel(context, wanted)?.second else null
    }

    private fun resolveKnownPackage(name: String): String? = knownApps[name]?.firstOrNull()
    private fun resolveInstalledPackage(context: Context?, name: String, fallback: String?): String? {
        if (context == null) return fallback ?: knownApps[name]?.firstOrNull()
        return resolveInstalledPackage(context, knownApps[name].orEmpty()) ?: fallback
    }

    private fun findLaunchableAppByLabel(context: Context?, wanted: String): Pair<String, String>? {
        if (context == null) return null
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val q = wanted.lowercase(Locale.getDefault()).trim()
        val hit = apps.firstOrNull { info ->
            val label = info.loadLabel(pm)?.toString().orEmpty().lowercase(Locale.getDefault())
            label == q || label.contains(q) || q.contains(label) && label.length > 2
        } ?: return null
        return hit.loadLabel(pm).toString() to hit.activityInfo.packageName
    }

    private fun prettyName(s: String) = s.replaceFirstChar { it.titlecase(Locale.getDefault()) }

    fun accessibilityEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager ?: return false
        return manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == context.packageName && it.resolveInfo.serviceInfo.name.endsWith("TikTokAssistService") }
    }

    fun isTikTokPackage(context: Context, packageName: String): Boolean {
        if (knownApps["tiktok"].orEmpty().contains(packageName)) return true
        val label = runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString() }.getOrNull()
        return label?.equals("TikTok", true) == true
    }
}
