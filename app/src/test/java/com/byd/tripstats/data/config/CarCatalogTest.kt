package com.byd.tripstats.data.config

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for CarCatalog lookup and CarConfig value sanity.
 * Pure JVM.
 */
class CarCatalogTest {

    // ── fromId ────────────────────────────────────────────────────────────────

    @Test fun `fromId returns correct car for known id`() {
        val car = CarCatalog.fromId("BYD_SEAL_EXCELLENCE")
        assertNotNull(car)
        assertEquals("BYD Seal Excellence", car!!.displayName)
        assertEquals(Drivetrain.AWD, car.drivetrain)
    }

    @Test fun `fromId returns null for unknown id`() {
        assertNull(CarCatalog.fromId("UNKNOWN_CAR"))
        assertNull(CarCatalog.fromId(null))
        assertNull(CarCatalog.fromId(""))
    }

    @Test fun `fromId is case sensitive`() {
        assertNull(CarCatalog.fromId("byd_seal_excellence"))
    }

    @Test fun `all catalog IDs are unique`() {
        val ids = CarCatalog.allCars.map { it.id }
        assertEquals("Duplicate IDs found", ids.size, ids.toSet().size)
    }

    // ── Value sanity ──────────────────────────────────────────────────────────

    @Test fun `all cars have positive batteryKwh`() {
        CarCatalog.allCars.forEach { car ->
            assertTrue("${car.id} batteryKwh must be > 0", car.batteryKwh > 0.0)
        }
    }

    @Test fun `all cars have positive wltpKm`() {
        CarCatalog.allCars.forEach { car ->
            assertTrue("${car.id} wltpKm must be > 0", car.wltpKm > 0)
        }
    }

    @Test fun `referenceConsumption converts to valid Wh per km range`() {
        // Real EVs: typically 120–250 Wh/km
        CarCatalog.allCars.forEach { car ->
            val whPerKm = car.referenceConsumptionKwhPer100km * 10.0
            assertTrue("${car.id} Wh/km ($whPerKm) below minimum", whPerKm >= 100.0)
            assertTrue("${car.id} Wh/km ($whPerKm) above maximum", whPerKm <= 300.0)
        }
    }

    @Test fun `tyre pressures are in plausible bar range`() {
        CarCatalog.allCars.forEach { car ->
            assertTrue("${car.id} front pressure too low",  car.frontTyrePressureBar >= 2.0)
            assertTrue("${car.id} front pressure too high", car.frontTyrePressureBar <= 3.5)
            assertTrue("${car.id} rear pressure too low",   car.rearTyrePressureBar  >= 2.0)
            assertTrue("${car.id} rear pressure too high",  car.rearTyrePressureBar  <= 3.5)
        }
    }

    @Test fun `reference consumption is higher than WLTP implied efficiency`() {
        // WLTP range is measured at ideal conditions — real-world reference consumption
        // is always higher than batteryKwh/wltpKm*100 (the theoretical WLTP-implied value).
        // The ratio across all BYD catalog cars is 1.15x–1.35x.
        CarCatalog.allCars.forEach { car ->
            val wltpImplied = car.batteryKwh / car.wltpKm * 100.0
            val ref = car.referenceConsumptionKwhPer100km
            assertTrue(
                "${car.id}: reference ($ref) should exceed WLTP-implied ($wltpImplied)",
                ref > wltpImplied
            )
            val ratio = ref / wltpImplied
            assertTrue(
                "${car.id}: ref/implied ratio $ratio out of plausible 1.1–1.4 range",
                ratio in 1.1..1.4
            )
        }
    }

    // ── Drivetrain ────────────────────────────────────────────────────────────

    @Test fun `Seal Excellence is AWD`() {
        assertEquals(Drivetrain.AWD, CarCatalog.BYD_SEAL_EXCELLENCE.drivetrain)
    }

    @Test fun `Seal Dynamic and Premium are RWD`() {
        assertEquals(Drivetrain.RWD, CarCatalog.BYD_SEAL_DYNAMIC_RWD.drivetrain)
        assertEquals(Drivetrain.RWD, CarCatalog.BYD_SEAL_PREMIUM_RWD.drivetrain)
    }

    @Test fun `Dolphin and Atto 3 are FWD`() {
        assertEquals(Drivetrain.FWD, CarCatalog.BYD_DOLPHIN_STANDARD.drivetrain)
        assertEquals(Drivetrain.FWD, CarCatalog.BYD_DOLPHIN_EXTENDED.drivetrain)
        assertEquals(Drivetrain.FWD, CarCatalog.BYD_ATTO_3.drivetrain)
    }

    @Test fun `catalog contains all 6 expected cars`() {
        assertEquals(6, CarCatalog.allCars.size)
    }
}