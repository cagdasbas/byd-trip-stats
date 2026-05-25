package com.byd.tripstats.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.tripstats.data.local.entity.ChargingDataPointEntity
import com.byd.tripstats.data.local.entity.ChargingSessionEntity
import com.byd.tripstats.ui.components.drawCrosshair
import com.byd.tripstats.ui.theme.*
import com.byd.tripstats.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
private const val MAX_CHART_RENDER_POINTS = 500
private val chargingDetailJson = Json { ignoreUnknownKeys = true }

private data class ChargingChartPoint(
    val timestamp: Long,
    val soc: Double,
    val chargingPowerKw: Double,
    val batteryTotalVoltageV: Double,
    val batteryTempAvgC: Double,
    val batteryCellTempMinC: Double,
    val batteryCellTempMaxC: Double,
)

private data class ChargingPowerSummary(
    val peakKw: Double,
    val avgKw: Double
)

private enum class ChargingXAxisMode {
    TIME,
    SOC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargingDetailScreen(
    sessionId: Long,
    viewModel: DashboardViewModel,
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(sessionId) { viewModel.selectSession(sessionId) }
    DisposableEffect(Unit) { onDispose { viewModel.clearSelectedSession() } }

    val session    by viewModel.selectedSession.collectAsState()
    val dataPoints by viewModel.selectedSessionDataPoints.collectAsState()
    val liveTelemetry by viewModel.displayTelemetry.collectAsState()
    // JSON parsing per data point is expensive — do it off the main thread.
    // For a 5→100% AC session this can be 3000–6000 points; running it
    // synchronously inside remember() blocks composition and makes the
    // spinner appear frozen until the thread is released.
    var baseChartPoints by remember { mutableStateOf<List<ChargingChartPoint>>(emptyList()) }
    LaunchedEffect(dataPoints) {
        baseChartPoints = withContext(Dispatchers.Default) {
            dataPoints.map { it.toBaseChartPoint() }
        }
    }
    val powerSummary = remember(session, baseChartPoints) {
        session?.let { buildChargingPowerSummary(it, baseChartPoints) }
    }
    // Cap the points fed into Canvas paths so that a long overnight session
    // (thousands of lineTo calls) doesn't cause a slow first render.
    val chartPoints = remember(baseChartPoints) {
        if (baseChartPoints.size <= MAX_CHART_RENDER_POINTS) baseChartPoints
        else {
            val step = baseChartPoints.size / MAX_CHART_RENDER_POINTS
            baseChartPoints.filterIndexed { i, _ -> i % step == 0 }
        }
    }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Power + SoC", "Voltage", "Temperature")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Charging Detail", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(28.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        if (session == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(modifier = Modifier.size(60.dp)) }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick  = { selectedTab = i },
                        text = {
                            Text(
                                title,
                                fontSize   = 14.sp,
                                fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Synthetic = reconstructed from SoC delta while car was off (no live data points)
            val isSynthetic = session!!.peakKw == 0.0 && session!!.avgKw == 0.0

            when (selectedTab) {
                0 -> ChargingOverviewTab(session!!, chartPoints, powerSummary)
                1 -> if (session!!.isActive && chartPoints.size < 2) {
                    ActiveChargingPowerTab(
                        latestKw = liveTelemetry?.chargingPower?.takeIf { it > 0.1 }
                            ?: chartPoints.asReversed().firstOrNull { it.chargingPowerKw > 0.1 }?.chargingPowerKw
                    )
                } else {
                    ChargingPowerSocTab(
                        dataPoints = chartPoints,
                        isSynthetic = isSynthetic
                    )
                }
                2 -> ChargingChartTab(
                    dataPoints    = chartPoints,
                    isSynthetic   = isSynthetic,
                    title         = "HV Battery Voltage",
                    yAxisLabel    = "V",
                    lineColor     = BydEcoTealDim,
                    valueSelector = { it.batteryTotalVoltageV }
                )
                3 -> ChargingTempTab(chartPoints, isSynthetic)
            }
        }
    }
}

@Composable
private fun ActiveChargingPowerTab(latestKw: Double?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Charge Power",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                latestKw?.takeIf { it > 0.1 }?.let {
                    Text(
                        "%.1f kW current estimate".format(it),
                        style = MaterialTheme.typography.headlineSmall,
                        color = AccelerationOrange,
                        fontWeight = FontWeight.Bold
                    )
                }
                latestKw?.takeIf { it > 0.1 }?.let {
                    HorizontalDivider()
                    Text(
                        "The live chart will appear as soon as enough charging-power samples are recorded.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Tab: Overview ─────────────────────────────────────────────────────────────

@Composable
private fun ChargingOverviewTab(
    session   : ChargingSessionEntity,
    dataPoints: List<ChargingChartPoint>,
    powerSummary: ChargingPowerSummary?
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault()) }
    val displayPeakKw = powerSummary?.peakKw?.takeIf { it > 0.0 } ?: session.peakKw
    val displayAvgKw = powerSummary?.avgKw?.takeIf { it > 0.0 } ?: session.avgKw
    val displayTempStart = dataPoints.firstNotNullOfOrNull { it.overviewTemperatureC() }
        ?: session.batteryTempStart.takeIf { it > 0.0 }
    val displayTempEnd = dataPoints.asReversed().firstNotNullOfOrNull { it.overviewTemperatureC() }
        ?: session.batteryTempEnd?.takeIf { it > 0.0 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Date & duration
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OverviewRow("Started",  dateFmt.format(Date(session.startTime)))
                session.endTime?.let {
                    OverviewRow("Ended",  dateFmt.format(Date(it)))
                }
                session.durationSeconds?.let {
                    OverviewRow("Duration", formatDurationLong(it))
                }
            }
        }

        // Energy & SoC
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Energy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OverviewRow("SoC start",    "%.1f%%".format(session.socStart))
                session.socEnd?.let {
                    OverviewRow("SoC end",  "%.1f%%".format(it))
                    OverviewRow("SoC added","%.1f%%".format(it - session.socStart))
                }
                session.kwhAdded?.let {
                    OverviewRow("kWh added", "%.2f kWh".format(it), valueColor = RegenGreen)
                }
                OverviewRow("Battery (car)", "%.1f kWh".format(session.batteryKwh))
            }
        }

        // Power
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Power", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (displayPeakKw > 0.0) {
                    OverviewRow("Peak power", "%.1f kW".format(displayPeakKw), valueColor = AccelerationOrange)
                }
                if (displayAvgKw > 0.0) {
                    OverviewRow("Average power", "%.1f kW".format(displayAvgKw))
                }
                // Average charge rate (kWh/h) from peak and duration
                session.durationSeconds?.let { secs ->
                    if (session.kwhAdded != null && secs > 0) {
                        val rate = session.kwhAdded / (secs / 3600.0)
                        OverviewRow("Charge rate", "%.1f kW (avg)".format(rate))
                    }
                }
            }
        }

        // Thermal
        if (displayTempStart != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Battery Temperature", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OverviewRow("At start", "%.1f °C".format(displayTempStart))
                    displayTempEnd?.let {
                        OverviewRow("At end",   "%.1f °C".format(it))
                        val delta = it - displayTempStart
                        val sign  = if (delta >= 0) "+" else ""
                        OverviewRow("Rise",     "$sign%.1f °C".format(delta),
                            valueColor = if (delta > 10) BydErrorRed else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        // Data points count
        if (dataPoints.isNotEmpty()) {
            Text(
                "${dataPoints.size} telemetry points recorded",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun OverviewRow(
    label     : String,
    value     : String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium, color = valueColor)
    }
}

// ── Tab: Generic single-series chart ─────────────────────────────────────────

@Composable
private fun ChargingPowerSocTab(
    dataPoints: List<ChargingChartPoint>,
    isSynthetic: Boolean = false
) {
    if (dataPoints.size < 2) {
        ChartEmptyState(isSynthetic)
        return
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val powerColor = AccelerationOrange
    val socColor = BatteryBlue
    var touchPos by remember { mutableStateOf<Offset?>(null) }
    var xAxisMode by remember { mutableStateOf(ChargingXAxisMode.TIME) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.medium
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Charge Power + SoC",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(ChargingXAxisMode.TIME to "Time", ChargingXAxisMode.SOC to "SoC").forEach { (mode, label) ->
                            val selected = xAxisMode == mode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { xAxisMode = mode }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    fontSize = 12.sp,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChargingLegendItem(powerColor, "Power (kW)")
                    if (xAxisMode == ChargingXAxisMode.TIME) {
                        Spacer(Modifier.width(20.dp))
                        ChargingLegendItem(socColor, "SoC (%)")
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .pointerInput(Unit) {
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
                    val w = size.width
                    val h = size.height
                    val padL = 80f
                    val padR = if (xAxisMode == ChargingXAxisMode.TIME) 72f else 16f
                    val padT = 16f
                    val padB = 40f
                    val chartW = w - padL - padR
                    val chartH = h - padT - padB

                    val powerValues = dataPoints.map { it.chargingPowerKw }
                    val socValues = dataPoints.map { it.soc }

                    val powerMinRaw = powerValues.minOrNull() ?: 0.0
                    val powerMaxRaw = powerValues.maxOrNull()?.coerceAtLeast(powerMinRaw + 1.0) ?: 1.0
                    val powerStep = niceStep(powerMaxRaw - powerMinRaw)
                    val powerMin = kotlin.math.floor(powerMinRaw / powerStep) * powerStep
                    val powerMax = kotlin.math.ceil(powerMaxRaw / powerStep) * powerStep + powerStep

                    val socMinRaw = socValues.minOrNull() ?: 0.0
                    val socMaxRaw = socValues.maxOrNull()?.coerceAtLeast(socMinRaw + 0.5) ?: 1.0
                    val socStep = niceStep(socMaxRaw - socMinRaw)
                    val socMin = (kotlin.math.floor(socMinRaw / socStep) * socStep).coerceAtLeast(0.0)
                    val socMax = (kotlin.math.ceil(socMaxRaw / socStep) * socStep).coerceAtMost(100.0)

                    val totalMs = dataPoints.last().timestamp - dataPoints.first().timestamp
                    val startMs = dataPoints.first().timestamp
                    val socXMin = socValues.minOrNull() ?: 0.0
                    val socXMax = socValues.maxOrNull()?.coerceAtLeast(socXMin + 0.1) ?: 1.0

                    fun xOf(point: ChargingChartPoint): Float =
                        when (xAxisMode) {
                            ChargingXAxisMode.TIME ->
                                if (totalMs <= 0L) padL + chartW / 2f
                                else padL + ((point.timestamp - startMs).toDouble() / totalMs.toDouble() * chartW).toFloat()
                            ChargingXAxisMode.SOC -> {
                                val frac = (point.soc - socXMin) / (socXMax - socXMin)
                                (padL + frac * chartW).toFloat()
                            }
                        }

                    fun yOfPower(v: Double): Float {
                        val frac = (v - powerMin) / (powerMax - powerMin)
                        return (padT + chartH * (1.0 - frac)).toFloat()
                    }

                    fun yOfSoc(v: Double): Float {
                        val frac = (v - socMin) / (socMax - socMin)
                        return (padT + chartH * (1.0 - frac)).toFloat()
                    }

                    val labelPaint = android.graphics.Paint().apply {
                        color = textColor.copy(alpha = 0.7f).toArgb()
                        textSize = 22f
                        isAntiAlias = true
                    }
                    val xLabelPaint = android.graphics.Paint().apply {
                        color = textColor.copy(alpha = 0.7f).toArgb()
                        textSize = 20f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    val nc = drawContext.canvas.nativeCanvas

                    var yTick = powerMin
                    while (yTick <= powerMax + 0.01) {
                        val y = yOfPower(yTick)
                        drawLine(gridColor, Offset(padL, y), Offset(w - padR, y), 1f)
                        labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
                        nc.drawText("%.0f".format(yTick), padL - 6f, y + 8f, labelPaint)
                        yTick += powerStep
                    }

                    if (xAxisMode == ChargingXAxisMode.TIME) {
                        var socTick = socMin
                        while (socTick <= socMax + 0.01) {
                            val y = yOfSoc(socTick)
                            labelPaint.color = socColor.copy(alpha = 0.85f).toArgb()
                            labelPaint.textAlign = android.graphics.Paint.Align.LEFT
                            nc.drawText("%.0f".format(socTick), w - padR + 10f, y + 8f, labelPaint)
                            socTick += socStep
                        }
                    }
                    labelPaint.color = textColor.copy(alpha = 0.7f).toArgb()

                    val leftAxisPaint = android.graphics.Paint().apply {
                        color = powerColor.copy(alpha = 0.9f).toArgb()
                        textSize = 19f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    nc.save()
                    nc.rotate(-90f, 18f, padT + chartH / 2f)
                    nc.drawText("Power (kW)", 18f, padT + chartH / 2f, leftAxisPaint)
                    nc.restore()

                    if (xAxisMode == ChargingXAxisMode.TIME) {
                        val rightAxisPaint = android.graphics.Paint().apply {
                            color = socColor.copy(alpha = 0.9f).toArgb()
                            textSize = 19f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                        nc.save()
                        nc.rotate(90f, w - 18f, padT + chartH / 2f)
                        nc.drawText("SoC (%)", w - 18f, padT + chartH / 2f, rightAxisPaint)
                        nc.restore()
                    }

                    drawLine(axisColor, Offset(padL, padT + chartH), Offset(w - padR, padT + chartH), 1.5f)

                    val desiredLabelCount = 6
                    val labelStep = ((dataPoints.size - 1) / desiredLabelCount).coerceAtLeast(1)
                    var lastLabelX = -90f
                    dataPoints.forEachIndexed { i, point ->
                        val shouldConsider = i % labelStep == 0 || i == dataPoints.lastIndex
                        val x = xOf(point)
                        if (shouldConsider && x - lastLabelX >= 72f) {
                            val label = when (xAxisMode) {
                                ChargingXAxisMode.TIME -> {
                                    val mins = (point.timestamp - startMs) / 60000L
                                    if (mins >= 60) {
                                        "+${mins / 60}h${mins % 60}m"
                                    } else {
                                        "+${mins}m"
                                    }
                                }
                                ChargingXAxisMode.SOC -> "%.1f%%".format(point.soc)
                            }
                            nc.drawText(label, x, h - 8f, xLabelPaint)
                            lastLabelX = x
                        }
                    }

                    val powerPath = Path().apply {
                        moveTo(xOf(dataPoints.first()), yOfPower(dataPoints.first().chargingPowerKw))
                        dataPoints.drop(1).forEach { point ->
                            lineTo(xOf(point), yOfPower(point.chargingPowerKw))
                        }
                    }
                    drawPath(
                        powerPath,
                        powerColor,
                        style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    if (xAxisMode == ChargingXAxisMode.TIME) {
                        val socPath = Path().apply {
                            moveTo(xOf(dataPoints.first()), yOfSoc(dataPoints.first().soc))
                            dataPoints.drop(1).forEach { point ->
                                lineTo(xOf(point), yOfSoc(point.soc))
                            }
                        }
                        drawPath(
                            socPath,
                            socColor,
                            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    touchPos?.let { tp ->
                        if (tp.x in padL..(w - padR) && dataPoints.size > 1) {
                            val fraction = ((tp.x - padL) / chartW).coerceIn(0f, 1f)
                            val p = when (xAxisMode) {
                                ChargingXAxisMode.TIME -> {
                                    val targetTs = startMs + (fraction * totalMs).toLong()
                                    dataPoints.minByOrNull { kotlin.math.abs(it.timestamp - targetTs) }
                                }
                                ChargingXAxisMode.SOC -> {
                                    val targetSoc = socXMin + (fraction * (socXMax - socXMin))
                                    dataPoints.minByOrNull { kotlin.math.abs(it.soc - targetSoc) }
                                }
                            }
                            if (p != null) {
                                val realTime =
                                    java.time.Instant.ofEpochMilli(p.timestamp)
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .format(timeFormatter)
                                val secs = (p.timestamp - startMs) / 1000L
                                drawCrosshair(
                                    cx = xOf(p),
                                    cy = yOfPower(p.chargingPowerKw),
                                    w = w,
                                    padL = padL,
                                    padR = padR,
                                    padT = padT,
                                    chartH = chartH,
                                    line1 = "Power: %.1f kW".format(p.chargingPowerKw),
                                    line2 = "SoC: %.1f%%".format(p.soc),
                                    line3 = if (xAxisMode == ChargingXAxisMode.TIME) {
                                        "+%d:%02d  %s".format(secs / 60, secs % 60, realTime)
                                    } else {
                                        "%s  +%d:%02d".format(realTime, secs / 60, secs % 60)
                                    },
                                    accentColor = powerColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChargingLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(4.dp)
                .border(0.dp, Color.Transparent)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(
                    color = color,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 4f
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChargingChartTab(
    dataPoints    : List<ChargingChartPoint>,
    isSynthetic   : Boolean = false,
    title         : String,
    yAxisLabel    : String,
    lineColor     : androidx.compose.ui.graphics.Color,
    valueSelector : (ChargingChartPoint) -> Double
) {
    if (dataPoints.size < 2) {
        ChartEmptyState(isSynthetic)
        return
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    var touchPos by remember { mutableStateOf<Offset?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = 0.2f
                        ),
                    shape = MaterialTheme.shapes.medium
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .pointerInput(Unit) {
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
                    val w = size.width
                    val h = size.height
                    val padL = 80f
                    val padR = 16f
                    val padT = 16f
                    val padB = 40f
                    val chartW = w - padL - padR
                    val chartH = h - padT - padB

                    val values = dataPoints.map(valueSelector)
                    val rawMin = values.minOrNull() ?: 0.0
                    val rawMax = values.maxOrNull()?.coerceAtLeast(rawMin + 1.0) ?: 1.0
                    val yStep = niceStep(rawMax - rawMin)
                    val yMin = kotlin.math.floor(rawMin / yStep) * yStep
                    val yMax = kotlin.math.ceil(rawMax / yStep) * yStep + yStep

                    val totalMs = dataPoints.last().timestamp - dataPoints.first().timestamp
                    val startMs = dataPoints.first().timestamp

                    fun xOf(ts: Long) =
                        padL + ((ts - startMs).toFloat() / totalMs.coerceAtLeast(1L)) * chartW
                    fun yOf(v: Double) =
                        (padT + chartH * (1.0 - (v - yMin) / (yMax - yMin))).toFloat()

                    val nc = drawContext.canvas.nativeCanvas
                    val labelPaint =
                        android.graphics.Paint().apply {
                            color = textColor.copy(alpha = 0.7f).toArgb()
                            textSize = 22f
                            isAntiAlias = true
                        }
                    val xLabelPaint =
                        android.graphics.Paint().apply {
                            color = textColor.copy(alpha = 0.6f).toArgb()
                            textSize = 20f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }

                    // Y grid + labels
                    var yTick = yMin
                    while (yTick <= yMax + 0.01) {
                        val y = yOf(yTick)
                        drawLine(gridColor, Offset(padL, y), Offset(w - padR, y), 1f)
                        labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
                        nc.drawText("%.0f".format(yTick), padL - 6f, y + 8f, labelPaint)
                        yTick += yStep
                    }

                    // Y axis label
                    val yAxisPaint =
                        android.graphics.Paint().apply {
                            color = textColor.copy(alpha = 0.55f).toArgb()
                            textSize = 19f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                    nc.save()
                    nc.rotate(-90f, 18f, padT + chartH / 2f)
                    nc.drawText(yAxisLabel, 18f, padT + chartH / 2f, yAxisPaint)
                    nc.restore()

                    // X axis
                    drawLine(
                        axisColor,
                        Offset(padL, padT + chartH),
                        Offset(w - padR, padT + chartH),
                        1.5f
                    )

                    // X tick labels — 5 evenly spaced time markers
                    for (i in 0..4) {
                        val frac = i / 4.0
                        val ts = startMs + (totalMs * frac).toLong()
                        val x = xOf(ts)
                        val mins = ((ts - startMs) / 60_000.0).roundToInt()
                        nc.drawText("+${mins}m", x, h - 4f, xLabelPaint)
                    }

                    // Area fill
                    val areaPath =
                        Path().apply {
                            moveTo(xOf(dataPoints.first().timestamp), yOf(values.first()))
                            dataPoints.drop(1).forEachIndexed { i, p ->
                                lineTo(xOf(p.timestamp), yOf(values[i + 1]))
                            }
                            lineTo(xOf(dataPoints.last().timestamp), padT + chartH)
                            lineTo(xOf(dataPoints.first().timestamp), padT + chartH)
                            close()
                        }
                    drawPath(
                        areaPath,
                        Brush.verticalGradient(
                            listOf(
                                lineColor.copy(alpha = 0.30f),
                                lineColor.copy(alpha = 0f)
                            ),
                            startY = yOf(values.max()),
                            endY = padT + chartH
                        )
                    )

                    // Line
                    val linePath =
                        Path().apply {
                            moveTo(xOf(dataPoints.first().timestamp), yOf(values.first()))
                            dataPoints.drop(1).forEachIndexed { i, p ->
                                lineTo(xOf(p.timestamp), yOf(values[i + 1]))
                            }
                        }
                    drawPath(
                        linePath,
                        lineColor,
                        style =
                            Stroke(
                                width = 3f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                    )

                    // Crosshair overlay
                    touchPos?.let { tp ->
                        if (tp.x in padL..(w - padR) && dataPoints.size > 1) {
                            val fraction = ((tp.x - padL) / chartW).coerceIn(0f, 1f)
                            val targetTs = startMs + (fraction * totalMs).toLong()
                            val p = dataPoints.minByOrNull { kotlin.math.abs(it.timestamp - targetTs) }
                            if (p != null) {
                                val v = valueSelector(p)
                                val secs = (p.timestamp - startMs) / 1000L
                                val realTime = java.time.Instant.ofEpochMilli(p.timestamp)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .format(timeFormatter)
                                val durationStr = "+%d:%02d".format(secs / 60, secs % 60)

                                drawCrosshair(
                                    cx = xOf(p.timestamp),
                                    cy = yOf(v),
                                    w = w,
                                    padL = padL,
                                    padR = padR,
                                    padT = padT,
                                    chartH = chartH,
                                    line1 = "%.1f %s".format(v, yAxisLabel),
                                    line2 = realTime,
                                    line3 = durationStr,
                                    accentColor = lineColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Tab: Temperature (multi-series) ──────────────────────────────────────────

@Composable
private fun ChargingTempTab(
    dataPoints  : List<ChargingChartPoint>,
    isSynthetic : Boolean = false
) {
    if (dataPoints.size < 2) {
        ChartEmptyState(isSynthetic)
        return
    }

    val textColor    = MaterialTheme.colorScheme.onSurface
    val gridColor    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val axisColor    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val avgColor     = BydErrorRed
    val minColor     = BydElectricAzure
    val maxColor     = AccelerationOrange
    var touchPos by remember { mutableStateOf<Offset?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Card(
            modifier = Modifier.fillMaxSize()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.medium
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
                Text(
                    "Battery Temperature",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                // Legend
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ChartLegendItem(avgColor, "Avg")
                    ChartLegendItem(minColor, "Cell min")
                    ChartLegendItem(maxColor, "Cell max")
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .pointerInput(Unit) {
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
                    val w = size.width
                    val h = size.height
                    val padL = 80f
                    val padR = 16f
                    val padT = 16f
                    val padB = 40f
                    val chartW = w - padL - padR
                    val chartH = h - padT - padB

                    val avgVals = dataPoints.map { it.batteryTempAvgC }
                    val minVals = dataPoints.map { it.batteryCellTempMinC }
                    val maxVals = dataPoints.map { it.batteryCellTempMaxC }
                    val allVals = avgVals + minVals + maxVals
                    val rawMin  = allVals.minOrNull() ?: 0.0
                    val rawMax  = allVals.maxOrNull()?.coerceAtLeast(rawMin + 1.0) ?: 1.0
                    val yStep   = niceStep(rawMax - rawMin)
                    val yMin    = kotlin.math.floor(rawMin / yStep) * yStep
                    val yMax    = kotlin.math.ceil(rawMax / yStep) * yStep + yStep

                    val totalMs  = dataPoints.last().timestamp - dataPoints.first().timestamp
                    val startMs  = dataPoints.first().timestamp

                    fun xOf(ts: Long)  = padL + ((ts - startMs).toFloat() / totalMs.coerceAtLeast(1L)) * chartW
                    fun yOf(v: Double) = (padT + chartH * (1.0 - (v - yMin) / (yMax - yMin))).toFloat()

                    val nc = drawContext.canvas.nativeCanvas
                    val labelPaint =
                        android.graphics.Paint().apply {
                            color = textColor.copy(alpha = 0.7f).toArgb()
                            textSize = 22f
                            isAntiAlias = true
                        }
                    val xLabelPaint =
                        android.graphics.Paint().apply {
                            color = textColor.copy(alpha = 0.6f).toArgb()
                            textSize = 20f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }

                    // Y grid + labels
                    var yTick = yMin
                    while (yTick <= yMax + 0.01) {
                        val y = yOf(yTick)
                        drawLine(gridColor, Offset(padL, y), Offset(w - padR, y), 1f)
                        labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
                        nc.drawText("%.0f".format(yTick), padL - 6f, y + 8f, labelPaint)
                        yTick += yStep
                    }

                    // Y axis label
                    val yAxisPaint =
                        android.graphics.Paint().apply {
                            color = textColor.copy(alpha = 0.55f).toArgb()
                            textSize = 19f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                    nc.save()
                    nc.rotate(-90f, 18f, padT + chartH / 2f)
                    nc.drawText("°C", 18f, padT + chartH / 2f, yAxisPaint)
                    nc.restore()

                    // X axis
                    drawLine(
                        axisColor,
                        Offset(padL, padT + chartH),
                        Offset(w - padR, padT + chartH),
                        1.5f
                    )
                    for (i in 0..4) {
                        val frac = i / 4.0
                        val ts = startMs + (totalMs * frac).toLong()
                        val mins = ((ts - startMs) / 60_000.0).roundToInt()
                        nc.drawText("+${mins}m", xOf(ts), h - 4f, xLabelPaint)
                    }

                    // Draw three series
                    fun drawSeries(
                        vals: List<Double>,
                        color: androidx.compose.ui.graphics.Color,
                        width: Float = 2.5f
                    ) {
                        if (vals.size < 2) return
                        val path =
                            Path().apply {
                                moveTo(xOf(dataPoints.first().timestamp), yOf(vals.first()))
                                vals.drop(1).forEachIndexed { i, v ->
                                    lineTo(xOf(dataPoints[i + 1].timestamp), yOf(v))
                                }
                            }
                        drawPath(
                            path,
                            color,
                            style =
                                Stroke(
                                    width = width,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                        )
                    }

                    drawSeries(minVals, minColor, 2f)
                    drawSeries(maxVals, maxColor, 2f)
                    drawSeries(avgVals, avgColor, 3f) // avg on top

                    // Crosshair overlay (tracking average temp)
                    touchPos?.let { tp ->
                        if (tp.x in padL..(w - padR) && dataPoints.size > 1) {
                            val fraction = ((tp.x - padL) / chartW).coerceIn(0f, 1f)
                            val targetTs = startMs + (fraction * totalMs).toLong()
                            val p =
                                dataPoints.minByOrNull {
                                    kotlin.math.abs(it.timestamp - targetTs)
                                }
                            if (p != null) {
                                val v = p.batteryTempAvgC
                                val secs = (p.timestamp - startMs) / 1000L
                                val realTime =
                                    java.time.Instant.ofEpochMilli(p.timestamp)
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .format(timeFormatter)
                                val durationStr = "+%d:%02d".format(secs / 60, secs % 60)

                                drawCrosshair(
                                    cx = xOf(p.timestamp),
                                    cy = yOf(v.toDouble()),
                                    w = w,
                                    padL = padL,
                                    padR = padR,
                                    padT = padT,
                                    chartH = chartH,
                                    line1 = "%.1f °C".format(v),
                                    line2 = realTime,
                                    line3 = durationStr,
                                    accentColor = avgColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildChargingPowerSummary(
    session: ChargingSessionEntity,
    dataPoints: List<ChargingChartPoint>
): ChargingPowerSummary {
    if (dataPoints.isEmpty()) {
        return ChargingPowerSummary(
            peakKw = session.peakKw,
            avgKw = session.avgKw
        )
    }

    val positivePowers = dataPoints.map { it.chargingPowerKw }.filter { it > 0.1 }
    val displayPeakKw = maxOf(session.peakKw, positivePowers.maxOrNull() ?: 0.0)
    val displayAvgKw = positivePowers.average().takeIf { positivePowers.isNotEmpty() }
        ?: session.avgKw

    return ChargingPowerSummary(
        peakKw = displayPeakKw,
        avgKw = displayAvgKw
    )
}

private fun ChargingDataPointEntity.toBaseChartPoint(): ChargingChartPoint {
    // Only parse rawJson when first-class columns are missing (old sessions).
    // Modern sessions populate all primary columns; parsing the full telemetry
    // JSON blob for every point is the source of 30–60s load times on large sessions.
    val needsRawJson = chargingPower <= 0.0
        || batteryTotalVoltage <= 0
        || (batteryCellTempMin <= 0 && batteryCellTempMax <= 0 && batteryTempAvg <= 0.0)
    val raw = if (needsRawJson) rawJson.toJsonObjectOrNull() else null
    val rawChargingPower = raw?.doubleOrNull("charging_power")
    val rawVoltage = raw?.doubleOrNull("battery_total_voltage")
    val rawCellTempAvg = raw?.doubleOrNull("statistic_cell_temp_avg")
    val rawBatteryCellTempMin = raw?.doubleOrNull("battery_cell_temp_min")
    val rawBatteryCellTempMax = raw?.doubleOrNull("battery_cell_temp_max")
    val rawStatisticCellTempMin = raw?.doubleOrNull("statistic_cell_temp_min")
    val rawStatisticCellTempMax = raw?.doubleOrNull("statistic_cell_temp_max")
    val rawPackTemp = raw?.doubleOrNull("battery_pack_temp")

    val effectiveVoltage = when {
        batteryTotalVoltage > 0 -> batteryTotalVoltage.toDouble()
        rawVoltage != null && rawVoltage > 0.0 -> rawVoltage
        else -> 0.0
    }

    val effectiveChargingPower = when {
        chargingPower > 0.0 -> chargingPower
        rawChargingPower != null && rawChargingPower > 0.0 -> rawChargingPower
        else -> 0.0
    }

    val effectiveCellTempMin = when {
        batteryCellTempMin > 0 -> batteryCellTempMin.toDouble()
        rawBatteryCellTempMin != null && rawBatteryCellTempMin > 0.0 -> rawBatteryCellTempMin
        rawStatisticCellTempMin != null && rawStatisticCellTempMin > 0.0 -> rawStatisticCellTempMin
        else -> 0.0
    }

    val effectiveCellTempMax = when {
        batteryCellTempMax > 0 -> batteryCellTempMax.toDouble()
        rawBatteryCellTempMax != null && rawBatteryCellTempMax > 0.0 -> rawBatteryCellTempMax
        rawStatisticCellTempMax != null && rawStatisticCellTempMax > 0.0 -> rawStatisticCellTempMax
        else -> 0.0
    }

    val effectiveTempAvg = when {
        effectiveCellTempMin > 0.0 && effectiveCellTempMax > 0.0 ->
            (effectiveCellTempMin + effectiveCellTempMax) / 2.0
        effectiveCellTempMin > 0.0 -> effectiveCellTempMin
        effectiveCellTempMax > 0.0 -> effectiveCellTempMax
        batteryTempAvg > 0.0 -> batteryTempAvg
        rawCellTempAvg != null && rawCellTempAvg > 0.0 -> rawCellTempAvg
        rawPackTemp != null && rawPackTemp > 0.0 -> rawPackTemp
        else -> 0.0
    }

    return ChargingChartPoint(
        timestamp = timestamp,
        soc = soc,
        chargingPowerKw = effectiveChargingPower,
        batteryTotalVoltageV = effectiveVoltage,
        batteryTempAvgC = effectiveTempAvg,
        batteryCellTempMinC = effectiveCellTempMin,
        batteryCellTempMaxC = effectiveCellTempMax
    )
}

private fun String.toJsonObjectOrNull(): JsonObject? {
    if (isBlank() || this == "{}") return null
    return runCatching { chargingDetailJson.parseToJsonElement(this).jsonObject }.getOrNull()
}

private fun JsonObject.doubleOrNull(key: String): Double? =
    this[key]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

private fun ChargingChartPoint.overviewTemperatureC(): Double? = when {
    batteryCellTempMinC > 0.0 || batteryCellTempMaxC > 0.0 -> {
        val samples = listOf(batteryCellTempMinC, batteryCellTempMaxC).filter { it > 0.0 }
        if (samples.isEmpty()) null else samples.average()
    }
    batteryTempAvgC > 0.0 -> batteryTempAvgC
    else -> null
}

// ── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun ChartEmptyState(isSynthetic: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            if (isSynthetic) {
                Text("⚡", fontSize = 40.sp)
                Text(
                    "Reconstructed session",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "This session was recorded while the car was off — " +
                    "live telemetry charts are only available for sessions " +
                    "started while you are in the car with it powered on.",
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            } else {
                Text(
                    "Not enough data",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun ChartLegendItem(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(20.dp, 3.dp)) {
            drawLine(
                color,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = 3f
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun niceStep(range: Double): Double = when {
    range <= 5    -> 1.0
    range <= 20   -> 5.0
    range <= 50   -> 10.0
    range <= 200  -> 25.0
    range <= 500  -> 50.0
    else          -> 100.0
}

private fun formatDurationLong(seconds: Long): String {
    val h = TimeUnit.SECONDS.toHours(seconds)
    val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
    val s = seconds % 60
    return if (h > 0) "${h}h ${m}min ${s}s" else "${m}min ${s}s"
}
