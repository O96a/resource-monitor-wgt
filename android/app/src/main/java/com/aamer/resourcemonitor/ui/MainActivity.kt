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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

// ── Theme ─────────────────────────────────────────────────────────

private val BgColor       = Color(0xFF0F1220)
private val SurfaceColor  = Color(0xFF1A1F2E)
private val CardColor     = Color(0xFF222840)
private val PrimaryText   = Color(0xFFD0DCFF)
private val MutedText     = Color(0xFF5A7090)
private val BlueAccent    = Color(0xFF4A9EFF)
private val GreenAccent   = Color(0xFF34D399)
private val AmberAccent   = Color(0xFFEF9F27)
private val RedAccent     = Color(0xFFE24B4A)
private val PurpleAccent  = Color(0xFFA78BFA)

fun pctColor(pct: Float) = when {
    pct >= 90 -> RedAccent
    pct >= 75 -> AmberAccent
    else       -> BlueAccent
}

// ── Activity ──────────────────────────────────────────────────────

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        try { installSplashScreen() } catch (_: Exception) { /* Samsung One UI workaround */ }
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this)[DashboardViewModel::class.java]
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = BgColor,
                    surface = SurfaceColor,
                    primary = BlueAccent,
                    onBackground = PrimaryText,
                    onSurface = PrimaryText,
                )
            ) {
                ResourceMonitorApp(vm)
            }
        }
    }
}

// ── Navigation shell ──────────────────────────────────────────────

@Composable
fun ResourceMonitorApp(vm: DashboardViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        containerColor = BgColor,
        bottomBar = {
            NavigationBar(containerColor = SurfaceColor, tonalElevation = 0.dp) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 },
                    icon     = { Icon(Icons.Default.Home, null) },
                    label    = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 },
                    icon     = {
                        BadgedBox(badge = {
                            if (state.alarms.isNotEmpty()) Badge { Text("${state.alarms.size}") }
                        }) { Icon(Icons.Default.Notifications, null) }
                    },
                    label    = { Text("Alarms") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick  = { selectedTab = 2 },
                    icon     = { Icon(Icons.Default.Settings, null) },
                    label    = { Text("Settings") }
                )
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

// ── Dashboard screen ──────────────────────────────────────────────

@Composable
fun DashboardScreen(state: DashboardUiState, onRefresh: () -> Unit) {
    if (!state.isConfigured) {
        SetupPrompt()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BgColor),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        state.snapshot?.serverName ?: "Resource Monitor",
                        color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 20.sp
                    )
                    Text("Oracle Server Dashboard", color = MutedText, fontSize = 13.sp)
                }
                IconButton(onClick = onRefresh) {
                    if (state.isLoading)
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = BlueAccent)
                    else
                        Icon(Icons.Default.Refresh, null, tint = MutedText)
                }
            }
        }

        state.error?.let { err ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x33E24B4A)),
                    shape  = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = RedAccent)
                        Spacer(Modifier.width(8.dp))
                        Text(err, color = Color(0xFFE4876E), fontSize = 13.sp)
                    }
                }
            }
        }

        state.snapshot?.let { snap ->
            // OS Gauge grid
            item {
                SectionLabel("System Resources")
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GaugeCard("CPU",  snap.os.cpuPercent,  Modifier.weight(1f))
                    GaugeCard("RAM",  snap.os.ramPercent,  Modifier.weight(1f))
                    GaugeCard("DISK", snap.os.diskPercent, Modifier.weight(1f))
                }
            }

            // OS detail row
            item {
                SectionLabel("System Details")
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Cores",     "${snap.os.cpuCoreCount}", Modifier.weight(1f))
                    StatCard("RAM Used",  "${snap.os.ramUsedGb} / ${snap.os.ramTotalGb} GB", Modifier.weight(2f))
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Disk Used", "${snap.os.diskUsedGb} / ${snap.os.diskTotalGb} GB", Modifier.weight(1f))
                    StatCard("Network",   "↑ ${snap.os.netSentMb.toInt()} MB  ↓ ${snap.os.netRecvMb.toInt()} MB", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Load 1m",  "${snap.os.loadAvg1m}", Modifier.weight(1f))
                    StatCard("Load 5m",  "${snap.os.loadAvg5m}", Modifier.weight(1f))
                    StatCard("Load 15m", "${snap.os.loadAvg15m}", Modifier.weight(1f))
                }
            }

            // Oracle section
            snap.oracle?.let { oracle ->
                item {
                    Spacer(Modifier.height(16.dp))
                    SectionLabel("Oracle Database")
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GaugeCard("Sessions",   oracle.sessionPercent,   Modifier.weight(1f))
                        GaugeCard("Tablespace", oracle.tablespacePercent, Modifier.weight(1f))
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("Active Sessions", "${oracle.activeSessions} / ${oracle.maxSessions}", Modifier.weight(1f))
                        StatCard("Redo / hr",       "${oracle.redoSwitchesPerHour}", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("Slow Queries",    "${oracle.slowQueriesCount}",    Modifier.weight(1f))
                        StatCard("DB Status",       oracle.dbStatus,                 Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    StatCard("Version",    oracle.dbVersion, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp)
}

@Composable
fun GaugeCard(label: String, pct: Float, modifier: Modifier = Modifier) {
    val color = pctColor(pct)
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = CardColor),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ArcGauge(pct, color, size = 80.dp)
            Spacer(Modifier.height(6.dp))
            Text(label, color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ArcGauge(pct: Float, color: Color, size: Dp) {
    val animPct by animateFloatAsState(
        targetValue  = pct,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label        = "gauge"
    )
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset  = stroke / 2
            val oval   = Size(this.size.width - inset * 2, this.size.height - inset * 2)

            // Track arc
            drawArc(
                color      = Color(0xFF2A3050),
                startAngle = -220f, sweepAngle = 260f,
                useCenter  = false,
                topLeft    = Offset(inset, inset), size = oval,
                style      = Stroke(stroke, cap = StrokeCap.Round)
            )
            // Filled arc
            drawArc(
                color      = color,
                startAngle = -220f, sweepAngle = (animPct / 100f) * 260f,
                useCenter  = false,
                topLeft    = Offset(inset, inset), size = oval,
                style      = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            "${pct.toInt()}%",
            color      = PrimaryText,
            fontSize   = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = CardColor),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, color = MutedText, fontSize = 10.sp)
            Spacer(Modifier.height(2.dp))
            Text(value, color = PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ── Alarms screen ─────────────────────────────────────────────────

@Composable
fun AlarmsScreen(alarms: List<Alarm>, onAck: (String) -> Unit) {
    if (alarms.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircle, null, tint = GreenAccent, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("All clear", color = PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("No active alarms", color = MutedText, fontSize = 13.sp)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BgColor),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("Active Alarms", color = PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        items(alarms, key = { it.id }) { alarm ->
            AlarmCard(alarm, onAck)
        }
    }
}

@Composable
fun AlarmCard(alarm: Alarm, onAck: (String) -> Unit) {
    val borderColor = if (alarm.severity == "critical") RedAccent else AmberAccent
    Card(
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape  = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(borderColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Warning, null, tint = borderColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(alarm.message, color = PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("${alarm.value}% / threshold ${alarm.threshold}%",
                    color = MutedText, fontSize = 11.sp)
            }
            TextButton(onClick = { onAck(alarm.id) }) {
                Text("Dismiss", color = MutedText, fontSize = 12.sp)
            }
        }
    }
}

// ── Settings screen ───────────────────────────────────────────────

@Composable
fun SettingsScreen(vm: DashboardViewModel) {
    val config by vm.configFlow.collectAsStateWithLifecycle(
        initialValue = com.aamer.resourcemonitor.data.repository.ServerConfig()
    )

    var baseUrl    by remember(config.baseUrl)    { mutableStateOf(config.baseUrl) }
    var apiKey     by remember(config.apiKey)     { mutableStateOf(config.apiKey) }
    var serverName by remember(config.serverName) { mutableStateOf(config.serverName) }
    var saved      by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Settings", color = PrimaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Configure your Oracle server connection", color = MutedText, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))

        SettingsField("Server URL", "http://192.168.1.100:8080", baseUrl)    { baseUrl    = it; saved = false }
        Spacer(Modifier.height(12.dp))
        SettingsField("API Key",    "your-api-key",               apiKey,  isPassword = true) { apiKey = it; saved = false }
        Spacer(Modifier.height(12.dp))
        SettingsField("Server Name","My Oracle Server",           serverName){ serverName = it; saved = false }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                vm.saveConfig(baseUrl, apiKey, serverName)
                saved = true
            },
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(containerColor = BlueAccent)
        ) {
            Text(if (saved) "✓ Saved" else "Save Configuration")
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = Color(0xFF2A3050))
        Spacer(Modifier.height(16.dp))
        Text("Quick start", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "1. Copy server/ directory to your Oracle server\n" +
            "2. cp .env.example .env and fill in Oracle credentials\n" +
            "3. pip install -r requirements.txt\n" +
            "4. python main.py\n" +
            "5. Enter the server URL and API key above\n" +
            "6. Long-press homescreen → Widgets → Resource Monitor",
            color = MutedText, fontSize = 12.sp, lineHeight = 20.sp
        )
    }
}

@Composable
fun SettingsField(
    label: String, placeholder: String, value: String,
    isPassword: Boolean = false, onValueChange: (String) -> Unit
) {
    Column {
        Text(label, color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = Color(0xFF3A4060), fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = BlueAccent,
                unfocusedBorderColor = Color(0xFF2A3050),
                focusedTextColor     = PrimaryText,
                unfocusedTextColor   = PrimaryText,
                cursorColor          = BlueAccent,
            ),
            shape = RoundedCornerShape(10.dp),
            visualTransformation = if (isPassword)
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            else
                androidx.compose.ui.text.input.VisualTransformation.None
        )
    }
}

@Composable
fun SetupPrompt() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.Settings, null, tint = BlueAccent, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(16.dp))
            Text("Setup required", color = PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Go to Settings and enter your Oracle server URL and API key to begin monitoring.",
                color = MutedText, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
