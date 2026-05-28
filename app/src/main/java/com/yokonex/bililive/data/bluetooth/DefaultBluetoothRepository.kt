package com.yokonex.bililive.data.bluetooth

import com.yokonex.bililive.data.mapper.WaveformMapper
import com.yokonex.bililive.data.bluetooth.model.BluetoothConnectionState
import com.yokonex.bililive.data.bluetooth.model.BluetoothDevice
import com.yokonex.bililive.data.bluetooth.model.BluetoothRuntimeStatus
import com.yokonex.bililive.data.storage.DefaultWaveforms
import com.yokonex.bililive.data.storage.SettingsStore
import com.yokonex.bililive.data.storage.dao.WaveformDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultBluetoothRepository(
    private val bleManager: AndroidBleManager,
    private val waveformDao: WaveformDao,
    private val settingsStore: SettingsStore,
    private val waveformRuntime: EmsWaveformRuntime,
    private val protocolEncoder: EmsProtocolEncoder,
    private val builtinWaveforms: Map<String, com.yokonex.bililive.domain.model.WaveformDefinition> =
        DefaultWaveforms.all.associateBy { waveform -> waveform.id },
) : BluetoothRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _connectionState = MutableStateFlow(BluetoothConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<BluetoothConnectionState> = _connectionState.asStateFlow()
    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    override val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()
    private val _runtimeStatus = MutableStateFlow(BluetoothRuntimeStatus())
    override val runtimeStatus: StateFlow<BluetoothRuntimeStatus> = _runtimeStatus.asStateFlow()

    private var lastScannedDevices: List<BluetoothDevice> = emptyList()
    private var connectedDevice: BluetoothDevice? = null
    private val connectionMutex = Mutex()

    init {
        scope.launch {
            bleManager.telemetry.collect { telemetry ->
                _runtimeStatus.update { currentStatus ->
                    currentStatus.copy(batteryLevel = telemetry.batteryLevel)
                }
            }
        }
    }

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
        connectionMutex.withLock {
            if (connectedDevice?.id == deviceId && _connectionState.value == BluetoothConnectionState.CONNECTED) {
                return
            }
            val device = lastScannedDevices.firstOrNull { it.id == deviceId }
                ?: throw IllegalArgumentException("未找到指定蓝牙设备")
            _connectionState.value = BluetoothConnectionState.CONNECTING
            runCatching {
                bleManager.connect(deviceId)
                connectedDevice = device.copy(connected = true)
                lastScannedDevices = markConnectedDevice(lastScannedDevices, deviceId)
                _devices.value = lastScannedDevices
                settingsStore.updateRecentDeviceId(deviceId)
                _runtimeStatus.value = BluetoothRuntimeStatus(
                    connected = true,
                    deviceName = device.name,
                    batteryLevel = bleManager.telemetry.value.batteryLevel,
                )
                bleManager.write(protocolEncoder.createBatteryQueryPacket())
                _connectionState.value = BluetoothConnectionState.CONNECTED
            }.getOrElse { error ->
                connectedDevice = null
                lastScannedDevices = markConnectedDevice(lastScannedDevices, null)
                _devices.value = lastScannedDevices
                _runtimeStatus.value = BluetoothRuntimeStatus()
                _connectionState.value = BluetoothConnectionState.ERROR
                throw error
            }
        }
    }

    override suspend fun disconnect() {
        connectionMutex.withLock {
            runCatching { bleManager.disconnect() }
            connectedDevice = null
            lastScannedDevices = markConnectedDevice(lastScannedDevices, null)
            _devices.value = lastScannedDevices
            _runtimeStatus.value = BluetoothRuntimeStatus()
            _connectionState.value = BluetoothConnectionState.DISCONNECTED
        }
    }

    override suspend fun playWaveform(
        waveformId: String,
        repeatCount: Int,
    ) {
        val device = connectedDevice ?: throw IllegalStateException("当前没有已连接的蓝牙设备")
        val waveform = waveformDao.findById(waveformId)
            ?.let(WaveformMapper::fromEntity)
            ?: builtinWaveforms[waveformId]
            ?: throw IllegalArgumentException("未找到波形 $waveformId")
        repeat(repeatCount.coerceAtLeast(1)) {
            waveformRuntime.play(
                waveform = waveform,
                protocol = device.protocol,
                onStepStarted = { step ->
                    _runtimeStatus.update { currentStatus ->
                        currentStatus.copy(
                            connected = true,
                            deviceName = device.name,
                            waveformName = waveform.name,
                            channelAStrength = step.channelA.coerceIn(0, 100),
                            channelBStrength = step.channelB.coerceIn(0, 100),
                        )
                    }
                },
                onCompleted = {
                    _runtimeStatus.update { currentStatus ->
                        currentStatus.copy(
                            connected = true,
                            deviceName = device.name,
                            waveformName = waveform.name,
                            channelAStrength = 0,
                            channelBStrength = 0,
                        )
                    }
                },
            )
        }
    }

    private fun markConnectedDevice(
        devices: List<BluetoothDevice>,
        connectedDeviceId: String?,
    ): List<BluetoothDevice> =
        devices.map { device ->
            device.copy(connected = device.id == connectedDeviceId)
        }
}
