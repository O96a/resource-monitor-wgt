package com.aamer.resourcemonitor.widget

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.aamer.resourcemonitor.data.models.MetricsSnapshot

// ── Colour helpers ────────────────────────────────────────────────

private fun percentColor(pct: Float): Color = when {
    pct >= 90 -> Color(0xFFE24B4A)   // red
    pct >= 75 -> Color(0xFFEF9F27)   // amber
    else       -> Color(0xFF4A9EFF)  // blue
}

private val BgDark    = Color(0xFF1A1F2E)
private val CardDark  = Color(0xFF222840)
private val TextPrimary = Color(0xFFD0DCFF)
private val TextMuted   = Color(0xFF5A7090)
private val BlueAccent  = Color(0xFF4A9EFF)

// ── Arc gauge bitmap factory ──────────────────────────────────────
// Generates a high-res bitmap to be scaled smoothly by ContentScale.Fit
fun makeGaugeBitmap(pct: Float, context: Context): BitmapDrawable {
    val size = 400 // High-res fixed size
    val bmp    = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx     = size / 2f
    val cy     = size / 2f
    val strokeWidth = size * 0.12f
    val r      = size / 2f - strokeWidth
    val oval   = RectF(cx - r, cy - r, cx + r, cy + r)

    val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        color = android.graphics.Color.parseColor("#2A3050")
    }
    canvas.drawArc(oval, -220f, 260f, false, track)

    val arcColor = percentColor(pct)
    val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        strokeCap = Paint.Cap.ROUND
        color = android.graphics.Color.argb(
            255,
            (arcColor.red * 255).toInt(),
            (arcColor.green * 255).toInt(),
            (arcColor.blue * 255).toInt()
        )
    }
    val sweep = (pct / 100f) * 260f
    canvas.drawArc(oval, -220f, sweep, false, arc)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size * 0.25f
        textAlign = Paint.Align.CENTER
        color = android.graphics.Color.parseColor("#D0DCFF")
        typeface = Typeface.DEFAULT_BOLD
    }
    val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2
    canvas.drawText("${pct.toInt()}%", cx, textY, textPaint)

    return BitmapDrawable(context.resources, bmp)
}

// ── Main widget composable ────────────────────────────────────────

class ResourceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(100.dp, 50.dp),    // 1x1
            DpSize(180.dp, 100.dp),   // 2x1
            DpSize(250.dp, 100.dp),   // 3x1
            DpSize(300.dp, 180.dp),   // 4x2
            DpSize(400.dp, 250.dp),   // 5x3
            DpSize(400.dp, 400.dp),   // 5x5+
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content() }
    }

    @Composable
    private fun Content() {
        val context = LocalContext.current
        val state   = WidgetStateHolder.state
        val size    = LocalSize.current

        val isSmall = size.height < 120.dp
        val isWide  = size.width >= 250.dp
        val isFull  = size.height >= 240.dp

        GlanceTheme {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(BgDark))
                    .cornerRadius(12.dp)
                    .padding(4.dp)
            ) {
                when {
                    state.error != null -> ErrorView(state.error)
                    state.snapshot == null -> LoadingView()
                    else -> MetricsView(
                        snapshot = state.snapshot,
                        context  = context,
                        size     = size,
                        isSmall  = isSmall,
                        isWide   = isWide,
                        isFull   = isFull
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricsView(
    snapshot: MetricsSnapshot,
    context: Context,
    size: DpSize,
    isSmall: Boolean,
    isWide: Boolean,
    isFull: Boolean
) {
    val os     = snapshot.os
    val oracle = snapshot.oracle

    Column(modifier = GlanceModifier.fillMaxSize().padding(4.dp)) {
        // ── Header (Server Name + Sync status + Refresh) ──────
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    snapshot.serverName,
                    style = TextStyle(color = ColorProvider(TextPrimary), 
                        fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                if (WidgetStateHolder.state.isSyncing) {
                    Text("Syncing...", style = TextStyle(color = ColorProvider(BlueAccent), fontSize = 9.sp))
                } else {
                    Text(updatedAgo(WidgetStateHolder.state.lastUpdated), 
                        style = TextStyle(color = ColorProvider(TextMuted), fontSize = 9.sp))
                }
            }
            
            // Larger touch target for refresh
            Box(
                modifier = GlanceModifier.size(32.dp).clickable(actionRunCallback<RefreshAction>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(android.R.drawable.ic_popup_sync),
                    contentDescription = "Refresh",
                    modifier = GlanceModifier.size(20.dp)
                )
            }
        }

        Spacer(GlanceModifier.height(4.dp))

        // ── Gauges Section ────────────────────────────────────
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val gaugeHeight = if (isSmall) 50.dp else 85.dp
            
            GaugeCell(context, os.cpuPercent, "CPU", gaugeHeight, GlanceModifier.defaultWeight())
            GaugeCell(context, os.ramPercent, "RAM", gaugeHeight, GlanceModifier.defaultWeight())
            GaugeCell(context, os.diskPercent, "DSK", gaugeHeight, GlanceModifier.defaultWeight())
            
            if (oracle != null && isWide) {
                GaugeCell(context, oracle.sessionPercent, "DB", gaugeHeight, GlanceModifier.defaultWeight())
            }
        }

        // ── Metrics Grid ──────────────────────────────────────
        if (!isSmall) {
            Spacer(GlanceModifier.height(6.dp))
            
            // Row 1: Net & Load
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                StatCard("↑ ${os.netSentMb.toInt()}M", "NET", GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(4.dp))
                StatCard("↓ ${os.netRecvMb.toInt()}M", "NET", GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(4.dp))
                StatCard("${os.loadAvg1m}", "LOAD", GlanceModifier.defaultWeight())
            }

            if (isFull) {
                Spacer(GlanceModifier.height(4.dp))
                // Row 2: Capacity details
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    StatCard("${os.ramUsedGb.toInt()}/${os.ramTotalGb.toInt()}G", "RAM", GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(4.dp))
                    StatCard("${os.diskUsedGb.toInt()}/${os.diskTotalGb.toInt()}G", "DISK", GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(4.dp))
                    StatCard("${os.cpuCoreCount}", "CORES", GlanceModifier.defaultWeight())
                }
                
                if (oracle != null) {
                    Spacer(GlanceModifier.height(4.dp))
                    // Row 3: Oracle details
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        StatCard("${oracle.tablespacePercent.toInt()}%", "TBSP", GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(4.dp))
                        StatCard(oracle.dbStatus, "STATUS", GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(4.dp))
                        StatCard("${oracle.activeSessions}/${oracle.maxSessions}", "SESS", GlanceModifier.defaultWeight())
                    }
                }
            }
        }

        // ── Alarms ────────────────────────────────────────────
        val activeAlarms = buildList {
            if (os.cpuPercent >= 85f) add("CPU")
            if (os.ramPercent >= 90f) add("RAM")
            if (os.diskPercent >= 80f) add("DSK")
            oracle?.let { if (it.tablespacePercent >= 85f) add("DB") }
        }
        
        if (activeAlarms.isNotEmpty()) {
            Spacer(GlanceModifier.height(4.dp))
            Box(
                modifier = GlanceModifier.fillMaxWidth().background(ColorProvider(Color(0x66E24B4A))).cornerRadius(4.dp).padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("⚠ ${activeAlarms.joinToString(", ")}", 
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 9.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: GlanceModifier = GlanceModifier) {
    Column(
        modifier = modifier.background(ColorProvider(CardDark)).cornerRadius(4.dp).padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = TextStyle(color = ColorProvider(TextPrimary), fontSize = 10.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(label, style = TextStyle(color = ColorProvider(TextMuted), fontSize = 7.sp), maxLines = 1)
    }
}

@Composable
private fun GaugeCell(context: Context, pct: Float, label: String, height: androidx.compose.ui.unit.Dp, modifier: GlanceModifier = GlanceModifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = GlanceModifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Image(
                provider = ImageProvider(makeGaugeBitmap(pct, context).bitmap),
                contentDescription = "$label $pct%",
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        Text(label, style = TextStyle(color = ColorProvider(TextMuted), fontSize = 8.sp, fontWeight = FontWeight.Bold))
    }
}

private fun updatedAgo(lastUpdated: java.time.Instant?): String {
    lastUpdated ?: return "Never"
    val sec = java.time.Instant.now().epochSecond - lastUpdated.epochSecond
    return when {
        sec < 60  -> "${sec}s ago"
        sec < 3600 -> "${sec / 60}m ago"
        else       -> "${sec / 3600}h ago"
    }
}

// ── Widget receiver & action ──────────────────────────────────────

class ResourceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ResourceWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MetricsFetchWorker.schedule(context)
        MetricsFetchWorker.fetchNow(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        MetricsFetchWorker.fetchNow(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        MetricsFetchWorker.cancel(context)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // Trigger the background fetch
        MetricsFetchWorker.fetchNow(context)
        
        // Immediately update the UI to show the 'Syncing...' state
        ResourceWidget().update(context, glanceId)
    }
}