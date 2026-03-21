package com.byd.tripstats.mock

import com.byd.tripstats.data.model.VehicleTelemetry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import kotlin.math.sin
import kotlin.random.Random

/**
 * Mock telemetry generator used by both the in-app mock drive button and the
 * unit / integration test suite.
 *
 * Simulates a realistic BYD Seal AWD Excellence drive cycle:
 *   0–15 %  → acceleration to 80 km/h
 *   15–70 % → motorway cruise at ~80 km/h
 *   70–85 % → regen deceleration
 *   85–100% → final stop and park
 *
 * All VehicleTelemetry fields are populated, including v2 fields
 * (tyrePressures, tyreTempLF/RF/LR/RR, socPanel, carOn, etc.).
 */
class MockDataGenerator {

    // Starting state — realistic values for a Seal Excellence mid-trip
    private var currentOdometer      = 23_366.3
    private var currentTotalDischarge = 4_762.6
    private var currentSoc           = 97.6
    private var currentSpeed         = 0.0
    private var currentPower         = 0.0
    private var currentSocPanel      = 97   // instrument cluster SoC (integer %)

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Emits a sequence of telemetry packets simulating a complete drive.
     *
     * @param durationSeconds  Total simulated drive time
     * @param updateIntervalMs Interval between packets (mirrors Electro publish rate)
     */
    fun generateMockDrive(
        durationSeconds:  Int  = 120,
        updateIntervalMs: Long = 1_000
    ): Flow<VehicleTelemetry> = flow {
        val totalUpdates = (durationSeconds * 1_000 / updateIntervalMs).toInt()
        for (i in 0..totalUpdates) {
            emit(generateTelemetryForProgress(i.toFloat() / totalUpdates))
            delay(updateIntervalMs)
        }
    }

    /**
     * Generates a [count] list of telemetry packets without any delay.
     * Useful in tests where you need deterministic data without coroutine timing.
     */
    fun generateDriveSequence(count: Int = 60): List<VehicleTelemetry> {
        reset()
        return (0 until count).map { i ->
            generateTelemetryForProgress(i.toFloat() / count)
        }
    }

    /** Returns a single parked-car telemetry snapshot. */
    fun generateParkedTelemetry(): VehicleTelemetry = VehicleTelemetry(
        battery12vVoltage    = 12.8,
        batteryCellTempMax   = 18,
        batteryCellVoltageMax = 3.331,
        batteryCellTempMin   = 16,
        batteryCellVoltageMin = 3.328,
        currentDatetime      = Instant.now().toString(),
        odometer             = currentOdometer,
        soc                  = currentSoc,
        soh                  = 98,
        locationAltitude     = 50.0,
        chargingPower        = 0.0,
        enginePower          = 0.0,
        engineSpeedFront     = 0,
        gear                 = "P",
        locationLatitude     = 37.9838,
        locationLongitude    = 23.7275,
        engineSpeedRear      = 0,
        speed                = 0.0,
        wifiSsid             = "",
        batteryTotalVoltage  = 573,
        electricDrivingRangeKm = (currentSoc * 5.2).toInt(),
        totalDischarge       = currentTotalDischarge,
        carOn                = 0,
        tyrePressureLF       = 38.5,
        tyrePressureRF       = 38.5,
        tyrePressureLR       = 42.0,
        tyrePressureRR       = 42.0,
        tyreTempLF           = 22,
        tyreTempRF           = 22,
        tyreTempLR           = 22,
        tyreTempRR           = 22,
        socPanel             = currentSocPanel,
        carLocked            = 1,
        anyDoorOpened        = 0
    )

    /** Generates a charging telemetry packet (AC, ~7 kW). */
    fun generateAcChargingTelemetry(
        chargingPower: Double = 7.2,
        soc: Double = currentSoc
    ): VehicleTelemetry = generateParkedTelemetry().copy(
        chargingPower = chargingPower,
        soc           = soc,
        carOn         = 0,
        gear          = "P"
    )

    /** Generates a DC fast-charging packet (50 kW). */
    fun generateDcChargingTelemetry(
        chargingPower: Double = 50.0,
        soc: Double = currentSoc
    ): VehicleTelemetry = generateParkedTelemetry().copy(
        chargingPower = chargingPower,
        soc           = soc,
        carOn         = 1,
        gear          = "P"
    )

    /** Resets internal state back to initial values. */
    fun reset() {
        currentOdometer       = 23_366.3
        currentTotalDischarge = 4_762.6
        currentSoc            = 97.6
        currentSpeed          = 0.0
        currentPower          = 0.0
        currentSocPanel       = 97
    }

    // ── Internal generation ───────────────────────────────────────────────────

    private fun generateTelemetryForProgress(progress: Float): VehicleTelemetry {
        val phase = when {
            progress < 0.15f -> "acceleration"
            progress < 0.70f -> "cruising"
            progress < 0.85f -> "deceleration"
            else             -> "stopping"
        }

        currentSpeed = when (phase) {
            "acceleration" -> (progress / 0.15f) * 80.0
            "cruising"     -> 80.0 + sin(progress * 10.0) * 5.0
            "deceleration" -> 80.0 * (1.0 - (progress - 0.70f) / 0.15f)
            else           -> maxOf(0.0, 20.0 * (1.0 - (progress - 0.85f) / 0.15f))
        }

        currentPower = when (phase) {
            "acceleration" -> 30.0 + Random.nextDouble(-5.0, 10.0)
            "cruising"     -> 15.0 + Random.nextDouble(-3.0,  3.0)
            "deceleration" -> -25.0 + Random.nextDouble(-10.0, 5.0)
            else           -> -15.0 + Random.nextDouble(-5.0,  2.0)
        }

        // Odometer — km per second at current speed
        currentOdometer += currentSpeed / 3_600.0

        // SoC — discharge or recover (regen)
        if (currentPower > 0) {
            val energyKwh = currentPower / 3_600.0
            currentTotalDischarge += energyKwh
            currentSoc -= energyKwh / 82.5 * 100.0
        } else {
            val recovered = -currentPower * 0.7 / 3_600.0
            currentSoc   += recovered / 82.5 * 100.0
        }
        currentSoc = currentSoc.coerceIn(0.0, 100.0)
        currentSocPanel = currentSoc.toInt()  // simplified — usually ±1 of soc

        val gear  = if (progress > 0.95f) "P" else "D"
        val carOn = if (progress > 0.97f) 0 else 1

        val baseTemp   = 22
        val tempOffset = (sin(progress * 20.0) * 3.0).toInt()

        // Tyre pressures rise slightly as tyres warm (PSI)
        val tyrePressureBase = 38.5 + progress * 1.5

        return VehicleTelemetry(
            battery12vVoltage     = 13.4 + Random.nextDouble(-0.3, 0.3),
            batteryCellTempMax    = baseTemp + tempOffset + 2,
            batteryCellVoltageMax = 3.331 + Random.nextDouble(-0.005, 0.005),
            batteryCellTempMin    = baseTemp + tempOffset - 2,
            batteryCellVoltageMin = 3.328 + Random.nextDouble(-0.005, 0.005),
            currentDatetime       = Instant.now().toString(),
            odometer              = currentOdometer,
            soc                   = currentSoc,
            soh                   = 98,
            locationAltitude      = 50.0 + Random.nextDouble(-5.0, 5.0),
            chargingPower         = 0.0,
            enginePower           = currentPower,
            engineSpeedFront      = if (gear == "D") (currentSpeed * 85).toInt() else 0,
            gear                  = gear,
            locationLatitude      = 37.9838 + progress * 0.05,
            locationLongitude     = 23.7275 + progress * 0.04,
            engineSpeedRear       = if (gear == "D") (currentSpeed * 100).toInt() else 0,
            speed                 = currentSpeed,
            wifiSsid              = "",
            batteryTotalVoltage   = (560 + currentSoc * 0.15).toInt(),
            electricDrivingRangeKm = (currentSoc * 5.2).toInt(),
            totalDischarge        = currentTotalDischarge,
            carOn                 = carOn,
            tyrePressureLF        = tyrePressureBase + Random.nextDouble(-0.3, 0.3),
            tyrePressureRF        = tyrePressureBase + Random.nextDouble(-0.3, 0.3),
            tyrePressureLR        = tyrePressureBase + 3.5 + Random.nextDouble(-0.3, 0.3),
            tyrePressureRR        = tyrePressureBase + 3.5 + Random.nextDouble(-0.3, 0.3),
            tyreTempLF            = 22 + (progress * 15).toInt(),
            tyreTempRF            = 22 + (progress * 15).toInt(),
            tyreTempLR            = 21 + (progress * 14).toInt(),
            tyreTempRR            = 21 + (progress * 14).toInt(),
            socPanel              = currentSocPanel,
            carLocked             = 0,
            anyDoorOpened         = 0
        )
    }
}