package com.byd.tripstats.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.byd.tripstats.data.entitlement.EntitlementManager
import com.byd.tripstats.data.entitlement.RedeemResult
import com.byd.tripstats.data.preferences.DEFAULT_CAR_OFF_TIMEOUT_MINUTES
import com.byd.tripstats.data.preferences.OffStateMode
import com.byd.tripstats.data.preferences.PreferencesManager
import com.byd.tripstats.data.preferences.SocSource
import com.byd.tripstats.data.preferences.ThemeMode
import com.byd.tripstats.data.preferences.UnitSystem
import com.byd.tripstats.data.preferences.convertDistance
import com.byd.tripstats.data.preferences.convertEfficiency
import com.byd.tripstats.data.preferences.consumptionUnit
import com.byd.tripstats.data.preferences.distanceUnit
import com.byd.tripstats.data.preferences.toKilometers
import com.byd.tripstats.receiver.OffStateKeepaliveReceiver
import com.byd.tripstats.service.VehicleTelemetryService
import com.byd.tripstats.ui.theme.BydElectricAzure
import com.byd.tripstats.ui.theme.ToggleUncheckedTrack
import com.byd.tripstats.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch

@Composable
internal fun AppPreferencesTab(
    viewModel: DashboardViewModel,
    preferencesManager: PreferencesManager,
    onNavigateToTripGoals: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val offStateMode by preferencesManager.offStateMode.collectAsState(
        initial = preferencesManager.getCachedOffStateMode()
    )
    val dashboardIconsEnabled by preferencesManager.dashboardAnimationsEnabled.collectAsState(
        initial = preferencesManager.getCachedAnimationsEnabled()
    )
    val carOffTimeoutMinutes by preferencesManager.carOffTimeoutMinutes.collectAsState(
        initial = preferencesManager.getCachedCarOffTimeoutMinutes()
    )
    val confirmBeforeAutoStop by preferencesManager.confirmBeforeAutoStop.collectAsState(
        initial = preferencesManager.getCachedConfirmBeforeAutoStop()
    )
    val minTripDistanceKm by preferencesManager.minTripDistanceKm.collectAsState(
        initial = preferencesManager.getCachedMinTripDistanceKm()
    )
    val cellImbalanceAlertEnabled by preferencesManager.cellImbalanceAlertEnabled.collectAsState(
        initial = preferencesManager.getCachedCellImbalanceAlertEnabled()
    )
    val cellImbalanceThresholdV by preferencesManager.cellImbalanceThresholdV.collectAsState(
        initial = preferencesManager.getCachedCellImbalanceThresholdV()
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
    var showCellImbalanceThresholdDialog by remember { mutableStateOf(false) }
    val isPro by EntitlementManager.isPro.collectAsState()
    val hasSavedCode by EntitlementManager.hasSavedCode.collectAsState()
    val currentDeviceId by EntitlementManager.currentDeviceId.collectAsState()
    var showLicenseDialog by remember { mutableStateOf(false) }
    var priceInput by remember(electricityPrice) {
        mutableStateOf(if (electricityPrice > 0.0) "%.4f".format(electricityPrice) else "")
    }
    val currencyOptions = remember {
        listOf(
            "€" to "EUR",
            "£" to "GBP",
            "$" to "USD",
            "A$" to "AUD",
            "฿" to "THB",
            "R$" to "BRL"
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

        // Locked → show the Pro upsell at the very top (prominent). Once unlocked it's just
        // status + a rarely-used "Remove code", so it's rendered at the bottom of the page instead.
        if (!isPro) {
            ProUnlockCard(
                isPro = isPro,
                currentDeviceId = currentDeviceId,
                hasSavedCode = hasSavedCode,
                onEnterCode = { showLicenseDialog = true },
            )
        }

        SettingsGroupLabel("General")

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

        SettingsGroupLabel("Trips")

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

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Ask before auto-stopping",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "When the timeout is reached while the app is open on screen, show a prompt to keep the trip going (and its live range projection) instead of stopping it — handy when you park but stay with the car. If you don't have the app open, or you leave it, the trip still stops automatically as usual. A kept trip ends once you drive again, tap Stop, or after ~30 min parked.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = confirmBeforeAutoStop,
                        onCheckedChange = { enabled ->
                            scope.launch { preferencesManager.saveConfirmBeforeAutoStop(enabled) }
                        }
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

        SettingsGroupLabel("Battery & telemetry")

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Cell imbalance alert",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (!isPro) {
                                Spacer(Modifier.width(8.dp))
                                ProBadge()
                            }
                        }
                        Text(
                            "Notify me when the pack's cell voltage spread stays above the limit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isPro) {
                        Switch(
                            checked = cellImbalanceAlertEnabled,
                            onCheckedChange = {
                                scope.launch { preferencesManager.saveCellImbalanceAlertEnabled(it) }
                            },
                            thumbContent = if (!cellImbalanceAlertEnabled) {
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
                    } else {
                        // Locked — tapping the lock opens the unlock prompt.
                        IconButton(onClick = { showLicenseDialog = true }) {
                            Icon(Icons.Filled.Lock, contentDescription = "Unlock with Pro")
                        }
                    }
                }
                Text(
                    "Spread = highest cell − lowest cell. A healthy pack stays under ~20 mV; a " +
                        "persistently high spread can flag a weak cell. Alerts are suppressed below " +
                        "5% and above 95% SoC, where a wide spread is normal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isPro && cellImbalanceAlertEnabled) {
                    Text(
                        "Current limit: %.0f mV".format(cellImbalanceThresholdV * 1000),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { showCellImbalanceThresholdDialog = true }) {
                        Icon(Icons.Filled.BatteryAlert, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Change limit")
                    }
                } else if (!isPro) {
                    Text(
                        "This is a BYD Trip Stats Pro feature. Unlock Pro to receive imbalance alerts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { showLicenseDialog = true }) {
                        Icon(Icons.Filled.Lock, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Unlock with Pro")
                    }
                }
            }
        }

        SettingsGroupLabel("Costs")

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

        SettingsGroupLabel("Power & background")

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
                            onClick = {
                                scope.launch { preferencesManager.saveOffStateMode(mode) }
                                // Reconcile background scheduling to the new mode immediately.
                                // saveOffStateMode only persists the pref; without this, the
                                // keepalive chain is (re)armed only on the next ACC_OFF, so
                                // switching e.g. Deep Sleep → Minimal while the car is already
                                // parked would leave NO keepalive armed (Deep Sleep cancels it),
                                // and off-state charging would never be sampled until the next
                                // drive. Apply the change now instead of waiting for a car cycle.
                                when (mode) {
                                    OffStateMode.DEEP_SLEEP ->
                                        OffStateKeepaliveReceiver.cancel(context)
                                    OffStateMode.DISABLED ->
                                        // Arm only if absent — if the service is still running it
                                        // will schedule the chain itself on self-stop.
                                        OffStateKeepaliveReceiver.ensureScheduled(context, "settings:minimal")
                                    OffStateMode.ENABLED -> {
                                        // Always On: keepalive is redundant (watchdog/restarter
                                        // keep the service alive); start the service now so it
                                        // goes resident even if the car is currently parked.
                                        OffStateKeepaliveReceiver.cancel(context)
                                        VehicleTelemetryService.start(context)
                                    }
                                }
                            },
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
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(label, fontWeight = FontWeight.Bold)
                                // Empty second line on the other two keeps all buttons the
                                // same height while marking Always On as the default.
                                Text(
                                    if (mode == OffStateMode.ENABLED) "Default" else "",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
                listOf(
                    OffStateMode.ENABLED    to "Always On (recommended default): service runs 24/7, taking continuous 12V/SoC samples into battery history and capturing off-state (e.g. overnight) charging. Small additional load on top of BYD's own stock background drain.\n\nNote: most BYD units cut WiFi ~15 min after the car is switched off, and the app can't override that. So while parked, ADB-over-WiFi, the Web Companion (PWA) and MQTT to a broker on your home network stop until the car powers on. MQTT to an internet-reachable broker (e.g. HiveMQ Cloud) usually keeps publishing while parked, because the car stays on mobile data.\nNote2: Electro app can override the above and keep the WiFi indefinitely when car is off",
                    OffStateMode.DISABLED   to "Minimal: service self-stops 5 min after the car turns off, then a 90-min alarm briefly wakes it for a charging snapshot. Lower drain, but off-state samples are sparse, ADB is not always on, the Web Companion (PWA) is only reachable during the brief wake blip, and MQTT is idle between blips. Off-state charging is caught within ~90 min, after which it publishes for the rest of the charge.",
                    OffStateMode.DEEP_SLEEP to "Deep Sleep: service self-stops 5 min after the car turns off with no further wakeups. Allows the car's ECUs to reach full deep sleep. The Web Companion (PWA) is unreachable and MQTT is silent the whole time the car is parked; off-state charging is not captured live — only reconstructed from the SoC delta when the car next turns on.",
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
                // Keepalive health — lets the user tell whether Minimal actually works on
                // their vehicle. The 90-min keepalive can't fire on head units that fully
                // power off at park, so a "never fired" here (after a real park) is the
                // signal that Minimal behaves like Deep Sleep and Always On is needed.
                val kaLastFired = OffStateKeepaliveReceiver.lastFiredMs(context)
                val kaCount     = OffStateKeepaliveReceiver.fireCount(context)
                val kaArmed     = OffStateKeepaliveReceiver.isScheduled(context)
                val kaStatus = if (kaLastFired <= 0L) {
                    "Keepalive: never fired yet${if (kaArmed) " (armed)" else ""}. If this stays " +
                        "\"never\" after the car has been parked a while, your head unit powers off " +
                        "fully at park — Minimal then behaves like Deep Sleep, so use Always On for parked data."
                } else {
                    val agoMin = ((System.currentTimeMillis() - kaLastFired) / 60_000L).coerceAtLeast(0)
                    val ago = if (agoMin < 60) "$agoMin min ago" else "${agoMin / 60}h ${agoMin % 60}m ago"
                    "Keepalive: last fired $ago • $kaCount total${if (kaArmed) " • armed" else " • not armed"}"
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                Text(
                    kaStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Unlocked → Pro is just status + the rarely-used "Remove code", so it lives at the
        // bottom of the page (out of the way) rather than up top where the upsell sits when locked.
        if (isPro) {
            ProUnlockCard(
                isPro = isPro,
                currentDeviceId = currentDeviceId,
                hasSavedCode = hasSavedCode,
                onEnterCode = { showLicenseDialog = true },
            )
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

    if (showCellImbalanceThresholdDialog) {
        // Edit in millivolts — friendlier than typing 0.05. Stored in volts.
        var thresholdInput by remember(cellImbalanceThresholdV) {
            mutableStateOf("%.0f".format(cellImbalanceThresholdV * 1000))
        }
        AlertDialog(
            onDismissRequest = { showCellImbalanceThresholdDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Cell imbalance limit", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Alert when the cell voltage spread stays above this for a few seconds. " +
                            "Typical limit: 50 mV. Allowed range: 10–500 mV.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = thresholdInput,
                        onValueChange = { thresholdInput = it },
                        label = { Text("Limit (mV)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mv = thresholdInput.replace(',', '.').toDoubleOrNull()
                        if (mv != null) {
                            scope.launch { preferencesManager.saveCellImbalanceThresholdV(mv / 1000.0) }
                        }
                        showCellImbalanceThresholdDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showCellImbalanceThresholdDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showLicenseDialog) {
        var codeInput by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Unlock BYD Trip Stats Pro", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter the unlock code you received after purchase. It's a short, " +
                            "vehicle-specific code, checked on-device — nothing leaves your car.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it; errorMsg = null },
                        label = { Text("Unlock code") },
                        singleLine = true,
                        isError = errorMsg != null
                    )
                    if (errorMsg != null) {
                        Text(
                            errorMsg!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (EntitlementManager.redeem(codeInput)) {
                            RedeemResult.SUCCESS -> {
                                android.widget.Toast.makeText(
                                    context, "Pro unlocked ✓", android.widget.Toast.LENGTH_SHORT
                                ).show()
                                showLicenseDialog = false
                            }
                            RedeemResult.INVALID ->
                                errorMsg = "That code isn't valid for this vehicle."
                            RedeemResult.NO_VEHICLE_YET ->
                                errorMsg = "Start the car so the app can read your Vehicle ID, then try again."
                            RedeemResult.UNAVAILABLE ->
                                errorMsg = "Pro verification is unavailable in this build."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                ) { Text("Unlock") }
            },
            dismissButton = {
                TextButton(onClick = { showLicenseDialog = false }) { Text("Cancel") }
            }
        )
    }
}

