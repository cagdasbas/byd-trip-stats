package com.byd.tripstats.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.byd.tripstats.service.VehicleTelemetryService
import com.byd.tripstats.util.ServiceIdleState
import java.util.concurrent.TimeUnit

/**
 * Periodic watchdog that ensures the vehicle telemetry service is alive even
 * after the OS kills the app process.
 *
 * WorkManager's scheduler survives process death — the OS will wake the app
 * every [INTERVAL_MINUTES] to run this worker. If the service is already
 * running, startForegroundService() is a harmless no-op.
 *
 * Skips the restart entirely while [ServiceIdleState.isStayingIdle] — without
 * this check the watchdog would re-start the service every 15 min overnight,
 * undoing the carOff+notCharging self-stop and draining the 12V battery.
 */
class ServiceWatchdogWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (ServiceIdleState.isStayingIdle(applicationContext)) {
            Log.i(TAG, "Watchdog skipped — service in off-state idle")
            return Result.success()
        }
        Log.i(TAG, "Watchdog fired — ensuring vehicle telemetry service is running")
        return try {
            VehicleTelemetryService.start(applicationContext)
            Log.i(TAG, "✅ Watchdog restart complete")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Watchdog failed", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "ServiceWatchdog"
        private const val WORK_NAME = "telemetry_service_watchdog"

        /** Minimum interval WorkManager supports is 15 minutes. */
        private const val INTERVAL_MINUTES = 15L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
                repeatInterval         = INTERVAL_MINUTES,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Log.i(TAG, "Watchdog scheduled (15-min periodic, KEEP policy)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Watchdog cancelled (off-state idle)")
        }
    }
}
