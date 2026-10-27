package com.sync.music

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
