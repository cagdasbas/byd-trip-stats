package com.byd.tripstats.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.tripstats.data.preferences.UnitSystem
import com.byd.tripstats.data.preferences.consumptionUnit
import com.byd.tripstats.data.preferences.convertDistance
import com.byd.tripstats.data.preferences.convertEfficiency
import com.byd.tripstats.data.preferences.distanceUnit
import com.byd.tripstats.ui.theme.*
import com.byd.tripstats.ui.viewmodel.DashboardViewModel
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripGoalsScreen(
    viewModel      : DashboardViewModel,
    onNavigateBack : () -> Unit
) {
    val goals          by viewModel.tripGoals.collectAsState()
    val personalBests  by viewModel.personalBests.collectAsState()
    val distanceMonth  by viewModel.distanceThisMonth.collectAsState()
    val allTrips       by viewModel.allTrips.collectAsState()
    val unitSystem     by viewModel.unitSystem.collectAsState()

    // Recent efficiency: average of last 5 completed trips with distance ≥ 1 km
    val recentAvgConsumption = remember(allTrips) {
        allTrips.filter { !it.isActive && (it.distance ?: 0.0) >= 1.0 && it.efficiency != null }
            .takeLast(5)
            .mapNotNull { it.efficiency }
            .takeIf { it.isNotEmpty() }
            ?.average()
    }

    var showGoalDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Goals & Personal Bests", fontSize = 20.sp,
                            fontWeight = FontWeight.Bold)
                        Text("Track your driving milestones",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            modifier = Modifier.size(28.dp))
                    }
                },
                actions = {
                    IconButton(onClick = { showGoalDialog = true }) {
                        Icon(Icons.Filled.Edit, "Set goals",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Personal bests ────────────────────────────────────────────────
            SectionLabel("🏆  Personal Bests")

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BestCard(
                    modifier = Modifier.weight(1f),
                    icon     = Icons.Filled.Eco,
                    color    = RegenGreen,
                    label    = "Best efficiency",
                    value    = personalBests.bestConsumption
                        ?.let { "${"%.2f".format(unitSystem.convertEfficiency(it))} ${unitSystem.consumptionUnit}" } ?: "No data",
                    sub      = "Lowest ever recorded"
                )
                BestCard(
                    modifier = Modifier.weight(1f),
                    icon     = Icons.Filled.Route,
                    color    = BatteryBlue,
                    label    = "Longest trip",
                    value    = personalBests.bestDistance
                        ?.let { "${"%.1f".format(unitSystem.convertDistance(it))} ${unitSystem.distanceUnit}" } ?: "No data",
                    sub      = "Single trip distance"
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔥", fontSize = 28.sp)
                    Column {
                        Text("Longest active streak",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${personalBests.longestStreak} consecutive day${
                                if (personalBests.longestStreak != 1) "s" else ""} with a trip",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(horizontal = 12.dp))

            // ── Goals ─────────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🎯  Goals", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                if (goals.targetConsumptionKwhPer100km == null &&
                    goals.targetDistanceKmPerMonth == null) {
                    TextButton(onClick = { showGoalDialog = true }) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Set goals")
                    }
                }
            }

            if (goals.targetConsumptionKwhPer100km == null &&
                goals.targetDistanceKmPerMonth == null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🎯", fontSize = 36.sp)
                        Text("No goals set yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                        Text("Tap ✏️ above to set a consumption or distance target.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                    }
                }
            }

            // Efficiency goal progress
            goals.targetConsumptionKwhPer100km?.let { target ->
                val displayTarget = unitSystem.convertEfficiency(target)
                val displayCurrent = recentAvgConsumption?.let { unitSystem.convertEfficiency(it) }
                GoalProgressCard(
                    icon        = Icons.Filled.Eco,
                    color       = RegenGreen,
                    title       = "Efficiency target",
                    subtitle    = "≤ ${"%.2f".format(displayTarget)} ${unitSystem.consumptionUnit} (last 5 trips avg)",
                    current     = displayCurrent,
                    target      = displayTarget,
                    unit        = unitSystem.consumptionUnit,
                    lowerIsBetter = true
                )
            }

            // Monthly distance goal progress
            goals.targetDistanceKmPerMonth?.let { target ->
                val displayTarget = unitSystem.convertDistance(target)
                val displayCurrent = unitSystem.convertDistance(distanceMonth)
                GoalProgressCard(
                    icon        = Icons.Filled.Route,
                    color       = BatteryBlue,
                    title       = "Monthly distance",
                    subtitle    = "${"%.0f".format(displayCurrent)} / ${"%.0f".format(displayTarget)} ${unitSystem.distanceUnit} this month",
                    current     = displayCurrent,
                    target      = displayTarget,
                    unit        = unitSystem.distanceUnit,
                    lowerIsBetter = false
                )
            }

            Spacer(Modifier.height(16.dp))
        }

        if (showGoalDialog) {
            GoalEditDialog(
                current    = goals,
                unitSystem = unitSystem,
                onSave     = { cons, dist ->
                    viewModel.saveTripGoals(cons, dist)
                    showGoalDialog = false
                },
                onDismiss = { showGoalDialog = false }
            )
        }
    }
}

// ── Goal progress card ────────────────────────────────────────────────────────

@Composable
private fun GoalProgressCard(
    icon          : ImageVector,
    color         : Color,
    title         : String,
    subtitle      : String,
    current       : Double?,
    target        : Double,
    unit          : String,
    lowerIsBetter : Boolean
) {
    val progress = if (current == null) null else {
        if (lowerIsBetter)
            // 0% = at or above target (bad), 100% = at or below target (good)
            (1.0 - ((current - target) / target).coerceIn(-1.0, 1.0)).toFloat()
                .coerceIn(0f, 1f)
        else
            (current / target).toFloat().coerceIn(0f, 1f)
    }

    val achieved = progress != null && progress >= 1f
    val progressColor = when {
        progress == null -> MaterialTheme.colorScheme.onSurfaceVariant
        achieved         -> RegenGreen
        lowerIsBetter && current != null && current > target * 1.1 ->
            MaterialTheme.colorScheme.error
        else             -> color
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = tween(800),
        label = "goalProgress"
    )

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = progressColor, modifier = Modifier.size(22.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                        Text(subtitle, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (achieved) {
                    Text("✅", fontSize = 20.sp)
                } else if (current != null) {
                    Text(
                        "${"%.1f".format(current)} $unit",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = progressColor
                    )
                }
            }

            // Progress bar
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(RoundedCornerShape(5.dp))
                        .background(progressColor)
                )
            }

            if (!achieved && current != null) {
                val remaining = if (lowerIsBetter)
                    "Need ${"%.2f".format(current - target)} $unit improvement"
                else
                    "${"%.0f".format(target - current)} $unit remaining this month"
                Text(remaining, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Small composables ─────────────────────────────────────────────────────────

@Composable
private fun BestCard(
    modifier : Modifier,
    icon     : ImageVector,
    color    : Color,
    label    : String,
    value    : String,
    sub      : String
) {
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}

// ── Goal edit dialog ──────────────────────────────────────────────────────────

@Composable
private fun GoalEditDialog(
    current    : DashboardViewModel.TripGoals,
    unitSystem : UnitSystem = UnitSystem.METRIC,
    onSave     : (consumption: Double?, distancePerMonth: Double?) -> Unit,
    onDismiss  : () -> Unit
) {
    val efficiencyFactor = if (unitSystem == UnitSystem.IMPERIAL) 1.0 / 0.621371 else 1.0
    val distanceFactor   = if (unitSystem == UnitSystem.IMPERIAL) 0.621371 else 1.0
    var consInput  by remember {
        mutableStateOf(current.targetConsumptionKwhPer100km?.let { "%.2f".format(it * efficiencyFactor) } ?: "")
    }
    var distInput  by remember {
        mutableStateOf(current.targetDistanceKmPerMonth?.let { "%.0f".format(it * distanceFactor) } ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surfaceVariant,
        icon = { Icon(Icons.Filled.TrackChanges, null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) },
        title = { Text("Set Goals", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Leave a field blank to disable that goal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = consInput,
                    onValueChange = { consInput = it },
                    label = { Text("Target consumption") },
                    placeholder = { Text("e.g. 17.0") },
                    suffix = { Text(unitSystem.consumptionUnit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text("Avg of last 5 trips must be ≤ this value")
                    }
                )
                OutlinedTextField(
                    value = distInput,
                    onValueChange = { distInput = it },
                    label = { Text("Monthly distance target") },
                    placeholder = { Text("e.g. 500") },
                    suffix = { Text("${unitSystem.distanceUnit}/month") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val cons = consInput.trim().replace(',', '.').toDoubleOrNull()
                    ?.takeIf { it > 0 }
                    ?.let { if (unitSystem == UnitSystem.IMPERIAL) it * 0.621371 else it }
                val dist = distInput.trim().toDoubleOrNull()?.takeIf { it > 0 }
                    ?.let { if (unitSystem == UnitSystem.IMPERIAL) it / 0.621371 else it }
                onSave(cons, dist)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}