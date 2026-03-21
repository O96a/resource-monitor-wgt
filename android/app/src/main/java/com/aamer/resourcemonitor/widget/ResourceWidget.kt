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
            DpSize(120.dp, 60.dp),    // Smallest
            DpSize(180.dp, 110.dp),   // 2x1
            DpSize(250.dp, 110.dp),   // 3x1
            DpSize(320.dp, 180.dp),   // 4x2
            DpSize(400.dp, 260.dp),   // 5x3
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

        // Determine available space
        val isWide = size.width >= 250.dp
        val isTall = size.height >= 180.dp
        val isXLarge = size.width >= 350.dp && size.height >= 300.dp

        GlanceTheme {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(BgDark))
                    .cornerRadius(16.dp)
                    .padding(8.dp)
            ) {
                when {
                    state.snapshot == null && state.error != null -> ErrorView(state.error)
                    state.snapshot == null                        -> LoadingView()
                    else -> MetricsView(
                        snapshot = state.snapshot,
                        context  = context,
                        size     = size,
                        isWide   = isWide,
                        isTall   = isTall,
                        isXLarge = isXLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Collecting…", style = TextStyle(color = ColorProvider(TextMuted), fontSize = 12.sp))
    }
}

@Composable
private fun ErrorView(message: String) {
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Connection error", style = TextStyle(color = ColorProvider(Color(0xFFE24B4A)), fontSize = 13.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(4.dp))
        Text(message.take(60), style = TextStyle(color = ColorProvider(TextMuted), fontSize = 11.sp))
    }
}

@Composable
private fun MetricsView(
    snapshot: MetricsSnapshot,
    context: Context,
    size: DpSize,
    isWide: Boolean,
    isTall: Boolean,
    isXLarge: Boolean
) {
    val os     = snapshot.os
    val oracle = snapshot.oracle

    Column(modifier = GlanceModifier.fillMaxSize()) {
        // ── Header ────────────────────────────────────────────
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                snapshot.serverName,
                style = TextStyle(color = ColorProvider(TextPrimary), 
                    fontSize = if (size.width < 150.dp) 10.sp else 12.sp, 
                    fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight()
            )
            if (WidgetStateHolder.state.isSyncing) {
                Text("Syncing...", style = TextStyle(color = ColorProvider(BlueAccent), fontSize = 10.sp))
            } else if (size.width > 180.dp) {
                Text(updatedAgo(WidgetStateHolder.state.lastUpdated), style = TextStyle(color = ColorProvider(TextMuted), fontSize = 9.sp))
            }
            Spacer(GlanceModifier.width(4.dp))
            Image(
                provider = ImageProvider(android.R.drawable.ic_popup_sync),
                contentDescription = "Refresh",
                modifier = GlanceModifier.size(16.dp).clickable(actionRunCallback<RefreshAction>())
            )
        }

        Spacer(GlanceModifier.height(if (size.height < 100.dp) 4.dp else 8.dp))

        // ── Gauges Row (CPU, RAM, DISK, [SESS]) ───────────────
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GaugeCell(context, os.cpuPercent, "CPU", GlanceModifier.defaultWeight())
            GaugeCell(context, os.ramPercent, "RAM", GlanceModifier.defaultWeight())
            GaugeCell(context, os.diskPercent, "DISK", GlanceModifier.defaultWeight())
            if (oracle != null && isWide) {
                GaugeCell(context, oracle.sessionPercent, "SESS", GlanceModifier.defaultWeight())
            }
        }

        // ── Secondary Info ────────────────────────────────────
        if (isTall) {
            Spacer(GlanceModifier.height(8.dp))
            // Row 1: Load and Network
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                StatCard("Load", "${os.loadAvg1m}", GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(4.dp))
                StatCard("Net ↑", "${os.netSentMb.toInt()}M", GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(4.dp))
                StatCard("Net ↓", "${os.netRecvMb.toInt()}M", GlanceModifier.defaultWeight())
            }
            
            if (isXLarge) {
                Spacer(GlanceModifier.height(4.dp))
                // Row 2: Capacity details
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    StatCard("RAM", "${os.ramUsedGb.toInt()}/${os.ramTotalGb.toInt()}G", GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(4.dp))
                    StatCard("Disk", "${os.diskUsedGb.toInt()}/${os.diskTotalGb.toInt()}G", GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(4.dp))
                    StatCard("Cores", "${os.cpuCoreCount}", GlanceModifier.defaultWeight())
                }
                
                if (oracle != null) {
                    Spacer(GlanceModifier.height(4.dp))
                    // Row 3: Oracle details
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        StatCard("Tablespace", "${oracle.tablespacePercent.toInt()}%", GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(4.dp))
                        StatCard("DB Status", oracle.dbStatus, GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(4.dp))
                        StatCard("Sessions", "${oracle.activeSessions}/${oracle.maxSessions}", GlanceModifier.defaultWeight())
                    }
                }
            }
        }

        // ── Alarm Bar ─────────────────────────────────────────
        val alarms = buildList {
            if (os.cpuPercent >= 85f) add("CPU")
            if (os.ramPercent >= 90f) add("RAM")
            if (os.diskPercent >= 80f) add("DSK")
            oracle?.let { if (it.tablespacePercent >= 85f) add("DB") }
        }
        if (alarms.isNotEmpty() && size.height > 120.dp) {
            Spacer(GlanceModifier.height(6.dp))
            Box(
                modifier = GlanceModifier.fillMaxWidth().background(ColorProvider(Color(0x44E24B4A))).cornerRadius(4.dp).padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("ALARM: ${alarms.joinToString(", ")}", style = TextStyle(color = ColorProvider(Color(0xFFFFAAAA)), fontSize = 10.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: GlanceModifier = GlanceModifier) {
    Column(
        modifier = modifier.background(ColorProvider(CardDark)).cornerRadius(6.dp).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = TextStyle(color = ColorProvider(TextPrimary), fontSize = 10.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(label, style = TextStyle(color = ColorProvider(TextMuted), fontSize = 8.sp), maxLines = 1)
    }
}

@Composable
private fun GaugeCell(context: Context, pct: Float, label: String, modifier: GlanceModifier = GlanceModifier) {
    val size = LocalSize.current
    val gaugeHeight = if (size.height < 120.dp) 40.dp else 60.dp
    
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = GlanceModifier.fillMaxWidth().height(gaugeHeight), contentAlignment = Alignment.Center) {
            Image(
                provider = ImageProvider(makeGaugeBitmap(pct, context).bitmap),
                contentDescription = "$label $pct%",
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        if (size.height > 80.dp) {
            Text(label, style = TextStyle(color = ColorProvider(TextMuted), fontSize = 9.sp, fontWeight = FontWeight.Bold))
        }
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
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // Trigger the background fetch
        MetricsFetchWorker.fetchNow(context)
        
        // Immediately update the UI to show the 'Syncing...' state
        ResourceWidget().update(context, glanceId)
    }
}