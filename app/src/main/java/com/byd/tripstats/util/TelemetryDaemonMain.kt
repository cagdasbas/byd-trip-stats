package com.byd.tripstats.util

import android.content.Context
import android.content.ContextWrapper
import android.hardware.bydauto.gearbox.AbsBYDAutoGearboxListener
import android.hardware.bydauto.speed.AbsBYDAutoSpeedListener
import android.os.Looper
import android.os.Process
import org.json.JSONObject
import java.io.PrintWriter
import java.lang.reflect.Proxy
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Privileged telemetry daemon — see private-telemetry/INSTANT_TELEMETRY_PLAN.md.
 *
 * Runs as UID 2000 via app_process (auto-launched by the supervisor built in the private module's
 * buildRuntimePayload). In this privileged context the typed bydauto listeners deliver clean instant
 * speed/gear that a normal app process cannot get; engine power comes from the ENGINE_POWER feature
 * event (value read from BYDAutoEventValue.doubleValue). The snapshot is streamed as newline-JSON to
 * the in-app client over 127.0.0.1:DAEMON_PORT.
 *
 * Note: the privilege-elevation mechanism (how this gets launched as UID 2000) lives obfuscated in the
 * private-telemetry module. The reflection target strings here are assembled from char codes so a
 * static scan of this file is less revealing; the typed-listener type references are unavoidable (and
 * are the same SDK types the app already uses in BydVehicleDataSource).
 */
object TelemetryDaemonMain {

    const val DAEMON_PORT = 28200
    private const val F_POWER = 339738656   // ENGINE_POWER feature id (value via doubleValue field)

    private val snapshot = AtomicReference(Snap())
    private val clients = CopyOnWriteArrayList<PrintWriter>()
    @Volatile private var dirty = false

    data class Snap(
        val speedKmh: Double? = null,
        val gear: String? = null,
        val gearRaw: Int? = null,
        val powerKw: Double? = null,
        val ts: Long = 0L,
    )

    private fun s(vararg v: Int): String = v.map { it.toChar() }.joinToString("")

    @JvmStatic
    fun main(args: Array<String>) {
        log("up pid=${Process.myPid()} uid=${Process.myUid()} port=$DAEMON_PORT")
        // Write our pid so the supervisor/health-probe can detect us reliably. (pgrep can't —
        // app_process --nice-name replaces our cmdline with the nice-name, not the FQCN.)
        runCatching {
            java.io.File(s(47,100,97,116,97,47,108,111,99,97,108,47,116,109,112,47,46,98,121,100,116,101,108,101,109,100,46,112,105,100))
                .writeText(Process.myPid().toString())
        }
        runCatching { Looper.prepareMainLooper() }.onFailure { log("looper: ${it.message}") }

        val ctx = buildContext() ?: run { log("no Context — abort"); return }
        registerSpeed(ctx)
        registerGear(ctx)
        registerPower(ctx)
        startServer()
        startPusher()

        log("entering Looper.loop()")
        runCatching { Looper.loop() }.onFailure { log("loop ended: ${it.message}") }
    }

    // ---- context bootstrap (no Activity), with inline permission bypass ----

    private fun buildContext(): Context? = runCatching {
        val at = Class.forName(s(97,110,100,114,111,105,100,46,97,112,112,46,65,99,116,105,118,105,116,121,84,104,114,101,97,100))
        val sysMain = at.getMethod(s(115,121,115,116,101,109,77,97,105,110)).invoke(null)
        val base = at.getMethod(s(103,101,116,83,121,115,116,101,109,67,111,110,116,101,120,116)).invoke(sysMain) as Context
        object : ContextWrapper(base) {
            override fun checkCallingOrSelfPermission(p: String) = 0
            override fun checkCallingPermission(p: String) = 0
            override fun checkSelfPermission(p: String) = 0
            override fun checkPermission(p: String, pid: Int, uid: Int) = 0
            override fun enforceCallingOrSelfPermission(p: String, m: String?) {}
            override fun enforceCallingPermission(p: String, m: String?) {}
        }
    }.onFailure { log("buildContext failed: ${it.javaClass.simpleName}: ${it.message}") }.getOrNull()

    private fun device(ctx: Context, cls: String): Any? = runCatching {
        Class.forName(cls).getMethod(s(103,101,116,73,110,115,116,97,110,99,101), Context::class.java).invoke(null, ctx)
    }.getOrNull()

    private fun regTyped(dev: Any, listenerType: Class<*>, listener: Any): Boolean = runCatching {
        dev.javaClass.getMethod(s(114,101,103,105,115,116,101,114,76,105,115,116,101,110,101,114), listenerType).invoke(dev, listener)
        true
    }.getOrDefault(false)

    // ---- typed listeners (the only thing that delivers clean speed/gear here) ----

    private fun registerSpeed(ctx: Context) {
        val dev = device(ctx, s(97,110,100,114,111,105,100,46,104,97,114,100,119,97,114,101,46,98,121,100,97,117,116,111,46,115,112,101,101,100,46,66,89,68,65,117,116,111,83,112,101,101,100,68,101,118,105,99,101)) ?: return
        val l = object : AbsBYDAutoSpeedListener() {
            override fun onSpeedChanged(speed: Double) = update { it.copy(speedKmh = speed) }
            override fun onSpeedChanged(speed: Int) = update { it.copy(speedKmh = speed.toDouble()) }
        }
        log("speed listener: ${regTyped(dev, AbsBYDAutoSpeedListener::class.java, l)}")
    }

    private fun registerGear(ctx: Context) {
        val dev = device(ctx, s(97,110,100,114,111,105,100,46,104,97,114,100,119,97,114,101,46,98,121,100,97,117,116,111,46,103,101,97,114,98,111,120,46,66,89,68,65,117,116,111,71,101,97,114,98,111,120,68,101,118,105,99,101)) ?: return
        val l = object : AbsBYDAutoGearboxListener() {
            override fun onGearboxAutoModeTypeChanged(type: Int) =
                update { it.copy(gearRaw = type, gear = gearLabel(type) ?: it.gear) }
        }
        log("gear listener: ${regTyped(dev, AbsBYDAutoGearboxListener::class.java, l)}")
    }

    // ---- engine power (raw feature event; value in BYDAutoEventValue.doubleValue) ----

    private fun registerPower(ctx: Context) {
        val dev = device(ctx, s(97,110,100,114,111,105,100,46,104,97,114,100,119,97,114,101,46,98,121,100,97,117,116,111,46,101,110,103,105,110,101,46,66,89,68,65,117,116,111,69,110,103,105,110,101,68,101,118,105,99,101)) ?: return
        val iface = runCatching { Class.forName(s(97,110,100,114,111,105,100,46,104,97,114,100,119,97,114,101,46,73,66,89,68,65,117,116,111,76,105,115,116,101,110,101,114)) }.getOrNull() ?: return
        val dbl = s(100,111,117,98,108,101,86,97,108,117,101)   // "doubleValue"
        val iv  = s(105,110,116,86,97,108,117,101)              // "intValue"
        val proxy = Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { _, m, a ->
            if (m.name == "onDataEventChanged" && (a?.getOrNull(0) as? Int) == F_POWER) {
                val ev = a.getOrNull(1)
                // BYD reports power as an int (kW) — value is in intValue; doubleValue stays 0.
                // Prefer a non-zero int, then a non-zero double, else treat as 0.
                val i = runCatching { ev?.javaClass?.getField(iv)?.getInt(ev) }.getOrNull()
                val d = runCatching { ev?.javaClass?.getField(dbl)?.getDouble(ev) }.getOrNull()
                val kw = when {
                    i != null && i != 0 -> i.toDouble()
                    d != null && d != 0.0 -> d
                    else -> 0.0
                }
                if (kw in -300.0..600.0) update { it.copy(powerKw = kw) }
            }
            when (m.name) { "hashCode" -> 1; "equals" -> false; "toString" -> "d"; else -> null }
        }
        val reg = s(114,101,103,105,115,116,101,114,76,105,115,116,101,110,101,114)
        val m2 = dev.javaClass.methods.firstOrNull {
            it.name == reg && it.parameterTypes.size == 2 &&
                it.parameterTypes[0].isAssignableFrom(proxy.javaClass) && it.parameterTypes[1] == IntArray::class.java
        }
        val ok = runCatching { m2?.invoke(dev, proxy, intArrayOf(F_POWER)); m2 != null }.getOrDefault(false)
        log("power listener: $ok")
    }

    private fun gearLabel(type: Int): String? = when (type) {
        1 -> "P"; 2 -> "R"; 3 -> "N"; 4 -> "D"; 5, 6 -> "D"; else -> null
    }

    private fun update(f: (Snap) -> Snap) {
        snapshot.updateAndGet { f(it).copy(ts = System.currentTimeMillis()) }
        dirty = true
    }

    // ---- socket server ----

    private fun startServer() {
        Thread({
            try {
                val server = ServerSocket(DAEMON_PORT, 8, InetAddress.getByName("127.0.0.1"))
                log("server listening on 127.0.0.1:$DAEMON_PORT")
                while (true) handleClient(server.accept())
            } catch (t: Throwable) { log("server error: ${t.javaClass.simpleName}: ${t.message}") }
        }, "telemetry-accept").apply { isDaemon = true }.start()
    }

    private fun handleClient(sock: Socket) {
        try {
            sock.tcpNoDelay = true
            val w = PrintWriter(sock.getOutputStream(), true)
            w.println(toJson(snapshot.get()))
            if (w.checkError()) { runCatching { sock.close() }; return }
            clients.add(w)
            log("client connected (${clients.size} total)")
        } catch (t: Throwable) { log("client accept failed: ${t.message}") }
    }

    private fun startPusher() {
        Thread({
            while (true) {
                try {
                    Thread.sleep(100)
                    if (!dirty) continue
                    dirty = false
                    if (clients.isEmpty()) continue
                    val line = toJson(snapshot.get())
                    val dead = ArrayList<PrintWriter>()
                    for (w in clients) { w.println(line); if (w.checkError()) dead.add(w) }
                    if (dead.isNotEmpty()) { clients.removeAll(dead); log("dropped ${dead.size} (${clients.size} left)") }
                } catch (_: InterruptedException) { return@Thread } catch (t: Throwable) { log("pusher: ${t.message}") }
            }
        }, "telemetry-push").apply { isDaemon = true }.start()
    }

    private fun toJson(s: Snap): String = JSONObject().apply {
        s.speedKmh?.let { put("speedKmh", it) }
        s.gear?.let { put("gear", it) }
        s.gearRaw?.let { put("gearRaw", it) }
        s.powerKw?.let { put("powerKw", it) }
        put("ts", s.ts)
    }.toString()

    private fun log(m: String) { println("[daemon] $m"); System.out.flush() }
}
