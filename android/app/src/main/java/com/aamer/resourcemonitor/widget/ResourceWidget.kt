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

// ── Design System (V1.1.0 - Refined) ──────────────────────────────

private val BgDark      = Color(0xFF0A0D18) // Deeper black for high contrast
private val CardDark    = Color(0xFF161B2C)
private val TextPrimary = Color(0xFFE0E8FF)
private val TextMuted   = Color(0xFF6B7FA5)
private val BlueAccent  = Color(0xFF5AB0FF)
private val GreenAccent = Color(0xFF4ADE80)
private val AmberAccent = Color(0xFFFBBF24)
private val RedAccent   = Color(0xFFF87171)

private fun percentColor(pct: Float): Color = when {
    pct >= 90 -> RedAccent
    pct >= 75 -> AmberAccent
    else       -> BlueAccent
}

// ── Professional Bitmap Logic ─────────────────────────────────────

fun makeGaugeBitmap(pct: Float, context: Context): BitmapDrawable {
    val size = 300
    val bmp    = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx     = size / 2f
    val cy     = size / 2f
    
    // Exact sizing to avoid overlap
    val stroke = size * 0.07f 
    val padding = stroke * 3.0f
    val radius = size / 2f - padding
    val rect   = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

    // Track
    val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.Stroke; strokeWidth = stroke; color = 0xFF1E253A.toInt()
    }
    canvas.drawArc(rect, -220f, 260f, false, trackPaint)

    // Fill
    val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.Stroke; strokeWidth = stroke; strokeCap = Paint.Cap.Round
        val c = percentColor(pct)
        color = android.graphics.Color.argb(255, (c.red*255).toInt(), (c.green*255).toInt(), (c.blue*255).toInt())
    }
    canvas.drawArc(rect, -220f, (pct / 100f) * 260f, false, arcPaint)

    // Centered percentage
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size * 0.15f; textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        color = 0xFFE0E8FF.toInt()
    }
    val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2
    canvas.drawText("${pct.toInt()}%", cx, textY, textPaint)

    return BitmapDrawable(context.resources, bmp)
}

fun makeSparklineBitmap(points: List<Float>, context: Context): BitmapDrawable {
    val w = 600; val h = 120
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    if (points.size < 2) return BitmapDrawable(context.resources, bmp)

    val max = points.max().let { if (it < 10f) 100f else it * 1.1f }
    val min = points.min()
    val range = if (max == min) 1f else max - min
    
    val path = Path()
    val step = w.toFloat() / (points.size - 1)
    
    points.forEachIndexed { i, v ->
        val x = i * step
        val y = h - ((v - min) / range) * h * 0.8f - h * 0.1f
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        color = 0xFF5AB0FF.toInt()
    }
    canvas.drawPath(path, paint)
    return BitmapDrawable(context.resources, bmp)
}

// ── Widget Implementation ─────────────────────────────────────────

class ResourceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(100.dp, 100.dp),
            DpSize(200.dp, 100.dp),
            DpSize(300.dp, 120.dp),
            DpSize(300.dp, 220.dp),
            DpSize(400.dp, 400.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content() }
    }

    @Composable
    private fun Content() {
        val state = WidgetStateHolder.state
        val size  = LocalSize.current
        val context = LocalContext.current

        GlanceTheme {
            Box(
                modifier = GlanceModifier.fillMaxSize()
                    .background(ColorProvider(BgDark)).cornerRadius(12.dp).padding(8.dp)
            ) {
                when {
                    state.error != null -> ErrorView(state.error)
                    state.snapshot == null -> LoadingView()
                    else -> MetricsDashboard(state.snapshot, state.cpuHistory, context, size)
                }
            }
        }
    }
}

@Composable
private fun MetricsDashboard(snap: MetricsSnapshot, history: List<Float>, context: Context, size: DpSize) {
    // Show detailed info if height is at least medium
    val showDetails = size.height >= 120.dp
    val showOracle  = size.width >= 250.dp && snap.oracle != null
    val showChart   = size.height >= 180.dp

    Column(modifier = GlanceModifier.fillMaxSize()) {
        // Header (Server Title + Sync)
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(snap.server_name, style = TextStyle(color = ColorProvider(TextPrimary), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                val statusText = if (WidgetStateHolder.state.isSyncing) "Syncing..." else updatedAgo(WidgetStateHolder.state.lastUpdated)
                Text(statusText, style = TextStyle(color = ColorProvider(if (WidgetStateHolder.state.isSyncing) BlueAccent else TextMuted), fontSize = 8.sp))
            }
            Box(modifier = GlanceModifier.padding(4.dp).clickable(actionRunCallback<RefreshAction>())) {
                Image(provider = ImageProvider(android.R.drawable.ic_popup_sync), contentDescription = "Refresh", modifier = GlanceModifier.size(20.dp))
            }
        }

        Spacer(GlanceModifier.height(6.dp))

        // Row 1: Primary Gauges (Always show)
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val gSize = if (size.height < 120.dp) 55.dp else 70.dp
            GaugeCell(context, snap.os.cpu_percent, "CPU", gSize, GlanceModifier.defaultWeight())
            GaugeCell(context, snap.os.ram_percent, "RAM", gSize, GlanceModifier.defaultWeight())
            GaugeCell(context, snap.os.disk_percent, "DISK", gSize, GlanceModifier.defaultWeight())
            if (showOracle) {
                GaugeCell(context, snap.oracle!!.session_percent, "DB", gSize, GlanceModifier.defaultWeight())
            }
        }

        if (showDetails) {
            Spacer(GlanceModifier.height(10.dp))
            
            // Row 2: Basic OS Info (Always show in details)
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                MetricCard("↑${snap.os.net_bytes_sent_mb.toInt()}M", "UP", GlanceModifier.defaultWeight())
                MetricCard("↓${snap.os.net_bytes_recv_mb.toInt()}M", "DN", GlanceModifier.defaultWeight())
                MetricCard("${snap.os.load_avg_1m}", "LOAD", GlanceModifier.defaultWeight())
            }
            
            Spacer(GlanceModifier.height(4.dp))
            
            // Row 3: Capacity Info
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                MetricCard("${snap.os.ram_used_gb.toInt()}G", "USED", GlanceModifier.defaultWeight())
                MetricCard("${snap.os.ram_total_gb.toInt()}G", "TOTAL", GlanceModifier.defaultWeight())
                MetricCard("${snap.os.cpu_core_count}", "CORE", GlanceModifier.defaultWeight())
            }

            if (snap.oracle != null) {
                val ora = snap.oracle
                Spacer(GlanceModifier.height(4.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    MetricCard("${ora.redo_switches_per_hour}", "REDO", GlanceModifier.defaultWeight(), GreenAccent)
                    MetricCard("${ora.slow_queries_count}", "SLOW", GlanceModifier.defaultWeight(), if(ora.slow_queries_count > 0) AmberAccent else TextMuted)
                    MetricCard("${ora.tablespace_percent.toInt()}%", "TBSP", GlanceModifier.defaultWeight())
                }
            }
        }

        if (showChart && history.size > 1) {
            Spacer(GlanceModifier.height(10.dp))
            Box(modifier = GlanceModifier.fillMaxWidth().height(35.dp).padding(horizontal = 4.dp)) {
                Image(provider = ImageProvider(makeSparklineBitmap(history, context).bitmap), 
                    contentDescription = "Chart", modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            }
        }
        
        Spacer(GlanceModifier.defaultWeight())
        
        // Version Tag (To verify user is on the right build)
        Text("v1.1.0", style = TextStyle(color = ColorProvider(TextMuted.copy(alpha = 0.5f)), fontSize = 7.sp))
    }
}

@Composable
private fun GaugeCell(context: Context, pct: Float, label: String, size: androidx.compose.ui.unit.Dp, modifier: GlanceModifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(provider = ImageProvider(makeGaugeBitmap(pct, context).bitmap), contentDescription = label,
            modifier = GlanceModifier.size(size), contentScale = ContentScale.Fit)
        Text(label, style = TextStyle(color = ColorProvider(TextMuted), fontSize = 9.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun MetricCard(value: String, label: String, modifier: GlanceModifier, valColor: Color = TextPrimary) {
    Column(modifier = modifier.padding(1.dp).background(ColorProvider(CardDark)).cornerRadius(6.dp).padding(4.dp), 
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = TextStyle(color = ColorProvider(valColor), fontSize = 10.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(label, style = TextStyle(color = ColorProvider(TextMuted), fontSize = 7.sp), maxLines = 1)
    }
}

@Composable
private fun LoadingView() {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Connecting...", style = TextStyle(color = ColorProvider(TextMuted), fontSize = 12.sp))
    }
}

@Composable
private fun ErrorView(msg: String) {
    Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        Text("Offline", style = TextStyle(color = ColorProvider(RedAccent), fontSize = 13.sp, fontWeight = FontWeight.Bold))
        Text(msg.take(20), style = TextStyle(color = ColorProvider(TextMuted), fontSize = 10.sp))
        Spacer(GlanceModifier.height(6.dp))
        Button("Retry", actionRunCallback<RefreshAction>())
    }
}

private fun updatedAgo(last: java.time.Instant?): String {
    val sec = java.time.Instant.now().epochSecond - (last?.epochSecond ?: 0)
    return if (last == null) "Never" else if (sec < 60) "${sec}s" else "${sec/60}m"
}

class ResourceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ResourceWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); MetricsFetchWorker.schedule(context); MetricsFetchWorker.fetchNow(context) }
    override fun onUpdate(c: Context, m: android.appwidget.AppWidgetManager, ids: IntArray) { super.onUpdate(c, m, ids); MetricsFetchWorker.fetchNow(c) }
    override fun onDisabled(context: Context) { super.onDisabled(context); MetricsFetchWorker.cancel(context) }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        MetricsFetchWorker.fetchNow(context)
        ResourceWidget().update(context, glanceId)
    }
}
