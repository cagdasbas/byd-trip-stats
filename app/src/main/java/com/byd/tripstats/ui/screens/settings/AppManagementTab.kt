package com.byd.tripstats.ui.screens.settings

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.tripstats.ui.theme.BydErrorRed
import com.byd.tripstats.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun AppManagementTab(
    viewModel         : DashboardViewModel,
    context           : Context,
    onNavigateToBackup: () -> Unit,
    scope             : CoroutineScope
) {
    val lastBackup     = remember { viewModel.listDatabaseBackups().firstOrNull() }
    val lastBackupLabel = remember(lastBackup) {
        if (lastBackup == null) "No local backup found"
        else {
            val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
            "Last backup: ${sdf.format(java.util.Date(lastBackup.lastModified()))}"
        }
    }

    var showResetConfirm by remember { mutableStateOf(false) }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(icon = Icons.Filled.Backup, title = "Backup & Restore")

        // Two-column summary row
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BackupSummaryCard(
                modifier   = Modifier.weight(1f),
                icon       = Icons.Filled.Storage,
                title      = "Local",
                body       = "Download/BydTripStats/",
                statusLine = lastBackupLabel,
                statusOk   = lastBackup != null
            )
            BackupSummaryCard(
                modifier   = Modifier.weight(1f),
                icon       = Icons.AutoMirrored.Filled.Send,
                title      = "Telegram",
                body       = "Private bot chat",
                statusLine = "Manual & scheduled",
                statusOk   = true
            )
        }

        Button(onClick = onNavigateToBackup, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Backup, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open Backup & Restore", fontSize = 16.sp)
        }

        HorizontalDivider()

        VehicleCompatibilitySection(context = context, scope = scope)

        HorizontalDivider()

        AppDiagnosticsCard()

        HorizontalDivider()

        WebCompanionSection(context = context, scope = scope)
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            icon  = { Icon(Icons.Filled.Warning, null,
                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp)) },
            title = { Text("Reset all trip data?", fontWeight = FontWeight.Bold) },
            text  = {
                Text(
                    "This will permanently delete all trips and statistics.\n\n" +
                    "A backup will be saved to Download automatically before the reset. " +
                    "The app will close and reopen.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        scope.launch {
                            viewModel.resetDatabase()
                            val launchIntent = context.packageManager
                                .getLaunchIntentForPackage(context.packageName)
                                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
                            if (launchIntent != null) {
                                val pending = PendingIntent.getActivity(
                                    context, 1, launchIntent,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                                )
                                val alarm = context.getSystemService(AlarmManager::class.java)
                                alarm.set(AlarmManager.RTC, System.currentTimeMillis() + 800L, pending)
                            }
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BydErrorRed)
                ) { Text("Yes, reset everything") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
