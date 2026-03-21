package com.byd.tripstats.test

import com.byd.tripstats.data.model.VehicleTelemetry
import java.time.Instant

/**
 * Minimal VehicleTelemetry factory for integration tests (androidTest source set).
 * Every field has a safe default; override only what the test cares about.
 */
fun telemetry(
    gear          : String = "P",
    speed         : Double = 0.0,
    enginePower   : Double = 0.0,
    chargingPower : Double = 0.0,
    soc           : Double = 80.0,
    carOn         : Int    = 1,
    odometer      : Double = 1000.0,
    totalDischarge: Double = 500.0,
    socPanel      : Int    = 80,
    tyrePressureLF: Double = 38.5,
    tyrePressureRF: Double = 38.5,
    tyrePressureLR: Double = 42.0,
    tyrePressureRR: Double = 42.0,
    batteryTotalVoltage   : Int    = 570,
    batteryCellVoltageMax : Double = 3.333,
    batteryCellVoltageMin : Double = 3.326,
    electricDrivingRangeKm: Int    = 400,
    datetime      : String = Instant.now().toString()
): VehicleTelemetry = VehicleTelemetry(
    battery12vVoltage      = 13.4,
    batteryCellTempMax     = 25,
    batteryCellVoltageMax  = batteryCellVoltageMax,
    batteryCellTempMin     = 22,
    batteryCellVoltageMin  = batteryCellVoltageMin,
    currentDatetime        = datetime,
    odometer               = odometer,
    soc                    = soc,
    soh                    = 98,
    locationAltitude       = 50.0,
    chargingPower          = chargingPower,
    enginePower            = enginePower,
    engineSpeedFront       = (speed * 85).toInt(),
    gear                   = gear,
    locationLatitude       = 37.9838,
    locationLongitude      = 23.7275,
    engineSpeedRear        = (speed * 100).toInt(),
    speed                  = speed,
    wifiSsid               = "",
    batteryTotalVoltage    = batteryTotalVoltage,
    electricDrivingRangeKm = electricDrivingRangeKm,
    totalDischarge         = totalDischarge,
    carOn                  = carOn,
    tyrePressureLF         = tyrePressureLF,
    tyrePressureRF         = tyrePressureRF,
    tyrePressureLR         = tyrePressureLR,
    tyrePressureRR         = tyrePressureRR,
    socPanel               = socPanel
)