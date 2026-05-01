package com.byd.tripstats.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import com.byd.tripstats.data.local.entity.TripEntity
import com.byd.tripstats.ui.theme.*
import com.byd.tripstats.ui.viewmodel.DashboardViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

// ── Per-trip color palette ────────────────────────────────────────────────────

private val TRIP_COLORS = listOf(BydElectricAzure, AccelerationOrange, RegenGreen)

private fun tripColor(index: Int): Color = TRIP_COLORS[index % TRIP_COLORS.size]

private fun tripColorArgb(index: Int): Int = when (index % 3) {
    0    -> 0xFF2979FF.toInt()
    1    -> 0xFFFF6D00.toInt()
    else -> 0xFF00C853.toInt()
}

// ── Entry point ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripCompareSheet(
    trips    : List<TripEntity>,
    viewModel: DashboardViewModel,
    onDismiss: () -> Unit
) {
    val sheetState     = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val compareData    by viewModel.compareDataPoints.collectAsState()
    val displayMetrics by viewModel.tripDisplayMetrics.collectAsState()
    val compareStats    = remember(trips) { viewModel.getCompareStats(trips.map { it.id }) }

    var selectedTab  by remember { mutableIntStateOf(0) }
    val tabs = listOf("Summary", "Charts", "Routes")

    // Eye toggle — indices of currently visible trips (all shown by default)
    var visibleTrips by remember { mutableStateOf(trips.indices.toSet()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = MaterialTheme.colorScheme.surface,
        modifier         = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.CompareArrows, null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Comparing ${trips.size} trips",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // ── Trip legend with eye toggles ──────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                trips.forEachIndexed { i, trip ->
                    val visible = i in visibleTrips
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            visibleTrips = if (visible)
                                visibleTrips - i
                            else
                                visibleTrips + i
                        }
                    ) {
                        Canvas(modifier = Modifier.size(20.dp, 3.dp)) {
                            drawLine(
                                color       = if (visible) tripColor(i)
                                else tripColor(i).copy(alpha = 0.25f),
                                start       = Offset(0f, size.height / 2),
                                end         = Offset(size.width, size.height / 2),
                                strokeWidth = 3f
                            )
                        }
                        Spacer(Modifier.width(5.dp))
                        Text(
                            tripShortLabel(trip),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (visible) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector        = if (visible) Icons.Filled.Visibility
                            else         Icons.Filled.VisibilityOff,
                            contentDescription = if (visible) "Hide trip" else "Show trip",
                            tint     = if (visible) tripColor(i)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // ── Tabs ──────────────────────────────────────────────────────────
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick  = { selectedTab = i },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == i) FontWeight.Bold
                                else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // ── Tab content ───────────────────────────────────────────────────
            // weight(1f)   — allocates exactly the remaining height after header/tabs
            // clipToBounds — hard clips any child that escapes during sheet drag
            //                animation (sheet remeasures with unbounded constraints;
            //                weight alone is not sufficient during that pass).
            Box(modifier = Modifier.weight(1f).clipToBounds()) {
                when (selectedTab) {
                    0 -> CompareSummaryTab(trips, displayMetrics, compareStats, visibleTrips)
                    1 -> CompareChartsTab(trips, compareData, visibleTrips)
                    2 -> CompareRoutesTab(trips, compareStats, visibleTrips)
                }
            }
        }
    }
}

// ── Tab 1: Summary ────────────────────────────────────────────────────────────

@Composable
private fun CompareSummaryTab(
    trips         : List<TripEntity>,
    displayMetrics: Map<Long, DashboardViewModel.TripDisplayMetrics>,
    compareStats  : List<com.byd.tripstats.data.local.entity.TripStatsEntity>,
    visibleTrips  : Set<Int>
) {
    val statsById = compareStats.associateBy { it.tripId }

    fun bestIndices(rawValues: List<Double?>, lowerIsBetter: Boolean): List<Boolean> {
        val visibleVals = rawValues.mapIndexed { i, v -> if (i in visibleTrips) v else null }
        val valid = visibleVals.filterNotNull()
        if (valid.isEmpty()) return rawValues.map { false }
        val best = if (lowerIsBetter) valid.min() else valid.max()
        return rawValues.mapIndexed { i, v ->
            i in visibleTrips && v != null && v == best
        }
    }

    data class MetricRow(val label: String, val values: List<String>, val winners: List<Boolean>)

    val rows = listOf(
        MetricRow("Distance",
            trips.map { "%.1f km".format(it.distance ?: 0.0) },
            bestIndices(trips.map { it.distance }, false)),
        MetricRow("Duration",
            trips.map { formatDurationCompare(it.duration ?: 0L) },
            bestIndices(trips.map { it.duration?.toDouble() }, false)),
        MetricRow("Consumption",
            trips.map { "%.1f kWh/100".format(it.efficiency ?: 0.0) },
            bestIndices(trips.map { it.efficiency }, true)),
        MetricRow("Energy used",
            trips.map { "%.2f kWh".format(it.energyConsumed ?: 0.0) },
            bestIndices(trips.map { it.energyConsumed }, true)),
        MetricRow("Max speed",
            trips.map { "${it.maxSpeed.toInt()} km/h" },
            bestIndices(trips.map { it.maxSpeed }, false)),
        MetricRow("Avg speed",
            trips.map { displayMetrics[it.id]?.avgSpeedKmh?.let { v -> "$v km/h" } ?: "—" },
            bestIndices(trips.map { displayMetrics[it.id]?.avgSpeedKmh?.toDouble() }, false)),
        MetricRow("SoC start→end",
            trips.map { "${it.startSoc.toInt()}% → ${it.endSoc?.toInt() ?: "—"}%" },
            trips.map { false }),
        MetricRow("Regen recovered",
            trips.map { statsById[it.id]?.totalRegenEnergy?.let { v -> "%.2f kWh".format(v) } ?: "—" },
            bestIndices(trips.map { statsById[it.id]?.totalRegenEnergy }, false)),
        MetricRow("Regen efficiency",
            trips.map { displayMetrics[it.id]?.regenEfficiencyPct?.let { v -> "%.1f%%".format(v) } ?: "—" },
            bestIndices(trips.map { displayMetrics[it.id]?.regenEfficiencyPct }, false)),
        MetricRow("Trip score",
            trips.map { displayMetrics[it.id]?.tripScore?.toString() ?: "—" },
            bestIndices(trips.map { displayMetrics[it.id]?.tripScore?.toDouble() }, false))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Column headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Spacer(Modifier.weight(1.2f))
            trips.forEachIndexed { i, trip ->
                Text(
                    text       = tripShortLabel(trip),
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                    color      = if (i in visibleTrips) tripColor(i)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier   = Modifier.weight(1f)
                )
            }
        }

        rows.forEachIndexed { rowIdx, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (rowIdx % 2 == 0)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.surface
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    row.label,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1.2f)
                )
                row.values.forEachIndexed { i, v ->
                    val hidden   = i !in visibleTrips
                    val isWinner = row.winners.getOrElse(i) { false }
                    Box(
                        modifier         = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = if (hidden) "—" else v,
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                            color      = when {
                                hidden   -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                isWinner -> tripColor(i)
                                else     -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ── Tab 2: Overlaid Charts ────────────────────────────────────────────────────

private enum class CompareChartType(
    val label     : String,
    val yAxisLabel: String,
    val yFloor    : Double? = null
) {
    SPEED("Speed", "km/h", yFloor = 0.0),
    POWER("Power", "kW"),
    ENERGY_CONSUMPTION("Consumption", "kWh/100", yFloor = 0.0),
    SOC("SoC", "%", yFloor = 0.0),
    ELEVATION("Elevation", "m")
}

@Composable
private fun CompareChartsTab(
    trips       : List<TripEntity>,
    compareData : Map<Long, List<TripDataPointEntity>>,
    visibleTrips: Set<Int>
) {
    if (trips.any { it.id !in compareData }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Loading chart data…", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val normalised = trips.map { trip ->
        normaliseTripData(compareData[trip.id] ?: emptyList())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CompareChartType.entries.forEach { chartType ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    Text(chartType.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                    OverlaidLineChart(
                        series = normalised.mapIndexed { i, pts ->
                            Triple(pts, tripColor(i), i in visibleTrips)
                        },
                        yAxisLabel    = chartType.yAxisLabel,
                        yFloor        = chartType.yFloor,
                        valueSelector = { pt ->
                            when (chartType) {
                                CompareChartType.SPEED              -> pt.avgSpeed
                                CompareChartType.POWER              -> pt.avgPower
                                CompareChartType.ENERGY_CONSUMPTION -> pt.avgConsumption
                                CompareChartType.SOC                -> pt.avgSoc
                                CompareChartType.ELEVATION          -> pt.avgElevation
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ── Normalised point ──────────────────────────────────────────────────────────

private data class NormalisedPoint(
    val distPct       : Double,
    val avgSpeed      : Double,
    val avgPower      : Double,
    val avgConsumption: Double,
    val avgSoc        : Double,
    val avgElevation  : Double
)

private fun normaliseTripData(
    points : List<TripDataPointEntity>,
    buckets: Int = 100
): List<NormalisedPoint> {
    if (points.size < 2) return emptyList()
    val startOdo  = points.first().odometer
    val totalDist = (points.last().odometer - startOdo).coerceAtLeast(0.001)

    val bucketLists = Array(buckets) { mutableListOf<TripDataPointEntity>() }
    for (pt in points) {
        val idx = ((pt.odometer - startOdo) / totalDist * buckets)
            .toInt().coerceIn(0, buckets - 1)
        bucketLists[idx].add(pt)
    }

    return bucketLists.mapIndexedNotNull { i, pts ->
        if (pts.isEmpty()) return@mapIndexedNotNull null
        val consPts = pts.filter { it.speed > 5.0 && it.power > 0.0 }
        NormalisedPoint(
            distPct        = i / buckets.toDouble() * 100.0,
            avgSpeed       = pts.map { it.speed }.average(),
            avgPower       = pts.map { it.power }.average(),
            avgConsumption = if (consPts.isNotEmpty())
                consPts.map { it.power / it.speed * 100.0 }.average()
            else 0.0,
            avgSoc         = pts.map { it.soc }.average(),
            avgElevation   = pts.map { it.altitude }.average()
        )
    }
}

// ── Multi-series canvas line chart ────────────────────────────────────────────

@Composable
private fun OverlaidLineChart(
    series       : List<Triple<List<NormalisedPoint>, Color, Boolean>>,
    yAxisLabel   : String,
    yFloor       : Double? = null,
    valueSelector: (NormalisedPoint) -> Double,
    modifier     : Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f)

    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val padL = 68f; val padR = 12f; val padT = 8f; val padB = 32f
        val chartW = w - padL - padR; val chartH = h - padT - padB

        // Y range from ALL series so the axis is stable when hiding/showing trips
        val allVals = series.flatMap { (pts, _, _) -> pts.map { valueSelector(it) } }
        if (allVals.isEmpty()) return@Canvas
        val rawMin = allVals.min(); val rawMax = allVals.max()
        val range  = (rawMax - rawMin).coerceAtLeast(1.0)
        val yStep  = niceStepCompare(range)
        val yMinRaw = (rawMin / yStep).toInt() * yStep - yStep
        val yMin = if (yFloor != null) maxOf(yMinRaw, yFloor) else yMinRaw
        val yMax = (rawMax / yStep).toInt() * yStep + yStep

        fun xOf(pct: Double) = padL + (pct / 100.0 * chartW).toFloat()
        fun yOf(v: Double)   = (padT + chartH * (1.0 - (v - yMin) / (yMax - yMin))).toFloat()

        val nc = drawContext.canvas.nativeCanvas
        val labelPaint = android.graphics.Paint().apply {
            color = textColor.copy(alpha = 0.65f).toArgb(); textSize = 20f; isAntiAlias = true
        }

        var yTick = yMin
        while (yTick <= yMax + 0.01) {
            val y = yOf(yTick)
            drawLine(gridColor, Offset(padL, y), Offset(w - padR, y), 1f)
            labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
            nc.drawText("%.0f".format(yTick), padL - 4f, y + 7f, labelPaint)
            yTick += yStep
        }

        val yAxisPaint = android.graphics.Paint().apply {
            color = textColor.copy(alpha = 0.50f).toArgb(); textSize = 18f
            textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
        }
        nc.save()
        nc.rotate(-90f, 14f, padT + chartH / 2f)
        nc.drawText(yAxisLabel, 14f, padT + chartH / 2f, yAxisPaint)
        nc.restore()

        drawLine(axisColor, Offset(padL, padT + chartH), Offset(w - padR, padT + chartH), 1.5f)
        val xLabelPaint = android.graphics.Paint().apply {
            color = textColor.copy(alpha = 0.55f).toArgb(); textSize = 18f
            textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
        }
        listOf(0, 25, 50, 75, 100).forEach { pct ->
            nc.drawText("$pct%", xOf(pct.toDouble()), h - 4f, xLabelPaint)
        }

        series.forEach { (pts, color, visible) ->
            if (!visible || pts.size < 2) return@forEach
            val path = Path().apply {
                moveTo(xOf(pts.first().distPct), yOf(valueSelector(pts.first())))
                pts.drop(1).forEach { pt -> lineTo(xOf(pt.distPct), yOf(valueSelector(pt))) }
            }
            drawPath(path, color.copy(alpha = 0.85f),
                style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

// ── Tab 3: Routes ─────────────────────────────────────────────────────────────

@Composable
private fun CompareRoutesTab(
    trips       : List<TripEntity>,
    compareStats: List<com.byd.tripstats.data.local.entity.TripStatsEntity>,
    visibleTrips: Set<Int>
) {
    val statsById    = compareStats.associateBy { it.tripId }
    val routesByTrip = trips.mapIndexedNotNull { i, trip ->
        val route = statsById[trip.id]?.compressedRoute
        if (route.isNullOrEmpty()) null else Triple(i, trip, route)
    }

    // Map fills the tab — no outer scroll wrapper so the map gets all gestures.
    // The sheet can still be dismissed by dragging the handle above the tabs.
    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {

        if (routesByTrip.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No route data available for these trips",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Box
        }

        val visibleRoutes = routesByTrip.filter { (i, _, _) -> i in visibleTrips }
        val allGeoPoints  = visibleRoutes.flatMap { (_, _, route) ->
            route.map { GeoPoint(it.lat, it.lon) }
        }

        AndroidView(
            factory = { ctx ->
                Configuration.getInstance()
                    .load(ctx, ctx.getSharedPreferences("osm_prefs", 0))
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(13.0)
                    // Hide built-in +/- buttons — pinch-to-zoom is sufficient
                    // and frees the bottom of the map for the legend card.
                    zoomController.setVisibility(
                        org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
                    )
                    // Tell every parent scroll/sheet not to steal touches while
                    // a finger is down on the map. Return false so MapView still
                    // receives and handles the event itself.
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN,
                            android.view.MotionEvent.ACTION_MOVE ->
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            android.view.MotionEvent.ACTION_UP,
                            android.view.MotionEvent.ACTION_CANCEL -> {
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                                v.performClick()
                            }
                        }
                        false
                    }
                }
            },
            update = { mapView ->
                mapView.overlays.clear()
                routesByTrip.forEach { (colorIndex, _, route) ->
                    if (colorIndex !in visibleTrips || route.size < 2) return@forEach
                    val polyline = Polyline().apply {
                        outlinePaint.color       = tripColorArgb(colorIndex)
                        outlinePaint.strokeWidth = 8f
                        outlinePaint.isAntiAlias = true
                        setPoints(route.map { GeoPoint(it.lat, it.lon) })
                    }
                    mapView.overlays.add(polyline)
                }
                if (allGeoPoints.size >= 2) {
                    val box = BoundingBox.fromGeoPoints(allGeoPoints)
                    mapView.post {
                        mapView.zoomToBoundingBox(box.increaseByScale(1.15f), true)
                    }
                }
                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Bottom-left legend overlay ────────────────────────────────────────
        // Consolidates: color swatch + date label + distance + consumption
        // Semi-transparent so the map shows through faintly.
        Card(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 24.dp),
            shape     = RoundedCornerShape(8.dp),
            colors    = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border    = androidx.compose.foundation.BorderStroke(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                routesByTrip.forEach { (colorIndex, trip, _) ->
                    val hidden = colorIndex !in visibleTrips
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Color swatch
                        Canvas(modifier = Modifier.size(24.dp, 4.dp)) {
                            drawLine(
                                color = if (hidden) tripColor(colorIndex).copy(alpha = 0.25f)
                                else tripColor(colorIndex),
                                start = Offset(0f, size.height / 2),
                                end   = Offset(size.width, size.height / 2),
                                strokeWidth = 4f,
                                cap   = StrokeCap.Round
                            )
                        }
                        // Date label
                        Text(
                            tripShortLabel(trip),
                            style      = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (hidden) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            else tripColor(colorIndex)
                        )
                        // Distance
                        Text(
                            "%.1f km".format(trip.distance ?: 0.0),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hidden) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.onSurface
                        )
                        // Efficiency
                        Text(
                            trip.efficiency?.let { "%.1f kWh/100".format(it) } ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hidden) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

private fun tripShortLabel(trip: TripEntity): String =
    DateTimeFormatter.ofPattern("dd/MM HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(trip.startTime))

private fun formatDurationCompare(ms: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (h > 0) "${h}h ${m}min" else "${m}min"
}

private fun niceStepCompare(range: Double): Double = when {
    range <= 5    -> 1.0
    range <= 20   -> 5.0
    range <= 50   -> 10.0
    range <= 200  -> 25.0
    range <= 500  -> 50.0
    else          -> 100.0
}