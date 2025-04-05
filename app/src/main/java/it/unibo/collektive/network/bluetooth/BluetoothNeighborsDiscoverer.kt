package it.unibo.collektive.network.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID
import kotlin.uuid.Uuid

/**
 * Discovers nearby Bluetooth devices by advertising its [deviceId] and scanning for others.
 */
class BluetoothNeighborsDiscoverer(private val deviceId: String, context: Context) {
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
                    Log.d(TAG, "Found: $parcelUuid")
                    if (parcelUuid.uuid == SERVICE_UUID) {
                        val serviceData = scanRecord.getServiceData(ParcelUuid(SERVICE_UUID))
                        serviceData?.decodeToString()?.let { neighborDeviceId ->
                            _neighborsIds.tryEmit(Uuid.parse(neighborDeviceId))
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
    private var isAdvertising = false
    private val _neighborsIds = MutableSharedFlow<Uuid>(replay = 1)

    /**
     * Returns a flow of discovered neighbor device IDs.
     */
    val neighborsIds: SharedFlow<Uuid> = _neighborsIds.asSharedFlow()

    /**
     * Starts advertising the device ID and scanning for nearby devices.
     */
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_SCAN])
    fun startAdvertisingDeviceId() {
        if (!isAdvertising) {
            advertiser.startAdvertisingSet(ADVERTISE_PARAMETERS, advertiseIdData, null, null, null, ADVERTISE_CALLBACK)
            bleScanner.startScan(SCAN_FILTER, SCAN_SETTINGS, scanCallback)
            isAdvertising = true
            Log.i(TAG, "Start advertising and scanning")
        }
    }

    /**
     * Stops advertising the device ID and scanning for nearby devices.
     */
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_SCAN])
    fun stopAdvertisingDeviceId() {
        if (isAdvertising) {
            advertiser.stopAdvertisingSet(ADVERTISE_CALLBACK)
            bleScanner.stopScan(scanCallback)
            isAdvertising = false
            Log.i(TAG, "Stop advertising and scanning")
        }
    }

    private companion object {
        private const val TAG: String = "BluetoothIdAdvertiser"
        private val SERVICE_UUID = UUID.fromString("0000AAAA-0000-1000-8000-00805F9B34FB")
        private val ADVERTISE_PARAMETERS = AdvertisingSetParameters.Builder().apply {
            setLegacyMode(false)
            setInterval(AdvertisingSetParameters.INTERVAL_HIGH)
            setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
            setPrimaryPhy(BluetoothDevice.PHY_LE_1M)
            setSecondaryPhy(BluetoothDevice.PHY_LE_2M)
        }.build()
        private val ADVERTISE_CALLBACK = object : AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(advertisingSet: AdvertisingSet?, txPower: Int, status: Int) {
                super.onAdvertisingSetStarted(advertisingSet, txPower, status)
                Log.i(TAG, "Start advertising with tx power: $txPower")
            }

            override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet?) {
                super.onAdvertisingSetStopped(advertisingSet)
                Log.i(TAG, "Stop advertising")
            }
        }
        private val SCAN_FILTER = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build())
        private val SCAN_SETTINGS = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
    }
}
