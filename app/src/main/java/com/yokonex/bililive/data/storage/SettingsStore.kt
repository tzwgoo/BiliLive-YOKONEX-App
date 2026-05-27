package com.yokonex.bililive.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
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

    suspend fun ensureDefaults() {
        dataStore.edit { preferences ->
            if (!preferences.contains(ROOM_ID)) {
                preferences[ROOM_ID] = ""
            }
            if (!preferences.contains(OUTPUT_MODE)) {
                preferences[OUTPUT_MODE] = OutputMode.BLUETOOTH.name
            }
        }
    }

    private companion object {
        val ROOM_ID = stringPreferencesKey("room_id")
        val OUTPUT_MODE = stringPreferencesKey("output_mode")
        val RECENT_DEVICE_ID = stringPreferencesKey("recent_device_id")
    }
}

