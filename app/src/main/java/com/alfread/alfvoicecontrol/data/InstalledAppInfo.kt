package com.alfread.alfvoicecontrol.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

/**
 * Looks up every app that is actually installed and launchable on this device.
 * This is deliberately dynamic - no package name is ever hardcoded - so
 * "Open App" commands work for whatever the user has installed, not just a
 * fixed list like TikTok/WhatsApp.
 */
object InstalledAppsRepository {

    fun listLaunchableApps(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)

        return resolveInfos
            .asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .mapNotNull { pkg ->
                runCatching {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    InstalledAppInfo(
                        packageName = pkg,
                        label = pm.getApplicationLabel(appInfo).toString(),
                        icon = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Returns the launch intent for a package, or null if it can no longer be launched. */
    fun getLaunchIntent(context: Context, packageName: String): Intent? {
        return context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
