package com.aamer.resourcemonitor

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

class ResourceMonitorApp : Application() {

    companion object {
        private const val TAG = "ResourceMonitor"
    }

    override fun onCreate() {
        super.onCreate()
        // WorkManager auto-initializes via Jetpack Startup
        initFirebaseSafely()
    }

    private fun initFirebaseSafely() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            // Re-enable messaging auto-init now that Firebase is ready
            FirebaseMessaging.getInstance().isAutoInitEnabled = true
            Log.i(TAG, "Firebase initialized successfully")
        } catch (t: Throwable) {
            Log.w(TAG, "Firebase init failed \u2013 push notifications disabled", t)
        }
    }
}
