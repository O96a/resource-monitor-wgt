package com.aamer.resourcemonitor.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aamer.resourcemonitor.data.models.*
import com.aamer.resourcemonitor.data.repository.ServerConfig
import com.aamer.resourcemonitor.data.repository.SettingsRepository
import com.aamer.resourcemonitor.widget.WidgetStateHolder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

// ── Design Tokens ──

private val BgColor       = Color(0xFF0A0D18)
private val SurfaceColor  = Color(0xFF161B2C)
private val CardColor     = Color(0xFF161B2C)
private val TextPrimary   = Color(0xFFE0E8FF)
private val TextMuted     = Color(0xFF6B7FA5)
private val BlueAccent    = Color(0xFF5AB0FF)
private val GreenAccent   = Color(0xFF4ADE80)
private val AmberAccent   = Color(0xFFFBBF24)
private val RedAccent     = Color(0xFFF87171)
private val PurpleAccent  = Color(0xFFA78BFA)

fun pctColor(pct: Float) = when {
    pct >= 90 -> RedAccent
    pct >= 75 -> AmberAccent
    else       -> BlueAccent
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        try { installSplashScreen() } catch (_: Exception) {}
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this)[DashboardViewModel::class.java]
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = BgColor,
                    surface = SurfaceColor,
                    primary = BlueAccent,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary,
                )
            ) {
                ResourceMonitorApp(vm)
            }
        }
    }
}

@Composable
fun ResourceMonitorApp(vm: DashboardViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        containerColor = BgColor,
        bottomBar = {
            NavigationBar(containerColor = SurfaceColor, tonalElevation = 0.dp) {
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Dash") })
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { BadgedBox(badge = { if (state.alarms.isNotEmpty()) Badge { Text("${state.alarms.size}") } }) { Icon(Icons.Default.Notifications, null) } }, label = { Text("Alarms") })
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Setup") })
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> DashboardScreen(state, onRefresh = vm::refresh)
                1 -> AlarmsScreen(state.alarms, onAck = vm::acknowledgeAlarm)
                2 -> SettingsScreen(vm)
            }
        }
    }
}

@Composable
fun DashboardScreen(state: DashboardUiState, onRefresh: () -> Unit) {
    if (!state.isConfigured) { SetupPrompt(); return }

    LazyColumn(modifier = Modifier.fillMaxSize().background(BgColor), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(state.snapshot?.serverName ?: "Server", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text("Live polling active (3s)", color = TextMuted, fontSize = 12.sp)
                }
                IconButton(onClick = onRefresh) {
                    if (state.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = BlueAccent)
                    else Icon(Icons.Default.Refresh, null, tint = TextMuted)
                }
            }
        }

        state.snapshot?.let { snap ->
            item {
                SectionLabel("REAL-TIME TREND")
                Spacer(Modifier.height(8.dp))
                LiveSparklineCard(history = WidgetStateHolder.state.cpuHistory)
            }

            item {
                SectionLabel("SYSTEM RESOURCES")
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GaugeCard("CPU", snap.os.cpuPercent, Modifier.weight(1f))
                    GaugeCard("RAM", snap.os.ramPercent, Modifier.weight(1f))
                    GaugeCard("DISK", snap.os.diskPercent, Modifier.weight(1f))
                }
            }

            item {
                SectionLabel("NETWORK & LOAD")
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Sent", "↑${snap.os.netSentMb.toInt()} MB", Modifier.weight(1f))
                    StatCard("Recv", "↓${snap.os.netRecvMb.toInt()} MB", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Load 1m", "${snap.os.loadAvg1m}", Modifier.weight(1f))
                    StatCard("Load 5m", "${snap.os.loadAvg5m}", Modifier.weight(1f))
                    StatCard("Load 15m", "${snap.os.loadAvg15m}", Modifier.weight(1f))
                }
            }

            item {
                SectionLabel("CAPACITY")
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Cores", "${snap.os.cpuCoreCount}", Modifier.weight(1f))
                    StatCard("RAM Total", "${snap.os.ramTotalGb.toInt()} GB", Modifier.weight(1f))
                }
            }

            snap.oracle?.let { ora ->
                item {
                    SectionLabel("ORACLE DATABASE")
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GaugeCard("Sessions", ora.sessionPercent, Modifier.weight(1f))
                        GaugeCard("Tablespace", ora.tablespacePercent, Modifier.weight(1f))
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("Active", "${ora.activeSessions}", Modifier.weight(1f))
                        StatCard("Redo Log/h", "${ora.redoSwitchesPerHour}", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("Slow Qs", "${ora.slowQueriesCount}", Modifier.weight(1f))
                        StatCard("Status", ora.dbStatus, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    StatCard("DB Version", ora.dbVersion, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

@Composable
fun GaugeCard(label: String, pct: Float, modifier: Modifier = Modifier) {
    val color = pctColor(pct)
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = CardColor), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ArcGauge(pct, color, size = 80.dp)
            Spacer(Modifier.height(6.dp))
            Text(label, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ArcGauge(pct: Float, color: Color, size: Dp) {
    val animPct by animateFloatAsState(targetValue = pct, animationSpec = tween(800, easing = FastOutSlowInEasing), label = "g")
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx(); val inset = stroke / 2
            val oval = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawArc(color = Color(0xFF2A3050), -220f, 260f, false, topLeft = Offset(inset, inset), size = oval, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color = color, -220f, (animPct / 100f) * 260f, false, topLeft = Offset(inset, inset), size = oval, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("${pct.toInt()}%", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun LiveSparklineCard(history: List<Float>) {
    Card(colors = CardDefaults.cardColors(containerColor = CardColor), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${history.lastOrNull()?.toInt() ?: 0}%", color = BlueAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("CPU Trend", color = TextMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(60.dp)) {
                if (history.size < 2) return@Canvas
                val max = history.max().let { if (it < 10f) 100f else it * 1.1f }; val min = history.min(); val range = if (max == min) 1f else max - min
                val path = Path()
                history.forEachIndexed { i, v ->
                    val x = i / (history.size - 1f) * size.width
                    val y = size.height - ((v - min) / range) * size.height * 0.8f - size.height * 0.1f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, BlueAccent, style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = CardColor), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, color = TextMuted, fontSize = 10.sp)
            Spacer(Modifier.height(2.dp))
            Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun AlarmsScreen(alarms: List<Alarm>, onAck: (String) -> Unit) {
    if (alarms.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircle, null, tint = GreenAccent, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("All clear", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("No active alarms", color = TextMuted, fontSize = 13.sp)
            }
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().background(BgColor), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Active Alarms", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        items(alarms, key = { it.id }) { alarm -> AlarmCard(alarm, onAck) }
    }
}

@Composable
fun AlarmCard(alarm: Alarm, onAck: (String) -> Unit) {
    val borderColor = if (alarm.severity == "critical") RedAccent else AmberAccent
    Card(colors = CardDefaults.cardColors(containerColor = CardColor), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, borderColor.copy(alpha = 0.5f))) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(borderColor.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Warning, null, tint = borderColor, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(alarm.message, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("${alarm.value}% / threshold ${alarm.threshold}%", color = TextMuted, fontSize = 11.sp)
            }
            TextButton(onClick = { onAck(alarm.id) }) { Text("Dismiss", color = TextMuted, fontSize = 12.sp) }
        }
    }
}

@Composable
fun SettingsScreen(vm: DashboardViewModel) {
    val config by vm.configFlow.collectAsStateWithLifecycle(initialValue = ServerConfig())
    var baseUrl by remember(config.baseUrl) { mutableStateOf(config.baseUrl) }
    var apiKey by remember(config.apiKey) { mutableStateOf(config.apiKey) }
    var serverName by remember(config.serverName) { mutableStateOf(config.serverName) }
    var saved by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().background(BgColor).padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("Settings", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Configure connection", color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
        SettingsField("Server URL", "http://ip:8090", baseUrl) { baseUrl = it; saved = false }
        Spacer(Modifier.height(12.dp))
        SettingsField("API Key", "your-key", apiKey, true) { apiKey = it; saved = false }
        Spacer(Modifier.height(12.dp))
        SettingsField("Server Name", "Oracle Node", serverName) { serverName = it; saved = false }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { vm.saveConfig(baseUrl, apiKey, serverName); saved = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)) { Text(if (saved) "✓ Saved" else "Save Config") }
    }
}

@Composable
fun SettingsField(label: String, placeholder: String, value: String, isPassword: Boolean = false, onValueChange: (String) -> Unit) {
    Column {
        Text(label, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = value, onValueChange = onValueChange, placeholder = { Text(placeholder, color = Color(0xFF3A4060), fontSize = 13.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BlueAccent, unfocusedBorderColor = Color(0xFF2A3050), focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = BlueAccent), shape = RoundedCornerShape(10.dp), visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)
    }
}

@Composable
fun SetupPrompt() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.Settings, null, tint = BlueAccent, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(16.dp))
            Text("Setup required", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Enter server URL and API key in Settings.", color = TextMuted, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
