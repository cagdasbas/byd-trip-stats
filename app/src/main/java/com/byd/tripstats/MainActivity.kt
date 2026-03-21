package com.byd.tripstats

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.byd.tripstats.BuildConfig
import com.byd.tripstats.data.preferences.PreferencesManager
import com.byd.tripstats.service.MqttService
import com.byd.tripstats.ui.navigation.AppNavigation
import com.byd.tripstats.ui.screens.InitializationScreen
import com.byd.tripstats.ui.theme.BydTripStatsTheme
import com.byd.tripstats.ui.viewmodel.DashboardViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"

    private val viewModel: DashboardViewModel by viewModels()
    private var mqttService: MqttService? = null
    private var bound = false

    // Shown once per app version update to remind the user to re-enable Autostart
    private val showAutostartReminder = mutableStateOf(false)

    // ── Service binding ───────────────────────────────────────────────────────

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MqttService.LocalBinder
            mqttService = binder.getService()
            bound = true
            Log.i(TAG, "MqttService connected")
            mqttService?.let { viewModel.observeMqttServiceState(it) }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            bound = false
            mqttService = null
            Log.w(TAG, "MqttService disconnected unexpectedly")
        }
    }

    // ── Permission launcher ───────────────────────────────────────────────────

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.entries.all { it.value }
        Log.d(TAG, "Permissions result — all granted: $allGranted")
        if (allGranted) bindToMqttService()
        else Log.w(TAG, "Some permissions were denied")
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestRequiredPermissions()
        checkAndShowAutostartReminder()

        setContent {
            BydTripStatsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val prefs = remember { PreferencesManager(applicationContext) }
                    val selectedCarId by prefs.selectedCarId.collectAsState(initial = null)
                    val mqttSettings by prefs.mqttSettings.collectAsState(initial = null)

                    // Autostart reminder dialog — shown once after each version update
                    if (showAutostartReminder.value) {
                        AlertDialog(
                            onDismissRequest = { dismissAutostartReminder() },
                            title = { Text("⚠️ Action Required — Autostart") },
                            text = {
                                Text(
                                    "A new version was installed. You need to toggle-off disable " +
                                    "autostart for this app, which enables background data collection " +
                                    "when the car is off (e.g. charging overnight).\n\n" +
                                    "After that action, you need to reboot the car and re-open the app " +
                                    "for changes to be in effect."
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = { dismissAutostartReminder() }) {
                                    Text("Got it")
                                }
                            }
                        )
                    }

                    // Wait for DataStore to emit before showing anything
                    if (selectedCarId == null && mqttSettings != null) {
                        InitializationScreen(
                            initialTopic = mqttSettings!!.topic,
                            onContinue = { car, topic ->
                                prefs.saveInitialSetup(car.id, topic)
                            }
                        )
                    } else if (selectedCarId == null) {
                        // DataStore hasn't emitted yet — show nothing or a spinner
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val navController = rememberNavController()
                        AppNavigation(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }

    private fun checkAndShowAutostartReminder() {
        val prefs = PreferencesManager(applicationContext)
        val currentVersion = BuildConfig.VERSION_CODE
        lifecycleScope.launch {
            val lastSeen = prefs.getLastSeenVersionCode()
            // Show dialog on upgrade (not on first install — lastSeen == 0 means no version stored)
            if (lastSeen > 0 && lastSeen < currentVersion) {
                showAutostartReminder.value = true
            }
            // Always persist the current version so we only show once per upgrade
            prefs.saveLastSeenVersionCode(currentVersion)
        }
    }

    private fun dismissAutostartReminder() {
        showAutostartReminder.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            unbindService(connection)
            bound = false
        }
        // Do NOT stop the service — it must keep running in the background
        // for auto trip detection and MQTT telemetry collection.
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    private fun requestRequiredPermissions() {
        val required = buildList {
            // POST_NOTIFICATIONS required on Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            // READ_EXTERNAL_STORAGE required on Android 10–12 to query MediaStore
            // entries not owned by the current app install (e.g. after a reinstall).
            // Superseded by READ_MEDIA_* on Android 13+ where it has no effect.
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            bindToMqttService()
        }
    }

    // ── Service binding ───────────────────────────────────────────────────────

    /**
     * Binds to MqttService. The service is started by [BydStatsApplication] on
     * every process start, so it should already be running by the time the
     * Activity reaches this point. [Context.BIND_AUTO_CREATE] ensures it is
     * started if somehow it is not yet running (e.g. during first-ever launch
     * before Application.onCreate has finished the 6-second broker-init delay).
     *
     * We no longer use the deprecated [android.app.ActivityManager.getRunningServices]
     * to detect whether the service is running — that API is unreliable on
     * modern Android and was removed from the call path entirely.
     */
    private fun bindToMqttService() {
        Log.d(TAG, "Binding to MqttService")
        val intent = Intent(this, MqttService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

}