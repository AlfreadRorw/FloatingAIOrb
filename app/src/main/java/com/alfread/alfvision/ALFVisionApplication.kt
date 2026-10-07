package com.alfread.alfvision

import android.app.Application
import com.alfread.alfvision.core.AppContainer
import com.alfread.alfvision.data.repository.HistoryRepository

class ALFVisionApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        HistoryRepository.cleanupExpiredScreenshots(this, AppContainer.preferences)
    }
}
