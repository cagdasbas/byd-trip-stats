package com.byd.tripstats.sdk

import android.os.Build
import android.util.Log

/**
 * Detects whether we are running on a DiLink-5 head unit (Sealion 7 etc., `ro.vehicle.type=Di5*`,
 * Android 11 / SDK 30) vs the DiLink-3 platform the app was originally built for.
 *
 * The data-source layer uses this to choose the DiLink-5 code path (typed AbsBYDAuto*Listener +
 * feature-ID event stream) over the DiLink-3 path. The real DiLink-5 bydauto SDK is bundled only
 * in the `dilink5` product flavor; on a `dilink3` build this returns false on D3 hardware and the
 * D5 hooks are simply absent.
 *
 * Shared (src/main) so both flavors compile — it touches no bydauto types.
 */
object DiLink5Platform {
    private const val TAG = "DiLink5Platform"

    val vehicleType: String by lazy { systemProp("ro.vehicle.type") }

    /** True on DiLink-5 hardware (ro.vehicle.type starts with "Di5", or SDK >= 30 as a fallback). */
    val isDiLink5: Boolean by lazy {
        val byType = vehicleType.startsWith("Di5", ignoreCase = true)
        val bySdk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R // 30
        val result = byType || (bySdk && vehicleType.isBlank())
        Log.i(TAG, "isDiLink5=$result (ro.vehicle.type='$vehicleType', sdk=${Build.VERSION.SDK_INT})")
        result
    }

    private fun systemProp(key: String): String = try {
        @Suppress("PrivateApi")
        val sp = Class.forName("android.os.SystemProperties")
        (sp.getMethod("get", String::class.java).invoke(null, key) as? String).orEmpty()
    } catch (t: Throwable) {
        ""
    }
}
