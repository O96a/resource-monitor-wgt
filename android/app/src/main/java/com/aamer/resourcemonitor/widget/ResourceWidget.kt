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
            DpSize(150.dp, 80.dp),    // Tiny
            DpSize(220.dp, 110.dp),   // Small
            DpSize(300.dp, 180.dp),   // Medium
            DpSize(400.dp, 260.dp),   // Large
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

        val isTiny    = size.width < 220.dp || size.height < 110.dp
        val isSmall   = !isTiny && (size.width < 300.dp || size.height < 180.dp)
        val isMedium  = !isTiny && !isSmall && (size.width < 400.dp || size.height < 260.dp)
        val isLarge   = !isTiny && !isSmall && !isMedium

        GlanceTheme {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(BgDark))
                    .cornerRadius(16.dp)
                    .padding(12.dp)
            ) {
                when {
                    state.snapshot == null && state.error != null -> ErrorView(state.error)
                    state.snapshot == null                        -> LoadingView()
                    else -> MetricsView(
                        snapshot = state.snapshot,
                        context  = context,
                        isTiny   = isTiny,
                        isSmall  = isSmall,
                        isMedium = isMedium,
                        isLarge  = isLarge
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
    isTiny: Boolean,
    isSmall: Boolean,
    isMedium: Boolean,
    isLarge: Boolean
) {
    val os        = snapshot.os
    val oracle    = snapshot.oracle

    Column(modifier = GlanceModifier.fillMaxSize()) {

        // Header
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                snapshot.serverName,
                style = TextStyle(color = ColorProvider(TextPrimary), 
                    fontSize = if (isTiny) 11.sp else 13.sp, 
                    fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight()
            )
            if (WidgetStateHolder.state.isSyncing) {
                Text(
                    "Syncing...",
                    style = TextStyle(color = ColorProvider(BlueAccent), fontSize = 10.sp)
                )
            } else if (!isTiny) {
                Text(
                    updatedAgo(WidgetStateHolder.state.lastUpdated),
                    style = TextStyle(color = ColorProvider(TextMuted), fontSize = 10.sp)
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            Image(
                provider = ImageProvider(android.R.drawable.ic_popup_sync),
                contentDescription = "Refresh",
                modifier = GlanceModifier
                    .size(if (isTiny) 14.dp else 18.dp)
                    .clickable(actionRunCallback<RefreshAction>())
            )
        }

        if (!isTiny) Spacer(GlanceModifier.height(12.dp))

        // Flexible Gauges Row using weight
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GaugeCell(context, os.cpuPercent,   "CPU",  GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(if (isTiny) 4.dp else 12.dp))
            GaugeCell(context, os.ramPercent,   "RAM",  GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(if (isTiny) 4.dp else 12.dp))
            GaugeCell(context, os.diskPercent,  "DISK", GlanceModifier.defaultWeight())
            if (oracle != null) {
                Spacer(GlanceModifier.width(if (isTiny) 4.dp else 12.dp))
                GaugeCell(context, oracle.sessionPercent, "SESS", GlanceModifier.defaultWeight())
            }
        }

        // Details rows based on size
        if (isMedium || isLarge) {
            Spacer(GlanceModifier.height(10.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                StatCard("Load", "${os.loadAvg1m}", modifier = GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(6.dp))
                if (oracle != null) {
                    StatCard("TBSP", "${oracle.tablespacePercent.toInt()}%", modifier = GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(6.dp))
                    StatCard("DB", oracle.dbStatus, modifier = GlanceModifier.defaultWeight())
                } else {
                    StatCard("Net ↑", "${os.netSentMb.toInt()}MB", modifier = GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(6.dp))
                    StatCard("Net ↓", "${os.netRecvMb.toInt()}MB", modifier = GlanceModifier.defaultWeight())
                }
            }
        }
        
        if (isLarge) {
            Spacer(GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                StatCard("RAM", "${os.ramUsedGb}/${os.ramTotalGb}G", modifier = GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(6.dp))
                StatCard("Disk", "${os.diskUsedGb}/${os.diskTotalGb}G", modifier = GlanceModifier.defaultWeight())
                if (oracle != null) {
                    Spacer(GlanceModifier.width(6.dp))
                    StatCard("Sessions", "${oracle.activeSessions}/${oracle.maxSessions}", modifier = GlanceModifier.defaultWeight())
                }
            }
        }

        // Alarms Bar
        val alarmMetrics = buildList {
            if (os.cpuPercent  >= 85f) add("CPU ${os.cpuPercent.toInt()}%")
            if (os.ramPercent  >= 90f) add("RAM ${os.ramPercent.toInt()}%")
            if (os.diskPercent >= 80f) add("DISK ${os.diskPercent.toInt()}%")
            oracle?.let {
                if (it.tablespacePercent >= 85f) add("TBSP ${it.tablespacePercent.toInt()}%")
            }
        }

        if (alarmMetrics.isNotEmpty() && !isTiny) {
            Spacer(GlanceModifier.height(8.dp))
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ColorProvider(Color(0x33E24B4A)))
                    .cornerRadius(8.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "⚠ ${alarmMetrics.joinToString("  ·  ")}",
                    style = TextStyle(color = ColorProvider(Color(0xFFE4876E)), fontSize = 11.sp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: GlanceModifier = GlanceModifier) {
    Column(
        modifier = modifier
            .background(ColorProvider(CardDark))
            .cornerRadius(8.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = TextStyle(color = ColorProvider(TextPrimary), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(label, style = TextStyle(color = ColorProvider(TextMuted), fontSize = 10.sp), maxLines = 1)
    }
}

@Composable
private fun GaugeCell(context: Context, pct: Float, label: String, modifier: GlanceModifier = GlanceModifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val drawable = makeGaugeBitmap(pct, context)
        Box(modifier = GlanceModifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) {
            Image(
                provider = ImageProvider(drawable.bitmap),
                contentDescription = "$label $pct%",
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        Text(
            label,
            style = TextStyle(color = ColorProvider(TextMuted), fontSize = 10.sp,
                fontWeight = FontWeight.Bold)
        )
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
        MetricsFetchWorker.fetchNow(context)
    }
}