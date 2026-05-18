package com.byd.tripstats.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import android.content.SharedPreferences
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.tripstats.data.config.CarCatalog
import com.byd.tripstats.data.config.Drivetrain
import com.byd.tripstats.data.model.BatteryVoltageHistoryPoint
import com.byd.tripstats.data.model.VehicleTelemetry
import com.byd.tripstats.data.preferences.PreferencesManager
import com.byd.tripstats.data.preferences.UnitSystem
import com.byd.tripstats.data.preferences.consumptionUnit
import com.byd.tripstats.data.preferences.convertDistance
import com.byd.tripstats.data.preferences.convertEfficiency
import com.byd.tripstats.data.preferences.distanceUnit
import com.byd.tripstats.data.preferences.convertSpeed
import com.byd.tripstats.data.preferences.isImperial
import com.byd.tripstats.data.preferences.speedUnit
import com.byd.tripstats.R
import com.byd.tripstats.ui.components.CompactBattery
import com.byd.tripstats.ui.components.LiquidFillBattery
import com.byd.tripstats.ui.components.RangeProjectionChart
import com.byd.tripstats.ui.components.RangeDataPoint
import com.byd.tripstats.ui.components.ConsumptionThumbnail
import com.byd.tripstats.ui.components.ConsumptionChartExpanded
import com.byd.tripstats.ui.components.drawCrosshair
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.byd.tripstats.ui.viewmodel.DashboardViewModel
import com.byd.tripstats.ui.theme.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import com.byd.tripstats.sdk.VehicleTelemetrySnapshot
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SHOW_MOCK_BUTTON = false  // Set to true for testing, false for production

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToHistory: () -> Unit,
    onNavigateToCharging: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToBatteryDegradation: () -> Unit = {}
) {
    val telemetry by viewModel.displayTelemetry.collectAsState()
    val vehicleSnapshot by viewModel.displayVehicleSnapshot.collectAsState()
    val batteryVoltageHistory24h by viewModel.batteryVoltageHistory24h.collectAsState()
    val isMockModeActive by viewModel.isMockModeActive.collectAsState()
    val isInTrip by viewModel.isInTrip.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val autoTripDetection by viewModel.autoTripDetection.collectAsState()
    val tripDataPoints by viewModel.tripDataPoints.collectAsState()
    val liveDistanceKm by viewModel.liveDistanceKm.collectAsState()
    val liveOdometerDistanceKm by viewModel.liveOdometerDistanceKm.collectAsState()
    val liveSegmentDistanceKm by viewModel.liveSegmentDistanceKm.collectAsState()
    val liveSessionStartMs by viewModel.liveSessionStartMs.collectAsState()
    val liveAccumulatedKwh by viewModel.liveAccumulatedKwh.collectAsState()
    val weeklyEfficiency by viewModel.weeklyEfficiency.collectAsState()
    val monthlyEfficiency by viewModel.monthlyEfficiency.collectAsState()
    val yearlyEfficiency by viewModel.yearlyEfficiency.collectAsState()

    val activity = LocalContext.current as androidx.activity.ComponentActivity
    val windowSizeClass = calculateWindowSizeClass(activity)
    val widthSizeClass = windowSizeClass.widthSizeClass

    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context.applicationContext) }
    val selectedCar by prefs.selectedCarConfig.collectAsState(initial = null)
    val dashboardIconsEnabled by prefs.dashboardAnimationsEnabled
        .collectAsState(initial = prefs.getCachedAnimationsEnabled())
    val scope = rememberCoroutineScope()
    var showCarSelectionDialog by remember { mutableStateOf(false) }
    var showBattery12vDialog by remember { mutableStateOf(false) }
    var consumptionExpanded by remember { mutableStateOf(false) }
    var showTyreUnitDialog by remember { mutableStateOf(false) }
    val tyrePrefs: SharedPreferences = remember { context.getSharedPreferences("tyre_unit_prefs", 0) }
    var tyreUnit by remember {
        mutableStateOf(
            TyrePressureUnit.entries.getOrElse(
                tyrePrefs.getInt("unit", TyrePressureUnit.BAR.ordinal)
            ) { TyrePressureUnit.BAR }
        )
    }

    val carCategories = remember {
        listOf(
            "Battery Electric (BEV)" to CarCatalog.groupedBev,
            "Plug-in Hybrid (PHEV / DM-i)" to CarCatalog.groupedPhev
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.byd_logo),
                            contentDescription = "BYD",
                            modifier = Modifier.height(28.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "trip stats",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )

                        selectedCar?.let { car ->
                            VerticalDivider(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .height(24.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f)
                            )
                            Text(
                                text = car.displayName.uppercase(),
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.clickable { showCarSelectionDialog = true }
                            )
                        }
                    }
                },
                actions = {
                    if (SHOW_MOCK_BUTTON) {
                        IconButton(onClick = { viewModel.startMockDrive() }) {
                            Icon(
                                imageVector = if (isMockModeActive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = if (isMockModeActive) "Stop Mock Drive" else "Mock Drive",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    if (!dashboardIconsEnabled) {
                        IconButton(onClick = onNavigateToCharging) {
                            CompactBattery(
                                soc = telemetry?.soc?.toFloat() ?: 0f,
                                isCharging = telemetry?.isCharging ?: false
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        IconButton(onClick = { consumptionExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.BarChart,
                                contentDescription = "Consumption Charts",
                                tint = RegenGreen,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = "Trip History",
                            tint = BatteryBlue,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    BadgedBox(
                        modifier = Modifier.padding(end = 8.dp),
                        badge = {
                            if (updateInfo != null) {
                                Badge(containerColor = AccelerationOrange) {
                                    Text(
                                        text = "1",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    ) {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        val currentTelemetry = telemetry
        if (currentTelemetry == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            DashboardContent(
                telemetry = currentTelemetry,
                vehicleSnapshot = vehicleSnapshot,
                isInTrip = isInTrip,
                autoTripDetection = autoTripDetection,
                tripDataPoints = tripDataPoints,
                weeklyEfficiency = weeklyEfficiency,
                monthlyEfficiency = monthlyEfficiency,
                yearlyEfficiency = yearlyEfficiency,
                onStartTrip = { viewModel.startManualTrip() },
                onEndTrip = { viewModel.endManualTrip() },
                onToggleAutoDetection = { viewModel.toggleAutoTripDetection() },
                onNavigateToBatteryDegradation = onNavigateToBatteryDegradation,
                onShowBattery12vHistory = { showBattery12vDialog = true },
                consumptionExpanded = consumptionExpanded,
                onConsumptionExpand = { consumptionExpanded = true },
                onConsumptionClose = { consumptionExpanded = false },
                tyreUnit = tyreUnit,
                onShowTyreDialog = { showTyreUnitDialog = true },
                dashboardIconsEnabled = dashboardIconsEnabled,
                onNavigateToCharging = onNavigateToCharging,
                widthSizeClass = widthSizeClass,
                sessionDistanceKm = liveSegmentDistanceKm,
                tripDistanceKm = liveDistanceKm,
                liveOdometerDistanceKm = liveOdometerDistanceKm,
                liveSessionStartMs = liveSessionStartMs,
                liveAccumulatedKwh = liveAccumulatedKwh,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }

    telemetry?.let { currentTelemetry ->
        if (showBattery12vDialog) {
            Battery12vHistoryDialog(
                historyPoints = batteryVoltageHistory24h,
                telemetry = currentTelemetry,
                vehicleSnapshot = vehicleSnapshot,
                onDismiss = { showBattery12vDialog = false }
            )
        }
    }

    if (showCarSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showCarSelectionDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = {
                Text("Select car")
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    CarSelectionPicker(
                        categories = carCategories,
                        selectedCarId = selectedCar?.id,
                        onCarSelected = { car ->
                            scope.launch { prefs.saveSelectedCar(car.id) }
                            showCarSelectionDialog = false
                        },
                        compact = true
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showCarSelectionDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showTyreUnitDialog) {
        AlertDialog(
            onDismissRequest = { showTyreUnitDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Tyre Pressure Unit") },
            text = {
                Column {
                    TyrePressureUnit.entries.forEach { u ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    tyreUnit = u
                                    tyrePrefs.edit().putInt("unit", u.ordinal).apply()
                                    showTyreUnitDialog = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = tyreUnit == u,
                                onClick = {
                                    tyreUnit = u
                                    tyrePrefs.edit().putInt("unit", u.ordinal).apply()
                                    showTyreUnitDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = when (u) {
                                    TyrePressureUnit.BAR -> "Bar  (default, e.g. 2.6)"
                                    TyrePressureUnit.PSI -> "PSI  (e.g. 37.7)"
                                    TyrePressureUnit.KPA -> "kPa  (e.g. 260)"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTyreUnitDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun DashboardContent(
    modifier: Modifier = Modifier,
    telemetry: VehicleTelemetry,
    vehicleSnapshot: VehicleTelemetrySnapshot? = null,
    isInTrip: Boolean,
    autoTripDetection: Boolean,
    tripDataPoints: List<RangeDataPoint>,
    weeklyEfficiency: List<DashboardViewModel.DailyEfficiency>,
    monthlyEfficiency: List<DashboardViewModel.DailyEfficiency>,
    yearlyEfficiency: List<DashboardViewModel.DailyEfficiency>,
    onStartTrip: () -> Unit,
    onEndTrip: () -> Unit,
    onToggleAutoDetection: () -> Unit,
    onNavigateToBatteryDegradation: () -> Unit = {},
    onShowBattery12vHistory: () -> Unit = {},
    consumptionExpanded: Boolean = false,
    onConsumptionExpand: () -> Unit = {},
    onConsumptionClose: () -> Unit = {},
    tyreUnit: TyrePressureUnit = TyrePressureUnit.BAR,
    onShowTyreDialog: () -> Unit = {},
    dashboardIconsEnabled: Boolean = true,
    onNavigateToCharging: () -> Unit = {},
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Expanded,
    sessionDistanceKm: Double = 0.0,
    tripDistanceKm: Double = 0.0,
    liveOdometerDistanceKm: Double = 0.0,
    liveSessionStartMs: Long? = null,
    liveAccumulatedKwh: Double = 0.0,
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context.applicationContext) }
    val unitSystem by prefs.unitSystem.collectAsState(initial = prefs.getCachedUnitSystem())
    // sessionDistanceKm is the current engine-on segment; tripDistanceKm is the
    // cumulative trip distance since trip start.

    val styledModifier = modifier.background(MaterialTheme.colorScheme.background)

    // Expanded  (>840 dp) → full landscape: side-by-side 85/15 — original layout
    // Medium    (600–840dp) → split-screen landscape or large tablet portrait: side-by-side 72/28
    // Compact   (<600 dp) → split-screen portrait or phone portrait: stacked vertically
    when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            // ── Compact: stacked portrait / narrow split-screen ───────────────
            // Stats moves above the energy diagram so nothing gets squeezed.
            Column(
                modifier = styledModifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Stats row at top — condensed horizontal strip
                VehicleStats(
                    telemetry = telemetry,
                    vehicleSnapshot = vehicleSnapshot,
                    modifier  = Modifier.fillMaxWidth(),
                    onNavigateToBatteryDegradation = onNavigateToBatteryDegradation,
                    onShowBattery12vHistory = onShowBattery12vHistory,
                    tyreUnit = tyreUnit,
                    onShowTyreDialog = onShowTyreDialog,
                    dashboardIconsEnabled = dashboardIconsEnabled
                )

                // Energy flow / range card
                EnergyFlowDiagram(
                    telemetry = telemetry,
                    tripDataPoints = tripDataPoints,
                    weeklyEfficiency = weeklyEfficiency,
                    monthlyEfficiency = monthlyEfficiency,
                    yearlyEfficiency = yearlyEfficiency,
                    sessionDistanceKm = sessionDistanceKm,
                    tripDistanceKm = tripDistanceKm,
                    consumptionExpanded = consumptionExpanded,
                    onConsumptionExpand = onConsumptionExpand,
                    onConsumptionClose = onConsumptionClose,
                    dashboardIconsEnabled = dashboardIconsEnabled,
                    onNavigateToCharging = onNavigateToCharging,
                    onShowTyreDialog = onShowTyreDialog,
                    tyreUnit = tyreUnit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp)
                        )
                )

                // Trip controls
                TripControls(
                    telemetry = telemetry,
                    isInTrip = isInTrip,
                    autoTripDetection = autoTripDetection,
                    onStartTrip = onStartTrip,
                    onEndTrip = onEndTrip,
                    onToggleAutoDetection = onToggleAutoDetection,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        WindowWidthSizeClass.Medium -> {
            // ── Medium: split-screen landscape or large portrait ─────────────
            // Same side-by-side structure but slightly wider right column so
            // stats don't get too cramped at 600–840 dp.
            Row(
                modifier = styledModifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left column - Energy Flow Diagram
                Column(
                    modifier = Modifier
                        .weight(0.72f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EnergyFlowDiagram(
                        telemetry = telemetry,
                        tripDataPoints = tripDataPoints,
                        weeklyEfficiency = weeklyEfficiency,
                        monthlyEfficiency = monthlyEfficiency,
                        yearlyEfficiency = yearlyEfficiency,
                        sessionDistanceKm = sessionDistanceKm,
                        tripDistanceKm = tripDistanceKm,
                        consumptionExpanded = consumptionExpanded,
                        onConsumptionExpand = onConsumptionExpand,
                        onConsumptionClose = onConsumptionClose,
                        dashboardIconsEnabled = dashboardIconsEnabled,
                        onNavigateToCharging = onNavigateToCharging,
                        onShowTyreDialog = onShowTyreDialog,
                        tyreUnit = tyreUnit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                    )
                    TripControls(
                        telemetry = telemetry,
                        isInTrip = isInTrip,
                        autoTripDetection = autoTripDetection,
                        onStartTrip = onStartTrip,
                        onEndTrip = onEndTrip,
                        onToggleAutoDetection = onToggleAutoDetection,
                        liveSessionStartMs = liveSessionStartMs,
                        liveDistanceKm = tripDistanceKm,
                        liveOdometerDistanceKm = liveOdometerDistanceKm,
                        liveAccumulatedKwh = liveAccumulatedKwh,
                        unitSystem = unitSystem,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(0.28f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VehicleStats(
                        telemetry  = telemetry,
                        vehicleSnapshot = vehicleSnapshot,
                        modifier   = Modifier.fillMaxWidth(),
                        fillHeight = true,
                        onNavigateToBatteryDegradation = onNavigateToBatteryDegradation,
                        onShowBattery12vHistory = onShowBattery12vHistory,
                        tyreUnit = tyreUnit,
                        onShowTyreDialog = onShowTyreDialog,
                        dashboardIconsEnabled = dashboardIconsEnabled
                    )
                }
            }
        }

        else -> {
            // ── Expanded: full landscape — original 85/15 layout ─────────────
            Row(
                modifier = styledModifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.85f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EnergyFlowDiagram(
                        telemetry = telemetry,
                        tripDataPoints = tripDataPoints,
                        weeklyEfficiency = weeklyEfficiency,
                        monthlyEfficiency = monthlyEfficiency,
                        yearlyEfficiency = yearlyEfficiency,
                        sessionDistanceKm = sessionDistanceKm,
                        tripDistanceKm = tripDistanceKm,
                        consumptionExpanded = consumptionExpanded,
                        onConsumptionExpand = onConsumptionExpand,
                        onConsumptionClose = onConsumptionClose,
                        dashboardIconsEnabled = dashboardIconsEnabled,
                        onNavigateToCharging = onNavigateToCharging,
                        onShowTyreDialog = onShowTyreDialog,
                        tyreUnit = tyreUnit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                    )
                    TripControls(
                        telemetry = telemetry,
                        isInTrip = isInTrip,
                        autoTripDetection = autoTripDetection,
                        onStartTrip = onStartTrip,
                        onEndTrip = onEndTrip,
                        onToggleAutoDetection = onToggleAutoDetection,
                        liveSessionStartMs = liveSessionStartMs,
                        liveDistanceKm = tripDistanceKm,
                        liveOdometerDistanceKm = liveOdometerDistanceKm,
                        liveAccumulatedKwh = liveAccumulatedKwh,
                        unitSystem = unitSystem,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Right column - Stats
                Column(
                    modifier = Modifier
                        .weight(0.15f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VehicleStats(
                        telemetry  = telemetry,
                        vehicleSnapshot = vehicleSnapshot,
                        modifier   = Modifier.fillMaxWidth(),
                        fillHeight = true,
                        onNavigateToBatteryDegradation = onNavigateToBatteryDegradation,
                        onShowBattery12vHistory = onShowBattery12vHistory,
                        tyreUnit = tyreUnit,
                        onShowTyreDialog = onShowTyreDialog,
                        dashboardIconsEnabled = dashboardIconsEnabled
                    )
                }
            }
        }
    }
}

/**
 * Formats segment and cumulative distances for UI display.
 *
 * Both values are coerced to be at least `0.0` and **truncated** to integers.
 * If the cumulative distance is greater than 0 and differs from the segment
 * distance by at least 1 (after truncation), returns the segment followed by
 * the cumulative distance in parentheses (e.g., `"5, (12)"`). Otherwise,
 * returns the larger of the two truncated values as a string.
 *
 * @param segmentKm The current segment distance in kilometers.
 * @param cumulativeKm The total cumulative distance in kilometers.
 * @return A formatted string representing the distance(s) to display.
 */
private fun formatDistanceDisplay(segmentKm: Double, cumulativeKm: Double, showDecimal: Boolean = false): String {
    val segRaw = segmentKm.coerceAtLeast(0.0)
    val cumRaw = cumulativeKm.coerceAtLeast(0.0)
    val segment = segRaw.toInt()
    val cumulative = cumRaw.toInt()
    return if (cumulative > 0 && abs(segment - cumulative) >= 1) {
        if (showDecimal) "%.1f (%.1f)".format(segRaw, cumRaw)
        else "$segment, ($cumulative)"
    } else {
        if (showDecimal) "%.1f".format(maxOf(segRaw, cumRaw))
        else "${maxOf(segment, cumulative)}"
    }
}

/**
 * Formats a distance value as a string by truncating the decimal portion.
 *
 * The result is an integer string representing the value in kilometers. The unit
 * display (km or miles) is handled by the `unit` field of the caller.
 *
 * @param km The distance value in kilometers.
 * @return A string representing the truncated integer value.
 */
private fun formatDistanceValue(km: Double): String {
    return "${km.toInt()}"
}

@Composable
fun EnergyFlowDiagram(
    telemetry: VehicleTelemetry,
    tripDataPoints: List<RangeDataPoint>,
    weeklyEfficiency: List<DashboardViewModel.DailyEfficiency>,
    monthlyEfficiency: List<DashboardViewModel.DailyEfficiency>,
    yearlyEfficiency: List<DashboardViewModel.DailyEfficiency>,
    sessionDistanceKm: Double = 0.0,
    tripDistanceKm: Double = 0.0,
    consumptionExpanded: Boolean = false,
    onConsumptionExpand: () -> Unit = {},
    onConsumptionClose: () -> Unit = {},
    dashboardIconsEnabled: Boolean = true,
    onNavigateToCharging: () -> Unit = {},
    onShowTyreDialog: () -> Unit = {},
    tyreUnit: TyrePressureUnit = TyrePressureUnit.BAR,
    modifier: Modifier = Modifier
) {
    val power = if (telemetry.isCharging && telemetry.chargingPower > 0.1) {
        -telemetry.chargingPower
    } else {
        telemetry.enginePower.toDouble()
    }
    val isRegenerating = telemetry.isRegenerating
    val isCharging = telemetry.isCharging
    val hasActiveEnergyFlow = abs(power) > 1.0 || isCharging
    // In multi-window/split-screen the app's window is narrower; skip decimal to avoid crowding.
    val isFullScreen = LocalConfiguration.current.screenWidthDp >= 840

    val context = LocalContext.current
    val appPrefs = remember { PreferencesManager(context.applicationContext) }

    val unitSystem by appPrefs.unitSystem.collectAsState(initial = appPrefs.getCachedUnitSystem())
    val distanceUnit = unitSystem.distanceUnit
    val speedUnit = unitSystem.speedUnit

    // Energy-flow row animation — only runs when icons section is enabled and visible.
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val isResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val flowOffsetAnim = remember { Animatable(0f) }
    LaunchedEffect(isResumed, dashboardIconsEnabled, hasActiveEnergyFlow) {
        if (dashboardIconsEnabled && isResumed && hasActiveEnergyFlow) {
            while (true) {
                delay(50L)  // 20fps
                val next = (flowOffsetAnim.value + 0.05f) % 1f
                flowOffsetAnim.snapTo(next)
            }
        }
    }
    val flowOffset = if (dashboardIconsEnabled && hasActiveEnergyFlow) flowOffsetAnim.value else 0f

    val awdBitmap: ImageBitmap? = if (dashboardIconsEnabled) {
        ImageBitmap.imageResource(id = R.drawable.awd)
    } else null

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        if (consumptionExpanded) {
            // ── Expanded consumption chart (tabbed: 7d / 30d / 12m) ───────────
            ConsumptionChartExpanded(
                weeklyData  = weeklyEfficiency,
                monthlyData = monthlyEfficiency,
                yearlyData  = yearlyEfficiency,
                onClose     = onConsumptionClose,
                modifier    = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                if (dashboardIconsEnabled && awdBitmap != null) {
                    // ── Icons row: battery + AWD/tyres + consumption thumbnail ─────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    ) {
                        EnergyFlowCanvas(
                            power = power,
                            isRegenerating = isRegenerating,
                            isCharging = isCharging,
                            flowOffset = { flowOffset },
                            animationsEnabled = true
                        )

                        LiquidFillBattery(
                            soc = telemetry.soc.toFloat(),
                            isCharging = isCharging,
                            animationsEnabled = true,
                            width = 60.dp,
                            height = 100.dp,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 16.dp)
                                .clickable { onNavigateToCharging() }
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp)
                        ) {
                            Image(
                                bitmap = awdBitmap,
                                contentDescription = "AWD drivetrain — tap to change pressure unit",
                                modifier = Modifier
                                    .size(90.dp)
                                    .clickable { onShowTyreDialog() },
                                contentScale = ContentScale.Fit,
                                filterQuality = FilterQuality.High
                            )

                            TyrePressureIndicator(
                                pressure = telemetry.tyrePressureLF,
                                tempC = telemetry.tyreTempLF.takeIf { it > 0 },
                                unit = tyreUnit,
                                isFront = true,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(x = (-24).dp, y = (-12).dp)
                            )

                            TyrePressureIndicator(
                                pressure = telemetry.tyrePressureRF,
                                tempC = telemetry.tyreTempRF.takeIf { it > 0 },
                                unit = tyreUnit,
                                isFront = true,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 26.dp, y = (-12).dp)
                            )

                            TyrePressureIndicator(
                                pressure = telemetry.tyrePressureLR,
                                tempC = telemetry.tyreTempLR.takeIf { it > 0 },
                                unit = tyreUnit,
                                isFront = false,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .offset(x = (-24).dp, y = (14).dp)
                            )

                            TyrePressureIndicator(
                                pressure = telemetry.tyrePressureRR,
                                tempC = telemetry.tyreTempRR.takeIf { it > 0 },
                                unit = tyreUnit,
                                isFront = false,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 26.dp, y = (14).dp)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .width(120.dp)
                                .fillMaxHeight()
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onConsumptionExpand() }
                                .padding(4.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "consumption charts",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            ConsumptionThumbnail(
                                data = monthlyEfficiency,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        }
                    }
                }

                // Range Projection Chart
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                ) {
                    RangeProjectionChart(
                        dataPoints = tripDataPoints,
                        liveSoc = telemetry.soc,
                        liveElectricRangeKm = telemetry.electricDrivingRangeKm,
                        useImperial = unitSystem.isImperial,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Power metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val powerDisplay = if (isCharging) {
                        String.format("%.1f", power)
                    } else {
                        power.roundToInt().toString()
                    }
                    PowerMetric(
                        label = "Power",
                        value = powerDisplay,
                        unit = "kW",
                        color = when {
                            isRegenerating -> RegenGreen
                            power > 0 -> AccelerationOrange
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    PowerMetric(
                        label = "Speed",
                        value = "${unitSystem.convertSpeed(telemetry.speed.toDouble()).toInt()}",
                        unit = speedUnit,
                        color = BydEcoTealDim
                    )
                    PowerMetric(
                        label = "SoC (BMS)",
                        value = "${telemetry.soc.toInt()}",
                        unit = "%",
                        color = BatteryBlue
                    )
                    PowerMetric(
                        label = "Range",
                        value = run {
                            val projected = tripDataPoints.lastOrNull()?.projectedRangeKm
                            val bms = telemetry.electricDrivingRangeKm
                            if (projected != null && projected.toInt() < bms) formatDistanceValue(projected) else formatDistanceValue(
                                bms.toDouble() // TODO: Perhaps show always the projected range but be cautious because it might skyrocket
                            )
                        },
                        unit = distanceUnit,
                        color = MaterialTheme.extendedColors.range
                    )
                    PowerMetric(
                        label = "Distance",
                        value = formatDistanceDisplay(unitSystem.convertDistance(sessionDistanceKm), unitSystem.convertDistance(tripDistanceKm), isFullScreen),
                        unit = distanceUnit,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }

}


// ── Tyre pressure unit support ────────────────────────────────────────────────

enum class TyrePressureUnit { BAR, PSI, KPA }

private fun Double.toDisplayPressure(unit: TyrePressureUnit): Double = when (unit) {
    TyrePressureUnit.BAR -> this / 14.5038
    TyrePressureUnit.PSI -> this
    TyrePressureUnit.KPA -> this * 6.89476
}

private fun Double.toBarFromPsi(): Double = this / 14.5038

private fun TyrePressureUnit.label(): String = when (this) {
    TyrePressureUnit.BAR -> "bar"
    TyrePressureUnit.PSI -> "psi"
    TyrePressureUnit.KPA -> "kPa"
}

private fun TyrePressureUnit.formatValue(psi: Double): String {
    if (psi < 0.1) return "--"
    return when (this) {
        TyrePressureUnit.BAR -> String.format("%.1f", psi.toDisplayPressure(this))
        TyrePressureUnit.PSI -> String.format("%.1f", psi.toDisplayPressure(this))
        TyrePressureUnit.KPA -> String.format("%.0f", psi.toDisplayPressure(this))
    }
}


@Composable
fun TyrePressureIndicator(
    pressure: Double,           // Always PSI from telemetry
    isFront: Boolean,           // true = front, false = rear
    tempC: Int? = null,         // Tyre temperature in °C — null when not available
    unit: TyrePressureUnit = TyrePressureUnit.BAR,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context.applicationContext) }
    val selectedCar by prefs.selectedCarConfig.collectAsState(initial = null)

    val car = selectedCar ?: return

    val frontTyrePressureBar = car.frontTyrePressureBar
    val rearTyrePressureBar = car.rearTyrePressureBar

    // Alarm thresholds are always evaluated in bar regardless of display unit
    val pressureBar = pressure.toBarFromPsi()
    val recommendedPressure = if (isFront) frontTyrePressureBar else rearTyrePressureBar
    val isNoData = pressure < 0.1
    val isLow    = !isNoData && pressureBar < recommendedPressure - 0.2
    val isHigh   = !isNoData && pressureBar > recommendedPressure + 0.2

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = when {
            isNoData -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
            isLow    -> AccelerationOrange.copy(alpha = 0.9f)
            isHigh   -> BydErrorRed.copy(alpha = 0.9f)
            else     -> RegenGreen.copy(alpha = 0.9f)
        }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = unit.formatValue(pressure),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 12.sp
            )
            if (tempC != null) {
                Text(
                    text = "${tempC}°C",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun EnergyFlowCanvas(
    power: Double,
    isRegenerating: Boolean,
    isCharging: Boolean,
    flowOffset: () -> Float,
    animationsEnabled: Boolean
) {
    val idleColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = Modifier.fillMaxSize()) {

        val topY = size.height * 0.3f + 30f  // Move up - 30% from top + 30px for better alignment with battery/motor icons

        val batteryY = topY

        val motorX = size.width * 0.5f
        val motorY = topY

        // Motor (center)
        val motorSize = 150f

        // Energy flow lines
        if (abs(power) > 1 && !isCharging) {
            val flowColor = when {
                isRegenerating -> RegenGreen
                power > 0 -> AccelerationOrange
                else -> idleColor
            }

            // Create animated flow effect
            val dashPhase = if (animationsEnabled) flowOffset() * 50f else 0f

            // batteryEdge is derived from the actual LiquidFillBattery icon geometry:
            //   padding(start = 16.dp), width = 60.dp → right edge at 76.dp from left.
            // This ensures the flow line docks flush into the battery icon regardless
            // of canvas width, instead of relying on the guessed batteryX proportion.
            val batteryEdge = Offset((16.dp + 60.dp).toPx(), batteryY)
            val motorEdge   = Offset(motorX   - motorSize   / 2f, motorY)

            if (isRegenerating) {
                // Motor → Battery (regeneration): arrows travel right-to-left
                drawEnergyFlow(
                    from = batteryEdge,
                    to   = motorEdge,
                    color = flowColor,
                    dashPhase = dashPhase,
                    reverse = true,
                    animated = animationsEnabled
                )
            } else if (power > 0) {
                // Battery → Motor (acceleration): arrows travel left-to-right
                drawEnergyFlow(
                    from = motorEdge,
                    to   = batteryEdge,
                    color = flowColor,
                    dashPhase = dashPhase,
                    reverse = true,
                    animated = animationsEnabled
                )
            }
        }
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEnergyFlow(
    from: Offset,
    to: Offset,
    color: Color,
    dashPhase: Float,
    reverse: Boolean,
    animated: Boolean
) {
    drawLine(color = color, start = from, end = to, strokeWidth = 6f)
    if (!animated) return

    val dx = to.x - from.x
    val dy = to.y - from.y
    val lineLength = kotlin.math.sqrt(dx * dx + dy * dy)
    if (lineLength == 0f) return

    // Pre-compute trig once per call instead of per arrow
    val ux = dx / lineLength
    val uy = dy / lineLength
    val cosA = 0.921f; val sinA = 0.389f
    val dir = if (!reverse) 1f else -1f
    val wx1 = dir * (-ux * cosA + uy * sinA) * 10f
    val wy1 = dir * (-uy * cosA - ux * sinA) * 10f
    val wx2 = dir * (-ux * cosA - uy * sinA) * 10f
    val wy2 = dir * ( ux * sinA - uy * cosA) * 10f

    val arrowSpacing = 50f
    val numArrows = (lineLength / arrowSpacing).toInt() + 2
    val phase = dashPhase % arrowSpacing
    val path = Path()  // reused across arrows

    for (i in 0 until numArrows) {
        val position = i * arrowSpacing - phase
        if (position < 0f || position > lineLength) continue
        val progress = position / lineLength
        val ax = from.x + dx * progress
        val ay = from.y + dy * progress
        path.reset()
        path.moveTo(ax + wx1, ay + wy1)
        path.lineTo(ax, ay)
        path.lineTo(ax + wx2, ay + wy2)
        drawPath(path = path, color = color, style = Stroke(width = 3f, cap = StrokeCap.Round))
    }
}

@Composable
fun PowerMetric(
    label: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = true
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun LiveTripStat(label: String, value: String, unit: String, isFullScreen: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = if (isFullScreen) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = if (isFullScreen) 11.sp else 9.sp
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = if (isFullScreen) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(2.dp))
                Text(
                    unit,
                    style = if (isFullScreen) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }
        }
    }
}

@Composable
fun TripControls(
    telemetry: VehicleTelemetry,
    isInTrip: Boolean,
    autoTripDetection: Boolean,
    onStartTrip: () -> Unit,
    onEndTrip: () -> Unit,
    onToggleAutoDetection: () -> Unit,
    liveSessionStartMs: Long? = null,
    liveDistanceKm: Double = 0.0,
    liveOdometerDistanceKm: Double = 0.0,
    liveAccumulatedKwh: Double = 0.0,
    unitSystem: UnitSystem = UnitSystem.METRIC,
    modifier: Modifier = Modifier
) {
    var showStopConfirmDialog by remember { mutableStateOf(false) }

    // ── Stop recording confirmation ────────────────────────────────────────────
    if (showStopConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showStopConfirmDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            icon = {
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = null,
                    tint = BydErrorRed,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Stop recording?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    buildString {
                        append("The current trip will be saved and closed. This cannot be undone.")
                        if (autoTripDetection) {
                            append("\n\nAutomatic trip recording will be turned off and future trips will be manual until you switch it back on.")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showStopConfirmDialog = false
                        if (autoTripDetection) {
                            onToggleAutoDetection()
                        }
                        onEndTrip()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BydErrorRed)
                ) { Text("Stop trip") }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmDialog = false }) { Text("Keep recording") }
            }
        )
    }

    Card(
        modifier = modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        // Elapsed time ticker — runs only while recording, survives car-off pauses
        var elapsedMs by remember(liveSessionStartMs) {
            mutableStateOf(liveSessionStartMs?.let { System.currentTimeMillis() - it } ?: 0L)
        }
        LaunchedEffect(liveSessionStartMs) {
            if (liveSessionStartMs == null) return@LaunchedEffect
            while (true) {
                delay(1000L)
                elapsedMs = System.currentTimeMillis() - liveSessionStartMs
            }
        }

        var showManualWarning by remember { mutableStateOf(false) }
        if (showManualWarning) {
            AlertDialog(
                onDismissRequest = { showManualWarning = false },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                icon = {
                    Icon(Icons.Filled.WarningAmber, null,
                        tint = MaterialTheme.colorScheme.error)
                },
                title = {
                    Text("Switch to Manual Tracking?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "With manual tracking enabled, trip data will not be recorded " +
                                "automatically when you start driving. You will need to start " +
                                "and stop each trip yourself.\n\n" +
                                "Trips that are not recorded will be absent from your daily, " +
                                "weekly, and monthly consumption statistics.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showManualWarning = false
                        onToggleAutoDetection()
                    }) {
                        Text("Switch to Manual",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualWarning = false }) {
                        Text("Keep Auto")
                    }
                }
            )
        }

        // BYD auto-shifts to P whenever the car stops (e.g. at a traffic light).
        // During an active trip that auto-P is not a deliberate park, so the
        // statusText below maps it to "Stopped" rather than "Trip in Progress".
        val autoParkedInTrip = isInTrip && telemetry.gear == "P"

        val gearColor = when {
            telemetry.gear == "R"  -> AccelerationOrange
            autoParkedInTrip       -> MaterialTheme.colorScheme.onSurfaceVariant
            isInTrip               -> MaterialTheme.colorScheme.primary
            else                   -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val statusText = when {
            isInTrip && telemetry.speed > 0.5              -> "Driving"
            isInTrip && telemetry.gear in listOf("D", "R") -> "Ready"
            autoParkedInTrip                               -> "Stopped"
            isInTrip                                        -> "Trip in Progress"
            telemetry.gear == "D"                           -> "Ready to Drive"
            telemetry.gear == "R"                           -> "Reverse"
            telemetry.gear == "P"                           -> "Waiting for Trip..."
            telemetry.gear == "N"                           -> "Neutral"
            else                                            -> "Waiting for Trip..."
        }

        val autoLabel: @Composable () -> Unit = {
            Text(text = "Auto", fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        val autoSwitch: @Composable () -> Unit = {
            Switch(
                checked = autoTripDetection,
                onCheckedChange = { enabled ->
                    if (!enabled) showManualWarning = true
                    else onToggleAutoDetection()
                },
                thumbContent = if (!autoTripDetection) {
                    {
                        Box(modifier = Modifier.size(12.dp).background(ToggleUncheckedTrack, CircleShape))
                    }
                } else null,
                colors = SwitchDefaults.colors(
                    uncheckedThumbColor  = Color.White,
                    uncheckedTrackColor  = ToggleUncheckedTrack,
                    uncheckedBorderColor = ToggleUncheckedTrack
                )
            )
        }
        val autoToggle: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                autoLabel()
                Spacer(modifier = Modifier.width(6.dp))
                autoSwitch()
            }
        }

        // ── ALT 2 — DENSE: single horizontal row, button + titles + stats + auto ───
        val elapsedH   = elapsedMs / 3_600_000L
        val elapsedM   = (elapsedMs % 3_600_000L) / 60_000L
        val elapsedS   = (elapsedMs % 60_000L) / 1000L
        val elapsedStr = "%02d:%02d:%02d".format(elapsedH, elapsedM, elapsedS)

        val distKm = liveDistanceKm
        // Prefer the pure odometer delta for avg speed — matches the formula the
        // finalized trip stats use (endOdometer - startOdometer / duration).
        val speedDistKm = liveOdometerDistanceKm.takeIf { it > 0.0 } ?: distKm
        val avgSpeedDisplay = if (elapsedMs > 10_000L) {
            unitSystem.convertDistance(speedDistKm) / (elapsedMs / 3_600_000.0)
        } else 0.0
        val kwhPer100km = if (distKm > 0.5) (liveAccumulatedKwh / distKm) * 100.0 else 0.0
        val effDisplay  = unitSystem.convertEfficiency(kwhPer100km)

        // Record is disabled while auto-detection is on (auto handles starting trips itself).
        val recordDisabled = !isInTrip && autoTripDetection

        // The actual card width — not screenWidthDp — drives the 4-col vs 2×2 choice.
        // On a tablet in side-by-side layout, the screen is wide but the card itself
        // sits in the left pane and may only be ~700dp.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Both branches key off the *card's* measured width, not the device's
        // screenWidthDp — in split-screen / multi-window the total screen is wide
        // but this card may only be ~500dp.
        val isWideCard   = maxWidth >= 840.dp
        val isNarrowCard = maxWidth < 720.dp
        // The narrow-card-in-trip layout sizes itself naturally; this height
        // applies only to the single-row arrangements below.
        val cardHeight = if (isWideCard) 80.dp else 64.dp

        val recordStopButton: @Composable (compact: Boolean) -> Unit = { compact ->
            Button(
                onClick = {
                    if (isInTrip) showStopConfirmDialog = true else onStartTrip()
                },
                enabled = !recordDisabled,
                shape = RoundedCornerShape(50),
                contentPadding = if (compact) {
                    PaddingValues(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
                } else {
                    PaddingValues(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isInTrip) BydErrorRed else BydElectricAzure,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Icon(
                    imageVector = if (isInTrip) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 11.dp else 14.dp)
                )
                Spacer(modifier = Modifier.width(if (compact) 4.dp else 6.dp))
                Text(
                    text = if (isInTrip) "Stop" else "Record",
                    fontSize = if (compact) 11.sp else 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        val titles: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "Trip Tracking",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(gearColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    val subtitle = if (isInTrip) statusText
                                   else "${telemetry.gear} · $statusText"
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (isNarrowCard && isInTrip) {
            // Split-screen layout, stacked vertically so the stat values get
            // their own bands. 4 rows top-to-bottom:
            //   1) titles (left) | "Auto" text (right)
            //   2) TIME / AVG
            //   3) ENERGY / CONS
            //   4) Stop button (left) | Auto switch (right)
            // Two horizontal rows of 5 equal-weight cells. The middle cell
            // is left intentionally empty so AVG / CONS push further right
            // and don't crowd TIME / ENERGY.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) { titles() }
                    Box(modifier = Modifier.weight(1f)) {
                        DenseStat("TIME", elapsedStr, "")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.weight(1f)) {
                        DenseStat("AVG", "%.0f".format(avgSpeedDisplay), unitSystem.speedUnit)
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterEnd
                    ) { autoLabel() }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) { recordStopButton(true) }
                    Box(modifier = Modifier.weight(1f)) {
                        DenseStat("ENERGY", "%.1f".format(liveAccumulatedKwh), "kWh")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.weight(1f)) {
                        DenseStat(
                            "CONS.",
                            if (effDisplay > 0) "%.1f".format(effDisplay) else "—",
                            unitSystem.consumptionUnit
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterEnd
                    ) { autoSwitch() }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                recordStopButton(false)
                titles()

                if (isInTrip) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DenseStat("TIME", elapsedStr, "")
                        DenseStat("AVG", "%.0f".format(avgSpeedDisplay), unitSystem.speedUnit)
                        DenseStat("ENERGY", "%.1f".format(liveAccumulatedKwh), "kWh")
                        DenseStat(
                            "CONS.",
                            if (effDisplay > 0) "%.1f".format(effDisplay) else "—",
                            unitSystem.consumptionUnit
                        )
                    }
                } else {
                    Text(
                        text = if (autoTripDetection)
                            "Auto detection will start a trip when you drive"
                        else
                            "Press Record to begin manual tracking",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                autoToggle()
            }
        }
        }
    }
}

@Composable
private fun DenseStat(label: String, value: String, unit: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            if (unit.isNotEmpty()) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = unit,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 1.dp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun VehicleStats(
    telemetry: VehicleTelemetry,
    vehicleSnapshot: VehicleTelemetrySnapshot? = null,
    modifier: Modifier = Modifier,
    /** true in side-by-side layouts: cards share available height via weight(1f), no scroll */
    fillHeight: Boolean = false,
    onNavigateToBatteryDegradation: () -> Unit = {},
    onShowBattery12vHistory: () -> Unit = {},
    tyreUnit: TyrePressureUnit = TyrePressureUnit.BAR,
    onShowTyreDialog: () -> Unit = {},
    dashboardIconsEnabled: Boolean = true
) {
    val context     = LocalContext.current
    val prefs       = remember { PreferencesManager(context.applicationContext) }
    val selectedCar by prefs.selectedCarConfig.collectAsState(initial = null)
    val unitSystem  by prefs.unitSystem.collectAsState(initial = prefs.getCachedUnitSystem())
    val distanceUnit = unitSystem.distanceUnit

    val colModifier = if (fillHeight) modifier.fillMaxHeight() else modifier.verticalScroll(rememberScrollState())
    val spacing     = if (fillHeight) 4.dp else 8.dp

    Column(
        modifier = colModifier,
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        // weight(1f) is a ColumnScope extension — must be declared inside the Column lambda
        val cardMod = if (fillHeight) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth()
        val smallCardMod = if (fillHeight) Modifier.fillMaxWidth().weight(0.75f) else Modifier.fillMaxWidth()
        val tyreCardMod = if (fillHeight) Modifier.fillMaxWidth().weight(1.0f) else Modifier.fillMaxWidth()
        // SOH: use the higher-fidelity source if available,
        // otherwise fall back to the estimated Int from toTelemetry() (also 1 decimal).
        // Allow up to 110% for the estimated path since the remaining-energy estimator
        // can slightly exceed 100% on a fully healthy pack.
        val sohDisplay: String = run {
            val statSoh = vehicleSnapshot?.statisticBatterySoh
                ?: telemetry.statisticBatterySoh
            val pct = when {
                statSoh != null && statSoh in 50.0..110.0 ->
                    String.format("%.1f%%", statSoh)
                telemetry.soh in 1..110 ->
                    String.format("%.1f%%", telemetry.soh.toDouble())
                else -> "—"
            }
            // Source tag: "stat" = statistic-feature read, "BMS" = direct device getter,
            // "~cap" = capacity estimate, "~nrg" = remaining-energy estimate.
            val src = when {
                statSoh != null && statSoh in 50.0..110.0 -> "stat"
                !telemetry.sohEstimated && telemetry.soh in 1..110 -> "BMS"
                telemetry.soh in 1..110 -> "~est"
                else -> ""
            }
            if (pct == "—") "SoH: —" else "SoH: $pct${if (src.isNotEmpty()) " ($src)" else ""}"
        }
        val batteryTempSubtitle: String? = run {
            // Source priority matches VehicleTelemetry.batteryTempAvg: Charging device
            // cell range first (m39/m40 via batteryCellTempMin/Max), then statistic cellTAvg.
            // batteryPackTemp is intentionally excluded — see telemetry getter for rationale.
            val cellMin = telemetry.batteryCellTempMin.toDouble().takeIf { it > 0.0 }
                ?: vehicleSnapshot?.statisticCellTempMin
            val cellMax = telemetry.batteryCellTempMax.toDouble().takeIf { it > 0.0 }
                ?: vehicleSnapshot?.statisticCellTempMax
            val avg = if (cellMin != null && cellMax != null && cellMax >= cellMin && (cellMax - cellMin) <= 25) {
                (cellMin + cellMax) / 2.0
            } else {
                vehicleSnapshot?.statisticCellTempAvg
            }
            avg?.takeIf { it.isFinite() && it in -40.0..120.0 }?.let { "Temp: ${it.toInt()} °C" }
        }
        StatCard(
            title    = "Battery",
            value    = sohDisplay,
            subtitle = batteryTempSubtitle,
            icon     = Icons.Filled.BatteryChargingFull,
            color    = BatteryBlue,
            compact  = fillHeight,
            modifier = cardMod,
            onClick  = onNavigateToBatteryDegradation
        )
        StatCard(
            title    = "Environment",
            value    = run {
                val externalTemp = vehicleSnapshot?.instrumentOutCarTemperature
                    ?: telemetry.instrumentOutCarTemperature
                if (externalTemp != null) "Ambient: $externalTemp °C" else "Ambient: — °C"
            },
            subtitle = run {
                val pm25In = vehicleSnapshot?.pm25InCar
                val pm25Out = vehicleSnapshot?.pm25OutCar
                if (pm25In != null || pm25Out != null)
                    "PM2.5: ${pm25In ?: "—"}/${pm25Out ?: "—"} μg/m³"
                else null
            },
            icon     = Icons.Filled.Public,
            color    = RegenGreen,
            compact  = fillHeight,
            modifier = cardMod
        )
        StatCard(
            title    = "HV / 12V",
            value    = run {
                // Use the live vehicle snapshot for cell voltage updates.
                val cellMin  = vehicleSnapshot?.statisticCellVoltageMin
                    ?: telemetry.statisticCellVoltageMin
                    ?: telemetry.batteryCellVoltageMin.takeIf { it > 0.0 }
                val cellMax  = vehicleSnapshot?.statisticCellVoltageMax
                    ?: telemetry.statisticCellVoltageMax
                    ?: telemetry.batteryCellVoltageMax.takeIf { it > 0.0 }
                val cellV    = cellMin ?: cellMax
                val cells    = selectedCar?.cellCount ?: 0
                val packVoltage = vehicleSnapshot?.batteryTotalVoltage?.takeIf { it > 0 }
                    ?: telemetry.batteryTotalVoltage.takeIf { it > 0 }
                val hvVolts: Int? = when {
                    packVoltage != null               -> packVoltage
                    cellV != null && cells > 0        -> (cellV * cells).toInt()
                    else                              -> null
                }
                val hvStr    = if (hvVolts != null) "$hvVolts V" else "— V"
                val v12Str   = if (telemetry.battery12vVoltage > 0.0) String.format("%.2f", telemetry.battery12vVoltage) + " V" else "— V"
                "$hvStr / $v12Str"
            },
            subtitle = run {
                val cellMin = vehicleSnapshot?.statisticCellVoltageMin
                    ?: telemetry.statisticCellVoltageMin
                    ?: telemetry.batteryCellVoltageMin.takeIf { it > 0.0 }
                val cellMax = vehicleSnapshot?.statisticCellVoltageMax
                    ?: telemetry.statisticCellVoltageMax
                    ?: telemetry.batteryCellVoltageMax.takeIf { it > 0.0 }
                when {
                    cellMin != null && cellMax != null ->
                        "Cells: ${String.format("%.3f", cellMin)} – ${String.format("%.3f", cellMax)} V"
                    cellMin != null ->
                        "Cells: ${String.format("%.3f", cellMin)} V"
                    cellMax != null ->
                        "Cells: ${String.format("%.3f", cellMax)} V"
                    else -> "Cells: awaiting data…"
                }
            },
            icon     = Icons.Filled.Bolt,
            color    = BydEcoTealDim,
            compact  = fillHeight,
            modifier = cardMod,
            onClick  = onShowBattery12vHistory
        )

        // Dead-band: BYD motor sensors report 1-9 RPM noise at standstill.
        // Also force to 0 when speed is zero to ignore larger noise spikes at standstill.
        val isStopped = telemetry.speed < 0.5
        val frontMotorRpm = if (isStopped) null else telemetry.engineSpeedFront.takeIf { it >= 10 }
        val rearMotorRpm  = if (isStopped) null else telemetry.engineSpeedRear.takeIf  { it >= 10 }
        val motorsAreSpinning = (frontMotorRpm != null || rearMotorRpm != null)

        // ── Motor card
        when (selectedCar?.drivetrain) {
            Drivetrain.FWD -> StatCard(
                title    = "Front Motor",
                value    = frontMotorRpm?.let { "$it RPM" } ?: "0 RPM",
                subtitle = "${telemetry.enginePower} kW",
                iconRes  = R.drawable.ic_motor_axle,
                color    = BydElectricBlue,
                compact  = fillHeight,
                modifier = cardMod
            )
            Drivetrain.RWD -> StatCard(
                title    = "Rear Motor",
                value    = rearMotorRpm?.let { "$it RPM" } ?: "0 RPM",
                subtitle = "${telemetry.enginePower} kW",
                iconRes  = R.drawable.ic_motor_axle,
                color    = BydElectricBlue,
                compact  = fillHeight,
                modifier = cardMod
            )
            else -> {
                val drivetrainLabel = when (telemetry.drivetrainState) {
                    1, 4 -> "AWD"
                    2, 5 -> "FWD"
                    3, 6, 19 -> "RWD"
                    else -> when (selectedCar?.drivetrain) {
                        Drivetrain.AWD -> "AWD"
                        Drivetrain.FWD -> "FWD"
                        Drivetrain.RWD -> "RWD"
                        else -> null
                    }
                }
                val kwLine = "${telemetry.enginePower} kW"
                StatCard(
                    title    = "Front / Rear Motors",
                    value    = "${frontMotorRpm ?: "0"} / ${rearMotorRpm ?: "0"} RPM",
                    subtitle = if (drivetrainLabel != null) "$drivetrainLabel · $kwLine" else kwLine,
                    iconRes  = R.drawable.ic_motor_axle,
                    color    = BydElectricBlue,
                    compact  = fillHeight,
                    modifier = cardMod
                )
            }
        }

        StatCard(
            title    = "Driving Dynamics",
            value    = "${telemetry.regenModeName} / ${telemetry.driveModeName}",
            subtitle = run {
                val slope = vehicleSnapshot?.roadSlopeDeg
                val needsInit = telemetry.isCarOn && (telemetry.driveMode == 0 || telemetry.regenMode == 0)
                when {
                    needsInit -> "Change drive/regen modes to initialize display"
                    slope != null -> "Slope: ${String.format("%.1f", slope)}°"
                    else -> null
                }
            },
            icon     = Icons.Filled.DirectionsCar,
            color    = BydElectricBlue,
            compact  = fillHeight,
            modifier = cardMod
        )

        if (!dashboardIconsEnabled) {
            TyreStatCard(
                telemetry = telemetry,
                tyreUnit  = tyreUnit,
                compact   = fillHeight,
                modifier  = tyreCardMod,
                onClick   = onShowTyreDialog
            )
        }

        StatCard(
            title    = "Odometer",
            value    = "${String.format("%.1f", telemetry.odometer)} $distanceUnit",
            icon     = Icons.Filled.Speed,
            color    = MaterialTheme.colorScheme.primary,
            compact  = fillHeight,
            modifier = smallCardMod
        )
        StatCard(
            title    = "Total Discharge",
            value    = "${String.format("%.1f", telemetry.totalDischarge)} kWh",
            subtitle = run {
                val phm = telemetry.totalElecConPHM ?: return@run null
                val mileage = vehicleSnapshot?.statisticTotalMileageValue?.toDouble()
                    ?: vehicleSnapshot?.statisticTotalMileageDecimal
                    ?: telemetry.odometer
                if (mileage > 0)
                    "Drivetrain: ${String.format("%.1f", phm * mileage / 100.0)} kWh"
                else null
            },
            icon     = Icons.Filled.ElectricalServices,
            color    = AccelerationOrange,
            compact  = fillHeight,
            modifier = smallCardMod
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Battery12vHistoryDialog(
    historyPoints: List<BatteryVoltageHistoryPoint>,
    telemetry: VehicleTelemetry,
    vehicleSnapshot: VehicleTelemetrySnapshot?,
    onDismiss: () -> Unit
) {
    val liveTimestamp = remember(
        telemetry.battery12vVoltage,
        telemetry.batteryTotalVoltage,
        vehicleSnapshot?.batteryTotalVoltage
    ) {
        System.currentTimeMillis()
    }
    val liveHvVoltage = vehicleSnapshot?.batteryTotalVoltage?.takeIf { it > 0 }
        ?: telemetry.batteryTotalVoltage.takeIf { it > 0 }
        ?: 0
    val mergedPoints = remember(historyPoints, telemetry.battery12vVoltage, liveTimestamp, liveHvVoltage) {
        val livePoint = telemetry.battery12vVoltage.takeIf { it > 0.0 }?.let {
            BatteryVoltageHistoryPoint(
                timestamp = liveTimestamp,
                battery12vVoltage = it,
                batteryTotalVoltage = liveHvVoltage,
                isChargingSample = telemetry.isCharging,
                soc = telemetry.soc
            )
        }
        val base = historyPoints.toMutableList()
        if (livePoint != null) {
            val last = base.lastOrNull()
            val shouldAppend = last == null ||
                livePoint.timestamp - last.timestamp > 60_000L ||
                kotlin.math.abs(livePoint.battery12vVoltage - last.battery12vVoltage) >= 0.01
            if (shouldAppend) base += livePoint
        }
        base.sortedBy { it.timestamp }
    }

    val latest = mergedPoints.lastOrNull()
    val min12v = mergedPoints.minOfOrNull { it.battery12vVoltage }
    val max12v = mergedPoints.maxOfOrNull { it.battery12vVoltage }
    val delta12v = if (mergedPoints.size >= 2) {
        mergedPoints.last().battery12vVoltage - mergedPoints.first().battery12vVoltage
    } else null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Column {
                            Text("HV / 12V Batteries - Last 48 Hours")
                            Text(
                                "Recorded telemetry samples plus the current live reading",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    }
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Battery12vSummaryChip("Latest", latest?.let { "%.2f V".format(it.battery12vVoltage) } ?: "—", Modifier.weight(1f))
                        Battery12vSummaryChip("Min", min12v?.let { "%.2f V".format(it) } ?: "—", Modifier.weight(1f))
                        Battery12vSummaryChip("Max", max12v?.let { "%.2f V".format(it) } ?: "—", Modifier.weight(1f))
                        Battery12vSummaryChip("Delta", delta12v?.let { "%+.2f V".format(it) } ?: "—", Modifier.weight(1f))
                    }
                    Battery12vHistoryChart(
                        points = mergedPoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun Battery12vSummaryChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun Battery12vHistoryChart(
    points: List<BatteryVoltageHistoryPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "No 12V history recorded yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val nowMs = remember(points) { max(points.last().timestamp, System.currentTimeMillis()) }
    val historyWindowMs = 48L * 60L * 60L * 1000L
    val tickStepMs = 6L * 60L * 60L * 1000L
    val startMs = nowMs - historyWindowMs
    val minVoltage = 12.0
    val maxVoltage = 14.0
    val chartColor = BatteryBlue
    val socColor   = MotorViolet
    val chargeColor = RegenGreen.copy(alpha = 0.12f)
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var touchPos by remember { mutableStateOf<Offset?>(null) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("12V / SOC chart (If offline mode, values are reconstructed)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(width = 18.dp, height = 8.dp)) {
                        drawLine(chartColor, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 4f, cap = StrokeCap.Round)
                    }
                    Text("12V", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Canvas(modifier = Modifier.size(width = 18.dp, height = 8.dp)) {
                        drawLine(socColor, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 3f,
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                    }
                    Text("SoC", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(points) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            touchPos = down.position
                            drag(down.id) { change -> touchPos = change.position }
                            touchPos = null
                        }
                    }
            ) {
                val padL = 54f
                val padR = 58f
                val padT = 18f
                val padB = 42f
                val chartW = size.width - padL - padR
                val chartH = size.height - padT - padB
                val nc = drawContext.canvas.nativeCanvas
                val labelPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = axisColor.toArgb()
                    textSize = 19f
                }

                fun xOf(timestamp: Long): Float =
                    (padL + ((timestamp - startMs).toDouble() / (nowMs - startMs).coerceAtLeast(1L).toDouble()).toFloat() * chartW)
                        .coerceIn(padL, padL + chartW)

                fun yOf(voltage: Double): Float =
                    padT + chartH - (((voltage - minVoltage) / (maxVoltage - minVoltage).coerceAtLeast(0.1)).toFloat() * chartH)

                fun yOfSoc(pct: Double): Float =
                    padT + chartH - (pct / 100.0).toFloat() * chartH

                listOf(14.0, 13.0, 12.0).forEachIndexed { index, value ->
                    val y = padT + chartH * index / 2f
                    drawLine(gridColor, Offset(padL, y), Offset(size.width - padR, y), 1f)
                    labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    nc.drawText("%.1f".format(value), padL - 8f, y + 7f, labelPaint)
                }

                // Right axis — SoC (%)
                val socAxisPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = socColor.copy(alpha = 0.8f).toArgb()
                    textSize = 19f
                    textAlign = android.graphics.Paint.Align.LEFT
                }
                for (tick in listOf(33, 66, 100)) {
                    val y = yOfSoc(tick.toDouble())
                    if (y in padT..(padT + chartH)) {
                        nc.drawText("$tick%", size.width - padR + 6f, y + 7f, socAxisPaint)
                    }
                }

                val timeTicks = buildList {
                    var tick = startMs
                    while (tick <= nowMs) {
                        add(tick)
                        tick += tickStepMs
                    }
                    if (lastOrNull() != nowMs) add(nowMs)
                }
                labelPaint.textAlign = android.graphics.Paint.Align.CENTER
                timeTicks.forEach { tick ->
                    val x = xOf(tick)
                    drawLine(gridColor, Offset(x, padT), Offset(x, padT + chartH), 1f)
                    val label = if (tick == nowMs) {
                        timeFormatter.format(Date(tick))
                    } else {
                        "now - ${((nowMs - tick) / 3_600_000L)}h"
                    }
                    nc.drawText(label, x, size.height - 10f, labelPaint)
                }

                val chargingSegments = points.filter { it.isChargingSample }
                chargingSegments.forEach { point ->
                    val x = xOf(point.timestamp)
                    drawRect(
                        color = chargeColor,
                        topLeft = Offset((x - 1.5f).coerceAtLeast(padL), padT),
                        size = androidx.compose.ui.geometry.Size(3f, chartH)
                    )
                }

                if (points.size == 1) {
                    val x = xOf(points.first().timestamp)
                    val y = yOf(points.first().battery12vVoltage)
                    drawCircle(chartColor, radius = 6f, center = Offset(x, y))
                } else {
                    val path = Path().apply {
                        moveTo(xOf(points.first().timestamp), yOf(points.first().battery12vVoltage))
                        points.drop(1).forEach { point ->
                            lineTo(xOf(point.timestamp), yOf(point.battery12vVoltage))
                        }
                    }
                    drawPath(
                        path = path,
                        color = chartColor,
                        style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    // SoC line (dashed, only for points that have SoC recorded)
                    val socPoints = points.filter { it.soc > 0.0 }
                    if (socPoints.size >= 2) {
                        val socPath = Path().apply {
                            moveTo(xOf(socPoints.first().timestamp), yOfSoc(socPoints.first().soc))
                            socPoints.drop(1).forEach { pt ->
                                lineTo(xOf(pt.timestamp), yOfSoc(pt.soc))
                            }
                        }
                        drawPath(
                            path = socPath,
                            color = socColor.copy(alpha = 0.85f),
                            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f)))
                        )
                    }
                }

                drawRect(
                    color = gridColor,
                    topLeft = Offset(padL, padT),
                    size = androidx.compose.ui.geometry.Size(chartW, chartH),
                    style = Stroke(width = 1f)
                )

                touchPos?.let { tp ->
                    if (tp.x in padL..(padL + chartW)) {
                        val closest = points.minByOrNull { kotlin.math.abs(xOf(it.timestamp) - tp.x) } ?: return@let
                        val cx = xOf(closest.timestamp)
                        val cy = yOf(closest.battery12vVoltage)
                        drawCrosshair(
                            cx = cx,
                            cy = cy,
                            w = size.width,
                            padL = padL,
                            padR = padR,
                            padT = padT,
                            chartH = chartH,
                            line1 = "12V: ${"%.2f".format(closest.battery12vVoltage)} V${if (closest.soc > 0.0) "  |  SoC: ${"%.1f".format(closest.soc)}%" else ""}",
                            line2 = if (closest.isChargingSample) "charging sample" else "drive/live sample",
                            line3 = timeFormatter.format(Date(closest.timestamp)),
                            accentColor = chartColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: Any? = null,
    iconRes: Int? = null,
    color: Color,
    subtitle: String? = null,
    /** true when cards share height via weight(1f) — reduces padding/text to avoid overflow */
    compact: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val pad      = if (compact) 8.dp  else 12.dp
    val iconSize = if (compact) 22.dp else 32.dp
    val spacerW  = if (compact) 8.dp  else 12.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            ),
        onClick  = onClick ?: {},
        enabled  = onClick != null,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(pad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                icon is ImageVector -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(iconSize)
                )
                iconRes != null -> Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(iconSize)
                )
            }
            Spacer(modifier = Modifier.width(spacerW))
            Column {
                Text(
                    text     = title,
                    style    = if (compact) MaterialTheme.typography.labelMedium
                    else MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text       = value,
                    style      = if (compact) MaterialTheme.typography.titleSmall
                    else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                subtitle?.let {
                    Text(
                        text  = it,
                        style = if (compact) MaterialTheme.typography.labelSmall
                        else MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TyreStatCard(
    telemetry: VehicleTelemetry,
    tyreUnit: TyrePressureUnit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val pad = 8.dp
    val iconSize = if (compact) 22.dp else 26.dp
    val spacerW = 8.dp
    val cellSpacing = 4.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(pad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.TireRepair,
                contentDescription = null,
                tint = BydElectricBlue,
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.width(spacerW))
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(cellSpacing)
                ) {
                    TyreCell("FL", telemetry.tyreTempLF.takeIf { it > 0 }, telemetry.tyrePressureLF, tyreUnit, modifier = Modifier.weight(1f))
                    TyreCell("FR", telemetry.tyreTempRF.takeIf { it > 0 }, telemetry.tyrePressureRF, tyreUnit, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(cellSpacing))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(cellSpacing)
                ) {
                    TyreCell("RL", telemetry.tyreTempLR.takeIf { it > 0 }, telemetry.tyrePressureLR, tyreUnit, modifier = Modifier.weight(1f))
                    TyreCell("RR", telemetry.tyreTempRR.takeIf { it > 0 }, telemetry.tyrePressureRR, tyreUnit, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TyreCell(
    label: String,
    tempC: Int?,
    pressurePsi: Double,
    unit: TyrePressureUnit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            if (tempC != null) {
                Text(
                    text = "${tempC}°",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = RegenGreen,
                    maxLines = 1
                )
            }
            Text(
                text = unit.formatValue(pressurePsi),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
