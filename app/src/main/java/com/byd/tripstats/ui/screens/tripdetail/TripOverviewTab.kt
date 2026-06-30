package com.byd.tripstats.ui.screens.tripdetail

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.byd.tripstats.data.analysis.calculateTripEnergyBreakdown
import com.byd.tripstats.data.config.CarConfig
import com.byd.tripstats.data.local.entity.ChargingSessionEntity
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import com.byd.tripstats.data.local.entity.TripEntity
import com.byd.tripstats.data.local.entity.TripStatsEntity
import com.byd.tripstats.data.preferences.SocSource
import com.byd.tripstats.data.preferences.UnitSystem
import com.byd.tripstats.data.preferences.convertDistance
import com.byd.tripstats.data.preferences.convertEfficiency
import com.byd.tripstats.data.preferences.distanceUnit
import com.byd.tripstats.data.preferences.speedUnit
import com.byd.tripstats.ui.screens.DetailRow
import com.byd.tripstats.ui.screens.EditableDetailRow
import com.byd.tripstats.ui.theme.*
import kotlin.math.abs

@Composable
fun TripOverviewTab(
    trip: TripEntity,
    stats: TripStatsEntity?,
    dataPoints: List<TripDataPointEntity>,
    selectedCarConfig: CarConfig?,
    regenEfficiencyPct: Double?,
    electricityPrice: Double = 0.0,
    currencySymbol: String = "€",
    unitSystem: UnitSystem = UnitSystem.METRIC,
    socSource: SocSource = SocSource.PANEL,
    chargingSessions: List<ChargingSessionEntity> = emptyList(),
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
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = (MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)))

                DetailRow("Initial mileage", "${String.format("%.1f", trip.startOdometer)} ${unitSystem.distanceUnit}")
                DetailRow("Final mileage", trip.endOdometer?.let { "${String.format("%.1f", it)} ${unitSystem.distanceUnit}" } ?: "-")
                DetailRow("Trip distance", trip.distance?.let { "${String.format("%.1f", unitSystem.convertDistance(it))} ${unitSystem.distanceUnit}" } ?: "-")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = (MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)))

                if (socSource == SocSource.PANEL) {
                    DetailRow("Start SoC (Panel)", "${trip.startSocPanel.toInt()}%")
                    DetailRow("End SoC (Panel)", trip.endSocPanel?.let { "${it.toInt()}%" } ?: "-")
                    DetailRow("SoC Change (Panel)", trip.socPanelDelta?.let { "${String.format("%.1f", it)}%" } ?: "-")
                } else {
                    DetailRow("Start SoC (BMS)", "${String.format("%.1f", trip.startSoc)}%")
                    DetailRow("End SoC (BMS)", trip.endSoc?.let { "${String.format("%.1f", it)}%" } ?: "-")
                    DetailRow("SoC Change (BMS)", trip.socDelta?.let { "${String.format("%.1f", it)}%" } ?: "-")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = (MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)))

                DetailRow("Max Speed", "${trip.maxSpeed.toInt()} ${unitSystem.speedUnit}")
                DetailRow("Avg Speed", stats?.avgSpeed?.toInt()?.toString()?.plus(" ${unitSystem.speedUnit}") ?: "-")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = (MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)))

                DetailRow("Max Power", "${trip.maxPower.toInt()} kW")
                DetailRow("Max Regen", "${abs(trip.maxRegenPower).toInt()} kW")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = (MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)))

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
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = (MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)))

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

                    DetailRow("Total consumed", formatKwh(breakdown.totalConsumedKwh))

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                    )

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

                    DetailRow(
                        "Rolling resistance",
                        if (breakdown.hasPhysicsBreakdown) {
                            "${formatKwh(rollingDisplay)} (${pct(rollingDisplay)}%)"
                        } else "n/a"
                    )

                    DetailRow(
                        "Aerodynamic drag",
                        if (breakdown.hasAeroEstimate) {
                            "${formatKwh(aeroDisplay)} (${pct(aeroDisplay)}%)"
                        } else "n/a"
                    )

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
                        keyboardOptions = KeyboardOptions(
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
    val rangeValid = min != null && max != null && max >= min && (max - min) <= 25
    return when {
        rangeValid -> "${min}°C - ${max}°C"
        min != null -> "${min}°C"
        max != null && max in -40..80 -> "${max}°C"
        else -> "-"
    }
}

private fun tripBatteryAvgTempLabel(trip: TripEntity): String {
    val min = trip.minBatteryCellTemp.takeIf { it in -40..120 }?.toDouble()
    val max = trip.maxBatteryCellTemp.takeIf { it in -40..120 }?.toDouble()
    if (min != null && max != null && max >= min && (max - min) <= 25) {
        return "${((min + max) / 2.0).toInt()}°C"
    }
    val avg = trip.avgBatteryTemp.takeIf { it.isFinite() && it in -40.0..120.0 } ?: return "-"
    return "${avg.toInt()}°C"
}

private fun formatKwh(value: Double): String =
    String.format("%.2f kWh", value)

private fun formatSignedKwh(value: Double): String =
    if (value >= 0.0) "+${formatKwh(value)}" else "-${formatKwh(abs(value))}"

private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) String.format("%dh %dmin", hours, minutes)
    else String.format("%dmin", minutes)
}

private fun formatTimestamp(timestamp: Long): String {
    val instant = java.time.Instant.ofEpochMilli(timestamp)
    val formatter = java.time.format.DateTimeFormatter
        .ofPattern("MMM dd, yyyy HH:mm")
        .withZone(java.time.ZoneId.systemDefault())
    return formatter.format(instant)
}
