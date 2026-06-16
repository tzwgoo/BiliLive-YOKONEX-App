package com.yokonex.bililive.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yokonex.bililive.domain.model.GiftTriggerMode
import com.yokonex.bililive.domain.model.OutputMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class SettingsStore(
    private val dataStore: DataStore<Preferences>,
) {
    val roomId: Flow<String> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            preferences[ROOM_ID] ?: ""
        }

    val outputMode: Flow<OutputMode> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            preferences[OUTPUT_MODE]
                ?.let(OutputMode::valueOf)
                ?: OutputMode.BLUETOOTH
        }

    val recentDeviceId: Flow<String?> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            preferences[RECENT_DEVICE_ID]
        }

    val recentDeviceName: Flow<String?> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            preferences[RECENT_DEVICE_NAME]
        }

    val bluetoothMixModeEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            preferences[BLUETOOTH_MIX_MODE_ENABLED]?.toBooleanStrictOrNull() ?: false
        }

    val websocketEndpoint: Flow<String> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            preferences[WEBSOCKET_ENDPOINT] ?: DEFAULT_WEBSOCKET_ENDPOINT
        }

    val websocketUid: Flow<String> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            preferences[WEBSOCKET_UID] ?: ""
        }

    val websocketToken: Flow<String> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            preferences[WEBSOCKET_TOKEN] ?: ""
        }

    val reconnectIntervalSeconds: Flow<Int> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            preferences[RECONNECT_INTERVAL_SECONDS]?.toIntOrNull()?.coerceAtLeast(1) ?: 3
        }

    val autoReconnectEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            preferences[AUTO_RECONNECT_ENABLED]?.toBooleanStrictOrNull() ?: true
        }

    val giftTriggerMode: Flow<GiftTriggerMode> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            preferences[GIFT_TRIGGER_MODE]
                ?.let { value -> enumValues<GiftTriggerMode>().firstOrNull { it.name == value } }
                ?: GiftTriggerMode.SINGLE
        }

    suspend fun updateRoomId(value: String) {
        dataStore.edit { preferences ->
            preferences[ROOM_ID] = value
        }
    }

    suspend fun updateOutputMode(mode: OutputMode) {
        dataStore.edit { preferences ->
            preferences[OUTPUT_MODE] = mode.name
        }
    }

    suspend fun updateRecentDeviceId(deviceId: String?) {
        dataStore.edit { preferences ->
            if (deviceId.isNullOrBlank()) {
                preferences.remove(RECENT_DEVICE_ID)
            } else {
                preferences[RECENT_DEVICE_ID] = deviceId
            }
        }
    }

    suspend fun updateRecentDeviceName(deviceName: String?) {
        dataStore.edit { preferences ->
            if (deviceName.isNullOrBlank()) {
                preferences.remove(RECENT_DEVICE_NAME)
            } else {
                preferences[RECENT_DEVICE_NAME] = deviceName.trim()
            }
        }
    }

    suspend fun updateBluetoothMixModeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BLUETOOTH_MIX_MODE_ENABLED] = enabled.toString()
        }
    }

    suspend fun updateWebSocketEndpoint(value: String) {
        dataStore.edit { preferences ->
            preferences[WEBSOCKET_ENDPOINT] = value.trim()
        }
    }

    suspend fun updateWebSocketUid(value: String) {
        dataStore.edit { preferences ->
            preferences[WEBSOCKET_UID] = value.trim()
        }
    }

    suspend fun updateWebSocketToken(value: String) {
        dataStore.edit { preferences ->
            preferences[WEBSOCKET_TOKEN] = value.trim()
        }
    }

    suspend fun updateReconnectIntervalSeconds(value: Int) {
        dataStore.edit { preferences ->
            preferences[RECONNECT_INTERVAL_SECONDS] = value.coerceAtLeast(1).toString()
        }
    }

    suspend fun updateAutoReconnectEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_RECONNECT_ENABLED] = enabled.toString()
        }
    }

    suspend fun updateGiftTriggerMode(mode: GiftTriggerMode) {
        dataStore.edit { preferences ->
            preferences[GIFT_TRIGGER_MODE] = mode.name
        }
    }

    suspend fun ensureDefaults() {
        dataStore.edit { preferences ->
            if (!preferences.contains(ROOM_ID)) {
                preferences[ROOM_ID] = ""
            }
            if (!preferences.contains(OUTPUT_MODE)) {
                preferences[OUTPUT_MODE] = OutputMode.BLUETOOTH.name
            }
            if (!preferences.contains(WEBSOCKET_ENDPOINT)) {
                preferences[WEBSOCKET_ENDPOINT] = DEFAULT_WEBSOCKET_ENDPOINT
            }
            if (!preferences.contains(WEBSOCKET_UID)) {
                preferences[WEBSOCKET_UID] = ""
            }
            if (!preferences.contains(WEBSOCKET_TOKEN)) {
                preferences[WEBSOCKET_TOKEN] = ""
            }
            if (!preferences.contains(BLUETOOTH_MIX_MODE_ENABLED)) {
                preferences[BLUETOOTH_MIX_MODE_ENABLED] = false.toString()
            }
            if (!preferences.contains(RECONNECT_INTERVAL_SECONDS)) {
                preferences[RECONNECT_INTERVAL_SECONDS] = "3"
            }
            if (!preferences.contains(AUTO_RECONNECT_ENABLED)) {
                preferences[AUTO_RECONNECT_ENABLED] = true.toString()
            }
            if (!preferences.contains(GIFT_TRIGGER_MODE)) {
                preferences[GIFT_TRIGGER_MODE] = GiftTriggerMode.SINGLE.name
            }
        }
    }

    private companion object {
        const val DEFAULT_WEBSOCKET_ENDPOINT = "ws://103.236.55.92:43001/"
        val ROOM_ID = stringPreferencesKey("room_id")
        val OUTPUT_MODE = stringPreferencesKey("output_mode")
        val RECENT_DEVICE_ID = stringPreferencesKey("recent_device_id")
        val RECENT_DEVICE_NAME = stringPreferencesKey("recent_device_name")
        val BLUETOOTH_MIX_MODE_ENABLED = stringPreferencesKey("bluetooth_mix_mode_enabled")
        val WEBSOCKET_ENDPOINT = stringPreferencesKey("websocket_endpoint")
        val WEBSOCKET_UID = stringPreferencesKey("websocket_uid")
        val WEBSOCKET_TOKEN = stringPreferencesKey("websocket_token")
        val RECONNECT_INTERVAL_SECONDS = stringPreferencesKey("reconnect_interval_seconds")
        val AUTO_RECONNECT_ENABLED = stringPreferencesKey("auto_reconnect_enabled")
        val GIFT_TRIGGER_MODE = stringPreferencesKey("gift_trigger_mode")
    }
}
