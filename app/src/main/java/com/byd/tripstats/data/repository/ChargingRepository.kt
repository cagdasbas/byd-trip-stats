package com.byd.tripstats.data.repository

import android.content.Context
import android.util.Log
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

    init {
        // Recover any session that was left open by a previous crash or force-kill
        recoverOrphanedSession()
    }

    // ── Public state ──────────────────────────────────────────────────────────

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    // ── Internal state ────────────────────────────────────────────────────────

    /**
     * Job-based debounce timer — fires automatically, no incoming packet required.
     *
     * Two timeouts depending on whether the car is on or off:
     *
     *   DC fast charging (car_on = 1, Electro publishes every 1 s):
     *     60 s — closes the session roughly one minute after unplugging.
     *
     *   AC home charging (car_on = 0, Electro publishes every 5–10 min):
     *     12 min — safely beyond the 10-min maximum publish interval, so a brief
     *     gap between packets never prematurely closes an ongoing overnight session.
     *     The session closes automatically once the charger is disconnected and the
     *     next slow packet (with chargingPower = 0) triggers a timer that fires
     *     12 minutes later.
     *
     * The timer is cancelled and restarted on every incoming charging packet,
     * so it only fires if the car genuinely stops sending charging data.
     */
    // Debounce windows for auto-close timer (when charging packets stop arriving):
    //   CAR_ON_DEBOUNCE  — Electro publishes every 1 s → 60 s of silence = problem
    //   CAR_OFF_DEBOUNCE — Electro publishes every 5 min → need > 5 min to survive gaps
    //                      Also covers DC toilet-break stops (< 20 min typically)
    private val CAR_ON_DEBOUNCE_MS  = 60_000L        //  1 min — car on (1 s publish interval)
    private val CAR_OFF_DEBOUNCE_MS = 12 * 60_000L   // 12 min — car off (5 min publish interval)

    private var closeSessionJob: Job? = null

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

    suspend fun getDataPointsForSessionSync(sessionId: Long): List<ChargingDataPointEntity> =
        sessionDao.getDataPointsForSessionSync(sessionId)

    /**
     * Feed each incoming telemetry packet here.
     * [carConfig] must be the currently selected car — used to compute kwhAdded on close.
     */
    fun onTelemetry(telemetry: VehicleTelemetry, carConfig: CarConfig?) {
        scope.launch {
            if (telemetry.isCharging) {
                // Cancel any pending close — we are definitely still charging
                closeSessionJob?.cancel()
                closeSessionJob = null

                // Track the last packet where we confirmed charging was active.
                // Used to set endTime accurately regardless of when the debounce fires.
                lastChargingTelemetry = telemetry

                if (activeSession == null) {
                    startSession(telemetry, carConfig)
                } else {
                    recordDataPoint(telemetry)
                }

                // Schedule automatic close in case packets stop arriving
                // (e.g. car goes to sleep mid-charge, Electro crashes, phone loses
                // connection). The job is cancelled and rescheduled on every charging
                // packet so it only fires if no charging packet arrives within the window.
                //
                // Distinguish DC vs AC by chargingPower magnitude — not car_on, which
                // is 0 whenever the car is in accessory/sleep mode during charging:
                //   ≥ 20 kW → DC fast charge → 60 s debounce (car is nearby, quick closure)
                //   <  20 kW → AC home charge → 12 min debounce (safe beyond 10-min interval)
                // Use car_on to select debounce — same logic as the else branch.
                // chargingPower >= 20 (DC) implies car_on = 1 in practice, but
                // car_on is the more direct signal and handles tapering correctly
                // (DC power can drop below 20 kW at end of charge while car stays on).
                val debounceMs = if (telemetry.isCarOn) CAR_ON_DEBOUNCE_MS else CAR_OFF_DEBOUNCE_MS
                closeSessionJob = scope.launch {
                    delay(debounceMs)
                    Log.i(TAG, "No charging packet for ${debounceMs / 1000}s — auto-closing session")
                    closeSession(lastChargingTelemetry ?: telemetry, carConfig)
                }
            } else {
                if (activeSession != null) {
                    // Explicit non-charging packet received — `chargingPower = 0` is
                    // definitive confirmation that charging stopped, regardless of whether
                    // the car is on or off or whether this was AC or DC.
                    //
                    // Always use CAR_ON_DEBOUNCE_MS (60 s) here because:
                    //   - DC, car on, manual stop → closes in 60 s ✅
                    //   - DC, car off, manual stop (toilet break ended, unplugged) →
                    //     closes in 60 s ✅ — we have an explicit signal
                    //   - AC, car off, completed overnight → closes in 60 s ✅
                    //     The next 5-min packet also shows 0 but session is already closed
                    //
                    // CAR_OFF_DEBOUNCE_MS (12 min) is only needed for the auto-close timer
                    // above — where packets *stop arriving* and we don't know why. It is
                    // wrong here because we already have explicit evidence charging ended.
                    closeSessionJob?.cancel()
                    closeSessionJob = scope.launch {
                        delay(CAR_ON_DEBOUNCE_MS)
                        Log.i(TAG, "Charging stopped confirmed after ${CAR_ON_DEBOUNCE_MS / 1000}s — closing session")
                        closeSession(lastChargingTelemetry ?: telemetry, carConfig)
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
            sessionId            = session.id,
            timestamp            = System.currentTimeMillis(),
            soc                  = telemetry.soc,
            socPanel             = telemetry.socPanel,
            chargingPower        = telemetry.chargingPower,
            batteryTotalVoltage  = telemetry.batteryTotalVoltage,
            battery12vVoltage    = telemetry.battery12vVoltage,
            batteryTempAvg       = telemetry.batteryTempAvg,
            batteryCellTempMin   = telemetry.batteryCellTempMin,
            batteryCellTempMax   = telemetry.batteryCellTempMax,
            batteryCellVoltageMin = telemetry.batteryCellVoltageMin,
            batteryCellVoltageMax = telemetry.batteryCellVoltageMax
        )

        sessionDao.insertDataPoint(point)
        pendingDataPoints.add(point)

        // Keep peak kW up to date on the session row
        if (telemetry.chargingPower > (activeSession?.peakKw ?: 0.0)) {
            activeSession = activeSession!!.copy(peakKw = telemetry.chargingPower)
            sessionDao.updateSession(activeSession!!)
        }
    }

    private suspend fun closeSession(telemetry: VehicleTelemetry, carConfig: CarConfig?) {
        val session = activeSession ?: return

        // Use lastChargingTelemetry as the source of truth for all closing metrics.
        // This anchors endTime, socEnd, and temperature to the last confirmed charging
        // packet rather than the debounce fire time or the first non-charging packet,
        // either of which can be minutes later than actual charging stopped.
        val last = lastChargingTelemetry ?: telemetry

        val endMs = runCatching {
            java.time.Instant.parse(last.currentDatetime).toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())

        Log.i(TAG, "Charging session closed — SoC: ${session.socStart}% → ${last.soc}%  endTime anchored to last charging packet")

        val dataPoints = sessionDao.getDataPointsForSessionSync(session.id)
        val avgKw = if (dataPoints.isNotEmpty())
            dataPoints.map { it.chargingPower }.average()
        else 0.0

        val batteryKwh = carConfig?.batteryKwh ?: session.batteryKwh
        val socDelta   = (last.soc - session.socStart).coerceAtLeast(0.0)
        val kwhAdded   = (socDelta / 100.0) * batteryKwh

        val closed = session.copy(
            endTime        = endMs,
            socEnd         = last.soc,
            kwhAdded       = kwhAdded,
            avgKw          = avgKw,
            batteryTempEnd = last.batteryTempAvg,
            voltageEnd     = last.batteryTotalVoltage,
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
    private fun recoverOrphanedSession() {
        scope.launch {
            val orphan = sessionDao.getActiveSession() ?: return@launch

            Log.w(TAG, "Found orphaned active session id=${orphan.id} — recovering")

            // Use last recorded data point timestamp as the best available endTime
            val dataPoints = sessionDao.getDataPointsForSessionSync(orphan.id)
            val endMs = dataPoints.lastOrNull()?.timestamp ?: orphan.startTime

            val avgKw = if (dataPoints.isNotEmpty())
                dataPoints.map { it.chargingPower }.average() else 0.0

            // SoC delta uses last data point's SoC, fallback to startSoc (0 kWh added)
            val socEnd  = dataPoints.lastOrNull()?.soc ?: orphan.socStart
            val socDelta = (socEnd - orphan.socStart).coerceAtLeast(0.0)
            val kwhAdded = (socDelta / 100.0) * orphan.batteryKwh

            sessionDao.updateSession(
                orphan.copy(
                    endTime  = endMs,
                    socEnd   = socEnd,
                    kwhAdded = kwhAdded,
                    avgKw    = avgKw,
                    isActive = false
                )
            )
            Log.i(TAG, "Orphaned session id=${orphan.id} closed — endTime anchored to last data point")
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