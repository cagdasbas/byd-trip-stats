@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.byd.tripstats.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VehicleTelemetry(
    // ── Required on all models / Electro versions ─────────────────────────────
    @SerialName("soc")            val soc: Double,
    @SerialName("speed")          val speed: Double,
    @SerialName("gear")           val gear: String,
    @SerialName("odometer")       val odometer: Double,
    @SerialName("engine_power")   val enginePower: Double,
    @SerialName("total_discharge") val totalDischarge: Double,

    // ── Optional — safe defaults so parsing never fails on missing keys ────────
    // FWD models (Atto 3, Dolphin) omit engine_speed_rear entirely.
    // Older Electro builds may omit soh, cell voltages, location, or wifi_ssid.
    @SerialName("battery_12v_voltage")        val battery12vVoltage: Double = 0.0,
    @SerialName("battery_cell_temp_max")      val batteryCellTempMax: Int = 0,
    @SerialName("battery_cell_voltage_max")   val batteryCellVoltageMax: Double = 0.0,
    @SerialName("battery_cell_temp_min")      val batteryCellTempMin: Int = 0,
    @SerialName("battery_cell_voltage_min")   val batteryCellVoltageMin: Double = 0.0,
    @SerialName("current_datetime")           val currentDatetime: String = "",
    @SerialName("soh")                        val soh: Int = 0,
    @SerialName("location_altitude")          val locationAltitude: Double = 0.0,
    @SerialName("charging_power")             val chargingPower: Double = 0.0,
    @SerialName("engine_speed_front")         val engineSpeedFront: Int = 0,
    @SerialName("location_latitude")          val locationLatitude: Double = 0.0,
    @SerialName("location_longitude")         val locationLongitude: Double = 0.0,
    @SerialName("engine_speed_rear")          val engineSpeedRear: Int = 0,   // absent on FWD
    @SerialName("wifi_ssid")                  val wifiSsid: String = "",
    @SerialName("battery_total_voltage")      val batteryTotalVoltage: Int = 0,
    @SerialName("electric_driving_range_km")  val electricDrivingRangeKm: Int = 0,
    @SerialName("car_on")                     val carOn: Int = 0,
    @SerialName("tyre_pressure_left_front_psi")  val tyrePressureLF: Double = 0.0,
    @SerialName("tyre_pressure_right_front_psi") val tyrePressureRF: Double = 0.0,
    @SerialName("tyre_pressure_left_rear_psi")   val tyrePressureLR: Double = 0.0,
    @SerialName("tyre_pressure_right_rear_psi")  val tyrePressureRR: Double = 0.0,
    // ── New fields (Electro telemetry v2) ─────────────────────────────────────
    @SerialName("tyre_temperature_left_front_c")  val tyreTempLF: Int = 0,
    @SerialName("tyre_temperature_right_front_c") val tyreTempRF: Int = 0,
    @SerialName("tyre_temperature_left_rear_c")   val tyreTempLR: Int = 0,
    @SerialName("tyre_temperature_right_rear_c")  val tyreTempRR: Int = 0,
    /** Displayed SoC on the car's instrument panel — may differ slightly from raw soc */
    @SerialName("soc_panel")        val socPanel: Int = 0,
    @SerialName("car_locked")       val carLocked: Int = 0,
    @SerialName("any_door_opened")  val anyDoorOpened: Int = 0,
    /** PHEV-only — always 0 on BEV models */
    @SerialName("fuel_percentage")      val fuelPercentage: Int = 0,
    /** PHEV-only — always 0 on BEV models */
    @SerialName("fuel_driving_range_km") val fuelDrivingRangeKm: Int = 0
) {
    // ── Computed helpers ──────────────────────────────────────────────────────

    val isCarOn: Boolean
        get() = carOn == 1

    val isCharging: Boolean
        get() = chargingPower > 0

    val isRegenerating: Boolean
        // -1.0 kW threshold filters out sensor noise at standstill.
        get() = enginePower < -1.0

    val isDriving: Boolean
        get() = gear in listOf("D", "R")

    val isMoving: Boolean
        get() = gear in listOf("D", "R") && speed > 0

    val isParked: Boolean
        get() = gear == "P"

    val batteryTempAvg: Double
        get() = (batteryCellTempMax + batteryCellTempMin) / 2.0

    /**
     * Serialises only non-default fields to rawJson to minimise storage.
     *
     * On a non-charging BEV during normal driving every field is at its default,
     * so the result is "{}" (2 bytes) vs the previous static string (38+ bytes) —
     * a ~94% reduction on the rawJson column for the vast majority of rows.
     *
     * Fields stored here (not promoted to first-class columns):
     *   chargingPower  — available via isCharging; not worth a column yet
     *   wifiSsid       — informational only
     *   carLocked      — display state, not analytically useful per-point
     *   anyDoorOpened  — same
     *   fuelPercentage / fuelDrivingRangeKm — PHEV-only, always 0 on BEV
     *
     * To promote a field: add column to TripDataPointEntity, bump DB version,
     * write ALTER TABLE migration, map it in recordDataPoint(), remove from here.
     */
    fun toRawJson(isPhev: Boolean = false): String {
        val fields = buildList {
            if (chargingPower > 0.0)    add(""""charging_power":$chargingPower""")
            if (wifiSsid.isNotBlank())  add(""""wifi_ssid":"$wifiSsid"""")
            if (carLocked != 0)         add(""""car_locked":$carLocked""")
            if (anyDoorOpened != 0)     add(""""any_door_opened":$anyDoorOpened""")
            // Only write PHEV fields when the selected car is actually a PHEV
            if (isPhev && fuelPercentage != 0)     add(""""fuel_percentage":$fuelPercentage""")
            if (isPhev && fuelDrivingRangeKm != 0) add(""""fuel_driving_range_km":$fuelDrivingRangeKm""")
        }
        return if (fields.isEmpty()) "{}" else "{${fields.joinToString(",")}}"
    }
}