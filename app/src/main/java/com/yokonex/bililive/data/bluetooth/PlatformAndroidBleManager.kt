package com.yokonex.bililive.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice as AndroidBluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.yokonex.bililive.data.bluetooth.model.BluetoothDevice
import com.yokonex.bililive.data.bluetooth.model.BluetoothTelemetry
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PlatformAndroidBleManager(
    context: Context,
    private val deviceClassifier: BluetoothDeviceClassifier = BluetoothDeviceClassifier(),
    private val timestampProvider: () -> Long = System::currentTimeMillis,
    private val reconnectCooldownMillis: Long = DEFAULT_RECONNECT_COOLDOWN_MILLIS,
) : AndroidBleManager {
    private val appContext = context.applicationContext
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter =
        bluetoothManager?.adapter ?: error("当前设备不支持蓝牙")
    private val telemetryState = MutableStateFlow(BluetoothTelemetry())
    override val telemetry: StateFlow<BluetoothTelemetry> = telemetryState.asStateFlow()

    @Volatile
    private var currentGatt: BluetoothGatt? = null

    @Volatile
    private var lastDisconnectAtMillis: Long = 0L

    @SuppressLint("MissingPermission")
    override suspend fun scan(): List<BluetoothDevice> =
        suspendCancellableCoroutine { continuation ->
            val scanner = bluetoothAdapter.bluetoothLeScanner
                ?: return@suspendCancellableCoroutine continuation.resumeWithException(
                    IllegalStateException("当前设备不支持 BLE 扫描"),
                )
            val devices = linkedMapOf<String, BluetoothDevice>()
            val callback = object : ScanCallback() {
                override fun onScanResult(
                    callbackType: Int,
                    result: ScanResult,
                ) {
                    val name = result.scanRecord?.deviceName ?: result.device.name ?: result.device.address
                    val serviceUuids = result.scanRecord?.serviceUuids
                        ?.map(ParcelUuid::getUuid)
                        ?.map(UUID::toString)
                        ?.toSet()
                        .orEmpty()
                    val mapped = deviceClassifier.classify(
                        deviceId = result.device.address,
                        name = name,
                        serviceUuids = serviceUuids,
                    )
                    if (mapped.protocol == "unknown") {
                        return
                    }
                    devices[mapped.id] = mapped
                }

                override fun onScanFailed(errorCode: Int) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException("蓝牙扫描失败 errorCode=$errorCode"),
                        )
                    }
                }
            }

            scanner.startScan(callback)
            continuation.invokeOnCancellation {
                runCatching { scanner.stopScan(callback) }
            }
            android.os.Handler(appContext.mainLooper).postDelayed(
                {
                    runCatching { scanner.stopScan(callback) }
                    if (continuation.isActive) {
                        continuation.resume(devices.values.toList())
                    }
                },
                6_000L,
            )
        }

    @SuppressLint("MissingPermission")
    override suspend fun connect(deviceId: String) {
        disconnect()
        val reconnectDelayMillis = calculateReconnectDelayMillis(
            nowMillis = timestampProvider(),
            lastDisconnectAtMillis = lastDisconnectAtMillis,
            cooldownMillis = reconnectCooldownMillis,
        )
        if (reconnectDelayMillis > 0L) {
            delay(reconnectDelayMillis)
        }
        telemetryState.value = BluetoothTelemetry()
        val device = bluetoothAdapter.getRemoteDevice(deviceId)
        connectGatt(device)
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() {
        val gatt = currentGatt
        if (gatt != null) {
            gatt.disconnect()
            withTimeoutOrNull(2_000L) {
                while (currentGatt === gatt) {
                    delay(50L)
                }
            }
            if (currentGatt === gatt) {
                gatt.close()
                currentGatt = null
                markDisconnectedNow()
            }
        }
        currentGatt = null
        telemetryState.value = BluetoothTelemetry()
    }

    @SuppressLint("MissingPermission")
    override suspend fun write(packet: ByteArray) {
        val gatt = currentGatt ?: throw IllegalStateException("当前没有已连接的蓝牙设备")
        val characteristic = gatt.findWriteCharacteristic()
            ?: throw IllegalStateException("未找到 EMS 写入特征")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = gatt.writeCharacteristic(
                characteristic,
                packet,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
            )
            if (status != BluetoothGatt.GATT_SUCCESS) {
                throw IllegalStateException("蓝牙写入失败 status=$status")
            }
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            characteristic.value = packet
            @Suppress("DEPRECATION")
            if (!gatt.writeCharacteristic(characteristic)) {
                throw IllegalStateException("蓝牙写入失败")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectGatt(device: AndroidBluetoothDevice) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(
                    gatt: BluetoothGatt,
                    status: Int,
                    newState: Int,
                ) {
                    when (resolveConnectionStateAction(status, newState)) {
                        GattConnectionAction.DISCOVER_SERVICES -> {
                            gatt.discoverServices()
                        }

                        GattConnectionAction.FAIL_CONNECTION -> {
                            currentGatt = null
                            gatt.close()
                            markDisconnectedNow()
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    IllegalStateException(
                                        buildConnectionFailureMessage(
                                            status = status,
                                            newState = newState,
                                        ),
                                    ),
                                )
                            }
                        }

                        GattConnectionAction.CLOSE_AS_DISCONNECTED -> {
                            currentGatt = null
                            gatt.close()
                            markDisconnectedNow()
                        }

                        GattConnectionAction.IGNORE -> Unit
                    }
                }

                override fun onServicesDiscovered(
                    gatt: BluetoothGatt,
                    status: Int,
                ) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        currentGatt = null
                        gatt.close()
                        markDisconnectedNow()
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("蓝牙服务发现失败 status=$status"),
                            )
                        }
                        return
                    }
                    enableNotifyIfPossible(gatt)
                    currentGatt = gatt
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                ) {
                    updateTelemetry(characteristic.value)
                }
            }

            val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(appContext, false, callback, AndroidBluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(appContext, false, callback)
            }
            continuation.invokeOnCancellation {
                gatt?.disconnect()
                gatt?.close()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifyIfPossible(gatt: BluetoothGatt) {
        val characteristic = gatt.findNotifyCharacteristic() ?: return
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun BluetoothGatt.findWriteCharacteristic(): BluetoothGattCharacteristic? =
        getService(EMS_SERVICE_UUID)?.getCharacteristic(EMS_WRITE_CHARACTERISTIC_UUID)

    private fun BluetoothGatt.findNotifyCharacteristic(): BluetoothGattCharacteristic? =
        getService(EMS_SERVICE_UUID)?.getCharacteristic(EMS_NOTIFY_CHARACTERISTIC_UUID)

    private fun updateTelemetry(packet: ByteArray?) {
        val batteryLevel = parseBatteryLevel(packet ?: return) ?: return
        telemetryState.value = BluetoothTelemetry(batteryLevel = batteryLevel)
    }

    private fun parseBatteryLevel(packet: ByteArray): Int? {
        if (packet.size < 4) {
            return null
        }
        if (packet[0].toInt() != 0x35 || packet[1].toInt() != 0x71 || packet[2].toInt() != 0x04) {
            return null
        }
        return packet[3].toInt().and(0xFF).coerceIn(0, 100)
    }

    private fun markDisconnectedNow() {
        lastDisconnectAtMillis = timestampProvider()
    }

    private companion object {
        const val DEFAULT_RECONNECT_COOLDOWN_MILLIS = 1_000L
        val EMS_SERVICE_UUID: UUID = UUID.fromString("0000ff30-0000-1000-8000-00805f9b34fb")
        val EMS_WRITE_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000ff31-0000-1000-8000-00805f9b34fb")
        val EMS_NOTIFY_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000ff32-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

internal enum class GattConnectionAction {
    DISCOVER_SERVICES,
    FAIL_CONNECTION,
    CLOSE_AS_DISCONNECTED,
    IGNORE,
}

internal fun resolveConnectionStateAction(
    status: Int,
    newState: Int,
): GattConnectionAction =
    when {
        newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED -> GattConnectionAction.DISCOVER_SERVICES
        status != BluetoothGatt.GATT_SUCCESS -> GattConnectionAction.FAIL_CONNECTION
        newState == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED -> GattConnectionAction.CLOSE_AS_DISCONNECTED
        else -> GattConnectionAction.IGNORE
    }

internal fun buildConnectionFailureMessage(
    status: Int,
    newState: Int = android.bluetooth.BluetoothProfile.STATE_DISCONNECTED,
): String =
    when (status) {
        201 -> "蓝牙连接失败，设备可能尚未完全释放或系统蓝牙栈正忙（status=$status, newState=$newState），请稍后重试"
        else -> "蓝牙连接失败 status=$status newState=$newState"
    }

internal fun calculateReconnectDelayMillis(
    nowMillis: Long,
    lastDisconnectAtMillis: Long,
    cooldownMillis: Long,
): Long {
    if (lastDisconnectAtMillis <= 0L || cooldownMillis <= 0L) {
        return 0L
    }
    val nextAllowedAt = lastDisconnectAtMillis + cooldownMillis
    return (nextAllowedAt - nowMillis).coerceAtLeast(0L)
}
