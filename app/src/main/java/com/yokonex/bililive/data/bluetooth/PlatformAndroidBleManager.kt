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
import java.util.UUID
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PlatformAndroidBleManager(
    context: Context,
    private val deviceClassifier: BluetoothDeviceClassifier = BluetoothDeviceClassifier(),
) : AndroidBleManager {
    private val appContext = context.applicationContext
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter =
        bluetoothManager?.adapter ?: error("当前设备不支持蓝牙")

    @Volatile
    private var currentGatt: BluetoothGatt? = null

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
        val device = bluetoothAdapter.getRemoteDevice(deviceId)
        connectGatt(device)
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() {
        currentGatt?.disconnect()
        currentGatt?.close()
        currentGatt = null
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
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        gatt.close()
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("蓝牙连接失败 status=$status"),
                            )
                        }
                        return
                    }
                    if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                        gatt.discoverServices()
                    } else if (newState == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED) {
                        currentGatt = null
                        gatt.close()
                    }
                }

                override fun onServicesDiscovered(
                    gatt: BluetoothGatt,
                    status: Int,
                ) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        gatt.close()
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

    private companion object {
        val EMS_SERVICE_UUID: UUID = UUID.fromString("0000ff30-0000-1000-8000-00805f9b34fb")
        val EMS_WRITE_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000ff31-0000-1000-8000-00805f9b34fb")
        val EMS_NOTIFY_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000ff32-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
