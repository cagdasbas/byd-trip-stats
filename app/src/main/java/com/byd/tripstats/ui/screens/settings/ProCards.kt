package com.byd.tripstats.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.byd.tripstats.data.entitlement.EntitlementManager
import com.byd.tripstats.ui.theme.BydElectricAzure
import com.byd.tripstats.util.QrCodeGenerator

/**
 * Pro card. Self-contained so it can be placed at the top of Preferences when locked (upsell)
 * or at the bottom once unlocked (status + the rarely-used "Remove code"). [onEnterCode] opens
 * the unlock-code dialog owned by the caller.
 */
@Composable
internal fun ProUnlockCard(
    isPro: Boolean,
    currentDeviceId: String?,
    hasSavedCode: Boolean,
    onEnterCode: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WorkspacePremium, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "BYD Trip Stats Pro",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isPro) {
                    Spacer(Modifier.width(8.dp))
                    ProBadge()
                }
            }
            if (isPro) {
                var showRemoveCodeConfirm by remember { mutableStateOf(false) }
                Text(
                    "Active — Pro unlocked for this vehicle (lifetime).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { showRemoveCodeConfirm = true }) {
                    Text("Remove code")
                }
                if (showRemoveCodeConfirm) {
                    AlertDialog(
                        onDismissRequest = { showRemoveCodeConfirm = false },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        icon = {
                            Icon(Icons.Filled.Warning, null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp))
                        },
                        title = { Text("Remove Pro unlock code?", fontWeight = FontWeight.Bold) },
                        text = {
                            Text(
                                "This turns Pro off on this vehicle and disables the Pro features " +
                                "(cell imbalance alert, battery health report, screenshots, SD card " +
                                "backup). Your code isn't lost — you can re-enter it anytime to unlock again."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showRemoveCodeConfirm = false
                                EntitlementManager.clear()
                            }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRemoveCodeConfirm = false }) { Text("Cancel") }
                        }
                    )
                }
            } else {
                Text(
                    "Unlock premium features like the battery cell imbalance alert and dashboard screenshots with a one-time code for your vehicle — €9.99, lifetime, one car. Verified on-device — nothing leaves your car.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Codes are derived from this vehicle's id — the buyer sends it at purchase.
                VehicleIdRow(currentDeviceId)
                currentDeviceId?.let { VehicleLicenseQr(it) }
                if (hasSavedCode) {
                    // A saved code that isn't unlocking here → it was issued for another vehicle.
                    Text(
                        "A saved code doesn't match this vehicle.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Button(
                    onClick = onEnterCode,
                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                ) {
                    Icon(Icons.Filled.Key, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Enter unlock code")
                }
            }
        }
    }
}

@Composable
internal fun ProBadge() {
    Box(
        modifier = Modifier
            .background(
                BydElectricAzure,
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            "PRO",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/**
 * A QR code that encodes a pre-filled email (to bydtripstats@gmail.com, with the
 * Vehicle ID in the body). The head unit has no mail app, so the user scans this with
 * their phone, picks their account and presses Send — the reply then goes straight to
 * their own address. Rendered 1:1 at the generated pixel size to stay crisp/scannable.
 */
@Composable
private fun VehicleLicenseQr(deviceId: String) {
    val licenseEmail = "bydtripstats@gmail.com"
    val sizePx = with(LocalDensity.current) { 180.dp.roundToPx() }
    val qr = remember(deviceId) {
        val subject = "BYD Trip Stats Pro unlock request"
        val body = "BYD Trip Stats Pro unlock request (€9.99, one-time).\n\nVehicle ID: $deviceId\n\n" +
            "Please reply with payment instructions and my unlock code. Thank you!"
        val mailto = "mailto:$licenseEmail?subject=" + android.net.Uri.encode(subject) +
            "&body=" + android.net.Uri.encode(body)
        QrCodeGenerator.generate(mailto, sizePx)?.asImageBitmap()
    } ?: return

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Scan this with your phone to email your Vehicle ID — " +
                "just choose your email account and press Send.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Image(
            bitmap = qr,
            contentDescription = "QR code that opens a pre-filled email with your Vehicle ID",
            filterQuality = FilterQuality.None,
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(8.dp))
                .padding(10.dp)
                .size(180.dp)
        )
        Text(
            "Goes to $licenseEmail — you'll get a reply with payment steps (€9.99) and your unlock code.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun VehicleIdRow(deviceId: String?) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "This vehicle's ID (send this when buying a licence):",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                deviceId ?: "Reading… start the car, then reopen Settings",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (deviceId != null) {
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(deviceId))
                    android.widget.Toast.makeText(
                        context, "Vehicle ID copied", android.widget.Toast.LENGTH_SHORT
                    ).show()
                }) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copy vehicle ID",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
