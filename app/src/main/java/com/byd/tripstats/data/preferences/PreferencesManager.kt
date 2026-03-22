package com.byd.tripstats.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.byd.tripstats.data.config.CarCatalog
import com.byd.tripstats.data.config.CarConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mqtt_settings")

private val SELECTED_CAR_ID        = stringPreferencesKey("selected_car_id")
private val LAST_SEEN_VERSION_CODE  = intPreferencesKey("last_seen_version_code")

class PreferencesManager(private val context: Context) {

    val selectedCarId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SELECTED_CAR_ID]
    }

    val selectedCarConfig: Flow<CarConfig?> = selectedCarId.map { id ->
        CarCatalog.fromId(id)
    }

    suspend fun saveSelectedCar(carId: String) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_CAR_ID] = carId
        }
    }

    suspend fun saveInitialSetup(carId: String, topic: String) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_CAR_ID] = carId
            preferences[TOPIC] = topic
        }
    }

    suspend fun saveTopic(topic: String) {
        context.dataStore.edit { preferences ->
            preferences[TOPIC] = topic
        }
    }

    suspend fun getSelectedCarId(): String? {
        return context.dataStore.data.map { it[SELECTED_CAR_ID] }.first()
    }

    suspend fun getSelectedCarConfig(): CarConfig? {
        return CarCatalog.fromId(getSelectedCarId())
    }
    
    companion object {
        private val BROKER_URL = stringPreferencesKey("broker_url")
        private val BROKER_PORT = intPreferencesKey("broker_port")
        private val USERNAME = stringPreferencesKey("username")
        private val PASSWORD = stringPreferencesKey("password")
        private val TOPIC = stringPreferencesKey("topic")
        private val ELECTRICITY_PRICE = doublePreferencesKey("electricity_price_per_kwh")
        private val CURRENCY_SYMBOL = stringPreferencesKey("currency_symbol")

    }
    
    data class MqttSettings(
        val brokerUrl: String = "127.0.0.1",
        val brokerPort: Int = 1883,
        val username: String = "",
        val password: String = "",
        val topic: String = ""
    )
    
    val mqttSettings: Flow<MqttSettings> = context.dataStore.data.map { preferences ->
        MqttSettings(
            brokerUrl = preferences[BROKER_URL] ?: "127.0.0.1",
            brokerPort = preferences[BROKER_PORT] ?: 1883,
            username = preferences[USERNAME] ?: "",
            password = preferences[PASSWORD] ?: "",
            topic = preferences[TOPIC] ?: ""
        )
    }
    
    suspend fun saveMqttSettings(
        brokerUrl: String,
        brokerPort: Int,
        username: String,
        password: String,
        topic: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[BROKER_URL] = brokerUrl
            preferences[BROKER_PORT] = brokerPort
            preferences[USERNAME] = username
            preferences[PASSWORD] = password
            preferences[TOPIC] = topic
        }
    }
    
    suspend fun getMqttSettings(): MqttSettings {
        return context.dataStore.data.map { preferences ->
            MqttSettings(
                brokerUrl  = preferences[BROKER_URL]  ?: "127.0.0.1",
                brokerPort = preferences[BROKER_PORT] ?: 1883,
                username   = preferences[USERNAME]    ?: "",
                password   = preferences[PASSWORD]    ?: "",
                topic      = preferences[TOPIC]       ?: ""
            )
        }.first()
    }

        /** Returns 0 if never persisted (i.e. first install or fresh data). */
    suspend fun getLastSeenVersionCode(): Int =
        context.dataStore.data.map { it[LAST_SEEN_VERSION_CODE] ?: 0 }.first()

    suspend fun saveLastSeenVersionCode(versionCode: Int) {
        context.dataStore.edit { it[LAST_SEEN_VERSION_CODE] = versionCode }
    }

    // ── Electricity cost ──────────────────────────────────────────────────────

    /** Price the user pays per kWh (in their chosen currency). 0.0 = not configured. */
    val electricityPricePerKwh: Flow<Double> = context.dataStore.data.map {
        it[ELECTRICITY_PRICE] ?: 0.0
    }

    /** Currency symbol shown alongside costs (e.g. "€", "$", "£"). Default "€". */
    val currencySymbol: Flow<String> = context.dataStore.data.map {
        it[CURRENCY_SYMBOL] ?: "€"
    }

    suspend fun saveElectricityPrice(price: Double, symbol: String) {
        context.dataStore.edit {
            it[ELECTRICITY_PRICE] = price
            it[CURRENCY_SYMBOL]   = symbol
        }
    }
}