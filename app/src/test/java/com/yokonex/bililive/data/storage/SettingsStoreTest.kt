package com.yokonex.bililive.data.storage

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.yokonex.bililive.data.storage.dao.WaveformDao
import com.yokonex.bililive.data.storage.entity.WaveformEntity
import com.yokonex.bililive.domain.model.GiftTriggerMode
import com.yokonex.bililive.domain.model.OutputMode
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {

    @Test
    fun defaultWaveforms_areSeededOnFirstLaunch() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
                Files.createTempFile("settings-store", ".preferences_pb").toFile()
            },
        )
        val settingsStore = SettingsStore(dataStore)
        val waveformDao = FakeWaveformDao()
        val bootstrapper = StorageBootstrapper(
            settingsStore = settingsStore,
            waveformDao = waveformDao,
        )

        bootstrapper.seedDefaultsIfNeeded()

        val waveforms = waveformDao.observeAll().first()
        assertTrue(waveforms.isNotEmpty())
        assertEquals("", settingsStore.roomId.first())
        assertEquals(OutputMode.BLUETOOTH, settingsStore.outputMode.first())
        assertEquals("ws://103.236.55.92:43001/", settingsStore.websocketEndpoint.first())
        assertEquals(false, settingsStore.bluetoothMixModeEnabled.first())
        assertTrue(settingsStore.autoReconnectEnabled.first())
        assertEquals(GiftTriggerMode.SINGLE, settingsStore.giftTriggerMode.first())
    }

    @Test
    fun updateAutoReconnectEnabled_persistsFlag() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
                Files.createTempFile("settings-store", ".preferences_pb").toFile()
            },
        )
        val settingsStore = SettingsStore(dataStore)

        settingsStore.updateAutoReconnectEnabled(false)

        assertTrue(!settingsStore.autoReconnectEnabled.first())
    }

    @Test
    fun updateGiftTriggerMode_persistsMode() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
                Files.createTempFile("settings-store", ".preferences_pb").toFile()
            },
        )
        val settingsStore = SettingsStore(dataStore)

        settingsStore.updateGiftTriggerMode(GiftTriggerMode.BY_QUANTITY)

        assertEquals(GiftTriggerMode.BY_QUANTITY, settingsStore.giftTriggerMode.first())
    }

    @Test
    fun updateBluetoothMixModeEnabled_persistsFlagAndRecentDeviceName() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
                Files.createTempFile("settings-store", ".preferences_pb").toFile()
            },
        )
        val settingsStore = SettingsStore(dataStore)

        settingsStore.updateBluetoothMixModeEnabled(true)
        settingsStore.updateRecentDeviceId("AA:BB:CC:77")
        settingsStore.updateRecentDeviceName("YYC-DJ-V2-077")

        assertEquals(true, settingsStore.bluetoothMixModeEnabled.first())
        assertEquals("AA:BB:CC:77", settingsStore.recentDeviceId.first())
        assertEquals("YYC-DJ-V2-077", settingsStore.recentDeviceName.first())
    }

    private class FakeWaveformDao : WaveformDao {
        private val state = MutableStateFlow<List<WaveformEntity>>(emptyList())

        override fun observeAll(): Flow<List<WaveformEntity>> = state

        override suspend fun count(): Int = state.value.size

        override suspend fun insertAll(waveforms: List<WaveformEntity>) {
            state.update { current ->
                current + waveforms
            }
        }

        override suspend fun upsert(waveform: WaveformEntity) {
            state.update { current ->
                current.filterNot { entity -> entity.id == waveform.id } + waveform
            }
        }

        override suspend fun findById(id: String): WaveformEntity? =
            state.value.firstOrNull { it.id == id }

        override suspend fun deleteById(id: String) {
            state.update { current ->
                current.filterNot { entity -> entity.id == id }
            }
        }
    }
}
