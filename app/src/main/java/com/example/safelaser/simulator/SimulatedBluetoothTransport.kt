package com.example.safelaser.simulator

import com.example.safelaser.core.DeviceIdentity
import com.example.safelaser.core.DeviceParameter
import com.example.safelaser.core.DeviceStatus
import com.example.safelaser.core.KnownState
import com.example.safelaser.core.OutputState
import com.example.safelaser.core.ParameterState
import com.example.safelaser.protocol.DeviceCommand
import com.example.safelaser.protocol.DeviceResponse
import com.example.safelaser.transport.BluetoothTransport
import com.example.safelaser.transport.TransportEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class SimulatedBluetoothTransport(
    var scenario: SimulatorScenario = SimulatorScenario.NORMAL,
) : BluetoothTransport {
    private val mutableEvents = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 32)
    override val events: Flow<TransportEvent> = mutableEvents

    private var connected = false
    private var output = OutputState.OFF
    private var appliedParameters = mapOf(
        "output_percent" to 20.0,
        "pwm_frequency_hz" to 1000.0,
        "duty_cycle_percent" to 20.0,
        "swing_frequency_hz" to 50.0,
        "swing_length_mm" to 2.0,
        "pulse_on_ms" to 100.0,
        "pulse_off_ms" to 100.0,
        "waveform_slot" to 0.0,
    )

    override suspend fun connect() {
        connected = true
        mutableEvents.emit(TransportEvent.Connected)
    }

    override suspend fun disconnect() {
        connected = false
        mutableEvents.emit(TransportEvent.Disconnected)
    }

    override suspend fun send(command: DeviceCommand): DeviceResponse {
        if (!connected) return DeviceResponse.Rejected("BLUETOOTH DISCONNECTED")
        if (scenario == SimulatorScenario.DELAYED_ACKNOWLEDGMENT) delay(2_000)
        if (scenario == SimulatorScenario.MISSING_ACKNOWLEDGMENT) return DeviceResponse.MissingAcknowledgment
        if (scenario == SimulatorScenario.MALFORMED_PACKET) return DeviceResponse.Malformed
        if (scenario == SimulatorScenario.STOP_ACKNOWLEDGMENT_FAILURE && command is DeviceCommand.RequestStop) {
            return DeviceResponse.MissingAcknowledgment
        }
        if (scenario == SimulatorScenario.REJECTED_PARAMETER &&
            (command is DeviceCommand.ApplyParameters || command is DeviceCommand.ApplySimulatedLiveParameters)
        ) {
            return DeviceResponse.Rejected("The simulated device rejected a parameter.")
        }

        when (command) {
            is DeviceCommand.ApplyParameters -> appliedParameters = command.values
            is DeviceCommand.ApplySimulatedLiveParameters -> appliedParameters = command.values
            DeviceCommand.RequestEmission -> {
                output = OutputState.ON
                if (scenario == SimulatorScenario.CONNECTION_LOSS_DURING_EMISSION) {
                    connected = false
                    mutableEvents.emit(TransportEvent.Disconnected)
                    return DeviceResponse.MissingAcknowledgment
                }
            }
            DeviceCommand.RequestStop -> output = OutputState.OFF
            else -> Unit
        }

        if (scenario == SimulatorScenario.DEVICE_RESTART) mutableEvents.emit(TransportEvent.DeviceRestarted)
        if (scenario == SimulatorScenario.STALE_STATUS) mutableEvents.emit(TransportEvent.StaleStatus)
        val response = DeviceResponse.Acknowledged(command, status())
        mutableEvents.emit(TransportEvent.Response(response))
        if (scenario == SimulatorScenario.DUPLICATE_NOTIFICATION) mutableEvents.emit(TransportEvent.Response(response))
        return response
    }

    fun status(nowMillis: Long = 0L): DeviceStatus {
        val interlock = if (scenario == SimulatorScenario.OPEN_INTERLOCK) KnownState.NO else KnownState.YES
        val cooling = if (scenario == SimulatorScenario.COOLING_FAULT) KnownState.NO else KnownState.YES
        val alarms = buildList {
            if (scenario == SimulatorScenario.COOLING_FAULT) add("COOLING SYSTEM FAULT")
            if (scenario == SimulatorScenario.HIGH_TEMPERATURE_FAULT) add("HIGH TEMPERATURE FAULT")
        }
        return DeviceStatus(
            identity = DeviceIdentity("SIMULATED LASER", "SIM-0001", "SIM-1", "SIM-1", true),
            output = output,
            interlockClosed = interlock,
            coolingAvailable = cooling,
            alarms = alarms,
            lastUpdateMillis = nowMillis,
            parameters = appliedParameters.map { (id, value) -> parameter(id, value) },
        )
    }

    private fun parameter(id: String, value: Double): DeviceParameter {
        val definition = when (id) {
            "output_percent" -> ParameterDefinition("Laser power", "%", 0.0, 100.0)
            "pwm_frequency_hz" -> ParameterDefinition("PWM frequency", "Hz", 1.0, 5_000.0)
            "duty_cycle_percent" -> ParameterDefinition("Duty cycle", "%", 1.0, 99.0)
            "swing_frequency_hz" -> ParameterDefinition("Swing frequency", "Hz", 1.0, 1_000.0)
            "swing_length_mm" -> ParameterDefinition("Swing length", "mm", 0.0, 5.0)
            "pulse_on_ms" -> ParameterDefinition("Pulse on time", "ms", 1.0, 60_000.0)
            "pulse_off_ms" -> ParameterDefinition("Pulse off time", "ms", 1.0, 60_000.0)
            "waveform_slot" -> ParameterDefinition("Waveform slot", "slot", 0.0, 8.0)
            else -> ParameterDefinition(id, "UNKNOWN", value, value)
        }
        return DeviceParameter(id, definition.name, definition.unit, definition.minimum, definition.maximum, value, value, ParameterState.APPLIED)
    }

    private data class ParameterDefinition(
        val name: String,
        val unit: String,
        val minimum: Double,
        val maximum: Double,
    )
}
