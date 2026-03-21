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
fun makeGaugeBitmap(pct: Float, context: Context): BitmapDrawable {
    val size = 400
    val bmp    = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx     = size / 2f
    val cy     = size / 2f
    val strokeWidth = size * 0.14f
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
        color = android.graphics.Color.argb(255, (arcColor.red * 255).toInt(), (arcColor.green * 255).toInt(), (arcColor.blue * 255).toInt())
    }
    canvas.drawArc(oval, -220f, (pct / 100f) * 260f, false, arc)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size * 0.28f
        textAlign = Paint.Align.CENTER
        color = android.graphics.Color.parseColor("#D0DCFF")
        typeface = Typeface.DEFAULT_BOLD
    }
    canvas.drawText("${pct.toInt()}%", cx, cy - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)

    return BitmapDrawable(context.resources, bmp)
}

// ── Main widget ───────────────────────────────────────────────────

class ResourceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(100.dp, 50.dp),
            DpSize(180.dp, 100.dp),
            DpSize(250.dp, 100.dp),
            DpSize(300.dp, 180.dp),
            DpSize(400.dp, 250.dp),
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
                    .background(ColorProvider(BgDark)).cornerRadius(12.dp).padding(4.dp)
            ) {
                when {
                    state.error != null -> ErrorView(state.error)
                    state.snapshot == null -> LoadingView()
                    else -> MetricsView(state.snapshot, context, size)
                }
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Loading...", style = TextStyle(color = ColorProvider(TextMuted), fontSize = 12.sp))
    }
}

@Composable
private fun ErrorView(msg: String) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        Text("Offline", style = TextStyle(color = ColorProvider(Color(0xFFE24B4A)), fontSize = 12.sp, fontWeight = FontWeight.Bold))
        Text(msg.take(40), style = TextStyle(color = ColorProvider(TextMuted), fontSize = 10.sp), maxLines = 1)
        Button("Retry", actionRunCallback<RefreshAction>())
    }
}

@Composable
private fun MetricsView(snapshot: MetricsSnapshot, context: Context, size: DpSize) {
    val os = snapshot.os
    val oracle = snapshot.oracle
    val isTall = size.height >= 150.dp
    val isWide = size.width >= 240.dp

    Column(modifier = GlanceModifier.fillMaxSize()) {
        // Header
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(snapshot.serverName, style = TextStyle(color = ColorProvider(TextPrimary), fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(if (WidgetStateHolder.state.isSyncing) "Syncing..." else updatedAgo(WidgetStateHolder.state.lastUpdated), 
                    style = TextStyle(color = ColorProvider(if (WidgetStateHolder.state.isSyncing) BlueAccent else TextMuted), fontSize = 8.sp))
            }
            Image(provider = ImageProvider(android.R.drawable.ic_popup_sync), contentDescription = "Refresh",
                modifier = GlanceModifier.size(24.dp).clickable(actionRunCallback<RefreshAction>()))
        }

        Spacer(GlanceModifier.height(2.dp))

        // Gauges
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            val gHeight = if (size.height < 100.dp) 45.dp else 90.dp
            GaugeCell(context, os.cpuPercent, "CPU", gHeight, GlanceModifier.defaultWeight())
            GaugeCell(context, os.ramPercent, "RAM", gHeight, GlanceModifier.defaultWeight())
            GaugeCell(context, os.diskPercent, "DISK", gHeight, GlanceModifier.defaultWeight())
            if (oracle != null && isWide) {
                GaugeCell(context, oracle.sessionPercent, "DB", gHeight, GlanceModifier.defaultWeight())
            }
        }

        // Metrics Grid
        if (size.height > 90.dp) {
            Spacer(GlanceModifier.height(4.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                StatCard("↑${os.netSentMb.toInt()}M", "NET", GlanceModifier.defaultWeight())
                StatCard("↓${os.netRecvMb.toInt()}M", "NET", GlanceModifier.defaultWeight())
                StatCard("${os.loadAvg1m}", "LOAD", GlanceModifier.defaultWeight())
            }
            
            if (isTall) {
                Spacer(GlanceModifier.height(2.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    StatCard("${os.ramUsedGb.toInt()}G", "USED", GlanceModifier.defaultWeight())
                    StatCard("${os.ramTotalGb.toInt()}G", "TOTAL", GlanceModifier.defaultWeight())
                    StatCard("${os.cpuCoreCount}", "CORE", GlanceModifier.defaultWeight())
                }
                if (oracle != null) {
                    Spacer(GlanceModifier.height(2.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        StatCard("${oracle.tablespacePercent.toInt()}%", "TBSP", GlanceModifier.defaultWeight())
                        StatCard(oracle.dbStatus, "DB", GlanceModifier.defaultWeight())
                        StatCard("${oracle.activeSessions}", "SESS", GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun GaugeCell(context: Context, pct: Float, label: String, height: androidx.compose.ui.unit.Dp, modifier: GlanceModifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(provider = ImageProvider(makeGaugeBitmap(pct, context).bitmap), contentDescription = label,
            modifier = GlanceModifier.fillMaxWidth().height(height), contentScale = ContentScale.Fit)
        Text(label, style = TextStyle(color = ColorProvider(TextMuted), fontSize = 8.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: GlanceModifier) {
    Column(modifier = modifier.padding(1.dp).background(ColorProvider(CardDark)).cornerRadius(4.dp).padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = TextStyle(color = ColorProvider(TextPrimary), fontSize = 9.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(label, style = TextStyle(color = ColorProvider(TextMuted), fontSize = 7.sp), maxLines = 1)
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
