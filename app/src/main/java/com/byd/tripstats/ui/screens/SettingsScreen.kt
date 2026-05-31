package com.byd.tripstats.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.view.Choreographer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.byd.tripstats.adb.AdbPermissionManager
import com.byd.tripstats.data.preferences.DEFAULT_CAR_OFF_TIMEOUT_MINUTES
import com.byd.tripstats.data.preferences.DEFAULT_MIN_TRIP_DISTANCE_KM
import com.byd.tripstats.data.preferences.PreferencesManager
import com.byd.tripstats.data.preferences.OffStateMode
import com.byd.tripstats.data.preferences.SocSource
import com.byd.tripstats.data.preferences.ThemeMode
import com.byd.tripstats.data.preferences.UnitSystem
import com.byd.tripstats.data.preferences.convertDistance
import com.byd.tripstats.data.preferences.convertEfficiency
import com.byd.tripstats.data.preferences.distanceUnit
import com.byd.tripstats.data.preferences.consumptionUnit
import com.byd.tripstats.data.preferences.toKilometers
import com.byd.tripstats.connections.AbrpConnectionManager
import com.byd.tripstats.connections.AbrpConnectionStore
import com.byd.tripstats.connections.MqttConnectionManager
import com.byd.tripstats.connections.MqttConnectionStore
import com.byd.tripstats.data.backup.TelegramManager
import com.byd.tripstats.data.model.VehicleTelemetry
import com.byd.tripstats.R
import com.byd.tripstats.sdk.VehicleCompatibilityProbe
import com.byd.tripstats.ui.theme.*
import com.byd.tripstats.ui.viewmodel.DashboardViewModel
import com.byd.tripstats.sdk.VehicleTelemetrySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private data class AppDiagnosticsSnapshot(
    val processCpuPercent: Double = 0.0,
    val systemCpuPercent: Double? = null,
    val rssMb: Int = 0,
    val pssMb: Int = 0,
    val javaHeapMb: Int = 0,
    val nativeHeapMb: Int = 0,
    val systemMemoryUsedPercent: Double = 0.0,
    val systemMemoryUsedMb: Int = 0,
    val systemMemoryTotalMb: Int = 0,
    val threadCount: Int = 0,
    val uptimeMinutes: Int = 0,
    val frameP50Ms: Double? = null,
    val frameP95Ms: Double? = null,
    val jankPercent: Double? = null,
    val history: List<DiagnosticsHistorySample> = emptyList(),
)

private data class DiagnosticsHistorySample(
    val timestampMs: Long,
    val appCpuPercent: Double,
    val systemCpuPercent: Double?,
    val appMemoryMb: Double,
    val systemMemoryPercent: Double
)

private data class CpuStat(
    val total: Long,
    val idle: Long
)

private object AppDiagnosticsMonitor {
    private const val PREFS_NAME = "app_diagnostics"
    private const val KEY_ENABLED = "details_enabled"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _snapshot = MutableStateFlow(AppDiagnosticsSnapshot())
    private val _enabled = MutableStateFlow(false)
    private var initialized = false
    private var samplingJob: Job? = null

    val snapshot: StateFlow<AppDiagnosticsSnapshot> = _snapshot.asStateFlow()
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val persisted = prefs.getBoolean(KEY_ENABLED, false)
        _enabled.value = persisted
        if (persisted) startSampling(context.applicationContext)
    }

    @Synchronized
    fun setEnabled(context: Context, value: Boolean) {
        initialize(context)
        if (_enabled.value == value) return
        _enabled.value = value
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, value)
            .apply()
        if (value) {
            startSampling(context.applicationContext)
        } else {
            stopSampling()
        }
    }

    @Synchronized
    private fun startSampling(context: Context) {
        if (samplingJob?.isActive == true) return
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        samplingJob = scope.launch {
            var lastCpuMs = Process.getElapsedCpuTime()
            var lastWallMs = SystemClock.elapsedRealtime()
            var lastSystemCpu = readCpuStat()
            val history = ArrayDeque(_snapshot.value.history)

            while (true) {
                val nowCpuMs = Process.getElapsedCpuTime()
                val nowWallMs = SystemClock.elapsedRealtime()
                val cpuDelta = max(0L, nowCpuMs - lastCpuMs)
                val wallDelta = max(1L, nowWallMs - lastWallMs)
                val cpuPercent = ((cpuDelta.toDouble() / wallDelta.toDouble()) * 100.0).coerceAtLeast(0.0)
                lastCpuMs = nowCpuMs
                lastWallMs = nowWallMs

                val currentSystemCpu = readCpuStat()
                val systemCpuPercent = calculateSystemCpuPercent(lastSystemCpu, currentSystemCpu)
                lastSystemCpu = currentSystemCpu

                val memInfo = activityManager.getProcessMemoryInfo(intArrayOf(Process.myPid())).firstOrNull()
                val systemMemInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
                val runtime = Runtime.getRuntime()
                val javaHeapMb = (((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024))).toInt()
                val nativeHeapMb = (Debug.getNativeHeapAllocatedSize() / (1024 * 1024)).toInt()
                val pssMb = memInfo?.totalPss?.div(1024) ?: 0
                val rssMb = memInfo?.totalPrivateDirty?.div(1024) ?: 0
                val systemTotalMb = (systemMemInfo.totalMem / (1024 * 1024)).toInt()
                val systemUsedMb = ((systemMemInfo.totalMem - systemMemInfo.availMem) / (1024 * 1024)).toInt()
                val systemMemoryPercent = if (systemMemInfo.totalMem > 0L) {
                    ((systemMemInfo.totalMem - systemMemInfo.availMem).toDouble() / systemMemInfo.totalMem.toDouble()) * 100.0
                } else {
                    0.0
                }
                val uptimeMinutes = (SystemClock.elapsedRealtime() / 60_000L).toInt()
                val threadCount = Thread.getAllStackTraces().size
                val cutoff = nowWallMs - 60_000L

                history.addLast(
                    DiagnosticsHistorySample(
                        timestampMs = nowWallMs,
                        appCpuPercent = cpuPercent,
                        systemCpuPercent = systemCpuPercent,
                        appMemoryMb = pssMb.toDouble(),
                        systemMemoryPercent = systemMemoryPercent
                    )
                )
                while (history.isNotEmpty() && history.first().timestampMs < cutoff) {
                    history.removeFirst()
                }

                _snapshot.value = _snapshot.value.copy(
                    processCpuPercent = cpuPercent,
                    systemCpuPercent = systemCpuPercent,
                    rssMb = rssMb,
                    pssMb = pssMb,
                    javaHeapMb = javaHeapMb,
                    nativeHeapMb = nativeHeapMb,
                    systemMemoryUsedPercent = systemMemoryPercent,
                    systemMemoryUsedMb = systemUsedMb,
                    systemMemoryTotalMb = systemTotalMb,
                    threadCount = threadCount,
                    uptimeMinutes = uptimeMinutes,
                    history = history.toList()
                )

                delay(2000)
            }
        }
    }

    @Synchronized
    private fun stopSampling() {
        samplingJob?.cancel()
        samplingJob = null
    }
}

private class FrameTimeTracker : Choreographer.FrameCallback {
    private val choreographer = Choreographer.getInstance()
    private val frameDurationsMs = ArrayDeque<Double>()
    private var started = false
    private var lastFrameNanos = 0L

    fun start() {
        if (started) return
        started = true
        lastFrameNanos = 0L
        choreographer.postFrameCallback(this)
    }

    fun stop() {
        if (!started) return
        started = false
        choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!started) return
        if (lastFrameNanos != 0L) {
            val durationMs = (frameTimeNanos - lastFrameNanos) / 1_000_000.0
            if (durationMs.isFinite() && durationMs in 0.0..5000.0) {
                frameDurationsMs.addLast(durationMs)
                while (frameDurationsMs.size > 180) frameDurationsMs.removeFirst()
            }
        }
        lastFrameNanos = frameTimeNanos
        choreographer.postFrameCallback(this)
    }

    fun snapshot(): Triple<Double?, Double?, Double?> {
        if (frameDurationsMs.isEmpty()) return Triple(null, null, null)
        val sorted = frameDurationsMs.toList().sorted()
        fun percentile(p: Double): Double {
            val index = ((sorted.lastIndex) * p).toInt().coerceIn(0, sorted.lastIndex)
            return sorted[index]
        }
        val p50 = percentile(0.50)
        val p95 = percentile(0.95)
        val janky = frameDurationsMs.count { it > 16.7 }
        val jankPercent = (janky.toDouble() / frameDurationsMs.size.toDouble()) * 100.0
        return Triple(p50, p95, jankPercent)
    }
}

private fun readCpuStat(): CpuStat? = runCatching {
    val parts = File("/proc/stat")
        .readLines()
        .firstOrNull { it.startsWith("cpu ") }
        ?.trim()
        ?.split(Regex("\\s+"))
        ?: return@runCatching null
    val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
    if (values.size < 5) return@runCatching null
    val idle = values.getOrElse(3) { 0L } + values.getOrElse(4) { 0L }
    CpuStat(total = values.sum(), idle = idle)
}.getOrNull()

// Android 10+ restricts /proc/stat reads. Fall back to /proc/loadavg (always
// readable on Linux) and approximate system load as load1 / cores × 100. Less precise
// than the stat-based delta but recognisable and updates over time.
private fun readLoadAvgCpuPercent(): Double? = runCatching {
    val line = File("/proc/loadavg").readText().trim()
    if (line.isEmpty()) return@runCatching null
    val load1 = line.split(Regex("\\s+")).firstOrNull()?.toDoubleOrNull() ?: return@runCatching null
    if (!load1.isFinite() || load1 < 0.0) return@runCatching null
    val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    ((load1 / cores) * 100.0).coerceIn(0.0, 100.0)
}.getOrNull()

private fun calculateSystemCpuPercent(previous: CpuStat?, current: CpuStat?): Double? {
    if (previous == null || current == null) return readLoadAvgCpuPercent()
    val totalDelta = current.total - previous.total
    val idleDelta = current.idle - previous.idle
    if (totalDelta <= 0L) return readLoadAvgCpuPercent()
    val result = ((totalDelta - idleDelta).toDouble() / totalDelta.toDouble() * 100.0).coerceIn(0.0, 100.0)
    // If the stat-based calculation produces an unrealistic value, fall back to loadavg
    return if (result.isFinite() && result >= 0.0) result else readLoadAvgCpuPercent()
}

private val diagnosticsTimeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel         : DashboardViewModel,
    onNavigateBack    : () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToTripGoals: () -> Unit = {}
) {
    val context           = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val scope             = rememberCoroutineScope()

    val snackbarHostState  = remember { SnackbarHostState() }
    var selectedTab       by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("App", "Connections", "Preferences", "About & FAQ")

    val updateInfo by viewModel.updateInfo.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(28.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        text = {
                            if (index == 3 && updateInfo != null) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = AccelerationOrange,
                                            modifier = Modifier.offset(x = 18.dp, y = (-2).dp)
                                        ) {
                                            Text(
                                                text = "1",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                ) {
                                    Text(
                                        title,
                                        fontSize   = 17.sp,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            } else {
                                Text(
                                    title,
                                    fontSize   = 17.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> AppManagementTab(
                        viewModel         = viewModel,
                        context           = context,
                        onNavigateToBackup = onNavigateToBackup,
                        scope             = scope
                    )
                    1 -> ConnectionsTab()
                    2 -> AppPreferencesTab(
                        viewModel = viewModel,
                        preferencesManager = preferencesManager,
                        onNavigateToTripGoals = onNavigateToTripGoals
                    )
                    3 -> AboutTab(viewModel = viewModel)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Tab 1 — App Management
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AppManagementTab(
    viewModel         : DashboardViewModel,
    context           : Context,
    onNavigateToBackup: () -> Unit,
    scope             : CoroutineScope
) {
    val lastBackup     = remember { viewModel.listDatabaseBackups().firstOrNull() }
    val lastBackupLabel = remember(lastBackup) {
        if (lastBackup == null) "No local backup found"
        else {
            val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
            "Last backup: ${sdf.format(java.util.Date(lastBackup.lastModified()))}"
        }
    }

    var showResetConfirm by remember { mutableStateOf(false) }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(icon = Icons.Filled.Backup, title = "Backup & Restore")

        // Two-column summary row
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BackupSummaryCard(
                modifier   = Modifier.weight(1f),
                icon       = Icons.Filled.Storage,
                title      = "Local",
                body       = "Download/BydTripStats/",
                statusLine = lastBackupLabel,
                statusOk   = lastBackup != null
            )
            BackupSummaryCard(
                modifier   = Modifier.weight(1f),
                icon       = Icons.AutoMirrored.Filled.Send,
                title      = "Telegram",
                body       = "Private bot chat",
                statusLine = "Manual & scheduled",
                statusOk   = true
            )
        }

        Button(onClick = onNavigateToBackup, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Backup, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open Backup & Restore", fontSize = 16.sp)
        }

        HorizontalDivider()

        VehicleCompatibilitySection(context = context, scope = scope)

        HorizontalDivider()

        AppDiagnosticsCard()

        HorizontalDivider()

        WebCompanionSection(context = context, scope = scope)
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            icon  = { Icon(Icons.Filled.Warning, null,
                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp)) },
            title = { Text("Reset all trip data?", fontWeight = FontWeight.Bold) },
            text  = {
                Text(
                    "This will permanently delete all trips and statistics.\n\n" +
                    "A backup will be saved to Download automatically before the reset. " +
                    "The app will close and reopen.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        scope.launch {
                            viewModel.resetDatabase()
                            val launchIntent = context.packageManager
                                .getLaunchIntentForPackage(context.packageName)
                                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
                            if (launchIntent != null) {
                                val pending = PendingIntent.getActivity(
                                    context, 1, launchIntent,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                                )
                                val alarm = context.getSystemService(AlarmManager::class.java)
                                alarm.set(AlarmManager.RTC, System.currentTimeMillis() + 800L, pending)
                            }
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BydErrorRed)
                ) { Text("Yes, reset everything") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Web Companion ─────────────────────────────────────────────────────────────

@Composable
private fun WebCompanionSection(context: Context, scope: CoroutineScope) {
    val preferencesManager = remember { PreferencesManager(context) }
    // DataStore is the authoritative state — the switch just writes here and
    // the LaunchedEffect reacts, so the toggle is always responsive.
    val enabled by preferencesManager.webServerEnabled.collectAsState(initial = true)
    val port    by preferencesManager.webServerPort.collectAsState(
        initial = com.byd.tripstats.data.preferences.PreferencesManager.DEFAULT_WEB_SERVER_PORT
    )
    val pin     by preferencesManager.webServerPin.collectAsState(initial = "")
    var portInput   by remember(port) { mutableStateOf(port.toString()) }
    var pinInput    by remember(pin)  { mutableStateOf(pin) }
    var pinVisible   by remember { mutableStateOf(false) }
    var serverUrl    by remember { mutableStateOf<String?>(null) }
    var serverError  by remember { mutableStateOf<String?>(null) }
    val lockedCount  by com.byd.tripstats.server.WebServerManager.lockedOutCount.collectAsState()
    val clipManager = androidx.compose.ui.platform.LocalClipboardManager.current

    // Ensure a PIN exists as soon as the section is first composed
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { preferencesManager.getOrCreateWebServerPin() }
    }

    // Start/stop the server whenever enabled, port, or PIN changes.
    // Runs on IO so ServerSocket binding never touches the main thread.
    LaunchedEffect(enabled, port, pin) {
        com.byd.tripstats.server.WebServerManager.stop()
        serverUrl   = null
        serverError = null
        if (enabled && pin.isNotEmpty()) {
            val error = withContext(Dispatchers.IO) {
                com.byd.tripstats.server.WebServerManager.start(context, port, pin)
            }
            if (error == null) {
                serverUrl   = com.byd.tripstats.server.WebServerManager.getUrl(context)
                serverError = null
            } else {
                serverUrl   = null
                serverError = error
            }
        }
    }

    SectionHeader(icon = Icons.Filled.Language, title = "Web Companion")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Web companion",
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (enabled)
                            "Browse trips & charging from any device on the same WiFi."
                        else
                            "Start a local server so you can open trip history in a browser.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked         = enabled,
                    onCheckedChange = { next ->
                        scope.launch { preferencesManager.saveWebServerEnabled(next) }
                    },
                    thumbContent = if (!enabled) {
                        { Box(Modifier.size(12.dp).background(ToggleUncheckedTrack, CircleShape)) }
                    } else null,
                    colors = SwitchDefaults.colors(
                        uncheckedThumbColor  = Color.White,
                        uncheckedTrackColor  = ToggleUncheckedTrack,
                        uncheckedBorderColor = ToggleUncheckedTrack
                    )
                )
            }

            // Port row — always visible so users can change it even while disabled
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = portInput,
                    onValueChange = { portInput = it.filter(Char::isDigit).take(5) },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val p = portInput.toIntOrNull()?.coerceIn(1024, 65535) ?: return@Button
                        scope.launch { preferencesManager.saveWebServerPort(p) }
                    },
                    enabled = portInput.toIntOrNull()?.let { it in 1024..65535 } == true &&
                              portInput.toIntOrNull() != port
                ) { Text("Apply") }
            }
            Text(
                "Try 8081, 8888, 9090 if a port is blocked. Changes restart the server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

            // ── PIN ──────────────────────────────────────────────────────────
            Text(
                "Access PIN",
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Shown on the browser login page when someone opens the server URL.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it.filter(Char::isDigit).take(10) },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = if (pinVisible)
                        androidx.compose.ui.text.input.VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        IconButton(onClick = { pinVisible = !pinVisible }) {
                            Icon(
                                if (pinVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (pinVisible) "Hide PIN" else "Show PIN"
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                // Apply custom PIN
                Button(
                    onClick = {
                        if (pinInput.length >= 4) scope.launch {
                            preferencesManager.saveWebServerPin(pinInput)
                        }
                    },
                    enabled = pinInput.length >= 4 && pinInput != pin
                ) { Text("Set") }
                // Generate a new random PIN
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val newPin = (100_000..999_999).random().toString()
                            pinInput = newPin
                            preferencesManager.saveWebServerPin(newPin)
                        }
                    }
                ) { Text("Regen") }
            }
            Text(
                "Min 4 digits. Changing the PIN invalidates all active browser sessions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

            // ── Lockout banner ───────────────────────────────────────────────
            if (lockedCount > 0) {
                val plural = if (lockedCount > 1) "addresses" else "address"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                            androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "$lockedCount IP $plural locked out",
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "Too many incorrect PIN attempts detected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                    OutlinedButton(
                        onClick = { com.byd.tripstats.server.WebServerManager.clearLockouts() },
                        colors  = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Clear") }
                }
            }

            if (enabled) {
                if (serverError != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "Failed to start: $serverError",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    val url = serverUrl ?: "Waiting for IP…"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            url,
                            style      = MaterialTheme.typography.bodyMedium,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier   = Modifier.weight(1f)
                        )
                        if (serverUrl != null) {
                            IconButton(
                                onClick  = { clipManager.setText(androidx.compose.ui.text.AnnotatedString(url)) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = "Copy URL",
                                    modifier = Modifier.size(18.dp),
                                    tint     = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Text(
                        "Open this address on any device connected to the same WiFi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Tab 2 — Connections
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ConnectionsTab() {
    val context = LocalContext.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<DashboardViewModel>()
    val selectedCarConfig by viewModel.selectedCarConfig.collectAsState()
    val liveSnapshot by viewModel.vehicleSnapshot.collectAsState()
    val currentTelemetry by viewModel.currentTelemetry.collectAsState()
    val liveTelemetry = remember(liveSnapshot, currentTelemetry, selectedCarConfig) {
        liveSnapshot?.toTelemetry(selectedCarConfig) ?: currentTelemetry
    }
    val abrpManager = remember { AbrpConnectionManager(context) }
    val mqttManager = remember { MqttConnectionManager(context) }
    val screenScope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(AbrpConnectionStore.load(context)) }
    var tokenInput by rememberSaveable { mutableStateOf(settings.userToken) }
    var enabled by rememberSaveable { mutableStateOf(settings.enabled) }
    var intervalInput by rememberSaveable { mutableStateOf(settings.uploadIntervalSeconds.toString()) }
    var lastSavedAt by remember { mutableStateOf(settings.lastUploadAtMs) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var mqttSettings by remember { mutableStateOf(MqttConnectionStore.load(context)) }
    var mqttBrokerInput by rememberSaveable { mutableStateOf(mqttSettings.brokerUrl) }
    var mqttPortInput by rememberSaveable { mutableStateOf(mqttSettings.brokerPort.toString()) }
    var mqttUsernameInput by rememberSaveable { mutableStateOf(mqttSettings.username) }
    var mqttPasswordInput by rememberSaveable { mutableStateOf(mqttSettings.password) }
    var mqttFriendlyNameInput by rememberSaveable { mutableStateOf(mqttSettings.friendlyName) }
    var mqttEnabled by rememberSaveable { mutableStateOf(mqttSettings.enabled) }
    var mqttIntervalInput by rememberSaveable { mutableStateOf(mqttSettings.publishIntervalSeconds.toString()) }
    var mqttResult by remember { mutableStateOf<String?>(null) }
    var mqttTesting by remember { mutableStateOf(false) }
    var showAbrpToken by rememberSaveable { mutableStateOf(false) }
    var showConnectionDetails by rememberSaveable { mutableStateOf(false) }

    fun reloadAbrpDraft() {
        settings = AbrpConnectionStore.load(context)
        tokenInput = settings.userToken
        enabled = settings.enabled
        intervalInput = settings.uploadIntervalSeconds.toString()
        lastSavedAt = settings.lastUploadAtMs
    }

    fun reloadMqttDraft() {
        mqttSettings = MqttConnectionStore.load(context)
        mqttBrokerInput = mqttSettings.brokerUrl
        mqttPortInput = mqttSettings.brokerPort.toString()
        mqttUsernameInput = mqttSettings.username
        mqttPasswordInput = mqttSettings.password
        mqttFriendlyNameInput = mqttSettings.friendlyName
        mqttEnabled = mqttSettings.enabled
        mqttIntervalInput = mqttSettings.publishIntervalSeconds.toString()
    }

    fun reloadConnectionDrafts() {
        reloadAbrpDraft()
        reloadMqttDraft()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(icon = Icons.Filled.Link, title = "Connections")

        if (!showConnectionDetails) {
            Text(
                "Choose a connection to configure. The cards below show live status at a glance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ConnectionSummaryCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Route,
                    title = "ABRP",
                    body = "A Better Routeplanner uploads live telemetry.",
                    statusLine = "Status: ${if (settings.enabled) "Enabled" else "Disabled"} • ${settings.lastStatus}"
                )
                ConnectionSummaryCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Cloud,
                    title = "MQTT",
                    body = "Publish live telemetry to a broker such as HiveMQ or HomeAssistant.",
                    statusLine = "Status: ${if (mqttSettings.enabled) "Enabled" else "Disabled"} • ${mqttSettings.lastStatus}"
                )
            }
            Button(
                onClick = {
                    reloadConnectionDrafts()
                    showConnectionDetails = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
            ) {
                Text("Open Connections")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    reloadConnectionDrafts()
                    showConnectionDetails = false
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Back to overview")
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "ABRP",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Link Generic token uploads live telemetry to A Better Routeplanner.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Get your ABRP Link Generic token from ABRP web or app. \n" +
                        "Go to Settings -> Vehicle -> Live data -> Edit connections -> " +
                        "In-car live data -> Link Generic",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Enable ABRP",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (enabled) "Uploads every $intervalInput s."
                                else "Turn on to configure ABRP uploads.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { next ->
                                enabled = next
                                if (!next) {
                                    val interval = intervalInput.toIntOrNull()?.coerceIn(5, 120) ?: 5
                                    settings = settings.copy(
                                        enabled = false,
                                        userToken = tokenInput,
                                        apiKey = settings.apiKey.ifBlank { AbrpConnectionStore.DEFAULT_PUBLIC_API_KEY },
                                        uploadIntervalSeconds = interval
                                    )
                                    AbrpConnectionStore.save(context, settings)
                                    lastSavedAt = System.currentTimeMillis()
                                }
                            },
                            thumbContent = if (!enabled) {
                                {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(ToggleUncheckedTrack, CircleShape)
                                    )
                                }
                            } else null,
                            colors = SwitchDefaults.colors(
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = ToggleUncheckedTrack,
                                uncheckedBorderColor = ToggleUncheckedTrack
                            )
                        )
                    }
                    AnimatedVisibility(visible = enabled, enter = expandVertically(), exit = shrinkVertically()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsGroupLabel("Connection")
                            OutlinedTextField(
                                value = tokenInput,
                                onValueChange = { tokenInput = it.trim() },
                                label = { Text("ABRP user token") },
                                placeholder = { Text("Paste your Link Generic token") },
                                singleLine = true,
                                visualTransformation = if (showAbrpToken) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showAbrpToken = !showAbrpToken }) {
                                        Icon(
                                            imageVector = if (showAbrpToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = if (showAbrpToken) "Hide token" else "Show token"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = intervalInput,
                                onValueChange = { intervalInput = it.filter(Char::isDigit).take(3) },
                                label = { Text("Upload interval (seconds)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        val interval = intervalInput.toIntOrNull()?.coerceIn(5, 120) ?: 5
                                        settings = settings.copy(
                                            enabled = enabled,
                                            userToken = tokenInput,
                                            apiKey = settings.apiKey.ifBlank { AbrpConnectionStore.DEFAULT_PUBLIC_API_KEY },
                                            uploadIntervalSeconds = interval
                                        )
                                        AbrpConnectionStore.save(context, settings)
                                        lastSavedAt = System.currentTimeMillis()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                                ) {
                                    Text("Save")
                                }
                                Button(
                                    onClick = {
                                        val telemetry = liveTelemetry
                                        if (telemetry == null) {
                                            testResult = if (viewModel.serviceConnected.value) {
                                                "Telemetry not ready yet - wait a second and try again"
                                            } else {
                                                "Telemetry service not connected yet"
                                            }
                                            return@Button
                                        }
                                        val current = AbrpConnectionStore.load(context).copy(
                                            enabled = enabled,
                                            userToken = tokenInput,
                                            apiKey = settings.apiKey.ifBlank { AbrpConnectionStore.DEFAULT_PUBLIC_API_KEY },
                                            uploadIntervalSeconds = intervalInput.toIntOrNull()?.coerceIn(5, 120) ?: 5
                                        )
                                        AbrpConnectionStore.save(context, current)
                                        screenScope.launch(Dispatchers.IO) {
                                            val (ok, status) = abrpManager.testUpload(telemetry, selectedCarConfig, current)
                                            launch(Dispatchers.Main) {
                                                testResult = if (ok) "Test upload succeeded" else status
                                                settings = AbrpConnectionStore.load(context)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                                ) {
                                    Text("Test & Save")
                                }
                                OutlinedButton(onClick = {
                                    reloadAbrpDraft()
                                }) {
                                    Text("Reload")
                                }
                            }
                        }
                    }
                }
                ConnectionStatusCard(
                    title = "ABRP status",
                    content = {
                        SettingsDetailRow("Configured", if (settings.userToken.isBlank()) "No" else "Yes")
                        SettingsDetailRow("Enabled", if (settings.enabled) "Yes" else "No")
                        SettingsDetailRow("Last upload", if (settings.lastUploadAtMs > 0L) {
                            formatFriendlyTimestamp(settings.lastUploadAtMs)
                        } else {
                            "n/a"
                        })
                        SettingsDetailRow("Last status", settings.lastStatus)
                        if (!testResult.isNullOrBlank()) {
                            Text(
                                testResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (testResult!!.contains("succeeded", ignoreCase = true)) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "MQTT",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Publish live telemetry to an external broker such as HiveMQ.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable MQTT", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                if (mqttEnabled) "Publishes the live telemetry JSON every $mqttIntervalInput s."
                                else "Turn on to configure MQTT publishing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = mqttEnabled,
                            onCheckedChange = { next ->
                                mqttEnabled = next
                                if (!next) {
                                    val interval = mqttIntervalInput.toIntOrNull()?.coerceIn(1, 120) ?: 1
                                    val port = mqttPortInput.toIntOrNull()?.coerceIn(1, 65535) ?: 1883
                                    mqttSettings = mqttSettings.copy(
                                        enabled = false,
                                        brokerUrl = mqttBrokerInput,
                                        brokerPort = port,
                                        username = mqttUsernameInput,
                                        password = mqttPasswordInput,
                                        friendlyName = mqttFriendlyNameInput,
                                        publishIntervalSeconds = interval
                                    )
                                    MqttConnectionStore.save(context, mqttSettings)
                                    mqttResult = "MQTT disabled and saved"
                                }
                            },
                            thumbContent = if (!mqttEnabled) {
                                {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(ToggleUncheckedTrack, CircleShape)
                                    )
                                }
                            } else null,
                            colors = SwitchDefaults.colors(
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = ToggleUncheckedTrack,
                                uncheckedBorderColor = ToggleUncheckedTrack
                            )
                        )
                    }
                    AnimatedVisibility(visible = mqttEnabled, enter = expandVertically(), exit = shrinkVertically()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsGroupLabel("Connection")
                            OutlinedTextField(
                                value = mqttBrokerInput,
                                onValueChange = { mqttBrokerInput = it.trim() },
                                label = { Text("Broker URL") },
                                placeholder = { Text("example.hivemq.cloud") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = mqttPortInput,
                                onValueChange = { mqttPortInput = it.filter(Char::isDigit).take(5) },
                                label = { Text("Port") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = mqttFriendlyNameInput,
                                onValueChange = { mqttFriendlyNameInput = it.trim() },
                                label = { Text("Device friendly name") },
                                placeholder = { Text("my-byd-seal") },
                                singleLine = true,
                                supportingText = {
                                    val preview = mqttFriendlyNameInput.replace("[^a-zA-Z0-9_-]".toRegex(), "_").ifBlank { "<android-id>" }
                                    Text("Topic: byd-trip-stats/$preview/state", style = MaterialTheme.typography.bodySmall)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = mqttUsernameInput,
                                onValueChange = { mqttUsernameInput = it },
                                label = { Text("Username") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = mqttPasswordInput,
                                onValueChange = { mqttPasswordInput = it },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = mqttIntervalInput,
                                onValueChange = { mqttIntervalInput = it.filter(Char::isDigit).take(3) },
                                label = { Text("Publish interval (seconds)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText = { Text("Driving: every ${mqttIntervalInput.ifBlank { "1" }} s  ·  Charging: every 30 s  ·  Idle: snapshot every ~90 min", style = MaterialTheme.typography.bodySmall) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        val interval = mqttIntervalInput.toIntOrNull()?.coerceIn(1, 120) ?: 1
                                        val port = mqttPortInput.toIntOrNull()?.coerceIn(1, 65535) ?: 1883
                                        mqttSettings = mqttSettings.copy(
                                            enabled = mqttEnabled,
                                            brokerUrl = mqttBrokerInput,
                                            brokerPort = port,
                                            username = mqttUsernameInput,
                                            password = mqttPasswordInput,
                                            friendlyName = mqttFriendlyNameInput,
                                            publishIntervalSeconds = interval
                                        )
                                        MqttConnectionStore.save(context, mqttSettings)
                                        mqttResult = "MQTT settings saved"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                                ) {
                                    Text("Save")
                                }
                                Button(
                                    enabled = !mqttTesting,
                                    onClick = {
                                        val telemetry = liveTelemetry
                                        if (telemetry == null) {
                                            mqttResult = "No live telemetry yet"
                                            return@Button
                                        }
                                        val interval = mqttIntervalInput.toIntOrNull()?.coerceIn(1, 120) ?: 1
                                        val port = mqttPortInput.toIntOrNull()?.coerceIn(1, 65535) ?: 1883
                                        val current = MqttConnectionStore.load(context).copy(
                                            enabled = mqttEnabled,
                                            brokerUrl = mqttBrokerInput,
                                            brokerPort = port,
                                            username = mqttUsernameInput,
                                            password = mqttPasswordInput,
                                            friendlyName = mqttFriendlyNameInput,
                                            publishIntervalSeconds = interval
                                        )
                                        MqttConnectionStore.save(context, current)
                                        mqttTesting = true
                                        mqttResult = null
                                        screenScope.launch {
                                            try {
                                                val (ok, status) = withContext(Dispatchers.IO) {
                                                    mqttManager.testPublish(telemetry)
                                                }
                                                mqttResult = if (ok) "MQTT test publish succeeded" else status
                                                mqttSettings = MqttConnectionStore.load(context)
                                            } catch (e: Exception) {
                                                mqttResult = "MQTT test failed: ${e.message}"
                                            } finally {
                                                mqttTesting = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                                ) {
                                    Text(if (mqttTesting) "Testing…" else "Test & Save")
                                }
                                OutlinedButton(onClick = {
                                    reloadMqttDraft()
                                }) {
                                    Text("Reload")
                                }
                            }
                        }
                    }
                }
                ConnectionStatusCard(
                    title = "MQTT status",
                    content = {
                        SettingsDetailRow("Configured", if (mqttSettings.brokerUrl.isBlank()) "No" else "Yes")
                        SettingsDetailRow("Enabled", if (mqttSettings.enabled) "Yes" else "No")
                        SettingsDetailRow("Last publish", if (mqttSettings.lastPublishAtMs > 0L) {
                            formatFriendlyTimestamp(mqttSettings.lastPublishAtMs)
                        } else {
                            "n/a"
                        })
                        SettingsDetailRow("Last status", mqttSettings.lastStatus)
                        if (!mqttResult.isNullOrBlank()) {
                            Text(
                                mqttResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (mqttResult!!.contains("succeeded", ignoreCase = true)) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ConnectionSummaryCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    body: String,
    statusLine: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                statusLine,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

private fun formatFriendlyTimestamp(epochMs: Long): String {
    val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return formatter.format(Date(epochMs))
}

// ═══════════════════════════════════════════════════════════════════════════════
// Tab 3 — Preferences
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun AppPreferencesTab(
    viewModel: DashboardViewModel,
    preferencesManager: PreferencesManager,
    onNavigateToTripGoals: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offStateMode by preferencesManager.offStateMode.collectAsState(
        initial = preferencesManager.getCachedOffStateMode()
    )
    val dashboardIconsEnabled by preferencesManager.dashboardAnimationsEnabled.collectAsState(
        initial = preferencesManager.getCachedAnimationsEnabled()
    )
    val carOffTimeoutMinutes by preferencesManager.carOffTimeoutMinutes.collectAsState(
        initial = preferencesManager.getCachedCarOffTimeoutMinutes()
    )
    val minTripDistanceKm by preferencesManager.minTripDistanceKm.collectAsState(
        initial = preferencesManager.getCachedMinTripDistanceKm()
    )
    val themeMode by preferencesManager.themeMode.collectAsState(
        initial = preferencesManager.getCachedThemeMode()
    )
    val socSource by preferencesManager.socSource.collectAsState(
        initial = preferencesManager.getCachedSocSource()
    )
    val unitSystem by viewModel.unitSystem.collectAsState()
    val electricityPrice by viewModel.electricityPricePerKwh.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val tripGoals by viewModel.tripGoals.collectAsState()
    val personalBests by viewModel.personalBests.collectAsState()
    var showTariffDialog by remember { mutableStateOf(false) }
    var showCarOffTimeoutDialog by remember { mutableStateOf(false) }
    var showMinTripDistanceDialog by remember { mutableStateOf(false) }
    var priceInput by remember(electricityPrice) {
        mutableStateOf(if (electricityPrice > 0.0) "%.4f".format(electricityPrice) else "")
    }
    val currencyOptions = remember {
        listOf(
            "€" to "EUR",
            "£" to "GBP",
            "$" to "USD",
            "A$" to "AUD",
            "฿" to "THB"
        )
    }
    var currencyMenuExpanded by remember { mutableStateOf(false) }
    var selectedCurrency by remember(currencySymbol) {
        mutableStateOf(currencyOptions.firstOrNull { it.first == currencySymbol } ?: currencyOptions.first())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(icon = Icons.Filled.Tune, title = "Preferences")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Dashboard icons & animations",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "When enabled, the range-projection card shows a liquid-fill battery icon, an AWD/axle drawing with tyre pressure/temperature overlays, and an animated consumption thumbnail above the chart. When disabled, those move into the top bar (battery and consumption icons) and a dedicated Tyres stat card on the side panel — freeing vertical space for the range chart and skipping all animations.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = dashboardIconsEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                preferencesManager.saveDashboardAnimationsEnabled(enabled)
                            }
                        },
                        thumbContent = if (!dashboardIconsEnabled) {
                            {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(ToggleUncheckedTrack, CircleShape)
                                )
                            }
                        } else null,
                        colors = SwitchDefaults.colors(
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = ToggleUncheckedTrack,
                            uncheckedBorderColor = ToggleUncheckedTrack
                        )
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Theme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Choose how the app looks. System default follows your device's dark/light setting.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        ThemeMode.SYSTEM to "System",
                        ThemeMode.LIGHT  to "Light",
                        ThemeMode.DARK   to "Dark",
                    ).forEach { (mode, label) ->
                        Button(
                            onClick = { scope.launch { preferencesManager.saveThemeMode(mode) } },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (themeMode == mode)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (themeMode == mode)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(label, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "SoC source",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Choose which battery percentage reading to display on the dashboard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        SocSource.PANEL to "Panel",
                        SocSource.BMS   to "BMS",
                    ).forEach { (source, label) ->
                        Button(
                            onClick = { scope.launch { preferencesManager.saveSocSource(source) } },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (socSource == source)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (socSource == source)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(label, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "On PHEVs the BMS SoC is usually not reported — use Panel if BMS shows 0.\nBMS is more accurate (float) than Panel (integer). Also, larger divergence from Panel is a great indication that it is time for either 100% charge or charging calibration",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Background activity when car is off",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Controls whether the telemetry service keeps running after the car is parked.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        OffStateMode.ENABLED    to "Always On",
                        OffStateMode.DISABLED   to "Minimal",
                        OffStateMode.DEEP_SLEEP to "Deep Sleep",
                    ).forEach { (mode, label) ->
                        Button(
                            onClick = { scope.launch { preferencesManager.saveOffStateMode(mode) } },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (offStateMode == mode)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (offStateMode == mode)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(label, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                listOf(
                    OffStateMode.ENABLED    to "Always On: service runs 24/7. Continuous 12V/SoC samples in battery history and ADB-over-WiFi stays reachable. Small additional load on top of BYD's own stock background drain.",
                    OffStateMode.DISABLED   to "Minimal: service self-stops 5 min after the car turns off, then a 90-min alarm briefly wakes it for a charging snapshot. Lower drain at the cost of sparse off-state samples and no always-on ADB.",
                    OffStateMode.DEEP_SLEEP to "Deep Sleep: service self-stops 5 min after the car turns off with no further wakeups. Allows the car's ECUs to reach full deep sleep.",
                ).forEach { (mode, description) ->
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (offStateMode == mode)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (offStateMode == mode) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Engine-off trip timeout",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "How long the trip stays open after the car turns off. If the car comes back on within this window the recording resumes seamlessly (same trip, a new segment appears along with the cumulative distance in parenthesis). Past the window the trip ends and the next drive starts a new one. Default is 3 minutes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Current: $carOffTimeoutMinutes min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { showCarOffTimeoutDialog = true }) {
                    Icon(Icons.Filled.Timer, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Change timeout")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Minimum trip distance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Auto-discard trips shorter than this when they end (datapoints, segments and stats are removed too). Useful for filtering out moving the car a few meters in the driveway or very short distances. Set to 0 to disable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Heads-up: discarded trips disappear from everything that reads the trip table — history list, weekly/monthly/yearly consumption charts, monthly distance and energy totals, seasonal analysis, and the SoH degradation series. A high threshold over a quiet day means no point will be plotted for that day. The chart already ignores trips under 0.5 km, so only thresholds above that change the chart further.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (minTripDistanceKm > 0.0) {
                        "Current: %.2f %s".format(
                            unitSystem.convertDistance(minTripDistanceKm),
                            unitSystem.distanceUnit
                        )
                    } else {
                        "Current: disabled"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { showMinTripDistanceDialog = true }) {
                    Icon(Icons.Filled.Straighten, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (minTripDistanceKm > 0.0) "Change minimum" else "Set minimum")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Units",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Choose how distances and speeds are displayed. Your vehicle's odometer and speed values come from the BMS and are already in the correct unit for your market.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { scope.launch { viewModel.saveUnitSystem(UnitSystem.METRIC) } },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (unitSystem == UnitSystem.METRIC)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (unitSystem == UnitSystem.METRIC)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Metric", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { scope.launch { viewModel.saveUnitSystem(UnitSystem.IMPERIAL) } },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (unitSystem == UnitSystem.IMPERIAL)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (unitSystem == UnitSystem.IMPERIAL)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Imperial", fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    if (unitSystem == UnitSystem.IMPERIAL)
                        "Imperial: miles, mph, kWh/100mi"
                    else
                        "Metric: km, km/h, kWh/100km",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Electricity tariff",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (electricityPrice > 0.0) {
                        "Current rate: ${"%.4f".format(electricityPrice)} $currencySymbol / kWh"
                    } else {
                        "Set your home charging tariff so trip costs can be estimated consistently."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { showTariffDialog = true }) {
                    Icon(Icons.Filled.Euro, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (electricityPrice > 0.0) "Edit tariff" else "Set tariff")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Goals & personal bests",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Consumption goal: ${
                        tripGoals.targetConsumptionKwhPer100km?.let { "%.1f ${unitSystem.consumptionUnit}".format(unitSystem.convertEfficiency(it)) } ?: "Not set"
                    }",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Monthly distance goal: ${
                        tripGoals.targetDistanceKmPerMonth?.let { "%.0f ${unitSystem.distanceUnit}".format(unitSystem.convertDistance(it)) } ?: "Not set"
                    }",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Best consumption: ${
                        personalBests.bestConsumption?.let { "%.1f ${unitSystem.consumptionUnit}".format(unitSystem.convertEfficiency(it)) } ?: "—"
                    }",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Best distance: ${
                        personalBests.bestDistance?.let { "%.1f ${unitSystem.distanceUnit}".format(unitSystem.convertDistance(it)) } ?: "—"
                    }",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onNavigateToTripGoals) {
                    Icon(Icons.Filled.EmojiEvents, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open goals & personal bests")
                }
            }
        }
    }

    if (showTariffDialog) {
        AlertDialog(
            onDismissRequest = { showTariffDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Electricity tariff", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter your home charging price so trip costs can use your fixed tariff as the baseline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = { Text("Price per kWh") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Box {
                        OutlinedTextField(
                            value = "${selectedCurrency.first} (${selectedCurrency.second})",
                            onValueChange = { },
                            label = { Text("Currency") },
                            singleLine = true,
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { currencyMenuExpanded = !currencyMenuExpanded }) {
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select currency")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = currencyMenuExpanded,
                            onDismissRequest = { currencyMenuExpanded = false },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 0.dp
                        ) {
                            currencyOptions.forEach { (symbol, code) ->
                                DropdownMenuItem(
                                    text = { Text("$code ($symbol)") },
                                    onClick = {
                                        selectedCurrency = symbol to code
                                        currencyMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    if (electricityPrice > 0.0) {
                        Text(
                            "Active: ${"%.4f".format(electricityPrice)} $currencySymbol / kWh",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val price = priceInput.replace(',', '.').toDoubleOrNull()
                        viewModel.saveElectricityPrice(price ?: 0.0, selectedCurrency.first)
                        showTariffDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showTariffDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCarOffTimeoutDialog) {
        var minutesInput by remember(carOffTimeoutMinutes) {
            mutableStateOf(carOffTimeoutMinutes.toString())
        }
        AlertDialog(
            onDismissRequest = { showCarOffTimeoutDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Engine-off trip timeout", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Trip stays open for this many minutes after the engine turns off. " +
                            "Default is $DEFAULT_CAR_OFF_TIMEOUT_MINUTES min.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = minutesInput,
                        onValueChange = { minutesInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Minutes") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mins = minutesInput.toIntOrNull()?.coerceAtLeast(1)
                            ?: DEFAULT_CAR_OFF_TIMEOUT_MINUTES
                        scope.launch { preferencesManager.saveCarOffTimeoutMinutes(mins) }
                        showCarOffTimeoutDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showCarOffTimeoutDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showMinTripDistanceDialog) {
        // Edit value in the user's display unit so the number they type matches
        // the number shown on the card. Convert back to km for storage.
        val initialDisplay = if (minTripDistanceKm > 0.0) {
            "%.2f".format(unitSystem.convertDistance(minTripDistanceKm))
        } else ""
        var distanceInput by remember(minTripDistanceKm, unitSystem) {
            mutableStateOf(initialDisplay)
        }
        AlertDialog(
            onDismissRequest = { showMinTripDistanceDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Minimum trip distance", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Trips shorter than this are discarded when they end. Set to 0 (or leave empty) to keep every trip.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = distanceInput,
                        onValueChange = { distanceInput = it },
                        label = { Text("Distance (${unitSystem.distanceUnit})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val displayValue = distanceInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                        val km = if (displayValue <= 0.0) 0.0 else unitSystem.toKilometers(displayValue)
                        scope.launch { preferencesManager.saveMinTripDistanceKm(km) }
                        showMinTripDistanceDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showMinTripDistanceDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AppDiagnosticsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var adbCommand by rememberSaveable { mutableStateOf("dumpsys package ${context.packageName}") }
    var adbOutput by rememberSaveable { mutableStateOf("") }
    var adbRunning by remember { mutableStateOf(false) }
    val diagnostics by AppDiagnosticsMonitor.snapshot.collectAsState()
    val diagnosticsEnabled by AppDiagnosticsMonitor.enabled.collectAsState()

    LaunchedEffect(context) {
        AppDiagnosticsMonitor.initialize(context)
    }

    SectionHeader(icon = Icons.Filled.Memory, title = "App Diagnostics")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Show diagnostic details",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (diagnosticsEnabled) {
                            "Live process, memory, frame, permission, and ADB tools are visible."
                        } else {
                            "Off by default so this screen does not keep polling while you are just checking backups."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = diagnosticsEnabled,
                    onCheckedChange = { AppDiagnosticsMonitor.setEnabled(context, it) },
                    thumbContent = if (!diagnosticsEnabled) {
                        {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(ToggleUncheckedTrack, CircleShape)
                            )
                        }
                    } else null,
                    colors = SwitchDefaults.colors(
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = ToggleUncheckedTrack,
                        uncheckedBorderColor = ToggleUncheckedTrack
                    )
                )
            }

            // ── Share diagnostics log ──────────────────────────────────────────
            // Always available (even with live diagnostics off) so the persistent
            // diag.log — which records speed-stall and "telemetry refresh wedged"
            // events — can be sent from the head unit after parking, no PC needed.
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val text = withContext(Dispatchers.IO) {
                            runCatching {
                                val f = java.io.File(context.getExternalFilesDir(null), "diag.log")
                                if (!f.exists()) "diag.log is empty (no diagnostic events recorded yet)."
                                else {
                                    val bytes = f.readBytes()
                                    // Last ~96 KB is plenty and keeps the share payload small.
                                    val tail = if (bytes.size > 96_000)
                                        bytes.copyOfRange(bytes.size - 96_000, bytes.size) else bytes
                                    String(tail)
                                }
                            }.getOrElse { "Failed to read diag.log: ${it.message}" }
                        }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "BYD Trip Stats — diagnostics log")
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(send, "Share diagnostics log")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share diagnostics log")
            }
            Text(
                "Sends recent telemetry diagnostics (speed-stall and 'telemetry refresh wedged' events). Share it after parking to diagnose the speed-freeze without a computer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!diagnosticsEnabled) {
                Text(
                    "Enable this only when troubleshooting; the history charts sample every 2 seconds while visible.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {

                // ── Background readiness ────────────────────────────────────────
                SettingsGroupLabel("Background Readiness")
                val hasWriteSecureSettings = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.WRITE_SECURE_SETTINGS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasBackgroundLocation = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasReadLogs = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.READ_LOGS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                SettingsDetailRow(
                    "Write settings",
                    if (hasWriteSecureSettings) "✅ granted" else "⚠️ NOT granted — run install script"
                )
                SettingsDetailRow(
                    "Background location",
                    if (hasBackgroundLocation) "✅ granted" else "⚠️ NOT granted — run install script"
                )
                SettingsDetailRow(
                    "Read logs",
                    if (hasReadLogs) "✅ granted" else "⚠️ NOT granted"
                )
                SettingsDetailRow("Startup safeguards", "Applied automatically on app start")
                if (!hasWriteSecureSettings || !hasBackgroundLocation) {
                    androidx.compose.material3.Card(
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "⚠️ Background permissions not granted. " +
                                "Open the app fresh to trigger the ADB setup dialog",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                SettingsGroupLabel("Process")
                SettingsDetailRow("CPU", "%.1f%%".format(diagnostics.processCpuPercent))
                // System CPU hidden when unavailable (BYD DiLink restricts /proc/stat and /proc/loadavg)
                diagnostics.systemCpuPercent?.let {
                    SettingsDetailRow("System CPU", "%.1f%%".format(it))
                }
                SettingsDetailRow("PSS", "${diagnostics.pssMb} MB")
                SettingsDetailRow("Private dirty", "${diagnostics.rssMb} MB")
                SettingsDetailRow("Java heap", "${diagnostics.javaHeapMb} MB")
                SettingsDetailRow("Native heap", "${diagnostics.nativeHeapMb} MB")
                SettingsDetailRow(
                    "System memory",
                    "%.1f%% (%d / %d MB)".format(
                        diagnostics.systemMemoryUsedPercent,
                        diagnostics.systemMemoryUsedMb,
                        diagnostics.systemMemoryTotalMb
                    )
                )
                SettingsDetailRow("Threads", diagnostics.threadCount.toString())
                SettingsDetailRow("App uptime", "${diagnostics.uptimeMinutes} min")

                SettingsGroupLabel("60-second history")
            DiagnosticsHistoryChart(
                title = "CPU usage",
                leftLabel = "App",
                rightLabel = "System",
                leftColor = BydElectricAzure,
                rightColor = AccelerationOrange,
                timestampsMs = diagnostics.history.map { it.timestampMs },
                values = diagnostics.history.map { it.appCpuPercent },
                secondaryValues = diagnostics.history.map { it.systemCpuPercent ?: 0.0 },
                leftSuffix = "%",
                rightSuffix = "%"
            )
                DiagnosticsHistoryChart(
                    title = "Memory usage",
                leftLabel = "App PSS",
                rightLabel = "System",
                leftColor = BatteryBlue,
                rightColor = RegenGreen,
                timestampsMs = diagnostics.history.map { it.timestampMs },
                values = diagnostics.history.map { it.appMemoryMb },
                secondaryValues = diagnostics.history.map { it.systemMemoryPercent },
                leftSuffix = " MB",
                rightSuffix = "%",
                normalizeSeparately = true
                )

                SettingsGroupLabel("ADB shell")
                Text(
                    "Runs through the authorized local ADB daemon. Enter the command exactly as you would after `adb shell`.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = adbCommand,
                    onValueChange = { adbCommand = it },
                    label = { Text("Shell command") },
                    singleLine = false,
                    minLines = 1,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        adbRunning = true
                        adbOutput = "Running..."
                        scope.launch {
                            val result = AdbPermissionManager.runShellCommand(context, adbCommand)
                            adbOutput = "exit=${result.exitCode}\n${result.output.ifBlank { "(no output)" }}"
                            adbRunning = false
                        }
                    },
                    enabled = !adbRunning && adbCommand.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                ) {
                    Icon(Icons.Filled.Terminal, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (adbRunning) "Running..." else "Run shell command")
                }
                if (adbOutput.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    ) {
                        Text(
                            adbOutput.take(4000),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Text(
                    text = "Samples refresh every 2 seconds while this screen is open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsHistoryChart(
    title: String,
    leftLabel: String,
    rightLabel: String,
    leftColor: Color,
    rightColor: Color,
    timestampsMs: List<Long>,
    values: List<Double>,
    secondaryValues: List<Double>,
    leftSuffix: String,
    rightSuffix: String,
    normalizeSeparately: Boolean = false
) {
    val primaryLatest = values.lastOrNull()
    val secondaryLatest = secondaryValues.lastOrNull()
    val primaryMax = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val secondaryMax = (secondaryValues.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val sharedMax = primaryMax.coerceAtLeast(secondaryMax)
    val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    var touchPos by remember { mutableStateOf<Offset?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "last 60s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                DiagnosticsLegendItem(
                    color = leftColor,
                    label = "$leftLabel ${primaryLatest?.let { "%.1f".format(it) + leftSuffix } ?: "n/a"}"
                )
                DiagnosticsLegendItem(
                    color = rightColor,
                    label = "$rightLabel ${secondaryLatest?.let { "%.1f".format(it) + rightSuffix } ?: "n/a"}"
                )
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .pointerInput(timestampsMs, values, secondaryValues) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            touchPos = down.position
                            drag(down.id) { change ->
                                touchPos = change.position
                            }
                            touchPos = null
                        }
                    }
            ) {
                val padL = 48f
                val padR = 48f
                val padY = 8f
                val chartW = size.width - padL - padR
                val chartH = size.height - padY * 2
                val gridColor = Color.White.copy(alpha = 0.16f)
                val nc = drawContext.canvas.nativeCanvas
                val labelPaint = android.graphics.Paint().apply {
                    color = axisLabelColor.toArgb()
                    textSize = 19f
                    isAntiAlias = true
                }
                repeat(4) { index ->
                    val y = padY + chartH * index / 3f
                    drawLine(gridColor, Offset(padL, y), Offset(size.width - padR, y), 1f)
                }
                repeat(4) { index ->
                    val ratio = 1.0 - index / 3.0
                    val y = padY + chartH * index / 3f
                    val leftValue = (if (normalizeSeparately) primaryMax else sharedMax) * ratio
                    val rightValue = (if (normalizeSeparately) secondaryMax else sharedMax) * ratio
                    labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    nc.drawText(
                        "%.0f".format(leftValue),
                        padL - 8f,
                        y + 7f,
                        labelPaint
                    )
                    labelPaint.textAlign = android.graphics.Paint.Align.LEFT
                    nc.drawText(
                        "%.0f".format(rightValue),
                        size.width - padR + 8f,
                        y + 7f,
                        labelPaint
                    )
                }

                fun drawSeries(series: List<Double>, color: Color, strokeWidth: Float, maxForSeries: Double) {
                    if (series.size < 2) return
                    val lastIndex = (series.size - 1).coerceAtLeast(1)
                    series.zipWithNext().forEachIndexed { index, (a, b) ->
                        val x1 = padL + chartW * index / lastIndex.toFloat()
                        val x2 = padL + chartW * (index + 1) / lastIndex.toFloat()
                        val y1 = padY + chartH - ((a / maxForSeries).coerceIn(0.0, 1.0).toFloat() * chartH)
                        val y2 = padY + chartH - ((b / maxForSeries).coerceIn(0.0, 1.0).toFloat() * chartH)
                        drawLine(
                            color = color,
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }

                drawRect(
                    color = Color.White.copy(alpha = 0.08f),
                    topLeft = Offset(padL, padY),
                    size = androidx.compose.ui.geometry.Size(chartW, chartH),
                    style = Stroke(width = 1f)
                )
                drawSeries(
                    secondaryValues,
                    rightColor.copy(alpha = 0.85f),
                    3f,
                    if (normalizeSeparately) secondaryMax else sharedMax
                )
                drawSeries(
                    values,
                    leftColor,
                    4f,
                    if (normalizeSeparately) primaryMax else sharedMax
                )

                touchPos?.let { tp ->
                    if (tp.x in padL..(size.width - padR) && values.isNotEmpty()) {
                        val idx = if (values.size == 1) {
                            0
                        } else {
                            ((tp.x - padL) / chartW * (values.size - 1)).roundToInt().coerceIn(0, values.lastIndex)
                        }
                        val x = if (values.size == 1) padL + chartW / 2f else padL + chartW * idx / values.lastIndex.toFloat()
                        val y = padY + chartH - (
                            (values[idx] / if (normalizeSeparately) primaryMax else sharedMax)
                                .coerceIn(0.0, 1.0)
                                .toFloat() * chartH
                        )
                        val timeLabel = timestampsMs.getOrNull(idx)?.let { diagnosticsTimeFmt.format(Date(it)) } ?: "n/a"
                        drawDiagnosticsCrosshair(
                            cx = x,
                            cy = y,
                            w = size.width,
                            padL = padL,
                            padR = padR,
                            padT = padY,
                            chartH = chartH,
                            primaryLine = "$leftLabel ${"%.1f".format(values[idx])}$leftSuffix",
                            secondaryLine = "$rightLabel ${"%.1f".format(secondaryValues.getOrElse(idx) { 0.0 })}$rightSuffix",
                            timeLine = timeLabel,
                            accentColor = leftColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsLegendItem(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Canvas(modifier = Modifier.size(width = 22.dp, height = 4.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = size.height,
                cap = StrokeCap.Round
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun DrawScope.drawDiagnosticsCrosshair(
    cx: Float,
    cy: Float,
    w: Float,
    padL: Float,
    padR: Float,
    padT: Float,
    chartH: Float,
    primaryLine: String,
    secondaryLine: String,
    timeLine: String,
    accentColor: Color
) {
    val nc = drawContext.canvas.nativeCanvas
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f))

    drawLine(
        color = accentColor.copy(alpha = 0.7f),
        start = Offset(cx, padT),
        end = Offset(cx, padT + chartH),
        strokeWidth = 1.5f,
        pathEffect = dashEffect
    )
    drawLine(
        color = accentColor.copy(alpha = 0.5f),
        start = Offset(padL, cy),
        end = Offset(w - padR, cy),
        strokeWidth = 1.2f,
        pathEffect = dashEffect
    )

    drawCircle(accentColor.copy(alpha = 0.25f), 10f, Offset(cx, cy))
    drawCircle(accentColor, 5f, Offset(cx, cy))
    drawCircle(Color.White, 2.5f, Offset(cx, cy))

    val titlePaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = Color.White.toArgb()
        textSize = 17f
        isFakeBoldText = true
    }
    val bodyPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = Color.White.copy(alpha = 0.92f).toArgb()
        textSize = 15f
    }
    val metaPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = Color.White.copy(alpha = 0.78f).toArgb()
        textSize = 13f
    }

    val paddingH = 10f
    val paddingV = 8f
    val lineGap = 4f
    val titleH = titlePaint.descent() - titlePaint.ascent()
    val bodyH = bodyPaint.descent() - bodyPaint.ascent()
    val metaH = metaPaint.descent() - metaPaint.ascent()
    val boxW = maxOf(
        titlePaint.measureText(primaryLine),
        bodyPaint.measureText(secondaryLine),
        metaPaint.measureText(timeLine)
    ) + paddingH * 2
    val boxH = titleH + bodyH + metaH + lineGap * 2 + paddingV * 2

    val desiredX = if (cx + 12f + boxW < w - padR) cx + 12f else cx - 12f - boxW
    val tooltipX = desiredX.coerceIn(padL, (w - padR - boxW).coerceAtLeast(padL))
    val tooltipY = (padT + 6f).coerceAtLeast(0f)

    val bgPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = accentColor.copy(alpha = 0.94f).toArgb()
        style = android.graphics.Paint.Style.FILL
    }
    nc.drawRoundRect(
        tooltipX,
        tooltipY,
        tooltipX + boxW,
        tooltipY + boxH,
        10f,
        10f,
        bgPaint
    )

    var textY = tooltipY + paddingV - titlePaint.ascent()
    nc.drawText(primaryLine, tooltipX + paddingH, textY, titlePaint)
    textY += titleH + lineGap
    nc.drawText(secondaryLine, tooltipX + paddingH, textY, bodyPaint)
    textY += bodyH + lineGap
    nc.drawText(timeLine, tooltipX + paddingH, textY, metaPaint)
}

@Composable
private fun DirectBydTelemetryCard(
    snapshot: VehicleTelemetrySnapshot?,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.SettingsRemote, null, tint = BydElectricAzure, modifier = Modifier.size(22.dp))
                Text("Direct BYD", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (snapshot == null) {
                Text(
                    "Waiting for direct BYD probes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SettingsGroupLabel("Remote-only")
                SettingsDetailRow(
                    "Battery remain",
                    snapshot.powerBatteryRemainPowerEV?.let { "%.1f kWh".format(it) } ?: "n/a"
                )
                SettingsDetailRow(
                    "Last 50 km",
                    snapshot.instrumentLast50KmPowerConsume?.let { "%.1f kWh".format(it) } ?: "n/a"
                )
                SettingsDetailRow(
                    "Outside temp",
                    snapshot.instrumentOutCarTemperature?.let { "$it°C" } ?: "n/a"
                )
                SettingsDetailRow(
                    "VIN",
                    snapshot.bodyworkAutoVin ?: "n/a"
                )
                SettingsDetailRow(
                    "Seatbelt",
                    "D=${directIntOrNA(snapshot.instrumentSafetyBeltDriverStatus)}, " +
                        "P=${directIntOrNA(snapshot.instrumentSafetyBeltPassengerStatus)}"
                )
                HorizontalDivider()
                SettingsGroupLabel("Minor-drive")
                SettingsDetailRow("Speed", "%.1f km/h".format(snapshot.directSpeedKmh))
                SettingsDetailRow(
                    "Gear",
                    snapshot.gear
                )
                SettingsDetailRow(
                    "Pedals",
                    "A=${directIntOrNA(snapshot.speedAccelerateDeepness)}, " +
                        "B=${directIntOrNA(snapshot.speedBrakeDeepness)}, " +
                        "brake=${directIntOrNA(snapshot.gearboxBrakePedalState)}"
                )
                SettingsDetailRow(
                    "Signals",
                    "flash=${directIntOrNA(snapshot.turnSignalFlashState)}, " +
                        "L=${directBoolOrNA(snapshot.turnSignalLeft)}, " +
                        "R=${directBoolOrNA(snapshot.turnSignalRight)}"
                )
                SettingsDetailRow(
                    "Trip",
                    "avg=${snapshot.instrumentAverageSpeed?.let { "%.1f km/h".format(it) } ?: "n/a"}, " +
                        "journey=${snapshot.instrumentCurrentJourneyDriveMileage?.let { "%.1f km".format(it) } ?: "n/a"}, " +
                        "time=${snapshot.instrumentCurrentJourneyDriveTime?.let { "%.1f".format(it) } ?: "n/a"}"
                )
                HorizontalDivider()
                Text(
                    "Debug",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SettingsDetailRow(
                    "Bodywork",
                    "auto=${directIntOrNA(snapshot.bodyworkAutoSystemState)}, power=${directIntOrNA(snapshot.bodyworkPowerLevel)}"
                )
                SettingsDetailRow(
                    "Battery dbg",
                    "capacity=${directIntOrNA(snapshot.bodyworkBatteryCapacity)}, " +
                        "hev=${snapshot.bodyworkBatteryPowerHEV ?: "n/a"}, " +
                        "value=${directIntOrNA(snapshot.bodyworkBatteryPowerValue)}, " +
                        "voltageLevel=${directIntOrNA(snapshot.bodyworkBatteryVoltageLevel)}"
                )
                SettingsDetailRow("Sensor", "temp=${snapshot.sensorTemperatureValue ?: "n/a"}")

                if (snapshot.probeValues.isNotEmpty()) {
                    HorizontalDivider()
                    SettingsGroupLabel("Probe values")
                    snapshot.probeValues.toSortedMap().forEach { (key, value) ->
                        SettingsDetailRow(
                            formatProbeLabel(key),
                            directProbeValue(value, key)
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Refresh direct snapshot", fontSize = 16.sp)
            }
        }
    }
}

private fun directIntOrNA(value: Int?): String {
    return when (value) {
        null -> "n/a"
        65535, -2147482645 -> "n/a"
        else -> {
            if (value <= -10000) "n/a" else value.toString()
        }
    }
}

private fun directBoolOrNA(value: Boolean?): String {
    return when (value) {
        null -> "n/a"
        true -> "on"
        false -> "off"
    }
}

private fun directProbeValue(value: Double, key: String): String {
    val suffix = when {
        key.endsWith("_kw") -> " kW"
        key.endsWith("_pct") -> "%"
        key.endsWith("_c") -> "°C"
        else -> ""
    }
    return String.format("%.2f%s", value, suffix)
}

private fun formatProbeLabel(key: String): String {
    return key.split('_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.replaceFirstChar { ch -> ch.uppercase() }
        }
}

@Composable
private fun VehicleSnapshotCard(
    telemetry: VehicleTelemetry?,
    snapshot: VehicleTelemetrySnapshot?
) {
    val chargingLabel = if (snapshot == null) "Waiting for vehicle data" else {
        val hrs = snapshot.remainHours
        val mins = snapshot.remainMinutes
        if (hrs > 0 || mins > 0) "${hrs}h ${mins}m"
        else "n/a"
    }
    val chargingPowerKw = telemetry?.chargingPower ?: 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.DirectionsCar, null, tint = BydElectricAzure, modifier = Modifier.size(22.dp))
                Text("Vehicle Snapshot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (snapshot == null) {
                Text(
                    "Vehicle data is not connected yet. Start the app or wake the car to populate this card.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SettingsDetailRow("Gear", snapshot.gear)
                SettingsDetailRow("Charging", "%.1f kW".format(chargingPowerKw))
                SettingsDetailRow("Charging energy", "%.3f kWh".format(snapshot.chargingCapacity))
                SettingsDetailRow("Charge active", if (snapshot.isChargingActive) "yes" else "no")
                SettingsDetailRow(
                    "Charge cap",
                    "state ${snapshot.chargingCapState}, value ${snapshot.chargingCapValue}"
                )
                SettingsDetailRow("Charge time", chargingLabel)
                SettingsDetailRow(
                    "Tyres",
                    "LF ${directTyrePsiOrNA(snapshot.tyrePressureLFPsi, snapshot.tyrePressureLFState)} | " +
                        "RF ${directTyrePsiOrNA(snapshot.tyrePressureRFPsi, snapshot.tyrePressureRFState)} | " +
                        "LR ${directTyrePsiOrNA(snapshot.tyrePressureLRPsi, snapshot.tyrePressureLRState)} | " +
                        "RR ${directTyrePsiOrNA(snapshot.tyrePressureRRPsi, snapshot.tyrePressureRRState)}"
                )
                SettingsDetailRow(
                    "States",
                    "charger ${snapshot.chargerState}/${snapshot.chargerWorkState}, " +
                        "gun ${snapshot.chargingGunState}, type ${snapshot.chargingType}, mode ${snapshot.chargingMode}, " +
                        "cap ${snapshot.chargingCapacity}"
                )
            }
        }
    }
}

@Composable
private fun TelemetryComparisonCard(
    telemetry: VehicleTelemetry?,
    snapshot: VehicleTelemetrySnapshot?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.Timeline, null, tint = BydElectricAzure, modifier = Modifier.size(22.dp))
                Text("Telemetry Compare", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                "Auto-refreshes while the App tab is open.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (telemetry == null && snapshot == null) {
                Text(
                    "Waiting for live telemetry and vehicle snapshot.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SettingsGroupLabel("Minor-drive")
                SettingsDetailRow(
                    "Gear",
                    liveVsCar(
                        telemetry?.gear,
                        snapshot?.gear
                    )
                )
                SettingsDetailRow(
                    "Speed",
                    liveVsCar(
                        telemetry?.let { "%.1f km/h".format(it.speed) },
                        snapshot?.let { "%.1f km/h".format(it.directSpeedKmh) }
                    )
                )
                SettingsDetailRow(
                    "Pedals",
                    "Live: n/a   |   Car: A=${snapshot?.speedAccelerateDeepness?.toString() ?: "n/a"}, " +
                        "B=${snapshot?.speedBrakeDeepness?.toString() ?: "n/a"}, " +
                        "brake=${snapshot?.gearboxBrakePedalState?.toString() ?: "n/a"}"
                )
                SettingsDetailRow(
                    "Trip",
                    "Live: n/a   |   Car: avg=${snapshot?.instrumentAverageSpeed?.let { "%.1f".format(it) } ?: "n/a"}, " +
                        "journey=${snapshot?.instrumentCurrentJourneyDriveMileage?.let { "%.1f".format(it) } ?: "n/a"}, " +
                        "time=${snapshot?.instrumentCurrentJourneyDriveTime?.let { "%.1f".format(it) } ?: "n/a"}"
                )
                HorizontalDivider()
                SettingsGroupLabel("Remote-only")
                SettingsDetailRow(
                    "Battery remain (kWh)",
                    "Live: n/a   |   Car: ${snapshot?.powerBatteryRemainPowerEV?.let { "%.1f kWh".format(it) } ?: "n/a"}"
                )
                SettingsDetailRow(
                    "Outside temp",
                    "Live: n/a   |   Car: ${snapshot?.instrumentOutCarTemperature?.let { "${it}°C" } ?: "n/a"}"
                )
                SettingsDetailRow(
                    "VIN",
                    "Live: n/a   |   Car: ${snapshot?.bodyworkAutoVin ?: "n/a"}"
                )
                SettingsDetailRow(
                    "Seatbelt",
                    "Live: n/a   |   Car: D=${snapshot?.instrumentSafetyBeltDriverStatus?.toString() ?: "n/a"}, " +
                        "P=${snapshot?.instrumentSafetyBeltPassengerStatus?.toString() ?: "n/a"}"
                )
                SettingsDetailRow(
                    "Signals",
                    "Live: n/a   |   Car: flash=${snapshot?.turnSignalFlashState?.toString() ?: "n/a"}, " +
                        "L=${directBoolOrNA(snapshot?.turnSignalLeft)}, " +
                        "R=${directBoolOrNA(snapshot?.turnSignalRight)}"
                )
                SettingsDetailRow(
                    "Tyres",
                    liveVsCar(
                        telemetry?.let {
                            "LF %.1f | RF %.1f | LR %.1f | RR %.1f psi".format(
                                it.tyrePressureLF,
                                it.tyrePressureRF,
                                it.tyrePressureLR,
                                it.tyrePressureRR
                            )
                        },
                        snapshot?.let {
                            "LF ${directTyrePsiOrNA(it.tyrePressureLFPsi, it.tyrePressureLFState)} | " +
                                "RF ${directTyrePsiOrNA(it.tyrePressureRFPsi, it.tyrePressureRFState)} | " +
                                "LR ${directTyrePsiOrNA(it.tyrePressureLRPsi, it.tyrePressureLRState)} | " +
                                "RR ${directTyrePsiOrNA(it.tyrePressureRRPsi, it.tyrePressureRRState)}"
                        }
                    )
                )
            }
        }
    }
}

private fun directTyrePsiOrNA(psi: Double, state: Int?): String {
    return if (state != null && state != 0) "n/a" else if (psi > 0.0) "%.1f psi".format(psi) else "n/a"
}

private fun liveVsCar(live: String?, car: String?): String {
    val left = live?.takeIf { it.isNotBlank() } ?: "n/a"
    val right = car?.takeIf { it.isNotBlank() } ?: "n/a"
    return "Live: $left   |   Car: $right"
}

@Composable
private fun CoreTelemetryCard(telemetry: VehicleTelemetry?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.Dashboard, null, tint = BydElectricAzure, modifier = Modifier.size(22.dp))
                Text("Core Telemetry", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (telemetry == null) {
                Text(
                    "Waiting for live telemetry.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SettingsGroupLabel("Core")
                SettingsDetailRow("SoC", "%.1f%%".format(telemetry.soc))
                SettingsDetailRow("SoC panel", "${telemetry.socPanel}%")
                SettingsDetailRow("Car on", telemetry.isCarOn.toString())
                SettingsDetailRow("Locked", telemetry.carLocked.toString())
                SettingsDetailRow("Door open", telemetry.anyDoorOpened.toString())
                SettingsDetailRow("Gear", telemetry.gear)
                SettingsDetailRow("Speed", "%.1f km/h".format(telemetry.speed))
                SettingsDetailRow("Odometer", "%.1f km".format(telemetry.odometer))
                SettingsDetailRow("Engine power", "${telemetry.enginePower} kW")
                SettingsDetailRow("Total discharge", "%.1f".format(telemetry.totalDischarge))
                SettingsDetailRow("Charging", "%.1f kW".format(telemetry.chargingPower))
                SettingsDetailRow("Electric range", "${telemetry.electricDrivingRangeKm} km")
                SettingsDetailRow("Fuel range", "${telemetry.fuelDrivingRangeKm} km")
                SettingsDetailRow("Fuel level", "${telemetry.fuelPercentage}%")
                SettingsDetailRow("Engine front", "${telemetry.engineSpeedFront} rpm")
                SettingsDetailRow("Engine rear", "${telemetry.engineSpeedRear} rpm")
                SettingsDetailRow("12V", "%.1f V".format(telemetry.battery12vVoltage))
                SettingsDetailRow("Battery", "%.1f V".format(telemetry.batteryTotalVoltage.toDouble()))
                SettingsDetailRow(
                    if (telemetry.sohEstimated) "Estimated SoH" else "SOH",
                    telemetry.soh.takeIf { it in 1..100 }?.let { "$it%" } ?: "—"
                )
                SettingsDetailRow(
                    "Battery temp",
                    "max=${telemetry.batteryCellTempMax}°C, min=${telemetry.batteryCellTempMin}°C, avg=${"%.1f".format(telemetry.batteryTempAvg)}°C"
                )
                SettingsDetailRow(
                    "Battery cells",
                    "Vmax ${"%.3f".format(telemetry.batteryCellVoltageMax)} / Vmin ${"%.3f".format(telemetry.batteryCellVoltageMin)}"
                )
                SettingsGroupLabel("Battery")
                SettingsDetailRow("Cell V max", "%.3f V".format(telemetry.batteryCellVoltageMax))
                SettingsDetailRow("Cell V min", "%.3f V".format(telemetry.batteryCellVoltageMin))
                SettingsDetailRow("Current date", if (telemetry.currentDatetime.isBlank()) "n/a" else telemetry.currentDatetime)
                SettingsDetailRow(
                    "Date / location",
                    if (telemetry.currentDatetime.isNotBlank()) {
                        "${telemetry.currentDatetime}, %.5f, %.5f @ %.0f m".format(
                            telemetry.locationLatitude,
                            telemetry.locationLongitude,
                            telemetry.locationAltitude
                        )
                    } else {
                        "%.5f, %.5f @ %.0f m".format(
                            telemetry.locationLatitude,
                            telemetry.locationLongitude,
                            telemetry.locationAltitude
                        )
                    }
                )
                SettingsDetailRow("Wi-Fi", if (telemetry.wifiSsid.isBlank()) "n/a" else telemetry.wifiSsid)

                SettingsGroupLabel("Vehicle overlay")
                SettingsDetailRow(
                    "Battery remain (kWh)",
                    telemetry.batteryRemainPowerEV?.let { "%.1f kWh".format(it) } ?: "n/a"
                )
                SettingsDetailRow(
                    "Avg speed",
                    telemetry.averageSpeed?.let { "%.1f km/h".format(it) } ?: "n/a"
                )
                SettingsDetailRow(
                    "Outside temp",
                    telemetry.instrumentOutCarTemperature?.let { "${it}°C" } ?: "n/a"
                )
                SettingsDetailRow(
                    "Seatbelt",
                    "D=${telemetry.instrumentSafetyBeltDriverStatus?.toString() ?: "n/a"}, " +
                        "P=${telemetry.instrumentSafetyBeltPassengerStatus?.toString() ?: "n/a"}"
                )
                SettingsDetailRow(
                    "Signals",
                    "flash=${telemetry.turnSignalFlashState?.toString() ?: "n/a"}, " +
                        "L=${telemetry.turnSignalLeft?.toString() ?: "n/a"}, " +
                        "R=${telemetry.turnSignalRight?.toString() ?: "n/a"}"
                )
            }
        }
    }
}

@Composable
private fun BackupSummaryCard(
    modifier  : Modifier,
    icon      : ImageVector,
    title     : String,
    body      : String,
    statusLine: String,
    statusOk  : Boolean
) {
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier            = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                statusLine,
                style      = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color      = if (statusOk) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Tab 4 — About & FAQ
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AboutTab(viewModel: DashboardViewModel) {
    val updateInfo        by viewModel.updateInfo.collectAsState()
    val downloadProgress  by viewModel.downloadProgress.collectAsState()
    val downloadedApk     by viewModel.downloadedApk.collectAsState()
    val canInstallNow     by viewModel.canInstallNow.collectAsState()
    val isCheckingUpdate  by viewModel.isCheckingUpdate.collectAsState()

    var easterEggClicks by remember { mutableStateOf(0) }
    var licenseClicks by remember { mutableStateOf(0) }
    var showChangelogDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showChangelogDialog) {
        val changelogText = remember {
            runCatching {
                context.assets.open("changelog.md").bufferedReader().readText()
            }.getOrDefault("Changelog not available.")
        }
        AlertDialog(
            onDismissRequest = { showChangelogDialog = false },
            containerColor   = MaterialTheme.colorScheme.surfaceVariant,
            title = {
                Text("Changelog", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text  = changelogText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showChangelogDialog = false }) { Text("Close") }
            }
        )
    }

    val hasBackgroundLocation = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(icon = Icons.Filled.Info, title = "About")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsDetailRow("App",       "BYD Trip Stats")
                SettingsDetailRow("Version",   com.byd.tripstats.BuildConfig.VERSION_NAME)
                SettingsDetailRow("Changelog", "What's new", onClick = { showChangelogDialog = true }, showClickIndicator = true)
                SettingsDetailRow("Author",    "Angelos Oikonomou (angoikon)")
                SettingsDetailRow(
                    label = "Platform",
                    value = "Android 10 · API 29",
                    onClick = {
                        easterEggClicks++
                        if (easterEggClicks >= 5) {
                            easterEggClicks = 0
                            try {
                                val intent = Intent().apply {
                                    setClassName("com.byd.countrycodetool", "com.byd.countrycodetool.MainActivity")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    Runtime.getRuntime().exec(arrayOf("am", "start", "-n", "com.byd.countrycodetool/com.byd.countrycodetool.MainActivity"))
                                } catch (e2: Exception) {
                                    android.widget.Toast.makeText(context, "Could not open tool", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
                SettingsDetailRow(
                    label = "License",
                    value = "BUSL 1.1",
                    onClick = {
                        licenseClicks++
                        if (licenseClicks >= 5) {
                            licenseClicks = 0
                            try {
                                val intent = Intent().apply {
                                    setClassName("com.byd.byddevelopmenttools", "com.byd.byddevelopmenttools.VerificationActivity")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    Runtime.getRuntime().exec(arrayOf("am", "start", "-n", "com.byd.byddevelopmenttools/com.byd.byddevelopmenttools.VerificationActivity"))
                                } catch (e2: Exception) {
                                    android.widget.Toast.makeText(context, "Could not open tool", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
                SettingsDetailRow("Github",      "github.com/angoikon/byd-trip-stats",
                    url = "https://github.com/angoikon/byd-trip-stats")
                SettingsDetailRow("Discord", "Join now", url = "https://discord.gg/pf8TjjTce9")
                if (!hasBackgroundLocation) {
                    SettingsDetailRow(
                        label = "Background operation",
                        value = "⚠️ ADB setup required — open app to trigger setup dialog"
                    )
                }
            }
        }

        UpdateCard(
            updateInfo       = updateInfo,
            downloadProgress = downloadProgress,
            downloadedApk    = downloadedApk,
            canInstallNow    = canInstallNow,
            isChecking       = isCheckingUpdate,
            onDownload       = { viewModel.downloadUpdate() },
            onInstall        = { viewModel.installUpdate() },
            onCancel         = { viewModel.cancelDownload() },
            onCheckNow       = { viewModel.checkForUpdateManually() }
        )

        HorizontalDivider()
        SectionHeader(icon = Icons.AutoMirrored.Filled.Help, title = "FAQ")

        buildFaqList().forEach { (q, a, u) -> FaqItem(question = q, answer = a, url = u) }

        Spacer(Modifier.height(8.dp))
    }
}

// ── Update card ───────────────────────────────────────────────────────────────

@Composable
private fun UpdateCard(
    updateInfo      : com.byd.tripstats.data.repository.UpdateRepository.UpdateInfo?,
    downloadProgress: Int?,
    downloadedApk   : java.io.File?,
    canInstallNow   : Boolean,
    isChecking      : Boolean = false,
    onDownload      : () -> Unit,
    onInstall       : () -> Unit,
    onCancel        : () -> Unit,
    onCheckNow      : () -> Unit = {}
) {
    val isDownloading = downloadProgress != null && downloadProgress in 0..99
    val isReady       = downloadedApk != null && downloadProgress == 100

    if (updateInfo == null && !isDownloading && !isReady) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier              = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.CheckCircle, null, tint = RegenGreen, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        if (isChecking) "Checking for updates…" else "App is up to date",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (!isChecking) {
                    TextButton(onClick = onCheckNow, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("Check now", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = when {
                isReady            -> RegenGreen.copy(alpha = 0.15f)
                updateInfo != null -> AccelerationOrange.copy(alpha = 0.12f)
                else               -> MaterialTheme.colorScheme.primaryContainer
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                isReady            -> RegenGreen.copy(alpha = 0.5f)
                updateInfo != null -> AccelerationOrange.copy(alpha = 0.4f)
                else               -> MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        isReady       -> Icons.Filled.SystemUpdate
                        isDownloading -> Icons.Filled.Downloading
                        else          -> Icons.Filled.NewReleases
                    },
                    contentDescription = null,
                    tint     = when {
                        isReady       -> RegenGreen
                        isDownloading -> BydElectricAzure
                        else          -> AccelerationOrange
                    },
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        when {
                            isReady       -> "v${updateInfo?.latestVersion} ready to install"
                            isDownloading -> "Downloading v${updateInfo?.latestVersion}…"
                            else          -> "Update available: v${updateInfo?.latestVersion}"
                        },
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (updateInfo != null && !isDownloading && !isReady) {
                        Text(
                            "Current: v${updateInfo.currentVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isDownloading) {
                val animatedProgress by animateFloatAsState(
                    targetValue   = (downloadProgress ?: 0) / 100f,
                    animationSpec = tween(300),
                    label         = "updateProgress"
                )
                LinearProgressIndicator(
                    progress   = { animatedProgress },
                    modifier   = Modifier.fillMaxWidth(),
                    color      = BydElectricAzure,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                Text(
                    "${downloadProgress}%  ·  ${updateInfo?.apkName ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (updateInfo != null && !isDownloading && !isReady && updateInfo.releaseNotes.isNotBlank()) {
                Text(
                    updateInfo.releaseNotes,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            if (isReady && !canInstallNow) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Info, null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp))
                    Text(
                        "Install button will become available when parked with no active trip or charging",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    isReady -> {
                        Button(
                            onClick  = onInstall,
                            enabled  = canInstallNow,
                            colors   = ButtonDefaults.buttonColors(containerColor = RegenGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.InstallMobile, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Install now")
                        }
                    }
                    isDownloading -> {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                            Text("Cancel")
                        }
                    }
                    else -> {
                        Button(
                            onClick  = onDownload,
                            colors   = ButtonDefaults.buttonColors(containerColor = BydElectricAzure),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download update")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String, url: String? = null) {
    val context  = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                MaterialTheme.shapes.medium
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    question,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.weight(1f).padding(end = 8.dp)
                )
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))
                    Text(answer, style = MaterialTheme.typography.bodyMedium)
                    if (url != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .clickable {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    )
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector        = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open link",
                                tint               = MaterialTheme.colorScheme.primary,
                                modifier           = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                url.removePrefix("https://").removePrefix("http://"),
                                style      = MaterialTheme.typography.bodySmall,
                                color      = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class FaqEntry(val question: String, val answer: String, val url: String? = null)

private fun buildFaqList(): List<FaqEntry> = listOf(

    FaqEntry(
        "Contrary to older versions, is it true that I no longer need the Electro app?",
        "Correct. BYD Trip Stats works as a standalone app on supported vehicles, so " +
        "trip recording and charging sessions work without Electro.\n\n" +
        "Electro is still a useful companion app for owners who want its own " +
        "features, but it is no longer required for BYD Trip Stats to function."
    ),

    FaqEntry(
        "Do I need internet or mobile signal?",
        "No for normal use. Trips, charging sessions, charts, and local history are recorded " +
        "entirely on-device.\n\n" +
        "Internet is only needed for optional features such as app update checks, opening web " +
        "links, MQTT / ABRP integration and Telegram backups if you enable them."
    ),

    FaqEntry(
        "Why is 'auto/unknown' shown at driving / regen modes?",
        "The app reads drive and regen modes directly from the car's instrument cluster, but the " +
        "car only broadcasts a mode value when you actively change it — it does not report the " +
        "current mode on startup.\n\n" +
        "Until you tap a mode on the car display, the app has no value to show and falls back to " +
        "'Auto' (regen) or 'Unknown' (drive). To fix this:\n\n" +
        "1. Cycle through each regen level on the car's touchscreen once\n" +
        "2. Cycle through each drive mode (Eco / Normal / Sport) once\n\n" +
        "The app will immediately pick up both values and start recording them with every trip data point. " +
        "You only need to do this once — after that, changing modes during normal driving keeps everything in sync."
    ),

    FaqEntry(
        "No network connectivity means trip will be missing from the history?",
        "No. Trip history is stored locally and does not depend on mobile signal or Wi-Fi.\n\n" +
        "A trip may close a little later only if live telemetry itself stops for a while. In that " +
        "case the watchdog closes the active trip after 3 minutes of telemetry silence, anchored " +
        "to the last packet the app received."
    ),

    FaqEntry(
        "The app stops working after a car restart",
        "You need to exclude BYD Trip Stats from the autostart-killer:\n\n" +
        "1. Open the Disable Autostart app (native BYD app, usually near the file explorer)\n" +
        "2. Find BYD Trip Stats and toggle its entry OFF — same as you have done for Electro\n" +
        "3. Open the app and hold the volume button for ~10 seconds to restart the car UI\n" +
        "4. Reopen the app\n\n" +
        "Important: you must repeat this step after every app update, because the permission " +
        "resets on reinstall."
    ),

    FaqEntry(
        "How do I back up my trip data?",
        "Three methods are available — all produce the same standard SQLite file:\n\n" +
        "Local (simplest — no setup): Settings → Data → Open Backup & Restore → Backup Now. " +
        "The file lands in Download/BydTripStats/ on the car's internal storage.\n\n" +
        "Telegram (recommended): Connect a bot once via Backup & Restore → Telegram Backup, " +
        "then send manually or enable scheduled backups (daily / weekly / monthly). " +
        "Backups appear in your private Telegram chat and can be restored directly from any device.\n\n" +
        "ADB (technical): Every in-app backup also writes a copy to the app's private directory. " +
        "Pull it wirelessly with:\n" +
        "adb shell run-as com.byd.tripstats cat files/db_backup/FILENAME.db > local.db\n\n" +
        "The private directory keeps the 5 most recent backups and prunes older ones automatically."
    ),

    FaqEntry(
        "I reinstalled the app — can I recover my backups?",
        "Yes, for both backup methods:\n\n" +
        "Telegram: After reinstalling, reconnect your bot token in Backup & Restore → " +
        "Telegram Backup. Then open Restore from Telegram and tap the refresh icon. " +
        "The app reads Download/BydTripStats/telegram_registry.json — which survives " +
        "uninstalls — and rediscovers all previous backups automatically.\n\n" +
        "Local: The .db files in Download/BydTripStats/ survive a reinstall. Open " +
        "Backup & Restore, tap the refresh icon under Local Restore, and they reappear " +
        "in the list. The app now reads that folder directly from disk for exactly this case.\n\n" +
        "As long as Download/BydTripStats/ has not been manually deleted, no data is lost."
    ),

    FaqEntry(
        "How does Telegram automatic backup work?",
        "Once your bot is connected, enable the Auto-backup toggle and pick an interval: " +
        "Daily, Weekly, or Monthly.\n\n" +
        "Key behaviours:\n" +
        "• The first automatic backup runs after one full interval has elapsed — enabling " +
        "the toggle does not send immediately\n" +
        "• Changing the interval reschedules from that moment forward\n" +
        "• If the car is off when a backup is due, it fires automatically on the next boot\n" +
        "• Failed attempts are retried with exponential backoff before giving up until the next window\n\n" +
        "The 'Last auto-backup' timestamp in the Telegram section confirms when the most recent run completed.\n\n" +
        "To stop: tap Disconnect bot. This cancels the schedule and clears saved credentials. " +
        "Your previous backups in Telegram and the registry file in Download are unaffected. Backups up to 50MB of database per file"
    ),

    FaqEntry(
        "How do I restore a backup pushed from my PC via ADB?",
        "1. Push the file to the car's Download folder:\n" +
        "   adb push backup.db /sdcard/Download/BydTripStats/backup.db\n\n" +
        "2. Open the app → Backup & Restore → Local Restore\n" +
        "3. Tap the refresh icon — the pushed file appears in the list\n" +
        "4. Tap Restore and confirm\n\n" +
        "The app validates the file as a genuine SQLite database before touching any live data. " +
        "It then restores and restarts automatically."
    ),

    FaqEntry(
        "Will this drain my 12V battery?",
        "No meaningful impact. In an EV the 12V battery is continuously trickle-charged from " +
        "the high-voltage pack. BYD Trip Stats only keeps a lightweight foreground service alive " +
        "to read telemetry and write local trip data. If the car is offline, it is used for recording " +
        "charging sessions and 12V/HV curves - you can also disable this feature if you don't want it"
    ),

    FaqEntry(
        "Can I export data to Excel?",
        "Yes. Open any trip, tap the share icon (top-right), then Save as CSV. The file lands " +
        "in the Download folder and can be opened directly in Excel, Google Sheets, or any " +
        "data tool.\n\nA JSON export is also available from the same menu. Both formats include " +
        "every recorded data point: timestamp, GPS, speed, power, SOC, altitude, battery " +
        "temperature, gear, and motor RPM (front and rear)."
    ),

    FaqEntry(
        "Is my data private?",
        "Completely. BYD Trip Stats collects zero information about you.\n\n" +
        "Everything stays on your device: trip data, GPS coordinates, backups, and settings. " +
        "The only optional external traffic is what you explicitly trigger, such as Telegram " +
        "backup uploads, checking for a newer app release, enabling MQTT integration and sending debug probes."
    ),

    FaqEntry(
        "Where can I get help, request a feature or report a bug?",
        "Check the README.md file at my Github, open an issue there or visit the discord server — tap the link below.\n\n" +
        "Don't forget to include your BYD model and the steps to reproduce the problem. ",
        url = "https://discord.gg/pf8TjjTce9"
    )
)

// ═══════════════════════════════════════════════════════════════════════════════
// Vehicle Compatibility Probe Section (App tab)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun VehicleCompatibilitySection(context: Context, scope: CoroutineScope) {
    val isEnabled by VehicleCompatibilityProbe.isEnabled.collectAsState()
    val entryCount by VehicleCompatibilityProbe.entryCount.collectAsState()
    val lastCapture by VehicleCompatibilityProbe.lastCaptureAt.collectAsState()
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var privacyExpanded by rememberSaveable { mutableStateOf(false) }
    val telegram = remember { TelegramManager.getInstance(context) }
    val telegramConfig by telegram.config.collectAsState()
    val telegramState by telegram.state.collectAsState()

    SectionHeader(icon = Icons.Filled.BugReport, title = "Vehicle Compatibility")

    Text(
        "Help support for your BYD model by sharing a raw telemetry probe report. " +
        "Enable probing, drive for a couple of minutes, then export and share the file.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // Privacy banner
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { privacyExpanded = !privacyExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Filled.PrivacyTip, null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Privacy notice",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Icon(
                    if (privacyExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    null, modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            AnimatedVisibility(visible = privacyExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Text(
                    "The report captures raw values from every data source on " +
                    "your car's head unit — numeric values, strings, and arrays.\n\n" +
                    "GPS latitude and longitude are NOT included.\n\n" +
                    "Everything else is included: battery state, motor data, gear, speed, " +
                    "drive modes, climate state, charging state, PHEV-specific values, etc.\n\n" +
                    "The report stays on your device until you send it. Nothing is sent automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }

    // Enable toggle row
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Enable compatibility probing",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (isEnabled)
                            "Active — $entryCount fields captured${lastCapture?.let { " · last: ${it.substringBefore('T')}" } ?: ""}"
                        else
                            "Off — enable, drive briefly, then export.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { next ->
                        VehicleCompatibilityProbe.setEnabled(next)
                    },
                    thumbContent = if (!isEnabled) {
                        {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(ToggleUncheckedTrack, CircleShape)
                            )
                        }
                    } else null,
                    colors = SwitchDefaults.colors(
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = ToggleUncheckedTrack,
                        uncheckedBorderColor = ToggleUncheckedTrack
                    )
                )
            }

            // Action buttons
            val isSending = telegramState is TelegramManager.TelegramState.InProgress
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (telegramConfig == null) {
                            statusMessage = "Telegram not configured — set it up under Backup & Restore first."
                            return@Button
                        }
                        scope.launch(Dispatchers.IO) {
                            try {
                                val file = VehicleCompatibilityProbe.exportReportFile()
                                telegram.sendFile(
                                    file,
                                    caption = "BYD Trip Stats — compatibility probe report\n" +
                                        "Entries: $entryCount · ${lastCapture?.substringBefore('T') ?: "no date"}"
                                )
                                launch(Dispatchers.Main) {
                                    statusMessage = "Sent via Telegram ✓"
                                }
                            } catch (e: Exception) {
                                launch(Dispatchers.Main) {
                                    statusMessage = "Telegram send failed: ${e.message}"
                                }
                            }
                        }
                    },
                    enabled = entryCount > 0 && !isSending,
                    modifier = Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (isSending) "Sending…" else "Telegram")
                }

                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                VehicleCompatibilityProbe.exportReportFile(saveToDownloads = true)
                                launch(Dispatchers.Main) {
                                    statusMessage = "Saved to Download/BydTripStats/compat_probe.json"
                                }
                            } catch (e: Exception) {
                                launch(Dispatchers.Main) {
                                    statusMessage = "Save failed: ${e.message}"
                                }
                            }
                        }
                    },
                    enabled = entryCount > 0 && !isSending,
                    modifier = Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                ) {
                    Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save")
                }

                Button(
                    onClick = {
                        VehicleCompatibilityProbe.clear()
                        statusMessage = "Probe data cleared."
                    },
                    enabled = entryCount > 0 && !isSending,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                }
            }

            if (telegramConfig == null) {
                Text(
                    "Telegram not configured — set it up under Backup & Restore first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Status line
            statusMessage?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (msg.startsWith("Telegram send failed"))
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(

    icon : ImageVector,
    title: String,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier              = Modifier.padding(start = 4.dp, bottom = 2.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Text(title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color)
    }
}

@Composable
private fun SettingsGroupLabel(
    title: String
) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsDetailRow(
    label: String,
    value: String,
    url: String? = null,
    onClick: (() -> Unit)? = null,
    showClickIndicator: Boolean = false
) {
    val context = LocalContext.current
    val hiddenClickInteractionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(
                when {
                    url != null -> Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                    onClick != null && showClickIndicator -> Modifier.clickable { onClick() }
                    onClick != null -> Modifier.clickable(
                        interactionSource = hiddenClickInteractionSource,
                        indication = null
                    ) { onClick() }
                    else -> Modifier
                }
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = if (url != null || showClickIndicator) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                url != null -> {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open link",
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(14.dp)
                    )
                }
                showClickIndicator -> {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
