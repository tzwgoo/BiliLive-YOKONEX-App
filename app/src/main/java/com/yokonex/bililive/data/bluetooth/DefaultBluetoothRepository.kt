package com.yokonex.bililive.data.bluetooth

import com.yokonex.bililive.data.mapper.WaveformMapper
import com.yokonex.bililive.data.storage.DefaultWaveforms
import com.yokonex.bililive.data.storage.SettingsStore
import com.yokonex.bililive.data.storage.dao.WaveformDao
import com.yokonex.bililive.data.bluetooth.model.BluetoothConnectionState
import com.yokonex.bililive.data.bluetooth.model.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DefaultBluetoothRepository(
    private val bleManager: AndroidBleManager,
    private val waveformDao: WaveformDao,
    private val settingsStore: SettingsStore,
    private val waveformRuntime: EmsWaveformRuntime,
    private val protocolEncoder: EmsProtocolEncoder,
    private val builtinWaveforms: Map<String, com.yokonex.bililive.domain.model.WaveformDefinition> =
        DefaultWaveforms.all.associateBy { waveform -> waveform.id },
) : BluetoothRepository {
    private val _connectionState = MutableStateFlow(BluetoothConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<BluetoothConnectionState> = _connectionState.asStateFlow()
    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    override val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()

    private var lastScannedDevices: List<BluetoothDevice> = emptyList()
    private var connectedDevice: BluetoothDevice? = null

    override suspend fun scan(): List<BluetoothDevice> {
        _connectionState.value = BluetoothConnectionState.SCANNING
        return runCatching { bleManager.scan() }
            .onSuccess { devices ->
                lastScannedDevices = markConnectedDevice(devices, connectedDevice?.id)
                _devices.value = lastScannedDevices
                _connectionState.value = if (connectedDevice == null) {
                    BluetoothConnectionState.DISCONNECTED
                } else {
                    BluetoothConnectionState.CONNECTED
                }
            }
            .getOrElse { error ->
                _connectionState.value = BluetoothConnectionState.ERROR
                throw error
            }
    }

    override suspend fun connect(deviceId: String) {
        val device = lastScannedDevices.firstOrNull { it.id == deviceId }
            ?: throw IllegalArgumentException("未找到指定蓝牙设备")
        _connectionState.value = BluetoothConnectionState.CONNECTING
        runCatching {
            bleManager.connect(deviceId)
            connectedDevice = device.copy(connected = true)
            lastScannedDevices = markConnectedDevice(lastScannedDevices, deviceId)
            _devices.value = lastScannedDevices
            settingsStore.updateRecentDeviceId(deviceId)
            if (device.protocol == "ems_v2") {
                bleManager.write(protocolEncoder.createBatteryQueryPacket())
            }
            _connectionState.value = BluetoothConnectionState.CONNECTED
        }.getOrElse { error ->
            connectedDevice = null
            lastScannedDevices = markConnectedDevice(lastScannedDevices, null)
            _devices.value = lastScannedDevices
            _connectionState.value = BluetoothConnectionState.ERROR
            throw error
        }
    }

    override suspend fun disconnect() {
        runCatching { bleManager.disconnect() }
        connectedDevice = null
        lastScannedDevices = markConnectedDevice(lastScannedDevices, null)
        _devices.value = lastScannedDevices
        _connectionState.value = BluetoothConnectionState.DISCONNECTED
    }

    override suspend fun playWaveform(waveformId: String) {
        val device = connectedDevice ?: throw IllegalStateException("当前没有已连接的蓝牙设备")
        val waveform = waveformDao.findById(waveformId)
            ?.let(WaveformMapper::fromEntity)
            ?: builtinWaveforms[waveformId]
            ?: throw IllegalArgumentException("未找到波形 $waveformId")
        waveformRuntime.play(
            waveform = waveform,
            protocol = device.protocol,
        )
    }

    private fun markConnectedDevice(
        devices: List<BluetoothDevice>,
        connectedDeviceId: String?,
    ): List<BluetoothDevice> =
        devices.map { device ->
            device.copy(connected = device.id == connectedDeviceId)
        }
}
