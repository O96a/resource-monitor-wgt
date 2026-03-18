package com.aamer.resourcemonitor.widget

import com.aamer.resourcemonitor.data.models.MetricsSnapshot
import java.time.Instant

data class WidgetState(
    val snapshot: MetricsSnapshot? = null,
    val lastUpdated: Instant? = null,
    val error: String? = null
)

object WidgetStateHolder {
    @Volatile private var _state = WidgetState()

    val state: WidgetState get() = _state

    fun update(snapshot: MetricsSnapshot) {
        _state = WidgetState(snapshot = snapshot, lastUpdated = Instant.now(), error = null)
    }

    fun setError(message: String) {
        _state = _state.copy(error = message)
    }
}
