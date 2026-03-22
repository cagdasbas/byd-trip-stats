package com.byd.tripstats.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.byd.tripstats.data.preferences.PreferencesManager
import com.byd.tripstats.service.MqttBrokerService
import com.byd.tripstats.service.MqttService
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic watchdog that ensures the MQTT stack (embedded broker + client)
 * is always running, even after the head unit's OS kills the app process.
 *
 * WorkManager's scheduler survives process death — the OS will wake the app
 * every [INTERVAL_MINUTES] to run this worker. If the services are already
 * alive, startForegroundService() is a harmless no-op (Android sees that the
 * service is already running and ignores the duplicate start command).
 */
class ServiceWatchdogWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Watchdog fired — checking MQTT stack health")
        return try {
            val prefs = PreferencesManager(applicationContext)
            val settings = prefs.mqttSettings.first()

            if (settings.brokerUrl.isBlank() || settings.topic.isBlank()) {
                Log.w(TAG, "MQTT not configured yet — skipping watchdog restart")
                return Result.success()
            }

            val isLocal = settings.brokerUrl.trim().let {
                it.isBlank() || it == "127.0.0.1" || it == "localhost" || it == "::1"
            }

            if (isLocal) {
                Log.i(TAG, "Restarting embedded MQTT broker…")
                MqttBrokerService.start(applicationContext)
                // Brief pause so the broker port is bound before the client connects.
                kotlinx.coroutines.delay(2_000)
            }

            Log.i(TAG, "Restarting MQTT client…")
            MqttService.start(
                context    = applicationContext,
                brokerUrl  = settings.brokerUrl,
                brokerPort = settings.brokerPort,
                username   = settings.username.ifBlank { null },
                password   = settings.password.ifBlank { null },
                topic      = settings.topic
            )

            Log.i(TAG, "✅ Watchdog restart complete")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Watchdog failed", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "ServiceWatchdog"
        private const val WORK_NAME = "mqtt_service_watchdog"

        /** Minimum interval WorkManager supports is 15 minutes. */
        private const val INTERVAL_MINUTES = 15L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
                repeatInterval         = INTERVAL_MINUTES,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                // No network constraint — the MQTT stack must restart regardless of
                // connectivity. Moquette needs no internet, and HiveMQ reconnects
                // automatically when the network returns.
                // The previous NetworkType.CONNECTED constraint prevented the watchdog
                // from firing when the car was parked with no SIM signal — causing
                // charging sessions to be missed entirely.
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Log.i(TAG, "Watchdog scheduled (15-min periodic, KEEP policy)")
        }
    }
}