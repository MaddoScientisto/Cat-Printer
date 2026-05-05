package soft.naitlee.catprinter.nativeapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CatPrinterBleClient(private val context: Context) {
    data class ScannedPrinter(val device: BluetoothDevice, val name: String, val address: String)

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
    private val adapter = bluetoothManager.adapter
    private val mainHandler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var paused = false
    @Volatile private var writeLatch: CountDownLatch? = null
    @Volatile private var writeStatus: Int = BluetoothGatt.GATT_SUCCESS
    @Volatile private var mtuPayloadSize: Int = 20

    fun rememberedPrinter(name: String, address: String): ScannedPrinter? {
        val bluetoothAdapter = adapter ?: return null
        if (!BluetoothAdapter.checkBluetoothAddress(address)) return null
        return ScannedPrinter(bluetoothAdapter.getRemoteDevice(address), name, address)
    }

    fun scan(timeoutMillis: Long, includeUnknown: Boolean, done: (Result<List<ScannedPrinter>>) -> Unit) {
        if (adapter == null) {
            done(Result.failure(IllegalStateException("Bluetooth is unavailable on this device")))
            return
        }
        if (!adapter.isEnabled) {
            done(Result.failure(IllegalStateException("Bluetooth is turned off")))
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            done(Result.failure(IllegalStateException("BLE scanner is unavailable")))
            return
        }

        val found = linkedMapOf<String, ScannedPrinter>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = remember(result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { remember(it) }
            override fun onScanFailed(errorCode: Int) {
                mainHandler.post { done(Result.failure(IllegalStateException("BLE scan failed: $errorCode"))) }
            }

            @SuppressLint("MissingPermission")
            private fun remember(result: ScanResult) {
                val device = result.device ?: return
                val advertisedName = result.scanRecord?.deviceName
                val name = advertisedName ?: device.name ?: return
                if (!includeUnknown && !PrinterModel.isSupportedName(name)) return
                found[device.address] = ScannedPrinter(device, name, device.address)
            }
        }

        try {
            scanner.startScan(callback)
        } catch (security: SecurityException) {
            done(Result.failure(security))
            return
        }

        mainHandler.postDelayed({
            try {
                scanner.stopScan(callback)
            } catch (_: Exception) {
            }
            done(Result.success(found.values.toList()))
        }, timeoutMillis)
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice, done: (Result<Unit>) -> Unit) {
        disconnect()
        mtuPayloadSize = 20
        var attempt = 0
        var completed = false

        fun closeFailedGatt(failedGatt: BluetoothGatt) {
            try {
                refreshDeviceCache(failedGatt)
                failedGatt.disconnect()
                failedGatt.close()
            } catch (_: Exception) {
            }
            if (gatt === failedGatt) gatt = null
            txCharacteristic = null
        }

        fun finish(result: Result<Unit>) {
            if (completed) return
            completed = true
            mainHandler.post { done(result) }
        }

        fun connectAttempt() {
            attempt++
            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (completed) return
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        closeFailedGatt(gatt)
                        if (attempt < CONNECTION_ATTEMPTS) {
                            mainHandler.postDelayed({ connectAttempt() }, CONNECTION_RETRY_DELAY_MS)
                        } else {
                            finish(Result.failure(IllegalStateException("GATT connection failed: $status")))
                        }
                        return
                    }
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        this@CatPrinterBleClient.gatt = gatt
                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                        mainHandler.postDelayed({
                            if (!completed && !gatt.discoverServices()) {
                                closeFailedGatt(gatt)
                                finish(Result.failure(IllegalStateException("Service discovery could not be started")))
                            }
                        }, SERVICE_DISCOVERY_DELAY_MS)
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        txCharacteristic = null
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (completed) return
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        closeFailedGatt(gatt)
                        finish(Result.failure(IllegalStateException("Service discovery failed: $status")))
                        return
                    }
                    txCharacteristic = gatt.services
                        .flatMap { it.characteristics }
                        .firstOrNull { it.uuid == UUID.fromString(CatPrinterProtocol.TX_CHARACTERISTIC) }
                    val rx = gatt.services
                        .flatMap { it.characteristics }
                        .firstOrNull { it.uuid == UUID.fromString(CatPrinterProtocol.RX_CHARACTERISTIC) }
                    if (txCharacteristic == null || rx == null) {
                        closeFailedGatt(gatt)
                        finish(Result.failure(IllegalStateException("Printer GATT characteristics were not found")))
                        return
                    }
                    gatt.setCharacteristicNotification(rx, true)
                    rx.descriptors.firstOrNull { it.uuid == CLIENT_CONFIG_UUID }?.let { descriptor ->
                        if (Build.VERSION.SDK_INT >= 33) {
                            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(descriptor)
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 21) gatt.requestMtu(247)
                    finish(Result.success(Unit))
                }

                override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) mtuPayloadSize = (mtu - 3).coerceIn(20, 200)
                }

                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                    updatePauseState(value)
                }

                @Deprecated("Deprecated in Java")
                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    @Suppress("DEPRECATION")
                    updatePauseState(characteristic.value)
                }

                override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                    writeStatus = status
                    writeLatch?.countDown()
                }
            }
            gatt = connectGatt(device, callback)
        }

        connectAttempt()
    }

    fun print(commands: Sequence<ByteArray>, status: (String) -> Unit) {
        val characteristic = txCharacteristic ?: error("Not connected to a printer")
        val gattHandle = gatt ?: error("Not connected to a printer")
        var commandCount = 0
        commands.forEach { command ->
            writeBytes(gattHandle, characteristic, command)
            commandCount++
            if (commandCount % 48 == 0) status("Sent $commandCount commands")
        }
    }

    fun isConnected(): Boolean = gatt != null && txCharacteristic != null

    @SuppressLint("MissingPermission")
    fun disconnect() {
        txCharacteristic = null
        paused = false
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {
        } finally {
            gatt = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeBytes(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            while (paused) Thread.sleep(200)
            val chunk = bytes.copyOfRange(offset, minOf(offset + mtuPayloadSize, bytes.size))
            writeLatch = CountDownLatch(1)
            writeStatus = BluetoothGatt.GATT_FAILURE
            val writeType = if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            characteristic.writeType = writeType
            @Suppress("DEPRECATION")
            characteristic.value = chunk
            @Suppress("DEPRECATION")
            val accepted = gatt.writeCharacteristic(characteristic)
            if (!accepted) error("Printer rejected a BLE write")
            val completed = writeLatch?.await(5, TimeUnit.SECONDS) == true
            if (!completed || writeStatus != BluetoothGatt.GATT_SUCCESS) error("BLE write failed: $writeStatus")
            Thread.sleep(20)
            offset += chunk.size
        }
    }

    private fun updatePauseState(value: ByteArray?) {
        if (value == null) return
        paused = when {
            value.contentEquals(CatPrinterProtocol.dataFlowPause) -> true
            value.contentEquals(CatPrinterProtocol.dataFlowResume) -> false
            else -> paused
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectGatt(device: BluetoothDevice, callback: BluetoothGattCallback): BluetoothGatt = when {
        Build.VERSION.SDK_INT >= 26 -> device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK)
        Build.VERSION.SDK_INT >= 23 -> device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        else -> device.connectGatt(context, false, callback)
    }

    private fun refreshDeviceCache(gatt: BluetoothGatt) {
        try {
            gatt.javaClass.getMethod("refresh").invoke(gatt)
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val CONNECTION_ATTEMPTS = 5
        private const val CONNECTION_RETRY_DELAY_MS = 1_200L
        private const val SERVICE_DISCOVERY_DELAY_MS = 500L
        private val CLIENT_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        fun requiredPermissions(): Array<String> = buildList {
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }.toTypedArray()

        fun hasRequiredPermissions(context: Context): Boolean =
            requiredPermissions().all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }
}
