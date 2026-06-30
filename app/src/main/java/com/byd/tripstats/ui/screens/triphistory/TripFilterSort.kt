package com.byd.tripstats.ui.screens.triphistory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.byd.tripstats.data.local.entity.TagEntity
import com.byd.tripstats.data.preferences.UnitSystem
import com.byd.tripstats.data.preferences.consumptionUnit
import com.byd.tripstats.data.preferences.distanceUnit
import com.byd.tripstats.data.preferences.speedUnit
import com.byd.tripstats.ui.components.TagChip
import com.byd.tripstats.ui.viewmodel.DashboardViewModel.TripFilterState
import com.byd.tripstats.ui.viewmodel.DashboardViewModel.TripSortField
import com.byd.tripstats.ui.viewmodel.DashboardViewModel.TripSortOrder

@Composable
internal fun SortSheetContent(
    currentField: TripSortField,
    currentOrder: TripSortOrder,
    onFieldSelected: (TripSortField) -> Unit,
    onOrderToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    val fields = listOf(
        TripSortField.DATE        to "Date",
        TripSortField.DISTANCE    to "Distance",
        TripSortField.DURATION    to "Duration",
        TripSortField.CONSUMPTION to "Avg Consumption",
        TripSortField.REGEN_EFF   to "Regen Efficiency",
        TripSortField.MAX_SPEED   to "Max Speed"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Sort by", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            FilledTonalButton(onClick = onOrderToggle) {
                Icon(
                    imageVector = if (currentOrder == TripSortOrder.DESC)
                        Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (currentOrder == TripSortOrder.DESC) "Descending" else "Ascending")
            }
        }

        Spacer(Modifier.height(16.dp))

        fields.forEach { (field, label) ->
            val selected = field == currentField
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected,
                    onClick = { onFieldSelected(field); onDismiss() }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
internal fun FilterSheetContent(
    current: TripFilterState,
    unitSystem: UnitSystem = UnitSystem.METRIC,
    allTags: List<TagEntity> = emptyList(),
    onApply: (TripFilterState) -> Unit,
    onClear: () -> Unit
) {
    var selectedTagIds by remember { mutableStateOf(current.tagIds) }
    var distMin    by remember { mutableStateOf(current.distanceMin?.toString()    ?: "") }
    var distMax    by remember { mutableStateOf(current.distanceMax?.toString()    ?: "") }
    var durMin     by remember { mutableStateOf(current.durationMin?.toString()    ?: "") }
    var durMax     by remember { mutableStateOf(current.durationMax?.toString()    ?: "") }
    var consMin    by remember { mutableStateOf(current.consumptionMin?.toString() ?: "") }
    var consMax    by remember { mutableStateOf(current.consumptionMax?.toString() ?: "") }
    var regenMin   by remember { mutableStateOf(current.regenEffMin?.toString()    ?: "") }
    var regenMax   by remember { mutableStateOf(current.regenEffMax?.toString()    ?: "") }
    var speedMin   by remember { mutableStateOf(current.maxSpeedMin?.toString()    ?: "") }
    var speedMax   by remember { mutableStateOf(current.maxSpeedMax?.toString()    ?: "") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Filter trips", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        FilterRangeRow("Distance (${unitSystem.distanceUnit})", distMin, distMax) { a, b -> distMin = a; distMax = b }
        FilterRangeRow("Duration (min)",                    durMin,  durMax)  { a, b -> durMin  = a; durMax  = b }
        FilterRangeRow("Avg Consumption (${unitSystem.consumptionUnit})", consMin, consMax) { a, b -> consMin = a; consMax = b }
        FilterRangeRow("Regen Efficiency (%)",              regenMin, regenMax) { a, b -> regenMin = a; regenMax = b }
        FilterRangeRow("Max Speed (${unitSystem.speedUnit})", speedMin, speedMax) { a, b -> speedMin = a; speedMax = b }

        if (allTags.isNotEmpty()) {
            Text("Tags", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                allTags.forEach { tag ->
                    TagChip(
                        tag = tag,
                        selected = tag.id in selectedTagIds,
                        onClick = {
                            selectedTagIds = if (tag.id in selectedTagIds)
                                selectedTagIds - tag.id else selectedTagIds + tag.id
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("Clear all") }
            Button(
                onClick = {
                    onApply(TripFilterState(
                        distanceMin    = distMin.toFloatOrNull(),
                        distanceMax    = distMax.toFloatOrNull(),
                        durationMin    = durMin.toFloatOrNull(),
                        durationMax    = durMax.toFloatOrNull(),
                        consumptionMin = consMin.toFloatOrNull(),
                        consumptionMax = consMax.toFloatOrNull(),
                        regenEffMin    = regenMin.toFloatOrNull(),
                        regenEffMax    = regenMax.toFloatOrNull(),
                        maxSpeedMin    = speedMin.toFloatOrNull(),
                        maxSpeedMax    = speedMax.toFloatOrNull(),
                        favouritesOnly = current.favouritesOnly,
                        tagIds         = selectedTagIds
                    ))
                },
                modifier = Modifier.weight(1f)
            ) { Text("Apply") }
        }
    }
}

@Composable
private fun FilterRangeRow(
    label: String,
    minVal: String,
    maxVal: String,
    onValuesChange: (String, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = minVal,
                onValueChange = { onValuesChange(it, maxVal) },
                modifier = Modifier.weight(1f),
                label = { Text("Min") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            Text("–", style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = maxVal,
                onValueChange = { onValuesChange(minVal, it) },
                modifier = Modifier.weight(1f),
                label = { Text("Max") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) String.format("%dh %dmin", hours, minutes)
    else String.format("%dmin", minutes)
}

private fun formatTimestamp(timestamp: Long): String {
    val instant = java.time.Instant.ofEpochMilli(timestamp)
    val formatter = java.time.format.DateTimeFormatter
        .ofPattern("MMM dd, yyyy HH:mm")
        .withZone(java.time.ZoneId.systemDefault())
    return formatter.format(instant)
}
