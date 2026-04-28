package com.byd.tripstats.adb

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.byd.tripstats.runtimebridge.RuntimeExtensionBridge
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.Socket

/**
 * Optional local permission helper for DiLink builds that expose a user-approved
 * debugging channel. The key pair is persistent once the user authorizes it.
 */
object AdbPermissionManager {

    private const val TAG = "AdbPermissionManager"
    private const val ADB_HOST = "127.0.0.1"
    private const val ADB_PORT = 5555
    private const val KEY_FILE = "adbkey"
    private const val KEY_PUB_FILE = "adbkey.pub"
    private const val PREFS_NAME = "adb_permission_prefs"
    private const val PREF_PERMISSIONS_GRANTED = "permissions_granted_v1"

    // Permissions that require elevated user-approved grant flow.
    private val REQUIRED_PERMISSIONS = listOf(
        "android.permission.WRITE_SECURE_SETTINGS",
        "android.permission.READ_LOGS",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
    )

    sealed class SetupState {
        object Idle         : SetupState()
        object Connecting   : SetupState()
        object WaitingAuth  : SetupState()
        object Granting     : SetupState()
        object Done         : SetupState()
        data class Failed(val reason: String) : SetupState()
    }

    data class ShellResult(
        val exitCode: Int,
        val output: String
    )

    private val _state = MutableStateFlow<SetupState>(SetupState.Idle)
    val state: StateFlow<SetupState> = _state.asStateFlow()

    /** True if all required permissions are already granted — skip setup entirely. */
    fun isSetupComplete(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_PERMISSIONS_GRANTED, false)) return true
        // Double-check at runtime in case permissions were revoked
        return checkPermissionsGranted(context)
    }

    /** Check via dumpsys whether our permissions are actually granted. */
    fun checkPermissionsGranted(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            REQUIRED_PERMISSIONS.all { perm ->
                pm.checkPermission(perm, context.packageName) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Run the full setup flow. Call from a coroutine.
     * Returns true if permissions are now granted.
     *
     * Safe to call multiple times — returns immediately if already done.
     */
    suspend fun runSetup(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isSetupComplete(context)) {
            _state.value = SetupState.Done
            Log.i(TAG, "Setup already complete")
            return@withContext true
        }

        if (_state.value == SetupState.Connecting ||
            _state.value == SetupState.WaitingAuth ||
            _state.value == SetupState.Granting) {
            Log.d(TAG, "Setup already in progress")
            return@withContext false
        }

        _state.value = SetupState.Connecting

        try {
            // Check ADB port is open (adbd running)
            if (!isPortOpen()) {
                _state.value = SetupState.Failed(
                    "ADB not enabled. Go to Settings → System → Developer Options " +
                    "and enable USB Debugging."
                )
                return@withContext false
            }

            val keyPair = getOrCreateKeyPair(context)
            Log.i(TAG, "Attempting ADB connection to $ADB_HOST:$ADB_PORT")

            // Try quick connect first (already authorized from previous run)
            val dadb = tryConnect(keyPair, timeoutMs = 2_000)
            if (dadb != null) {
                return@withContext grantPermissionsAndClose(dadb, context)
            }

            // Not yet authorized — show waiting state and poll
            _state.value = SetupState.WaitingAuth
            Log.i(TAG, "Waiting for ADB authorization in car UI (max 3 min)...")

            val maxAttempts = 60  // 3 minutes at 3s intervals
            repeat(maxAttempts) { attempt ->
                delay(3_000)
                if (_state.value != SetupState.WaitingAuth) return@withContext false

                Log.d(TAG, "Auth poll ${attempt + 1}/$maxAttempts")
                val d = tryConnect(keyPair, timeoutMs = 2_000)
                if (d != null) {
                    return@withContext grantPermissionsAndClose(d, context)
                }
            }

            _state.value = SetupState.Failed(
                "Authorization timed out. Please tap 'Allow' when the USB debugging " +
                "dialog appears in the car screen, then restart the app."
            )
            false
        } catch (e: Exception) {
            Log.e(TAG, "Setup failed: ${e.message}", e)
            _state.value = SetupState.Failed("Connection error: ${e.message}")
            false
        }
    }

    suspend fun runShellCommand(context: Context, command: String): ShellResult = withContext(Dispatchers.IO) {
        val safeCommand = command.trim()
        if (safeCommand.isBlank()) return@withContext ShellResult(-1, "No command entered")

        if (!isPortOpen()) {
            return@withContext ShellResult(-1, "Local permission channel is not reachable")
        }

        val keyPair = getOrCreateKeyPair(context)
        val dadb = tryConnect(keyPair, timeoutMs = 2_000)
            ?: return@withContext ShellResult(-1, "ADB is not authorized yet")

        try {
            val result = dadb.shell(safeCommand)
            ShellResult(result.exitCode, result.allOutput.trim())
        } catch (e: Exception) {
            ShellResult(-1, "Command failed: ${e.message}")
        } finally {
            runCatching { dadb.close() }
        }
    }

    /**
     * Run a batch of commands through a single authenticated dadb session.
     * Each command is bounded by [perCommandTimeoutMs]; a hang does not block
     * the rest of the batch. Returns results in order. Empty list on auth/port
     * failure so callers can proceed silently.
     */
    suspend fun runShellBatch(
        context: Context,
        commands: List<String>,
        perCommandTimeoutMs: Long = 5_000L,
    ): List<ShellResult> = withContext(Dispatchers.IO) {
        if (commands.isEmpty()) return@withContext emptyList()
        if (!isPortOpen()) return@withContext emptyList()

        val keyPair = getOrCreateKeyPair(context)
        val dadb = tryConnect(keyPair, timeoutMs = 2_000) ?: return@withContext emptyList()

        val out = ArrayList<ShellResult>(commands.size)
        try {
            for (cmd in commands) {
                val trimmed = cmd.trim()
                if (trimmed.isBlank()) { out += ShellResult(-1, ""); continue }
                val result = withTimeoutOrNull(perCommandTimeoutMs) {
                    try {
                        val r = dadb.shell(trimmed)
                        ShellResult(r.exitCode, r.allOutput.trim())
                    } catch (e: Exception) {
                        ShellResult(-1, "Command failed: ${e.message}")
                    }
                } ?: ShellResult(-1, "timeout")
                out += result
            }
        } finally {
            runCatching { dadb.close() }
        }
        out
    }

    private suspend fun grantPermissionsAndClose(dadb: Dadb, context: Context): Boolean {
        return try {
            _state.value = SetupState.Granting
            val pkg = context.packageName
            var allGranted = true

            REQUIRED_PERMISSIONS.forEach { perm ->
                val result = dadb.shell("pm grant $pkg $perm")
                val ok = result.exitCode == 0 || result.allOutput.contains("Success", ignoreCase = true)
                Log.i(TAG, "grant $perm: ${if (ok) "✅" else "❌"} (${result.allOutput.trim()})")
                if (!ok) allGranted = false
            }

            RuntimeExtensionBridge.stringList("s01", context.packageName).forEach { command ->
                dadb.shell(command)
            }

            dadb.close()

            if (allGranted) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(PREF_PERMISSIONS_GRANTED, true).apply()
                _state.value = SetupState.Done
                Log.i(TAG, "✅ All permissions granted via ADB")
                true
            } else {
                _state.value = SetupState.Failed("Some permissions could not be granted")
                false
            }
        } catch (e: Exception) {
            runCatching { dadb.close() }
            _state.value = SetupState.Failed("Grant failed: ${e.message}")
            false
        }
    }

    /** Non-throwing connect attempt. Returns null on timeout/auth-pending. */
    private fun tryConnect(keyPair: AdbKeyPair, timeoutMs: Long): Dadb? {
        var result: Dadb? = null
        val thread = Thread {
            try {
                val d = Dadb.create(ADB_HOST, ADB_PORT, keyPair)
                val test = d.shell("echo ok")
                if (test.exitCode == 0) result = d else d.close()
            } catch (_: Exception) {}
        }
        thread.start()
        thread.join(timeoutMs)
        if (thread.isAlive) thread.interrupt()
        return result
    }

    private fun isPortOpen(): Boolean = try {
        Socket(ADB_HOST, ADB_PORT).use { true }
    } catch (_: Exception) { false }

    private fun getOrCreateKeyPair(context: Context): AdbKeyPair {
        val privateKey = File(context.filesDir, KEY_FILE)
        val publicKey  = File(context.filesDir, KEY_PUB_FILE)
        if (privateKey.exists() && publicKey.exists()) {
            runCatching { return AdbKeyPair.read(privateKey, publicKey) }
        }
        Log.i(TAG, "Generating new ADB key pair")
        AdbKeyPair.generate(privateKey, publicKey)
        return AdbKeyPair.read(privateKey, publicKey)
    }
}
