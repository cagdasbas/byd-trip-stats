package com.byd.tripstats.ui.screens

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.tripstats.R
import com.byd.tripstats.data.config.CarCatalog
import com.byd.tripstats.data.model.VehicleTelemetry
import com.byd.tripstats.data.preferences.PreferencesManager
import com.byd.tripstats.data.preferences.SocSource
import com.byd.tripstats.data.preferences.UnitSystem
import com.byd.tripstats.data.preferences.convertDistance
import com.byd.tripstats.sdk.VehicleTelemetrySnapshot
import com.byd.tripstats.ui.components.CompactBattery
import com.byd.tripstats.ui.components.RangeDataPoint
import com.byd.tripstats.ui.screens.dashboard.*
import com.byd.tripstats.ui.theme.AccelerationOrange
import com.byd.tripstats.ui.theme.BatteryBlue
import com.byd.tripstats.ui.theme.RegenGreen
import com.byd.tripstats.ui.viewmodel.DashboardViewModel
import com.byd.tripstats.data.entitlement.EntitlementManager
import com.byd.tripstats.util.ScreenshotUtil
import kotlinx.coroutines.launch

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
    val isPro by EntitlementManager.isPro.collectAsState()
    val batteryVoltageHistory24h by viewModel.batteryVoltageHistory24h.collectAsState()
    val isMockModeActive by viewModel.isMockModeActive.collectAsState()
    val isInTrip by viewModel.isInTrip.collectAsState()
    val pendingAutoStop by viewModel.pendingAutoStop.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val autoTripDetection by viewModel.autoTripDetection.collectAsState()
    val tripDataPoints by viewModel.tripDataPoints.collectAsState()
    val liveDistanceKm by viewModel.liveDistanceKm.collectAsState()
    val liveOdometerDistanceKm by viewModel.liveOdometerDistanceKm.collectAsState()
    val liveSegmentDistanceKm by viewModel.liveSegmentDistanceKm.collectAsState()
    val liveSessionStartMs by viewModel.liveSessionStartMs.collectAsState()
    val liveOffStateMs by viewModel.liveOffStateMs.collectAsState()
    val liveAccumulatedKwh by viewModel.liveAccumulatedKwh.collectAsState()
    val activeRangeModel by viewModel.activeRangeModel.collectAsState()
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
    val socSource by prefs.socSource.collectAsState(initial = prefs.getCachedSocSource())
    val scope = rememberCoroutineScope()
    var showCarSelectionDialog by remember { mutableStateOf(false) }
    var showBattery12vDialog by remember { mutableStateOf(false) }
    var showScreenshotFlash by remember { mutableStateOf(false) }
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
            context.getString(R.string.car_type_bev) to CarCatalog.groupedBev,
            context.getString(R.string.car_type_phev) to CarCatalog.groupedPhev
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val logoInteractionSource = remember { MutableInteractionSource() }
                        Image(
                            painter = painterResource(id = R.drawable.byd_logo),
                            contentDescription = "BYD — tap to save a screenshot",
                            modifier = Modifier
                                .height(28.dp)
                                .clickable(
                                    interactionSource = logoInteractionSource,
                                    indication = null
                                ) {
                                    if (!isPro) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.screenshots_pro_only),
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return@clickable
                                    }
                                    scope.launch {
                                        try {
                                            ScreenshotUtil.captureAndSave(
                                                activity,
                                                onCaptured = { showScreenshotFlash = true }
                                            )
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.screenshot_failed, e.message),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
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
                                soc = (if (socSource == SocSource.PANEL) telemetry?.socPanel?.toFloat() else telemetry?.soc?.toFloat()) ?: 0f,
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
          Box(modifier = Modifier.fillMaxSize()) {
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
                socSource = socSource,
                onNavigateToCharging = onNavigateToCharging,
                widthSizeClass = widthSizeClass,
                sessionDistanceKm = liveSegmentDistanceKm,
                tripDistanceKm = liveDistanceKm,
                liveOdometerDistanceKm = liveOdometerDistanceKm,
                liveSessionStartMs = liveSessionStartMs,
                liveOffStateMs = liveOffStateMs,
                liveAccumulatedKwh = liveAccumulatedKwh,
                activeRangeModel = activeRangeModel,
                modifier = Modifier.padding(paddingValues)
            )
            if (currentTelemetry.speedGetterWedged) {
                SpeedWedgeBanner(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(paddingValues)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
          }
        }
    }

        ScreenshotFlash(
            visible = showScreenshotFlash,
            onFinished = { showScreenshotFlash = false }
        )
    }

    telemetry?.let { currentTelemetry ->
        if (showBattery12vDialog) {
            Battery12vHistoryDialog(
                historyPoints = batteryVoltageHistory24h,
                telemetry = currentTelemetry,
                vehicleSnapshot = vehicleSnapshot,
                socSource = socSource,
                onDismiss = { showBattery12vDialog = false }
            )
        }
    }

    if (pendingAutoStop) {
        val offTimeoutMin = remember { prefs.getCachedCarOffTimeoutMinutes() }
        AlertDialog(
            onDismissRequest = { /* require Keep or Stop — don't dismiss on outside tap */ },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text(stringResource(R.string.keep_trip_going_title)) },
            text = {
                Text(stringResource(R.string.keep_recording_body, offTimeoutMin))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.keepTripAcrossOff() }) {
                    Text(stringResource(R.string.keep_recording_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmAutoStop() }) {
                    Text(stringResource(R.string.stop_trip_action))
                }
            }
        )
    }

    if (showCarSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showCarSelectionDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = {
                Text(stringResource(R.string.select_car_label))
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
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showTyreUnitDialog) {
        AlertDialog(
            onDismissRequest = { showTyreUnitDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text(stringResource(R.string.tyre_pressure_unit_title)) },
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
                                    TyrePressureUnit.BAR -> stringResource(R.string.tyre_unit_bar)
                                    TyrePressureUnit.PSI -> stringResource(R.string.tyre_unit_psi)
                                    TyrePressureUnit.KPA -> stringResource(R.string.tyre_unit_kpa)
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTyreUnitDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun ScreenshotFlash(visible: Boolean, onFinished: () -> Unit) {
    if (!visible) return
    val alpha = remember { Animatable(0.9f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(0f, animationSpec = tween(durationMillis = 320, easing = LinearEasing))
        onFinished()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = alpha.value))
    )
}

@Composable
private fun SpeedWedgeBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null)
            Column {
                Text(
                    stringResource(R.string.live_telemetry_paused),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.speed_wedge_banner),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
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
    socSource: SocSource = SocSource.PANEL,
    onNavigateToCharging: () -> Unit = {},
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Expanded,
    sessionDistanceKm: Double = 0.0,
    tripDistanceKm: Double = 0.0,
    liveOdometerDistanceKm: Double = 0.0,
    liveSessionStartMs: Long? = null,
    liveOffStateMs: Long = 0L,
    liveAccumulatedKwh: Double = 0.0,
    activeRangeModel: DashboardViewModel.RangeModel = DashboardViewModel.RangeModel.BASELINE,
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context.applicationContext) }
    val unitSystem by prefs.unitSystem.collectAsState(initial = prefs.getCachedUnitSystem())

    val styledModifier = modifier.background(MaterialTheme.colorScheme.background)

    when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            Column(
                modifier = styledModifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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

                EnergyFlowDiagram(
                    telemetry = telemetry,
                    liveSpeedKmh = vehicleSnapshot?.directSpeedKmh,
                    livePowerKw = vehicleSnapshot?.enginePower,
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
                    socSource = socSource,
                    activeRangeModel = activeRangeModel,
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

                TripControls(
                    telemetry = telemetry,
                    liveGear = vehicleSnapshot?.gear,
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
            Row(
                modifier = styledModifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.72f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EnergyFlowDiagram(
                        telemetry = telemetry,
                        liveSpeedKmh = vehicleSnapshot?.directSpeedKmh,
                        livePowerKw = vehicleSnapshot?.enginePower,
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
                        socSource = socSource,
                        activeRangeModel = activeRangeModel,
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
                        liveGear = vehicleSnapshot?.gear,
                        isInTrip = isInTrip,
                        autoTripDetection = autoTripDetection,
                        onStartTrip = onStartTrip,
                        onEndTrip = onEndTrip,
                        onToggleAutoDetection = onToggleAutoDetection,
                        liveSessionStartMs = liveSessionStartMs,
                        liveOffStateMs = liveOffStateMs,
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
                        liveSpeedKmh = vehicleSnapshot?.directSpeedKmh,
                        livePowerKw = vehicleSnapshot?.enginePower,
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
                        socSource = socSource,
                        activeRangeModel = activeRangeModel,
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
                        liveGear = vehicleSnapshot?.gear,
                        isInTrip = isInTrip,
                        autoTripDetection = autoTripDetection,
                        onStartTrip = onStartTrip,
                        onEndTrip = onEndTrip,
                        onToggleAutoDetection = onToggleAutoDetection,
                        liveSessionStartMs = liveSessionStartMs,
                        liveOffStateMs = liveOffStateMs,
                        liveDistanceKm = tripDistanceKm,
                        liveOdometerDistanceKm = liveOdometerDistanceKm,
                        liveAccumulatedKwh = liveAccumulatedKwh,
                        unitSystem = unitSystem,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

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
