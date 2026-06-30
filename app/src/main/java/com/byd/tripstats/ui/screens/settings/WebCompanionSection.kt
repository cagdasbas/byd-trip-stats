package com.byd.tripstats.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.byd.tripstats.data.preferences.PreferencesManager
import com.byd.tripstats.ui.theme.ToggleUncheckedTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun WebCompanionSection(context: Context, scope: CoroutineScope) {
    val preferencesManager = remember { PreferencesManager(context) }
    // DataStore is the authoritative state — the switch just writes here and
    // the LaunchedEffect reacts, so the toggle is always responsive.
    val enabled by preferencesManager.webServerEnabled.collectAsState(initial = true)
    val port    by preferencesManager.webServerPort.collectAsState(
        initial = PreferencesManager.DEFAULT_WEB_SERVER_PORT
    )
    val pin     by preferencesManager.webServerPin.collectAsState(initial = "")
    var portInput   by remember(port) { mutableStateOf(port.toString()) }
    var pinInput    by remember(pin)  { mutableStateOf(pin) }
    var pinVisible   by remember { mutableStateOf(false) }
    var serverUrl    by remember { mutableStateOf<String?>(null) }
    var serverError  by remember { mutableStateOf<String?>(null) }
    val lockedCount  by com.byd.tripstats.server.WebServerManager.lockedOutCount.collectAsState()
    val clipManager = androidx.compose.ui.platform.LocalClipboardManager.current

    // Ensure a PIN exists as soon as the section is first composed
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { preferencesManager.getOrCreateWebServerPin() }
    }

    // Start/stop the server whenever enabled, port, or PIN changes.
    // Runs on IO so ServerSocket binding never touches the main thread.
    LaunchedEffect(enabled, port, pin) {
        com.byd.tripstats.server.WebServerManager.stop()
        serverUrl   = null
        serverError = null
        if (enabled && pin.isNotEmpty()) {
            val error = withContext(Dispatchers.IO) {
                com.byd.tripstats.server.WebServerManager.start(context, port, pin)
            }
            if (error == null) {
                serverUrl   = com.byd.tripstats.server.WebServerManager.getUrl(context)
                serverError = null
            } else {
                serverUrl   = null
                serverError = error
            }
        }
    }

    SectionHeader(icon = Icons.Filled.Language, title = "Web Companion")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Web companion",
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (enabled)
                            "Browse trips & charging from any device on the same WiFi."
                        else
                            "Start a local server so you can open trip history in a browser.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked         = enabled,
                    onCheckedChange = { next ->
                        scope.launch { preferencesManager.saveWebServerEnabled(next) }
                    },
                    thumbContent = if (!enabled) {
                        { Box(Modifier.size(12.dp).background(ToggleUncheckedTrack, CircleShape)) }
                    } else null,
                    colors = SwitchDefaults.colors(
                        uncheckedThumbColor  = Color.White,
                        uncheckedTrackColor  = ToggleUncheckedTrack,
                        uncheckedBorderColor = ToggleUncheckedTrack
                    )
                )
            }

            if (enabled) {
                Text(
                    "Served over your local WiFi, so it's reachable while the car is on and the " +
                        "telemetry service is alive. Once parked, most BYD units cut WiFi ~15 min " +
                        "after the car is off — after that the companion is unreachable (there's no " +
                        "mobile-data fallback for a local server) until the car powers on again, in " +
                        "every Background activity mode.\nNote: Electro app has an option to keep WiFi alive indefinitely",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Port row — always visible so users can change it even while disabled
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = portInput,
                    onValueChange = { portInput = it.filter(Char::isDigit).take(5) },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val p = portInput.toIntOrNull()?.coerceIn(1024, 65535) ?: return@Button
                        scope.launch { preferencesManager.saveWebServerPort(p) }
                    },
                    enabled = portInput.toIntOrNull()?.let { it in 1024..65535 } == true &&
                              portInput.toIntOrNull() != port
                ) { Text("Apply") }
            }
            Text(
                "Try 8081, 8888, 9090 if a port is blocked. Changes restart the server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

            // ── PIN ──────────────────────────────────────────────────────────
            Text(
                "Access PIN",
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Shown on the browser login page when someone opens the server URL.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it.filter(Char::isDigit).take(10) },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = if (pinVisible)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        IconButton(onClick = { pinVisible = !pinVisible }) {
                            Icon(
                                if (pinVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (pinVisible) "Hide PIN" else "Show PIN"
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                // Apply custom PIN
                Button(
                    onClick = {
                        if (pinInput.length >= 4) scope.launch {
                            preferencesManager.saveWebServerPin(pinInput)
                        }
                    },
                    enabled = pinInput.length >= 4 && pinInput != pin
                ) { Text("Set") }
                // Generate a new random PIN
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val newPin = (100_000..999_999).random().toString()
                            pinInput = newPin
                            preferencesManager.saveWebServerPin(newPin)
                        }
                    }
                ) { Text("Regen") }
            }
            Text(
                "Min 4 digits. Changing the PIN invalidates all active browser sessions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

            // ── Lockout banner ───────────────────────────────────────────────
            if (lockedCount > 0) {
                val plural = if (lockedCount > 1) "addresses" else "address"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "$lockedCount IP $plural locked out",
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "Too many incorrect PIN attempts detected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                    OutlinedButton(
                        onClick = { com.byd.tripstats.server.WebServerManager.clearLockouts() },
                        colors  = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Clear") }
                }
            }

            if (enabled) {
                if (serverError != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "Failed to start: $serverError",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    val url = serverUrl ?: "Waiting for IP…"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            url,
                            style      = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            modifier   = Modifier.weight(1f)
                        )
                        if (serverUrl != null) {
                            IconButton(
                                onClick  = { clipManager.setText(AnnotatedString(url)) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = "Copy URL",
                                    modifier = Modifier.size(18.dp),
                                    tint     = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Text(
                        "Open this address on any device connected to the same WiFi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
