package com.alfread.alfvision

import android.content.Context
import androidx.room.Room
import com.alfread.alfvision.core.util.*
import com.alfread.alfvision.data.local.AppDatabase
import com.alfread.alfvision.data.network.GroqRepository
import com.alfread.alfvision.data.network.buildHttpClient
import com.alfread.alfvision.data.repository.*
import com.alfread.alfvision.vision.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

class AppContainer(context: Context) {
    val appContext = context.applicationContext
    val database: AppDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, "alf_vision.db")
        .fallbackToDestructiveMigration()
        .build()
    val secureStore = SecureStore(appContext)
    val settingsRepository = SettingsRepository(appContext, database.appSettingsDao())
    val historyRepository = HistoryRepository(database)
    val regionRepository = RegionRepository(database.regionPresetDao())
    val profileRepository = ProfileRepository(database.aiProfileDao())
    val sessionStore = SessionStore()
    val imageProcessor = ImageProcessor()
    val imageStorage = ImageStorage(appContext)
    val captureCoordinator = CaptureCoordinator()
    val networkMonitor = NetworkMonitor(appContext)
    val shizukuCompat = ShizukuCompat(appContext)
    val voiceInputManager = VoiceInputManager(appContext, sessionStore)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val groqRepository = GroqRepository(
        secureStore = secureStore,
        clientProvider = { buildHttpClient(settingsSnapshotTimeout) },
        timeoutProvider = { settingsSnapshotTimeout },
        retryProvider = { settingsSnapshotRetry }
    )

    val controller = VisionAssistantController(
        settings = settingsRepository,
        profiles = profileRepository,
        history = historyRepository,
        groq = groqRepository,
        capture = captureCoordinator,
        imageProcessor = imageProcessor,
        imageStorage = imageStorage,
        session = sessionStore
    )

    @Volatile private var settingsSnapshotTimeout: Long = 30
    @Volatile private var settingsSnapshotRetry: Int = 2

    init {
        scope.launch {
            runCatching {
                profileRepository.ensureDefaults()
                val initial = settingsRepository.flow.first()
                if (initial.autoDeleteDays > 0) {
                    historyRepository.deleteOlderThan(
                        System.currentTimeMillis() - initial.autoDeleteDays * 86_400_000L
                    )
                }
            }

            runCatching {
                var lastAutoAnalyze = false
                settingsRepository.flow.collect { value ->
                    settingsSnapshotTimeout = value.networkTimeoutSeconds
                    settingsSnapshotRetry = value.retryCount
                    // Auto Analyze: switch di Settings sekarang benar-benar menyalakan/mematikan loop.
                    if (value.vision.autoAnalyze != lastAutoAnalyze) {
                        lastAutoAnalyze = value.vision.autoAnalyze
                        withContext(Dispatchers.Main) { controller.setAutoAnalyze(lastAutoAnalyze) }
                    }
                }
            }
        }
    }

    fun close() {
        voiceInputManager.stop()
        controller.stop()
        database.close()
        scope.cancel()
    }
}
