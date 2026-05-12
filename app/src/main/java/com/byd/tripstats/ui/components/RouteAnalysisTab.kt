package com.byd.tripstats.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.tripstats.data.local.entity.TripEntity
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import com.byd.tripstats.data.preferences.UnitSystem
import com.byd.tripstats.data.preferences.convertDistance
import com.byd.tripstats.data.preferences.convertEfficiency
import com.byd.tripstats.data.preferences.distanceUnit
import com.byd.tripstats.data.preferences.consumptionUnit
import com.byd.tripstats.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * Route Analysis tab — detailed trip statistics and timeline.
 * Shows waypoints, segments, energy hotspots, and timeline.
 */
@Composable
fun RouteAnalysisTab(
    trip: TripEntity? = null,
    dataPoints: List<TripDataPointEntity>,
    useImperial: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (dataPoints.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No route data available",
                style = MaterialTheme.typography.titleMedium
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ModeInsightsCard(
            dataPoints = dataPoints,
            trip = trip,
            useImperial = useImperial
        )
        WaypointsCard(dataPoints)
        RouteSegmentsCard(dataPoints, useImperial)
        EnergyHeatmapCard(dataPoints)
        TripTimelineCard(dataPoints)
    }
}

// ── Shared card border modifier ───────────────────────────────────────────────

private val cardBorder: Modifier
    @Composable get() = Modifier.border(
        width = 1.dp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.medium
    )

private val cardColors: CardColors
    @Composable get() = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

// ── Helpers ───────────────────────────────────────────────────────────────────

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
private fun fmt(ts: Long) = timeFormat.format(Date(ts))
private const val MODE_SUMMARY_MIN_DURATION_MINUTES = 0.2

internal data class ModeSummary(
    val label: String,
    val color: Color,
    val distanceKm: Double,
    val distanceSharePct: Double,
    val durationMinutes: Double,
    val consumptionKwhPer100Km: Double?,
    val regenSharePct: Double?
)

@Composable
private fun ModeInsightsCard(
    dataPoints: List<TripDataPointEntity>,
    trip: TripEntity?,
    useImperial: Boolean = false
) {
    if (!hasTripModeData(dataPoints)) return

    val driveSummaries = remember(dataPoints, trip) {
        buildDriveModeSummaries(
            dataPoints = dataPoints,
            trip = trip
        )
    }
    val regenSummaries = remember(dataPoints, trip) {
        buildRegenModeSummaries(
            dataPoints = dataPoints,
            trip = trip
        )
    }
    val insights: List<String> = remember(driveSummaries, regenSummaries, useImperial) {
        buildList {
            compareDriveModes(driveSummaries, useImperial)?.let(::add)
            compareRegenModes(regenSummaries)?.let(::add)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().then(cardBorder),
        colors = cardColors
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Mode Insights",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "What your drive and regen modes actually did on this trip",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── Horizontal stacked-bar summary ───────────────────────────────
            ModeStackedBar(
                label = "Drive",
                summaries = driveSummaries
            )
            if (regenSummaries.isNotEmpty()) {
                ModeStackedBar(
                    label = "Regen",
                    summaries = regenSummaries
                )
            }

            if (insights.isNotEmpty()) {
                insights.forEach { insight: String ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = insight,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (driveSummaries.isNotEmpty()) {
                Text("Drive mode usage", fontWeight = FontWeight.SemiBold)
                driveSummaries.forEach { summary: ModeSummary ->
                    ModeSummaryRow(summary, useImperial)
                }
            }

            if (regenSummaries.isNotEmpty()) {
                Text("Regen mode usage", fontWeight = FontWeight.SemiBold)
                regenSummaries.forEach { summary: ModeSummary ->
                    ModeSummaryRow(summary, useImperial)
                }
            }
        }
    }
}

@Composable
private fun ModeStackedBar(
    label: String,
    summaries: List<ModeSummary>
) {
    if (summaries.isEmpty()) return
    val total = summaries.sumOf { it.distanceKm }.takeIf { it > 0.0 } ?: return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Stacked bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(999.dp))
        ) {
            summaries.forEach { s ->
                val frac = (s.distanceKm / total).toFloat().coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .weight(frac.coerceAtLeast(0.001f))
                        .fillMaxHeight()
                        .background(s.color)
                )
            }
        }
        // Legend row beneath the bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            summaries.forEach { s ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(s.color)
                    )
                    Text(
                        text = "${s.label} ${String.format("%.0f", s.distanceSharePct)}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeSummaryRow(summary: ModeSummary, useImperial: Boolean = false) {
    val unitSystem = if (useImperial) UnitSystem.IMPERIAL else UnitSystem.METRIC
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.05f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(summary.color, RoundedCornerShape(999.dp))
            )
            Column {
                Text(summary.label, fontWeight = FontWeight.Bold)
                Text(
                    text = "${String.format("%.1f", unitSystem.convertDistance(summary.distanceKm))} ${unitSystem.distanceUnit} • ${String.format("%.0f", summary.distanceSharePct)}% of mode-attributed trip",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = summary.consumptionKwhPer100Km?.let { "${String.format("%.1f", unitSystem.convertEfficiency(it))} ${unitSystem.consumptionUnit}" } ?: "—",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = summary.regenSharePct?.let { "${String.format("%.1f", it)}% recovered via regen" } ?: "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun buildDriveModeSummaries(
    dataPoints: List<TripDataPointEntity>,
    trip: TripEntity?
): List<ModeSummary> =
    buildModeSummaries(
        dataPoints = dataPoints,
        trip = trip,
        keySelector = { it.extractTripModes().driveMode },
        labelSelector = ::driveModeLabel,
        colorSelector = ::driveModeColor
    )

internal fun buildRegenModeSummaries(
    dataPoints: List<TripDataPointEntity>,
    trip: TripEntity?
): List<ModeSummary> =
    buildModeSummaries(
        dataPoints = dataPoints,
        trip = trip,
        keySelector = { it.extractTripModes().regenMode },
        labelSelector = ::regenModeLabel,
        colorSelector = ::regenModeColor
    )

private fun buildModeSummaries(
    dataPoints: List<TripDataPointEntity>,
    trip: TripEntity?,
    keySelector: (TripDataPointEntity) -> Int,
    labelSelector: (Int) -> String,
    colorSelector: (Int) -> Color
): List<ModeSummary> {
    data class Bucket(
        var distanceKm: Double = 0.0,
        var durationMinutes: Double = 0.0,
        var netEnergyKwh: Double = 0.0,
        var tractionKwh: Double = 0.0,
        var regenKwh: Double = 0.0
    )

    // True trip totals from the stored trip row when available. These are the
    // same resolved values shown in the overview tab, so normalising back to
    // them keeps mode insights numerically consistent with the trip summary.
    val tripDistanceKm = trip?.distance?.takeIf { it.isFinite() && it >= 0.0 }
        ?: if (dataPoints.size >= 2) {
            (dataPoints.last().odometer - dataPoints.first().odometer).coerceAtLeast(0.0)
        } else 0.0
    val tripEnergyKwh = trip?.energyConsumed?.takeIf { it.isFinite() && it >= 0.0 }

    val buckets = mutableMapOf<Int, Bucket>()
    dataPoints.zipWithNext { a, b ->
        val mode = keySelector(a)
        if (mode == 0) return@zipWithNext
        val bucket = buckets.getOrPut(mode) { Bucket() }
        // Cap dt to 10 s — data points are written only when something changes
        // (write-throttle), so gaps can be several seconds. Without a cap, a
        // long gap spanning a stop inflates speed×dt distance for that interval.
        val dtMs = (b.timestamp - a.timestamp).coerceIn(0L, 10_000L)
        val dtHours = dtMs / 3_600_000.0
        val dtMinutes = dtHours * 60.0
        val powerKw = a.power
        // Derive distance from speed × time rather than odometer deltas.
        // Odometer increments coarsely (often stays 0 between 1-second samples).
        val avgSpeedKmh = (a.speed + b.speed) / 2.0
        val distance = avgSpeedKmh * dtHours
        bucket.distanceKm += distance
        bucket.durationMinutes += dtMinutes
        if (powerKw > 0.0) bucket.tractionKwh += powerKw * dtHours
        if (powerKw < 0.0) bucket.regenKwh += abs(powerKw) * dtHours
    }
    // Derive net energy from traction − regen (power-integrated). totalDischarge deltas
    // are unreliable — car frequently reports 0 for long stretches,
    // producing 0.0 kWh/100km for modes that have real consumption. Power × dt is sampled
    // every telemetry tick and reflects real-time battery draw.
    buckets.values.forEach { b ->
        b.netEnergyKwh = (b.tractionKwh - b.regenKwh).coerceAtLeast(0.0)
    }

    // Include all modes that had at least 12 seconds of active time.
    val visibleBuckets = buckets.filterValues { it.durationMinutes >= MODE_SUMMARY_MIN_DURATION_MINUTES }

    // Total raw distance attributed across all modes (before scaling).
    val rawTotalDistance = visibleBuckets.values.sumOf { it.distanceKm }.takeIf { it > 0.0 } ?: 1.0

    // Scale factor: normalise every bucket back to the true trip distance so the
    // displayed mode distances sum to the same total shown in the overview tab.
    // If the trip distance is unavailable, leave distances unscaled.
    val scale = if (tripDistanceKm > 0.0 && rawTotalDistance > 0.0) {
        tripDistanceKm / rawTotalDistance
    } else {
        1.0
    }

    val rawTotalEnergy = visibleBuckets.values.sumOf { it.netEnergyKwh }
    val energyScale = if (tripEnergyKwh != null && tripEnergyKwh > 0.0 && rawTotalEnergy > 0.0) {
        tripEnergyKwh / rawTotalEnergy
    } else {
        1.0
    }

    return visibleBuckets
        .mapNotNull { (mode, bucket) ->
            val scaledDistance = bucket.distanceKm * scale
            val scaledEnergy = bucket.netEnergyKwh * energyScale
            // Only show consumption when the mode covered meaningful distance.
            val consumption = if (scaledDistance >= 0.1) {
                (scaledEnergy / scaledDistance) * 100.0
            } else null
            val regenShare = if (bucket.tractionKwh + bucket.regenKwh > 0.0) {
                (bucket.regenKwh / (bucket.tractionKwh + bucket.regenKwh)) * 100.0
            } else null
            ModeSummary(
                label = labelSelector(mode),
                color = colorSelector(mode),
                distanceKm = scaledDistance,
                distanceSharePct = (bucket.distanceKm / rawTotalDistance) * 100.0,
                durationMinutes = bucket.durationMinutes,
                consumptionKwhPer100Km = consumption,
                regenSharePct = regenShare
            )
        }
        .sortedByDescending { it.distanceKm }
}

private fun compareDriveModes(summaries: List<ModeSummary>, useImperial: Boolean = false): String? {
    val eco = summaries.firstOrNull { it.label == "Eco" && it.distanceKm >= 1.0 }
    val normal = summaries.firstOrNull { it.label == "Normal" && it.distanceKm >= 1.0 }
    val sport = summaries.firstOrNull { it.label == "Sport" && it.distanceKm >= 1.0 }

    if (eco != null && normal != null &&
        eco.consumptionKwhPer100Km != null && normal.consumptionKwhPer100Km != null
    ) {
        val diffPct = ((eco.consumptionKwhPer100Km - normal.consumptionKwhPer100Km) / normal.consumptionKwhPer100Km) * 100.0
        if (abs(diffPct) < 5.0) {
            return "Eco and Normal were effectively the same on this trip, so route and traffic mattered more than the selected drive mode."
        }
    }

    if (sport != null && normal != null &&
        sport.consumptionKwhPer100Km != null && normal.consumptionKwhPer100Km != null
    ) {
        val delta = sport.consumptionKwhPer100Km - normal.consumptionKwhPer100Km
        if (delta > 1.0) {
            val deltaDisplay = if (useImperial) delta / 0.621371 else delta
            val unit = if (useImperial) "kWh/100mi" else "kWh/100km"
            return "Sport used ${String.format("%.1f", deltaDisplay)} $unit more than Normal over comparable segments in this trip."
        }
    }
    return null
}

private fun compareRegenModes(summaries: List<ModeSummary>): String? {
    val standard = summaries.firstOrNull { it.label == "Standard" && it.distanceKm >= 1.0 }
    val high = summaries.firstOrNull { it.label == "High" && it.distanceKm >= 1.0 }

    if (high != null && standard != null &&
        high.regenSharePct != null && standard.regenSharePct != null
    ) {
        val delta = high.regenSharePct - standard.regenSharePct
        if (delta < 2.0) {
            return "High regen did not materially improve recovered energy on this trip, so braking opportunities likely mattered more than the regen setting."
        }
    }
    return null
}

// ── Waypoints ─────────────────────────────────────────────────────────────────

@Composable
private fun WaypointsCard(dataPoints: List<TripDataPointEntity>) {
    val startPoint = dataPoints.first()
    val endPoint   = dataPoints.last()

    Card(
        modifier = Modifier.fillMaxWidth().then(cardBorder),
        colors = cardColors
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Waypoints",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            WaypointItem(
                icon    = Icons.Filled.FlagCircle,
                label   = "Start",
                time    = fmt(startPoint.timestamp),
                soc     = "${startPoint.soc.toInt()}%",
                color   = RegenGreen
            )
            Spacer(modifier = Modifier.height(8.dp))
            WaypointItem(
                icon    = Icons.Filled.LocationOn,
                label   = "End",
                time    = fmt(endPoint.timestamp),
                soc     = "${endPoint.soc.toInt()}%",
                color   = BydErrorRed
            )
        }
    }
}

@Composable
private fun WaypointItem(
    icon: ImageVector,
    label: String,
    time: String,
    soc: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(32.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "SOC: $soc", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ── Route Segments ────────────────────────────────────────────────────────────

@Composable
private fun RouteSegmentsCard(dataPoints: List<TripDataPointEntity>, useImperial: Boolean = false) {
    val segmentSize = (dataPoints.size / 5).coerceAtLeast(1)
    val segments    = dataPoints.chunked(segmentSize).take(5)
    val speedFactor = if (useImperial) 0.621371 else 1.0
    val speedUnit   = if (useImperial) "mph" else "km/h"

    Card(
        modifier = Modifier.fillMaxWidth().then(cardBorder),
        colors = cardColors
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Route Segments",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            segments.forEachIndexed { index, segment ->
                val avgSpeed  = segment.map { it.speed }.average()
                val avgPower  = segment.map { it.power }.average()
                val socChange = segment.first().soc - segment.last().soc
                val startTime = fmt(segment.first().timestamp)
                val endTime   = fmt(segment.last().timestamp)

                SegmentItem(
                    segmentNumber = index + 1,
                    timeRange     = "$startTime – $endTime",
                    avgSpeed      = (avgSpeed * speedFactor).toInt(),
                    speedUnit     = speedUnit,
                    avgPower      = avgPower.toInt(),
                    socChange     = socChange
                )

                if (index < segments.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SegmentItem(
    segmentNumber: Int,
    timeRange: String,
    avgSpeed: Int,
    speedUnit: String = "km/h",
    avgPower: Int,
    socChange: Double
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.05f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Segment $segmentNumber",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = timeRange,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = "$avgSpeed $speedUnit", style = MaterialTheme.typography.bodySmall)
            Text(
                text = "${abs(avgPower)} kW",
                style = MaterialTheme.typography.bodySmall,
                color = if (avgPower < 0) RegenGreen else AccelerationOrange
            )
            Text(
                text = "${String.format("%.1f", abs(socChange))}% SoC",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ── Energy Hotspots ───────────────────────────────────────────────────────────

@Composable
private fun EnergyHeatmapCard(dataPoints: List<TripDataPointEntity>) {
    // Chunk into groups of 10 points and calculate energy using actual time deltas.
    // E (kWh) = Σ power_kW × Δt_seconds / 3600
    data class EnergySegment(val startTs: Long, val endTs: Long, val energyKwh: Double)

    val segments = dataPoints.chunked(10).map { chunk ->
        var energy = 0.0
        for (i in 1 until chunk.size) {
            val dtSeconds = (chunk[i].timestamp - chunk[i - 1].timestamp) / 1000.0
            energy += abs(chunk[i].power) * dtSeconds / 3600.0
        }
        EnergySegment(
            startTs   = chunk.first().timestamp,
            endTs     = chunk.last().timestamp,
            energyKwh = energy
        )
    }.sortedByDescending { it.energyKwh }.take(5)

    Card(
        modifier = Modifier.fillMaxWidth().then(cardBorder),
        colors = cardColors
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Energy Hotspots",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Segments with highest energy usage",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            segments.forEachIndexed { index, seg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${fmt(seg.startTs)} – ${fmt(seg.endTs)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${String.format("%.3f", seg.energyKwh)} kWh",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccelerationOrange
                    )
                }
                if (index < segments.size - 1) {
                    HorizontalDivider(
                        modifier  = Modifier.padding(vertical = 2.dp),
                        thickness = 0.5.dp,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

// ── Trip Timeline ─────────────────────────────────────────────────────────────

@Composable
private fun TripTimelineCard(dataPoints: List<TripDataPointEntity>) {
    val events = mutableListOf<TimelineEvent>()

    events.add(TimelineEvent(fmt(dataPoints.first().timestamp), "Trip Started",  Icons.Filled.FlagCircle,  RegenGreen))

    // Debounce acceleration/braking events: use a 5-point rolling average of power
    // to suppress noise, and enforce a minimum 10-second gap between events.
    val window = 5
    var lastEventTs = dataPoints.first().timestamp

    for (i in window until dataPoints.size) {
        val curr = dataPoints[i]
        val gapSeconds = (curr.timestamp - lastEventTs) / 1000.0
        if (gapSeconds < 10.0) continue // too soon after last event

        val avgBefore = dataPoints.subList(i - window, i).map { it.power }.average()
        val avgAfter  = dataPoints.subList(i, (i + window).coerceAtMost(dataPoints.size)).map { it.power }.average()
        val delta     = avgAfter - avgBefore

        if (abs(delta) > 30) { // significant smoothed power change
            events.add(TimelineEvent(
                time  = fmt(curr.timestamp),
                title = if (delta > 0) "Hard Acceleration" else "Hard Braking",
                icon  = if (delta > 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                color = if (delta > 0) AccelerationOrange else RegenGreen
            ))
            lastEventTs = curr.timestamp
        }
    }

    events.add(TimelineEvent(fmt(dataPoints.last().timestamp), "Trip Ended", Icons.Filled.LocationOn, BydErrorRed))
    val visibleEvents = sampleTimelineEvents(events, maxVisible = 15)

    Card(
        modifier = Modifier.fillMaxWidth().then(cardBorder),
        colors = cardColors
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Trip Timeline",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            visibleEvents.forEachIndexed { index, event ->
                TimelineEventItem(event)
                if (index < (visibleEvents.size - 1)) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun TimelineEventItem(event: TimelineEvent) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = event.icon, contentDescription = event.title, tint = event.color, modifier = Modifier.size(24.dp))
        Column {
            Text(text = event.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(text = event.time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class TimelineEvent(
    val time:  String,
    val title: String,
    val icon:  ImageVector,
    val color: Color
)

private fun sampleTimelineEvents(events: List<TimelineEvent>, maxVisible: Int): List<TimelineEvent> {
    if (events.size <= maxVisible) return events
    val lastIndex = events.lastIndex
    val sampledIndices = (0 until maxVisible).map { slot ->
        ((slot.toDouble() / (maxVisible - 1)) * lastIndex).toInt()
    }.distinct().sorted()
    val sampled = sampledIndices.map { events[it] }.toMutableList()
    if (sampled.firstOrNull() != events.first()) sampled.add(0, events.first())
    if (sampled.lastOrNull() != events.last()) sampled.add(events.last())
    return sampled.distinct()
}
