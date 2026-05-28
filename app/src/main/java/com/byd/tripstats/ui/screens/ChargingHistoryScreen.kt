package com.byd.tripstats.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.tripstats.data.local.entity.ChargingSessionEntity
import com.byd.tripstats.ui.theme.*
import com.byd.tripstats.data.preferences.SocSource
import com.byd.tripstats.ui.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargingHistoryScreen(
    viewModel: DashboardViewModel,
    onSessionClick: (Long) -> Unit,
    onNavigateBack: () -> Unit
) {
    val sessions by viewModel.allChargingSessions.collectAsState()
    val socSource by viewModel.socSource.collectAsState()
    val completed = sessions.filter { !it.isActive }.sortedByDescending { it.startTime }
    val active = sessions.filter { it.isActive }.sortedByDescending { it.startTime }

    var selectedSessions by remember { mutableStateOf(setOf<Long>()) }
    var selectionMode by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (selectionMode) {
                        Text(
                            "${selectedSessions.size} selected",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            "Charging History",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectionMode) {
                                selectionMode = false
                                selectedSessions = setOf()
                            } else {
                                onNavigateBack()
                            }
                        }
                    ) {
                    Icon(
                        imageVector =
                            if (selectionMode) Icons.Filled.Close
                            else Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription =
                            if (selectionMode) "Cancel" else "Back",
                        modifier = Modifier.size(28.dp)
                    )
                    }
                },
                actions = {
                    if (selectionMode && selectedSessions.isNotEmpty()) {
                        IconButton(onClick = { showDeleteSelectedDialog = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete selected",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
            )
        }
    ) { paddingValues ->
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.BatteryChargingFull,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No charging sessions yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Sessions are reconstructed automatically\nfrom SoC changes on each car wake-up",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Active sessions at the top
                items(active, key = { it.id }) { session ->
                    ChargingSessionCard(
                        session = session,
                        isActive = true,
                        isSelected = selectedSessions.contains(session.id),
                        selectionMode = selectionMode,
                        socSource = socSource,
                        onClick = {
                            if (selectionMode) {
                                selectedSessions =
                                    if (selectedSessions.contains(session.id)) {
                                        selectedSessions - session.id
                                    } else {
                                        selectedSessions + session.id
                                    }
                            } else {
                                onSessionClick(session.id)
                            }
                        },
                        onLongClick = {
                            if (!selectionMode) {
                                selectionMode = true
                                selectedSessions = setOf(session.id)
                            }
                        },
                        onDelete = { viewModel.deleteChargingSession(session.id) }
                    )
                }

                // Summary header
                if (completed.isNotEmpty()) {
                    item { ChargingStatsSummary(completed, socSource) }
                }

                items(completed, key = { it.id }) { session ->
                    ChargingSessionCard(
                        session = session,
                        isActive = false,
                        isSelected = selectedSessions.contains(session.id),
                        selectionMode = selectionMode,
                        socSource = socSource,
                        onClick = {
                            if (selectionMode) {
                                selectedSessions =
                                    if (selectedSessions.contains(session.id)) {
                                        selectedSessions - session.id
                                    } else {
                                        selectedSessions + session.id
                                    }
                            } else {
                                onSessionClick(session.id)
                            }
                        },
                        onLongClick = {
                            if (!selectionMode) {
                                selectionMode = true
                                selectedSessions = setOf(session.id)
                            }
                        },
                        onDelete = { viewModel.deleteChargingSession(session.id) }
                    )
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = {
                Text(
                    "Delete ${selectedSessions.size} Session${if (selectedSessions.size > 1) "s" else ""}?"
                )
            },
            text = {
                Text(
                    "This will permanently delete the selected charging sessions and all their data. This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteChargingSessions(selectedSessions.toList())
                        showDeleteSelectedDialog = false
                        selectionMode = false
                        selectedSessions = setOf()
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Summary card ──────────────────────────────────────────────────────────────

@Composable
private fun ChargingStatsSummary(
    sessions : List<ChargingSessionEntity>,
    socSource: SocSource = SocSource.PANEL,
) {
    val totalKwh      = sessions.sumOf { it.kwhAdded ?: 0.0 }
    val totalSessions = sessions.size
    val avgSocDelta = if (socSource == SocSource.PANEL) {
        sessions.mapNotNull { it.socPanelDelta.takeIf { d -> d != null && it.socStartPanel > 0.0 } }
            .takeIf { it.isNotEmpty() }?.average()
            ?: sessions.mapNotNull { it.socDelta }.takeIf { it.isNotEmpty() }?.average()
            ?: 0.0
    } else {
        sessions.mapNotNull { it.socDelta }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
    }

    Card(
        modifier =
            Modifier.fillMaxWidth()
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(12.dp)
                ),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryMetric(label = "Sessions", value = totalSessions.toString(), unit = "")
            SummaryMetric(label = "Total added", value = "%.1f".format(totalKwh), unit = "kWh")
            SummaryMetric(label = "Avg SoC gain", value = "%.0f".format(avgSocDelta), unit = "%")
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = RegenGreen
            )
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

// ── Session card ──────────────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChargingSessionCard(
    session      : ChargingSessionEntity,
    isActive     : Boolean,
    isSelected   : Boolean = false,
    selectionMode: Boolean = false,
    socSource    : SocSource = SocSource.PANEL,
    onClick      : () -> Unit,
    onLongClick  : () -> Unit = {},
    onDelete     : () -> Unit = {}
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault()) }
    val startLabel = dateFmt.format(Date(session.startTime))
    val durationStr =
        if (isActive) {
            formatDuration((System.currentTimeMillis() - session.startTime) / 1000)
        } else {
            session.durationSeconds?.let { formatDuration(it) } ?: "—"
        }

    val usePanelSoc = socSource == SocSource.PANEL && session.socStartPanel > 0.0
    val displaySocStart = if (usePanelSoc) session.socStartPanel else session.socStart
    val displaySocEnd   = if (usePanelSoc) session.socEndPanel else session.socEnd
    val socText =
        when {
            displaySocEnd != null ->
                "%.1f%%  →  %.1f%%".format(displaySocStart, displaySocEnd)
            else -> "%.1f%%  →  …".format(displaySocStart)
        }

    val kwhText = session.kwhAdded?.let { "%.2f kWh".format(it) } ?: "—"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                enabled = !isActive || !selectionMode
            )
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) RegenGreen
                        else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isActive && selectionMode -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.primaryContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header row: checkbox + date + active badge + delete icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkbox
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (selectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onClick() },
                            enabled = !isActive,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text  = startLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = RegenGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text     = "● Charging",
                                style    = MaterialTheme.typography.labelSmall,
                                color    = RegenGreen,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Delete icon
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (!selectionMode) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            enabled = !isActive,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(18.dp),
                                tint =
                                    if (isActive)
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.38f
                                        )
                                    else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SessionMetricChip(
                    icon  = Icons.Filled.Timer,
                    label = durationStr,
                    tint  = MaterialTheme.colorScheme.primary
                )
                SessionMetricChip(
                    icon  = Icons.Filled.BatteryChargingFull,
                    label = socText,
                    tint  = BatteryBlue
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SessionMetricChip(
                    icon  = Icons.Filled.ElectricalServices,
                    label = kwhText,
                    tint  = RegenGreen
                )
                if (session.peakKw > 0) {
                    SessionMetricChip(
                        icon  = Icons.Filled.Bolt,
                        label = "Peak %.0f kW".format(session.peakKw),
                        tint  = AccelerationOrange
                    )
                }
                if (session.avgKw > 0) {
                    SessionMetricChip(
                        icon  = Icons.AutoMirrored.Filled.TrendingUp,
                        label = "Avg %.0f kW".format(session.avgKw),
                        tint  = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Synthetic session badge — shown when no real-time data was captured
                if (session.peakKw == 0.0 && session.avgKw == 0.0 && !isActive) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "⚡ Reconstructed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Delete Charging Session?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SessionMetricChip(
    icon : androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint : androidx.compose.ui.graphics.Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatDuration(seconds: Long): String {
    val h = TimeUnit.SECONDS.toHours(seconds)
    val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
    return if (h > 0) "${h}h ${m}min" else "${m}min"
}