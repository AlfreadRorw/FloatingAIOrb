package com.alfread.alfdownloader.overlay

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

/** A real launchable Android application (not a web page). */
data class NativeApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable?
)

fun loadLaunchableApps(context: Context): List<NativeApp> = runCatching {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
    pm.queryIntentActivities(intent, 0)
        .asSequence()
        .mapNotNull { info ->
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName) return@mapNotNull null
            NativeApp(
                packageName = pkg,
                activityName = info.activityInfo.name,
                label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifBlank { pkg },
                icon = runCatching { info.loadIcon(pm) }.getOrNull()
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
        .toList()
}.getOrElse { emptyList() }
