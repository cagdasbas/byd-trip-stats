package com.byd.tripstats.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.byd.tripstats.data.config.CarConfig
import com.byd.tripstats.data.local.BydStatsDatabase
import com.byd.tripstats.data.local.entity.ChargingDataPointEntity
import com.byd.tripstats.data.local.entity.ChargingSessionEntity
import com.byd.tripstats.data.model.VehicleTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages charging session detection, recording, and persistence.
 *
 * Detection logic mirrors TripRepository:
 *   - isCharging flips true  → open a new session
 *   - isCharging flips false → close the active session (with a debounce
 *     to survive brief telemetry gaps without prematurely ending a session)
 *
 * Call [onTelemetry] from DashboardViewModel for every incoming MQTT packet.
 * All DB writes run on the IO dispatcher; the caller never needs to worry
 * about threading.
 */
class ChargingRepository private constructor(context: Context) {

    private val TAG = "ChargingRepository"

    private val database   = BydStatsDatabase.getDatabase(context)
    private val sessionDao = database.chargingSessionDao()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Recover any session that was left open by a previous crash or force-kill
    // This runs before any telemetry is processed to prevent race conditions.
    private val initJob = scope.launch {
        recoverOrphanedSession()
    }

    // ── Public state ──────────────────────────────────────────────────────────

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    // ── Internal state ────────────────────────────────────────────────────────

    /**
     * Auto-close debounce — fires when no charging packet arrives within the window.
     *
     * The timer is cancelled and restarted on every incoming charging packet,
     * so it only fires if the car genuinely stops sending charging data.
     *
     * Both car-on and car-off use the same 60 s window because:
     *   - Car ON:  Electro publishes every 1 s, so 60 s of silence is definitive.
     *   - Car OFF: Electro publishes every ~30 s (recommended setting). MqttService
     *     stays alive as a foreground service (START_STICKY + wake lock), so the app
     *     continues receiving these packets. The 60 s debounce gives a comfortable
     *     2× margin over the 30 s publish interval, avoiding false session closes
     *     due to network jitter.
     */
    private val DEBOUNCE_MS = 60_000L  // 1 min — both car-on and car-off

    private var closeSessionJob: Job? = null
    private var isStoppingExplicitly = false

    /**
     * The last telemetry packet where chargingPower > 0.
     * Used to anchor endTime to the actual last known charging moment
     * rather than the debounce fire time, which can be up to one publish
     * interval + debounce window later.
     */
    private var lastChargingTelemetry: VehicleTelemetry? = null

    private var activeSession: ChargingSessionEntity? = null
    private val pendingDataPoints = mutableListOf<ChargingDataPointEntity>()

    // ── Public API ────────────────────────────────────────────────────────────

    fun getAllSessions(): Flow<List<ChargingSessionEntity>> =
        sessionDao.getAllSessions()

    fun getDataPointsForSession(sessionId: Long): Flow<List<ChargingDataPointEntity>> =
        sessionDao.getDataPointsForSession(sessionId)

    suspend fun getSessionById(sessionId: Long): ChargingSessionEntity? =
        sessionDao.getSessionById(sessionId)

    suspend fun getDataPointsForSessionSync(sessionId: Long): List <ChargingDataPointEntity> =
        sessionDao.getDataPointsForSessionSync(sessionId)

    /**
     * Feed each incoming telemetry packet here.
     * [carConfig] must be the currently selected car — used to compute kwhAdded on close.
     */
    fun onTelemetry(telemetry: VehicleTelemetry, carConfig: CarConfig?) {
        scope.launch {
            initJob.join()

            if (telemetry.isCharging) {
                // Cancel any pending close — we are definitely still charging
                closeSessionJob?.cancel()
                closeSessionJob = null
                isStoppingExplicitly = false

                // Track the last packet where we confirmed charging was active.
                // Used to set endTime accurately regardless of when the debounce fires.
                lastChargingTelemetry = telemetry

                if (activeSession == null) {
                    startSession(telemetry, carConfig)
                } else {
                    recordDataPoint(telemetry)
                }

                // Schedule automatic close in case packets stop arriving
                // (e.g. car turns off mid-charge, Electro stops, phone loses connection).
                // Cancelled and rescheduled on every charging packet so it only fires
                // if no charging packet arrives within DEBOUNCE_MS.
                closeSessionJob = scope.launch {
                    delay(DEBOUNCE_MS)
                    Log.i(TAG, "No charging packet for ${DEBOUNCE_MS / 1000}s — auto-closing session")
                    closeSession(
                        telemetry = lastChargingTelemetry ?: telemetry,
                        carConfig = carConfig,
                        explicitStopPacket = false
                    )
                }
            } else {
                if (activeSession != null) {
                    // Explicit non-charging packet — definitive proof that charging
                    // stopped. Wait DEBOUNCE_MS before closing to avoid reacting to
                    // a single stray packet.
                    
                    if (isStoppingExplicitly) return@launch // Already winding down

                    isStoppingExplicitly = true
                    closeSessionJob?.cancel()
                    closeSessionJob = scope.launch {
                        try {
                            delay(DEBOUNCE_MS)
                            Log.i(TAG, "Charging stopped confirmed after ${DEBOUNCE_MS / 1000}s — closing session")
                            closeSession(
                                telemetry = telemetry,
                                carConfig = carConfig,
                                explicitStopPacket = true
                            )
                        } finally {
                            isStoppingExplicitly = false
                        }
                    }
                }
            }
        }
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    private suspend fun startSession(telemetry: VehicleTelemetry, carConfig: CarConfig?) {
        Log.i(TAG, "Charging session started — SoC: ${telemetry.soc}%")

        val session = ChargingSessionEntity(
            startTime       = System.currentTimeMillis(),
            socStart        = telemetry.soc,
            batteryTempStart = telemetry.batteryTempAvg,
            voltageStart    = telemetry.batteryTotalVoltage,
            batteryKwh      = carConfig?.batteryKwh ?: 0.0,
            carConfigId     = carConfig?.id ?: "",
            isActive        = true
        )

        val id = sessionDao.insertSession(session)
        activeSession = session.copy(id = id)
        _activeSessionId.value = id
        _isCharging.value = true

        pendingDataPoints.clear()
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
        pendingDataPoints.add(point)

        // Keep peak kW and real-time SOC up to date on the session row
        val currentPeak = activeSession?.peakKw ?: 0.0
        val needsPeakUpdate = telemetry.chargingPower > currentPeak
        val needsSocUpdate = telemetry.soc != activeSession?.socEnd

        if (needsPeakUpdate || needsSocUpdate) {
            activeSession = activeSession!!.copy(
                peakKw = if (needsPeakUpdate) telemetry.chargingPower else currentPeak,
                socEnd = telemetry.soc
            )
            sessionDao.updateSession(activeSession!!)
        }
    }

    private suspend fun closeSession(
        telemetry: VehicleTelemetry,
        carConfig: CarConfig?,
        explicitStopPacket: Boolean
    ) {
        val session = activeSession ?: return

        val dataPoints = sessionDao.getDataPointsForSessionSync(session.id)
        val lastDataPoint = dataPoints.lastOrNull()
        val lastCharging = lastChargingTelemetry ?: telemetry

        // When we receive an explicit non-charging packet, trust that packet for
        // final SoC / temperature / voltage because the last SoC increment often
        // becomes visible exactly when chargingPower drops to 0.
        //
        // For endTime, still anchor to the last confirmed charging sample rather
        // than the debounce fire time or the first non-charging packet, either
        // of which can be later than the actual end of energy transfer.
        val finalStateTelemetry =
            if (explicitStopPacket && !telemetry.isCharging) telemetry else lastCharging

        val endMs = lastDataPoint?.timestamp ?: runCatching {
            java.time.Instant.parse(lastCharging.currentDatetime).toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())

        Log.i(
            TAG,
            "Charging session closed — SoC: ${session.socStart}% → ${finalStateTelemetry.soc}%  endTime anchored to last charging sample"
        )

        val avgKw = if (dataPoints.isNotEmpty())
            dataPoints.map { it.chargingPower }.average()
        else 0.0

        val batteryKwh = carConfig?.batteryKwh ?: session.batteryKwh
        val socDelta   = (finalStateTelemetry.soc - session.socStart).coerceAtLeast(0.0)
        val kwhAdded   = (socDelta / 100.0) * batteryKwh

        val closed = session.copy(
            endTime        = endMs,
            socEnd         = finalStateTelemetry.soc,
            kwhAdded       = kwhAdded,
            avgKw          = avgKw,
            batteryTempEnd = finalStateTelemetry.batteryTempAvg,
            voltageEnd     = finalStateTelemetry.batteryTotalVoltage,
            isActive       = false
        )

        sessionDao.updateSession(closed)
        activeSession = null
        lastChargingTelemetry = null
        closeSessionJob?.cancel()
        closeSessionJob = null
        pendingDataPoints.clear()
        _activeSessionId.value = null
        _isCharging.value = false
    }

    // ── Startup recovery ─────────────────────────────────────────────────────

    /**
     * Called once on construction. Finds any session left with isActive = true
     * from a previous app run (crash, force-kill, or OOM) and closes it using
     * the timestamp of its last recorded data point as endTime.
     *
     * Without this, orphaned sessions show as "ongoing" in charging history
     * and a new charging session started after restart would create a duplicate
     * instead of resuming the orphaned one.
     */
    private suspend fun recoverOrphanedSession() {
        val orphan = sessionDao.getActiveSession() ?: return

        Log.w(TAG, "Found orphaned active session id=${orphan.id} — recovering")

        // Use last recorded data point timestamp as the best available endTime
        val dataPoints = sessionDao.getDataPointsForSessionSync(orphan.id)
        val lastDataPoint = dataPoints.lastOrNull()
        val endMs = lastDataPoint?.timestamp ?: orphan.startTime

        val avgKw = if (dataPoints.isNotEmpty())
            dataPoints.map { it.chargingPower }.average() else 0.0

        // SoC delta uses last data point's SoC, fallback to startSoc (0 kWh added)
        val socEnd   = lastDataPoint?.soc ?: orphan.socStart
        val socDelta = (socEnd - orphan.socStart).coerceAtLeast(0.0)
        val kwhAdded = (socDelta / 100.0) * orphan.batteryKwh

        sessionDao.updateSession(
            orphan.copy(
                endTime        = endMs,
                socEnd         = socEnd,
                kwhAdded       = kwhAdded,
                avgKw          = avgKw,
                batteryTempEnd = lastDataPoint?.batteryTempAvg,
                voltageEnd     = lastDataPoint?.batteryTotalVoltage,
                isActive       = false
            )
        )
        Log.i(TAG, "Orphaned session id=${orphan.id} closed — endTime anchored to last data point")
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
            sessionIds.forEach { sessionId ->
                sessionDao.deleteDataPointsForSession(sessionId)
                sessionDao.deleteSessionById(sessionId)
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
    }
}