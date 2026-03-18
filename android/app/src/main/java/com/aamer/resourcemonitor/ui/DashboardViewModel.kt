package com.aamer.resourcemonitor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aamer.resourcemonitor.data.models.Alarm
import com.aamer.resourcemonitor.data.models.HistoryPoint
import com.aamer.resourcemonitor.data.models.MetricsSnapshot
import com.aamer.resourcemonitor.data.repository.MetricsRepository
import com.aamer.resourcemonitor.data.repository.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardUiState(
    val snapshot: MetricsSnapshot? = null,
    val cpuHistory: List<HistoryPoint> = emptyList(),
    val alarms: List<Alarm> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isConfigured: Boolean = false,
)

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(app)

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepo.configFlow.collect { config ->
                val configured = config.baseUrl.isNotBlank() && config.apiKey.isNotBlank()
                _state.update { it.copy(isConfigured = configured) }
                if (configured) refresh(config.baseUrl, config.apiKey)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val config = settingsRepo.configFlow.first()
            if (config.baseUrl.isNotBlank() && config.apiKey.isNotBlank()) {
                refresh(config.baseUrl, config.apiKey)
            }
        }
    }

    private suspend fun refresh(baseUrl: String, apiKey: String) {
        _state.update { it.copy(isLoading = true, error = null) }
        val repo = MetricsRepository.create(baseUrl, apiKey)

        val snapshotResult = repo.getSnapshot()
        snapshotResult.onSuccess { snap ->
            _state.update { it.copy(snapshot = snap) }
        }.onFailure { e ->
            _state.update { it.copy(error = e.message ?: "Connection failed") }
        }

        repo.getHistory("os.cpu_percent", "30m").onSuccess { hist ->
            _state.update { it.copy(cpuHistory = hist.data) }
        }

        repo.getAlarms().onSuccess { alarms ->
            _state.update { it.copy(alarms = alarms.alarms) }
        }

        _state.update { it.copy(isLoading = false) }
    }

    fun acknowledgeAlarm(alarmId: String) {
        viewModelScope.launch {
            val config = settingsRepo.configFlow.first()
            MetricsRepository.create(config.baseUrl, config.apiKey).acknowledgeAlarm(alarmId)
            _state.update { s ->
                s.copy(alarms = s.alarms.filter { it.id != alarmId })
            }
        }
    }
}
