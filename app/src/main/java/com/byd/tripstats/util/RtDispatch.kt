package com.byd.tripstats.util

import android.content.Context
import android.util.Log
import com.byd.tripstats.adb.AdbPermissionManager
import com.byd.tripstats.runtimebridge.RuntimeExtensionBridge

internal object RtDispatch {
    private const val TAG = "RtDispatch"

    suspend fun launch(context: Context) {
        if (!AdbPermissionManager.isSetupComplete(context)) return
        val apk = context.applicationInfo.sourceDir
        if (apk.isNullOrBlank()) {
            Log.w(TAG, "no apk path")
            return
        }
        val payload = RuntimeExtensionBridge.stringMap("r01", apk)
        val probe = payload["probe"] ?: return
        val dispatch = payload["dispatch"] ?: return

        val probeRes = AdbPermissionManager.runShellBatch(
            context,
            listOf(probe),
            perCommandTimeoutMs = 3_000L,
        )
        val probeOutput = probeRes.firstOrNull()?.output?.trim().orEmpty()
        if (probeOutput == "ALIVE") {
            Log.i(TAG, "healthy — skip")
            return
        }
        Log.i(TAG, "re-dispatch :: ${probeOutput.take(100).ifBlank { "(no probe)" }}")

        val results = AdbPermissionManager.runShellBatch(
            context,
            listOf(dispatch),
            perCommandTimeoutMs = 6_000L,
        )
        if (results.isEmpty()) {
            Log.w(TAG, "channel unreachable")
            return
        }
        val r = results.first()
        Log.i(TAG, "dispatched exit=${r.exitCode} :: ${r.output.take(120)}")
    }
}
