package com.byd.tripstats.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.tripstats.data.entitlement.EntitlementManager
import com.byd.tripstats.ui.theme.*
import com.byd.tripstats.ui.viewmodel.DashboardViewModel
import com.byd.tripstats.util.BatteryHealthReport
import com.byd.tripstats.ui.screens.batterydegrad.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
                        textAlign = TextAlign.Center
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
                            textAlign = TextAlign.Center
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
        val regression  = computeLinearRegression(data)
        val stats       = buildDegradationScreenStats(regression)

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
