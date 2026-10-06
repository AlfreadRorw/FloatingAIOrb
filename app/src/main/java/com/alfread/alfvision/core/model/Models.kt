package com.alfread.alfvision.core.model

import android.graphics.Rect

enum class Role { USER, ASSISTANT, SYSTEM }

data class ChatLine(
    val id: Long,
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imagePath: String? = null,
    val model: String? = null,
    val tokenUsage: Int? = null
)

data class Region(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val displayId: Int = 0,
    val rotation: Int = 0
) {
    fun toRect(): Rect = Rect(x, y, x + width, y + height)
    fun normalized(maxWidth: Int, maxHeight: Int): Region {
        val sx = if (screenWidth > 0) maxWidth.toFloat() / screenWidth else 1f
        val sy = if (screenHeight > 0) maxHeight.toFloat() / screenHeight else 1f
        val nx = (x * sx).toInt().coerceIn(0, maxWidth)
        val ny = (y * sy).toInt().coerceIn(0, maxHeight)
        val nw = (width * sx).toInt().coerceIn(1, maxWidth - nx)
        val nh = (height * sy).toInt().coerceIn(1, maxHeight - ny)
        return copy(x = nx, y = ny, width = nw, height = nh, screenWidth = maxWidth, screenHeight = maxHeight)
    }
}

data class GroqModel(
    val id: String,
    val active: Boolean,
    val ownedBy: String? = null,
    val contextWindow: Long? = null,
    val maxCompletionTokens: Long? = null,
    val supportsVision: Boolean = false
)

data class ModelUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)

data class AiResult(
    val text: String,
    val model: String,
    val usage: ModelUsage = ModelUsage()
)

data class AiProfile(
    val id: Long = 0,
    val name: String,
    val systemPrompt: String,
    val temperature: Float = 0.2f,
    val maxTokens: Int = 1024,
    val preferredModel: String? = null,
    val builtIn: Boolean = false
)

enum class ResponseStyle(val label: String) {
    SHORT("Short"), NORMAL("Normal"), DETAILED("Detailed"), TECHNICAL("Technical"), STEP_BY_STEP("Step-by-step")
}

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class Accent(val label: String, val argb: Long) {
    BLUE("Blue", 0xFF3F51B5), PURPLE("Purple", 0xFF7B4DFF), CYAN("Cyan", 0xFF00BCD4),
    GREEN("Green", 0xFF2E7D32), RED("Red", 0xFFC62828), GOLD("Gold", 0xFFFFA000)
}

enum class AutoAnalyzeInterval(val label: String, val millis: Long) {
    HALF_SECOND("0.5 second", 500), ONE_SECOND("1 second", 1_000), TWO_SECONDS("2 seconds", 2_000),
    FIVE_SECONDS("5 seconds", 5_000), TEN_SECONDS("10 seconds", 10_000), THIRTY_SECONDS("30 seconds", 30_000)
}

data class VisionSettings(
    val captureOnDemand: Boolean = true,
    val autoAnalyze: Boolean = false,
    val interval: AutoAnalyzeInterval = AutoAnalyzeInterval.TEN_SECONDS,
    val quality: Int = 88,
    val maxImageBytes: Int = 7_000_000,
    val saveScreenshots: Boolean = false,
    val sendOnlyRegion: Boolean = true,
    val screenshotPreview: Boolean = true,
    val freezeFrame: Boolean = true
)

data class FloatingSettings(
    val panelWidthDp: Int = 360,
    val panelHeightDp: Int = 540,
    val orbSizeDp: Int = 60,
    val opacity: Float = 0.96f,
    val snapToEdge: Boolean = true,
    val locked: Boolean = false,
    val compactMode: Boolean = false,
    val autoHide: Boolean = false,
    val autoHideMillis: Long = 8_000,
    val animation: Boolean = true,
    val x: Int = 24,
    val y: Int = 140
)

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.PURPLE,
    val activeModel: String = "qwen/qwen3.8-27b",
    val activeProfileId: Long = 0,
    val temperature: Float = 0.2f,
    val maxTokens: Int = 1024,
    val responseStyle: ResponseStyle = ResponseStyle.NORMAL,
    val saveHistory: Boolean = true,
    val autoDeleteDays: Int = 0,
    val debugLogging: Boolean = false,
    val networkTimeoutSeconds: Long = 30,
    val retryCount: Int = 2,
    val shizukuEnhanced: Boolean = false,
    val vision: VisionSettings = VisionSettings(),
    val floating: FloatingSettings = FloatingSettings()
)

data class ImagePayload(
    val bytes: ByteArray,
    val mimeType: String = "image/jpeg",
    val width: Int,
    val height: Int,
    val byteCount: Int = bytes.size
)

data class CaptureRequest(
    val id: String,
    val region: Region?,
    val fullScreen: Boolean = false,
    val quality: Int = 88,
    val maxBytes: Int = 7_000_000
)

data class CaptureResult(
    val requestId: String,
    val payload: ImagePayload,
    val region: Region?
)

data class PendingImage(
    val bytes: ByteArray,
    val mimeType: String,
    val label: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class PanelMode { NORMAL, COMPACT, EXPANDED }
