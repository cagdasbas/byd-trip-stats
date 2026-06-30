package com.byd.tripstats.ui.screens.tripdetail

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import com.byd.tripstats.ui.components.AltitudeChart
import com.byd.tripstats.ui.components.BatteryVoltageChart
import com.byd.tripstats.ui.components.CondensedAltitudeChart
import com.byd.tripstats.ui.components.CondensedBatteryVoltageChart
import com.byd.tripstats.ui.components.CondensedEnergyChart
import com.byd.tripstats.ui.components.CondensedInstantConsumptionChart
import com.byd.tripstats.ui.components.CondensedMotorRpmChart
import com.byd.tripstats.ui.components.CondensedPowerChart
import com.byd.tripstats.ui.components.CondensedSocChart
import com.byd.tripstats.ui.components.CondensedSpeedChart
import com.byd.tripstats.ui.components.CondensedTyrePressureChart
import com.byd.tripstats.ui.components.EnergyConsumptionChart
import com.byd.tripstats.ui.components.InstantConsumptionChart
import com.byd.tripstats.ui.components.ModeTimelineChart
import com.byd.tripstats.ui.components.ModeTimelineControls
import com.byd.tripstats.ui.components.MotorRpmChart
import com.byd.tripstats.ui.components.PowerChart
import com.byd.tripstats.ui.components.SocChart
import com.byd.tripstats.ui.components.SpeedChart
import com.byd.tripstats.ui.components.TyrePressureChart
import com.byd.tripstats.ui.components.condenseData
import com.byd.tripstats.ui.components.condenseForPower
import com.byd.tripstats.ui.components.condenseForRpm
import com.byd.tripstats.ui.components.condenseForSpeed
import com.byd.tripstats.ui.components.extractTripModes

@Composable
fun TripChartsTab(
    dataPoints: List<TripDataPointEntity>,
    useImperial: Boolean = false
) {
    var expandedChart by remember { mutableStateOf<ChartType?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ClickableChartCard(
            title = "Speed Profile",
            subtitle = "${if (useImperial) "mph" else "km/h"} over time",
            onClick = { expandedChart = ChartType.SPEED }
        ) {
            CondensedSpeedChart(dataPoints = dataPoints, useImperial = useImperial, modifier = Modifier.fillMaxSize())
        }

        ClickableChartCard(
            title = "Motor RPM",
            subtitle = "RPM over time",
            onClick = { expandedChart = ChartType.MOTOR_RPM }
        ) {
            CondensedMotorRpmChart(dataPoints = dataPoints, modifier = Modifier.fillMaxSize())
        }

        ClickableChartCard(
            title = "Power Profile",
            subtitle = "kW over time",
            onClick = { expandedChart = ChartType.POWER }
        ) {
            CondensedPowerChart(dataPoints = dataPoints, modifier = Modifier.fillMaxSize())
        }

        ClickableChartCard(
            title = "Energy Consumption",
            subtitle = "kWh over time",
            onClick = { expandedChart = ChartType.ENERGY }
        ) {
            CondensedEnergyChart(dataPoints = dataPoints, modifier = Modifier.fillMaxSize())
        }

        ClickableChartCard(
            title = "State of Charge",
            subtitle = "SoC% over time",
            onClick = { expandedChart = ChartType.SOC }
        ) {
            CondensedSocChart(dataPoints = dataPoints, modifier = Modifier.fillMaxSize())
        }

        ClickableChartCard(
            title = "Elevation Profile",
            subtitle = "Altitude over time",
            onClick = { expandedChart = ChartType.ALTITUDE }
        ) {
            CondensedAltitudeChart(dataPoints = dataPoints, modifier = Modifier.fillMaxSize())
        }

        ClickableChartCard(
            title = "Battery Voltage",
            subtitle = "HV bus + cell min/max over time",
            onClick = { expandedChart = ChartType.BATTERY_VOLTAGE }
        ) {
            CondensedBatteryVoltageChart(dataPoints = dataPoints, modifier = Modifier.fillMaxSize())
        }

        ClickableChartCard(
            title = "Tyre Pressures",
            subtitle = "All four wheels (pressure) over time",
            onClick = { expandedChart = ChartType.TYRE_PRESSURE }
        ) {
            CondensedTyrePressureChart(dataPoints = dataPoints, modifier = Modifier.fillMaxSize())
        }

        ClickableChartCard(
            title = "Instantaneous Consumption",
            subtitle = "${if (useImperial) "kWh/100mi" else "kWh/100km"} — raw + rolling average",
            onClick = { expandedChart = ChartType.INSTANT_CONSUMPTION }
        ) {
            CondensedInstantConsumptionChart(
                dataPoints = dataPoints,
                useImperial = useImperial,
                modifier = Modifier.fillMaxSize()
            )
        }

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

    expandedChart?.let { chartType ->
        FullscreenChartDialog(
            chartType = chartType,
            dataPoints = dataPoints,
            useImperial = useImperial,
            onDismiss = { expandedChart = null }
        )
    }
}

@Composable
internal fun ClickableChartCard(
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
internal fun FullscreenChartDialog(
    chartType: ChartType,
    dataPoints: List<TripDataPointEntity>,
    useImperial: Boolean = false,
    onDismiss: () -> Unit
) {
    val chartData = remember(chartType, dataPoints) {
        when (chartType) {
            ChartType.MOTOR_RPM -> condenseForRpm(dataPoints, maxPoints = 480)
            ChartType.SPEED     -> condenseForSpeed(dataPoints, maxPoints = 144)
            ChartType.POWER     -> condenseForPower(dataPoints, maxPoints = 144)
            ChartType.ENERGY    -> dataPoints
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
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = when (chartType) {
                                    ChartType.ENERGY             -> "Energy Consumption (Detailed)"
                                    ChartType.SOC                -> "State of Charge (Detailed)"
                                    ChartType.SPEED              -> "Speed Profile (Detailed)"
                                    ChartType.MOTOR_RPM          -> "Motor RPM (Detailed)"
                                    ChartType.ALTITUDE           -> "Elevation Profile (Detailed)"
                                    ChartType.POWER              -> "Power Profile (Detailed)"
                                    ChartType.BATTERY_VOLTAGE    -> "Battery Voltage (Detailed)"
                                    ChartType.TYRE_PRESSURE      -> "Tyre Pressures (Detailed)"
                                    ChartType.INSTANT_CONSUMPTION -> "Instantaneous Consumption (Detailed)"
                                    ChartType.MODE_TIMELINE      -> "Drive / Regen Modes (Detailed)"
                                },
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when (chartType) {
                                    ChartType.ENERGY             -> "kWh over time"
                                    ChartType.SOC                -> "SoC% over time"
                                    ChartType.SPEED              -> "${if (useImperial) "mph" else "km/h"} over time"
                                    ChartType.MOTOR_RPM          -> "RPM over time"
                                    ChartType.ALTITUDE           -> "Altitude over time"
                                    ChartType.POWER              -> "kW over time"
                                    ChartType.BATTERY_VOLTAGE    -> "HV bus + cell min/max (V)"
                                    ChartType.TYRE_PRESSURE      -> "All four wheels (bar)"
                                    ChartType.INSTANT_CONSUMPTION -> "${if (useImperial) "kWh/100mi" else "kWh/100km"} over distance"
                                    ChartType.MODE_TIMELINE      -> "Mode timeline over the trip"
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

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    when (chartType) {
                        ChartType.ENERGY -> EnergyConsumptionChart(
                            dataPoints = chartData, modifier = Modifier.fillMaxSize(), maxPoints = 144
                        )
                        ChartType.SOC -> SocChart(dataPoints = chartData, modifier = Modifier.fillMaxSize())
                        ChartType.SPEED -> SpeedChart(
                            dataPoints = chartData, useImperial = useImperial, modifier = Modifier.fillMaxSize()
                        )
                        ChartType.MOTOR_RPM -> MotorRpmChart(dataPoints = chartData, modifier = Modifier.fillMaxSize())
                        ChartType.ALTITUDE  -> AltitudeChart(dataPoints = chartData, modifier = Modifier.fillMaxSize())
                        ChartType.POWER     -> PowerChart(dataPoints = chartData, modifier = Modifier.fillMaxSize())
                        ChartType.BATTERY_VOLTAGE -> BatteryVoltageChart(dataPoints = chartData, modifier = Modifier.fillMaxSize())
                        ChartType.TYRE_PRESSURE   -> TyrePressureChart(dataPoints = chartData, modifier = Modifier.fillMaxSize())
                        ChartType.INSTANT_CONSUMPTION -> InstantConsumptionChart(
                            dataPoints = dataPoints, useImperial = useImperial, modifier = Modifier.fillMaxSize()
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
internal fun ModeRecordingHint(missingDriveMode: Boolean, missingRegenMode: Boolean) {
    val missing = buildList {
        if (missingDriveMode) add("drive")
        if (missingRegenMode) add("regen")
    }.joinToString(" and ")
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(8.dp),
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
