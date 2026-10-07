package com.alfread.alfvision.core

object Constants {
    const val GROQ_BASE_URL = "https://api.groq.com/openai/v1/"
    const val DEFAULT_MODEL = "qwen/qwen3.8-27b"
    const val MAX_IMAGE_BYTES = 4_500_000
    const val MAX_IMAGE_DIMENSION = 1600
    const val CAPTURE_NOTIFICATION_ID = 4101
    const val CAPTURE_CHANNEL_ID = "alf_vision_capture"
    const val PANEL_DEFAULT_WIDTH = 360
    const val PANEL_DEFAULT_HEIGHT = 520
    const val ORB_DEFAULT_SIZE = 64
    const val PANEL_MIN_WIDTH = 300
    const val PANEL_MIN_HEIGHT = 260
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AccentColor { BLUE, PURPLE, CYAN, GREEN, RED, GOLD }

enum class ResponseStyle { SHORT, NORMAL, DETAILED, TECHNICAL, STEP_BY_STEP }

enum class PanelStyle { SOLID, GLASS, ELEVATED }

enum class AutoDeletePeriod { NEVER, DAY_1, DAYS_7, DAYS_30 }

enum class AnalysisMode { CAPTURE_ONCE, ANALYZE, AUTO_ANALYZE, FREEZE_FRAME }

enum class RegionHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
