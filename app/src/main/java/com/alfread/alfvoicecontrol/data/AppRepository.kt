package com.alfread.alfvoicecontrol.data

import android.content.Context
import android.content.Intent
import com.alfread.alfvoicecontrol.model.AppTarget

class AppRepository(private val context: Context) {
    fun getLaunchableApps(): List<AppTarget> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager
            .queryIntentActivities(intent, 0)
            .map { info ->
                AppTarget(
                    packageName = info.activityInfo.packageName,
                    label = info.loadLabel(context.packageManager).toString().ifBlank { info.activityInfo.packageName }
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
