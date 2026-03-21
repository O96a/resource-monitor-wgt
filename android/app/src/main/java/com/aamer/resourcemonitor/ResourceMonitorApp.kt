package com.aamer.resourcemonitor

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ResourceMonitorApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    companion object {
        private const val TAG = "ResourceMonitor"
    }

    override fun onCreate() {
        super.onCreate()
        initFirebaseSafely()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

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
