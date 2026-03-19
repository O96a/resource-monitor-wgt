package com.aamer.resourcemonitor.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aamer.resourcemonitor.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "server_prefs")

data class ServerConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val serverName: String = "My Oracle Server"
)

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_BASE_URL    = stringPreferencesKey("base_url")
        private val KEY_API_KEY     = stringPreferencesKey("api_key")
        private val KEY_SERVER_NAME = stringPreferencesKey("server_name")
    }

    val configFlow: Flow<ServerConfig> = context.dataStore.data.map { prefs ->
        ServerConfig(
            baseUrl    = prefs[KEY_BASE_URL]    ?: BuildConfig.DEFAULT_SERVER_URL,
            apiKey     = prefs[KEY_API_KEY]     ?: BuildConfig.DEFAULT_API_KEY,
            serverName = prefs[KEY_SERVER_NAME] ?: "My Oracle Server"
        )
    }

    suspend fun saveConfig(config: ServerConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BASE_URL]    = config.baseUrl
            prefs[KEY_API_KEY]     = config.apiKey
            prefs[KEY_SERVER_NAME] = config.serverName
        }
    }

    suspend fun isConfigured(): Boolean {
        val prefs = context.dataStore.data.first()
        return !prefs[KEY_BASE_URL].isNullOrBlank() && !prefs[KEY_API_KEY].isNullOrBlank()
    }
}
