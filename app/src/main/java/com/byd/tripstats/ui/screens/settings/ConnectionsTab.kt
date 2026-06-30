package com.byd.tripstats.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.byd.tripstats.connections.AbrpConnectionManager
import com.byd.tripstats.connections.AbrpConnectionStore
import com.byd.tripstats.connections.MqttConnectionManager
import com.byd.tripstats.connections.MqttConnectionStore
import com.byd.tripstats.ui.theme.BydElectricAzure
import com.byd.tripstats.ui.theme.RegenGreen
import com.byd.tripstats.ui.theme.ToggleUncheckedTrack
import com.byd.tripstats.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ConnectionsTab() {
    val context = LocalContext.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<DashboardViewModel>()
    val selectedCarConfig by viewModel.selectedCarConfig.collectAsState()
    val liveSnapshot by viewModel.vehicleSnapshot.collectAsState()
    val currentTelemetry by viewModel.currentTelemetry.collectAsState()
    val liveTelemetry = remember(liveSnapshot, currentTelemetry, selectedCarConfig) {
        liveSnapshot?.toTelemetry(selectedCarConfig) ?: currentTelemetry
    }
    val abrpManager = remember { AbrpConnectionManager(context) }
    val mqttManager = remember { MqttConnectionManager(context) }
    val screenScope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(AbrpConnectionStore.load(context)) }
    var tokenInput by rememberSaveable { mutableStateOf(settings.userToken) }
    var enabled by rememberSaveable { mutableStateOf(settings.enabled) }
    var intervalInput by rememberSaveable { mutableStateOf(settings.uploadIntervalSeconds.toString()) }
    var lastSavedAt by remember { mutableStateOf(settings.lastUploadAtMs) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var mqttSettings by remember { mutableStateOf(MqttConnectionStore.load(context)) }
    var mqttBrokerInput by rememberSaveable { mutableStateOf(mqttSettings.brokerUrl) }
    var mqttPortInput by rememberSaveable { mutableStateOf(mqttSettings.brokerPort.toString()) }
    var mqttUsernameInput by rememberSaveable { mutableStateOf(mqttSettings.username) }
    var mqttPasswordInput by rememberSaveable { mutableStateOf(mqttSettings.password) }
    var mqttFriendlyNameInput by rememberSaveable { mutableStateOf(mqttSettings.friendlyName) }
    var mqttEnabled by rememberSaveable { mutableStateOf(mqttSettings.enabled) }
    var mqttUseTls by rememberSaveable { mutableStateOf(mqttSettings.useTls) }
    var mqttUseWebSocket by rememberSaveable { mutableStateOf(mqttSettings.useWebSocket) }
    var mqttWsPathInput by rememberSaveable { mutableStateOf(mqttSettings.webSocketPath) }
    var mqttIntervalInput by rememberSaveable { mutableStateOf(mqttSettings.publishIntervalSeconds.toString()) }
    var mqttResult by remember { mutableStateOf<String?>(null) }
    var mqttTesting by remember { mutableStateOf(false) }
    var showAbrpToken by rememberSaveable { mutableStateOf(false) }
    var showConnectionDetails by rememberSaveable { mutableStateOf(false) }

    fun reloadAbrpDraft() {
        settings = AbrpConnectionStore.load(context)
        tokenInput = settings.userToken
        enabled = settings.enabled
        intervalInput = settings.uploadIntervalSeconds.toString()
        lastSavedAt = settings.lastUploadAtMs
    }

    fun reloadMqttDraft() {
        mqttSettings = MqttConnectionStore.load(context)
        mqttBrokerInput = mqttSettings.brokerUrl
        mqttPortInput = mqttSettings.brokerPort.toString()
        mqttUsernameInput = mqttSettings.username
        mqttPasswordInput = mqttSettings.password
        mqttFriendlyNameInput = mqttSettings.friendlyName
        mqttEnabled = mqttSettings.enabled
        mqttUseTls = mqttSettings.useTls
        mqttUseWebSocket = mqttSettings.useWebSocket
        mqttWsPathInput = mqttSettings.webSocketPath
        mqttIntervalInput = mqttSettings.publishIntervalSeconds.toString()
    }

    fun reloadConnectionDrafts() {
        reloadAbrpDraft()
        reloadMqttDraft()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(icon = Icons.Filled.Link, title = "Connections")

        if (!showConnectionDetails) {
            Text(
                "Choose a connection to configure. The cards below show live status at a glance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ConnectionSummaryCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Route,
                    title = "ABRP",
                    body = "A Better Routeplanner uploads live telemetry.",
                    statusLine = "Status: ${if (settings.enabled) "Enabled" else "Disabled"} • ${settings.lastStatus}"
                )
                ConnectionSummaryCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Cloud,
                    title = "MQTT",
                    body = "Publish live telemetry to a broker such as HiveMQ or HomeAssistant.",
                    statusLine = "Status: ${if (mqttSettings.enabled) "Enabled" else "Disabled"} • ${mqttSettings.lastStatus}"
                )
            }
            Button(
                onClick = {
                    reloadConnectionDrafts()
                    showConnectionDetails = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
            ) {
                Text("Open Connections")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    reloadConnectionDrafts()
                    showConnectionDetails = false
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Back to overview")
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "ABRP",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Link Generic token uploads live telemetry to A Better Routeplanner.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Get your ABRP Link Generic token from ABRP web or app. \n" +
                        "Go to Settings -> Vehicle -> Live data -> Edit connections -> " +
                        "In-car live data -> Link Generic",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Enable ABRP",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (enabled) "Uploads every $intervalInput s."
                                else "Turn on to configure ABRP uploads.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { next ->
                                enabled = next
                                if (!next) {
                                    val interval = intervalInput.toIntOrNull()?.coerceIn(5, 120) ?: 5
                                    settings = settings.copy(
                                        enabled = false,
                                        userToken = tokenInput,
                                        apiKey = settings.apiKey.ifBlank { AbrpConnectionStore.DEFAULT_PUBLIC_API_KEY },
                                        uploadIntervalSeconds = interval
                                    )
                                    AbrpConnectionStore.save(context, settings)
                                    lastSavedAt = System.currentTimeMillis()
                                }
                            },
                            thumbContent = if (!enabled) {
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
                    AnimatedVisibility(visible = enabled, enter = expandVertically(), exit = shrinkVertically()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsGroupLabel("Connection")
                            OutlinedTextField(
                                value = tokenInput,
                                onValueChange = { tokenInput = it.trim() },
                                label = { Text("ABRP user token") },
                                placeholder = { Text("Paste your Link Generic token") },
                                singleLine = true,
                                visualTransformation = if (showAbrpToken) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showAbrpToken = !showAbrpToken }) {
                                        Icon(
                                            imageVector = if (showAbrpToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = if (showAbrpToken) "Hide token" else "Show token"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = intervalInput,
                                onValueChange = { intervalInput = it.filter(Char::isDigit).take(3) },
                                label = { Text("Upload interval (seconds)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        val interval = intervalInput.toIntOrNull()?.coerceIn(5, 120) ?: 5
                                        settings = settings.copy(
                                            enabled = enabled,
                                            userToken = tokenInput,
                                            apiKey = settings.apiKey.ifBlank { AbrpConnectionStore.DEFAULT_PUBLIC_API_KEY },
                                            uploadIntervalSeconds = interval
                                        )
                                        AbrpConnectionStore.save(context, settings)
                                        lastSavedAt = System.currentTimeMillis()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                                ) {
                                    Text("Save")
                                }
                                Button(
                                    onClick = {
                                        val telemetry = liveTelemetry
                                        if (telemetry == null) {
                                            testResult = if (viewModel.serviceConnected.value) {
                                                "Telemetry not ready yet - wait a second and try again"
                                            } else {
                                                "Telemetry service not connected yet"
                                            }
                                            return@Button
                                        }
                                        val current = AbrpConnectionStore.load(context).copy(
                                            enabled = enabled,
                                            userToken = tokenInput,
                                            apiKey = settings.apiKey.ifBlank { AbrpConnectionStore.DEFAULT_PUBLIC_API_KEY },
                                            uploadIntervalSeconds = intervalInput.toIntOrNull()?.coerceIn(5, 120) ?: 5
                                        )
                                        AbrpConnectionStore.save(context, current)
                                        screenScope.launch(Dispatchers.IO) {
                                            val (ok, status) = abrpManager.testUpload(telemetry, selectedCarConfig, current)
                                            launch(Dispatchers.Main) {
                                                testResult = if (ok) "Test upload succeeded" else status
                                                settings = AbrpConnectionStore.load(context)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                                ) {
                                    Text("Test & Save")
                                }
                                OutlinedButton(onClick = {
                                    reloadAbrpDraft()
                                }) {
                                    Text("Reload")
                                }
                            }
                        }
                    }
                }
                ConnectionStatusCard(
                    title = "ABRP status",
                    content = {
                        SettingsDetailRow("Configured", if (settings.userToken.isBlank()) "No" else "Yes")
                        SettingsDetailRow("Enabled", if (settings.enabled) "Yes" else "No")
                        SettingsDetailRow("Last upload", if (settings.lastUploadAtMs > 0L) {
                            formatFriendlyTimestamp(settings.lastUploadAtMs)
                        } else {
                            "n/a"
                        })
                        SettingsDetailRow("Last status", settings.lastStatus)
                        if (!testResult.isNullOrBlank()) {
                            Text(
                                testResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (testResult!!.contains("succeeded", ignoreCase = true)) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "MQTT",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Publish live telemetry to an external broker such as HiveMQ.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable MQTT", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                if (mqttEnabled) "Publishes the live telemetry JSON every $mqttIntervalInput s."
                                else "Turn on to configure MQTT publishing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = mqttEnabled,
                            onCheckedChange = { next ->
                                mqttEnabled = next
                                if (!next) {
                                    val interval = mqttIntervalInput.toIntOrNull()?.coerceIn(1, 120) ?: 1
                                    val port = mqttPortInput.toIntOrNull()?.coerceIn(1, 65535) ?: 1883
                                    mqttSettings = mqttSettings.copy(
                                        enabled = false,
                                        brokerUrl = mqttBrokerInput,
                                        brokerPort = port,
                                        username = mqttUsernameInput,
                                        password = mqttPasswordInput,
                                        friendlyName = mqttFriendlyNameInput,
                                        publishIntervalSeconds = interval
                                    )
                                    MqttConnectionStore.save(context, mqttSettings)
                                    mqttResult = "MQTT disabled and saved"
                                }
                            },
                            thumbContent = if (!mqttEnabled) {
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
                    AnimatedVisibility(visible = mqttEnabled, enter = expandVertically(), exit = shrinkVertically()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsGroupLabel("Connection")
                            OutlinedTextField(
                                value = mqttBrokerInput,
                                onValueChange = { mqttBrokerInput = it.trim() },
                                label = { Text("Broker URL") },
                                placeholder = { Text("example.hivemq.cloud") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = mqttPortInput,
                                onValueChange = { mqttPortInput = it.filter(Char::isDigit).take(5) },
                                label = { Text("Port") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Use TLS",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "Encrypted connection (mqtts / wss). Typically port 8883.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (mqttPortInput == "8883" && !mqttUseTls) {
                                        Text(
                                            "Port 8883 normally requires TLS — without it the connection is unencrypted and will likely fail.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                Switch(
                                    checked = mqttUseTls,
                                    onCheckedChange = { mqttUseTls = it },
                                    thumbContent = if (!mqttUseTls) {
                                        { Box(modifier = Modifier.size(12.dp).background(ToggleUncheckedTrack, CircleShape)) }
                                    } else null,
                                    colors = SwitchDefaults.colors(
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = ToggleUncheckedTrack,
                                        uncheckedBorderColor = ToggleUncheckedTrack
                                    )
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Use WebSocket",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "MQTT over WebSocket so the broker can sit behind an HTTP reverse proxy.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = mqttUseWebSocket,
                                    onCheckedChange = { mqttUseWebSocket = it },
                                    thumbContent = if (!mqttUseWebSocket) {
                                        { Box(modifier = Modifier.size(12.dp).background(ToggleUncheckedTrack, CircleShape)) }
                                    } else null,
                                    colors = SwitchDefaults.colors(
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = ToggleUncheckedTrack,
                                        uncheckedBorderColor = ToggleUncheckedTrack
                                    )
                                )
                            }
                            AnimatedVisibility(visible = mqttUseWebSocket, enter = expandVertically(), exit = shrinkVertically()) {
                                OutlinedTextField(
                                    value = mqttWsPathInput,
                                    onValueChange = { mqttWsPathInput = it.trim() },
                                    label = { Text("WebSocket path") },
                                    placeholder = { Text("/mqtt") },
                                    singleLine = true,
                                    supportingText = {
                                        val scheme = if (mqttUseTls) "wss" else "ws"
                                        val host = mqttBrokerInput.ifBlank { "broker" }
                                        val portTxt = mqttPortInput.ifBlank { "<port>" }
                                        val path = mqttWsPathInput.ifBlank { "/mqtt" }.let { if (it.startsWith("/")) it else "/$it" }
                                        Text("$scheme://$host:$portTxt$path", style = MaterialTheme.typography.bodySmall)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            OutlinedTextField(
                                value = mqttFriendlyNameInput,
                                onValueChange = { mqttFriendlyNameInput = it.trim() },
                                label = { Text("Device friendly name") },
                                placeholder = { Text("my-byd-seal") },
                                singleLine = true,
                                supportingText = {
                                    val preview = mqttFriendlyNameInput.replace("[^a-zA-Z0-9_-]".toRegex(), "_").ifBlank { "<android-id>" }
                                    Text("Topic: byd-trip-stats/$preview/state", style = MaterialTheme.typography.bodySmall)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = mqttUsernameInput,
                                onValueChange = { mqttUsernameInput = it },
                                label = { Text("Username") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = mqttPasswordInput,
                                onValueChange = { mqttPasswordInput = it },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = mqttIntervalInput,
                                onValueChange = { mqttIntervalInput = it.filter(Char::isDigit).take(3) },
                                label = { Text("Publish interval (seconds)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText = { Text("Driving: every ${mqttIntervalInput.ifBlank { "1" }} s  ·  Charging / idle while running: every 30 s", style = MaterialTheme.typography.bodySmall) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "While parked, publishing depends on the Background activity mode " +
                                    "and your broker. In Always On the service keeps running, but most " +
                                    "BYD units cut WiFi ~15 min after the car is off — so a broker on " +
                                    "your home network stops receiving until the car powers on, while " +
                                    "an internet-reachable broker (e.g. HiveMQ Cloud) usually keeps " +
                                    "getting data over mobile data. Minimal publishes only during the " +
                                    "~90-min wake blips, and Deep Sleep not at all.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        val interval = mqttIntervalInput.toIntOrNull()?.coerceIn(1, 120) ?: 1
                                        val port = mqttPortInput.toIntOrNull()?.coerceIn(1, 65535) ?: 1883
                                        mqttSettings = mqttSettings.copy(
                                            enabled = mqttEnabled,
                                            brokerUrl = mqttBrokerInput,
                                            brokerPort = port,
                                            username = mqttUsernameInput,
                                            password = mqttPasswordInput,
                                            friendlyName = mqttFriendlyNameInput,
                                            publishIntervalSeconds = interval,
                                            useTls = mqttUseTls,
                                            useWebSocket = mqttUseWebSocket,
                                            webSocketPath = mqttWsPathInput
                                        )
                                        MqttConnectionStore.save(context, mqttSettings)
                                        mqttResult = "MQTT settings saved"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                                ) {
                                    Text("Save")
                                }
                                Button(
                                    enabled = !mqttTesting,
                                    onClick = {
                                        val telemetry = liveTelemetry
                                        if (telemetry == null) {
                                            mqttResult = "No live telemetry yet"
                                            return@Button
                                        }
                                        val interval = mqttIntervalInput.toIntOrNull()?.coerceIn(1, 120) ?: 1
                                        val port = mqttPortInput.toIntOrNull()?.coerceIn(1, 65535) ?: 1883
                                        val current = MqttConnectionStore.load(context).copy(
                                            enabled = mqttEnabled,
                                            brokerUrl = mqttBrokerInput,
                                            brokerPort = port,
                                            username = mqttUsernameInput,
                                            password = mqttPasswordInput,
                                            friendlyName = mqttFriendlyNameInput,
                                            publishIntervalSeconds = interval,
                                            useTls = mqttUseTls,
                                            useWebSocket = mqttUseWebSocket,
                                            webSocketPath = mqttWsPathInput
                                        )
                                        MqttConnectionStore.save(context, current)
                                        mqttTesting = true
                                        mqttResult = null
                                        screenScope.launch {
                                            try {
                                                val (ok, status) = withContext(Dispatchers.IO) {
                                                    mqttManager.testPublish(telemetry)
                                                }
                                                mqttResult = if (ok) "MQTT test publish succeeded" else status
                                                mqttSettings = MqttConnectionStore.load(context)
                                            } catch (e: Exception) {
                                                mqttResult = "MQTT test failed: ${e.message}"
                                            } finally {
                                                mqttTesting = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                                ) {
                                    Text(if (mqttTesting) "Testing…" else "Test & Save")
                                }
                                OutlinedButton(onClick = {
                                    reloadMqttDraft()
                                }) {
                                    Text("Reload")
                                }
                            }
                        }
                    }
                }
                ConnectionStatusCard(
                    title = "MQTT status",
                    content = {
                        SettingsDetailRow("Configured", if (mqttSettings.brokerUrl.isBlank()) "No" else "Yes")
                        SettingsDetailRow("Enabled", if (mqttSettings.enabled) "Yes" else "No")
                        SettingsDetailRow("Last publish", if (mqttSettings.lastPublishAtMs > 0L) {
                            formatFriendlyTimestamp(mqttSettings.lastPublishAtMs)
                        } else {
                            "n/a"
                        })
                        SettingsDetailRow("Last status", mqttSettings.lastStatus)
                        if (!mqttResult.isNullOrBlank()) {
                            Text(
                                mqttResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (mqttResult!!.contains("saved", ignoreCase = true) ||
                                    mqttResult!!.contains("succeeded", ignoreCase = true)) {
                                    RegenGreen
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ConnectionSummaryCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    body: String,
    statusLine: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                statusLine,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}
