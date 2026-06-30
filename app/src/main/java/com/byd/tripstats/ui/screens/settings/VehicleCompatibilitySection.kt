package com.byd.tripstats.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.byd.tripstats.data.backup.TelegramManager
import com.byd.tripstats.sdk.VehicleCompatibilityProbe
import com.byd.tripstats.ui.theme.BydElectricAzure
import com.byd.tripstats.ui.theme.ToggleUncheckedTrack
import com.byd.tripstats.util.QrCodeGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
internal fun VehicleCompatibilitySection(context: Context, scope: CoroutineScope) {
    val isEnabled by VehicleCompatibilityProbe.isEnabled.collectAsState()
    val entryCount by VehicleCompatibilityProbe.entryCount.collectAsState()
    val lastCapture by VehicleCompatibilityProbe.lastCaptureAt.collectAsState()
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var privacyExpanded by rememberSaveable { mutableStateOf(false) }
    var qrUrl by remember { mutableStateOf<String?>(null) }
    var uploadInProgress by remember { mutableStateOf(false) }
    val telegram = remember { TelegramManager.getInstance(context) }
    val telegramConfig by telegram.config.collectAsState()
    val telegramState by telegram.state.collectAsState()

    SectionHeader(icon = Icons.Filled.BugReport, title = "Vehicle Compatibility")

    Text(
        "Help support for your BYD model by sharing a raw telemetry probe report. " +
        "Enable probing, drive for a couple of minutes, then export and share the file.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // Privacy banner
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { privacyExpanded = !privacyExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Filled.PrivacyTip, null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Privacy notice",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Icon(
                    if (privacyExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    null, modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            AnimatedVisibility(visible = privacyExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Text(
                    "The report captures raw values from every data source on " +
                    "your car's head unit — numeric values, strings, and arrays.\n\n" +
                    "GPS latitude and longitude are NOT included.\n\n" +
                    "Everything else is included: battery state, motor data, gear, speed, " +
                    "drive modes, climate state, charging state, PHEV-specific values, etc.\n\n" +
                    "Nothing is sent automatically. \"Email via QR code\" uploads the report to a " +
                    "temporary public host (litterbox) and shows a link that auto-deletes after 12 hours; " +
                    "anyone with the link can read it until then. The other options keep the report on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }

    // Enable toggle row
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Enable compatibility probing",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (isEnabled)
                            "Active — $entryCount fields captured${lastCapture?.let { " · last: ${it.substringBefore('T')}" } ?: ""}"
                        else
                            "Off — enable, drive briefly, then export.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { next ->
                        VehicleCompatibilityProbe.setEnabled(next)
                    },
                    thumbContent = if (!isEnabled) {
                        {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(ToggleUncheckedTrack, CircleShape)
                            )
                        }
                    } else null,
                    colors = SwitchDefaults.colors(
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = ToggleUncheckedTrack,
                        uncheckedBorderColor = ToggleUncheckedTrack
                    )
                )
            }

            // Action buttons
            val isSending = telegramState is TelegramManager.TelegramState.InProgress
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (telegramConfig == null) {
                            statusMessage = "Telegram not configured — set it up under Backup & Restore first."
                            return@Button
                        }
                        scope.launch(Dispatchers.IO) {
                            try {
                                val file = VehicleCompatibilityProbe.exportReportFile()
                                telegram.sendFile(
                                    file,
                                    caption = "BYD Trip Stats — compatibility probe report\n" +
                                        "Entries: $entryCount · ${lastCapture?.substringBefore('T') ?: "no date"}"
                                )
                                launch(Dispatchers.Main) {
                                    statusMessage = "Sent via Telegram ✓"
                                }
                            } catch (e: Exception) {
                                launch(Dispatchers.Main) {
                                    statusMessage = "Telegram send failed: ${e.message}"
                                }
                            }
                        }
                    },
                    enabled = entryCount > 0 && !isSending,
                    modifier = Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (isSending) "Sending…" else "Telegram")
                }

                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                VehicleCompatibilityProbe.exportReportFile(saveToDownloads = true)
                                launch(Dispatchers.Main) {
                                    statusMessage = "Saved to Download/BydTripStats/compat_probe.json"
                                }
                            } catch (e: Exception) {
                                launch(Dispatchers.Main) {
                                    statusMessage = "Save failed: ${e.message}"
                                }
                            }
                        }
                    },
                    enabled = entryCount > 0 && !isSending,
                    modifier = Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                ) {
                    Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save")
                }

                Button(
                    onClick = {
                        VehicleCompatibilityProbe.clear()
                        statusMessage = "Probe data cleared."
                    },
                    enabled = entryCount > 0 && !isSending,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                }
            }

            // Recommended path: upload the report and show a QR the user scans with
            // their phone to email the download link — no Telegram/adb/file transfer.
            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        launch(Dispatchers.Main) { uploadInProgress = true; statusMessage = null }
                        try {
                            val url = VehicleCompatibilityProbe.uploadReport(retention = "12h")
                            launch(Dispatchers.Main) {
                                uploadInProgress = false
                                qrUrl = url
                            }
                        } catch (e: Exception) {
                            launch(Dispatchers.Main) {
                                uploadInProgress = false
                                statusMessage = "Upload failed: ${e.message}"
                            }
                        }
                    }
                },
                enabled = entryCount > 0 && !uploadInProgress && !isSending,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
            ) {
                if (uploadInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(Icons.Filled.QrCode2, null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(if (uploadInProgress) "Uploading…" else "Email via QR code")
            }

            if (telegramConfig == null) {
                Text(
                    "Telegram not configured — set it up under Backup & Restore first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Status line
            statusMessage?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (msg.startsWith("Telegram send failed") || msg.startsWith("Upload failed"))
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    qrUrl?.let { url ->
        ProbeEmailQrDialog(downloadUrl = url, onDismiss = { qrUrl = null })
    }
}

/**
 * Shows a QR code encoding a pre-filled `mailto:bydtripstats@gmail.com` whose body carries
 * the probe download link. The head unit has no mail app, so the user scans this with their
 * phone, picks an account and presses Send — the report link reaches support, and the link
 * expires in 12h. Falls back to showing the raw URL with a copy button.
 */
@Composable
private fun ProbeEmailQrDialog(downloadUrl: String, onDismiss: () -> Unit) {
    val supportEmail = "bydtripstats@gmail.com"
    val clipboard = LocalClipboardManager.current
    val sizePx = with(LocalDensity.current) { 220.dp.roundToPx() }
    val qr = remember(downloadUrl) {
        val subject = "BYD Trip Stats — vehicle compatibility probe"
        val body = "Vehicle compatibility probe report.\n\n" +
            "Download (expires in 12h):\n$downloadUrl\n\n" +
            "Sent from BYD Trip Stats."
        val mailto = "mailto:$supportEmail?subject=" + Uri.encode(subject) +
            "&body=" + Uri.encode(body)
        QrCodeGenerator.generate(mailto, sizePx)?.asImageBitmap()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(downloadUrl)) }) {
                Text("Copy link")
            }
        },
        title = { Text("Scan to email the report") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Scan with your phone — it opens a pre-filled email to $supportEmail. " +
                        "Just choose your account and press Send. The link expires in 12 hours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (qr != null) {
                    Image(
                        bitmap = qr,
                        contentDescription = "QR code that opens a pre-filled email with the probe download link",
                        filterQuality = FilterQuality.None,
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                            .size(220.dp)
                    )
                } else {
                    Text(
                        "Couldn't render the QR code. Use \"Copy link\" and email it to $supportEmail.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    downloadUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    )
}
