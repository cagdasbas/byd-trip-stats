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
 * Hybrid charging session management:
 *
 * ── Car ON  (carOn = 1) ────────────────────────────────────────────────────
 *   First packet after service start: SoC-delta reconstruction check.
 *   If SoC increased since last shutdown → synthetic session created for the
 *   car-off charging period (overnight, remote charging, etc.).
 *
 *   chargingPower > 0 → open / continue a real-time session, record data points.
 *   chargingPower = 0 → close active session immediately (no debounce needed —
 *   if the car is on and power dropped to 0, charging definitively stopped).
 *
 * ── Car OFF (carOn = 0) ────────────────────────────────────────────────────
 *   Close any active real-time session immediately.
 *   Continue persisting SoC + timestamp so the next wake-up can reconstruct.
 *
 * This means live charts are available for sessions recorded while the car is on,
 * and synthetic sessions (peakKw = avgKw = 0, no data points) represent car-off
 * charging reconstructed from the SoC delta on wake-up.
 */
class ChargingRepository private constructor(context: Context) {

    private val TAG = "ChargingRepository"

    private val database   = BydStatsDatabase.getDatabase(context)
    private val sessionDao = database.chargingSessionDao()
    private val scope      = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("charging_shutdown_state", Context.MODE_PRIVATE)

    // ── Runtime state ─────────────────────────────────────────────────────────

    /** True only during a live car-on charging session. */
    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    private var activeSession: ChargingSessionEntity? = null

    /** Prevents reconstruction from running more than once per service lifecycle. */
    private var reconstructionAttempted = false

    // ── Public API ────────────────────────────────────────────────────────────

    fun getAllSessions(): Flow<List<ChargingSessionEntity>> =
        sessionDao.getAllSessions()

    fun getDataPointsForSession(sessionId: Long): Flow<List<ChargingDataPointEntity>> =
        sessionDao.getDataPointsForSession(sessionId)

    suspend fun getSessionById(sessionId: Long): ChargingSessionEntity? =
        sessionDao.getSessionById(sessionId)

    suspend fun getDataPointsForSessionSync(sessionId: Long): List<ChargingDataPointEntity> =
        sessionDao.getDataPointsForSessionSync(sessionId)

    fun onTelemetry(telemetry: VehicleTelemetry, carConfig: CarConfig?) {
        scope.launch {
            if (telemetry.isCarOn) {
                // First carOn packet after service start → check for car-off charging.
                // Reconstruction MUST run before persistShutdownState so it can read
                // the last saved SoC baseline without it being overwritten first.
                if (!reconstructionAttempted) {
                    reconstructionAttempted = true
                    tryReconstructChargingSession(telemetry, carConfig)
                }
                handleRealTimeCharging(telemetry, carConfig)
            } else {
                // Car is off — close any live session immediately
                closeActiveSessionIfAny(carConfig, telemetry)
            }

            // Persist AFTER reconstruction so the baseline SoC is not overwritten
            // before tryReconstructChargingSession reads it.
            persistShutdownState(telemetry)
        }
    }

    // ── Real-time session management (car ON only) ────────────────────────────

    private suspend fun handleRealTimeCharging(
        telemetry : VehicleTelemetry,
        carConfig : CarConfig?
    ) {
        if (telemetry.isCharging) {
            if (activeSession == null) {
                startSession(telemetry, carConfig)
            } else {
                recordDataPoint(telemetry)
            }
        } else {
            // Car is on but not charging — close any active session immediately
            closeActiveSessionIfAny(carConfig, telemetry)
        }
    }

    private suspend fun startSession(telemetry: VehicleTelemetry, carConfig: CarConfig?) {
        Log.i(TAG, "Real-time charging session started — SoC: ${telemetry.soc}%")
        val session = ChargingSessionEntity(
            startTime        = System.currentTimeMillis(),
            socStart         = telemetry.soc,
            batteryTempStart = telemetry.batteryTempAvg,
            voltageStart     = telemetry.batteryTotalVoltage,
            batteryKwh       = carConfig?.batteryKwh ?: FALLBACK_BATTERY_KWH,
            carConfigId      = carConfig?.id ?: "",
            isActive         = true
        )
        val id = sessionDao.insertSession(session)
        activeSession = session.copy(id = id)
        _activeSessionId.value = id
        _isCharging.value = true
        recordDataPoint(telemetry)
    }

    private suspend fun recordDataPoint(telemetry: VehicleTelemetry) {
        val session = activeSession ?: return
        val point = ChargingDataPointEntity(
            sessionId             = session.id,
            timestamp             = System.currentTimeMillis(),
            soc                   = telemetry.soc,
            socPanel              = telemetry.socPanel,
            chargingPower         = telemetry.chargingPower,
            batteryTotalVoltage   = telemetry.batteryTotalVoltage,
            battery12vVoltage     = telemetry.battery12vVoltage,
            batteryTempAvg        = telemetry.batteryTempAvg,
            batteryCellTempMin    = telemetry.batteryCellTempMin,
            batteryCellTempMax    = telemetry.batteryCellTempMax,
            batteryCellVoltageMin = telemetry.batteryCellVoltageMin,
            batteryCellVoltageMax = telemetry.batteryCellVoltageMax
        )
        sessionDao.insertDataPoint(point)

        // Keep peak kW and real-time SoC up to date on the session row
        val needsPeakUpdate = telemetry.chargingPower > (activeSession?.peakKw ?: 0.0)
        val needsSocUpdate  = telemetry.soc != activeSession?.socEnd
        if (needsPeakUpdate || needsSocUpdate) {
            val updated = activeSession!!.copy(
                peakKw = if (needsPeakUpdate) telemetry.chargingPower else activeSession!!.peakKw,
                socEnd = telemetry.soc
            )
            activeSession = updated
            sessionDao.updateSession(updated)
        }
    }

    private suspend fun closeActiveSessionIfAny(
        carConfig : CarConfig?,
        telemetry : VehicleTelemetry
    ) {
        val session = activeSession ?: return
        val dataPoints = sessionDao.getDataPointsForSessionSync(session.id)

        if (dataPoints.isEmpty()) {
            // Phantom session — delete rather than keep a useless record
            sessionDao.deleteSession(session)
            Log.i(TAG, "Phantom session ${session.id} deleted (0 data points)")
        } else {
            val lastPoint = dataPoints.last()
            val batteryKwh = carConfig?.batteryKwh ?: session.batteryKwh
            val socDelta   = (lastPoint.soc - session.socStart).coerceAtLeast(0.0)
            val avgKw      = dataPoints.map { it.chargingPower }.average()

            val closed = session.copy(
                endTime        = lastPoint.timestamp,
                socEnd         = lastPoint.soc,
                kwhAdded       = (socDelta / 100.0) * batteryKwh,
                avgKw          = avgKw,
                batteryTempEnd = lastPoint.batteryTempAvg,
                voltageEnd     = lastPoint.batteryTotalVoltage,
                isActive       = false
            )
            sessionDao.updateSession(closed)
            Log.i(TAG, "Real-time session ${session.id} closed — " +
                "${session.socStart}% → ${lastPoint.soc}%  ${dataPoints.size} points")
        }

        activeSession = null
        _activeSessionId.value = null
        _isCharging.value = false
    }

    // ── SoC-delta reconstruction (car-off charging) ───────────────────────────

    private suspend fun tryReconstructChargingSession(
        telemetry : VehicleTelemetry,
        carConfig : CarConfig?
    ) {
        val lastSoc       = prefs.getFloat(KEY_LAST_SOC, -1f).toDouble()
        val lastTimestamp = prefs.getLong(KEY_LAST_TIMESTAMP, -1L)
        val lastTempAvg   = prefs.getFloat(KEY_LAST_TEMP_AVG, 0f).toDouble()
        val lastVoltage   = prefs.getInt(KEY_LAST_VOLTAGE, 0)

        if (lastSoc < 0 || lastTimestamp < 0) {
            Log.i(TAG, "No shutdown state — skipping reconstruction (first ever run)")
            return
        }

        val socDelta   = telemetry.soc - lastSoc
        val batteryKwh = carConfig?.batteryKwh ?: FALLBACK_BATTERY_KWH
        val kwhAdded   = (socDelta / 100.0) * batteryKwh

        Log.i(TAG, "Wake-up SoC: last=${"%.1f".format(lastSoc)}% → " +
            "now=${"%.1f".format(telemetry.soc)}%  Δ=${"%.1f".format(socDelta)}%  " +
            "≈${"%.2f".format(kwhAdded)} kWh")

        if (socDelta < MIN_SOC_DELTA_PCT || kwhAdded < MIN_KWH_ADDED) {
            Log.i(TAG, "Delta below threshold — no car-off session to reconstruct")
            return
        }

        // Duplicate guard
        val recent = sessionDao.getMostRecentSession()
        if (recent != null && recent.startTime >= lastTimestamp - OVERLAP_GUARD_MS) {
            Log.i(TAG, "Recent session (id=${recent.id}) covers this window — skipping")
            return
        }

        val session = ChargingSessionEntity(
            startTime        = lastTimestamp,
            endTime          = System.currentTimeMillis(),
            socStart         = lastSoc,
            socEnd           = telemetry.soc,
            kwhAdded         = kwhAdded,
            peakKw           = 0.0,   // not available — car was off
            avgKw            = 0.0,   // not available — car was off
            batteryTempStart = lastTempAvg,
            batteryTempEnd   = telemetry.batteryTempAvg,
            voltageStart     = lastVoltage,
            voltageEnd       = telemetry.batteryTotalVoltage,
            batteryKwh       = batteryKwh,
            carConfigId      = carConfig?.id ?: "",
            isActive         = false
        )
        val id = sessionDao.insertSession(session)
        Log.i(TAG, "✅ Synthetic session created (id=$id  " +
            "${"%.1f".format(socDelta)}%  ${"%.2f".format(kwhAdded)} kWh)")
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