package com.byd.tripstats.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.byd.tripstats.service.ServiceRestarterJobService
import com.byd.tripstats.service.VehicleTelemetryService
import com.byd.tripstats.util.McuWakeHelper
import com.byd.tripstats.util.RtDispatch
import com.byd.tripstats.util.RtInProcessPatches
import com.byd.tripstats.util.RtShellPatches
import com.byd.tripstats.util.ServiceIdleState
import com.byd.tripstats.worker.ServiceWatchdogWorker
import com.byd.tripstats.receiver.OffStateKeepaliveReceiver
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Receives boot/package/ACC/car-off style events and kicks the telemetry service twice:
 * immediately, then again after short delays in case BYD system services are
 * not ready yet during early boot or right after the car transitions off.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"

        private val START_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_REBOOT,
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            "com.byd.action.ACC_ON",
            "com.byd.action.ACC_OFF",
            "com.byd.action.IGN_ON",
            "com.byd.accmode.ACC_MODE_CHANGED",
        )

        private val WHITELIST_REFRESH_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_USER_UNLOCKED,
            "com.byd.action.ACC_OFF",
            "com.byd.action.ACC_ON",
            "com.byd.action.IGN_ON",
            "com.byd.accmode.ACC_MODE_CHANGED",
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in START_ACTIONS) {
            Log.d(TAG, "Ignoring action=$action")
            return
        }

        Log.i(TAG, "Boot/start trigger received: action=$action")
        try {
            val appContext = context.applicationContext

            // Re-inject BYD whitelists on car-on / boot / package-replace events.
            // BYD firmware can reset these on ACC cycles, so we re-apply each time.
            if (action in WHITELIST_REFRESH_ACTIONS) {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        withTimeout(8_000L) {
                            RtInProcessPatches.apply(appContext)
                            RtShellPatches.apply(appContext)
                            RtDispatch.launch(appContext)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Whitelist injection failed/timed out on $action: ${e.message}")
                    } finally {
                        pending.finish()
                    }
                }
            }

            // ── ACC_OFF / POWER_DISCONNECTED: idle path, no restart re-arming ──
            // Schedule the 90-min off-state keepalive chain and send one MCU
            // keepalive — but do NOT start the service or schedule any periodic
            // restart sources. The existing service (if still running from the
            // trip) will detect carOff+notCharging and self-stop after 5 min.
            // Re-arming restart kicks here would defeat that self-stop and
            // burn the 12V battery overnight.
            val isOffEvent = action == "com.byd.action.ACC_OFF" ||
                action == "com.byd.accmode.ACC_MODE_CHANGED" ||
                action == Intent.ACTION_POWER_DISCONNECTED
            if (isOffEvent) {
                if (action == "com.byd.action.ACC_OFF" || action == "com.byd.accmode.ACC_MODE_CHANGED") {
                    val pending = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            withTimeout(10_000L) {
                                McuWakeHelper.keepAlive(appContext)
                            }
                            Log.i(TAG, "MCU keepalive sent on $action")
                        } catch (e: Exception) {
                            Log.w(TAG, "MCU keepalive failed/timed out on $action: ${e.message}")
                        } finally {
                            pending.finish()
                        }
                    }
                    // Off-state keepalive chain — survives process death
                    OffStateKeepaliveReceiver.schedule(appContext, iteration = 0)
                }
                Log.i(TAG, "Off-event $action — skipping service start and restart re-arm")
                return
            }

            // ── ACC_ON / IGN_ON / POWER_CONNECTED / boot actions: real start ──
            // Cancel the off-state keepalive chain (we're back online) and clear
            // the staying_idle flag so periodic restart sources resume.
            if (action == "com.byd.action.ACC_ON" || action == "com.byd.action.IGN_ON") {
                OffStateKeepaliveReceiver.cancel(appContext)
            }
            ServiceIdleState.setStayingIdle(appContext, false)

            VehicleTelemetryService.start(appContext)
            ServiceWatchdogWorker.schedule(appContext)
            // Kick at 15s and 45s for fast service readiness.
            ServiceRestartReceiver.schedule(appContext, delayMs = 15_000L, reason = "boot:$action")
            ServiceRestartReceiver.schedule(appContext, delayMs = 45_000L, reason = "boot-followup:$action")
            // Kick at 2 min for cold boot, when platform services may still be settling.
            ServiceRestartReceiver.schedule(appContext, delayMs = 120_000L, reason = "boot-platform-ready:$action")
            ServiceRestarterJobService.schedulePeriodic(appContext, "boot:$action")
            ServiceRestarterJobService.scheduleEarlyKick(appContext, delayMs = 15_000L, reason = "boot:$action")
            ServiceRestarterJobService.scheduleLateKick(appContext, delayMs = 45_000L, reason = "boot-followup:$action")
            Log.i(TAG, "✅ Vehicle telemetry start dispatched for action=$action")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to dispatch vehicle telemetry start for action=$action", e)
        }
    }
}
