package com.aamer.resourcemonitor.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.aamer.resourcemonitor.data.repository.MetricsRepository
import com.aamer.resourcemonitor.data.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class MetricsFetchWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val config = settingsRepository.configFlow.first()

        if (config.baseUrl.isBlank() || config.apiKey.isBlank()) {
            WidgetStateHolder.setError("Server not configured")
            ResourceWidget().updateAll(applicationContext)
            return Result.failure()
        }

        return try {
            val repo     = MetricsRepository.create(config.baseUrl, config.apiKey)
            val snapshot = repo.getSnapshot().getOrThrow()
            
            // Store the latest snapshot in WidgetStateHolder for Glance to read
            WidgetStateHolder.update(snapshot)

            // Trigger Glance widget re-render
            ResourceWidget().updateAll(applicationContext)

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) {
                Result.retry()
            } else {
                WidgetStateHolder.setError(e.message ?: "Connection failed")
                ResourceWidget().updateAll(applicationContext)
                Result.failure()
            }
        }
    }

    companion object {
        private const val WORK_NAME = "resource_monitor_fetch"
        private const val PERIODIC_WORK_NAME = "resource_monitor_periodic"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MetricsFetchWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = java.util.concurrent.TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        fun fetchNow(context: Context) {
            WidgetStateHolder.startSync()
            // Immediately update the widget if possible
            try {
                // In a real app, you'd trigger a broadcast or call GlanceAppWidgetManager().updateAll()
                // but since we are in a static context, we rely on the caller to update if they need immediate feedback.
            } catch (_: Exception) {}

            val request = OneTimeWorkRequestBuilder<MetricsFetchWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
