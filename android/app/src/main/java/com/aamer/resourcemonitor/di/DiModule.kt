package com.aamer.resourcemonitor.di

import android.content.Context
import com.aamer.resourcemonitor.data.api.ApiClientFactory
import com.aamer.resourcemonitor.data.api.ResourceMonitorApi
import com.aamer.resourcemonitor.data.repository.MetricsRepository
import com.aamer.resourcemonitor.data.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DiModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository {
        return SettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideResourceMonitorApi(settingsRepository: SettingsRepository): ResourceMonitorApi {
        // Warning: This creates the API client with the configuration present at startup.
        // In a fully reactive app, this should be a factory or re-instantiated if config changes.
        // For widget purposes, it's generally fine, or we can use a factory instead.
        val config = runBlocking { settingsRepository.configFlow.first() }
        return ApiClientFactory.create(config.baseUrl, config.apiKey)
    }

    @Provides
    @Singleton
    fun provideMetricsRepository(api: ResourceMonitorApi): MetricsRepository {
        return MetricsRepository(api)
    }
}
