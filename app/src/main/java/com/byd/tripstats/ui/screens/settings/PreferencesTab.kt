package com.byd.tripstats.ui.screens.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.byd.tripstats.R
import com.byd.tripstats.util.LocaleHelper
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
    var showLanguageDialog by remember { mutableStateOf(false) }
    var currentLanguageTag by remember { mutableStateOf(LocaleHelper.getSelectedTag(context)) }
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
        SectionHeader(icon = Icons.Filled.Tune, title = stringResource(R.string.settings_tab_preferences))

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

        SettingsGroupLabel(stringResource(R.string.section_general))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.pref_theme),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.theme_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                        ThemeMode.LIGHT  to stringResource(R.string.theme_light),
                        ThemeMode.DARK   to stringResource(R.string.theme_dark),
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
                    stringResource(R.string.pref_language),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.language_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.current_language_label, LocaleHelper.displayNameForTag(currentLanguageTag)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { showLanguageDialog = true }) {
                    Icon(Icons.Filled.Language, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.change_language_action))
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
                    stringResource(R.string.pref_units),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.units_desc),
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
                        Text(stringResource(R.string.units_metric), fontWeight = FontWeight.Bold)
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
                        Text(stringResource(R.string.units_imperial), fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    if (unitSystem == UnitSystem.IMPERIAL)
                        stringResource(R.string.units_imperial_display)
                    else
                        stringResource(R.string.units_metric_display),
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
                            stringResource(R.string.pref_dashboard_icons),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.dashboard_icons_desc),
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

        SettingsGroupLabel(stringResource(R.string.section_trips))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.pref_engine_off_timeout),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.engine_off_timeout_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.current_timeout_value, carOffTimeoutMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { showCarOffTimeoutDialog = true }) {
                    Icon(Icons.Filled.Timer, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.change_timeout_action))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            stringResource(R.string.pref_auto_stop_prompt),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.auto_stop_prompt_desc),
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
                    stringResource(R.string.pref_min_trip_distance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.min_trip_distance_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.min_trip_distance_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (minTripDistanceKm > 0.0) {
                        stringResource(
                            R.string.current_minimum_value,
                            "%.2f".format(unitSystem.convertDistance(minTripDistanceKm)),
                            unitSystem.distanceUnit
                        )
                    } else {
                        stringResource(R.string.current_minimum_disabled)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { showMinTripDistanceDialog = true }) {
                    Icon(Icons.Filled.Straighten, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (minTripDistanceKm > 0.0) stringResource(R.string.change_minimum_action) else stringResource(R.string.set_minimum_action))
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
                    stringResource(R.string.pref_goals_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(
                        R.string.goal_consumption_value,
                        tripGoals.targetConsumptionKwhPer100km?.let { "%.1f ${unitSystem.consumptionUnit}".format(unitSystem.convertEfficiency(it)) } ?: stringResource(R.string.not_set)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(
                        R.string.goal_monthly_distance_value,
                        tripGoals.targetDistanceKmPerMonth?.let { "%.0f ${unitSystem.distanceUnit}".format(unitSystem.convertDistance(it)) } ?: stringResource(R.string.not_set)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(
                        R.string.best_consumption_value,
                        personalBests.bestConsumption?.let { "%.1f ${unitSystem.consumptionUnit}".format(unitSystem.convertEfficiency(it)) } ?: "—"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(
                        R.string.best_distance_value,
                        personalBests.bestDistance?.let { "%.1f ${unitSystem.distanceUnit}".format(unitSystem.convertDistance(it)) } ?: "—"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onNavigateToTripGoals) {
                    Icon(Icons.Filled.EmojiEvents, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.open_goals_action))
                }
            }
        }

        SettingsGroupLabel(stringResource(R.string.section_battery))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.pref_soc_source),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.soc_source_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        SocSource.PANEL to stringResource(R.string.soc_panel_option),
                        SocSource.BMS   to stringResource(R.string.soc_bms_option),
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
                        stringResource(R.string.soc_source_info),
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
                                stringResource(R.string.pref_cell_imbalance),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (!isPro) {
                                Spacer(Modifier.width(8.dp))
                                ProBadge()
                            }
                        }
                        Text(
                            stringResource(R.string.cell_imbalance_desc),
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
                            Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.unlock_pro_action))
                        }
                    }
                }
                Text(
                    stringResource(R.string.cell_imbalance_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isPro && cellImbalanceAlertEnabled) {
                    Text(
                        stringResource(R.string.current_limit_value, "%.0f".format(cellImbalanceThresholdV * 1000)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { showCellImbalanceThresholdDialog = true }) {
                        Icon(Icons.Filled.BatteryAlert, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.change_limit_action))
                    }
                } else if (!isPro) {
                    Text(
                        stringResource(R.string.pro_feature_imbalance_msg),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { showLicenseDialog = true }) {
                        Icon(Icons.Filled.Lock, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.unlock_pro_action))
                    }
                }
            }
        }

        SettingsGroupLabel(stringResource(R.string.section_costs))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.pref_electricity_tariff),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (electricityPrice > 0.0) {
                        stringResource(R.string.current_rate_value, "%.4f".format(electricityPrice), currencySymbol)
                    } else {
                        stringResource(R.string.tariff_not_set_desc)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { showTariffDialog = true }) {
                    Icon(Icons.Filled.Euro, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (electricityPrice > 0.0) stringResource(R.string.edit_tariff_action) else stringResource(R.string.set_tariff_action))
                }
            }
        }

        SettingsGroupLabel(stringResource(R.string.section_power))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.pref_background_activity),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.background_activity_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        OffStateMode.ENABLED    to stringResource(R.string.bg_always_on),
                        OffStateMode.DISABLED   to stringResource(R.string.bg_minimal),
                        OffStateMode.DEEP_SLEEP to stringResource(R.string.bg_deep_sleep),
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
                                    if (mode == OffStateMode.ENABLED) stringResource(R.string.bg_default_label) else "",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
                listOf(
                    OffStateMode.ENABLED    to stringResource(R.string.bg_always_on_desc),
                    OffStateMode.DISABLED   to stringResource(R.string.bg_minimal_desc),
                    OffStateMode.DEEP_SLEEP to stringResource(R.string.bg_deep_sleep_desc),
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
                val armedSuffix = if (kaArmed) stringResource(R.string.keepalive_armed) else ""
                val notArmedSuffix = if (kaArmed) "" else stringResource(R.string.keepalive_not_armed)
                val kaStatus = if (kaLastFired <= 0L) {
                    stringResource(R.string.keepalive_never_fired, armedSuffix)
                } else {
                    val agoMin = ((System.currentTimeMillis() - kaLastFired) / 60_000L).coerceAtLeast(0)
                    val ago = if (agoMin < 60) "$agoMin min ago" else "${agoMin / 60}h ${agoMin % 60}m ago"
                    stringResource(R.string.keepalive_fired, ago, kaCount, notArmedSuffix)
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
            title = { Text(stringResource(R.string.tariff_dialog_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.tariff_input_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = { Text(stringResource(R.string.price_per_kwh_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Box {
                        OutlinedTextField(
                            value = "${selectedCurrency.first} (${selectedCurrency.second})",
                            onValueChange = { },
                            label = { Text(stringResource(R.string.currency_label)) },
                            singleLine = true,
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { currencyMenuExpanded = !currencyMenuExpanded }) {
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.currency_label))
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
                            stringResource(R.string.active_tariff_value, "%.4f".format(electricityPrice), currencySymbol),
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
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showTariffDialog = false }) { Text(stringResource(R.string.cancel)) }
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
            title = { Text(stringResource(R.string.timeout_dialog_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.timeout_input_desc, DEFAULT_CAR_OFF_TIMEOUT_MINUTES),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = minutesInput,
                        onValueChange = { minutesInput = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.minutes_label)) },
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
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showCarOffTimeoutDialog = false }) { Text(stringResource(R.string.cancel)) }
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
            title = { Text(stringResource(R.string.min_distance_dialog_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.min_distance_input_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = distanceInput,
                        onValueChange = { distanceInput = it },
                        label = { Text(stringResource(R.string.distance_unit_label, unitSystem.distanceUnit)) },
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
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showMinTripDistanceDialog = false }) { Text(stringResource(R.string.cancel)) }
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
            title = { Text(stringResource(R.string.imbalance_dialog_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.imbalance_input_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = thresholdInput,
                        onValueChange = { thresholdInput = it },
                        label = { Text(stringResource(R.string.limit_mv_label)) },
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
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showCellImbalanceThresholdDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showLanguageDialog) {
        val activity = context as? Activity
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Language", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    LocaleHelper.supportedLanguages.forEach { lang ->
                        val selected = lang.tag == currentLanguageTag
                        Row(
                            modifier = Modifier.clickable {
                                if (!selected) {
                                    LocaleHelper.saveTag(context, lang.tag)
                                    currentLanguageTag = lang.tag
                                    showLanguageDialog = false
                                    activity?.recreate()
                                } else {
                                    showLanguageDialog = false
                                }
                            }
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        LocaleHelper.saveTag(context, lang.tag)
                                        currentLanguageTag = lang.tag
                                        showLanguageDialog = false
                                        activity?.recreate()
                                    } else {
                                        showLanguageDialog = false
                                    }
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(lang.displayName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text("Cancel") }
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

