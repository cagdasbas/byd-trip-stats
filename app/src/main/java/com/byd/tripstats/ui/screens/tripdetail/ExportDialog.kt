package com.byd.tripstats.ui.screens.tripdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.byd.tripstats.data.backup.TelegramManager
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import com.byd.tripstats.data.local.entity.TripEntity
import com.byd.tripstats.data.preferences.SocSource
import com.byd.tripstats.data.preferences.UnitSystem

@Composable
fun ExportDialog(
    trip: TripEntity,
    dataPoints: List<TripDataPointEntity>,
    onDismiss: () -> Unit,
    unitSystem: UnitSystem = UnitSystem.METRIC,
    socSource: SocSource = SocSource.PANEL
) {
    val context          = LocalContext.current
    val stableTrip       = remember { trip }
    val stableDataPoints = remember { dataPoints.toList() }

    val telegram         = remember { TelegramManager.getInstance(context) }
    val telegramConfig   by telegram.config.collectAsState()
    val telegramState    by telegram.state.collectAsState()
    val telegramSending  = telegramState is TelegramManager.TelegramState.InProgress
    val scope            = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text("Export Trip Data", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {

                // ── Clipboard ─────────────────────────────────────────────────
                OutlinedButton(
                    onClick = {
                        copyTripSummaryToClipboard(context, stableTrip, unitSystem, socSource)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy Summary to Clipboard")
                }

                HorizontalDivider()

                // ── Downloads (collapsible) ───────────────────────────────────
                var downloadsExpanded by remember { mutableStateOf(false) }
                ExpandableSectionHeader(
                    label = "Save to Download folder",
                    expanded = downloadsExpanded,
                    onToggle = { downloadsExpanded = !downloadsExpanded }
                )
                if (downloadsExpanded) {
                    OutlinedButton(
                        onClick = {
                            saveTripAsCSV(context, stableTrip, stableDataPoints)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.TableChart, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save as CSV")
                    }

                    OutlinedButton(
                        onClick = {
                            saveTripAsJSON(context, stableTrip, stableDataPoints)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.DataObject, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save as JSON")
                    }

                    OutlinedButton(
                        onClick = {
                            saveTripAsHtml(context, stableTrip, stableDataPoints)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Public, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save as HTML viewer (double-click to open)")
                    }
                }

                HorizontalDivider()

                // ── Telegram (collapsible) ────────────────────────────────────
                var telegramExpanded by remember { mutableStateOf(false) }
                ExpandableSectionHeader(
                    label = if (telegramConfig != null)
                        "Send to Telegram bot (@${telegramConfig!!.botName})"
                    else
                        "Send to Telegram bot (not configured)",
                    expanded = telegramExpanded,
                    onToggle = { telegramExpanded = !telegramExpanded }
                )
                if (telegramExpanded) {
                    if (telegramConfig == null) {
                        Text(
                            "Set up the bot in Settings → Backup & Restore.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            sendTripExportToTelegram(
                                context, telegram, scope, stableTrip,
                                format = "csv",
                                content = buildTripCsv(stableDataPoints)
                            )
                            onDismiss()
                        },
                        enabled = telegramConfig != null && !telegramSending,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Send CSV to Telegram")
                    }

                    OutlinedButton(
                        onClick = {
                            sendTripExportToTelegram(
                                context, telegram, scope, stableTrip,
                                format = "json",
                                content = buildTripJson(stableTrip, stableDataPoints)
                            )
                            onDismiss()
                        },
                        enabled = telegramConfig != null && !telegramSending,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Send JSON to Telegram")
                    }

                    OutlinedButton(
                        onClick = {
                            sendTripExportToTelegram(
                                context, telegram, scope, stableTrip,
                                format = "html",
                                content = buildTripEmbeddedHtml(context, stableTrip, stableDataPoints)
                            )
                            onDismiss()
                        },
                        enabled = telegramConfig != null && !telegramSending,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Send HTML viewer to Telegram")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Tappable section header for the export dialog — caret + label that flips state on click.
 */
@Composable
internal fun ExpandableSectionHeader(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowDown
                          else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
