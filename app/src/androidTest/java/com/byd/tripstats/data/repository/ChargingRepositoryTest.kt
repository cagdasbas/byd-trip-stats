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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for ChargingRepository (SoC-delta reconstruction approach).
 *
 * The repository no longer performs real-time session detection. Instead it:
 *   1. Persists SoC + timestamp to SharedPreferences on every telemetry packet.
 *   2. On the FIRST packet after service start, compares current SoC against the
 *      last persisted value and creates a synthetic session if the delta is meaningful.
 *
 * Tests simulate "previous shutdown state" by writing directly to the SharedPreferences
 * key names used by the repository before calling onTelemetry.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ChargingRepositoryTest {

    private lateinit var db: BydStatsDatabase
    private lateinit var repo: ChargingRepository
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val car = CarCatalog.BYD_SEAL_EXCELLENCE  // 82.5 kWh
    private val gen = MockDataGenerator()
    private val SETTLE = 400L

    // Must match ChargingRepository.companion constants
    private val PREFS_NAME   = "charging_shutdown_state"
    private val KEY_SOC      = "last_soc"
    private val KEY_TS       = "last_timestamp"
    private val KEY_TEMP     = "last_temp_avg"
    private val KEY_VOLTAGE  = "last_voltage"

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, BydStatsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        BydStatsDatabase::class.java.getDeclaredField("INSTANCE")
            .also { it.isAccessible = true }.set(null, db)
        ChargingRepository::class.java.getDeclaredField("INSTANCE")
            .also { it.isAccessible = true }.set(null, null)
        // Clear SharedPreferences so each test starts with no prior state
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        repo = ChargingRepository.getInstance(context)
        Thread.sleep(SETTLE)
        gen.reset()
    }

    @After fun tearDown() {
        ChargingRepository::class.java.getDeclaredField("INSTANCE")
            .also { it.isAccessible = true }.set(null, null)
        BydStatsDatabase::class.java.getDeclaredField("INSTANCE")
            .also { it.isAccessible = true }.set(null, null)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        db.close()
    }

    // ── State invariants ──────────────────────────────────────────────────────

    @Test fun isChargingAlwaysFalse() {
        assertFalse("isCharging should always be false (no real-time detection)", repo.isCharging.value)
    }

    @Test fun activeSessionIdAlwaysNull() {
        assertNull("activeSessionId should always be null", repo.activeSessionId.value)
    }

    @Test fun initiallyNoSessionsInDatabase() = runBlocking {
        assertTrue(repo.getAllSessions().first().isEmpty())
    }

    // ── First-ever run (no prior state) ───────────────────────────────────────

    @Test fun firstRunWithNoSavedStateCreatesNoSession() = runBlocking {
        // No SharedPreferences written — simulates fresh install
        repo.onTelemetry(gen.generateAcChargingTelemetry(soc = 80.0), car)
        Thread.sleep(SETTLE)
        assertTrue("No session should be created on first ever run",
            repo.getAllSessions().first().isEmpty())
    }

    @Test fun firstRunPersistsCurrentSoCForNextWakeUp() = runBlocking {
        repo.onTelemetry(gen.generateAcChargingTelemetry(soc = 75.0), car)
        Thread.sleep(SETTLE)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        assertEquals(75.0f, prefs.getFloat(KEY_SOC, -1f), 0.1f)
    }

    // ── SoC delta reconstruction ───────────────────────────────────────────────

    @Test fun socIncreaseAboveThresholdCreatesSyntheticSession() = runBlocking {
        writeShutdownState(soc = 50.0f, tsOffsetMs = -3_600_000L)  // 1 hour ago at 50%

        repo.onTelemetry(gen.generateAcChargingTelemetry(soc = 75.0), car)  // woke at 75%
        Thread.sleep(SETTLE)

        val sessions = repo.getAllSessions().first()
        assertEquals("Should have one synthetic session", 1, sessions.size)
        val session = sessions.first()
        assertFalse("Synthetic session should not be active", session.isActive)
        assertEquals(50.0, session.socStart, 0.1)
        assertEquals(75.0, session.socEnd!!, 0.1)
    }

    @Test fun syntheticSessionKwhIsCorrect() = runBlocking {
        writeShutdownState(soc = 40.0f, tsOffsetMs = -7_200_000L)

        repo.onTelemetry(gen.generateAcChargingTelemetry(soc = 80.0), car)  // +40%
        Thread.sleep(SETTLE)

        val session = repo.getAllSessions().first().first()
        // 40% × 82.5 kWh = 33.0 kWh
        assertEquals(33.0, session.kwhAdded!!, 0.5)
    }

    @Test fun syntheticSessionPeakAndAvgKwAreZero() = runBlocking {
        writeShutdownState(soc = 50.0f, tsOffsetMs = -3_600_000L)

        repo.onTelemetry(gen.generateAcChargingTelemetry(soc = 75.0), car)
        Thread.sleep(SETTLE)

        val session = repo.getAllSessions().first().first()
        assertEquals("peakKw should be 0 for synthetic session", 0.0, session.peakKw, 0.0)
        assertEquals("avgKw should be 0 for synthetic session", 0.0, session.avgKw, 0.0)
    }

    @Test fun socIncreaseBelowThresholdCreatesNoSession() = runBlocking {
        writeShutdownState(soc = 79.4f, tsOffsetMs = -600_000L)  // only 0.6% increase

        repo.onTelemetry(gen.generateAcChargingTelemetry(soc = 80.0), car)
        Thread.sleep(SETTLE)

        assertTrue("Delta below 1% should not create a session",
            repo.getAllSessions().first().isEmpty())
    }

    @Test fun socDecreaseCreatesNoSession() = runBlocking {
        writeShutdownState(soc = 85.0f, tsOffsetMs = -1_800_000L)  // drove, SoC dropped

        repo.onTelemetry(gen.generateAcChargingTelemetry(soc = 70.0), car)
        Thread.sleep(SETTLE)

        assertTrue("SoC decrease should not create a session",
            repo.getAllSessions().first().isEmpty())
    }

    @Test fun reconstructionOnlyRunsOncePerServiceLifecycle() = runBlocking {
        writeShutdownState(soc = 50.0f, tsOffsetMs = -3_600_000L)

        // First packet — triggers reconstruction
        repo.onTelemetry(gen.generateAcChargingTelemetry(soc = 75.0), car)
        Thread.sleep(SETTLE)
        // Second packet with even higher SoC — should NOT trigger a second reconstruction
        repo.onTelemetry(gen.generateAcChargingTelemetry(soc = 90.0), car)
        Thread.sleep(SETTLE)

        assertEquals("Reconstruction should only run once per lifecycle",
            1, repo.getAllSessions().first().size)
    }

    // ── Duplicate guard ───────────────────────────────────────────────────────

    @Test fun duplicateGuardSkipsReconstructionWhenRecentSessionExists() = runBlocking {
        val shutdownTs = System.currentTimeMillis() - 3_600_000L
        writeShutdownState(soc = 50.0f, tsOffsetMs = -3_600_000L)

        // Pre-existing session that overlaps the window
        db.chargingSessionDao().insertSession(
            ChargingSessionEntity(
                startTime    = shutdownTs + 60_000L,  // 1 min after our shutdown ts
                socStart     = 50.0,
                batteryKwh   = 82.5,
                carConfigId  = "BYD_SEAL_EXCELLENCE",
                isActive     = false,
                socEnd       = 75.0,
                kwhAdded     = 20.6
            )
        )

        repo.onTelemetry(gen.generateAcChargingTelemetry(soc = 75.0), car)
        Thread.sleep(SETTLE)

        assertEquals("Should not create duplicate session when recent one exists",
            1, repo.getAllSessions().first().size)
    }

    // ── SoC persistence ───────────────────────────────────────────────────────

    @Test fun everyCachedPacketUpdatesSavedSoC() = runBlocking {
        repo.onTelemetry(gen.generateAcChargingTelemetry(soc = 60.0), car)
        Thread.sleep(SETTLE)
        repo.onTelemetry(gen.generateAcChargingTelemetry(soc = 65.0), car)
        Thread.sleep(SETTLE)
        repo.onTelemetry(gen.generateAcChargingTelemetry(soc = 70.0), car)
        Thread.sleep(SETTLE)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        assertEquals("Last persisted SoC should be 70%", 70.0f, prefs.getFloat(KEY_SOC, -1f), 0.1f)
    }

    // ── Deletion ──────────────────────────────────────────────────────────────

    @Test fun deleteSessionRemovesFromDatabase() = runBlocking {
        writeShutdownState(soc = 50.0f, tsOffsetMs = -3_600_000L)
        repo.onTelemetry(gen.generateAcChargingTelemetry(soc = 75.0), car)
        Thread.sleep(SETTLE)

        val sessionId = repo.getAllSessions().first().first().id
        repo.deleteSession(sessionId)
        Thread.sleep(SETTLE)

        assertTrue("Session should be deleted", repo.getAllSessions().first().isEmpty())
    }

    @Test fun deleteSessionsRemovesMultiple() = runBlocking {
        // Insert 3 sessions directly
        val ids = (1..3).map { i ->
            db.chargingSessionDao().insertSession(
                ChargingSessionEntity(
                    startTime    = System.currentTimeMillis() - i * 3_600_000L,
                    socStart     = 30.0,
                    batteryKwh   = 82.5,
                    carConfigId  = "BYD_SEAL_EXCELLENCE",
                    isActive     = false,
                    socEnd       = 60.0,
                    kwhAdded     = 24.75
                )
            )
        }
        repo.deleteSessions(ids)
        Thread.sleep(SETTLE)
        assertTrue(repo.getAllSessions().first().isEmpty())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun writeShutdownState(soc: Float, tsOffsetMs: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_SOC,     soc)
            .putLong(KEY_TS,       System.currentTimeMillis() + tsOffsetMs)
            .putFloat(KEY_TEMP,    22.0f)
            .putInt(KEY_VOLTAGE,   450)
            .commit()  // synchronous — must be done before onTelemetry is called
    }
}