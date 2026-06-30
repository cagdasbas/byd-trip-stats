package com.byd.tripstats.ui.screens.charginghistory

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.byd.tripstats.data.local.entity.ChargingSessionEntity
import com.byd.tripstats.data.preferences.SocSource
import com.byd.tripstats.ui.theme.RegenGreen

@Composable
internal fun ChargingStatsSummary(
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
