package com.lowrider.fit

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class ConnectionStatus { Disconnected, Scanning, Connecting, Connected }

data class HrmState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val heartRate: Int = 0,
    val deviceName: String? = null,
    val scanResults: List<HrmScanResult> = emptyList()
)

data class HrmScanResult(
    val device: BluetoothDevice,
    val name: String,
    val rssi: Int
)

@SuppressLint("MissingPermission")
class HeartRateMonitorManager(private val context: Context) {

    private val _state = MutableStateFlow(HrmState())
    val state: StateFlow<HrmState> = _state.asStateFlow()

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())

    private var reconnectAttempts = 0
    private var shouldReconnect = false

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "HrmManager"
        private const val PREFS_NAME = "hrm_prefs"
        private const val KEY_DEVICE_ADDRESS = "last_hrm_address"
        private const val KEY_DEVICE_NAME = "last_hrm_name"
        private const val SCAN_TIMEOUT_MS = 10_000L
        private const val MAX_RECONNECT_ATTEMPTS = 3

        val HR_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        @Volatile
        private var instance: HeartRateMonitorManager? = null

        fun getInstance(context: Context): HeartRateMonitorManager {
            return instance ?: synchronized(this) {
                instance ?: HeartRateMonitorManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    fun hasSavedDevice(): Boolean = prefs.getString(KEY_DEVICE_ADDRESS, null) != null

    fun savedDeviceName(): String? = prefs.getString(KEY_DEVICE_NAME, null)

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun startScan() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return

        scanner = adapter.bluetoothLeScanner ?: return

        _state.value = HrmState(
            connectionStatus = ConnectionStatus.Scanning,
            scanResults = emptyList()
        )

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(HR_SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(listOf(filter), settings, scanCallback)
        Log.d(TAG, "BLE scan started")

        handler.postDelayed({ stopScan() }, SCAN_TIMEOUT_MS)
    }

    fun stopScan() {
        scanner?.stopScan(scanCallback)
        scanner = null
        if (_state.value.connectionStatus == ConnectionStatus.Scanning) {
            _state.value = _state.value.copy(connectionStatus = ConnectionStatus.Disconnected)
        }
        Log.d(TAG, "BLE scan stopped")
    }

    fun connectToDevice(device: BluetoothDevice) {
        stopScan()
        reconnectAttempts = 0
        shouldReconnect = true

        val name = device.name ?: context.getString(R.string.hrm_unknown_device)
        saveDevice(device.address, name)

        _state.value = HrmState(
            connectionStatus = ConnectionStatus.Connecting,
            deviceName = name
        )

        gatt?.close()
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        Log.d(TAG, "Connecting to ${device.address}")
    }

    fun connectToSaved() {
        val address = prefs.getString(KEY_DEVICE_ADDRESS, null) ?: return
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return

        val device = adapter.getRemoteDevice(address)
        val name = prefs.getString(KEY_DEVICE_NAME, null)
            ?: context.getString(R.string.hrm_unknown_device)

        reconnectAttempts = 0
        shouldReconnect = true

        _state.value = HrmState(
            connectionStatus = ConnectionStatus.Connecting,
            deviceName = name
        )

        gatt?.close()
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        Log.d(TAG, "Reconnecting to saved device $address")
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectAttempts = MAX_RECONNECT_ATTEMPTS
        handler.removeCallbacksAndMessages(null)
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _state.value = HrmState(connectionStatus = ConnectionStatus.Disconnected)
        Log.d(TAG, "Disconnected")
    }

    fun clearSavedDevice() {
        prefs.edit().remove(KEY_DEVICE_ADDRESS).remove(KEY_DEVICE_NAME).apply()
    }

    fun release() {
        disconnect()
        stopScan()
    }

    private fun saveDevice(address: String, name: String) {
        prefs.edit()
            .putString(KEY_DEVICE_ADDRESS, address)
            .putString(KEY_DEVICE_NAME, name)
            .apply()
    }

    private fun attemptReconnect() {
        if (!shouldReconnect || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            _state.value = _state.value.copy(connectionStatus = ConnectionStatus.Disconnected)
            Log.d(TAG, "Reconnect exhausted after $reconnectAttempts attempts")
            return
        }

        reconnectAttempts++
        val delayMs = 1000L * (1L shl (reconnectAttempts - 1)) // 1s, 2s, 4s
        Log.d(TAG, "Reconnect attempt $reconnectAttempts in ${delayMs}ms")

        handler.postDelayed({ connectToSaved() }, delayMs)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: context.getString(R.string.hrm_unknown_device)
            val existing = _state.value.scanResults.toMutableList()

            val index = existing.indexOfFirst { it.device.address == device.address }
            val entry = HrmScanResult(device = device, name = name, rssi = result.rssi)
            if (index >= 0) {
                existing[index] = entry
            } else {
                existing.add(entry)
            }

            _state.value = _state.value.copy(scanResults = existing)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed: $errorCode")
            _state.value = _state.value.copy(connectionStatus = ConnectionStatus.Disconnected)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "GATT connected, discovering services")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "GATT disconnected, status=$status")
                    gatt.close()
                    this@HeartRateMonitorManager.gatt = null

                    if (shouldReconnect) {
                        _state.value = _state.value.copy(
                            connectionStatus = ConnectionStatus.Connecting,
                            heartRate = 0
                        )
                        handler.post { attemptReconnect() }
                    } else {
                        _state.value = HrmState(connectionStatus = ConnectionStatus.Disconnected)
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                return
            }

            val hrService = gatt.getService(HR_SERVICE_UUID)
            if (hrService == null) {
                Log.e(TAG, "HR service not found on device")
                disconnect()
                return
            }

            val hrChar = hrService.getCharacteristic(HR_MEASUREMENT_UUID)
            if (hrChar == null) {
                Log.e(TAG, "HR measurement characteristic not found")
                disconnect()
                return
            }

            gatt.setCharacteristicNotification(hrChar, true)
            val descriptor = hrChar.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                gatt.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                )
            }

            val name = _state.value.deviceName
                ?: gatt.device.name
                ?: context.getString(R.string.hrm_unknown_device)
            _state.value = HrmState(
                connectionStatus = ConnectionStatus.Connected,
                deviceName = name,
                heartRate = 0
            )
            reconnectAttempts = 0
            Log.d(TAG, "HR notifications enabled")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid != HR_MEASUREMENT_UUID) return
            val bpm = parseHeartRate(value)
            _state.value = _state.value.copy(heartRate = bpm)
        }
    }

    private fun parseHeartRate(value: ByteArray): Int {
        if (value.isEmpty()) return 0
        val flags = value[0].toInt() and 0xFF
        val is16Bit = (flags and 0x01) != 0
        return if (is16Bit && value.size >= 3) {
            (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
        } else if (value.size >= 2) {
            value[1].toInt() and 0xFF
        } else {
            0
        }
    }
}
