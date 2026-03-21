package com.byd.tripstats.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.byd.tripstats.data.local.BydStatsDatabase
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

    @Before fun setUp() {
        // In-memory DB — safe because testApplicationId gives us our own process/data dir
        db = Room.inMemoryDatabaseBuilder(context, BydStatsDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Inject the in-memory DB as the singleton so TripRepository.getInstance() uses it
        BydStatsDatabase::class.java.getDeclaredField("INSTANCE")
            .also { it.isAccessible = true }.set(null, db)
        TripRepository::class.java.getDeclaredField("INSTANCE")
            .also { it.isAccessible = true }.set(null, null)

        repo = TripRepository.getInstance(context)
        repo.setAutoTripDetection(false)
        Thread.sleep(SETTLE)
        gen.reset()
    }

    @After fun tearDown() {
        TripRepository::class.java.getDeclaredField("INSTANCE")
            .also { it.isAccessible = true }.set(null, null)
        BydStatsDatabase::class.java.getDeclaredField("INSTANCE")
            .also { it.isAccessible = true }.set(null, null)
        db.close()
    }

    private fun drivingTelemetry(
        gear: String = "D",
        speed: Double = 10.0,
        odometer: Double = 1000.0
    ) = telemetry(gear = gear, speed = speed, enginePower = 10.0, carOn = 1, odometer = odometer)

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

    @Test fun autoDetectionEndsTripWhenCarTurnsOff() = runBlocking {
        repo.setAutoTripDetection(true)
        repo.processTelemetry(drivingTelemetry())
        Thread.sleep(SETTLE)
        assertTrue(repo.isInTrip.value)
        repo.processTelemetry(telemetry(gear = "P", speed = 0.0, carOn = 0))
        Thread.sleep(SETTLE_LONG)
        assertFalse("Trip should end when carOn=0", repo.isInTrip.value)
        assertNull(repo.currentTripId.value)
    }

    @Test fun completedTripIsPersistedInDatabase() = runBlocking {
        repo.setAutoTripDetection(true)
        repo.processTelemetry(drivingTelemetry(odometer = 1000.0))
        Thread.sleep(SETTLE)
        repo.processTelemetry(telemetry(gear = "P", speed = 0.0, odometer = 1005.0, carOn = 0))
        Thread.sleep(SETTLE_LONG)
        val trips = repo.getAllTrips().first()
        assertEquals(1, trips.size)
        assertFalse("Trip should be inactive after engine off", trips.first().isActive)
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

    @Test fun completeMockDriveResultsInOneCompletedTrip() = runBlocking {
        repo.setAutoTripDetection(true)
        gen.generateDriveSequence(60).forEach { t ->
            repo.processTelemetry(t)
            Thread.sleep(20)
        }
        repo.processTelemetry(telemetry(gear = "P", speed = 0.0, carOn = 0))
        Thread.sleep(SETTLE_LONG)
        val completed = repo.getAllTrips().first().filter { !it.isActive }
        assertEquals("Mock drive should produce one completed trip", 1, completed.size)
    }

    @Test fun tripDistanceIsPositiveAfterMockDrive() = runBlocking {
        repo.setAutoTripDetection(true)
        gen.generateDriveSequence(80).forEach { repo.processTelemetry(it); Thread.sleep(20) }
        repo.processTelemetry(telemetry(gear = "P", speed = 0.0, carOn = 0))
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
        val staleStartTime = System.currentTimeMillis() - 15 * 60 * 1000L
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
}