package com.byd.tripstats.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.byd.tripstats.util.McuWakeHelper
import kotlinx.coroutines.flow.collectLatest
import com.byd.tripstats.MainActivity
import com.byd.tripstats.R
import com.byd.tripstats.data.config.CarConfig
import com.byd.tripstats.data.model.VehicleTelemetry
import com.byd.tripstats.data.preferences.PreferencesManager
import com.byd.tripstats.data.repository.BatteryVoltageHistoryRepository
import com.byd.tripstats.data.repository.ChargingRepository
import com.byd.tripstats.data.repository.TripRepository
import com.byd.tripstats.connections.AbrpConnectionManager
import com.byd.tripstats.connections.MqttConnectionManager
import com.byd.tripstats.receiver.ServiceRestartReceiver
import com.byd.tripstats.sdk.BydVehicleDataSource
import com.byd.tripstats.sdk.VehicleCompatibilityProbe
import com.byd.tripstats.sdk.VehicleTelemetrySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Background service that acquires vehicle telemetry from the car at a
 * 1-second interval.
 *
 * Runs as a foreground service with type dataSync|location. The location type
 * is actively satisfied by a real LocationManager subscription so the OS sets
 * oom_adj=PERCEPTIBLE_APP_ADJ (125) instead of SERVICE_ADJ (200). At adj=125
 * the process survives all but the most extreme memory pressure.
 */
class VehicleTelemetryService : Service() {

    private val TAG = "VehicleTelemetrySvc"
    private val CHANNEL_ID = "vehicle_telemetry_service_channel"
    private val NOTIFICATION_ID = 1

    private val binder = LocalBinder()

    private lateinit var vehicleDataSource: BydVehicleDataSource
    val telemetrySnapshot: StateFlow<VehicleTelemetrySnapshot>
        get() = vehicleDataSource.vehicleSnapshot

    fun refreshVehicleSnapshot() {
        vehicleDataSource.refreshSnapshots()
    }

    companion object {
        @Volatile
        private var stopRequested = false

        fun start(context: Context) {
            stopRequested = false
            context.startForegroundService(Intent(context, VehicleTelemetryService::class.java))
        }

        fun stop(context: Context) {
            stopRequested = true
            context.stopService(Intent(context, VehicleTelemetryService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var telemetryLoopJob: Job? = null

    private var tripRepository: TripRepository? = null
    private var chargingRepository: ChargingRepository? = null
    private var batteryVoltageHistoryRepository: BatteryVoltageHistoryRepository? = null
    private var carConfig: CarConfig? = null
    private var abrpConnectionManager: AbrpConnectionManager? = null
    private var mqttConnectionManager: MqttConnectionManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var screenOffReceiver: android.content.BroadcastReceiver? = null
    private var mcuWakeJob: kotlinx.coroutines.Job? = null
    private var intentionalStop = false
    private var notificationStartedAtWallClock: Long = 0L
    @Volatile
    private var telemetryLoopActive = false
    @Volatile
    private var telemetryLoopStarting = false

    // ── Location subscription — satisfies foregroundServiceType=location ───────
    // The OS validates that a location-type foreground service actually uses
    // LocationManager. Without a real subscription, the service type is ignored
    // and oom_adj stays at SERVICE_ADJ (200) rather than PERCEPTIBLE_APP_ADJ (125).
    private var locationManager: LocationManager? = null
    // All LocationListener methods must be implemented on API 29 (Android 10).
    // Missing onProviderDisabled/onProviderEnabled causes AbstractMethodError crash.
    private val keepaliveLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // The app's telemetry source handles trip location; this subscription
            // only satisfies the OS foreground service type check.
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
    }

    // ── Connection state ───────────────────────────────────────────────────────
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting   : ConnectionState()
        object Connected    : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _telemetryCount = MutableStateFlow(0)
    val telemetryCount: StateFlow<Int> = _telemetryCount.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): VehicleTelemetryService = this@VehicleTelemetryService
    }

    override fun onBind(intent: Intent): IBinder = binder

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        notificationStartedAtWallClock = System.currentTimeMillis()
        createNotificationChannel()
        acquireWakeLock()
        acquireLocationSubscription()
        registerScreenOffReceiver()
        startMcuWakeLoop()
        vehicleDataSource = BydVehicleDataSource(this)
        vehicleDataSource.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (telemetryLoopActive || telemetryLoopStarting) {
            Log.d(TAG, "onStartCommand — telemetry loop already active, ignoring duplicate start")
            return START_STICKY
        }
        telemetryLoopStarting = true
        Log.d(TAG, "onStartCommand — starting telemetry loop")
        intentionalStop = stopRequested

        // Declare both dataSync and location foreground service types.
        // The location type is what drives the OS to assign oom_adj=125 (PERCEPTIBLE)
        // rather than adj=200 (SERVICE). The acquireLocationSubscription() call in
        // onCreate() ensures the OS can verify the location type is genuine.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            createNotification("Trip recording and data visualization"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            else
                0
        )
        _connectionState.value = ConnectionState.Connecting

        tripRepository = TripRepository.getInstance(applicationContext)
        chargingRepository = ChargingRepository.getInstance(applicationContext)
        batteryVoltageHistoryRepository = BatteryVoltageHistoryRepository.getInstance(applicationContext)
        abrpConnectionManager = AbrpConnectionManager(applicationContext)
        mqttConnectionManager = MqttConnectionManager(applicationContext)

        // Start telemetry loop IMMEDIATELY using the synchronous SharedPreferences
        // cache — do NOT wait for DataStore async emit. The notification already
        // shows "Starting…" so the user thinks the service is ready; if we wait
        // for DataStore (100–500 ms on DiLink) we miss the first telemetry packets
        // and auto-trip detection never fires on the drive after a fresh start.
        val prefs = PreferencesManager(applicationContext)
        carConfig = prefs.getCachedSelectedCarConfig()
        Log.d(TAG, "Car config (cached): ${carConfig?.displayName ?: "none"} — starting loop immediately")
        pushCarConfigToProbe(carConfig)
        startTelemetryLoop()

        // Also watch DataStore for car selection changes (user switches car model).
        // When it emits a different value, restart the loop with updated config.
        serviceScope.launch {
            try {
                prefs.selectedCarConfig
                    .distinctUntilChanged()
                    .collect { config ->
                        if (config?.id != carConfig?.id) {
                            carConfig = config
                            Log.d(TAG, "Car config changed to ${config?.displayName ?: "none"} — restarting loop")
                            pushCarConfigToProbe(config)
                            startTelemetryLoop()
                        } else {
                            // Same car — just update the reference in case object differs
                            carConfig = config
                        }
                    }
            } catch (t: Throwable) {
                Log.w(TAG, "Car config watcher error: ${t.message}")
            }
        }

        return START_STICKY
    }

    private fun pushCarConfigToProbe(config: CarConfig?) {
        VehicleCompatibilityProbe.setVehicleInfo(
            userModel            = config?.displayName,
            userModelId          = config?.id,
            isPhev               = config?.isPhev,
            drivetrain           = config?.drivetrain?.name,
            batteryKwh           = config?.batteryKwh,
            phevUsableBatteryKwh = config?.phevUsableBatteryKwh,
            wltpKm               = config?.wltpKm,
        )
    }

    override fun onDestroy() {
        Log.e(TAG, "Service destroyed — stopRequested=$stopRequested intentionalStop=$intentionalStop")
        telemetryLoopJob?.cancel()
        telemetryLoopActive = false
        telemetryLoopStarting = false
        releaseLocationSubscription()
        mcuWakeJob?.cancel()
        unregisterScreenOffReceiver()
        wakeLock?.release()
        wifiLock?.release()
        serviceScope.cancel()
        _connectionState.value = ConnectionState.Disconnected
        vehicleDataSource.stop()
        mqttConnectionManager?.shutdown()
        if (!stopRequested && !intentionalStop) {
            scheduleSelfRestart("onDestroy")
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "Task removed — scheduling vehicle telemetry restart")
        if (!stopRequested) {
            scheduleSelfRestart("onTaskRemoved")
        }
        super.onTaskRemoved(rootIntent)
    }

    // ── Location subscription ──────────────────────────────────────────────────

    /**
     * Registers a minimal LocationManager update to satisfy the OS requirement
     * that a foreground service with type=location actually uses location APIs.
     * Without this, the OS ignores the declared type and keeps oom_adj at
     * SERVICE_ADJ (200) rather than the more protected PERCEPTIBLE_APP_ADJ (125).
     *
     * Uses a 30-second interval with 0m displacement — low frequency, no battery
     * impact, but enough to keep the OS convinced the subscription is real.
     */
    private fun acquireLocationSubscription() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.w(TAG, "Location permission not granted — foreground service type=location may not be honoured by OS")
            return
        }

        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager = lm
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
            ).filter { provider ->
                runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false)
            }
            providers.forEach { provider ->
                runCatching {
                    lm.requestLocationUpdates(
                        provider,
                        30_000L,   // 30 s interval — enough to satisfy OS, minimal battery
                        0f,
                        keepaliveLocationListener,
                        Looper.getMainLooper()
                    )
                }.onFailure { Log.w(TAG, "requestLocationUpdates failed for $provider: ${it.message}") }
            }
            if (providers.isNotEmpty()) {
                Log.i(TAG, "Location subscription active on ${providers.joinToString()} — foreground type=location satisfied")
            }
        } catch (e: Exception) {
            Log.w(TAG, "acquireLocationSubscription failed: ${e.message}")
        }
    }

    private fun releaseLocationSubscription() {
        runCatching { locationManager?.removeUpdates(keepaliveLocationListener) }
        locationManager = null
    }

    // ── Telemetry loop ─────────────────────────────────────────────────────────

    private fun startTelemetryLoop() {
        telemetryLoopJob?.cancel()
        telemetryLoopActive = true
        telemetryLoopStarting = false
        telemetryLoopJob = serviceScope.launch {
            try {
                val carName = carConfig?.displayName ?: "BYD"
                Log.d(TAG, "Vehicle telemetry loop started for $carName")
                _connectionState.value = ConnectionState.Connected
                updateNotification("Trip recording and data visualization")

                // When the car is off, slow the poll interval to 30 s regardless of
                // whether a charging session is in progress. This keeps the CPU idle
                // between ticks instead of spinning at 1 Hz, reducing 12V drain
                // overnight and during long AC charging sessions. 30 s matches the
                // MQTT publish rate, so no data is lost. The service stays alive as
                // a foreground service throughout; the loop never stops.
                //
                // Delay comes BEFORE the work so the interval is based on the last
                // known state — avoids one unnecessary fast poll on ON→OFF transition.
                // null on first boot resolves to 0 ms (immediate first poll).
                var lastTelemetry: VehicleTelemetry? = null
                var lastLoopTime = SystemClock.elapsedRealtime()
                while (true) {
                    val msSinceLastEvent = SystemClock.elapsedRealtime() - vehicleDataSource.lastFeatureEventElapsedMs
                    val pollIntervalMs = when {
                        lastTelemetry == null -> 0L
                        lastTelemetry.isCarOn -> 1_000L
                        lastTelemetry.isCharging && lastTelemetry.chargingPower > 23.0 -> 1_000L
                        msSinceLastEvent < 60_000L -> 5_000L  // car awake (recent SDK event) but not driving
                        else -> 30_000L
                    }
                    delay(pollIntervalMs)

                    val now = SystemClock.elapsedRealtime()
                    val delta = now - lastLoopTime
                    lastLoopTime = now

                    if (pollIntervalMs > 0L && delta > pollIntervalMs * 2) {
                        Log.w(TAG, "⏱ Telemetry loop delayed: ${delta}ms (expected ~${pollIntervalMs}ms) — system may have suspended")
                        if (delta > 60_000L) {
                            Log.w(TAG, "⏱ Long suspension detected (${delta}ms) — forcing immediate snapshot refresh")
                            try { vehicleDataSource.refreshSnapshots() } catch (t: Throwable) { Log.w(TAG, "Force refresh after sleep failed: ${t.message}") }
                        }
                    }

                    try {
                        vehicleDataSource.refreshSnapshots()
                        val snapshot = vehicleDataSource.vehicleSnapshot.value
                        val telemetry = snapshot.toTelemetry(carConfig)
                        lastTelemetry = telemetry

                        batteryVoltageHistoryRepository?.onTelemetry(telemetry)
                        tripRepository?.processTelemetry(telemetry)
                        chargingRepository?.onTelemetry(telemetry, carConfig)
                        abrpConnectionManager?.onTelemetry(telemetry, carConfig, serviceScope)
                        mqttConnectionManager?.onTelemetry(telemetry, serviceScope)

                        _telemetryCount.value = _telemetryCount.value + 1
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error in telemetry loop tick", t)
                    }
                }
            } finally {
                telemetryLoopActive = false
                telemetryLoopStarting = false
            }
        }
    }

    // ── Notification helpers ───────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Vehicle Data Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains live vehicle connection"
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun createNotification(message: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val serviceWhen = notificationStartedAtWallClock.takeIf { it > 0L }
            ?: System.currentTimeMillis()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BYD Trip Stats")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            // Show a counting-up chronometer anchored to when the service started.
            // setWhen sets the base time; setUsesChronometer(true) replaces the static
            // timestamp with a live "1:23" elapsed counter. setShowWhen(true) is required
            // to force the time field visible — without it some firmware builds hide it.
            .setWhen(serviceWhen)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .build()
    }

    fun updateNotification(message: String) {
        val notification = createNotification(message)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun buildStatusText(@Suppress("UNUSED_PARAMETER") telemetry: VehicleTelemetry): String {
        return "Trip recording and data visualization"
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BydStats::VehicleTelemetryWakeLock"
        ).apply { acquire() }
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wifiManager?.createWifiLock(
                WifiManager.WIFI_MODE_FULL,
                "BydStats::VehicleTelemetryWifiLock"
            )?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to acquire Wi-Fi lock", e)
        }
    }

    // ── ScreenOffReceiver ─────────────────────────────────────────────────────
    // SCREEN_OFF cannot be registered in the manifest — must be dynamic.
    // When the screen turns off (car goes to sleep) we re-kick the MCU to
    // prevent it cutting WiFi power after its ~10-15 min countdown expires.
    private fun registerScreenOffReceiver() {
        if (screenOffReceiver != null) return
        screenOffReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {
                if (intent.action == android.content.Intent.ACTION_SCREEN_OFF) {
                    Log.i(TAG, "SCREEN_OFF — triggering MCU keepalive")
                    serviceScope.launch { McuWakeHelper.keepAlive(applicationContext) }
                }
            }
        }
        registerReceiver(
            screenOffReceiver,
            android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF)
        )
        Log.i(TAG, "ScreenOffReceiver registered")
    }

    private fun unregisterScreenOffReceiver() {
        screenOffReceiver?.let {
            runCatching { unregisterReceiver(it) }
            screenOffReceiver = null
        }
    }

    // ── MCU wake loop ──────────────────────────────────────────────────────────
    // Periodically pokes the BYD MCU when the car is off to reset its WiFi
    // power-cut countdown (~15 min). Interval is 3 min so the MCU is touched
    // at t=0, 3, 6, 9, 12 min — safely within the window even if one call is
    // a no-op while the MCU is still transitioning to sleep state.
    private fun startMcuWakeLoop() {
        mcuWakeJob?.cancel()
        mcuWakeJob = serviceScope.launch {
            // Poke immediately at startup so the countdown is reset as soon as
            // the service launches, before the first periodic interval expires.
            try {
                val status = McuWakeHelper.getMcuStatus(applicationContext)
                Log.i(TAG, "MCU wake loop (initial): status=$status")
                McuWakeHelper.keepAlive(applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "MCU wake loop (initial) error: ${e.message}")
            }

            while (true) {
                kotlinx.coroutines.delay(3 * 60 * 1000L) // every 3 minutes
                try {
                    val status = McuWakeHelper.getMcuStatus(applicationContext)
                    if (status != 1) { // not ACTIVE
                        Log.i(TAG, "MCU wake loop: status=$status, keeping alive")
                        McuWakeHelper.keepAlive(applicationContext)
                    } else {
                        Log.d(TAG, "MCU wake loop: status=ACTIVE, no keepalive needed")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "MCU wake loop error: ${e.message}")
                }
            }
        }
    }

    private fun scheduleSelfRestart(reason: String) {
        ServiceRestartReceiver.schedule(applicationContext, delayMs = 8_000L, reason = reason)
        ServiceRestarterJobService.scheduleEarlyKick(
            applicationContext,
            delayMs = 8_000L,
            reason = "service:$reason"
        )
    }
}
