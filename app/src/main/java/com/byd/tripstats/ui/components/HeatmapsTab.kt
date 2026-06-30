package com.byd.tripstats.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.byd.tripstats.data.config.Drivetrain
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import com.byd.tripstats.data.preferences.PreferencesManager
import com.byd.tripstats.data.preferences.isImperial
import com.byd.tripstats.ui.components.heatmaps.*

@Composable
fun TripHeatmapsTab(dataPoints: List<TripDataPointEntity>) {
    if (dataPoints.size < 30) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Not enough data points for heatmaps.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { PreferencesManager(context.applicationContext) }
    val selectedCar by prefs.selectedCarConfig.collectAsState(initial = null)
    val car = selectedCar ?: return
    val unitSystem by prefs.unitSystem.collectAsState(initial = prefs.getCachedUnitSystem())
    val useImperial = unitSystem.isImperial
    val socSource by prefs.socSource.collectAsState(initial = prefs.getCachedSocSource())
    var selectedDriveMode by remember { mutableStateOf(DriveModeFilter.ALL) }
    var selectedRegenMode by remember { mutableStateOf(RegenModeFilter.ALL) }
    val filteredDataPoints = remember(dataPoints, selectedDriveMode, selectedRegenMode) {
        filterTripPointsByModes(dataPoints, selectedDriveMode, selectedRegenMode)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (hasTripModeData(dataPoints)) {
            Text(
                text = "Mode filters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            ModeFilterRow(
                title = "Drive",
                filters = DriveModeFilter.entries,
                selected = selectedDriveMode,
                onSelected = { selectedDriveMode = it }
            )
            ModeFilterRow(
                title = "Regen",
                filters = RegenModeFilter.entries,
                selected = selectedRegenMode,
                onSelected = { selectedRegenMode = it }
            )
        }

        val consUnit  = if (useImperial) "kWh/100mi" else "kWh/100km"

        HeatmapCard(
            title    = "Power vs Speed",
            subtitle = "Motor output at each speed — shows where the car actually operates"
        ) {
            PowerVsSpeedHeatmap(filteredDataPoints, useImperial, Modifier.fillMaxSize())
        }

        HeatmapCard(
            title    = "Consumption vs Speed",
            subtitle = "Instantaneous efficiency ($consUnit) across the speed range"
        ) {
            ConsumptionVsSpeedHeatmap(filteredDataPoints, useImperial, Modifier.fillMaxSize())
        }

        HeatmapCard(
            title    = "Regen Power vs Speed",
            subtitle = "Regenerative braking strength by speed band"
        ) {
            RegenVsSpeedHeatmap(filteredDataPoints, useImperial, Modifier.fillMaxSize())
        }

        HeatmapCard(
            title = when (car.drivetrain) {
                Drivetrain.FWD -> "Front Motor RPM vs Speed"
                Drivetrain.RWD -> "Rear Motor RPM vs Speed"
                Drivetrain.AWD -> "Motor RPM vs Speed"
            },
            subtitle = when (car.drivetrain) {
                Drivetrain.FWD -> "Front motor operating map — near-linear for a direct-drive EV"
                Drivetrain.RWD -> "Rear motor operating map — near-linear for a direct-drive EV"
                Drivetrain.AWD -> "Dominant motor RPM operating map — near-linear for a direct-drive EV"
            }
        ) {
            RpmVsSpeedHeatmap(
                dataPoints  = filteredDataPoints,
                drivetrain  = car.drivetrain,
                useImperial = useImperial,
                modifier    = Modifier.fillMaxSize()
            )
        }

        HeatmapCard(
            title    = "Battery Temp vs Power",
            subtitle = "Thermal operating window — higher power available as pack warms up"
        ) {
            BatteryTempVsPowerHeatmap(filteredDataPoints, Modifier.fillMaxSize())
        }

        HeatmapCard(
            title    = "Acceleration vs Speed",
            subtitle = "Where the driver accelerates hard or coasts — lower is more efficient"
        ) {
            AccelerationVsSpeedHeatmap(filteredDataPoints, useImperial, Modifier.fillMaxSize())
        }

        HeatmapCard(
            title    = "SOC vs Consumption",
            subtitle = "Whether efficiency changes at low or high charge states"
        ) {
            SocVsConsumptionHeatmap(filteredDataPoints, useImperial, socSource, Modifier.fillMaxSize())
        }

        HeatmapCard(
            title    = "Time of Day vs Speed",
            subtitle = "When and how fast you drive — reveals rush-hour congestion patterns"
        ) {
            TimeOfDayVsSpeedHeatmap(filteredDataPoints, useImperial, Modifier.fillMaxSize())
        }

        HeatmapCard(
            title    = "Gradient vs Consumption",
            subtitle = "How slope affects energy — regen visible in negative-gradient band"
        ) {
            GradientVsConsumptionHeatmap(filteredDataPoints, useImperial, Modifier.fillMaxSize())
        }

        if (car.drivetrain == Drivetrain.AWD) {
            HeatmapCard(
                title    = "Front vs Rear Motor RPM",
                subtitle = "AWD torque split — diagonal = equal share, off-diagonal = one motor dominant"
            ) {
                FrontVsRearRpmHeatmap(filteredDataPoints, Modifier.fillMaxSize())
            }
        }

        HeatmapCard(
            title    = "Tyre Pressure vs Consumption",
            subtitle = "Whether higher pressure measurably improves efficiency on your car"
        ) {
            TyrePressureVsConsumptionHeatmap(filteredDataPoints, useImperial, Modifier.fillMaxSize())
        }

        HeatmapCard(
            title    = "SOC vs Regen Efficiency",
            subtitle = "Where BMS throttles regenerative braking — expected near 100% SoC"
        ) {
            SocVsRegenHeatmap(filteredDataPoints, socSource, Modifier.fillMaxSize())
        }

        HeatmapCard(
            title    = "Speed vs Battery Temperature",
            subtitle = "Motorway thermal load vs stop-start urban — cleaner than Power vs Temp"
        ) {
            SpeedVsBatteryTempHeatmap(filteredDataPoints, useImperial, Modifier.fillMaxSize())
        }

        HeatmapCard(
            title    = "Cell Voltage Spread vs SOC",
            subtitle = "Flat = healthy pack · Divergence spike at low SoC = weak cell"
        ) {
            CellVoltageSpreadVsSocHeatmap(filteredDataPoints, socSource, Modifier.fillMaxSize())
        }
    }
}
