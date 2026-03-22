package com.byd.tripstats.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.room.withTransaction
import com.byd.tripstats.data.config.CarConfig
import com.byd.tripstats.data.local.BydStatsDatabase
import com.byd.tripstats.data.local.entity.ChargingDataPointEntity
import com.byd.tripstats.data.local.entity.ChargingSessionEntity
import com.byd.tripstats.data.model.VehicleTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages charging session detection and persistence using SoC-delta reconstruction.
 *
 * **Architecture rationale:**
 * Real-time charging recording while the car is off is architecturally impossible:
 * DiLink kills TripStats when the car powers down, taking Moquette with it.
 * Instead, we persist the last known SoC + timestamp on every telemetry packet.
 * When the car wakes and TripStats starts receiving data again, we compare
 * current SoC against the last saved value. A meaningful increase means a
 * charging session happened while we were offline — we create a synthetic
 * session record with accurate energy figures from the SoC delta.
 *
 * This approach is 100% reliable: it works regardless of how long the car
 * was off, whether TripStats survived, or how many times the system was restarted.
 *
 * **Synthetic session fields:**
 *   startTime  = timestamp of last telemetry before previous shutdown
 *   endTime    = timestamp of first telemetry after current wake-up
 *   socStart   = SoC at last shutdown
 *   socEnd     = SoC at first wake-up packet
 *   kwhAdded   = socDelta / 100 × batteryKwh
 *   peakKw/avgKw = 0.0  (not recorded — car was off)
 */
class ChargingRepository private constructor(context: Context) {

    private val TAG = "ChargingRepository"

    private val database   = BydStatsDatabase.getDatabase(context)
    private val sessionDao = database.chargingSessionDao()
    private val scope      = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("charging_shutdown_state", Context.MODE_PRIVATE)

    // Prevents re-running reconstruction on every packet after the first one
    private var reconstructionAttempted = false

    // ── Public state — retained for compile compatibility ─────────────────────

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    // ── Public API ────────────────────────────────────────────────────────────

    fun getAllSessions(): Flow<List<ChargingSessionEntity>> =
        sessionDao.getAllSessions()

    fun getDataPointsForSession(sessionId: Long): Flow<List<ChargingDataPointEntity>> =
        sessionDao.getDataPointsForSession(sessionId)

    suspend fun getSessionById(sessionId: Long): ChargingSessionEntity? =
        sessionDao.getSessionById(sessionId)

    suspend fun getDataPointsForSessionSync(sessionId: Long): List<ChargingDataPointEntity> =
        sessionDao.getDataPointsForSessionSync(sessionId)

    /**
     * Called on every incoming telemetry packet from MqttService.
     *
     * First call: attempts SoC-delta reconstruction from last shutdown state.
     * Every call: persists current SoC + timestamp for the next wake-up.
     */
    fun onTelemetry(telemetry: VehicleTelemetry, carConfig: CarConfig?) {
        scope.launch {
            if (!reconstructionAttempted) {
                reconstructionAttempted = true
                tryReconstructChargingSession(telemetry, carConfig)
            }
            persistShutdownState(telemetry)
        }
    }

    // ── Reconstruction ────────────────────────────────────────────────────────

    /**
     * Compares current SoC against last persisted SoC.
     * Creates a synthetic charging session if the increase exceeds thresholds.
     *
     * Thresholds:
     *   MIN_SOC_DELTA_PCT = 1.0 — filters BMS recalibration noise (± 0.5%)
     *   MIN_KWH_ADDED     = 0.3 — filters rounding artefacts on tiny moves
     */
    private suspend fun tryReconstructChargingSession(
        telemetry : VehicleTelemetry,
        carConfig : CarConfig?
    ) {
        val lastSoc       = prefs.getFloat(KEY_LAST_SOC, -1f).toDouble()
        val lastTimestamp = prefs.getLong(KEY_LAST_TIMESTAMP, -1L)
        val lastTempAvg   = prefs.getFloat(KEY_LAST_TEMP_AVG, 0f).toDouble()
        val lastVoltage   = prefs.getInt(KEY_LAST_VOLTAGE, 0)

        if (lastSoc < 0 || lastTimestamp < 0) {
            Log.i(TAG, "No shutdown state saved — skipping reconstruction (first ever run)")
            return
        }

        val socDelta   = telemetry.soc - lastSoc
        val batteryKwh = carConfig?.batteryKwh ?: FALLBACK_BATTERY_KWH
        val kwhAdded   = (socDelta / 100.0) * batteryKwh

        Log.i(TAG, "Wake-up SoC: last=${"%.1f".format(lastSoc)}% → now=${"%.1f".format(telemetry.soc)}%  Δ=${"%.1f".format(socDelta)}%  ≈${"%.2f".format(kwhAdded)} kWh")

        if (socDelta < MIN_SOC_DELTA_PCT || kwhAdded < MIN_KWH_ADDED) {
            Log.i(TAG, "Delta below threshold — no charging session to reconstruct")
            return
        }

        // Duplicate guard — don't create a session if one already exists covering
        // this window (e.g. TripStats survived briefly and recorded something)
        val recent = sessionDao.getMostRecentSession()
        if (recent != null && recent.startTime >= lastTimestamp - OVERLAP_GUARD_MS) {
            Log.i(TAG, "Session id=${recent.id} already covers this window — skipping")
            return
        }

        val session = ChargingSessionEntity(
            startTime        = lastTimestamp,
            endTime          = System.currentTimeMillis(),
            socStart         = lastSoc,
            socEnd           = telemetry.soc,
            kwhAdded         = kwhAdded,
            peakKw           = 0.0,
            avgKw            = 0.0,
            batteryTempStart = lastTempAvg,
            batteryTempEnd   = telemetry.batteryTempAvg,
            voltageStart     = lastVoltage,
            voltageEnd       = telemetry.batteryTotalVoltage,
            batteryKwh       = batteryKwh,
            carConfigId      = carConfig?.id ?: "",
            isActive         = false
        )

        val id = sessionDao.insertSession(session)
        Log.i(TAG, "✅ Synthetic charging session created (id=$id,  ${"%.1f".format(socDelta)}%,  ${"%.2f".format(kwhAdded)} kWh)")
    }

    private fun persistShutdownState(telemetry: VehicleTelemetry) {
        prefs.edit()
            .putFloat(KEY_LAST_SOC,      telemetry.soc.toFloat())
            .putLong(KEY_LAST_TIMESTAMP, System.currentTimeMillis())
            .putFloat(KEY_LAST_TEMP_AVG, telemetry.batteryTempAvg.toFloat())
            .putInt(KEY_LAST_VOLTAGE,    telemetry.batteryTotalVoltage)
            .apply()
    }

    // ── Deletion ──────────────────────────────────────────────────────────────

    suspend fun deleteSession(sessionId: Long) {
        database.withTransaction {
            sessionDao.deleteDataPointsForSession(sessionId)
            sessionDao.deleteSessionById(sessionId)
        }
    }

    suspend fun deleteSessions(sessionIds: List<Long>) {
        database.withTransaction {
            sessionIds.forEach { id ->
                sessionDao.deleteDataPointsForSession(id)
                sessionDao.deleteSessionById(id)
            }
        }
    }

    // ── Singleton ─────────────────────────────────────────────────────────────

    companion object {
        @Volatile private var INSTANCE: ChargingRepository? = null

        fun getInstance(context: Context): ChargingRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChargingRepository(context.applicationContext).also { INSTANCE = it }
            }

        private const val KEY_LAST_SOC        = "last_soc"
        private const val KEY_LAST_TIMESTAMP  = "last_timestamp"
        private const val KEY_LAST_TEMP_AVG   = "last_temp_avg"
        private const val KEY_LAST_VOLTAGE    = "last_voltage"

        private const val MIN_SOC_DELTA_PCT   = 1.0
        private const val MIN_KWH_ADDED       = 0.3
        private const val OVERLAP_GUARD_MS    = 5 * 60 * 1000L
        private const val FALLBACK_BATTERY_KWH = 82.5
    }
}