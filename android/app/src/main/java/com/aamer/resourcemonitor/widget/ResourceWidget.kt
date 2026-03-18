package com.aamer.resourcemonitor.widget

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import kotlin.math.min

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

// ── Arc gauge bitmap factory ──────────────────────────────────────

fun makeGaugeBitmap(pct: Float, size: Int, context: Context): BitmapDrawable {
    val bmp    = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx     = size / 2f
    val cy     = size / 2f
    val r      = size / 2f - 6f
    val oval   = RectF(cx - r, cy - r, cx + r, cy + r)

    val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
        color = android.graphics.Color.parseColor("#2A3050")
    }
    canvas.drawArc(oval, -220f, 260f, false, track)

    val arcColor = percentColor(pct)
    val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
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
        textSize = size * 0.20f
        textAlign = Paint.Align.CENTER
        color = android.graphics.Color.parseColor("#D0DCFF")
        typeface = Typeface.DEFAULT_BOLD
    }
    val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2
    canvas.drawText("${pct.toInt()}%", cx, textY, textPaint)

    return BitmapDrawable(context.resources, bmp)
}

fun makeSparklineBitmap(points: List<Float>, w: Int, h: Int,
                         color: Color, context: Context): BitmapDrawable {
    val bmp    = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    if (points.size < 2) return BitmapDrawable(context.resources, bmp)

    val min = points.min()
    val max = points.max().let { if (it == min) min + 1f else it }

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style      = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap  = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color  = android.graphics.Color.argb(
            200,
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        )
    }

    val path = Path()
    points.forEachIndexed { i, v ->
        val x = i / (points.size - 1f) * w
        val y = h - ((v - min) / (max - min)) * h * 0.85f - h * 0.075f
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    canvas.drawPath(path, paint)

    return BitmapDrawable(context.resources, bmp)
}

// ── Main widget composable ────────────────────────────────────────

class ResourceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 110.dp),   // small
            DpSize(320.dp, 140.dp),   // medium
            DpSize(400.dp, 220.dp),   // large
        )
    )

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val state   = WidgetStateHolder.state
        val size    = LocalSize.current

        val isLarge  = size.width >= 380.dp
        val isMedium = size.width >= 300.dp

        GlanceTheme {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(BgDark))
                    .cornerRadius(20.dp)
                    .padding(12.dp)
            ) {
                when {
                    state.snapshot == null && state.error != null -> ErrorView(state.error)
                    state.snapshot == null                        -> LoadingView()
                    else -> MetricsView(
                        snapshot = state.snapshot,
                        context  = context,
                        isLarge  = isLarge,
                        isMedium = isMedium
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
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Text("Connection error", style = TextStyle(color = ColorProvider(Color(0xFFE24B4A)), fontSize = 13.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(4.dp))
        Text(message.take(60), style = TextStyle(color = ColorProvider(TextMuted), fontSize = 11.sp))
    }
}

@Composable
private fun MetricsView(
    snapshot: MetricsSnapshot,
    context: Context,
    isLarge: Boolean,
    isMedium: Boolean
) {
    val gaugeSize = if (isLarge) 72 else if (isMedium) 64 else 56
    val os        = snapshot.os
    val oracle    = snapshot.oracle

    Column(modifier = GlanceModifier.fillMaxSize()) {

        // ── Header ────────────────────────────────────────────
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                snapshot.serverName,
                style = TextStyle(color = ColorProvider(TextPrimary), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                updatedAgo(WidgetStateHolder.state.lastUpdated),
                style = TextStyle(color = ColorProvider(TextMuted), fontSize = 10.sp)
            )
            Spacer(GlanceModifier.width(8.dp))
            Image(
                provider = ImageProvider(android.R.drawable.ic_popup_sync),
                contentDescription = "Refresh",
                modifier = GlanceModifier
                    .size(16.dp)
                    .clickable(actionRunCallback<RefreshAction>())
            )
        }

        Spacer(GlanceModifier.height(8.dp))

        // ── Gauge row ─────────────────────────────────────────
        Row(
            modifier          = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GaugeCell(context, os.cpuPercent,   "CPU",  gaugeSize)
            Spacer(GlanceModifier.width(8.dp))
            GaugeCell(context, os.ramPercent,   "RAM",  gaugeSize)
            Spacer(GlanceModifier.width(8.dp))
            GaugeCell(context, os.diskPercent,  "DISK", gaugeSize)
            if (oracle != null) {
                Spacer(GlanceModifier.width(8.dp))
                GaugeCell(context, oracle.sessionPercent, "SESS", gaugeSize)
            }
        }

        // ── Large-only: sparklines + tablespace ───────────────
        if (isLarge && oracle != null) {
            Spacer(GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                OracleInfoCard("Tablespace", oracle.tablespacePercent, modifier = GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(6.dp))
                OracleInfoCard("Redo/hr", oracle.redoSwitchesPerHour.toFloat(), isCount = true, modifier = GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(6.dp))
                OracleInfoCard("Slow Q", oracle.slowQueriesCount.toFloat(), isCount = true, modifier = GlanceModifier.defaultWeight())
            }
        }

        // ── Alarm bar ─────────────────────────────────────────
        val alarmMetrics = buildList {
            if (os.cpuPercent  >= 85f) add("CPU ${os.cpuPercent.toInt()}%")
            if (os.ramPercent  >= 90f) add("RAM ${os.ramPercent.toInt()}%")
            if (os.diskPercent >= 80f) add("DISK ${os.diskPercent.toInt()}%")
            oracle?.let {
                if (it.tablespacePercent >= 85f) add("TBSP ${it.tablespacePercent.toInt()}%")
            }
        }

        if (alarmMetrics.isNotEmpty()) {
            Spacer(GlanceModifier.height(6.dp))
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
                    style = TextStyle(color = ColorProvider(Color(0xFFE4876E)), fontSize = 10.sp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun GaugeCell(context: Context, pct: Float, label: String, size: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val drawable = makeGaugeBitmap(pct, size, context)
        Image(
            provider = ImageProvider(drawable.bitmap),
            contentDescription = "$label $pct%",
            modifier = GlanceModifier.size(size.dp)
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            label,
            style = TextStyle(color = ColorProvider(TextMuted), fontSize = 9.sp,
                fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun OracleInfoCard(
    label: String,
    value: Float,
    isCount: Boolean = false,
    modifier: GlanceModifier = GlanceModifier
) {
    val display = if (isCount) value.toInt().toString() else "${value.toInt()}%"
    val color   = if (!isCount) percentColor(value) else Color(0xFF9DB4D8)

    Column(
        modifier = modifier
            .background(ColorProvider(CardDark))
            .cornerRadius(8.dp)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(display, style = TextStyle(color = ColorProvider(color), fontSize = 14.sp, fontWeight = FontWeight.Bold))
        Text(label,   style = TextStyle(color = ColorProvider(TextMuted), fontSize = 9.sp))
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
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        MetricsFetchWorker.cancel(context)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        MetricsFetchWorker.fetchNow(context)
    }
}
