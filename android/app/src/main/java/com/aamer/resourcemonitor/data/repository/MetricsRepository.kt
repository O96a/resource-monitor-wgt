package com.aamer.resourcemonitor.data.repository

import com.aamer.resourcemonitor.data.api.ApiClientFactory
import com.aamer.resourcemonitor.data.api.ResourceMonitorApi
import com.aamer.resourcemonitor.data.models.AlarmsResponse
import com.aamer.resourcemonitor.data.models.HistoryResponse
import com.aamer.resourcemonitor.data.models.MetricsSnapshot

class MetricsRepository(private val api: ResourceMonitorApi) {

    companion object {
        fun create(baseUrl: String, apiKey: String): MetricsRepository =
            MetricsRepository(ApiClientFactory.create(baseUrl, apiKey))
    }

    suspend fun getSnapshot(): Result<MetricsSnapshot> = runCatching {
        val resp = api.getMetrics()
        resp.body() ?: error("Empty response body (${resp.code()})")
    }

    suspend fun getHistory(metric: String, window: String = "30m"): Result<HistoryResponse> =
        runCatching {
            val resp = api.getHistory(metric, window)
            resp.body() ?: error("Empty history response (${resp.code()})")
        }

    suspend fun getAlarms(): Result<AlarmsResponse> = runCatching {
        val resp = api.getAlarms()
        resp.body() ?: error("Empty alarms response (${resp.code()})")
    }

    suspend fun acknowledgeAlarm(alarmId: String): Result<Unit> = runCatching {
        api.acknowledgeAlarm(alarmId)
    }
}
