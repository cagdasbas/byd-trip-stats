package com.byd.tripstats.data.preferences

enum class UnitSystem { METRIC, IMPERIAL }

private const val KM_TO_MI = 0.621371

/** Convert a distance calculated/stored in km to the display unit. Telemetry odometer/speed are not converted — the BMS already reports them in the correct unit for the vehicle's market. */
fun UnitSystem.convertDistance(km: Double): Double =
    if (this == UnitSystem.IMPERIAL) km * KM_TO_MI else km

/** Convert efficiency stored as kWh/100km to the display unit (kWh/100mi for imperial). */
fun UnitSystem.convertEfficiency(kwhPer100km: Double): Double =
    if (this == UnitSystem.IMPERIAL) kwhPer100km / KM_TO_MI else kwhPer100km

val UnitSystem.distanceUnit: String get() = if (this == UnitSystem.IMPERIAL) "mi" else "km"
val UnitSystem.speedUnit: String get() = if (this == UnitSystem.IMPERIAL) "mph" else "km/h"
val UnitSystem.consumptionUnit: String get() = if (this == UnitSystem.IMPERIAL) "kWh/100mi" else "kWh/100km"
val UnitSystem.isImperial: Boolean get() = this == UnitSystem.IMPERIAL
