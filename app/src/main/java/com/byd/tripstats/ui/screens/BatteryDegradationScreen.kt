package com.byd.tripstats.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.tripstats.data.entitlement.EntitlementManager
import com.byd.tripstats.ui.theme.*
import com.byd.tripstats.ui.viewmodel.DashboardViewModel
import com.byd.tripstats.util.BatteryHealthReport
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryDegradationScreen(
    viewModel: DashboardViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val data by viewModel.batteryDegradationData.collectAsState()
    val sohExclusion by viewModel.sohExclusion.collectAsState()
    val excludedTripCount by viewModel.sohExcludedTripCount.collectAsState()
    // Decline rate, 80% projection and the exportable report are Pro features.
    val isPro by EntitlementManager.isPro.collectAsState()
    val selectedCar by viewModel.selectedCarConfig.collectAsState(initial = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Battery Degradation", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("State of Health over time", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            modifier = Modifier.size(28.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (data.size < 2) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Filled.BatteryChargingFull,
                        contentDescription = null,
                        tint = BatteryBlue,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        "Not enough data yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Complete at least 2 trips with telemetry active\nto see battery degradation tracking.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    // All (or all but one) trips are legacy and currently excluded — give the
                    // user a way out so they aren't stranded on an empty screen.
                    if (excludedTripCount > 0) {
                        Text(
                            "$excludedTripCount earlier trip(s) are hidden because they were recorded " +
                            "with a legacy method that under-reported SoH. You can include them, though " +
                            "the chart may show a misleading dip.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        OutlinedButton(onClick = { viewModel.setSohExclusionOff() }) {
                            Icon(Icons.Filled.Visibility, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Show all recorded data")
                        }
                    }
                }
            }
            return@Scaffold
        }

        // ── Compute regression + projection ───────────────────────────────────
        val regression  = linearRegression(data)
        val stats       = degradationStats(regression)

        // Assemble the report payload from the same data/stats shown on screen.
        fun buildReportData(): BatteryHealthReport.Data {
            val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val declineLabel = if (stats.declinePerYear < 0.01) "< 0.01% / yr"
                else "${"%.2f".format(stats.declinePerYear)}% / yr"
            return BatteryHealthReport.Data(
                vehicleName = selectedCar?.displayName ?: "BYD vehicle",
                batteryKwh = selectedCar?.batteryKwh,
                appVersion = com.byd.tripstats.BuildConfig.VERSION_NAME,
                currentSoh = data.last().avgSoh,
                declinePerYearLabel = declineLabel,
                projectedAt80Label = stats.projectedAt80Label,
                firstDate = df.format(Date(data.first().timestamp)),
                lastDate = df.format(Date(data.last().timestamp)),
                tripsAnalyzed = data.size,
                warrantyNote = "BYD's battery warranty typically covers the pack to 70% State of " +
                    "Health for 8 years / 250,000 km. Check your vehicle's warranty booklet for " +
                    "the exact terms.",
                exclusionNote = if (excludedTripCount > 0)
                    "Excludes $excludedTripCount earlier trip(s) recorded before " +
                        "${df.format(Date(sohExclusion.cutoffMs ?: 0L))} using a legacy estimation " +
                        "method that under-reported State of Health."
                    else null,
                entries = data.map {
                    BatteryHealthReport.Entry(
                        date = df.format(Date(it.timestamp)),
                        odometerKm = it.odometer,
                        soh = it.avgSoh
                    )
                }
            )
        }

        // Generate the report (PDF or HTML) and toast the saved path / any error.
        fun saveReport(asPdf: Boolean) {
            scope.launch {
                try {
                    val d = buildReportData()
                    val name = if (asPdf) BatteryHealthReport.generatePdfAndSave(context, d)
                               else BatteryHealthReport.generateAndSave(context, d)
                    Toast.makeText(
                        context,
                        "Report saved: Download/BydTripStats/$name",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Report failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Summary stat cards ─────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DegradationStatCard(
                    modifier  = Modifier.weight(1f),
                    label     = "Current SoH",
                    value     = "${"%.1f".format(data.last().avgSoh)}%",
                    icon      = Icons.Filled.BatteryChargingFull,
                    color     = sohColor(data.last().avgSoh)
                )
                DegradationStatCard(
                    modifier  = Modifier.weight(1f),
                    label     = "Trips analysed",
                    value     = "${data.size}",
                    icon      = Icons.Filled.Route,
                    color     = BatteryBlue
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DegradationStatCard(
                    modifier  = Modifier.weight(1f),
                    label     = "Decline rate",
                    value     = if (stats.declinePerYear < 0.01) "< 0.01% / yr"
                                else "${"%.2f".format(stats.declinePerYear)}% / yr",
                    icon      = Icons.Filled.Timeline,
                    color     = AccelerationOrange
                )
                DegradationStatCard(
                    modifier  = Modifier.weight(1f),
                    label     = "Projected @ 80%",
                    value     = stats.projectedAt80Label,
                    icon      = Icons.Filled.CalendarMonth,
                    color     = MaterialTheme.colorScheme.primary
                )
            }

            // ── Chart ──────────────────────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(300.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SoH History", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        // Legend
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LegendDot(BatteryBlue,      "Recorded")
                            LegendDot(AccelerationOrange, "Trend")
                            LegendDot(AccelerationOrange.copy(alpha = 0.5f), "Projected")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    DegradationChart(
                        data       = data,
                        regression = regression,
                        modifier   = Modifier.fillMaxSize()
                    )
                }
            }

            // ── Health interpretation card ─────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Info, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text("How to read this", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "SoH (State of Health) is shown when the car exposes a direct battery-health value, " +
                        "or as an estimate derived from vehicle telemetry when a direct value is unavailable. " +
                        "100% means the pack is at factory capacity. The orange trend line is a " +
                        "least-squares linear fit across all your recorded trips.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    HealthBand(range = "95 - 100%", label = "Excellent — factory new condition",
                        color = RegenGreen)
                    HealthBand(range = "90 - 95%",  label = "Good — normal ageing",
                        color = BatteryBlue)
                    HealthBand(range = "80 - 90%",  label = "Fair — noticeable range reduction",
                        color = AccelerationOrange)
                    HealthBand(range = "< 80%",     label = "Poor — heavy range reduction",
                        color = MaterialTheme.colorScheme.error)
                    HorizontalDivider()
                    Text(
                        "Tip: BYD's warranty covers the battery to 70% SoH for 8 years / 250,000 km.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // ── Battery health report card (Pro) ───────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Description, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text("Battery health report", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        if (!isPro) {
                            Spacer(Modifier.width(4.dp))
                            ProBadge()
                        }
                    }
                    Text(
                        "Export a report of your battery's State of Health, decline rate and 80% " +
                        "projection — useful evidence when selling the car or raising a warranty " +
                        "claim. Saved to Download/BydTripStats/ as a print-ready PDF or as HTML " +
                        "(opens in any browser). Generated entirely on-device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isPro) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { saveReport(asPdf = true) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.PictureAsPdf, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("PDF")
                            }
                            OutlinedButton(
                                onClick = { saveReport(asPdf = false) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Description, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("HTML")
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Lock, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Unlock with Pro")
                        }
                    }
                }
            }

            // ── SoH data range (legacy exclusion) card ─────────────────────────
            val dateFmt = remember {
                java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            }
            var advancedOpen by remember { mutableStateOf(false) }
            var showShowAllConfirm by remember { mutableStateOf(false) }

            // Apply a change and offer an UNDO snackbar; captures the prior state first.
            fun applyExclusion(change: () -> Unit) {
                val prev = sohExclusion
                change()
                scope.launch {
                    val res = snackbarHostState.showSnackbar(
                        message = "Battery data range updated",
                        actionLabel = "UNDO",
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) viewModel.restoreSohExclusion(prev)
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.FilterList, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text("Battery data range", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                    }
                    val cutoffLabel = sohExclusion.cutoffMs?.let { dateFmt.format(java.util.Date(it)) }
                    val statusText = when (sohExclusion.mode) {
                        DashboardViewModel.SohExclusionMode.AUTO ->
                            if (excludedTripCount > 0)
                                "Hiding $excludedTripCount early trip(s) recorded before $cutoffLabel " +
                                    "with the legacy estimation method (recommended)."
                            else
                                "Using every recorded trip — none predate the statistical SoH method."
                        DashboardViewModel.SohExclusionMode.CUSTOM ->
                            "Hiding $excludedTripCount trip(s) recorded before $cutoffLabel."
                        DashboardViewModel.SohExclusionMode.OFF ->
                            "Including every recorded trip, even early ones whose calculated SoH may read low."
                    }
                    Text(statusText, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "This only changes what the chart and reports use — no trips are ever " +
                        "deleted, and you can change it back anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    TextButton(onClick = { advancedOpen = !advancedOpen }) {
                        Icon(if (advancedOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (advancedOpen) "Hide options" else "Adjust data range")
                    }
                    if (advancedOpen) {
                        if (sohExclusion.mode != DashboardViewModel.SohExclusionMode.AUTO) {
                            OutlinedButton(
                                onClick = { applyExclusion { viewModel.setSohExclusionAuto() } },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Recommend, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Use recommended range")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                applyExclusion { viewModel.setSohExclusionCutoff(System.currentTimeMillis()) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.RestartAlt, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Start fresh from today")
                        }
                        if (sohExclusion.mode != DashboardViewModel.SohExclusionMode.OFF) {
                            OutlinedButton(
                                onClick = { showShowAllConfirm = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Visibility, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Show all recorded data")
                            }
                        }
                    }
                }
            }

            if (showShowAllConfirm) {
                AlertDialog(
                    onDismissRequest = { showShowAllConfirm = false },
                    icon = { Icon(Icons.Filled.Warning, null) },
                    title = { Text("Show all recorded data?") },
                    text = {
                        Text(
                            "This re-includes early trips recorded with the legacy method, which " +
                            "under-estimated SoH — it will add a misleading dip to the chart and to " +
                            "any report you generate. Nothing is deleted; you can switch back anytime."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showShowAllConfirm = false
                            applyExclusion { viewModel.setSohExclusionOff() }
                        }) { Text("Show all") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showShowAllConfirm = false }) { Text("Cancel") }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Chart ─────────────────────────────────────────────────────────────────────

@Composable
private fun DegradationChart(
    data       : List<DashboardViewModel.SohDataPoint>,
    regression : RegressionResult,
    modifier   : Modifier = Modifier
) {
    val lineColor       = BatteryBlue
    val dotFill         = BatteryBlue
    val dotGlow         = BatteryBlue.copy(alpha = 0.18f)
    val trendColor      = AccelerationOrange
    val projectedColor  = AccelerationOrange.copy(alpha = 0.45f)
    val gridColor       = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
    val textColor       = MaterialTheme.colorScheme.onSurface
    val fmt             = SimpleDateFormat("MM/yy", Locale.getDefault())

    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val padL = 52f; val padR = 16f; val padT = 12f; val padB = 32f
        val chartW = w - padL - padR; val chartH = h - padT - padB

        // Y range: clamp to [floor-2, 100], minimum window of 6 %
        val minSoh  = data.minOf { it.avgSoh }
        val yMin    = (floor(minSoh / 2) * 2 - 2).coerceAtLeast(70.0)
        val yMax    = 100.0
        val yRange  = yMax - yMin

        // X range: first trip to projection end (or +2 years if trend is flat)
        val xMinMs  = data.first().timestamp.toDouble()
        val projMs  = regression.projectedAt80Ms
        val xMaxMs  = if (projMs != null && projMs > data.last().timestamp)
            (data.last().timestamp + (projMs - data.last().timestamp) * 0.3)
                .coerceAtMost(data.last().timestamp + 5 * 365.25 * 86_400_000.0)
        else
            data.last().timestamp + 365.25 * 86_400_000.0  // always show at least +1 year
        val xRange  = xMaxMs - xMinMs

        fun xOf(ms: Double) = (padL + ((ms - xMinMs) / xRange) * chartW).toFloat()
        fun yOf(soh: Double) = (padT + chartH * (1.0 - (soh - yMin) / yRange)).toFloat()

        val nc = drawContext.canvas.nativeCanvas

        // Y grid + labels
        val yStep = if (yRange <= 8) 1.0 else if (yRange <= 15) 2.0 else 5.0
        var yTick = ceil(yMin / yStep) * yStep
        while (yTick <= yMax + 0.01) {
            val y = yOf(yTick)
            drawLine(gridColor, Offset(padL, y), Offset(w - padR, y), 1f)
            nc.drawText(
                "%.0f%%".format(yTick),
                padL - 6f, y + 8f,
                android.graphics.Paint().apply {
                    color     = textColor.copy(alpha = 0.65f).toArgb()
                    textSize  = 22f
                    textAlign = android.graphics.Paint.Align.RIGHT
                    isAntiAlias = true
                }
            )
            yTick += yStep
        }

        // X labels — only drawn when SoH drops vs the previous point,
        // plus always the first point. This keeps the axis uncluttered:
        // a flat SoH period shows no intermediate dates (they'd all look
        // the same anyway), and a drop immediately flags when it happened.
        val xLabelPaint = android.graphics.Paint().apply {
            color     = textColor.copy(alpha = 0.65f).toArgb()
            textSize  = 20f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        data.forEachIndexed { i, pt ->
            val shouldLabel = i == 0 ||
                pt.avgSoh < data[i - 1].avgSoh   // SoH dropped — mark when
            if (shouldLabel) {
                nc.drawText(
                    fmt.format(Date(pt.timestamp)),
                    xOf(pt.timestamp.toDouble()),
                    h - 4f,
                    xLabelPaint
                )
            }
        }

        // ── Trend line (solid orange) ──────────────────────────────────────────
        if (data.size >= 2) {
            val trendPath = Path().apply {
                moveTo(xOf(xMinMs), yOf(regression.predict(xMinMs)).coerceIn(padT, padT + chartH))
                lineTo(xOf(data.last().timestamp.toDouble()),
                    yOf(regression.predict(data.last().timestamp.toDouble()))
                        .coerceIn(padT, padT + chartH))
            }
            drawPath(trendPath, trendColor,
                style = Stroke(width = 2.5f, cap = StrokeCap.Round))

            // ── Projected line (dashed, faded) ────────────────────────────────
            val projEnd = xMaxMs
            val projPath = Path().apply {
                moveTo(xOf(data.last().timestamp.toDouble()),
                    yOf(regression.predict(data.last().timestamp.toDouble()))
                        .coerceIn(padT, padT + chartH))
                lineTo(xOf(projEnd),
                    yOf(regression.predict(projEnd)).coerceIn(padT, padT + chartH))
            }
            drawPath(projPath, projectedColor,
                style = Stroke(width = 2f, cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))))
        }

        // ── Actual data line ───────────────────────────────────────────────────
        if (data.size >= 2) {
            val linePath = Path().apply {
                moveTo(xOf(data[0].timestamp.toDouble()), yOf(data[0].avgSoh))
                data.drop(1).forEach { pt ->
                    lineTo(xOf(pt.timestamp.toDouble()), yOf(pt.avgSoh))
                }
            }
            drawPath(linePath, lineColor,
                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // ── Data dots ─────────────────────────────────────────────────────────
        data.forEach { pt ->
            val x = xOf(pt.timestamp.toDouble())
            val y = yOf(pt.avgSoh)
            drawCircle(dotGlow, 14f, Offset(x, y))
            drawCircle(dotFill,  6f, Offset(x, y))
            drawCircle(Color.White, 2.5f, Offset(x, y))
        }
    }
}

// ── Regression helpers ────────────────────────────────────────────────────────

private data class RegressionResult(
    val slope          : Double,  // SoH % per millisecond
    val intercept      : Double,
    val projectedAt80Ms: Long?    // null if trend is flat or going up
) {
    fun predict(ms: Double): Double = slope * ms + intercept
}

private fun linearRegression(data: List<DashboardViewModel.SohDataPoint>): RegressionResult {
    val xs  = data.map { it.timestamp.toDouble() }
    val ys  = data.map { it.avgSoh }
    val xMean = xs.average()
    val yMean = ys.average()
    val num   = xs.zip(ys).sumOf { (x, y) -> (x - xMean) * (y - yMean) }
    val den   = xs.sumOf { x -> (x - xMean).pow(2) }
    val slope = if (den == 0.0) 0.0 else num / den
    val intercept = yMean - slope * xMean

    // Project when SoH hits 80% — only meaningful if trend is declining
    val proj80Ms: Long? = if (slope < 0) {
        val ms = (80.0 - intercept) / slope
        if (ms > data.last().timestamp) ms.toLong() else null
    } else null

    return RegressionResult(slope, intercept, proj80Ms)
}

private data class DegradationStats(
    val declinePerYear   : Double,
    val projectedAt80Label: String
)

private fun degradationStats(
    regression: RegressionResult
): DegradationStats {
    // slope is % per ms → convert to % per year
    val msPerYear      = 365.25 * 24 * 3_600_000.0
    val declinePerYear = -regression.slope * msPerYear  // positive = declining

    val projLabel = when {
        regression.slope >= 0 -> "Not declining"
        regression.projectedAt80Ms == null -> "Far future"
        else -> {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = regression.projectedAt80Ms
            }
            "${calendar.get(Calendar.YEAR)}"
        }
    }
    return DegradationStats(declinePerYear.coerceAtLeast(0.0), projLabel)
}

// ── Small composables ─────────────────────────────────────────────────────────

@Composable
private fun DegradationStatCard(
    modifier : Modifier,
    label    : String,
    value    : String,
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    color    : Color
) {
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier            = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProBadge() {
    Box(
        modifier = Modifier
            .background(
                BydElectricAzure,
                androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            "PRO",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun HealthBand(range: String, label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(12.dp)
                .background(color, androidx.compose.foundation.shape.CircleShape)
        )
        Text(range, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.width(80.dp))
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(8.dp).background(color,
            androidx.compose.foundation.shape.CircleShape))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun sohColor(soh: Double): Color = when {
    soh >= 95 -> RegenGreen
    soh >= 90 -> BatteryBlue
    soh >= 80 -> AccelerationOrange
    else      -> MaterialTheme.colorScheme.error
}
