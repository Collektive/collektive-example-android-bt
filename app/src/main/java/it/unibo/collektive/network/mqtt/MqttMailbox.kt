package it.unibo.collektive.network.mqtt

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import it.nicolasfarabegoli.mktt.MkttClient
import it.nicolasfarabegoli.mktt.MqttQoS
import it.unibo.collektive.network.AbstractSerializerMailbox
import it.unibo.collektive.network.bluetooth.BluetoothNeighborsDiscoverer
import it.unibo.collektive.networking.Message
import it.unibo.collektive.networking.SerializedMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * A mailbox that uses MQTT as the underlying transport.
 */
class MqttMailbox private constructor(
    private val deviceId: Uuid,
    host: String,
    port: Int,
    private val serializer: SerialFormat,
    private val retentionTime: Duration,
    private val dispatcher: CoroutineDispatcher,
    context: Context,
) : AbstractSerializerMailbox<Uuid>(deviceId, serializer, retentionTime) {
    private val internalScope = CoroutineScope(dispatcher)
    private val neighborsDiscoverer = BluetoothNeighborsDiscoverer(deviceId.toString(), context)
    private val mqttClient = MkttClient(dispatcher) {
        brokerUrl = host
        this.port = port
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_SCAN])
    private suspend fun initializeMqttClient() {
        Log.i("MqttMailbox", "Connecting to the broker...")
        mqttClient.connect()
        Log.i("MqttMailbox", "Connected to the broker")
        neighborsDiscoverer.startAdvertisingDeviceId()
        internalScope.launch {
            neighborsDiscoverer.neighborsIds.collect {
                addNeighbor(it)
            }
        }
        internalScope.launch { cleanHeartbeatPulse() }
        internalScope.launch(dispatcher) { receiveNeighborMessages() }
    }

    private suspend fun cleanHeartbeatPulse() {
        cleanupNeighbors(retentionTime)
        delay(retentionTime)
        cleanHeartbeatPulse()
    }

    private suspend fun receiveNeighborMessages() {
        mqttClient.subscribe(deviceTopic(deviceId)).collect {
            Log.i("MqttMailbox", "Received message from ${it.topic}")
            try {
                val deserialized = serializer.decodeSerialMessage<Uuid>(it.payload)
                Log.d("MqttMailbox", "Received message from ${deserialized.senderId}")
                deliverableReceived(deserialized)
            } catch (exception: SerializationException) {
                Log.e("MqttMailbox", "Failed to deserialize message from ${it.topic}: ${exception.message}")
            }
        }
    }

    override suspend fun close() {
        internalScope.cancel()
        mqttClient.disconnect()
        Log.i("MqttMailbox", "Disconnected from the broker")
    }

    override fun onDeliverableReceived(receiverId: Uuid, message: Message<Uuid, Any?>) {
        require(message is SerializedMessage<Uuid>)
        Log.i("MqttMailbox", "Sending message to $receiverId from $deviceId")
        internalScope.launch(dispatcher) {
            mqttClient.publish(
                topic = deviceTopic(receiverId),
                qos = MqttQoS.AtLeastOnce,
                message = serializer.encodeSerialMessage(message),
            )
        }
    }

    /**
     * Companion object to create a new instance of [MqttMailbox].
     */
    companion object {
        /**
         * Create a new instance of [MqttMailbox].
         */
        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_SCAN])
        suspend operator fun invoke(
            deviceId: Uuid,
            host: String,
            port: Int = 1883,
            serializer: SerialFormat = Json,
            retentionTime: Duration = 5.seconds,
            dispatcher: CoroutineDispatcher,
            context: Context,
        ): MqttMailbox = coroutineScope {
            MqttMailbox(deviceId, host, port, serializer, retentionTime, dispatcher, context).apply {
                initializeMqttClient()
            }
        }

        private const val APP_NAMESPACE = "CollektiveExampleAndroid"
        private fun deviceTopic(deviceId: Uuid) = "$APP_NAMESPACE/device/$deviceId"
    }
}
