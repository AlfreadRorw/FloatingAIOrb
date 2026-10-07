package com.alfread.alfvision.service

import android.app.*
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.alfread.alfvision.AlfVisionApplication
import com.alfread.alfvision.R
import com.alfread.alfvision.core.model.CaptureRequest
import com.alfread.alfvision.core.model.CaptureResult
import com.alfread.alfvision.core.util.ScreenMetrics
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ScreenCaptureService : Service() {
    private lateinit var container: com.alfread.alfvision.AppContainer
    private lateinit var projectionManager: MediaProjectionManager
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var pendingRequest: CaptureRequest? = null
    private var serviceJob: Job? = null
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Frame terbaru ditahan (tanpa copy). VirtualDisplay hanya mengirim frame saat layar berubah;
     * sebelumnya capture pada layar statis menunggu frame baru selamanya (stuck "busy").
     */
    private var heldImage: Image? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as AlfVisionApplication).container
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        createNotificationChannel()
        serviceJob = mainScope.launch {
            container.captureCoordinator.requests.collect { request ->
                // Request baru menggantikan request lama yang mungkin sudah timeout.
                pendingRequest?.let { old ->
                    container.captureCoordinator.fail(old.id, IllegalStateException("Capture replaced by a newer request"))
                }
                pendingRequest = request
                deliverPending()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }
        if (projection != null) return START_NOT_STICKY

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.parcelableIntent(EXTRA_DATA)
        if (data == null || resultCode != Activity.RESULT_OK) {
            // FIX: service yang dijalankan lewat startForegroundService() WAJIB memanggil startForeground()
            // walau akan langsung berhenti. Tanpa ini Android melempar ForegroundServiceDidNotStartInTimeException.
            runCatching { startForeground(NOTIFICATION_ID, notification()) }
            container.sessionStore.setError("Screen capture permission belum diberikan.")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            // FIX: startForeground dipanggil PERTAMA (batas waktu 5 detik) sebelum setup projection.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification())
            }
            setupProjection(resultCode, data)
            isRunning = true
            container.sessionStore.setError(null)
        } catch (t: Throwable) {
            stopCapture()
            container.sessionStore.setError("Screen capture gagal dimulai. Periksa permission dan coba lagi.")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun setupProjection(resultCode: Int, data: Intent) {
        val created = projectionManager.getMediaProjection(resultCode, data)
            ?: throw IllegalStateException("MediaProjection unavailable")
        projection = created
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                mainScope.launch {
                    stopCapture()
                    stopSelf()
                }
            }
        }
        projectionCallback = callback
        // Android 14+: callback WAJIB didaftarkan sebelum createVirtualDisplay.
        created.registerCallback(callback, mainHandler)
        createVirtualDisplay()
    }

    private fun newImageReader(width: Int, height: Int): ImageReader =
        ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3).apply {
            setOnImageAvailableListener({ onFrame() }, mainHandler)
        }

    private fun createVirtualDisplay() {
        releaseImageReader()
        virtualDisplay?.release()
        val (width, height) = ScreenMetrics.size(this)
        val dpi = resources.displayMetrics.densityDpi
        val reader = newImageReader(width.coerceAtLeast(1), height.coerceAtLeast(1))
        imageReader = reader
        virtualDisplay = projection?.createVirtualDisplay(
            "ALF Vision Panel",
            width.coerceAtLeast(1),
            height.coerceAtLeast(1),
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            null
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val display = virtualDisplay ?: return
        if (projection == null) return
        // Rotasi layar: ukuran virtual display ikut berubah supaya hasil crop tidak meleset.
        runCatching {
            val (width, height) = ScreenMetrics.size(this)
            val dpi = resources.displayMetrics.densityDpi
            val oldReader = imageReader
            runCatching { heldImage?.close() }
            heldImage = null
            val reader = newImageReader(width.coerceAtLeast(1), height.coerceAtLeast(1))
            imageReader = reader
            display.resize(width.coerceAtLeast(1), height.coerceAtLeast(1), dpi)
            display.surface = reader.surface
            runCatching { oldReader?.setOnImageAvailableListener(null, null) }
            runCatching { oldReader?.close() }
        }
    }

    private fun onFrame() {
        val reader = imageReader ?: return
        val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return
        runCatching { heldImage?.close() }
        heldImage = image
        if (pendingRequest != null) deliverPending()
    }

    private fun deliverPending() {
        val request = pendingRequest ?: return
        val image = heldImage ?: return
        try {
            val plane = image.planes.firstOrNull() ?: throw IllegalStateException("No image plane")
            val width = image.width
            val height = image.height
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            val paddedWidth = width + rowPadding / pixelStride
            val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
            val buffer = plane.buffer
            buffer.rewind()
            padded.copyPixelsFromBuffer(buffer)
            // FIX: buang kolom padding di kanan supaya lebar bitmap == lebar layar (region tidak bergeser).
            val bitmap = if (paddedWidth != width) {
                val trimmed = Bitmap.createBitmap(padded, 0, 0, width, height)
                padded.recycle()
                trimmed
            } else padded
            val region = request.region
            val cropped = if (!request.fullScreen && region != null) container.imageProcessor.cropRegion(bitmap, region) else bitmap
            val payload = container.imageProcessor.encodeJpeg(cropped, request.quality, request.maxBytes)
            if (cropped !== bitmap) cropped.recycle()
            bitmap.recycle()
            pendingRequest = null
            container.captureCoordinator.complete(CaptureResult(request.id, payload, request.region))
        } catch (t: Throwable) {
            pendingRequest = null
            container.captureCoordinator.fail(request.id, t)
            container.sessionStore.setError("Screenshot terlalu besar atau gagal diproses.")
        }
    }

    private fun releaseImageReader() {
        runCatching { heldImage?.close() }
        heldImage = null
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
        projectionCallback?.let { callback -> runCatching { projection?.unregisterCallback(callback) } }
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
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "alf_vision_capture"
        private const val NOTIFICATION_ID = 1101
        const val ACTION_STOP = "com.alfread.alfvision.capture.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "projection_data"

        private val _running = MutableStateFlow(false)

        /** Observable supaya Home bisa menampilkan status capture secara live. */
        val running: StateFlow<Boolean> = _running

        var isRunning: Boolean
            get() = _running.value
            private set(value) { _running.value = value }
    }
}

private fun Intent.parcelableIntent(key: String): Intent? {
    return if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, Intent::class.java) else @Suppress("DEPRECATION") getParcelableExtra(key)
}
