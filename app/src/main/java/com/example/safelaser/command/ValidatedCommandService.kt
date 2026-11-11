package com.example.safelaser.command

import com.example.safelaser.audit.AuditEntry
import com.example.safelaser.audit.AuditLogger
import com.example.safelaser.core.Clock
import com.example.safelaser.core.DeviceStatus
import com.example.safelaser.core.KnownState
import com.example.safelaser.core.SafetyEvent
import com.example.safelaser.core.SafetyState
import com.example.safelaser.core.SafetyStateMachine
import com.example.safelaser.protocol.DeviceCommand
import com.example.safelaser.protocol.DeviceResponse
import com.example.safelaser.protocol.SafetyClass
import com.example.safelaser.transport.BluetoothTransport
import java.util.UUID

data class ValidationContext(
    val sessionId: String,
    val identityVerified: Boolean,
    val protocolCompatible: Boolean,
    val status: DeviceStatus?,
    val statusMaxAgeMillis: Long,
    val allowedCommands: Set<Class<out DeviceCommand>>,
    val allowLiveHardwareTuning: Boolean = false,
)

sealed interface CommandResult {
    data class Completed(val response: DeviceResponse.Acknowledged) : CommandResult
    data class Blocked(val reasons: List<String>) : CommandResult
    data class Failed(val response: DeviceResponse) : CommandResult
}

class ValidatedCommandService(
    private val transport: BluetoothTransport,
    private val stateMachine: SafetyStateMachine,
    private val clock: Clock,
    private val auditLogger: AuditLogger,
) {
    private var sessionId = UUID.randomUUID().toString()

    fun newSession(): String {
        sessionId = UUID.randomUUID().toString()
        return sessionId
    }

    suspend fun send(command: DeviceCommand, action: String, context: ValidationContext): CommandResult {
        val reasons = validate(command, context)
        if (reasons.isNotEmpty()) return CommandResult.Blocked(reasons)
        if (command is DeviceCommand.RequestStop) stateMachine.dispatch(SafetyEvent.STOP_REQUESTED)

        val before = stateMachine.state
        val response = transport.send(command)
        updateState(command, response, before)
        auditLogger.log(
            AuditEntry(
                timestampMillis = clock.nowMillis(),
                sessionId = sessionId,
                applicationState = before,
                operatorAction = action,
                command = command,
                structuredPayload = when (command) {
                    is DeviceCommand.ApplyParameters -> command.values.mapValues { it.value.toString() }
                    is DeviceCommand.ApplySimulatedLiveParameters -> command.values.mapValues { it.value.toString() }
                    else -> emptyMap()
                },
                characteristicUuid = "SIMULATOR",
                writeResult = if (response is DeviceResponse.Acknowledged) "WRITE ACCEPTED" else "WRITE NOT CONFIRMED",
                acknowledgmentResult = response::class.simpleName ?: "UNKNOWN",
                deviceStateBefore = before.toString(),
                deviceStateAfter = stateMachine.state.toString(),
                transmittedLines = (response as? DeviceResponse.Acknowledged)?.transmittedLines.orEmpty(),
                receivedLines = (response as? DeviceResponse.Acknowledged)?.receivedLines.orEmpty(),
            ),
        )
        return when (response) {
            is DeviceResponse.Acknowledged -> CommandResult.Completed(response)
            else -> CommandResult.Failed(response)
        }
    }

    private fun validate(command: DeviceCommand, context: ValidationContext): List<String> = buildList {
        if (context.sessionId != sessionId) add("The connection session is not current.")
        if (!context.identityVerified && command != DeviceCommand.Identify) add("The device identity is not verified.")
        if (!context.protocolCompatible && command != DeviceCommand.Identify) add("The device protocol is not compatible.")
        if (context.allowedCommands.none { it.isInstance(command) }) add("The command is not allowed.")
        val status = context.status
        if (status == null) add("The device status is unknown.")
        if (status?.lastUpdateMillis == null || clock.nowMillis() - status.lastUpdateMillis > context.statusMaxAgeMillis) {
            add("The device status is stale.")
        }
        if (status?.interlockClosed != KnownState.YES && command.safetyClass in setOf(SafetyClass.CONFIGURATION, SafetyClass.EMISSION)) add("The interlock is not closed.")
        if (status?.alarms?.isNotEmpty() == true && command.safetyClass in setOf(SafetyClass.CONFIGURATION, SafetyClass.EMISSION)) add("The device has an active alarm.")
        val parameterValues = when (command) {
            is DeviceCommand.ApplyParameters -> command.values
            is DeviceCommand.ApplySimulatedLiveParameters -> command.values
            else -> emptyMap()
        }
        if (parameterValues.isNotEmpty()) {
            parameterValues.forEach { (name, value) ->
                val parameter = status?.parameters?.firstOrNull { it.id == name }
                if (parameter == null || value !in parameter.minimum..parameter.maximum) add("The parameter $name is outside its proven range.")
            }
        }
        if (!stateAllows(command, stateMachine.state, context.allowLiveHardwareTuning)) add("The application state does not allow the command.")
    }

    private fun stateAllows(command: DeviceCommand, state: SafetyState, allowLiveHardwareTuning: Boolean): Boolean = when (command) {
        DeviceCommand.Identify -> state is SafetyState.ConnectedUnverified
        DeviceCommand.ReadStatus -> state !is SafetyState.Disconnected && state !is SafetyState.Connecting
        is DeviceCommand.ApplyParameters -> state is SafetyState.ConfigurationPending ||
            (allowLiveHardwareTuning && state is SafetyState.Emitting)
        is DeviceCommand.ApplySimulatedLiveParameters -> state is SafetyState.Emitting
        DeviceCommand.Arm -> state is SafetyState.ReadyToArm
        DeviceCommand.RequestEmission -> state is SafetyState.Armed
        DeviceCommand.RequestStop -> state !is SafetyState.Disconnected && state !is SafetyState.Connecting
    }

    private fun updateState(command: DeviceCommand, response: DeviceResponse, before: SafetyState) {
        if (response is DeviceResponse.Malformed) stateMachine.dispatch(SafetyEvent.MALFORMED_RESPONSE)
        if (response is DeviceResponse.MissingAcknowledgment) {
            stateMachine.dispatch(SafetyEvent.COMMUNICATION_TIMEOUT)
            return
        }
        if (response !is DeviceResponse.Acknowledged) return
        when (command) {
            DeviceCommand.Arm -> stateMachine.dispatch(SafetyEvent.ARM_HELD)
            DeviceCommand.RequestEmission -> stateMachine.dispatch(SafetyEvent.EMISSION_CONFIRMED)
            DeviceCommand.RequestStop -> stateMachine.dispatch(SafetyEvent.STOP_CONFIRMED)
            is DeviceCommand.ApplyParameters -> if (before !is SafetyState.Emitting) stateMachine.dispatch(SafetyEvent.PARAMETERS_APPLIED) else Unit
            is DeviceCommand.ApplySimulatedLiveParameters -> Unit
            else -> Unit
        }
    }
}
