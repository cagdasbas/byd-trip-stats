package com.byd.tripstats.ui.screens.chargingdetail

import androidx.compose.runtime.Composable
import com.byd.tripstats.data.preferences.SocSource
import com.byd.tripstats.ui.theme.*

/**
 * Battery temperature over a charge: average plus the per-cell min/max band. The min/max can be
 * absent early (the BMS reports only the aggregate at first) — handled by [ChargingSeriesChart]'s
 * per-series gap-skip. X-axis toggles between time and SoC.
 */
@Composable
internal fun ChargingTempTab(
    dataPoints  : List<ChargingChartPoint>,
    isSynthetic : Boolean = false,
    socSource   : SocSource = SocSource.PANEL,
    xAxisMode   : ChargingXAxisMode,
    onXAxisModeChange: (ChargingXAxisMode) -> Unit,
) {
    ChargingSeriesChart(
        dataPoints  = dataPoints,
        isSynthetic = isSynthetic,
        title       = "Battery Temperature",
        yAxisLabel  = "°C",
        socSource   = socSource,
        xAxisMode   = xAxisMode,
        onXAxisModeChange = onXAxisModeChange,
        series = listOf(
            ChargingSeriesSpec("Avg", BydErrorRed, 3f) { it.batteryTempAvgC },
            ChargingSeriesSpec("Cell min", BydElectricAzure, 2f) { it.batteryCellTempMinC },
            ChargingSeriesSpec("Cell max", AccelerationOrange, 2f) { it.batteryCellTempMaxC },
        ),
        crosshairFor = { p ->
            p.batteryTempAvgC.takeIf { it > 0.0 }?.let {
                ChargingCrosshairInfo(it, listOf("%.1f °C".format(it)), BydErrorRed)
            }
        },
    )
}
