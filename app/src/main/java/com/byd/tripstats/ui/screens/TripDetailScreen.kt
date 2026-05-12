package com.byd.tripstats.ui.screens

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.byd.tripstats.data.analysis.calculateTripEnergyBreakdown
import com.byd.tripstats.data.config.CarConfig
import com.byd.tripstats.data.local.entity.TripEntity
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import com.byd.tripstats.ui.components.AltitudeChart
import com.byd.tripstats.ui.components.CondensedAltitudeChart
import com.byd.tripstats.ui.components.CondensedEnergyChart
import com.byd.tripstats.ui.components.CondensedMotorRpmChart
import com.byd.tripstats.ui.components.CondensedSocChart
import com.byd.tripstats.ui.components.CondensedPowerChart
import com.byd.tripstats.ui.components.CondensedSpeedChart
import com.byd.tripstats.ui.components.BatteryVoltageChart
import com.byd.tripstats.ui.components.CondensedBatteryVoltageChart
import com.byd.tripstats.ui.components.CondensedInstantConsumptionChart
import com.byd.tripstats.ui.components.CondensedTyrePressureChart
import com.byd.tripstats.ui.components.EnergyConsumptionChart
import com.byd.tripstats.ui.components.InstantConsumptionChart
import com.byd.tripstats.ui.components.ModeTimelineChart
import com.byd.tripstats.ui.components.ModeTimelineControls
import com.byd.tripstats.ui.components.TyrePressureChart
import com.byd.tripstats.ui.components.MotorRpmChart
import com.byd.tripstats.ui.components.OsmRouteMap
import com.byd.tripstats.ui.components.PowerChart
import com.byd.tripstats.ui.components.RouteAnalysisTab
import com.byd.tripstats.ui.components.TripHeatmapsTab
import com.byd.tripstats.ui.components.SocChart
import com.byd.tripstats.ui.components.SpeedChart
import com.byd.tripstats.data.preferences.UnitSystem
import com.byd.tripstats.data.preferences.consumptionUnit
import com.byd.tripstats.data.preferences.convertDistance
import com.byd.tripstats.data.preferences.convertEfficiency
import com.byd.tripstats.data.preferences.distanceUnit
import com.byd.tripstats.data.preferences.isImperial
import com.byd.tripstats.data.preferences.speedUnit
import com.byd.tripstats.ui.theme.*
import com.byd.tripstats.ui.viewmodel.DashboardViewModel
import kotlin.math.abs
import com.byd.tripstats.ui.components.condenseData
import com.byd.tripstats.ui.components.condenseForRpm
import com.byd.tripstats.ui.components.condenseForSpeed
import com.byd.tripstats.ui.components.condenseForPower
import com.byd.tripstats.ui.components.extractTripModes
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    tripId: Long,
    viewModel: DashboardViewModel,
    onNavigateBack: () -> Unit
) {
    val trip by remember(tripId) { viewModel.getTripDetails(tripId) }.collectAsState()
    val dataPoints by remember(tripId) { viewModel.getTripDataPoints(tripId) }.collectAsState()
    val stats by remember(tripId) { viewModel.getTripStats(tripId) }.collectAsState()
    val tripMetrics by viewModel.tripDisplayMetrics.collectAsState()
    val regenEfficiencyPct = tripMetrics[tripId]?.regenEfficiencyPct
    val electricityPrice by viewModel.electricityPricePerKwh.collectAsState()
    val currencySymbol   by viewModel.currencySymbol.collectAsState()
    val unitSystem       by viewModel.unitSystem.collectAsState()
    val chargingSessions by viewModel.allChargingSessions.collectAsState()
    val tripAdditionalChargingCosts by viewModel.tripAdditionalChargingCosts.collectAsState()
    val selectedCarConfig by viewModel.selectedCarConfig.collectAsState(initial = null)

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Charts", "Heatmaps", "Route", "Analysis")

    // Capture data snapshot ONCE when dialog is requested
    // Use nullable Pair - null means dialog is closed
    var dialogData by remember { 
        mutableStateOf<Pair<TripEntity, List<TripDataPointEntity>>?>(null) 
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Details", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(28.dp))
                    }
                },
                actions = {
                    // Export/Share button
                    IconButton(
                        onClick = { 
                            // Capture immutable snapshot when button clicked
                            trip?.let { currentTrip ->
                                dialogData = currentTrip to dataPoints.toList()
                            }
                        }
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = "Export trip data",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        if (trip == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(60.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Tab selector
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 18.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
                
                // Tab content - constrained to remaining space
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (selectedTab) {
                        0 -> TripOverviewTab(
                            trip = trip!!,
                            stats = stats,
                            dataPoints = dataPoints,
                            selectedCarConfig = selectedCarConfig,
                            regenEfficiencyPct = regenEfficiencyPct,
                            electricityPrice = electricityPrice,
                            currencySymbol = currencySymbol,
                            unitSystem = unitSystem,
                            chargingSessions = chargingSessions,
                            additionalChargingCost = tripAdditionalChargingCosts[tripId] ?: 0.0,
                            onSaveAdditionalChargingCost = { amount ->
                                viewModel.saveTripAdditionalChargingCost(tripId, amount)
                            }
                        )
                        1 -> TripChartsTab(dataPoints = dataPoints, useImperial = unitSystem.isImperial)
                        2 -> TripHeatmapsTab(dataPoints = dataPoints)
                        3 -> TripRouteTab(dataPoints = dataPoints)
                        4 -> RouteAnalysisTab(
                            trip = trip,
                            dataPoints = dataPoints,
                            useImperial = unitSystem.isImperial
                        )
                    }
                }
            }
        }
    }

    // Show dialog ONLY when we have captured data
    // Uses IMMUTABLE snapshot - won't recompose when live data updates
    dialogData?.let { (capturedTrip, capturedPoints) ->
        ExportDialog(
            trip = capturedTrip,
            dataPoints = capturedPoints,
            onDismiss = { 
                dialogData = null  // Clear snapshot on dismiss
            }
        )
    }
}

@Composable
fun ExportDialog(
    trip: com.byd.tripstats.data.local.entity.TripEntity,
    dataPoints: List<com.byd.tripstats.data.local.entity.TripDataPointEntity>,
    onDismiss: () -> Unit
) {
    // Capture stable references
    val context          = androidx.compose.ui.platform.LocalContext.current
    val stableTrip       = remember { trip }
    val stableDataPoints = remember { dataPoints.toList() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text("Export Trip Data", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // ── Clipboard ─────────────────────────────────────────────────
                OutlinedButton(
                    onClick = {
                        copyTripSummaryToClipboard(context, stableTrip)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy Summary to Clipboard")
                }

                HorizontalDivider()

                // ── Downloads ─────────────────────────────────────────────────
                Text(
                    "Save to Download folder:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = {
                        saveTripAsCSV(context, stableTrip, stableDataPoints)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.TableChart, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save as CSV")
                }

                OutlinedButton(
                    onClick = {
                        saveTripAsJSON(context, stableTrip, stableDataPoints)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.DataObject, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save as JSON")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Copy summary to clipboard
fun copyTripSummaryToClipboard(
    context: android.content.Context,
    trip: com.byd.tripstats.data.local.entity.TripEntity
) {
    val summary = buildString {
        appendLine("🚗 BYD Trip Stats")
        appendLine("")
        appendLine("📅 Date: ${formatTimestamp(trip.startTime)}")
        appendLine("🛣️ Distance: ${String.format("%.1f", trip.distance ?: 0.0)} km")
        appendLine("⏱️ Duration: ${formatDuration(trip.duration ?: 0)}")
        appendLine("⚡ Energy: ${String.format("%.2f", trip.energyConsumed ?: 0.0)} kWh")
        appendLine("🌿 Consumption: ${String.format("%.1f", trip.efficiency ?: 0.0)} kWh/100km")
        appendLine("🔋 SOC: ${String.format("%.1f", trip.startSoc)}% → ${String.format("%.1f", trip.endSoc ?: 0.0)}%")
        appendLine("⚡ Max Power: ${trip.maxPower.toInt()} kW")
        appendLine("🔋 Max Regen: ${kotlin.math.abs(trip.maxRegenPower).toInt()} kW")
        appendLine("🏎️ Max Speed: ${trip.maxSpeed.toInt()} km/h")
    }
    
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = android.content.ClipData.newPlainText("Trip Summary", summary)
    clipboard.setPrimaryClip(clip)
    
    android.widget.Toast.makeText(
        context,
        "Trip summary copied to clipboard!",
        android.widget.Toast.LENGTH_SHORT
    ).show()
}

/**
 * Save trip data as CSV directly to the device's Download folder.
 *
 * Uses MediaStore.Downloads (API 29+) — no WRITE_EXTERNAL_STORAGE permission needed.
 * The file will appear in Download and be accessible via any file manager on the device.
 */
fun buildTripCsv(
    dataPoints: List<com.byd.tripstats.data.local.entity.TripDataPointEntity>
): String = buildString {
    appendLine("timestamp,latitude,longitude,altitude,speed,power,soc,odometer,batteryTemp,gear,engineSpeedFront,engineSpeedRear")
    dataPoints.forEach { point ->
        appendLine("${point.timestamp},${point.latitude},${point.longitude},${point.altitude},${point.speed},${point.power},${point.soc},${point.odometer},${point.batteryTemp},${point.gear},${point.engineSpeedFront},${point.engineSpeedRear}")
    }
}

fun saveTripAsCSV(
    context: android.content.Context,
    trip: com.byd.tripstats.data.local.entity.TripEntity,
    dataPoints: List<com.byd.tripstats.data.local.entity.TripDataPointEntity>
) {
    try {
        val fileName = "trip_${trip.id}_${System.currentTimeMillis()}.csv"
        saveToDownloads(context, fileName, "text/csv", buildTripCsv(dataPoints))
    } catch (e: Exception) {
        Log.e("TripDetailScreen", "Save CSV failed", e)
        android.widget.Toast.makeText(context, "Save failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

/**
 * Save trip data as JSON directly to the device's Download folder.
 */
fun buildTripJson(
    trip: com.byd.tripstats.data.local.entity.TripEntity,
    dataPoints: List<com.byd.tripstats.data.local.entity.TripDataPointEntity>
): String = buildString {
    appendLine("{")
    appendLine("  \"tripId\": ${trip.id},")
    appendLine("  \"startTime\": ${trip.startTime},")
    appendLine("  \"endTime\": ${trip.endTime},")
    appendLine("  \"distance\": ${trip.distance},")
    appendLine("  \"duration\": ${trip.duration},")
    appendLine("  \"consumption\": ${trip.efficiency},")
    appendLine("  \"energyConsumed\": ${trip.energyConsumed},")
    appendLine("  \"maxSpeed\": ${trip.maxSpeed},")
    appendLine("  \"maxPower\": ${trip.maxPower},")
    appendLine("  \"dataPoints\": [")
    dataPoints.forEachIndexed { index, point ->
        appendLine("    {")
        appendLine("      \"timestamp\": ${point.timestamp},")
        appendLine("      \"latitude\": ${point.latitude},")
        appendLine("      \"longitude\": ${point.longitude},")
        appendLine("      \"altitude\": ${point.altitude},")
        appendLine("      \"speed\": ${point.speed},")
        appendLine("      \"power\": ${point.power},")
        appendLine("      \"soc\": ${point.soc},")
        appendLine("      \"gear\": \"${point.gear}\",")
        appendLine("      \"engineSpeedFront\": ${point.engineSpeedFront},")
        appendLine("      \"engineSpeedRear\": ${point.engineSpeedRear}")
        appendLine("    }${if (index < dataPoints.size - 1) "," else ""}")
    }
    appendLine("  ]")
    appendLine("}")
}

fun saveTripAsJSON(
    context: android.content.Context,
    trip: com.byd.tripstats.data.local.entity.TripEntity,
    dataPoints: List<com.byd.tripstats.data.local.entity.TripDataPointEntity>
) {
    try {
        val fileName = "trip_${trip.id}_${System.currentTimeMillis()}.json"
        saveToDownloads(context, fileName, "application/json", buildTripJson(trip, dataPoints))
    } catch (e: Exception) {
        Log.e("TripDetailScreen", "Save JSON failed", e)
        android.widget.Toast.makeText(context, "Save failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

/**
 * Core helper — writes text content to the public Downloads folder using MediaStore.
 * Works on API 29+ without any storage permissions.
 * Shows a toast confirming success with the file name.
 */
private fun saveToDownloads(
    context: android.content.Context,
    fileName: String,
    mimeType: String,
    content: String
) {
    val values = android.content.ContentValues().apply {
        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType)
        put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
        put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: throw Exception("Could not create file in Download")

    resolver.openOutputStream(uri)?.use { stream ->
        stream.write(content.toByteArray(Charsets.UTF_8))
    } ?: throw Exception("Could not open output stream")

    // Mark file as complete so it becomes visible immediately
    values.clear()
    values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
    resolver.update(uri, values, null, null)

    android.widget.Toast.makeText(
        context,
        "Saved to Download: $fileName",
        android.widget.Toast.LENGTH_LONG
    ).show()
}

@Composable
fun TripOverviewTab(
    trip: com.byd.tripstats.data.local.entity.TripEntity,
    stats: com.byd.tripstats.data.local.entity.TripStatsEntity?,
    dataPoints: List<com.byd.tripstats.data.local.entity.TripDataPointEntity>,
    selectedCarConfig: CarConfig?,
    regenEfficiencyPct: Double?,
    electricityPrice: Double = 0.0,
    currencySymbol: String = "€",
    unitSystem: UnitSystem = UnitSystem.METRIC,
    chargingSessions: List<com.byd.tripstats.data.local.entity.ChargingSessionEntity> = emptyList(),
    additionalChargingCost: Double = 0.0,
    onSaveAdditionalChargingCost: (Double?) -> Unit = {}
) {
    val energyBreakdown = remember(dataPoints, selectedCarConfig, trip.energyConsumed) {
        calculateTripEnergyBreakdown(
            dataPoints = dataPoints,
            carConfig = selectedCarConfig,
            totalEnergyConsumedKwh = trip.energyConsumed
        )
    }
    val tripEnd = trip.endTime ?: System.currentTimeMillis()
    val overlappingChargingSessions = remember(trip, tripEnd, chargingSessions) {
        chargingSessions.filter { session ->
            val sessionEnd = session.endTime ?: session.startTime
            session.startTime <= tripEnd && sessionEnd >= trip.startTime
        }
    }
    val overlappingChargingKwh = remember(overlappingChargingSessions) {
        overlappingChargingSessions.sumOf { it.kwhAdded ?: 0.0 }
    }
    val tariffTripCost = remember(trip, electricityPrice) {
        trip.energyConsumed?.takeIf { electricityPrice > 0.0 }?.let { it * electricityPrice }
    }
    val tariffDeductionKwh = remember(trip, overlappingChargingKwh) {
        minOf(overlappingChargingKwh, trip.energyConsumed ?: 0.0)
    }
    val tariffDeductionCost = remember(tariffDeductionKwh, electricityPrice) {
        tariffDeductionKwh * electricityPrice
    }
    val adjustedTripCost = remember(tariffTripCost, tariffDeductionCost, additionalChargingCost) {
        tariffTripCost?.minus(tariffDeductionCost)?.plus(additionalChargingCost)
    }
    var showChargingCostDialog by remember { mutableStateOf(false) }
    var chargingCostInput by remember(additionalChargingCost) {
        mutableStateOf(if (additionalChargingCost > 0.0) "%.2f".format(additionalChargingCost) else "")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Key metrics cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MetricCard(
                title = "Distance",
                value = String.format("%.1f", unitSystem.convertDistance(trip.distance ?: 0.0)),
                unit = unitSystem.distanceUnit,
                icon = Icons.Filled.Route,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .weight(1f)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.medium
                    )
            )
            
            MetricCard(
                title = "Duration",
                value = formatDuration(trip.duration ?: 0),
                unit = "",
                icon = Icons.Filled.Timer,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.medium
                    )
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MetricCard(
                title = "Energy consumed",
                value = String.format("%.2f", trip.energyConsumed ?: 0.0),
                unit = "kWh",
                icon = Icons.Filled.BatteryChargingFull,
                color = AccelerationOrange,
                modifier = Modifier
                    .weight(1f)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.medium
                    )
            )
            
            MetricCard(
                title = "Average consumption",
                value = String.format("%.2f", unitSystem.convertEfficiency(trip.efficiency ?: 0.0)),
                unit = "kWh / 100${unitSystem.distanceUnit}",
                icon = Icons.Filled.Eco,
                color = RegenGreen,
                modifier = Modifier
                    .weight(1f)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.medium
                    )
            )
        }
        
        // Detailed stats
        Card(
            modifier = Modifier
                .fillMaxWidth()
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
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Trip Statistics",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                DetailRow("Start Time", formatTimestamp(trip.startTime))
                DetailRow("End Time", trip.endTime?.let { formatTimestamp(it) } ?: "In Progress")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp),color = (MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)))

                DetailRow("Initial mileage", "${String.format("%.1f", trip.startOdometer)} ${unitSystem.distanceUnit}")
                DetailRow("Final mileage", trip.endOdometer?.let { "${String.format("%.1f", it)} ${unitSystem.distanceUnit}" } ?: "-")
                DetailRow("Trip distance", trip.distance?.let { "${String.format("%.1f", unitSystem.convertDistance(it))} ${unitSystem.distanceUnit}" } ?: "-")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp),color = (MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)))

                DetailRow("Start SOC", "${String.format("%.1f", trip.startSoc)}%")
                DetailRow("End SOC", trip.endSoc?.let { "${String.format("%.1f", it)}%" } ?: "-")
                DetailRow("SOC Change", trip.socDelta?.let { "${String.format("%.1f", it)}%" } ?: "-")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp),color = (MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)))

                DetailRow("Max Speed", "${trip.maxSpeed.toInt()} ${unitSystem.speedUnit}")
                DetailRow("Avg Speed", stats?.avgSpeed?.toInt()?.toString()?.plus(" ${unitSystem.speedUnit}") ?: "-")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp),color = (MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)))

                DetailRow("Max Power", "${trip.maxPower.toInt()} kW")
                DetailRow("Max Regen", "${abs(trip.maxRegenPower).toInt()} kW")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp),color = (MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)))

                DetailRow("Energy consumed", trip.energyConsumed?.let { String.format("%.2f kWh", it) } ?: "-")
                if (electricityPrice > 0.0) {
                    if (overlappingChargingKwh > 0.01) {
                        DetailRow(
                            "Trip cost @ fixed tariff",
                            tariffTripCost?.let { "${currencySymbol}${String.format("%.2f", it)}" } ?: "-"
                        )
                        DetailRow("En-route charging added", String.format("%.2f kWh", overlappingChargingKwh))
                        DetailRow("Tariff deduction", "-${currencySymbol}${String.format("%.2f", tariffDeductionCost)}")
                        EditableDetailRow(
                            label = "Custom DC charging cost",
                            value = if (additionalChargingCost > 0.0) {
                                "${currencySymbol}${String.format("%.2f", additionalChargingCost)}"
                            } else {
                                "Set cost"
                            },
                            onEdit = { showChargingCostDialog = true }
                        )
                        DetailRow(
                            "Adjusted trip cost",
                            if (additionalChargingCost > 0.0 && adjustedTripCost != null) {
                                "${currencySymbol}${String.format("%.2f", adjustedTripCost)}"
                            } else {
                                "Set DC cost"
                            }
                        )
                    } else {
                        DetailRow("Trip cost", tariffTripCost?.let { "${currencySymbol}${String.format("%.2f", it)}" } ?: "-")
                    }
                }
                DetailRow("Energy regenerated", stats?.totalRegenEnergy?.let { String.format("%.2f kWh", it) } ?: "-")
                DetailRow("Gross energy consumed", String.format("%.2f kWh", (trip.energyConsumed ?: 0.0) + (stats?.totalRegenEnergy ?: 0.0)))
                DetailRow("Regeneration efficiency", regenEfficiencyPct?.let { String.format("%.2f%%", it) } ?: "-")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp),color = (MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)))

                DetailRow("Battery Temp Range", tripBatteryTempRangeLabel(trip))
                DetailRow("Avg Battery Temp", tripBatteryAvgTempLabel(trip))
            }
        }

        energyBreakdown?.let { breakdown ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
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
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Energy Breakdown",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    val caveats = buildList {
                        if (!breakdown.hasPhysicsBreakdown) add("Rolling, aero, and gradient estimates require vehicle mass")
                        if (!breakdown.hasAeroEstimate) add("Aerodynamic estimate requires CdA for this model")
                    }
                    if (caveats.isNotEmpty()) {
                        Text(
                            text = caveats.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )

                    // ── Total ─────────────────────────────────────────────────
                    DetailRow("Total consumed", formatKwh(breakdown.totalConsumedKwh))

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                    )

                    // Scale modelled components so they always sum to totalConsumedKwh.
                    // When the physics model over-estimates (GPS/Crr noise), raw modelled
                    // values exceed consumed — scaling down keeps the breakdown coherent.
                    // When it under-estimates, the remainder shows as auxiliary losses.
                    val modelledRaw = breakdown.rollingResistanceKwh +
                        breakdown.aeroDragKwh +
                        breakdown.netGradientKwh.coerceAtLeast(0.0)
                    val scale = if (modelledRaw > breakdown.totalConsumedKwh && modelledRaw > 0.0)
                        breakdown.totalConsumedKwh / modelledRaw else 1.0
                    val rollingDisplay  = breakdown.rollingResistanceKwh * scale
                    val aeroDisplay     = breakdown.aeroDragKwh * scale
                    val climbDisplay    = breakdown.climbKwh * scale
                    val descentDisplay  = breakdown.descentKwh * scale
                    val netGradDisplay  = breakdown.netGradientKwh * scale
                    val auxDisplay      = (breakdown.totalConsumedKwh - modelledRaw * scale).coerceAtLeast(0.0)
                    fun pct(kwh: Double) = if (breakdown.totalConsumedKwh > 0.0)
                        String.format("%.1f", kwh / breakdown.totalConsumedKwh * 100.0) else "0.0"

                    // ── Rolling resistance ────────────────────────────────────
                    DetailRow(
                        "Rolling resistance",
                        if (breakdown.hasPhysicsBreakdown) {
                            "${formatKwh(rollingDisplay)} (${pct(rollingDisplay)}%)"
                        } else "n/a"
                    )

                    // ── Aerodynamic drag ──────────────────────────────────────
                    DetailRow(
                        "Aerodynamic drag",
                        if (breakdown.hasAeroEstimate) {
                            "${formatKwh(aeroDisplay)} (${pct(aeroDisplay)}%)"
                        } else "n/a"
                    )

                    // ── Gradient ──────────────────────────────────────────────
                    DetailRow(
                        "Climb",
                        if (breakdown.hasGradientEstimate) formatKwh(climbDisplay) else "n/a"
                    )
                    DetailRow(
                        "Descent (recovery)",
                        if (breakdown.hasGradientEstimate) "−${formatKwh(descentDisplay)}" else "n/a"
                    )
                    DetailRow(
                        "Net gradient",
                        if (breakdown.hasGradientEstimate) {
                            val signed = if (netGradDisplay >= 0.0) "+${formatKwh(netGradDisplay)}"
                                         else "−${formatKwh(-netGradDisplay)}"
                            "$signed (${pct(netGradDisplay.coerceAtLeast(0.0))}%)"
                        } else "n/a"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                    )

                    // ── Auxiliary losses ──────────────────────────────────────
                    DetailRow(
                        "Auxiliary losses",
                        if (breakdown.hasPhysicsBreakdown) {
                            "${formatKwh(auxDisplay)} (${pct(auxDisplay)}%)"
                        } else "n/a"
                    )
                    Text(
                        text = "12 V system, HVAC, and model residual. Shown as remainder after rolling, aero, and gradient. Motor/inverter losses are factored into the figures above",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                    )

                    // ── Vehicle metadata ──────────────────────────────────────
                    DetailRow(
                        "Vehicle mass",
                        breakdown.estimatedKerbMassKg?.let { "${it.toInt()} kg (estimate)" } ?: "n/a"
                    )
                    DetailRow(
                        "CdA (drag area)",
                        breakdown.cdA?.let { String.format("%.3f m²", it) } ?: "n/a"
                    )
                }
            }
        }
    }

    if (showChargingCostDialog) {
        AlertDialog(
            onDismissRequest = { showChargingCostDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Custom DC charging cost", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter the total amount you paid for DC charging during this trip. The app will deduct the overlapping charging energy from the fixed home-tariff estimate and add this custom cost instead.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = chargingCostInput,
                        onValueChange = { chargingCostInput = it },
                        label = { Text("Total DC charging cost") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        prefix = { Text(currencySymbol) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = chargingCostInput.replace(',', '.').toDoubleOrNull()
                    onSaveAdditionalChargingCost(parsed?.takeIf { it > 0.0 })
                    showChargingCostDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (additionalChargingCost > 0.0) {
                        TextButton(onClick = {
                            chargingCostInput = ""
                            onSaveAdditionalChargingCost(null)
                            showChargingCostDialog = false
                        }) { Text("Clear") }
                    }
                    TextButton(onClick = { showChargingCostDialog = false }) { Text("Cancel") }
                }
            }
        )
    }
}

private fun tripBatteryTempRangeLabel(trip: TripEntity): String {
    fun isValidCellTemp(value: Int): Boolean = value in -40..120
    val min = trip.minBatteryCellTemp.takeIf(::isValidCellTemp)
    val max = trip.maxBatteryCellTemp.takeIf(::isValidCellTemp)
    return when {
        min != null && max != null && max > min -> "${min}°C - ${max}°C"
        min != null && max != null -> "${max}°C"
        min != null -> "${min}°C"
        max != null -> "${max}°C"
        else -> "-"
    }
}

private fun tripBatteryAvgTempLabel(trip: TripEntity): String =
    trip.avgBatteryTemp
        .takeIf { it.isFinite() && it in -40.0..120.0 }
        ?.let { "${it.toInt()}°C" }
        ?: "-"

private fun formatKwh(value: Double): String =
    String.format("%.2f kWh", value)

private fun formatSignedKwh(value: Double): String =
    if (value >= 0.0) "+${formatKwh(value)}" else "-${formatKwh(abs(value))}"

@Composable
fun TripChartsTab(
    dataPoints: List<com.byd.tripstats.data.local.entity.TripDataPointEntity>,
    useImperial: Boolean = false
) {
    // Track which chart is expanded
    var expandedChart by remember { mutableStateOf<ChartType?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Speed Profile
        ClickableChartCard(
            title = "Speed Profile",
            subtitle = "${if (useImperial) "mph" else "km/h"} over time",
            onClick = { expandedChart = ChartType.SPEED }
        ) {
            CondensedSpeedChart(
                dataPoints = dataPoints,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Motor RPM
        ClickableChartCard(
            title = "Motor RPM",
            subtitle = "RPM over time",
            onClick = { expandedChart = ChartType.MOTOR_RPM }
        ) {
            CondensedMotorRpmChart(
                dataPoints = dataPoints,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. Power Profile
        ClickableChartCard(
            title = "Power Profile",
            subtitle = "kW over time",
            onClick = { expandedChart = ChartType.POWER }
        ) {
            CondensedPowerChart(
                dataPoints = dataPoints,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 4. Energy Consumption
        ClickableChartCard(
            title = "Energy Consumption",
            subtitle = "kWh over time",
            onClick = { expandedChart = ChartType.ENERGY }
        ) {
            CondensedEnergyChart(
                dataPoints = dataPoints,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 5. State of Charge
        ClickableChartCard(
            title = "State of Charge",
            subtitle = "SoC% over time",
            onClick = { expandedChart = ChartType.SOC }
        ) {
            CondensedSocChart(
                dataPoints = dataPoints,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 6. Elevation Profile
        ClickableChartCard(
            title = "Elevation Profile",
            subtitle = "Altitude over time",
            onClick = { expandedChart = ChartType.ALTITUDE }
        ) {
            CondensedAltitudeChart(
                dataPoints = dataPoints,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 7. Battery Voltage
        ClickableChartCard(
            title = "Battery Voltage",
            subtitle = "HV bus + cell min/max over time",
            onClick = { expandedChart = ChartType.BATTERY_VOLTAGE }
        ) {
            CondensedBatteryVoltageChart(
                dataPoints = dataPoints,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 8. Tyre Pressures
        ClickableChartCard(
            title = "Tyre Pressures",
            subtitle = "All four wheels (pressure) over time",
            onClick = { expandedChart = ChartType.TYRE_PRESSURE }
        ) {
            CondensedTyrePressureChart(
                dataPoints = dataPoints,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 9. Instantaneous Consumption
        ClickableChartCard(
            title = "Instantaneous Consumption",
            subtitle = "${if (useImperial) "kWh/100mi" else "kWh/100km"} — raw + rolling average",
            onClick = { expandedChart = ChartType.INSTANT_CONSUMPTION }
        ) {
            CondensedInstantConsumptionChart(
                dataPoints = dataPoints,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 10. Drive / Regen Modes
        ClickableChartCard(
            title = "Drive / Regen Modes",
            subtitle = "Mode timeline across the trip",
            onClick = { expandedChart = ChartType.MODE_TIMELINE },
            cardHeight = 360.dp
        ) {
            var showDriveModes by remember { mutableStateOf(true) }
            var showRegenModes by remember { mutableStateOf(true) }
            val missingDriveMode = remember(dataPoints) { dataPoints.none { it.extractTripModes().driveMode != 0 } }
            val missingRegenMode = remember(dataPoints) { dataPoints.none { it.extractTripModes().regenMode != 0 } }
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (missingDriveMode || missingRegenMode) {
                    ModeRecordingHint(missingDriveMode, missingRegenMode)
                }
                ModeTimelineControls(
                    showDriveModes = showDriveModes,
                    showRegenModes = showRegenModes,
                    onToggleDriveModes = { showDriveModes = !showDriveModes },
                    onToggleRegenModes = { showRegenModes = !showRegenModes }
                )
                ModeTimelineChart(
                    dataPoints = dataPoints,
                    showDriveModes = showDriveModes,
                    showRegenModes = showRegenModes,
                    compact = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

    }
    
    // Fullscreen chart dialog
    expandedChart?.let { chartType ->
        FullscreenChartDialog(
            chartType = chartType,
            dataPoints = dataPoints,  // Full data
            useImperial = useImperial,
            onDismiss = { expandedChart = null }
        )
    }
}

@Composable
private fun ClickableChartCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    cardHeight: Dp = 300.dp,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .clickable(onClick = onClick)
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Fullscreen,
                    contentDescription = "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

enum class ChartType {
    ENERGY, SPEED, MOTOR_RPM, ALTITUDE, SOC, POWER,
    BATTERY_VOLTAGE, TYRE_PRESSURE, INSTANT_CONSUMPTION, MODE_TIMELINE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullscreenChartDialog(
    chartType: ChartType,
    dataPoints: List<com.byd.tripstats.data.local.entity.TripDataPointEntity>,
    useImperial: Boolean = false,
    onDismiss: () -> Unit
) {
    // Most fullscreen charts still use a condensed dataset for readability,
    // but RPM needs an axle-aware reduction so short front/rear bursts are not
    // lost between buckets.
    val chartData = remember(chartType, dataPoints) {
        when (chartType) {
            ChartType.MOTOR_RPM -> condenseForRpm(dataPoints, maxPoints = 480)
            ChartType.SPEED     -> condenseForSpeed(dataPoints, maxPoints = 144)
            ChartType.POWER     -> condenseForPower(dataPoints, maxPoints = 144)
            else                -> condenseData(dataPoints, maxPoints = 144)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with close button
                TopAppBar(
                    title = {
                        Column {
                            // Main title
                            Text(
                                text = when (chartType) {
                                    ChartType.ENERGY -> "Energy Consumption (Detailed)"
                                    ChartType.SOC -> "State of Charge (Detailed)"
                                    ChartType.SPEED -> "Speed Profile (Detailed)"
                                    ChartType.MOTOR_RPM -> "Motor RPM (Detailed)"
                                    ChartType.ALTITUDE -> "Elevation Profile (Detailed)"
                                    ChartType.POWER -> "Power Profile (Detailed)"
                                    ChartType.BATTERY_VOLTAGE -> "Battery Voltage (Detailed)"
                                    ChartType.TYRE_PRESSURE -> "Tyre Pressures (Detailed)"
                                    ChartType.INSTANT_CONSUMPTION -> "Instantaneous Consumption (Detailed)"
                                    ChartType.MODE_TIMELINE -> "Drive / Regen Modes (Detailed)"
                                },
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            // Subtitle
                            Text(
                                text = when (chartType) {
                                    ChartType.ENERGY -> "kWh over time"
                                    ChartType.SOC -> "SoC% over time"
                                    ChartType.SPEED -> "${if (useImperial) "mph" else "km/h"} over time"
                                    ChartType.MOTOR_RPM -> "RPM over time"
                                    ChartType.ALTITUDE -> "Altitude over time"
                                    ChartType.POWER -> "kW over time"
                                    ChartType.BATTERY_VOLTAGE -> "HV bus + cell min/max (V)"
                                    ChartType.TYRE_PRESSURE -> "All four wheels (bar)"
                                    ChartType.INSTANT_CONSUMPTION -> "${if (useImperial) "kWh/100mi" else "kWh/100km"} over distance"
                                    ChartType.MODE_TIMELINE -> "Mode timeline over the trip"
                                },
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, "Close")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                
                // Chart with chart-specific condensed data
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    when (chartType) {
                        ChartType.ENERGY -> EnergyConsumptionChart(
                            dataPoints = chartData,
                            modifier = Modifier.fillMaxSize()
                        )
                        ChartType.SOC -> SocChart(
                            dataPoints = chartData,
                            modifier = Modifier.fillMaxSize()
                        )
                        ChartType.SPEED -> SpeedChart(
                            dataPoints = chartData,
                            useImperial = useImperial,
                            modifier = Modifier.fillMaxSize()
                        )
                        ChartType.MOTOR_RPM -> MotorRpmChart(
                            dataPoints = chartData,
                            modifier = Modifier.fillMaxSize()
                        )
                        ChartType.ALTITUDE -> AltitudeChart(
                            dataPoints = chartData,
                            modifier = Modifier.fillMaxSize()
                        )
                        ChartType.POWER -> PowerChart(
                            dataPoints = chartData,
                            modifier = Modifier.fillMaxSize()
                        )
                        ChartType.BATTERY_VOLTAGE -> BatteryVoltageChart(
                            dataPoints = chartData,
                            modifier = Modifier.fillMaxSize()
                        )
                        ChartType.TYRE_PRESSURE -> TyrePressureChart(
                            dataPoints = chartData,
                            modifier = Modifier.fillMaxSize()
                        )
                        ChartType.INSTANT_CONSUMPTION -> InstantConsumptionChart(
                            dataPoints = dataPoints,  // full data — chart filters internally
                            useImperial = useImperial,
                            modifier = Modifier.fillMaxSize()
                        )
                        ChartType.MODE_TIMELINE -> {
                            var showDriveModes by remember { mutableStateOf(true) }
                            var showRegenModes by remember { mutableStateOf(true) }
                            val missingDriveMode = remember(dataPoints) { dataPoints.none { it.extractTripModes().driveMode != 0 } }
                            val missingRegenMode = remember(dataPoints) { dataPoints.none { it.extractTripModes().regenMode != 0 } }
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                if (missingDriveMode || missingRegenMode) {
                                    ModeRecordingHint(missingDriveMode, missingRegenMode)
                                }
                                ModeTimelineControls(
                                    showDriveModes = showDriveModes,
                                    showRegenModes = showRegenModes,
                                    onToggleDriveModes = { showDriveModes = !showDriveModes },
                                    onToggleRegenModes = { showRegenModes = !showRegenModes }
                                )
                                ModeTimelineChart(
                                    dataPoints = dataPoints,
                                    showDriveModes = showDriveModes,
                                    showRegenModes = showRegenModes,
                                    modifier = Modifier.fillMaxSize()
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
private fun ModeRecordingHint(missingDriveMode: Boolean, missingRegenMode: Boolean) {
    val missing = buildList {
        if (missingDriveMode) add("drive")
        if (missingRegenMode) add("regen")
    }.joinToString(" and ")
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "No $missing mode recorded — on future trips tap each mode on the car display once to enable analytics.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TripRouteTab(
    dataPoints: List<com.byd.tripstats.data.local.entity.TripDataPointEntity>
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        OsmRouteMap(
            dataPoints = dataPoints,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun EditableDetailRow(label: String, value: String, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) {
        String.format("%dh %dmin", hours, minutes)
    } else {
        String.format("%dmin", minutes)
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val instant = java.time.Instant.ofEpochMilli(timestamp)
    val formatter = java.time.format.DateTimeFormatter
        .ofPattern("MMM dd, yyyy HH:mm")
        .withZone(java.time.ZoneId.systemDefault())
    return formatter.format(instant)
}
