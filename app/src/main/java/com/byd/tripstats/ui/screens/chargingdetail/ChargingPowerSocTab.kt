package com.byd.tripstats.ui.screens.chargingdetail

import androidx.compose.runtime.Composable
import com.byd.tripstats.data.preferences.SocSource
import com.byd.tripstats.ui.theme.AccelerationOrange

/**
 * Charge power over a session. The SoC overlay (right axis, time mode) and the time/SoC x-axis are
 * supplied by the shared [ChargingSeriesChart].
 */
@Composable
internal fun ChargingPowerSocTab(
    dataPoints : List<ChargingChartPoint>,
    isSynthetic: Boolean = false,
    socSource  : SocSource = SocSource.PANEL,
    xAxisMode  : ChargingXAxisMode,
    onXAxisModeChange: (ChargingXAxisMode) -> Unit,
) {
    ChargingSeriesChart(
        dataPoints  = dataPoints,
        isSynthetic = isSynthetic,
        title       = "Charge Power",
        yAxisLabel  = "Power (kW)",
        socSource   = socSource,
        xAxisMode   = xAxisMode,
        onXAxisModeChange = onXAxisModeChange,
        series = listOf(
            ChargingSeriesSpec("Power (kW)", AccelerationOrange, 3f) { it.chargingPowerKw },
        ),
        crosshairFor = { p ->
            ChargingCrosshairInfo(
                anchor = p.chargingPowerKw,
                lines = listOf("Power: %.1f kW".format(p.chargingPowerKw)),
                color = AccelerationOrange,
            )
        },
    )
}
