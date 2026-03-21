package com.aamer.resourcemonitor.widget

import com.aamer.resourcemonitor.data.models.MetricsSnapshot
import java.time.Instant

data class WidgetState(
    val snapshot: MetricsSnapshot? = null,
    val cpuHistory: List<Float> = emptyList(),
    val lastUpdated: Instant? = null,
    val isSyncing: Boolean = false,
    val error: String? = null
)

object WidgetStateHolder {
    private const val MAX_HISTORY = 20
    @Volatile private var _state = WidgetState()

    val state: WidgetState get() = _state

    fun startSync() {
        _state = _state.copy(isSyncing = true, error = null)
    }

    fun update(snapshot: MetricsSnapshot) {
        val newHistory = (_state.cpuHistory + snapshot.os.cpuPercent).takeLast(MAX_HISTORY)
        
        _state = WidgetState(
            snapshot = snapshot,
            cpuHistory = newHistory,
            lastUpdated = Instant.now(),
            isSyncing = false,
            error = null
        )
    }

    fun setError(message: String) {
        _state = _state.copy(error = message, isSyncing = false)
    }
}
