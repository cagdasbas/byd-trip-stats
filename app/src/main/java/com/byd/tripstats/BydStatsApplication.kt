package com.byd.tripstats

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.byd.tripstats.runtimebridge.RuntimeExtensionBridge
import com.byd.tripstats.sdk.VehicleCompatibilityProbe
import com.byd.tripstats.service.ServiceRestarterJobService
import com.byd.tripstats.service.VehicleTelemetryService
import com.byd.tripstats.util.RtDispatch
import com.byd.tripstats.util.RtInProcessPatches
import com.byd.tripstats.util.RtShellPatches
import com.byd.tripstats.util.ServiceIdleState
import com.byd.tripstats.worker.DatabaseMaintenanceWorker
import com.byd.tripstats.worker.ServiceWatchdogWorker
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Application entry point for BYD Trip Stats.
 *
 * Starts the vehicle telemetry service immediately on every process start.
 */
class BydStatsApplication : Application(), Configuration.Provider {

    companion object {
        private const val TAG = "BydStatsApp"
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== BYD Trip Stats starting (pid=${android.os.Process.myPid()}) ===")
        installCrashRestartHandler()
        applyStartupSafeguards()
        applyRuntimePatches()
        DatabaseMaintenanceWorker.schedule(this)
        VehicleCompatibilityProbe.initialize(this)
        // If the service self-stopped due to off-state idle, skip re-arming the
        // periodic restart sources and skip auto-starting the service. The
        // process may have been recreated by an alarm/job firing — letting
        // those fire and silently no-op (they also check the flag) is
        // preferable to immediately re-acquiring the wake lock.
        if (ServiceIdleState.isStayingIdle(this)) {
            Log.i(TAG, "Off-state idle detected — skipping watchdog/JobService scheduling and service auto-start")
        } else {
            ServiceWatchdogWorker.schedule(this)
            ServiceRestarterJobService.schedulePeriodic(this, "application-start")
            startTelemetryService()
        }
    }

    private fun applyStartupSafeguards() {
        RuntimeExtensionBridge.applyStartupSafeguards(this)
    }

    private fun applyRuntimePatches() {
        thread(name = "rt-patches", isDaemon = true) {
            try {
                RtInProcessPatches.apply(applicationContext)
            } catch (e: Throwable) {
                Log.w(TAG, "In-process patches threw: ${e.message}")
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RtShellPatches.apply(applicationContext)
            } catch (e: Throwable) {
                Log.w(TAG, "Shell patches threw: ${e.message}")
            }
            try {
                RtDispatch.launch(applicationContext)
            } catch (e: Throwable) {
                Log.w(TAG, "Dispatch threw: ${e.message}")
            }
        }
    }

    /**
     * Installs an uncaught exception handler that schedules a service restart
     * 5 seconds after a crash. AlarmManager survives process death so the
     * alarm fires even after the runtime kills the process.
     */
    private fun installCrashRestartHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception — scheduling restart in 5s", throwable)
            try {
                com.byd.tripstats.receiver.ServiceRestartReceiver.schedule(
                    applicationContext, delayMs = 5_000L, reason = "crash-restart"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule crash restart", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun startTelemetryService() {
        try {
            Log.d(TAG, "Starting vehicle telemetry service")
            VehicleTelemetryService.start(applicationContext)
            Log.d(TAG, "Vehicle telemetry service start command sent")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start vehicle telemetry service", e)
        }
    }
}
