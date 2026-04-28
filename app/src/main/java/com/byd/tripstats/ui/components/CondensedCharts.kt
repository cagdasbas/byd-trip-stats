package com.byd.tripstats.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.byd.tripstats.data.local.entity.TripDataPointEntity

/**
 * Condensed versions of charts for overview display
 * These charts:
 * - Sample data to 36 points max
 * - Disable scrolling/zooming
 * - Fit entire trip in viewport
 */

@Composable
fun CondensedEnergyChart(
    dataPoints: List<TripDataPointEntity>,
    modifier: Modifier = Modifier
) {
    val condensed = remember(dataPoints) { condenseData(dataPoints) }
    EnergyConsumptionChart(dataPoints = condensed, modifier = modifier)
}

@Composable
fun CondensedSpeedChart(
    dataPoints: List<TripDataPointEntity>,
    modifier: Modifier = Modifier
) {
    val condensed = remember(dataPoints) { condenseForSpeed(dataPoints) }
    SpeedChart(dataPoints = condensed, modifier = modifier)
}

@Composable
fun CondensedMotorRpmChart(
    dataPoints: List<TripDataPointEntity>,
    modifier: Modifier = Modifier
) {
    // RPM is an event-driven field: between feature events the DB-written value
    // can be 0 for individual rows while the car is actually spinning the
    // motors. Index-sampling (as in condenseData) can land on those zero rows.
    // Also, on AWD cars the front and rear peaks often occur on different rows,
    // so picking one "winning" row per bucket can still erase the other axle.
    // Collapse each bucket into a representative row that keeps each axle's own
    // peak RPM, so short front-motor bursts survive the condensed view.
    val condensed = remember(dataPoints) { condenseForRpm(dataPoints) }
    MotorRpmChart(dataPoints = condensed, modifier = modifier)
}

fun condenseForRpm(
    dataPoints: List<TripDataPointEntity>,
    maxPoints: Int = 36
): List<TripDataPointEntity> {
    if (dataPoints.size <= maxPoints) return dataPoints
    val step = (dataPoints.size + maxPoints - 1) / maxPoints
    return dataPoints.chunked(step).map { bucket ->
        val anchor = bucket[bucket.size / 2]
        val frontPeak = bucket.maxOf { kotlin.math.abs(it.engineSpeedFront) }
        val rearPeak = bucket.maxOf { kotlin.math.abs(it.engineSpeedRear) }
        anchor.copy(
            engineSpeedFront = frontPeak,
            engineSpeedRear = rearPeak
        )
    }.take(maxPoints)
}

fun condenseForSpeed(
    dataPoints: List<TripDataPointEntity>,
    maxPoints: Int = 36
): List<TripDataPointEntity> {
    if (dataPoints.size <= maxPoints) return dataPoints
    val step = (dataPoints.size + maxPoints - 1) / maxPoints
    return dataPoints.chunked(step).map { bucket ->
        val anchor = bucket[bucket.size / 2]
        anchor.copy(speed = bucket.maxOf { it.speed })
    }.take(maxPoints)
}

fun condenseForPower(
    dataPoints: List<TripDataPointEntity>,
    maxPoints: Int = 36
): List<TripDataPointEntity> {
    if (dataPoints.size <= maxPoints) return dataPoints
    val step = (dataPoints.size + maxPoints - 1) / maxPoints
    return dataPoints.chunked(step).map { bucket ->
        val anchor = bucket[bucket.size / 2]
        // Preserve the most extreme power value (peak acceleration or peak regen)
        // so brief full-throttle bursts survive bucketing.
        val peakPower = bucket.maxByOrNull { kotlin.math.abs(it.power) }?.power ?: anchor.power
        anchor.copy(power = peakPower)
    }.take(maxPoints)
}

@Composable
fun CondensedAltitudeChart(
    dataPoints: List<TripDataPointEntity>,
    modifier: Modifier = Modifier
) {
    val condensed = remember(dataPoints) { condenseData(dataPoints) }
    AltitudeChart(dataPoints = condensed, modifier = modifier)
}

@Composable
fun CondensedSocChart(
    dataPoints: List<TripDataPointEntity>,
    modifier: Modifier = Modifier
) {
    val condensed = remember(dataPoints) { condenseData(dataPoints) }
    SocChart(dataPoints = condensed, modifier = modifier)
}

@Composable
fun CondensedPowerChart(
    dataPoints: List<TripDataPointEntity>,
    modifier: Modifier = Modifier
) {
    val condensed = remember(dataPoints) { condenseForPower(dataPoints) }
    PowerChart(dataPoints = condensed, modifier = modifier)
}

/**
 * Condense data points to a maximum number of points
 * @param dataPoints The original data points
 * @param maxPoints Maximum number of points to return (default: 36)
 * @return Condensed list fitting within maxPoints
 */
fun condenseData(
    dataPoints: List<TripDataPointEntity>,
    maxPoints: Int = 36  // Default
): List<TripDataPointEntity> {
    if (dataPoints.size <= maxPoints) return dataPoints
    
    // Use ceiling division to ensure we don't exceed maxPoints
    val step = (dataPoints.size + maxPoints - 1) / maxPoints  // Ceiling division
    return dataPoints.filterIndexed { index, _ -> index % step == 0 }.take(maxPoints)
}
@Composable
fun CondensedBatteryVoltageChart(
    dataPoints: List<TripDataPointEntity>,
    modifier: Modifier = Modifier
) {
    val condensed = remember(dataPoints) { condenseData(dataPoints) }
    BatteryVoltageChart(dataPoints = condensed, modifier = modifier)
}

@Composable
fun CondensedTyrePressureChart(
    dataPoints: List<TripDataPointEntity>,
    modifier: Modifier = Modifier
) {
    val condensed = remember(dataPoints) { condenseData(dataPoints) }
    TyrePressureChart(dataPoints = condensed, modifier = modifier)
}

@Composable
fun CondensedInstantConsumptionChart(
    dataPoints: List<TripDataPointEntity>,
    modifier: Modifier = Modifier
) {
    // No condensing needed — InstantConsumptionChart already filters to driving points
    InstantConsumptionChart(dataPoints = dataPoints, modifier = modifier)
}
