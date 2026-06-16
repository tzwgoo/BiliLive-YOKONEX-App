package com.yokonex.bililive.data.bluetooth

import com.yokonex.bililive.data.mapper.WaveformMapper
import com.yokonex.bililive.data.bluetooth.model.BluetoothConnectionState
import com.yokonex.bililive.data.bluetooth.model.BluetoothDevice
import com.yokonex.bililive.data.bluetooth.model.BluetoothRuntimeStatus
import com.yokonex.bililive.data.storage.DefaultWaveforms
import com.yokonex.bililive.data.storage.SettingsStore
import com.yokonex.bililive.data.storage.dao.WaveformDao
import com.yokonex.bililive.domain.model.LiveEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    private var mixModeEnabled: Boolean = false
    private var mixElapsedMs: Long = 0L
    private var mixPlaybackJob: Job? = null
    private var mixRuntime = BluetoothMixRuntime(
        bleManager = bleManager,
        protocolEncoder = protocolEncoder,
        protocol = "ems_v2",
    )

    init {
        scope.launch {
            bleManager.telemetry.collect { telemetry ->
                _runtimeStatus.update { currentStatus ->
                    currentStatus.copy(batteryLevel = telemetry.batteryLevel)
                }
            }
        }
        scope.launch {
            // 混波开关需要和持久化配置保持一致，避免应用重启后仓库仍沿用旧的内存状态。
            settingsStore.bluetoothMixModeEnabled.collect { enabled ->
                applyMixModeEnabled(enabled)
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
                settingsStore.updateRecentDeviceName(device.name)
                _runtimeStatus.value = BluetoothRuntimeStatus(
                    connected = true,
                    deviceName = device.name,
                    batteryLevel = bleManager.telemetry.value.batteryLevel,
                    mixModeEnabled = mixModeEnabled,
                )
                mixRuntime = BluetoothMixRuntime(
                    bleManager = bleManager,
                    protocolEncoder = protocolEncoder,
                    protocol = device.protocol,
                )
                mixElapsedMs = 0L
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
            mixPlaybackJob?.cancel()
            mixPlaybackJob = null
            mixRuntime.clearLayers()
            mixElapsedMs = 0L
            _runtimeStatus.value = BluetoothRuntimeStatus()
            _connectionState.value = BluetoothConnectionState.DISCONNECTED
        }
    }

    override suspend fun playWaveform(
        waveformId: String,
        repeatCount: Int,
    ) {
        val device = connectedDevice ?: throw IllegalStateException("当前没有已连接的蓝牙设备")
        val waveform = resolveWaveform(waveformId)
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

    override suspend fun enqueueWaveform(
        waveformId: String,
        eventType: LiveEventType,
        repeatCount: Int,
    ) {
        if (!mixModeEnabled) {
            playWaveform(waveformId, repeatCount)
            return
        }
        val device = connectedDevice ?: throw IllegalStateException("当前没有已连接的蓝牙设备")
        val waveform = resolveWaveform(waveformId)
        mixRuntime.enqueueLayer(
            com.yokonex.bililive.data.bluetooth.model.ActiveWaveformLayer(
                id = "${eventType.name.lowercase()}-${System.nanoTime()}",
                eventType = eventType,
                waveform = waveform,
                startedAtElapsedMs = mixElapsedMs,
                repeatCount = repeatCount.coerceAtLeast(1),
                priority = MixPolicy.priorityOf(eventType),
                weight = MixPolicy.weightOf(eventType),
            ),
        )
        val frame = mixRuntime.tick(mixElapsedMs)
        updateMixRuntimeStatus(
            device = device,
            waveformName = waveform.name,
            frame = frame,
        )
        ensureMixPlaybackLoop(
            device = device,
            waveformName = waveform.name,
        )
    }

    override suspend fun clearActiveWaveforms() {
        mixPlaybackJob?.cancel()
        mixPlaybackJob = null
        mixRuntime.clearLayers()
        mixElapsedMs = 0L
        _runtimeStatus.update { currentStatus ->
            currentStatus.copy(
                waveformName = "",
                channelAStrength = 0,
                channelBStrength = 0,
            )
        }
    }

    override fun setMixModeEnabled(enabled: Boolean) {
        applyMixModeEnabled(enabled)
    }

    private fun ensureMixPlaybackLoop(
        device: BluetoothDevice,
        waveformName: String,
    ) {
        if (mixPlaybackJob?.isActive == true) {
            return
        }
        mixPlaybackJob = scope.launch {
            while (mixRuntime.hasActiveLayers()) {
                delay(MixPolicy.DEFAULT_TICK_MS)
                mixElapsedMs += MixPolicy.DEFAULT_TICK_MS
                val frame = mixRuntime.tick(mixElapsedMs)
                updateMixRuntimeStatus(
                    device = device,
                    waveformName = waveformName,
                    frame = frame,
                )
            }
        }
    }

    private suspend fun resolveWaveform(
        waveformId: String,
    ): com.yokonex.bililive.domain.model.WaveformDefinition =
        waveformDao.findById(waveformId)
            ?.let(WaveformMapper::fromEntity)
            ?: builtinWaveforms[waveformId]
            ?: throw IllegalArgumentException("未找到波形 $waveformId")

    private fun applyMixModeEnabled(enabled: Boolean) {
        mixModeEnabled = enabled
        if (!enabled) {
            // 关闭混波时要立即清理叠加层，避免界面已经切回串行，但底层仍继续输出旧的混波帧。
            mixPlaybackJob?.cancel()
            mixPlaybackJob = null
            mixRuntime.clearLayers()
            mixElapsedMs = 0L
        }
        _runtimeStatus.update { currentStatus ->
            currentStatus.copy(
                waveformName = if (!enabled) "" else currentStatus.waveformName,
                channelAStrength = if (!enabled) 0 else currentStatus.channelAStrength,
                channelBStrength = if (!enabled) 0 else currentStatus.channelBStrength,
                leaderEventType = if (!enabled) null else currentStatus.leaderEventType,
                activeLayerCount = if (!enabled) 0 else currentStatus.activeLayerCount,
                outputCap = if (!enabled) MixPolicy.NORMAL_CAP else currentStatus.outputCap,
                mixedChannelAStrength = if (!enabled) 0 else currentStatus.mixedChannelAStrength,
                mixedChannelBStrength = if (!enabled) 0 else currentStatus.mixedChannelBStrength,
                mixModeEnabled = enabled,
            )
        }
    }

    private fun updateMixRuntimeStatus(
        device: BluetoothDevice,
        waveformName: String,
        frame: com.yokonex.bililive.data.bluetooth.model.MixFrame?,
    ) {
        _runtimeStatus.update { currentStatus ->
            currentStatus.copy(
                connected = true,
                deviceName = device.name,
                waveformName = waveformName,
                channelAStrength = frame?.channelA ?: 0,
                channelBStrength = frame?.channelB ?: 0,
                leaderEventType = frame?.leaderEventType,
                activeLayerCount = mixRuntime.activeLayerCount(),
                outputCap = frame?.cap ?: MixPolicy.NORMAL_CAP,
                mixedChannelAStrength = frame?.channelA ?: 0,
                mixedChannelBStrength = frame?.channelB ?: 0,
                mixModeEnabled = mixModeEnabled,
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
