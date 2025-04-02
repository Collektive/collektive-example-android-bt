package it.unibo.collektive.network.bluetooth

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.uuid.Uuid

class BluetoothNeighborsDiscoverer(private val deviceId: String, private val context: Context) {
    private val bleManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bleAdapter = bleManager.adapter
    private val bleScanner = bleAdapter.bluetoothLeScanner
    private val advertiser = bleAdapter.bluetoothLeAdvertiser
    private val encodedDeviceId: ByteArray by lazy { deviceId.encodeToByteArray() }
    private val advertiseIdData = AdvertiseData.Builder().apply {
        val serviceId = ParcelUuid(SERVICE_UUID)
        addServiceData(serviceId, encodedDeviceId)
        setIncludeDeviceName(true)
    }.build()
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.scanRecord?.let { scanRecord ->
                scanRecord.serviceUuids.forEach { parcelUuid ->
                    if (parcelUuid.uuid == SERVICE_UUID) {
                        val serviceData = scanRecord.getServiceData(ParcelUuid(SERVICE_UUID))
                        serviceData?.decodeToString()?.let { neighborDeviceId ->
                            _neighborsIds.value += Uuid.parse(neighborDeviceId)
                            Log.i(TAG, "Discovered neighbor device with ID: $neighborDeviceId")
                        }
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "Scan failed with error: $errorCode")
        }
    }
    private val _neighborsIds = MutableStateFlow(emptySet<Uuid>())
    private var isAdvertising = false

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_SCAN])
    fun startAdvertisingDeviceId() {
        if (!isAdvertising) {
            advertiser.startAdvertising(ADVERTISE_SETTINGS, advertiseIdData, ADVERTISE_CALLBACK)
            bleScanner.startScan(SCAN_FILTER, SCAN_SETTINGS, scanCallback)
            isAdvertising = true
            Log.i(TAG, "Start advertising and scanning")
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_SCAN])
    fun stopAdvertisingDeviceId() {
        if (isAdvertising) {
            advertiser.stopAdvertising(ADVERTISE_CALLBACK)
            bleScanner.stopScan(scanCallback)
            isAdvertising = false
            Log.i(TAG, "Stop advertising and scanning")
        }
    }

    fun neighborsIds(): StateFlow<Set<Uuid>> = _neighborsIds.asStateFlow()

    private companion object {
        private const val TAG: String = "BluetoothIdAdvertiser"
        private val SERVICE_UUID = UUID.fromString("0000AAAA-0000-1000-8000-00805F9B34FB")
        private val ADVERTISE_SETTINGS = AdvertiseSettings.Builder().apply {
            setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            setConnectable(false)
        }.build()
        private val ADVERTISE_CALLBACK = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                super.onStartSuccess(settingsInEffect)
                Log.i(TAG, "Advertising start successfully")
                Log.d(TAG, "Advertising settings in use: $settingsInEffect")
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                Log.e(TAG, "Advertising failed with error: $errorCode")
            }
        }
        private val SCAN_FILTER = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build())
        private val SCAN_SETTINGS = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
    }
}
