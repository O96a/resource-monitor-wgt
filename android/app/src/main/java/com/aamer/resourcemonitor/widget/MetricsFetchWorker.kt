package com.aamer.resourcemonitor.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.*
import com.aamer.resourcemonitor.data.repository.MetricsRepository
import com.aamer.resourcemonitor.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class MetricsFetchWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val settings = SettingsRepository(applicationContext)
        val config = settings.configFlow.first()

        if (config.baseUrl.isBlank() || config.apiKey.isBlank()) {
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
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "resource_monitor_periodic"

        fun schedule(context: Context, intervalMinutes: Long = 15L) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<MetricsFetchWorker>(
                repeatInterval = intervalMinutes,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun fetchNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<MetricsFetchWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
