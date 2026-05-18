package com.byd.tripstats.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.byd.tripstats.data.local.BydStatsDatabase
import com.byd.tripstats.data.local.TestDatabaseRule
import org.junit.Rule
import com.byd.tripstats.mock.MockDataGenerator
import com.byd.tripstats.test.telemetry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for TripRepository using an in-memory Room DB.
 *
 * Safe to run via ./gradlew connectedAndroidTest because testApplicationId in
 * build.gradle.kts isolates these tests under com.byd.tripstats.test — a
 * completely separate package with its own data directory. The real app's
 * database at /data/data/com.byd.tripstats/ is never touched.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class TripRepositoryTest {

    private lateinit var db: BydStatsDatabase
    private lateinit var repo: TripRepository
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val gen = MockDataGenerator()

    private val SETTLE      = 600L
    private val SETTLE_LONG = 1500L

    @get:Rule val dbRule = TestDatabaseRule()

    @Before fun setUp() {
        // dbRule already injected the in-memory DB into BydStatsDatabase.INSTANCE
        db = dbRule.db
        TripRepository::class.java.getDeclaredField("INSTANCE")
            .also { it.isAccessible = true }.set(null, null)
        repo = TripRepository.getInstance(context)
        repo.setAutoTripDetection(false)
        Thread.sleep(SETTLE)
        gen.reset()
    }

    @After fun tearDown() {
        repo.close()
        TripRepository::class.java.getDeclaredField("INSTANCE")
            .also { it.isAccessible = true }.set(null, null)
        // BydStatsDatabase INSTANCE reset and db.close() handled by TestDatabaseRule.after()
    }

    private fun drivingTelemetry(
        gear: String = "D",
        speed: Double = 10.0,
        odometer: Double = 1000.0
    ) = telemetry(gear = gear, speed = speed, enginePower = 10, carOn = 2, odometer = odometer)

    @Test fun initiallyNotInTrip() {
        assertFalse(repo.isInTrip.value)
        assertNull(repo.currentTripId.value)
    }

    @Test fun initiallyNoTripsInDatabase() = runBlocking {
        assertTrue(repo.getAllTrips().first().isEmpty())
    }

    @Test fun autoDetectionStartsTripWhenGearShiftsToD() = runBlocking {
        repo.setAutoTripDetection(true)
        repo.processTelemetry(telemetry(gear = "P", speed = 0.0, carOn = 1))
        Thread.sleep(SETTLE)
        repo.processTelemetry(drivingTelemetry(gear = "D"))
        Thread.sleep(SETTLE)
        assertTrue("Should be in trip after D gear with speed>5", repo.isInTrip.value)
        assertNotNull(repo.currentTripId.value)
    }

    @Test fun autoDetectionDoesNotStartTripWhenDisabled() = runBlocking {
        repo.setAutoTripDetection(false)
        repo.processTelemetry(drivingTelemetry())
        Thread.sleep(SETTLE)
        assertFalse("Should NOT start trip when auto-detection off", repo.isInTrip.value)
    }

    @Test fun autoDetectionStartsTripForRGearAsWell() = runBlocking {
        repo.setAutoTripDetection(true)
        repo.processTelemetry(telemetry(gear = "P", carOn = 1))
        Thread.sleep(SETTLE)
        repo.processTelemetry(drivingTelemetry(gear = "R", speed = 6.0))
        Thread.sleep(SETTLE)
        assertTrue("R gear with speed>5 should start a trip", repo.isInTrip.value)
    }

    @Test fun autoDetectionDoesNotStartTripOnDriveGearWithoutMovementOrPower() = runBlocking {
        repo.setAutoTripDetection(true)
        repo.processTelemetry(telemetry(gear = "P", carOn = 1, odometer = 1000.0))
        Thread.sleep(SETTLE)
        repo.processTelemetry(
            telemetry(
                gear = "D",
                speed = 0.0,
                enginePower = 0,
                carOn = 2,
                odometer = 1000.0   // same odometer — no movement signal
            )
        )
        Thread.sleep(SETTLE)
        assertFalse(
            "Should not start on D gear alone without speed/power/odometer movement",
            repo.isInTrip.value
        )
    }

    @Test fun autoDetectionEndsTripWhenCarTurnsOff() = runBlocking {
        // Car-off does NOT end the trip immediately — the 30-min timeout
        // allows brief stops. This test verifies the trip survives a single
        // car-off packet (matching briefEngineOffStopDoesNotEndTripUntilDriverExits).
        repo.setAutoTripDetection(true)
        repo.processTelemetry(drivingTelemetry())
        Thread.sleep(SETTLE)
        assertTrue(repo.isInTrip.value)
        repo.processTelemetry(telemetry(gear = "P", speed = 0.0, carOn = 0, battery12vVoltage = 12.4))
        Thread.sleep(SETTLE_LONG)
        // Trip should remain active — engine-off does not immediately end recording
        assertTrue("Trip should still be active after single engine-off packet (30-min timeout)", repo.isInTrip.value)
        assertNotNull(repo.currentTripId.value)
    }

    @Test fun briefEngineOffStopDoesNotEndTripUntilDriverExits() = runBlocking {
        repo.setAutoTripDetection(true)
        repo.processTelemetry(drivingTelemetry())
        Thread.sleep(SETTLE)
        assertTrue(repo.isInTrip.value)

        repo.processTelemetry(telemetry(gear = "P", speed = 0.0, enginePower = 0, carOn = 0, battery12vVoltage = 12.4))
        Thread.sleep(SETTLE)

        assertTrue("Trip should remain active during a brief engine-off stop", repo.isInTrip.value)
        assertNotNull(repo.currentTripId.value)
    }

    @Test fun highTwelveVoltCarOffPacketsStillEndAfterTimeout() = runBlocking {
        repo.setAutoTripDetection(true)
        repo.processTelemetry(drivingTelemetry())
        Thread.sleep(SETTLE)
        assertTrue(repo.isInTrip.value)

        repo.processTelemetry(telemetry(gear = "P", speed = 0.0, enginePower = 0, carOn = 0, battery12vVoltage = 13.7))
        Thread.sleep(SETTLE)
        assertTrue("Trip should remain active during the continuation window", repo.isInTrip.value)

        TripRepository::class.java.getDeclaredField("carOffSinceMs").also {
            it.isAccessible = true
            it.setLong(repo, System.currentTimeMillis() - 31L * 60L * 1000L)
        }

        repo.processTelemetry(telemetry(gear = "P", speed = 0.0, enginePower = 0, carOn = 0, battery12vVoltage = 13.7))
        Thread.sleep(SETTLE_LONG)

        assertFalse("High 12V alone must not keep an engine-off trip alive forever", repo.isInTrip.value)
        assertNull(repo.currentTripId.value)
    }

    @Test fun tripEndsWhenCarIsLockedAfterStopping() = runBlocking {
        // Lock signal no longer ends trips — BYD auto-locks within seconds
        // of ACC-off which would prematurely end every brief stop.
        // Trip continues until the 30-min engine-off timeout.
        // This test verifies locking alone does NOT end the trip.
        repo.setAutoTripDetection(true)
        repo.processTelemetry(drivingTelemetry())
        Thread.sleep(SETTLE)
        assertTrue(repo.isInTrip.value)

        repo.processTelemetry(telemetry(gear = "P", speed = 0.0, enginePower = 0, carOn = 0, battery12vVoltage = 12.4))
        Thread.sleep(SETTLE)
        assertTrue(repo.isInTrip.value)

        repo.processTelemetry(
            telemetry(
                gear = "P",
                speed = 0.0,
                enginePower = 0,
                carOn = 0,
                carLocked = 1,
                battery12vVoltage = 12.4
            )
        )
        Thread.sleep(SETTLE_LONG)

        // Locking does not end trip — 30-min timeout is required
        assertTrue("Trip should remain active after locking — only 30-min timeout ends it", repo.isInTrip.value)
        assertNotNull(repo.currentTripId.value)
    }

    @Test fun completedTripIsPersistedInDatabase() = runBlocking {
        repo.setAutoTripDetection(true)
        repo.processTelemetry(drivingTelemetry(odometer = 1000.0))
        Thread.sleep(SETTLE)
        // Use manual stop to end the trip (30-min timeout not feasible in tests)
        repo.requestManualStop()
        Thread.sleep(SETTLE_LONG)
        val trips = repo.getAllTrips().first()
        assertEquals(1, trips.size)
        assertFalse("Trip should be inactive after manual stop", trips.first().isActive)
    }

    @Test fun manualStartCreatesAnActiveTrip() = runBlocking {
        repo.setAutoTripDetection(false)
        repo.processTelemetry(drivingTelemetry())
        Thread.sleep(SETTLE)
        repo.requestManualStart()
        Thread.sleep(SETTLE)
        assertTrue("Manual start should create active trip", repo.isInTrip.value)
    }

    @Test fun manualStopEndsAnActiveTrip() = runBlocking {
        repo.setAutoTripDetection(true)
        repo.processTelemetry(drivingTelemetry())
        Thread.sleep(SETTLE)
        assertTrue(repo.isInTrip.value)
        repo.requestManualStop()
        Thread.sleep(SETTLE_LONG)
        assertFalse("Manual stop should end trip", repo.isInTrip.value)
    }

    @Test fun manualTripIsFlaggedIsManualInDatabase() = runBlocking {
        repo.setAutoTripDetection(false)
        repo.processTelemetry(drivingTelemetry())
        Thread.sleep(SETTLE)
        repo.requestManualStart()
        Thread.sleep(SETTLE)
        repo.requestManualStop()
        Thread.sleep(SETTLE_LONG)
        val trips = repo.getAllTrips().first()
        assertTrue("Trip should be flagged as manual", trips.any { it.isManual })
    }

    @Test fun autoStartFromParkedPacketDoesNotBackAnchorJourneyCounter() = runBlocking {
        repo.setAutoTripDetection(true)

        repo.processTelemetry(telemetry(gear = "P", speed = 0.0, carOn = 0, odometer = 1000.0))
        Thread.sleep(SETTLE)

        repo.processTelemetry(
            telemetry(
                gear = "D",
                speed = 36.0,
                enginePower = 20,
                carOn = 2,
                odometer = 1000.6,
                totalDischarge = 500.5,
                currentJourneyDriveMileage = 0.6,
                currentJourneyDriveTime = 1.0
            )
        )
        Thread.sleep(SETTLE)

        repo.requestManualStop()
        Thread.sleep(SETTLE_LONG)

        val trip = repo.getAllTrips().first().single()
        assertEquals(
            "First observed driving packet after a parked packet should anchor from its own odometer",
            1000.6,
            trip.startOdometer,
            0.001
        )
        assertEquals(0.0, trip.distance ?: -1.0, 0.001)
    }

    @Test fun endTripPrefersReportedDischargeOverIntegratedPowerFallback() = runBlocking {
        repo.setAutoTripDetection(false)

        repo.processTelemetry(
            telemetry(
                gear = "D",
                speed = 50.0,
                enginePower = 50,
                carOn = 2,
                odometer = 1000.0,
                totalDischarge = 500.0
            )
        )
        Thread.sleep(SETTLE)

        repo.requestManualStart()
        Thread.sleep(SETTLE)

        repo.processTelemetry(
            telemetry(
                gear = "D",
                speed = 50.0,
                enginePower = 50,
                carOn = 2,
                odometer = 1001.0,
                totalDischarge = 500.5
            )
        )
        Thread.sleep(SETTLE)

        TripRepository::class.java.getDeclaredField("tripIntegratedDischargeKwh").also {
            it.isAccessible = true
            it.setDouble(repo, 2.0)
        }

        repo.requestManualStop()
        Thread.sleep(SETTLE_LONG)

        val trip = repo.getAllTrips().first().single()
        assertEquals(0.5, trip.energyConsumed ?: -1.0, 0.001)
    }

    @Test fun tripStatsAvgSpeedUsesDistanceOverDuration() = runBlocking {
        repo.setAutoTripDetection(false)

        repo.processTelemetry(
            telemetry(
                gear = "D",
                speed = 60.0,
                enginePower = 12,
                carOn = 2,
                odometer = 1000.0,
                totalDischarge = 500.0
            )
        )
        Thread.sleep(SETTLE)

        repo.requestManualStart()
        Thread.sleep(SETTLE)

        repo.processTelemetry(
            telemetry(
                gear = "D",
                speed = 60.0,
                enginePower = 12,
                carOn = 2,
                odometer = 1006.0,
                totalDischarge = 501.0
            )
        )
        Thread.sleep(SETTLE)

        val tripId = repo.currentTripId.value!!
        val activeTrip = db.tripDao().getTripById(tripId)!!
        db.tripDao().updateTrip(activeTrip.copy(startTime = activeTrip.startTime - 10L * 60L * 1000L))

        repo.requestManualStop()
        Thread.sleep(SETTLE_LONG)

        val completedTrip = db.tripDao().getTripById(tripId)!!
        val stats = db.tripStatsDao().getStatsForTrip(tripId)!!
        val expectedAvgSpeed = completedTrip.distance!! / (completedTrip.duration!!.toDouble() / 3_600_000.0)

        assertEquals(expectedAvgSpeed, stats.avgSpeed, 0.5)
        assertTrue("Distance/time average should be well below the raw 60 km/h sample speed", stats.avgSpeed < 50.0)
    }

    @Test fun completeMockDriveResultsInOneCompletedTrip() = runBlocking {
        repo.setAutoTripDetection(true)
        gen.generateDriveSequence(60).forEach { t ->
            repo.processTelemetry(t)
            Thread.sleep(20)
        }
        repo.requestManualStop()
        Thread.sleep(SETTLE_LONG)
        val completed = repo.getAllTrips().first().filter { !it.isActive }
        assertEquals("Mock drive should produce one completed trip", 1, completed.size)
    }

    @Test fun tripDistanceIsPositiveAfterMockDrive() = runBlocking {
        repo.setAutoTripDetection(true)
        gen.generateDriveSequence(80).forEach { repo.processTelemetry(it); Thread.sleep(20) }
        repo.requestManualStop()
        Thread.sleep(SETTLE_LONG)
        val trip = repo.getAllTrips().first().firstOrNull { !it.isActive }
        assertNotNull(trip)
        assertTrue("Trip distance should be > 0", (trip!!.distance ?: 0.0) > 0.0)
    }

    @Test fun noDataPointsWrittenWhenNotInTrip() = runBlocking {
        repo.setAutoTripDetection(false)
        repeat(10) {
            repo.processTelemetry(drivingTelemetry())
            Thread.sleep(50)
        }
        Thread.sleep(SETTLE)
        assertTrue("No trips without auto-detection", repo.getAllTrips().first().isEmpty())
    }

    @Test fun recoveryClosesOrphanedActiveTripOnStartup() = runBlocking {
        val staleStartTime = System.currentTimeMillis() - 35 * 60 * 1000L // > CAR_OFF_TIMEOUT_MS (30 min)
        val tripId = db.tripDao().insertTrip(
            com.byd.tripstats.data.local.entity.TripEntity(
                startTime           = staleStartTime,
                startOdometer       = 1000.0,
                startSoc            = 80.0,
                startTotalDischarge = 500.0,
                isActive            = true
            )
        )
        TripRepository::class.java.getDeclaredField("INSTANCE")
            .also { it.isAccessible = true }.set(null, null)
        val freshRepo = TripRepository.getInstance(context)
        Thread.sleep(SETTLE_LONG)
        val trip = db.tripDao().getTripById(tripId)
        assertFalse("Stale orphaned trip should be closed by recovery", trip!!.isActive)
        assertFalse("Fresh repo should not be in trip", freshRepo.isInTrip.value)
    }

    @Test fun recordedDataPointPersistsExtendedTelemetryFields() = runBlocking {
        repo.setAutoTripDetection(true)

        repo.processTelemetry(telemetry(gear = "P", speed = 0.0, carOn = 1, odometer = 1000.0))
        Thread.sleep(SETTLE)
        repo.processTelemetry(telemetry(gear = "D", speed = 10.0, enginePower = 10, carOn = 2, odometer = 1000.1))
        Thread.sleep(SETTLE)
        repo.processTelemetry(
            telemetry(
                gear = "D",
                speed = 16.0,
                enginePower = 18,
                carOn = 2,
                odometer = 1000.2,
                socPanel = 79,
                tyreTempLF = 31,
                tyreTempRF = 32,
                tyreTempLR = 33,
                tyreTempRR = 34
            )
        )
        Thread.sleep(SETTLE_LONG)

        val tripId = repo.currentTripId.value
        assertNotNull("Trip should be active so a data point can be inspected", tripId)

        val points = db.tripDataPointDao().getDataPointsForTripSync(tripId!!)
        assertTrue("Active trip should have at least one persisted data point", points.isNotEmpty())

        val point = points.last()
        assertEquals(79, point.socPanel)
        assertEquals(31, point.tyreTempLF)
        assertEquals(32, point.tyreTempRF)
        assertEquals(33, point.tyreTempLR)
        assertEquals(34, point.tyreTempRR)
        assertTrue("rawJson should preserve the full schema payload", point.rawJson.contains("\"car_on\":2"))
    }

    @Test fun invalidStartTempsDoNotPoisonTripMetrics() = runBlocking {
        repo.setAutoTripDetection(true)

        repo.processTelemetry(
            telemetry(
                gear = "D",
                speed = 10.0,
                enginePower = 10,
                carOn = 2,
                odometer = 1000.0,
                batteryPackTemp = 0.0,
                batteryCellTempMax = 0,
                batteryCellTempMin = 0
            )
        )
        Thread.sleep(SETTLE)

        repo.processTelemetry(
            telemetry(
                gear = "D",
                speed = 18.0,
                enginePower = 16,
                carOn = 2,
                odometer = 1000.2,
                batteryPackTemp = 28.0,
                batteryCellTempMax = 31,
                batteryCellTempMin = 27
            )
        )
        Thread.sleep(SETTLE)

        repo.requestManualStop()
        Thread.sleep(SETTLE_LONG)

        val trip = repo.getAllTrips().first().single()
        assertFalse("Trip should be completed after manual stop", trip.isActive)
        // Expected = (cellMin + cellMax) / 2 = (27 + 31) / 2 = 29.0.
        // batteryPackTemp is intentionally NOT consulted by VehicleTelemetry.batteryTempAvg
        // (m51 reads like a coolant probe, m36 sits several °C below cell temps under
        // active cooling) — see the comment on that getter. The test's role here is to
        // verify that the invalid zero-temp first packet doesn't drag the avg down, not
        // to pin the source to packTemp.
        assertEquals(29.0, trip.avgBatteryTemp, 0.001)
        assertEquals(31, trip.maxBatteryCellTemp)
        assertEquals(27, trip.minBatteryCellTemp)
    }
}
