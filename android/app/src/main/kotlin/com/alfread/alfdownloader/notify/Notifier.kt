package com.alfread.alfdownloader.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.alfread.alfdownloader.MainActivity
import com.alfread.alfdownloader.R
import com.alfread.alfdownloader.model.Job

object Notifier {
    private const val CHANNEL = "downloads"

    fun init(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Unduhan", NotificationManager.IMPORTANCE_DEFAULT))
    }

    @SuppressLint("MissingPermission")
    fun show(context: Context, job: Job) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val ok = job.status == "completed"
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_alf)
            .setContentTitle(if (ok) "Unduhan selesai" else "Unduhan gagal")
            .setContentText(job.title ?: job.url)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(job.id.hashCode(), n) }
    }
}
