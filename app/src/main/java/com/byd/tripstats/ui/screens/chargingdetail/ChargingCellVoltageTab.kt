package com.byd.tripstats.ui.screens.chargingdetail

import androidx.compose.runtime.Composable
import com.byd.tripstats.data.preferences.SocSource
import com.byd.tripstats.ui.theme.*

/**
 * Per-cell high/low voltage across a charge, with the high−low spread (cell imbalance) on the
 * crosshair. Cell min/max can be absent early on some firmwares (only the aggregate is reported at
 * first) — [ChargingSeriesChart]'s per-series gap-skip handles that. X-axis toggles time / SoC.
 */
@Composable
internal fun ChargingCellVoltageTab(
    dataPoints  : List<ChargingChartPoint>,
    isSynthetic : Boolean = false,
    socSource   : SocSource = SocSource.PANEL,
    xAxisMode   : ChargingXAxisMode,
    onXAxisModeChange: (ChargingXAxisMode) -> Unit,
) {
    ChargingSeriesChart(
        dataPoints  = dataPoints,
        isSynthetic = isSynthetic,
        title       = "Cell Voltage",
        yAxisLabel  = "V",
        socSource   = socSource,
        xAxisMode   = xAxisMode,
        onXAxisModeChange = onXAxisModeChange,
        series = listOf(
            ChargingSeriesSpec("Cell low", BydElectricAzure, 2.5f) { it.batteryCellVoltageMinV },
            ChargingSeriesSpec("Cell high", AccelerationOrange, 2.5f) { it.batteryCellVoltageMaxV },
        ),
        crosshairFor = { p ->
            val hi = p.batteryCellVoltageMaxV
            val lo = p.batteryCellVoltageMinV
            if (hi > 0.0 && lo > 0.0) {
                ChargingCrosshairInfo(
                    anchor = hi,
                    lines = listOf(
                        "High %.3f V".format(hi),
                        "Low %.3f V".format(lo),
                        "Δ %.0f mV".format((hi - lo) * 1000),
                    ),
                    color = AccelerationOrange,
                )
            } else null
        },
    )
}
