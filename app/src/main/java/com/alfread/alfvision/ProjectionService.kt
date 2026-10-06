package com.alfread.alfvision

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics

class ProjectionService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val ACTION_CAPTURE = "com.alfread.alfvision.CAPTURE"
        const val EXTRA_REQUEST_ID = "request_id"
        const val ACTION_CAPTURE_RESULT = "com.alfread.alfvision.CAPTURE_RESULT"
        const val EXTRA_PATH = "path"
        const val CHANNEL = "alf_projection"
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ready = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(7, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        if (intent.action == ACTION_CAPTURE) {
            capture(intent.getStringExtra(EXTRA_REQUEST_ID) ?: System.currentTimeMillis().toString())
            return START_STICKY
        }
        val code = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_DATA, Intent::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DATA)
        if (data != null && code != 0) startProjection(code, data)
        return START_STICKY
    }

    private fun startProjection(code: Int, data: Intent) {
        if (ready) return
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(code, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, handler)

        val metrics = Resources.getSystem().displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection?.createVirtualDisplay(
            "ALF Vision Capture", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )
        ready = true
    }

    private fun capture(requestId: String) {
        if (!ready) return
        var attempts = 0
        fun tryRead() {
            val image = imageReader?.acquireLatestImage()
            if (image == null) {
                attempts++
                if (attempts < 6) handler.postDelayed({ tryRead() }, 80)
                return
            }
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * image.width
                val full = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
                full.copyPixelsFromBuffer(buffer)
                val region = AppPrefs.getRegion(this)
                val cropped = crop(full, region)
                val file = java.io.File(cacheDir, "capture_${requestId}.jpg")
                java.io.FileOutputStream(file).use { out -> cropped.compress(Bitmap.CompressFormat.JPEG, 80, out) }
                cropped.recycle()
                full.recycle()
                sendBroadcast(Intent(ACTION_CAPTURE_RESULT).setPackage(packageName).putExtra(EXTRA_PATH, file.absolutePath).putExtra(EXTRA_REQUEST_ID, requestId))
            } finally {
                image.close()
            }
        }
        tryRead()
    }

    private fun crop(full: Bitmap, region: IntArray): Bitmap {
        val dm = Resources.getSystem().displayMetrics
        val sx = full.width.toFloat() / dm.widthPixels.toFloat()
        val sy = full.height.toFloat() / dm.heightPixels.toFloat()
        val l = (region[0] * sx).toInt().coerceIn(0, full.width - 1)
        val t = (region[1] * sy).toInt().coerceIn(0, full.height - 1)
        val r = (region[2] * sx).toInt().coerceIn(l + 1, full.width)
        val b = (region[3] * sy).toInt().coerceIn(t + 1, full.height)
        return Bitmap.createBitmap(full, l, t, r - l, b - t)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "ALF Vision Capture", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(this, CHANNEL) else android.app.Notification.Builder(this)
        return builder.setContentTitle("ALF Vision Panel").setContentText("Screen capture service active").setSmallIcon(com.alfread.alfvision.R.drawable.ic_alf_vision).setOngoing(true).build()
    }

    override fun onDestroy() {
        virtualDisplay?.release()
        imageReader?.close()
        projection?.stop()
        ready = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
