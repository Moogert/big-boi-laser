package com.example.safelaser.transport

import com.example.safelaser.protocol.DeviceCommand
import com.example.safelaser.protocol.DeviceResponse
import kotlinx.coroutines.flow.Flow

sealed interface TransportEvent {
    data object Connected : TransportEvent
    data object Disconnected : TransportEvent
    data class Response(val response: DeviceResponse) : TransportEvent
    data object StaleStatus : TransportEvent
    data object DeviceRestarted : TransportEvent
    data class Candidates(val devices: List<DiscoveredLaser>) : TransportEvent
    data class RawTraffic(val direction: String, val text: String, val hex: String) : TransportEvent
}

data class DiscoveredLaser(val name: String, val address: String, val rssi: Int)

interface BluetoothTransport {
    val events: Flow<TransportEvent>
    suspend fun connect()
    suspend fun connect(address: String) = connect()
    suspend fun disconnect()
    suspend fun send(command: DeviceCommand): DeviceResponse
}
