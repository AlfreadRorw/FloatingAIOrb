package com.alfread.alfvision.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.alfread.alfvision.MainActivity
import com.alfread.alfvision.R
import com.alfread.alfvision.core.CaptureCommand
import com.alfread.alfvision.core.AppContainer
import com.alfread.alfvision.core.CaptureEventBus
import com.alfread.alfvision.core.CapturedFrame
import com.alfread.alfvision.core.Constants
import com.alfread.alfvision.core.RegionRect
import com.alfread.alfvision.core.VisionEventBus
import com.alfread.alfvision.domain.ImageProcessor
import com.alfread.alfvision.util.AlfLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest

class ScreenCaptureService : Service() {
    companion object {
        const val ACTION_START = "com.alfread.alfvision.capture.START"
        const val ACTION_STOP = "com.alfread.alfvision.capture.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_WIDTH = "capture_width"
        const val EXTRA_HEIGHT = "capture_height"
        const val EXTRA_DENSITY = "capture_density"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var projectionManager: MediaProjectionManager
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var pendingRegion: RegionRect? = null
    private var capturePending = false
    private var requestedWidth = 0
    private var requestedHeight = 0
    private var requestedDensity = 0
    private var shuttingDown = false

    override fun onCreate() {
        super.onCreate()
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        createNotificationChannel()
        startForegroundCompat()
        scope.launch {
            CaptureEventBus.commands.collectLatest { command ->
                when (command) {
                    is CaptureCommand.CAPTURE -> requestCapture(command.region)
                    CaptureCommand.STOP -> stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_START -> startProjection(intent)
        }
        return START_NOT_STICKY
    }

    private fun startProjection(intent: Intent) {
        if (projection != null) return
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (data == null) { stopSelf(); return }
        requestedWidth = intent.getIntExtra(EXTRA_WIDTH, resources.displayMetrics.widthPixels)
        requestedHeight = intent.getIntExtra(EXTRA_HEIGHT, resources.displayMetrics.heightPixels)
        requestedDensity = intent.getIntExtra(EXTRA_DENSITY, resources.displayMetrics.densityDpi)
        runCatching {
            projection = projectionManager.getMediaProjection(resultCode, data).also { mediaProjection ->
                mediaProjection.registerCallback(projectionCallback, handler)
            }
            createVirtualDisplay()
        }.onFailure {
            AlfLogger.e("MediaProjection initialization failed", it)
            stopSelf()
        }
    }

    private fun createVirtualDisplay() {
        releaseDisplayOnly()
        val width = requestedWidth.coerceAtLeast(1)
        val height = requestedHeight.coerceAtLeast(1)
        imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            if (!capturePending || shuttingDown) {
                image.close()
                return@setOnImageAvailableListener
            }
            capturePending = false
            try {
                val bitmap = imageToBitmap(image)
                if (bitmap != null) {
                    val region = pendingRegion
                    pendingRegion = null
                    val useRegion = AppContainer.preferences.snapshot().sendOnlySelectedRegion
                    val output = if (useRegion) AppContainer.visionAnalyzer.prepareRegion(bitmap, region) else bitmap
                    if (output !== bitmap) bitmap.recycle()
                    VisionEventBus.publish(CapturedFrame(output, output.width, output.height, region))
                }
            } finally {
                image.close()
            }
        }, handler)

        virtualDisplay = projection?.createVirtualDisplay(
            "ALF Vision Panel",
            width,
            height,
            requestedDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )
    }

    private fun requestCapture(region: RegionRect?) {
        if (projection == null || imageReader == null) return
        pendingRegion = region
        capturePending = true
        handler.postDelayed({
            if (capturePending) capturePending = false
        }, 1500L)
    }

    private fun imageToBitmap(image: android.media.Image): Bitmap? {
        return runCatching {
            val plane = image.planes.firstOrNull() ?: return null
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val tempWidth = image.width + rowPadding / pixelStride
            val bitmap = Bitmap.createBitmap(tempWidth, image.height, Bitmap.Config.ARGB_8888)
            buffer.rewind()
            bitmap.copyPixelsFromBuffer(buffer)
            if (tempWidth != image.width) Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height).also { bitmap.recycle() } else bitmap
        }.getOrNull()
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            AlfLogger.i("MediaProjection stopped")
            stopSelf()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (projection != null) {
            requestedWidth = resources.displayMetrics.widthPixels
            requestedHeight = resources.displayMetrics.heightPixels
            requestedDensity = resources.displayMetrics.densityDpi
            handler.post { createVirtualDisplay() }
        }
    }

    override fun onDestroy() {
        shuttingDown = true
        releaseDisplayOnly()
        projection?.unregisterCallback(projectionCallback)
        projection?.stop()
        projection = null
        scope.cancel()
        super.onDestroy()
    }

    private fun releaseDisplayOnly() {
        capturePending = false
        pendingRegion = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
    }

    private fun startForegroundCompat() {
        val notification = NotificationCompat.Builder(this, Constants.CAPTURE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_capture_active))
            .setContentText("Screen analysis is active")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                R.drawable.ic_notification,
                "STOP",
                PendingIntent.getService(
                    this,
                    91,
                    Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    92,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                Constants.CAPTURE_NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(Constants.CAPTURE_NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    Constants.CAPTURE_CHANNEL_ID,
                    getString(R.string.notification_channel_capture),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
