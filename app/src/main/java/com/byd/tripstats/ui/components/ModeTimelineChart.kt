package com.byd.tripstats.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.byd.tripstats.data.local.entity.TripDataPointEntity

@Composable
fun ModeTimelineChart(
    dataPoints: List<TripDataPointEntity>,
    showDriveModes: Boolean,
    showRegenModes: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val pointsWithModes = remember(dataPoints) {
        dataPoints.map { it to it.extractTripModes() }
    }
    val hasAnyDriveMode = remember(pointsWithModes) { pointsWithModes.any { (_, m) -> m.driveMode != 0 } }
    val hasAnyRegenMode = remember(pointsWithModes) { pointsWithModes.any { (_, m) -> m.regenMode != 0 } }
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val verticalSpacing = if (compact) 8.dp else 12.dp
    val laneSpacing = if (compact) 10.dp else 14.dp
    val laneHeight = if (compact) 34.dp else 44.dp

    if (!hasAnyDriveMode && !hasAnyRegenMode) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No drive/regen mode data recorded for this trip.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
    ) {
        ModeTimelineLegend(
            showDriveModes = showDriveModes && hasAnyDriveMode,
            showRegenModes = showRegenModes && hasAnyRegenMode,
            compact = compact
        )

        if ((!showDriveModes || !hasAnyDriveMode) && (!showRegenModes || !hasAnyRegenMode)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Enable Drive mode or Regen mode to view the timeline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(laneSpacing)
            ) {
                if (showDriveModes && hasAnyDriveMode) {
                    ModeTimelineLane(
                        title = "Drive mode",
                        pointsWithModes = pointsWithModes,
                        outlineColor = outlineColor,
                        laneHeight = laneHeight,
                        colorFor = { driveModeColor(it.driveMode) }
                    )
                }
                if (showRegenModes && hasAnyRegenMode) {
                    ModeTimelineLane(
                        title = "Regen mode",
                        pointsWithModes = pointsWithModes,
                        outlineColor = outlineColor,
                        laneHeight = laneHeight,
                        colorFor = { regenModeColor(it.regenMode) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeTimelineLane(
    title: String,
    pointsWithModes: List<Pair<TripDataPointEntity, TripPointModes>>,
    outlineColor: Color,
    laneHeight: Dp,
    colorFor: (TripPointModes) -> Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(laneHeight)
                .clip(RoundedCornerShape(10.dp))
        ) {
            val widthPerSegment = size.width / pointsWithModes.size.coerceAtLeast(1)
            pointsWithModes.forEachIndexed { index, (_, modes) ->
                drawRect(
                    color = colorFor(modes),
                    topLeft = androidx.compose.ui.geometry.Offset(index * widthPerSegment, 0f),
                    size = androidx.compose.ui.geometry.Size(widthPerSegment + 1f, size.height)
                )
            }
            drawRoundRect(
                color = outlineColor,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

@Composable
private fun ModeTimelineLegend(
    showDriveModes: Boolean,
    showRegenModes: Boolean,
    compact: Boolean
) {
    val sectionSpacing = if (compact) 6.dp else 10.dp
    val chipSpacing = if (compact) 10.dp else 12.dp
    val labelStyle = if (compact) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.labelMedium
    }
    Column(verticalArrangement = Arrangement.spacedBy(sectionSpacing)) {
        if (showDriveModes) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Drive modes",
                    style = labelStyle,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(chipSpacing)
                ) {
                    LegendSwatch(driveModeColor(1), "Eco")
                    LegendSwatch(driveModeColor(3), "Normal")
                    LegendSwatch(driveModeColor(2), "Sport")
                }
            }
        }
        if (showRegenModes) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Regen modes",
                    style = labelStyle,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(chipSpacing)
                ) {
                    LegendSwatch(regenModeColor(1), "Standard")
                    LegendSwatch(regenModeColor(2), "High")
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 22.dp, height = 10.dp)
                .clip(RoundedCornerShape(999.dp))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = color,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f, size.height / 2f)
                )
            }
        }
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ModeTimelineControls(
    showDriveModes: Boolean,
    showRegenModes: Boolean,
    onToggleDriveModes: () -> Unit,
    onToggleRegenModes: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(
            "Drive modes" to (showDriveModes to onToggleDriveModes),
            "Regen modes" to (showRegenModes to onToggleRegenModes)
        ).forEach { (label, pair) ->
            val (selected, onClick) = pair
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onClick() }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
