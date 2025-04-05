package it.unibo.collektive.viewmodels

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.collektive.Collektive
import it.unibo.collektive.aggregate.api.Aggregate.Companion.neighboring
import it.unibo.collektive.network.mqtt.MqttMailbox
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * A ViewModel that manages the list of nearby devices.
 */
class NearbyDevicesViewModel(application: Application) : AndroidViewModel(application) {
    @Suppress("InjectDispatcher")
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
    private val _dataFlow = MutableStateFlow<Set<Uuid>>(emptySet())
    private val _connectionFlow = MutableStateFlow(ConnectionState.DISCONNECTED)

    /**
     * The connection state.
     */
    enum class ConnectionState {
        /**
         * Connected to the broker.
         */
        CONNECTED,

        /**
         * Disconnected from the broker.
         */
        DISCONNECTED,
    }

    /**
     * The set of nearby devices.
     */
    val dataFlow: StateFlow<Set<Uuid>> = _dataFlow.asStateFlow()

    /**
     * The connection state.
     */
    val connectionFlow: StateFlow<ConnectionState> = _connectionFlow.asStateFlow()

    /**
     * The local device ID.
     */
    val deviceId = Uuid.random()

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_SCAN])
    private suspend fun collektiveProgram(): Collektive<Uuid, Set<Uuid>> {
        val mailbox =
            MqttMailbox(deviceId, "broker.hivemq.com", dispatcher = dispatcher, context = getApplication())
        return Collektive(deviceId, mailbox) {
            neighboring(localId).neighbors.toSet()
        }
    }

    /**
     * Start the Collektive program.
     */
    fun startCollektiveProgram() {
        viewModelScope.launch {
            Log.i("NearbyDevicesViewModel", "Starting Collektive program...")
            val hasScan = ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.BLUETOOTH_SCAN,
            ) == PackageManager.PERMISSION_GRANTED
            val hasAdvertise = ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.BLUETOOTH_ADVERTISE,
            ) == PackageManager.PERMISSION_GRANTED
            if (hasScan && hasAdvertise) {
                val program = collektiveProgram()
                _connectionFlow.value = ConnectionState.CONNECTED
                Log.i("NearbyDevicesViewModel", "Collektive program started")
                while (true) {
                    val newResult = program.cycle()
                    _dataFlow.value = newResult
                    delay(1.seconds)
                    Log.i("NearbyDevicesViewModel", "New nearby devices: $newResult")
                }
            } else {
                Log.e("NearbyDevicesViewModel", "Permissions not granted")
                _connectionFlow.value = ConnectionState.DISCONNECTED
            }
        }
    }
}
