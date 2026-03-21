package com.aamer.resourcemonitor.widget

import com.aamer.resourcemonitor.data.models.MetricsSnapshot
import java.time.Instant

data class WidgetState(
    val snapshot: MetricsSnapshot? = null,
    val cpuHistory: List<Float> = emptyList(),
    val lastUpdated: Instant? = null,
    val error: String? = null
)

object WidgetStateHolder {
    @Volatile private var _state = WidgetState()

    val state: WidgetState get() = _state

    fun update(snapshot: MetricsSnapshot, cpuHistory: List<Float> = emptyList()) {
        _state = WidgetState(
            snapshot = snapshot,
            cpuHistory = cpuHistory,
            lastUpdated = Instant.now(),
            error = null
        )
    }

    fun setError(message: String) {
        _state = _state.copy(error = message)
    }
}
