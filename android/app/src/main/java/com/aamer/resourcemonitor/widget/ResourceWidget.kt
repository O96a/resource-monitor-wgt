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

// ── Design System ────────────────────────────────────────────────

private val BgDark      = Color(0xFF0F1220)
private val CardDark    = Color(0xFF1A1F2E)
private val TextPrimary = Color(0xFFD0DCFF)
private val TextMuted   = Color(0xFF5A7090)
private val BlueAccent  = Color(0xFF4A9EFF)
private val GreenAccent = Color(0xFF34D399)
private val AmberAccent = Color(0xFFEF9F27)
private val RedAccent   = Color(0xFFE24B4A)

private fun percentColor(pct: Float): Color = when {
    pct >= 90 -> RedAccent
    pct >= 75 -> AmberAccent
    else       -> BlueAccent
}

// ── High-Quality Bitmap Drawing ───────────────────────────────────

fun makeGaugeBitmap(pct: Float, context: Context): BitmapDrawable {
    val size = 300
    val bmp    = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx     = size / 2f
    val cy     = size / 2f
    val stroke = size * 0.12f
    val radius = size / 2f - stroke
    val rect   = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

    // Track
    val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = stroke; color = 0xFF2A3050.toInt()
    }
    canvas.drawArc(rect, -220f, 260f, false, trackPaint)

    // Fill
    val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
        val c = percentColor(pct)
        color = Color.argb(255, (c.red*255).toInt(), (c.green*255).toInt(), (c.blue*255).toInt()).hashCode()
    }
    canvas.drawArc(rect, -220f, (pct / 100f) * 260f, false, arcPaint)

    // Text - Professional sizing and centering
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size * 0.22f; textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        color = 0xFFD0DCFF.toInt()
    }
    canvas.drawText("${pct.toInt()}%", cx, cy + (textPaint.textSize / 3f), textPaint)

    return BitmapDrawable(context.resources, bmp)
}

fun makeSparklineBitmap(points: List<Float>, context: Context): BitmapDrawable {
    val w = 600; val h = 150
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    if (points.size < 2) return BitmapDrawable(context.resources, bmp)

    val max = points.max().let { if (it < 10f) 100f else it * 1.1f }
    val path = Path()
    val step = w.toFloat() / (points.size - 1)
    
    points.forEachIndexed { i, v ->
        val x = i * step
        val y = h - (v / max) * h * 0.8f - h * 0.1f
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 6f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        color = 0xFF4A9EFF.toInt()
    }
    canvas.drawPath(path, paint)
    
    return BitmapDrawable(context.resources, bmp)
}

// ── Widget Implementation ─────────────────────────────────────────

class ResourceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(120.dp, 120.dp),   // Small
            DpSize(250.dp, 120.dp),   // Wide
            DpSize(300.dp, 250.dp),   // 4x3
            DpSize(400.dp, 400.dp),   // Full
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content() }
    }

    @Composable
    private fun Content() {
        val state   = WidgetStateHolder.state
        val size    = LocalSize.current
        val context = LocalContext.current

        GlanceTheme {
            Box(
                modifier = GlanceModifier.fillMaxSize()
                    .background(ColorProvider(BgDark)).cornerRadius(16.dp).padding(10.dp)
            ) {
                when {
                    state.error != null -> ErrorView(state.error)
                    state.snapshot == null -> LoadingView()
                    else -> MetricsView(state.snapshot, state.cpuHistory, context, size)
                }
            }
        }
    }
}

@Composable
private fun MetricsView(snap: MetricsSnapshot, history: List<Float>, context: Context, size: DpSize) {
    val isLarge = size.height >= 200.dp
    val isWide  = size.width >= 240.dp

    Column(modifier = GlanceModifier.fillMaxSize()) {
        // ── Header ────────────────────────────────────────────
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(snap.serverName, style = TextStyle(color = ColorProvider(TextPrimary), fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                val statusText = if (WidgetStateHolder.state.isSyncing) "Syncing..." else updatedAgo(WidgetStateHolder.state.lastUpdated)
                Text(statusText, style = TextStyle(color = ColorProvider(if (WidgetStateHolder.state.isSyncing) BlueAccent else TextMuted), fontSize = 9.sp))
            }
            Image(provider = ImageProvider(android.R.drawable.ic_popup_sync), contentDescription = "Refresh",
                modifier = GlanceModifier.size(24.dp).clickable(actionRunCallback<RefreshAction>()))
        }

        Spacer(GlanceModifier.height(8.dp))

        // ── Main Row (Gauges) ─────────────────────────────────
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GaugeCell(context, snap.os.cpuPercent, "CPU", GlanceModifier.defaultWeight())
            GaugeCell(context, snap.os.ramPercent, "RAM", GlanceModifier.defaultWeight())
            GaugeCell(context, snap.os.diskPercent, "DISK", GlanceModifier.defaultWeight())
            if (isWide && snap.oracle != null) {
                GaugeCell(context, snap.oracle.sessionPercent, "DB", GlanceModifier.defaultWeight())
            }
        }

        if (isLarge) {
            Spacer(GlanceModifier.height(12.dp))
            
            // ── Live Sparkline Chart (New!) ───────────────────
            if (history.size > 1) {
                Box(modifier = GlanceModifier.fillMaxWidth().height(40.dp)) {
                    Image(provider = ImageProvider(makeSparklineBitmap(history, context).bitmap), 
                        contentDescription = "CPU History", modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                }
                Spacer(GlanceModifier.height(8.dp))
            }

            // ── Bird's Eye Metrics Grid (Dense & Proper) ──────
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                // Row 1: System
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    MetricBox("↑${snap.os.netSentMb.toInt()}M", "NET UP", GlanceModifier.defaultWeight())
                    MetricBox("↓${snap.os.netRecvMb.toInt()}M", "NET DN", GlanceModifier.defaultWeight())
                    MetricBox("${snap.os.loadAvg1m}", "LOAD", GlanceModifier.defaultWeight())
                }
                Spacer(GlanceModifier.height(4.dp))
                // Row 2: Capacity
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    MetricBox("${snap.os.ramUsedGb.toInt()}G", "RAM USED", GlanceModifier.defaultWeight())
                    MetricBox("${snap.os.diskUsedGb.toInt()}G", "DSK USED", GlanceModifier.defaultWeight())
                    MetricBox("${snap.os.cpuCoreCount}", "CORES", GlanceModifier.defaultWeight())
                }
                
                // Row 3: Oracle Advanced (New Metrics!)
                snap.oracle?.let { ora ->
                    Spacer(GlanceModifier.height(4.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        MetricBox("${ora.redoSwitchesPerHour}", "REDO/H", GlanceModifier.defaultWeight(), GreenAccent)
                        MetricBox("${ora.slowQueriesCount}", "SLOW Q", GlanceModifier.defaultWeight(), if(ora.slowQueriesCount > 0) AmberAccent else TextMuted)
                        MetricBox("${ora.tablespacePercent.toInt()}%", "TBSP", GlanceModifier.defaultWeight())
                    }
                }
            }
        }
        
        // Push everything up so it doesn't stick to footer
        Spacer(GlanceModifier.defaultWeight())
    }
}

@Composable
private fun GaugeCell(context: Context, pct: Float, label: String, modifier: GlanceModifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(provider = ImageProvider(makeGaugeBitmap(pct, context).bitmap), contentDescription = label,
            modifier = GlanceModifier.size(65.dp), contentScale = ContentScale.Fit)
        Text(label, style = TextStyle(color = ColorProvider(TextMuted), fontSize = 10.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun MetricBox(value: String, label: String, modifier: GlanceModifier, valColor: Color = TextPrimary) {
    Column(modifier = modifier.padding(2.dp).background(ColorProvider(CardDark)).cornerRadius(8.dp).padding(6.dp), 
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = TextStyle(color = ColorProvider(valColor), fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(label, style = TextStyle(color = ColorProvider(TextMuted), fontSize = 8.sp), maxLines = 1)
    }
}

@Composable
private fun LoadingView() {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Initializing...", style = TextStyle(color = ColorProvider(TextMuted), fontSize = 12.sp))
    }
}

@Composable
private fun ErrorView(msg: String) {
    Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        Text("Offline", style = TextStyle(color = ColorProvider(RedAccent), fontSize = 14.sp, fontWeight = FontWeight.Bold))
        Text(msg.take(30), style = TextStyle(color = ColorProvider(TextMuted), fontSize = 10.sp))
        Spacer(GlanceModifier.height(8.dp))
        Button("Retry", actionRunCallback<RefreshAction>())
    }
}

private fun updatedAgo(last: java.time.Instant?): String {
    val sec = java.time.Instant.now().epochSecond - (last?.epochSecond ?: 0)
    return if (last == null) "Never" else if (sec < 60) "${sec}s ago" else "${sec/60}m ago"
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
