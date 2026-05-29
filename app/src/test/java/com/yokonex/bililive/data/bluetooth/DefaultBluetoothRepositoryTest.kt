package com.yokonex.bililive.data.bluetooth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.yokonex.bililive.data.mapper.WaveformMapper
import com.yokonex.bililive.data.bluetooth.model.BluetoothTelemetry
import com.yokonex.bililive.data.storage.SettingsStore
import com.yokonex.bililive.data.storage.dao.WaveformDao
import com.yokonex.bililive.data.storage.entity.WaveformEntity
import com.yokonex.bililive.data.bluetooth.model.BluetoothConnectionState
import com.yokonex.bililive.data.bluetooth.model.BluetoothDevice
import com.yokonex.bililive.domain.model.LiveEventType
import com.yokonex.bililive.domain.model.WaveformDefinition
import com.yokonex.bililive.domain.model.WaveformStep
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBluetoothRepositoryTest {

    @Test
    fun connect_updatesStateAndPersistsRecentDevice() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
                Files.createTempFile("bt-repo", ".preferences_pb").toFile()
            },
        )
        val settingsStore = SettingsStore(dataStore)
        val bleManager = FakeAndroidBleManager(
            devices = listOf(
                BluetoothDevice(
                    id = "AA:BB:CC:01",
                    name = "YYC-DJ-V2-001",
                    protocol = "ems_v2",
                ),
            ),
        )
        val repository = DefaultBluetoothRepository(
            bleManager = bleManager,
            waveformDao = FakeWaveformDao(),
            settingsStore = settingsStore,
            waveformRuntime = EmsWaveformRuntime(bleManager, EmsProtocolEncoder()),
            protocolEncoder = EmsProtocolEncoder(),
        )

        repository.scan()
        repository.connect("AA:BB:CC:01")

        assertEquals(BluetoothConnectionState.CONNECTED, repository.connectionState.value)
        assertEquals("AA:BB:CC:01", settingsStore.recentDeviceId.first())
        assertArrayEquals(
            byteArrayOf(0x35, 0x71, 0x04, 0xAA.toByte()),
            bleManager.writes.first(),
        )
        assertEquals("YYC-DJ-V2-001", repository.runtimeStatus.value.deviceName)
    }

    @Test
    fun connect_emsV1Device_alsoQueriesBatteryAfterConnecting() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
                Files.createTempFile("bt-repo", ".preferences_pb").toFile()
            },
        )
        val bleManager = FakeAndroidBleManager(
            devices = listOf(
                BluetoothDevice(
                    id = "AA:BB:CC:11",
                    name = "YYC-DJ-001",
                    protocol = "ems_v1",
                ),
            ),
        )
        val repository = DefaultBluetoothRepository(
            bleManager = bleManager,
            waveformDao = FakeWaveformDao(),
            settingsStore = SettingsStore(dataStore),
            waveformRuntime = EmsWaveformRuntime(bleManager, EmsProtocolEncoder()),
            protocolEncoder = EmsProtocolEncoder(),
        )

        repository.scan()
        repository.connect("AA:BB:CC:11")

        assertArrayEquals(
            byteArrayOf(0x35, 0x71, 0x04, 0xAA.toByte()),
            bleManager.writes.first(),
        )
        assertEquals("YYC-DJ-001", repository.runtimeStatus.value.deviceName)
    }

    @Test
    fun playWaveform_loadsStoredWaveformAndWritesPackets() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
                Files.createTempFile("bt-repo", ".preferences_pb").toFile()
            },
        )
        val waveform = WaveformDefinition(
            id = "custom-wave",
            name = "自定义波形",
            builtin = false,
            steps = listOf(
                WaveformStep(
                    durationMs = 120,
                    channelA = 48,
                    channelB = 24,
                    channelAMode = 0x11,
                    channelAFrequency = 10,
                    channelAPulseWidth = 5,
                ),
            ),
        )
        val bleManager = FakeAndroidBleManager(
            devices = listOf(
                BluetoothDevice(
                    id = "AA:BB:CC:02",
                    name = "YYC-DJ-Classic",
                    protocol = "ems_v1",
                ),
            ),
        )
        val repository = DefaultBluetoothRepository(
            bleManager = bleManager,
            waveformDao = FakeWaveformDao(
                listOf(WaveformMapper.toEntity(waveform)),
            ),
            settingsStore = SettingsStore(dataStore),
            waveformRuntime = EmsWaveformRuntime(bleManager, EmsProtocolEncoder()),
            protocolEncoder = EmsProtocolEncoder(),
        )

        repository.scan()
        repository.connect("AA:BB:CC:02")
        repository.playWaveform("custom-wave")

        assertTrue(bleManager.writes.size >= 3)
        assertArrayEquals(
            byteArrayOf(0x35, 0x71, 0x04, 0xAA.toByte()),
            bleManager.writes.first(),
        )
        assertArrayEquals(
            byteArrayOf(
                0x35,
                0x11,
                0x03,
                0x01,
                0x00,
                0x30,
                0x11,
                0x0A,
                0x05,
                0x9A.toByte(),
            ),
            bleManager.writes[1],
        )
        assertArrayEquals(
            byteArrayOf(
                0x35,
                0x11,
                0x03,
                0x00,
                0x00,
                0x01,
                0x01,
                0x00,
                0x00,
                0x4B,
            ),
            bleManager.writes.last(),
        )
        assertEquals(0, repository.runtimeStatus.value.channelAStrength)
        assertEquals(0, repository.runtimeStatus.value.channelBStrength)
    }

    @Test
    fun connect_sameDeviceRepeatedly_onlyConnectsOnce() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
                Files.createTempFile("bt-repo", ".preferences_pb").toFile()
            },
        )
        val bleManager = FakeAndroidBleManager(
            devices = listOf(
                BluetoothDevice(
                    id = "AA:BB:CC:03",
                    name = "YYC-DJ-V2-002",
                    protocol = "ems_v2",
                ),
            ),
            connectGate = CompletableDeferred(),
        )
        val repository = DefaultBluetoothRepository(
            bleManager = bleManager,
            waveformDao = FakeWaveformDao(),
            settingsStore = SettingsStore(dataStore),
            waveformRuntime = EmsWaveformRuntime(bleManager, EmsProtocolEncoder()),
            protocolEncoder = EmsProtocolEncoder(),
        )

        repository.scan()
        val firstConnect = backgroundScope.launch { repository.connect("AA:BB:CC:03") }
        advanceUntilIdle()
        val secondConnect = backgroundScope.launch { repository.connect("AA:BB:CC:03") }
        advanceUntilIdle()
        bleManager.completeConnect()
        firstConnect.join()
        secondConnect.join()

        assertEquals(1, bleManager.connectCalls)
        assertEquals(BluetoothConnectionState.CONNECTED, repository.connectionState.value)
    }

    @Test
    fun enqueueWaveform_usesMixedRuntimePath_whenMixModeEnabled() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
                Files.createTempFile("bt-repo", ".preferences_pb").toFile()
            },
        )
        val waveform = WaveformDefinition(
            id = "custom-wave",
            name = "自定义波形",
            builtin = false,
            steps = listOf(
                WaveformStep(
                    durationMs = 120,
                    channelA = 48,
                    channelB = 24,
                ),
            ),
        )
        val bleManager = FakeAndroidBleManager(
            devices = listOf(
                BluetoothDevice(
                    id = "AA:BB:CC:04",
                    name = "YYC-DJ-V2-003",
                    protocol = "ems_v2",
                ),
            ),
        )
        val repository = DefaultBluetoothRepository(
            bleManager = bleManager,
            waveformDao = FakeWaveformDao(
                listOf(WaveformMapper.toEntity(waveform)),
            ),
            settingsStore = SettingsStore(dataStore),
            waveformRuntime = EmsWaveformRuntime(bleManager, EmsProtocolEncoder()),
            protocolEncoder = EmsProtocolEncoder(),
        )

        repository.scan()
        repository.connect("AA:BB:CC:04")
        repository.setMixModeEnabled(true)
        repository.enqueueWaveform(
            waveformId = "custom-wave",
            eventType = LiveEventType.GIFT,
            repeatCount = 1,
        )

        assertTrue(bleManager.writes.size >= 2)
        assertArrayEquals(
            EmsProtocolEncoder().createStepPacket(
                step = waveform.steps.first(),
                protocol = "ems_v2",
                signalMode = waveform.signalMode,
            ),
            bleManager.writes[1],
        )
    }
}

private class FakeAndroidBleManager(
    private val devices: List<BluetoothDevice>,
    private val connectGate: CompletableDeferred<Unit>? = null,
) : AndroidBleManager {
    val writes = mutableListOf<ByteArray>()
    var connectCalls: Int = 0
    private val telemetryState = MutableStateFlow(BluetoothTelemetry())
    override val telemetry = telemetryState.asStateFlow()

    override suspend fun scan(): List<BluetoothDevice> = devices

    override suspend fun connect(deviceId: String) {
        connectCalls += 1
        connectGate?.await()
    }

    override suspend fun disconnect() = Unit

    override suspend fun write(packet: ByteArray) {
        writes += packet
    }

    fun completeConnect() {
        connectGate?.complete(Unit)
    }
}

private class FakeWaveformDao(
    initial: List<WaveformEntity> = emptyList(),
) : WaveformDao {
    private val state = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<WaveformEntity>> = state

    override suspend fun count(): Int = state.value.size

    override suspend fun insertAll(waveforms: List<WaveformEntity>) {
        state.value = state.value + waveforms
    }

    override suspend fun upsert(waveform: WaveformEntity) {
        state.value = state.value.filterNot { entity -> entity.id == waveform.id } + waveform
    }

    override suspend fun findById(id: String): WaveformEntity? =
        state.value.firstOrNull { it.id == id }

    override suspend fun deleteById(id: String) {
        state.value = state.value.filterNot { entity -> entity.id == id }
    }
}
