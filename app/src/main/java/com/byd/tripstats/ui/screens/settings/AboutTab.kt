package com.byd.tripstats.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.byd.tripstats.ui.theme.AccelerationOrange
import com.byd.tripstats.ui.theme.BydElectricAzure
import com.byd.tripstats.ui.theme.RegenGreen
import com.byd.tripstats.ui.viewmodel.DashboardViewModel

@Composable
internal fun AboutTab(viewModel: DashboardViewModel) {
    val updateInfo        by viewModel.updateInfo.collectAsState()
    val downloadProgress  by viewModel.downloadProgress.collectAsState()
    val downloadedApk     by viewModel.downloadedApk.collectAsState()
    val canInstallNow     by viewModel.canInstallNow.collectAsState()
    val isCheckingUpdate  by viewModel.isCheckingUpdate.collectAsState()

    var easterEggClicks by remember { mutableStateOf(0) }
    var licenseClicks by remember { mutableStateOf(0) }
    var showChangelogDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showChangelogDialog) {
        val changelogText = remember {
            runCatching {
                context.assets.open("changelog.md").bufferedReader().readText()
            }.getOrDefault("Changelog not available.")
        }
        AlertDialog(
            onDismissRequest = { showChangelogDialog = false },
            containerColor   = MaterialTheme.colorScheme.surfaceVariant,
            title = {
                Text("Changelog", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text  = changelogText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showChangelogDialog = false }) { Text("Close") }
            }
        )
    }

    val hasBackgroundLocation = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(icon = Icons.Filled.Info, title = "About")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsDetailRow("App",       "BYD Trip Stats")
                SettingsDetailRow("Version",   com.byd.tripstats.BuildConfig.VERSION_NAME)
                SettingsDetailRow("Changelog", "What's new", onClick = { showChangelogDialog = true }, showClickIndicator = true)
                SettingsDetailRow("Author",    "Angelos Oikonomou (angoikon)")
                SettingsDetailRow(
                    label = "Platform",
                    value = "Android 10 · API 29",
                    onClick = {
                        easterEggClicks++
                        if (easterEggClicks >= 5) {
                            easterEggClicks = 0
                            try {
                                val intent = Intent().apply {
                                    setClassName("com.byd.countrycodetool", "com.byd.countrycodetool.MainActivity")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    Runtime.getRuntime().exec(arrayOf("am", "start", "-n", "com.byd.countrycodetool/com.byd.countrycodetool.MainActivity"))
                                } catch (e2: Exception) {
                                    android.widget.Toast.makeText(context, "Could not open tool", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
                SettingsDetailRow(
                    label = "License",
                    value = "BUSL 1.1",
                    onClick = {
                        licenseClicks++
                        if (licenseClicks >= 5) {
                            licenseClicks = 0
                            try {
                                val intent = Intent().apply {
                                    setClassName("com.byd.byddevelopmenttools", "com.byd.byddevelopmenttools.VerificationActivity")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    Runtime.getRuntime().exec(arrayOf("am", "start", "-n", "com.byd.byddevelopmenttools/com.byd.byddevelopmenttools.VerificationActivity"))
                                } catch (e2: Exception) {
                                    android.widget.Toast.makeText(context, "Could not open tool", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
                SettingsDetailRow("Site",      "https://byd-trip-stats.angoikon.workers.dev",
                    url = "https://byd-trip-stats.angoikon.workers.dev/")
                SettingsDetailRow("Github",      "github.com/angoikon/byd-trip-stats",
                    url = "https://github.com/angoikon/byd-trip-stats")
                SettingsDetailRow("Discord", "Join now", url = "https://discord.gg/pf8TjjTce9")
                if (!hasBackgroundLocation) {
                    SettingsDetailRow(
                        label = "Background operation",
                        value = "⚠️ ADB setup required — open app to trigger setup dialog"
                    )
                }
            }
        }

        UpdateCard(
            updateInfo       = updateInfo,
            downloadProgress = downloadProgress,
            downloadedApk    = downloadedApk,
            canInstallNow    = canInstallNow,
            isChecking       = isCheckingUpdate,
            onDownload       = { viewModel.downloadUpdate() },
            onInstall        = { viewModel.installUpdate() },
            onCancel         = { viewModel.cancelDownload() },
            onCheckNow       = { viewModel.checkForUpdateManually() }
        )

        HorizontalDivider()
        SectionHeader(icon = Icons.AutoMirrored.Filled.Help, title = "FAQ")

        buildFaqList().forEach { (q, a, u) -> FaqItem(question = q, answer = a, url = u) }

        Spacer(Modifier.height(8.dp))
    }
}

// ── Update card ───────────────────────────────────────────────────────────────

@Composable
private fun UpdateCard(
    updateInfo      : com.byd.tripstats.data.repository.UpdateRepository.UpdateInfo?,
    downloadProgress: Int?,
    downloadedApk   : java.io.File?,
    canInstallNow   : Boolean,
    isChecking      : Boolean = false,
    onDownload      : () -> Unit,
    onInstall       : () -> Unit,
    onCancel        : () -> Unit,
    onCheckNow      : () -> Unit = {}
) {
    val isDownloading = downloadProgress != null && downloadProgress in 0..99
    val isReady       = downloadedApk != null && downloadProgress == 100

    if (updateInfo == null && !isDownloading && !isReady) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier              = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.CheckCircle, null, tint = RegenGreen, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        if (isChecking) "Checking for updates…" else "App is up to date",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (!isChecking) {
                    TextButton(onClick = onCheckNow, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("Check now", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = when {
                isReady            -> RegenGreen.copy(alpha = 0.15f)
                updateInfo != null -> AccelerationOrange.copy(alpha = 0.12f)
                else               -> MaterialTheme.colorScheme.primaryContainer
            }
        ),
        border = BorderStroke(
            1.dp,
            when {
                isReady            -> RegenGreen.copy(alpha = 0.5f)
                updateInfo != null -> AccelerationOrange.copy(alpha = 0.4f)
                else               -> MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        isReady       -> Icons.Filled.SystemUpdate
                        isDownloading -> Icons.Filled.Downloading
                        else          -> Icons.Filled.NewReleases
                    },
                    contentDescription = null,
                    tint     = when {
                        isReady       -> RegenGreen
                        isDownloading -> BydElectricAzure
                        else          -> AccelerationOrange
                    },
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        when {
                            isReady       -> "v${updateInfo?.latestVersion} ready to install"
                            isDownloading -> "Downloading v${updateInfo?.latestVersion}…"
                            else          -> "Update available: v${updateInfo?.latestVersion}"
                        },
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (updateInfo != null && !isDownloading && !isReady) {
                        Text(
                            "Current: v${updateInfo.currentVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isDownloading) {
                val animatedProgress by animateFloatAsState(
                    targetValue   = (downloadProgress ?: 0) / 100f,
                    animationSpec = tween(300),
                    label         = "updateProgress"
                )
                LinearProgressIndicator(
                    progress   = { animatedProgress },
                    modifier   = Modifier.fillMaxWidth(),
                    color      = BydElectricAzure,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                Text(
                    "${downloadProgress}%  ·  ${updateInfo?.apkName ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (updateInfo != null && !isDownloading && !isReady && updateInfo.releaseNotes.isNotBlank()) {
                Text(
                    updateInfo.releaseNotes,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isReady && !canInstallNow) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Info, null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp))
                    Text(
                        "Install button will become available when parked with no active trip or charging",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    isReady -> {
                        Button(
                            onClick  = onInstall,
                            enabled  = canInstallNow,
                            colors   = ButtonDefaults.buttonColors(containerColor = RegenGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.InstallMobile, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Install now")
                        }
                    }
                    isDownloading -> {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                            Text("Cancel")
                        }
                    }
                    else -> {
                        Button(
                            onClick  = onDownload,
                            colors   = ButtonDefaults.buttonColors(containerColor = BydElectricAzure),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download update")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String, url: String? = null) {
    val context  = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                MaterialTheme.shapes.medium
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    question,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.weight(1f).padding(end = 8.dp)
                )
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))
                    Text(answer, style = MaterialTheme.typography.bodyMedium)
                    if (url != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .clickable {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    )
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector        = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open link",
                                tint               = MaterialTheme.colorScheme.primary,
                                modifier           = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                url.removePrefix("https://").removePrefix("http://"),
                                style      = MaterialTheme.typography.bodySmall,
                                color      = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class FaqEntry(val question: String, val answer: String, val url: String? = null)

private fun buildFaqList(): List<FaqEntry> = listOf(

    FaqEntry(
        "Contrary to older versions, is it true that I no longer need the Electro app?",
        "Correct. BYD Trip Stats works as a standalone app on supported vehicles, so " +
        "trip recording and charging sessions work without Electro.\n\n" +
        "Electro is still a useful companion app for owners who want its own " +
        "features, but it is no longer required for BYD Trip Stats to function."
    ),

    FaqEntry(
        "Do I need internet or mobile signal?",
        "No for normal use. Trips, charging sessions, charts, and local history are recorded " +
        "entirely on-device.\n\n" +
        "Internet is only needed for optional features such as app update checks, opening web " +
        "links, MQTT / ABRP integration and Telegram backups if you enable them."
    ),

    FaqEntry(
        "What is BYD Trip Stats Pro and what does it include?",
        "Pro is an optional one-time unlock for premium extras — a small thank-you that helps " +
        "support development. It's €9.99, paid once, for the lifetime of one vehicle (no " +
        "subscription). Everything that was free stays free; Pro only adds new features:\n\n" +
        "• Cell imbalance alert — get notified when the pack's cell-voltage spread stays above a limit\n" +
        "• Battery health report — export a printable PDF/HTML of your State of Health, decline rate " +
        "and projected date to 80% (handy evidence for resale or a warranty claim)\n" +
        "• Dashboard screenshots — tap the BYD logo to save the current screen\n" +
        "• SD card backup — save the database to a BydTripStats folder on a removable SD card, which " +
        "survives an app uninstall\n\n" +
        "Unlocking: the code is tied to your specific car. Open Settings → Preferences → BYD Trip " +
        "Stats Pro, scan the QR code with your phone (or copy your Vehicle ID) to request a code, then " +
        "paste it back in. It's verified entirely on-device — no account, no sign-in, nothing leaves " +
        "your car — and because it's tied to your vehicle, it only unlocks that car."
    ),

    FaqEntry(
        "Why is 'auto/unknown' shown at driving / regen modes?",
        "The app reads drive and regen modes directly from the car's instrument cluster, but the " +
        "car only broadcasts a mode value when you actively change it — it does not report the " +
        "current mode on startup.\n\n" +
        "Until you tap a mode on the car display, the app has no value to show and falls back to " +
        "'Auto' (regen) or 'Unknown' (drive). To fix this:\n\n" +
        "1. Cycle through each regen level on the car's touchscreen once\n" +
        "2. Cycle through each drive mode (Eco / Normal / Sport) once\n\n" +
        "The app will immediately pick up both values and start recording them with every trip data point. " +
        "You only need to do this once — after that, changing modes during normal driving keeps everything in sync."
    ),

    FaqEntry(
        "No network connectivity means trip will be missing from the history?",
        "No. Trip history is stored locally and does not depend on mobile signal or Wi-Fi.\n\n" +
        "A trip may close a little later only if live telemetry itself stops for a while. In that " +
        "case the watchdog closes the active trip after 3 minutes of telemetry silence, anchored " +
        "to the last packet the app received."
    ),

    FaqEntry(
        "The app stops working after a car restart",
        "You need to exclude BYD Trip Stats from the autostart-killer:\n\n" +
        "1. Open the Disable Autostart app (native BYD app, usually near the file explorer)\n" +
        "2. Find BYD Trip Stats and toggle its entry OFF — same as you have done for Electro\n" +
        "3. Open the app and hold the volume button for ~10 seconds to restart the car UI\n" +
        "4. Reopen the app\n\n" +
        "Important: you must repeat this step after every app update, because the permission " +
        "resets on reinstall."
    ),

    FaqEntry(
        "How do I back up my trip data?",
        "Several methods are available — all produce the same standard SQLite file:\n\n" +
        "Local (simplest — no setup): Settings → Data → Open Backup & Restore → Backup Now. " +
        "The file lands in Download/BydTripStats/ on the car's internal storage.\n\n" +
        "SD card (Pro): Backup & Restore → Backup to SD card. Saves to a BydTripStats folder on a " +
        "removable SD card; unlike the Download copy, these survive an app uninstall and can be " +
        "pulled out and read on a computer.\n\n" +
        "Telegram (recommended): Connect a bot once via Backup & Restore → Telegram Backup, " +
        "then send manually or enable scheduled backups (daily / weekly / monthly). " +
        "Backups appear in your private Telegram chat and can be restored directly from any device.\n\n" +
        "ADB (technical): Every in-app backup also writes a copy to the app's private directory. " +
        "Pull it wirelessly with:\n" +
        "adb shell run-as com.byd.tripstats cat files/db_backup/FILENAME.db > local.db\n\n" +
        "The private directory keeps the 5 most recent backups and prunes older ones automatically."
    ),

    FaqEntry(
        "I reinstalled the app — can I recover my backups?",
        "Yes, for both backup methods:\n\n" +
        "Telegram: After reinstalling, reconnect your bot token in Backup & Restore → " +
        "Telegram Backup. Then open Restore from Telegram and tap the refresh icon. " +
        "The app reads Download/BydTripStats/telegram_registry.json — which survives " +
        "uninstalls — and rediscovers all previous backups automatically.\n\n" +
        "Local: The .db files in Download/BydTripStats/ survive a reinstall. Open " +
        "Backup & Restore, tap the refresh icon under Local Restore, and they reappear " +
        "in the list. The app now reads that folder directly from disk for exactly this case.\n\n" +
        "As long as Download/BydTripStats/ has not been manually deleted, no data is lost."
    ),

    FaqEntry(
        "How does Telegram automatic backup work?",
        "Once your bot is connected, enable the Auto-backup toggle and pick an interval: " +
        "Daily, Weekly, or Monthly.\n\n" +
        "Key behaviours:\n" +
        "• The first automatic backup runs after one full interval has elapsed — enabling " +
        "the toggle does not send immediately\n" +
        "• Changing the interval reschedules from that moment forward\n" +
        "• If the car is off when a backup is due, it fires automatically on the next boot\n" +
        "• Failed attempts are retried with exponential backoff before giving up until the next window\n\n" +
        "The 'Last auto-backup' timestamp in the Telegram section confirms when the most recent run completed.\n\n" +
        "To stop: tap Disconnect bot. This cancels the schedule and clears saved credentials. " +
        "Your previous backups in Telegram and the registry file in Download are unaffected. Backups up to 50MB of database per file"
    ),

    FaqEntry(
        "How do I restore a backup pushed from my PC via ADB?",
        "1. Push the file to the car's Download folder:\n" +
        "   adb push backup.db /sdcard/Download/BydTripStats/backup.db\n\n" +
        "2. Open the app → Backup & Restore → Local Restore\n" +
        "3. Tap the refresh icon — the pushed file appears in the list\n" +
        "4. Tap Restore and confirm\n\n" +
        "The app validates the file as a genuine SQLite database before touching any live data. " +
        "It then restores and restarts automatically."
    ),

    FaqEntry(
        "Will this drain my 12V battery?",
        "No meaningful impact. In an EV the 12V battery is continuously trickle-charged from " +
        "the high-voltage pack. BYD Trip Stats only keeps a lightweight foreground service alive " +
        "to read telemetry and write local trip data. If the car is offline, it is used for recording " +
        "charging sessions and 12V/HV curves - you can also disable this feature if you don't want it"
    ),

    FaqEntry(
        "Can I export data to Excel?",
        "Yes. Open any trip, tap the share icon (top-right), then Save as CSV. The file lands " +
        "in the Download folder and can be opened directly in Excel, Google Sheets, or any " +
        "data tool.\n\nA JSON export is also available from the same menu. Both formats include " +
        "every recorded data point: timestamp, GPS, speed, power, SOC, altitude, battery " +
        "temperature, gear, and motor RPM (front and rear)."
    ),

    FaqEntry(
        "Is my data private?",
        "Completely. BYD Trip Stats collects zero information about you.\n\n" +
        "Everything stays on your device: trip data, GPS coordinates, backups, and settings. " +
        "The only optional external traffic is what you explicitly trigger, such as Telegram " +
        "backup uploads, checking for a newer app release, enabling MQTT integration and sending debug probes."
    ),

    FaqEntry(
        "Where can I get help, request a feature or report a bug?",
        "Check the README.md file at my Github, open an issue there or visit the discord server — tap the link below.\n\n" +
        "Don't forget to include your BYD model and the steps to reproduce the problem. ",
        url = "https://discord.gg/pf8TjjTce9"
    )
)
