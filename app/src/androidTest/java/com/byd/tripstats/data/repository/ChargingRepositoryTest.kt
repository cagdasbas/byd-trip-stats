package com.byd.tripstats.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.byd.tripstats.data.config.CarCatalog
import com.byd.tripstats.data.local.BydStatsDatabase
import com.byd.tripstats.data.local.entity.ChargingSessionEntity
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
 * Integration tests for ChargingRepository using an in-memory Room DB.
 *
 * Safe to run via ./gradlew connectedAndroidTest because testApplicationId in
 * build.gradle.kts isolates these tests under com.byd.tripstats.test — a
 * completely separate package with its own data directory. The real app's
 * database at /data/data/com.byd.tripstats/ is never touched.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ChargingRepositoryTest {

    private lateinit var db: BydStatsDatabase
    private lateinit var repo: ChargingRepository
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val car = CarCatalog.BYD_SEAL_EXCELLENCE
    private val gen = MockDataGenerator()
    private val SETTLE = 400L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, BydStatsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        BydStatsDatabase::class.java.getDeclaredField("INSTANCE")
            .also { it.isAccessible = true }.set(null, db)
        ChargingRepository::class.java.getDeclaredField("INSTANCE")
            .also { it.isAccessible = true }.set(null, null)
        repo = ChargingRepository.getInstance(context)
        Thread.sleep(SETTLE)
        gen.reset()
    }

    @After fun tearDown() {
        ChargingRepository::class.java.getDeclaredField("INSTANCE")
            .also { it.isAccessible = true }.set(null, null)
        BydStatsDatabase::class.java.getDeclaredField("INSTANCE")
            .also { it.isAccessible = true }.set(null, null)
        db.close()
    }

    @Test fun initiallyNotCharging() {
        assertFalse(repo.isCharging.value)
        assertNull(repo.activeSessionId.value)
    }

    @Test fun initiallyNoSessionsInDatabase() = runBlocking {
        assertTrue(repo.getAllSessions().first().isEmpty())
    }

    @Test fun aCChargingPacketOpensASession() = runBlocking {
        repo.onTelemetry(gen.generateAcChargingTelemetry(chargingPower = 7.2), car)
        Thread.sleep(SETTLE)
        assertTrue("Should be charging", repo.isCharging.value)
        assertNotNull("Active session ID should be set", repo.activeSessionId.value)
    }

    @Test fun sessionStoresCorrectStartingSoC() = runBlocking {
        repo.onTelemetry(gen.generateAcChargingTelemetry(soc = 45.0), car)
        Thread.sleep(SETTLE)
        val session = repo.getSessionById(repo.activeSessionId.value!!)
        assertNotNull(session)
        assertEquals(45.0, session!!.socStart, 0.1)
    }

    @Test fun sessionIsMarkedActiveWhileCharging() = runBlocking {
        repo.onTelemetry(gen.generateAcChargingTelemetry(), car)
        Thread.sleep(SETTLE)
        val sessions = repo.getAllSessions().first()
        assertEquals(1, sessions.size)
        assertTrue("Session should be active", sessions.first().isActive)
    }

    @Test fun dCChargingPacketAlsoOpensASession() = runBlocking {
        repo.onTelemetry(gen.generateDcChargingTelemetry(chargingPower = 50.0), car)
        Thread.sleep(SETTLE)
        assertTrue("DC charging should open session", repo.isCharging.value)
    }

    @Test fun peakKWUpdatedDuringDCSession() = runBlocking {
        repo.onTelemetry(gen.generateDcChargingTelemetry(chargingPower = 50.0), car)
        Thread.sleep(SETTLE)
        repo.onTelemetry(gen.generateDcChargingTelemetry(chargingPower = 80.0), car)
        Thread.sleep(SETTLE)
        val session = repo.getSessionById(repo.activeSessionId.value!!)
        assertNotNull(session)
        assertEquals(80.0, session!!.peakKw, 0.1)
    }

    @Test fun multipleConsecutiveChargingPacketsResultInOneSession() = runBlocking {
        repeat(5) {
            repo.onTelemetry(gen.generateAcChargingTelemetry(), car)
            Thread.sleep(50)
        }
        Thread.sleep(SETTLE)
        assertEquals(1, repo.getAllSessions().first().size)
    }

    @Test fun orphanedActiveSessionIsClosedOnRepositoryCreation() = runBlocking {
        val sessionId = db.chargingSessionDao().insertSession(
            ChargingSessionEntity(
                startTime        = System.currentTimeMillis() - 120_000,
                socStart         = 40.0,
                batteryTempStart = 22.0,
                voltageStart     = 450,
                batteryKwh       = 82.5,
                carConfigId      = "BYD_SEAL_EXCELLENCE",
                isActive         = true
            )
        )
        ChargingRepository::class.java.getDeclaredField("INSTANCE")
            .also { it.isAccessible = true }.set(null, null)
        val freshRepo = ChargingRepository.getInstance(context)
        Thread.sleep(SETTLE)
        val session = db.chargingSessionDao().getSessionById(sessionId)
        assertFalse("Orphaned session should be closed", session!!.isActive)
        assertFalse("Fresh repo should not be charging", freshRepo.isCharging.value)
    }

    @Test fun isChargingFlipsTrueAfterChargingPacket() = runBlocking {
        assertFalse(repo.isCharging.value)
        repo.onTelemetry(gen.generateAcChargingTelemetry(), car)
        Thread.sleep(SETTLE)
        assertTrue(repo.isCharging.value)
    }

    @Test fun completedSessionsVisibleInHistory() = runBlocking {
        repeat(2) { i ->
            db.chargingSessionDao().insertSession(
                ChargingSessionEntity(
                    startTime        = System.currentTimeMillis() - (i + 1) * 3_600_000L,
                    socStart         = 30.0 + i * 20,
                    batteryTempStart = 20.0,
                    voltageStart     = 400,
                    batteryKwh       = 82.5,
                    carConfigId      = "BYD_SEAL_EXCELLENCE",
                    isActive         = false,
                    socEnd           = 60.0 + i * 20,
                    kwhAdded         = 24.75
                )
            )
        }
        val sessions = repo.getAllSessions().first()
        assertEquals(2, sessions.filter { !it.isActive }.size)
    }
}