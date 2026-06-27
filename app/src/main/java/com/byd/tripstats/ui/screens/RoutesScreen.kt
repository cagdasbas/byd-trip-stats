package com.byd.tripstats.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.tripstats.data.analysis.RouteGroup
import com.byd.tripstats.data.analysis.RouteInstance
import com.byd.tripstats.data.preferences.UnitSystem
import com.byd.tripstats.data.preferences.consumptionUnit
import com.byd.tripstats.data.preferences.convertDistance
import com.byd.tripstats.data.preferences.convertEfficiency
import com.byd.tripstats.data.preferences.distanceUnit
import com.byd.tripstats.ui.theme.*
import com.byd.tripstats.ui.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesScreen(
    viewModel: DashboardViewModel,
    onTripClick: (Long) -> Unit,
    onNavigateBack: () -> Unit
) {
    val routes     by viewModel.recurringRoutes.collectAsState()
    val unitSystem by viewModel.unitSystem.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Routes", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Recurring journeys & efficiency",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            modifier = Modifier.size(28.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        if (routes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Text("🛣️", fontSize = 52.sp)
                    Text("No recurring routes yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Text(
                        "Drive the same journey at least 3 times — like a daily commute — " +
                            "and it'll appear here so you can compare efficiency across each trip.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(routes, key = { it.id }) { route ->
                RouteCard(route = route, unitSystem = unitSystem, onTripClick = onTripClick)
            }
        }
    }
}

// ── Route card ─────────────────────────────────────────────────────────────────

@Composable
private fun RouteCard(
    route: RouteGroup,
    unitSystem: UnitSystem,
    onTripClick: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dateRange = remember(route.id) {
        val fmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        "${fmt.format(Date(route.firstDrivenAt))} – ${fmt.format(Date(route.lastDrivenAt))}"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // ── Header ──────────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Route, null,
                        tint = BydElectricAzure, modifier = Modifier.size(26.dp))
                    Column {
                        Text(
                            "${"%.1f".format(unitSystem.convertDistance(route.avgDistanceKm))} " +
                                "${unitSystem.distanceUnit} route",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("${route.instanceCount} trips · $dateRange",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "%.1f".format(unitSystem.convertEfficiency(route.avgEfficiencyKwhPer100km)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BydElectricAzure
                    )
                    Text("avg ${unitSystem.consumptionUnit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // ── Best / worst summary (always visible) ───────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RouteStatChip("🟢", "best",
                    "%.1f".format(unitSystem.convertEfficiency(route.bestEfficiencyKwhPer100km)),
                    RegenGreen)
                RouteStatChip("🔴", "worst",
                    "%.1f".format(unitSystem.convertEfficiency(route.worstEfficiencyKwhPer100km)),
                    MaterialTheme.colorScheme.error)
                RouteStatChip("⏱️", "avg",
                    formatDuration(route.avgDurationMs),
                    MaterialTheme.colorScheme.onSurface)
            }

            // ── Expanded: trend chart + instance list ───────────────────────────
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                    Text("Efficiency per trip (oldest → newest)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    RouteEfficiencyTrend(
                        route = route,
                        unitSystem = unitSystem,
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                    route.instances.forEach { instance ->
                        RouteInstanceRow(
                            instance = instance,
                            isBest = instance.efficiencyKwhPer100km == route.bestEfficiencyKwhPer100km,
                            isWorst = instance.efficiencyKwhPer100km == route.worstEfficiencyKwhPer100km,
                            unitSystem = unitSystem,
                            onClick = { onTripClick(instance.tripId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteStatChip(icon: String, label: String, value: String, valueColor: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 12.sp)
        Text("$label ", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun RouteInstanceRow(
    instance: RouteInstance,
    isBest: Boolean,
    isWorst: Boolean,
    unitSystem: UnitSystem,
    onClick: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()) }
    val effColor = when {
        isBest  -> RegenGreen
        isWorst -> MaterialTheme.colorScheme.error
        else    -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(dateFmt.format(Date(instance.startTime)),
                style = MaterialTheme.typography.bodyMedium)
            Text(
                "${"%.1f".format(unitSystem.convertDistance(instance.distanceKm))} " +
                    "${unitSystem.distanceUnit} · ${formatDuration(instance.durationMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${"%.1f".format(unitSystem.convertEfficiency(instance.efficiencyKwhPer100km))} " +
                    unitSystem.consumptionUnit,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = effColor
            )
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(12.dp))
        }
    }
}

// ── Efficiency trend chart ──────────────────────────────────────────────────────

@Composable
private fun RouteEfficiencyTrend(
    route: RouteGroup,
    unitSystem: UnitSystem,
    modifier: Modifier = Modifier
) {
    // Instances are newest-first; chart chronologically (oldest → newest).
    val series = remember(route.id) { route.instances.reversed() }
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val errorColor = MaterialTheme.colorScheme.error

    val best  = route.bestEfficiencyKwhPer100km
    val worst = route.worstEfficiencyKwhPer100km
    val avg   = route.avgEfficiencyKwhPer100km

    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val padL = 8f; val padR = 8f; val padT = 16f; val padB = 8f
        val chartW = w - padL - padR; val chartH = h - padT - padB
        if (series.isEmpty()) return@Canvas

        // Scale: 0 at bottom looks flat for tightly-clustered efficiencies, so frame
        // the bars around the observed min/max with a little headroom.
        val lo = (best * 0.92)
        val hi = (worst * 1.04).coerceAtLeast(lo + 1.0)
        fun yOf(v: Double) = (padT + chartH * (1.0 - (v - lo) / (hi - lo))).toFloat()

        // Average reference line (dashed).
        val avgY = yOf(avg)
        drawLine(
            color = BydElectricAzure.copy(alpha = 0.7f),
            start = Offset(padL, avgY), end = Offset(w - padR, avgY),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
        )

        val n = series.size
        val slot = chartW / n
        val barW = (slot * 0.55f).coerceAtMost(40f)
        series.forEachIndexed { i, inst ->
            val eff = inst.efficiencyKwhPer100km
            val cx = padL + slot * i + slot / 2f
            val top = yOf(eff)
            val color = when {
                eff == best  -> RegenGreen
                eff == worst -> errorColor
                else         -> BydElectricAzure.copy(alpha = 0.8f)
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(cx - barW / 2f, top),
                size = Size(barW, (h - padB) - top),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f)
            )
        }

        // "avg" label at the dashed line.
        drawContext.canvas.nativeCanvas.drawText(
            "avg ${"%.1f".format(unitSystem.convertEfficiency(avg))}",
            padL + 2f, (avgY - 4f).coerceAtLeast(12f),
            android.graphics.Paint().apply {
                color = textColor.copy(alpha = 0.7f).toArgb()
                textSize = 20f
                isAntiAlias = true
            }
        )
        // Faint baseline.
        drawLine(gridColor, Offset(padL, h - padB), Offset(w - padR, h - padB), 1f)
    }
}

private fun formatDuration(ms: Long): String {
    val totalMin = ms / 60_000L
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
