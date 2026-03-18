package com.aamer.resourcemonitor

import android.app.Application

class ResourceMonitorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // WorkManager auto-initializes via Jetpack Startup
    }
}
