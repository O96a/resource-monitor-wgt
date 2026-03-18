package com.aamer.resourcemonitor.data.api

import com.aamer.resourcemonitor.data.models.AlarmsResponse
import com.aamer.resourcemonitor.data.models.HistoryResponse
import com.aamer.resourcemonitor.data.models.MetricsSnapshot
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ResourceMonitorApi {

    @GET("metrics")
    suspend fun getMetrics(): Response<MetricsSnapshot>

    @GET("history")
    suspend fun getHistory(
        @Query("metric") metric: String,
        @Query("window") window: String = "30m"
    ): Response<HistoryResponse>

    @GET("alarms")
    suspend fun getAlarms(): Response<AlarmsResponse>

    @POST("alarms/{id}/acknowledge")
    suspend fun acknowledgeAlarm(@Path("id") alarmId: String): Response<Unit>
}
