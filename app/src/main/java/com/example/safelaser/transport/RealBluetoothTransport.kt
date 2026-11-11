package com.example.safelaser.transport

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.safelaser.core.DeviceIdentity
import com.example.safelaser.core.DeviceParameter
import com.example.safelaser.core.DeviceStatus
import com.example.safelaser.core.KnownState
import com.example.safelaser.core.OutputState
import com.example.safelaser.core.ParameterState
import com.example.safelaser.protocol.DeviceCommand
import com.example.safelaser.protocol.DeviceResponse
import com.example.safelaser.protocol.LegacyLaserProtocol
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withTimeoutOrNull

/** Hardware-validation transport. It accepts only DeviceCommand, never arbitrary text. */
class RealBluetoothTransport(private val context: Context) : BluetoothTransport {
    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val mutableEvents = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 128)
    override val events: Flow<TransportEvent> = mutableEvents
    private val notificationLines = Channel<String>(Channel.UNLIMITED)
    private var gatt: BluetoothGatt? = null
    private var communication: BluetoothGattCharacteristic? = null
    private var servicesReady = CompletableDeferred<Boolean>()
    private var writeReady = CompletableDeferred<Boolean>()
    private var receiveBuffer = StringBuilder()
    private var lastIdentity: DeviceIdentity? = null
    private var output = OutputState.UNKNOWN
    private val values = mutableMapOf<String, Double>()
    private var frequencyMaximum = 5_000.0
    private var dutyMinimum = 1.0
    private var lastResponseMillis: Long? = null

    @SuppressLint("MissingPermission")
    override suspend fun connect() {
        requirePermissions()
        val scanner = adapter?.bluetoothLeScanner ?: error("Bluetooth scanning is unavailable.")
        val found = linkedMapOf<String, DiscoveredLaser>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: result.scanRecord?.deviceName ?: return
                if (name.contains("BWT", ignoreCase = true)) {
                    found[result.device.address] = DiscoveredLaser(name, result.device.address, result.rssi)
                }
            }
        }
        scanner.startScan(callback)
        delay(SCAN_MILLIS)
        scanner.stopScan(callback)
        val devices = found.values.sortedByDescending { it.rssi }
        if (devices.size != 1) {
            mutableEvents.emit(TransportEvent.Candidates(devices))
            if (devices.isEmpty()) error("No compatible BWT laser was found.")
            return
        }
        connect(devices.single().address)
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(address: String) {
        requirePermissions()
        disconnect()
        servicesReady = CompletableDeferred()
        val device = adapter?.getRemoteDevice(address) ?: error("Bluetooth is unavailable.")
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        if (withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) { servicesReady.await() } != true) {
            disconnect()
            error("The laser service or communication characteristic was not found.")
        }
        mutableEvents.emit(TransportEvent.Connected)
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() {
        communication = null
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    override suspend fun send(command: DeviceCommand): DeviceResponse {
        val lines = try { LegacyLaserProtocol.requestLines(command) } catch (error: Exception) {
            return DeviceResponse.Rejected(error.message ?: "The command is not allowlisted.")
        }
        if (command == DeviceCommand.Arm) return DeviceResponse.Acknowledged(command, status())
        if (lines.isEmpty()) return DeviceResponse.Rejected("The command has no hardware encoding.")
        val received = mutableListOf<String>()
        for (line in lines) {
            if (!writeLine(line)) return DeviceResponse.MissingAcknowledgment
            val expected = LegacyLaserProtocol.expectedResponse(line) ?: continue
            val response = awaitMatching(expected) ?: return DeviceResponse.MissingAcknowledgment
            received += response
            updateFrom(response)
        }
        if (command is DeviceCommand.ApplyParameters && !readBackMatches(command.values)) {
            return DeviceResponse.Rejected("The parameter read-back did not match the requested value.")
        }
        if (command == DeviceCommand.Identify && lastIdentity?.verified != true) return DeviceResponse.Malformed
        return DeviceResponse.Acknowledged(command, status(), lines, received)
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeLine(line: String): Boolean {
        val localGatt = gatt ?: return false
        val characteristic = communication ?: return false
        for (segment in LegacyLaserProtocol.segments(line)) {
            writeReady = CompletableDeferred()
            mutableEvents.emit(TransportEvent.RawTraffic("TX", line, segment.toHex()))
            val started = if (Build.VERSION.SDK_INT >= 33) {
                localGatt.writeCharacteristic(characteristic, segment, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == 0
            } else {
                @Suppress("DEPRECATION")
                run { characteristic.value = segment; localGatt.writeCharacteristic(characteristic) }
            }
            if (!started || withTimeoutOrNull(WRITE_TIMEOUT_MILLIS) { writeReady.await() } != true) return false
        }
        return true
    }

    private suspend fun awaitMatching(prefix: String): String? = withTimeoutOrNull(RESPONSE_TIMEOUT_MILLIS) {
        while (true) {
            val line = notificationLines.receive()
            if (line == "BWTLASER" || line.startsWith(prefix)) return@withTimeoutOrNull line
        }
        @Suppress("UNREACHABLE_CODE") null
    }

    private fun updateFrom(line: String) {
        if (line.contains("BWTLASER")) {
            lastIdentity = DeviceIdentity("BWT LASER", "UNKNOWN", "UNKNOWN", "LEGACY-ASCII", true)
            return
        }
        val parts = line.trim().split(Regex("[ =:]+"), limit = 2)
        if (parts.size < 2) return
        val value = parts[1].trim().toDoubleOrNull()
        when (parts[0]) {
            "REN" -> output = when (parts[1].trim()) { "1" -> OutputState.ON; "0" -> OutputState.OFF; else -> OutputState.UNKNOWN }
            "RCPR" -> value?.let { values["output_percent"] = it }
            "RCMF" -> value?.let { values["pwm_frequency_hz"] = it }
            "RCDC" -> value?.let { values["duty_cycle_percent"] = it }
            "RCMFH" -> value?.let { frequencyMaximum = it }
            "RCDCL" -> value?.let { dutyMinimum = it }
        }
    }

    private fun readBackMatches(requested: Map<String, Double>): Boolean = requested.all { (key, value) ->
        key == "waveform_slot" || values[key] == value
    }

    fun status(nowMillis: Long = android.os.SystemClock.elapsedRealtime()): DeviceStatus = DeviceStatus(
        identity = lastIdentity,
        output = output,
        interlockClosed = KnownState.UNKNOWN,
        coolingAvailable = KnownState.UNKNOWN,
        alarms = emptyList(),
        lastUpdateMillis = lastResponseMillis,
        parameters = listOf(
            parameter("output_percent", "Laser power", "%", 0.0, 100.0),
            parameter("pwm_frequency_hz", "PWM frequency", "Hz", 1.0, frequencyMaximum),
            parameter("duty_cycle_percent", "Duty cycle", "%", dutyMinimum, 99.0),
            parameter("waveform_slot", "Waveform slot", "slot", 0.0, 8.0),
        ),
    )

    private fun parameter(id: String, name: String, unit: String, min: Double, max: Double) = DeviceParameter(
        id, name, unit, min, max, values[id], null, ParameterState.APPLIED,
    )

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> gatt.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> mutableEvents.tryEmit(TransportEvent.Disconnected)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service: BluetoothGattService? = gatt.services.firstOrNull { it.uuid.toString().startsWith("0000fff0") }
            val characteristic = service?.characteristics?.firstOrNull { it.uuid.toString().startsWith("0000fff6") }
            mutableEvents.tryEmit(TransportEvent.RawTraffic("META", "service=${service?.uuid};characteristic=${characteristic?.uuid}", ""))
            val usable = characteristic != null && characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 &&
                characteristic.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            communication = characteristic.takeIf { usable }
            if (usable) enableNotifications(gatt, characteristic)
            else servicesReady.complete(false)
        }

        @SuppressLint("MissingPermission")
        private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (!gatt.setCharacteristicNotification(characteristic, true)) { servicesReady.complete(false); return }
            val descriptor = characteristic.getDescriptor(UUID.fromString(CCCD_UUID)) ?: run { servicesReady.complete(false); return }
            if (Build.VERSION.SDK_INT >= 33) gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            else @Suppress("DEPRECATION") run { descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; gatt.writeDescriptor(descriptor) }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (!servicesReady.isCompleted) servicesReady.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            writeReady.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        @Deprecated("Used below Android 13")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION") consume(characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) = consume(value)
    }

    private fun consume(bytes: ByteArray) {
        lastResponseMillis = android.os.SystemClock.elapsedRealtime()
        mutableEvents.tryEmit(TransportEvent.RawTraffic("RX", bytes.toString(Charsets.US_ASCII), bytes.toHex()))
        receiveBuffer.append(bytes.toString(Charsets.US_ASCII))
        while (true) {
            val end = receiveBuffer.indexOf("\r\n")
            if (end < 0) break
            val line = receiveBuffer.substring(0, end).trim()
            receiveBuffer.delete(0, end + 2)
            if (line.isNotEmpty()) notificationLines.trySend(line)
        }
    }

    private fun status() = status(android.os.SystemClock.elapsedRealtime())

    private fun requirePermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            check(ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)
            check(ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
        } else {
            check(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02X".format(it) }

    private companion object {
        const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"
        const val SCAN_MILLIS = 5_000L
        const val CONNECT_TIMEOUT_MILLIS = 12_000L
        const val WRITE_TIMEOUT_MILLIS = 5_000L
        const val RESPONSE_TIMEOUT_MILLIS = 5_000L
    }
}
