package com.alfread.alfvision.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.alfread.alfvision.AlfVisionApplication
import com.alfread.alfvision.R
import com.alfread.alfvision.core.model.CaptureRequest
import com.alfread.alfvision.core.model.CaptureResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureService : Service() {
    private lateinit var container: com.alfread.alfvision.AppContainer
    private lateinit var projectionManager: MediaProjectionManager
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var pendingRequest: CaptureRequest? = null
    private var latestImageAvailable = AtomicBoolean(false)
    private var serviceJob: Job? = null
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        container = (application as AlfVisionApplication).container
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        createNotificationChannel()
        serviceJob = mainScope.launch {
            container.captureCoordinator.requests.collect { request ->
                if (pendingRequest != null) {
                    container.captureCoordinator.fail(request.id, IllegalStateException("Capture already in progress"))
                } else {
                    pendingRequest = request
                    processAvailableImage()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }
        if (projection != null) return START_STICKY

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.parcelableIntent(EXTRA_DATA) ?: run {
            container.sessionStore.setError("MediaProjection permission data tidak tersedia.")
            stopSelf()
            return START_NOT_STICKY
        }
        if (resultCode != Activity.RESULT_OK) {
            container.sessionStore.setError("Screen capture permission belum diberikan.")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification())
            }
            setupProjection(resultCode, data)
            isRunning = true
            container.sessionStore.setError(null)
        } catch (t: Throwable) {
            container.sessionStore.setError("Screen capture gagal dimulai. Periksa permission dan coba lagi.")
            stopSelf()
        }
        return START_STICKY
    }

    private fun setupProjection(resultCode: Int, data: Intent) {
        projection = projectionManager.getMediaProjection(resultCode, data) ?: throw IllegalStateException("MediaProjection unavailable")
        projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                mainScope.launch { stopCapture() }
            }
        }
        projection?.registerCallback(projectionCallback!!, android.os.Handler(Looper.getMainLooper()))
        createVirtualDisplay()
    }

    private fun createVirtualDisplay() {
        releaseImageReader()
        virtualDisplay?.release()
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3).apply {
            setOnImageAvailableListener({ processAvailableImage() }, android.os.Handler(Looper.getMainLooper()))
        }
        virtualDisplay = projection?.createVirtualDisplay(
            "ALF Vision Panel",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
        latestImageAvailable.set(false)
    }

    private fun processAvailableImage() {
        val request = pendingRequest ?: return
        val reader = imageReader ?: return
        val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return
        image.use {
            try {
                val plane = it.planes.firstOrNull() ?: throw IllegalStateException("No image plane")
                val width = it.width
                val height = it.height
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val bitmapWidth = width + rowPadding / pixelStride
                val bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
                val buffer: ByteBuffer = plane.buffer
                buffer.rewind()
                bitmap.copyPixelsFromBuffer(buffer)
                val cropped = if (!request.fullScreen && request.region != null) container.imageProcessor.cropRegion(bitmap, request.region) else bitmap
                val payload = container.imageProcessor.encodeJpeg(cropped, request.quality, request.maxBytes)
                if (cropped !== bitmap) cropped.recycle()
                bitmap.recycle()
                pendingRequest = null
                container.captureCoordinator.complete(CaptureResult(request.id, payload, request.region))
                latestImageAvailable.set(true)
            } catch (t: Throwable) {
                pendingRequest = null
                container.captureCoordinator.fail(request.id, t)
                container.sessionStore.setError("Screenshot terlalu besar atau gagal diproses.")
            }
        }
    }

    private fun releaseImageReader() {
        runCatching { imageReader?.setOnImageAvailableListener(null, null) }
        runCatching { imageReader?.close() }
        imageReader = null
    }

    fun stopCapture() {
        pendingRequest?.let { container.captureCoordinator.fail(it.id, IllegalStateException("Capture stopped")) }
        pendingRequest = null
        releaseImageReader()
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { projection?.unregisterCallback(projectionCallback!!) }
        runCatching { projection?.stop() }
        projection = null
        projectionCallback = null
        isRunning = false
    }

    override fun onDestroy() {
        stopCapture()
        serviceJob?.cancel()
        mainScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP)
        val pending = PendingIntent.getService(
            this, 90, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_text))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(R.drawable.ic_notification, getString(R.string.notification_stop), pending)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "alf_vision_capture"
        private const val NOTIFICATION_ID = 1101
        const val ACTION_STOP = "com.alfread.alfvision.capture.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "projection_data"
        @Volatile var isRunning: Boolean = false
    }
}


private fun Intent.parcelableIntent(key: String): Intent? {
    return if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, Intent::class.java) else @Suppress("DEPRECATION") getParcelableExtra(key)
}
