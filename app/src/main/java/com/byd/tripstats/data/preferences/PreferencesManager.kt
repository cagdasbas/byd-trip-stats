package com.byd.tripstats.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.byd.tripstats.data.config.CarCatalog
import com.byd.tripstats.data.config.CarConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.util.Locale

// The backing file keeps its historical name so existing installs preserve
// their selected car and other preferences across upgrades.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mqtt_settings")

private val SELECTED_CAR_ID             = stringPreferencesKey("selected_car_id")
private val LAST_SEEN_VERSION_CODE       = intPreferencesKey("last_seen_version_code")
private val DASHBOARD_ANIMATIONS_ENABLED = booleanPreferencesKey("dashboard_animations_enabled")
private val KEEP_SERVICE_ALIVE_WHEN_OFF  = booleanPreferencesKey("keep_service_alive_when_off")
private val ELECTRICITY_PRICE            = doublePreferencesKey("electricity_price_per_kwh")
private val CURRENCY_SYMBOL              = stringPreferencesKey("currency_symbol")
private val SOH_BASELINE_EPOCH_MS        = longPreferencesKey("soh_baseline_epoch_ms")
private val UNIT_SYSTEM                  = stringPreferencesKey("unit_system")

class PreferencesManager(private val context: Context) {

    // ── Synchronous bootstrap cache ───────────────────────────────────────────
    // All UI-critical preferences are mirrored into SharedPreferences so
    // collectAsState(initial = cached) renders the correct value on the very
    // first frame without waiting for DataStore's async disk read.
    private val cache: SharedPreferences =
        context.getSharedPreferences("startup_cache", Context.MODE_PRIVATE)

    // ── Selected car ──────────────────────────────────────────────────────────

    val selectedCarId: Flow<String?> = context.dataStore.data
        .map { it[SELECTED_CAR_ID] }
        .onEach { id -> if (id != null) cache.edit().putString("selected_car_id", id).apply() }

    val selectedCarConfig: Flow<CarConfig?> = selectedCarId.map { CarCatalog.fromId(it) }

    fun getCachedSelectedCarId(): String? = cache.getString("selected_car_id", null)

    /** Synchronous — returns CarConfig immediately from SharedPreferences cache.
     *  Safe to call from any thread, including service onCreate/onStartCommand.
     */
    fun getCachedSelectedCarConfig(): com.byd.tripstats.data.config.CarConfig? =
        CarCatalog.fromId(getCachedSelectedCarId())

    suspend fun saveSelectedCar(carId: String) {
        context.dataStore.edit { it[SELECTED_CAR_ID] = carId }
        cache.edit().putString("selected_car_id", carId).apply()
    }

    suspend fun saveInitialSetup(carId: String) = saveSelectedCar(carId)

    suspend fun getSelectedCarId(): String? =
        context.dataStore.data.map { it[SELECTED_CAR_ID] }.first()

    suspend fun getSelectedCarConfig(): CarConfig? = CarCatalog.fromId(getSelectedCarId())

    // ── Animations ────────────────────────────────────────────────────────────

    val dashboardAnimationsEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[DASHBOARD_ANIMATIONS_ENABLED] ?: true }
        .onEach { cache.edit().putBoolean("animations_enabled", it).apply() }

    /** Synchronous read — safe to use as collectAsState initial value. */
    fun getCachedAnimationsEnabled(): Boolean = cache.getBoolean("animations_enabled", true)

    suspend fun saveDashboardAnimationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[DASHBOARD_ANIMATIONS_ENABLED] = enabled }
        cache.edit().putBoolean("animations_enabled", enabled).apply()
    }

    // ── Keep service alive when off ───────────────────────────────────────────
    // When true, the telemetry service does NOT self-stop after carOff+notCharging.
    // Benefits: continuous 12V/SoC sampling, ADB-over-WiFi stays reachable without
    // remote-waking the car. Cost: small additional wake-lock/WiFi-lock load on
    // top of the BYD stock-process drain (which is the dominant component).
    // Defaulting to true: real-world data shows our app's contribution to off-state
    // drain is within measurement noise vs. the BYD GPS/telematics baseline, and
    // the convenience win is large.
    val keepServiceAliveWhenOff: Flow<Boolean> = context.dataStore.data
        .map { it[KEEP_SERVICE_ALIVE_WHEN_OFF] ?: true }
        .onEach { cache.edit().putBoolean("keep_service_alive_when_off", it).apply() }

    fun getCachedKeepServiceAliveWhenOff(): Boolean =
        cache.getBoolean("keep_service_alive_when_off", true)

    suspend fun saveKeepServiceAliveWhenOff(enabled: Boolean) {
        context.dataStore.edit { it[KEEP_SERVICE_ALIVE_WHEN_OFF] = enabled }
        cache.edit().putBoolean("keep_service_alive_when_off", enabled).apply()
    }

    // ── Electricity cost ──────────────────────────────────────────────────────

    val electricityPricePerKwh: Flow<Double> = context.dataStore.data
        .map { it[ELECTRICITY_PRICE] ?: 0.0 }
        .onEach { cache.edit().putFloat("electricity_price", it.toFloat()).apply() }

    fun getCachedElectricityPrice(): Double =
        cache.getFloat("electricity_price", 0f).toDouble()

    val currencySymbol: Flow<String> = context.dataStore.data
        .map { it[CURRENCY_SYMBOL] ?: "€" }
        .onEach { cache.edit().putString("currency_symbol", it).apply() }

    fun getCachedCurrencySymbol(): String = cache.getString("currency_symbol", "€") ?: "€"

    suspend fun saveElectricityPrice(price: Double, symbol: String) {
        context.dataStore.edit {
            it[ELECTRICITY_PRICE] = price
            it[CURRENCY_SYMBOL]   = symbol
        }
        cache.edit()
            .putFloat("electricity_price", price.toFloat())
            .putString("currency_symbol", symbol)
            .apply()
    }

    // ── Version tracking ──────────────────────────────────────────────────────

    /** Returns 0 if never persisted (i.e. first install or fresh data). */
    suspend fun getLastSeenVersionCode(): Int =
        context.dataStore.data.map { it[LAST_SEEN_VERSION_CODE] ?: 0 }.first()

    suspend fun saveLastSeenVersionCode(versionCode: Int) {
        context.dataStore.edit { it[LAST_SEEN_VERSION_CODE] = versionCode }
    }

    // ── SoH baseline epoch ────────────────────────────────────────────────────
    // When set, the battery degradation chart only plots trips that started
    // at or after this timestamp. Used to exclude pre-v2.0.0 data that was
    // recorded via a different (incompatible) SoH source.

    /** Emits null if no baseline has been set (= show all trips). */
    val sohBaselineEpochMs: Flow<Long?> = context.dataStore.data
        .map { it[SOH_BASELINE_EPOCH_MS] }

    suspend fun saveSohBaselineEpochMs(epochMs: Long) {
        context.dataStore.edit { it[SOH_BASELINE_EPOCH_MS] = epochMs }
    }

    suspend fun clearSohBaselineEpochMs() {
        context.dataStore.edit { it.remove(SOH_BASELINE_EPOCH_MS) }
    }

    // ── Unit system ───────────────────────────────────────────────────────────

    val unitSystem: Flow<UnitSystem> = context.dataStore.data
        .map { prefs ->
            prefs[UNIT_SYSTEM]
                ?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() }
                ?: localeDefaultUnitSystem()
        }
        .onEach { cache.edit().putString("unit_system", it.name).apply() }

    fun getCachedUnitSystem(): UnitSystem =
        cache.getString("unit_system", null)
            ?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() }
            ?: localeDefaultUnitSystem()

    suspend fun saveUnitSystem(system: UnitSystem) {
        context.dataStore.edit { it[UNIT_SYSTEM] = system.name }
        cache.edit().putString("unit_system", system.name).apply()
    }

    private fun localeDefaultUnitSystem(): UnitSystem =
        if (Locale.getDefault().country.uppercase() == "GB") UnitSystem.IMPERIAL
        else UnitSystem.METRIC
}