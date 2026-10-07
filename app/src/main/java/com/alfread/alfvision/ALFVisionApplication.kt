package com.alfread.alfvision

import android.app.Application

class AlfVisionApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
