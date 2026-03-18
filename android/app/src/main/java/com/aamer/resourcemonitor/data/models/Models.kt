package com.aamer.resourcemonitor.data.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ── Metrics snapshot ─────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class MetricsSnapshot(
    val timestamp: String,
    @Json(name = "server_name") val serverName: String,
    val os: OsMetrics,
    val oracle: OracleMetrics?
)

@JsonClass(generateAdapter = true)
data class OsMetrics(
    @Json(name = "cpu_percent")      val cpuPercent: Float,
    @Json(name = "cpu_core_count")   val cpuCoreCount: Int,
    @Json(name = "ram_percent")      val ramPercent: Float,
    @Json(name = "ram_used_gb")      val ramUsedGb: Float,
    @Json(name = "ram_total_gb")     val ramTotalGb: Float,
    @Json(name = "disk_percent")     val diskPercent: Float,
    @Json(name = "disk_used_gb")     val diskUsedGb: Float,
    @Json(name = "disk_total_gb")    val diskTotalGb: Float,
    @Json(name = "net_bytes_sent_mb") val netSentMb: Float,
    @Json(name = "net_bytes_recv_mb") val netRecvMb: Float,
    @Json(name = "load_avg_1m")      val loadAvg1m: Float,
    @Json(name = "load_avg_5m")      val loadAvg5m: Float,
    @Json(name = "load_avg_15m")     val loadAvg15m: Float,
)

@JsonClass(generateAdapter = true)
data class OracleMetrics(
    @Json(name = "active_sessions")          val activeSessions: Int,
    @Json(name = "max_sessions")             val maxSessions: Int,
    @Json(name = "session_percent")          val sessionPercent: Float,
    @Json(name = "tablespace_used_gb")       val tablespaceUsedGb: Float,
    @Json(name = "tablespace_total_gb")      val tablespaceTotalGb: Float,
    @Json(name = "tablespace_percent")       val tablespacePercent: Float,
    @Json(name = "redo_switches_per_hour")   val redoSwitchesPerHour: Int,
    @Json(name = "slow_queries_count")       val slowQueriesCount: Int,
    @Json(name = "db_status")               val dbStatus: String,
    @Json(name = "db_version")              val dbVersion: String,
)

// ── History ──────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class HistoryResponse(
    val metric: String,
    val window: String,
    val points: Int,
    val data: List<HistoryPoint>
)

@JsonClass(generateAdapter = true)
data class HistoryPoint(
    val timestamp: String,
    val value: Float
)

// ── Alarms ───────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class AlarmsResponse(
    val count: Int,
    val alarms: List<Alarm>
)

@JsonClass(generateAdapter = true)
data class Alarm(
    val id: String,
    val metric: String,
    val value: Float,
    val threshold: Float,
    val severity: String,
    val message: String,
    @Json(name = "triggered_at") val triggeredAt: String,
    val acknowledged: Int
)
