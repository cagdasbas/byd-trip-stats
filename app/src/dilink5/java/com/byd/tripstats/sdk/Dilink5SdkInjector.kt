package com.byd.tripstats.sdk

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Load the real OEM `bydauto` classes at runtime by injecting the already-installed
 * `com.byd.data.collect` APK into our OWN app classloader, instead of requiring a
 * locally-regenerated `dilink5-sdk.jar` to be bundled into the build.
 *
 * Why our own loader (not createPackageContext / a child DexClassLoader): the D3-shared data path
 * SUBCLASSES the OEM abstract listeners (AbsBYDAutoChargingListener etc.), and a subclass must share
 * one classloader with its superclass. Appending the OEM apk to this app's PathClassLoader.dexElements
 * makes `android.hardware.bydauto.*` resolve through the same loader that defines our subclasses.
 *
 * Safe to call before consent/exemption exists: on failure this just leaves the classes unresolvable,
 * and every touch-point (chargingListener/gearboxListener/tyreListener are `by lazy`; Dilink5Client's
 * reflective binds go through tryDevice{}) already tolerates that — same as it does today when the
 * OEM SDK simply isn't present. Call BEFORE the first tryDevice{} block in start() runs.
 *
 * NOTE: touches hidden dalvik.system members (pathList / makePathElements) → needs the hidden-API
 * exemption widened to include `Ldalvik/system/` (see AdbPermissionManager.VEHICLE_API_SETTINGS).
 */
object Dilink5SdkInjector {
    private const val TAG = "Dilink5SdkInjector"
    private const val PROBE_CLASS = "android.hardware.bydauto.statistic.BYDAutoStatisticDevice"
    private const val OEM_PKG = "com.byd.data.collect"

    // Only latches (skips future attempts within this process) once we're CONFIDENT retrying won't
    // help — i.e. the OEM package genuinely isn't installed. A reflection/permission failure during
    // the actual injection attempt is usually transient (e.g. ensure() ran before the hidden-API
    // exemption took effect on a pre-consent first launch) and is allowed to retry on the next call.
    @Volatile private var permanentlyUnavailable = false

    /** True if the bydauto classes are available on this app's classloader (already, or after inject). */
    @Synchronized
    fun ensure(context: Context): Boolean {
        val loader = context.classLoader
        if (loadable(loader)) return true             // already present (e.g. bundled jar)
        if (permanentlyUnavailable) return false

        val apkPaths = oemApkPaths(context)
        if (apkPaths.isEmpty()) {
            Log.w(TAG, "$OEM_PKG not found / no apk path")
            permanentlyUnavailable = true              // won't change without a fresh install; stop retrying
            return false
        }

        return try {
            val baseCl = Class.forName("dalvik.system.BaseDexClassLoader")
            val pathListF = baseCl.getDeclaredField("pathList").apply { isAccessible = true }
            val pathList = pathListF.get(loader)
            val dexListCls = pathList.javaClass
            val dexElementsF = dexListCls.getDeclaredField("dexElements").apply { isAccessible = true }
            val old = dexElementsF.get(pathList) as Array<*>

            val optDir = File(context.codeCacheDir, "bydauto-inj").apply { mkdirs() }
            val files = apkPaths.map { File(it) }
            val suppressed = ArrayList<IOException>()
            val newEls = makeElements(dexListCls, files, optDir, suppressed)
                ?: return false.also { Log.w(TAG, "makePathElements/makeDexElements not found") }
            suppressed.forEach { Log.w(TAG, "suppressed: $it") }

            // APPEND (reverted from an earlier prepend attempt — confirmed crashing on-car). Dex
            // elements are searched in order, first match wins. com.byd.data.collect bundles its OWN
            // (R8-minified) copy of AndroidX Lifecycle etc., whose short obfuscated class names (e.g.
            // androidx.lifecycle.o) can collide with our own app's real classes of the same name.
            // Prepending meant data.collect's incompatible copy won those collisions, which crashed
            // MainActivity.onCreate with NoSuchMethodError whenever the classloader mutation happened
            // to run (via the service, racing an abrupt process restart) before our own copy of that
            // class had been resolved/cached. Appending means OUR classes are always checked first
            // for ANY name collision, independent of that race — bydauto/* is the only thing OEM
            // actually needs to supply, and compileOnly + dexdump verification (see PR discussion)
            // already confirm no bydauto class exists in our own apk to be shadowed by this ordering.
            val comp = old.javaClass.componentType
            val combined = java.lang.reflect.Array.newInstance(comp, old.size + newEls.size)
            System.arraycopy(old, 0, combined, 0, old.size)
            System.arraycopy(newEls, 0, combined, old.size, newEls.size)
            dexElementsF.set(pathList, combined)

            val ok = loadable(loader)
            Log.i(TAG, "injected ${files.size} apk(s); bydauto loadable=$ok")
            ok
            // NOT latched here even on failure: a thrown SecurityException/NoSuchFieldException etc.
            // usually means the hidden-API exemption isn't active yet, which can resolve without a
            // fresh process (e.g. a later ensure() call after consent lands mid-session).
        } catch (t: Throwable) {
            Log.w(TAG, "inject failed: ${t.javaClass.name}: ${t.message}")
            false
        }
    }

    private fun loadable(loader: ClassLoader): Boolean =
        runCatching { Class.forName(PROBE_CLASS, false, loader) }.isSuccess

    private fun oemApkPaths(context: Context): List<String> = runCatching {
        val ai = context.packageManager.getApplicationInfo(OEM_PKG, 0)
        buildList {
            ai.sourceDir?.let { add(it) }
            ai.splitSourceDirs?.let { addAll(it) }
        }.distinct()
    }.getOrDefault(emptyList())

    // DexPathList element-builder signature drifts across API levels — try the known variants.
    private fun makeElements(
        dexListCls: Class<*>, files: List<File>, optDir: File, suppressed: MutableList<IOException>
    ): Array<*>? {
        runCatching {
            val m = dexListCls.getDeclaredMethod(
                "makePathElements", List::class.java, File::class.java, List::class.java
            ).apply { isAccessible = true }
            return m.invoke(null, files, optDir, suppressed) as Array<*>
        }
        runCatching {
            val m = dexListCls.getDeclaredMethod(
                "makeDexElements", List::class.java, File::class.java, List::class.java, ClassLoader::class.java
            ).apply { isAccessible = true }
            return m.invoke(null, files, optDir, suppressed, this@Dilink5SdkInjector.javaClass.classLoader) as Array<*>
        }
        return null
    }
}
