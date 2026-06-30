package com.byd.tripstats.ui.screens.triphistory

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.byd.tripstats.ui.theme.AccelerationOrange
import com.byd.tripstats.ui.viewmodel.DashboardViewModel

@Composable
internal fun MonthlyCostSummaryCard(
    months         : List<DashboardViewModel.MonthlyCost>,
    currencySymbol : String
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded) months else months.take(1)
    val hiddenCount = (months.size - 1).coerceAtLeast(0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.AttachMoney, null,
                        tint = AccelerationOrange, modifier = Modifier.size(20.dp))
                    Text("Monthly Cost", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                }
                if (months.size > 1) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Show less" else "Show $hiddenCount more")
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            shown.forEach { month ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(month.label, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "${"%.1f".format(month.kwhTotal)} kWh",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "$currencySymbol${"%.2f".format(month.costAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AccelerationOrange
                        )
                    }
                }
            }
        }
    }
}
