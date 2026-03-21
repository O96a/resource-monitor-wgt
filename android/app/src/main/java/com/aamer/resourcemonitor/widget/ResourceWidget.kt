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
import java.time.Instant

// ── Design Tokens ────────────────────────────────────────────────

private val BgDark      = Color(0xFF0A0D18)
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

// ── Drawing Logic ────────────────────────────────────────────────

fun makeGaugeBitmap(pct: Float, context: Context): BitmapDrawable {
    val size = 300
    val bmp    = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx     = size / 2f
    val cy     = size / 2f
    
    val stroke = size * 0.08f 
    val padding = stroke * 2.5f
    val radius = size / 2f - padding
    val rect   = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

    val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = stroke; color = 0xFF2A3050.toInt()
    }
    canvas.drawArc(rect, -220f, 260f, false, trackPaint)

    val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
        val c = percentColor(pct)
        color = android.graphics.Color.argb(255, (c.red*255).toInt(), (c.green*255).toInt(), (c.blue*255).toInt())
    }
    canvas.drawArc(rect, -220f, (pct / 100f) * 260f, false, arcPaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size * 0.16f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        color = 0xFFD0DCFF.toInt()
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
        color = 0xFF4A9EFF.toInt()
    }
    canvas.drawPath(path, paint)
    
    return BitmapDrawable(context.resources, bmp)
}

// ── Widget Component ──────────────────────────────────────────────

class ResourceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact // Switch to Exact for better precision during resize

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
                    .background(ColorProvider(BgDark)).cornerRadius(12.dp).padding(8.dp)
            ) {
                when {
                    state.error != null -> ErrorView(state.error!!)
                    state.snapshot == null -> LoadingView()
                    else -> MetricsDashboard(state.snapshot!!, state.cpuHistory, context, size)
                }
            }
        }
    }
}

@Composable
private fun MetricsDashboard(snap: MetricsSnapshot, history: List<Float>, context: Context, size: DpSize) {
    val showMetrics = size.height >= 130.dp
    val showOracle  = size.width >= 250.dp && snap.oracle != null
    val showChart   = size.height >= 220.dp

    Column(modifier = GlanceModifier.fillMaxSize()) {
        // ── Header (Title + Refresh) ──
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(snap.serverName, style = TextStyle(color = ColorProvider(TextPrimary), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                val statusText = if (WidgetStateHolder.state.isSyncing) "Syncing..." else updatedAgo(WidgetStateHolder.state.lastUpdated)
                Text(statusText, style = TextStyle(color = ColorProvider(if (WidgetStateHolder.state.isSyncing) BlueAccent else TextMuted), fontSize = 8.sp))
            }
            Box(modifier = GlanceModifier.padding(4.dp).clickable(actionRunCallback<RefreshAction>())) {
                Image(provider = ImageProvider(android.R.drawable.ic_popup_sync), contentDescription = "Refresh", modifier = GlanceModifier.size(20.dp))
            }
        }

        Spacer(GlanceModifier.height(4.dp))

        // ── Primary Gauges (Dynamically Scaling) ──
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            // We use defaultWeight() on cells to ensure they grow, and fillMaxHeight() on Images
            GaugeCell(context, snap.os.cpuPercent, "CPU", GlanceModifier.defaultWeight())
            GaugeCell(context, snap.os.ramPercent, "RAM", GlanceModifier.defaultWeight())
            GaugeCell(context, snap.os.diskPercent, "DISK", GlanceModifier.defaultWeight())
            if (showOracle) {
                GaugeCell(context, snap.oracle!!.sessionPercent, "DB", GlanceModifier.defaultWeight())
            }
        }

        if (showMetrics) {
            Spacer(GlanceModifier.height(8.dp))
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    MetricCard("↑${snap.os.netSentMb.toInt()}M", "UP", GlanceModifier.defaultWeight())
                    MetricCard("↓${snap.os.netRecvMb.toInt()}M", "DN", GlanceModifier.defaultWeight())
                    MetricCard("${snap.os.loadAvg1m}", "LOAD", GlanceModifier.defaultWeight())
                }
                Spacer(GlanceModifier.height(4.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    MetricCard("${snap.os.ramUsedGb.toInt()}G", "RAM", GlanceModifier.defaultWeight())
                    MetricCard("${snap.os.diskUsedGb.toInt()}G", "DISK", GlanceModifier.defaultWeight())
                    MetricCard("${snap.os.cpuCoreCount}", "CORES", GlanceModifier.defaultWeight())
                }
                
                if (snap.oracle != null) {
                    val ora = snap.oracle!!
                    Spacer(GlanceModifier.height(4.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        MetricCard("${ora.redoSwitchesPerHour}", "REDO", GlanceModifier.defaultWeight(), GreenAccent)
                        MetricCard("${ora.slowQueriesCount}", "SLOW", GlanceModifier.defaultWeight(), if(ora.slowQueriesCount > 0) AmberAccent else TextMuted)
                        MetricCard("${ora.tablespacePercent.toInt()}%", "TBSP", GlanceModifier.defaultWeight())
                    }
                }
            }
        }

        if (showChart && history.size > 1) {
            Spacer(GlanceModifier.height(10.dp))
            // ── Boxed Live Chart ──
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(45.dp)
                    .background(ColorProvider(CardDark))
                    .cornerRadius(8.dp)
                    .padding(4.dp)
            ) {
                Image(
                    provider = ImageProvider(makeSparklineBitmap(history, context).bitmap), 
                    contentDescription = "CPU Chart", 
                    modifier = GlanceModifier.fillMaxSize(), 
                    contentScale = ContentScale.FillBounds
                )
            }
        }
        
        Spacer(GlanceModifier.height(4.dp))
        Text("v1.1.1", style = TextStyle(color = ColorProvider(TextMuted.copy(alpha = 0.4f)), fontSize = 7.sp))
    }
}

@Composable
private fun GaugeCell(context: Context, pct: Float, label: String, modifier: GlanceModifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Remove fixed size, use fillMaxWidth and height weight
        Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
            Image(
                provider = ImageProvider(makeGaugeBitmap(pct, context).bitmap), 
                contentDescription = label,
                modifier = GlanceModifier.fillMaxSize(), // Scale to fill the weight-based container
                contentScale = ContentScale.Fit
            )
        }
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

private fun updatedAgo(last: Instant?): String {
    val sec = Instant.now().epochSecond - (last?.epochSecond ?: 0)
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
