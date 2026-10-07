package com.alfread.alfvision.core

import android.content.Context
import com.alfread.alfvision.data.local.AppDatabase
import com.alfread.alfvision.data.network.GroqApiClient
import com.alfread.alfvision.data.network.GroqApiService
import com.alfread.alfvision.data.prefs.AppPreferences
import com.alfread.alfvision.data.repository.HistoryRepository
import com.alfread.alfvision.data.repository.ProfileRepository
import com.alfread.alfvision.data.repository.RegionRepository
import com.alfread.alfvision.data.secure.SecureStore
import com.alfread.alfvision.domain.ImageProcessor
import com.alfread.alfvision.domain.VisionAnalyzer
import com.alfread.alfvision.data.repository.GroqRepository

object AppContainer {
    lateinit var context: Context
        private set
    lateinit var preferences: AppPreferences
        private set
    lateinit var secureStore: SecureStore
        private set
    lateinit var database: AppDatabase
        private set
    lateinit var groq: GroqRepository
        private set
    lateinit var history: HistoryRepository
        private set
    lateinit var regions: RegionRepository
        private set
    lateinit var profiles: ProfileRepository
        private set
    lateinit var imageProcessor: ImageProcessor
        private set
    lateinit var visionAnalyzer: VisionAnalyzer
        private set

    fun init(appContext: Context) {
        if (::context.isInitialized) return
        context = appContext.applicationContext
        preferences = AppPreferences(context)
        secureStore = SecureStore(context)
        database = AppDatabase.create(context)
        val client = GroqApiClient(preferences, secureStore)
        val apiService = GroqApiService(client)
        imageProcessor = ImageProcessor(context)
        visionAnalyzer = VisionAnalyzer(imageProcessor)
        groq = GroqRepository(apiService, preferences, secureStore, imageProcessor)
        history = HistoryRepository(database, preferences)
        HistoryRepository.register(history)
        regions = RegionRepository(database)
        profiles = ProfileRepository(database)
    }
}
