package com.example.safelaser

import android.content.Context

import com.example.safelaser.audit.FileAuditLogger
import com.example.safelaser.command.CommandResult
import com.example.safelaser.command.ValidatedCommandService
import com.example.safelaser.command.ValidationContext
import com.example.safelaser.core.DeviceStatus
import com.example.safelaser.core.OutputState
import com.example.safelaser.core.ParameterDraft
import com.example.safelaser.core.SafetyEvent
import com.example.safelaser.core.SafetyState
import com.example.safelaser.core.SafetyStateMachine
import com.example.safelaser.core.SystemClock
import com.example.safelaser.protocol.DeviceCommand
import com.example.safelaser.simulator.SimulatedBluetoothTransport
import com.example.safelaser.transport.BluetoothTransport
import com.example.safelaser.transport.DiscoveredLaser
import com.example.safelaser.transport.RealBluetoothTransport
import com.example.safelaser.transport.TransportEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OperatorUiState(
    val safetyState: SafetyState = SafetyState.Disconnected,
    val status: DeviceStatus? = null,
    val sessionId: String = "",
    val message: String? = null,
    val showEmissionDialog: Boolean = false,
    val pendingParameters: ParameterDraft = ParameterDraft(),
    val appliedWaveformSlot: Int = 0,
    val auditLines: List<String> = emptyList(),
    val experimentalLiveTuning: Boolean = false,
    val discoveredLasers: List<DiscoveredLaser> = emptyList(),
    val buildLabel: String = if (BuildConfig.TRANSPORT_MODE == "SIMULATOR") "SIMULATOR BUILD" else "HARDWARE VALIDATION",
)

class LaserController(context: Context) {
    private val transport: BluetoothTransport = if (BuildConfig.TRANSPORT_MODE == "SIMULATOR") {
        SimulatedBluetoothTransport()
    } else {
        RealBluetoothTransport(context.applicationContext)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val stateMachine = SafetyStateMachine(SystemClock)
    private val auditLogger = FileAuditLogger(context.applicationContext)
    private val commands = ValidatedCommandService(transport, stateMachine, SystemClock, auditLogger)
    private val mutableState = MutableStateFlow(OperatorUiState())
    val state: StateFlow<OperatorUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            transport.events.collectLatest { event ->
                when (event) {
                    is TransportEvent.Candidates -> mutableState.value = mutableState.value.copy(discoveredLasers = event.devices)
                    is TransportEvent.RawTraffic -> auditLogger.logTraffic(mutableState.value.sessionId, event.direction, event.text, event.hex)
                    TransportEvent.Disconnected -> {
                        stateMachine.dispatch(SafetyEvent.CONNECTION_LOST)
                        mutableState.value = mutableState.value.copy(experimentalLiveTuning = false)
                        publish("BLUETOOTH CONNECTION LOST. LASER OUTPUT STATE IS UNKNOWN.")
                    }
                    else -> Unit
                }
            }
        }
    }

    fun setExperimentalLiveTuning(enabled: Boolean) {
        val allowed = BuildConfig.TRANSPORT_MODE == "SIMULATOR" || BuildConfig.TRANSPORT_MODE == "HARDWARE_VALIDATION"
        mutableState.value = mutableState.value.copy(
            experimentalLiveTuning = enabled && allowed,
            message = if (enabled && !allowed) "SIMULATOR LIVE TUNING IS NOT AVAILABLE" else mutableState.value.message,
        )
    }

    suspend fun connectSimulator() {
        stateMachine.dispatch(SafetyEvent.CONNECT_REQUESTED)
        publish()
        transport.connect()
        completeConnection()
    }

    suspend fun connectLaser(address: String) {
        stateMachine.dispatch(SafetyEvent.CONNECT_REQUESTED)
        publish()
        transport.connect(address)
        completeConnection()
    }

    private suspend fun completeConnection() {
        stateMachine.dispatch(SafetyEvent.TRANSPORT_CONNECTED)
        val session = commands.newSession()
        auditLogger.startSession(session)
        mutableState.value = mutableState.value.copy(sessionId = session, experimentalLiveTuning = false)
        val identityResult = if (BuildConfig.TRANSPORT_MODE == "SIMULATOR") null else
            commands.send(DeviceCommand.Identify, "VERIFY DEVICE IDENTITY", bootstrapContext())
        val statusResult = if (BuildConfig.TRANSPORT_MODE == "SIMULATOR") null else
            commands.send(DeviceCommand.ReadStatus, "READ DEVICE STATUS", bootstrapContext(identityVerified = true))
        val status = currentTransportStatus()
        if (BuildConfig.TRANSPORT_MODE != "SIMULATOR" &&
            (identityResult !is CommandResult.Completed || statusResult !is CommandResult.Completed || status.identity?.verified != true)
        ) {
            stateMachine.dispatch(SafetyEvent.MALFORMED_RESPONSE)
            publish("DEVICE IDENTITY OR STATUS IS NOT VERIFIED")
            return
        }
        stateMachine.dispatch(SafetyEvent.IDENTITY_AND_PROTOCOL_VERIFIED)
        stateMachine.dispatch(SafetyEvent.STATUS_CONFIRMED_SAFE)
        mutableState.value = OperatorUiState(
            safetyState = stateMachine.state,
            status = status,
            sessionId = session,
            auditLines = auditLogger.lines(),
        )
    }

    fun editParameter(id: String, value: Double) {
        val values = mutableState.value.pendingParameters.values + (id to value)
        mutableState.value = mutableState.value.copy(
            pendingParameters = mutableState.value.pendingParameters.copy(values = values),
            message = "PARAMETERS PENDING",
        )
        if (stateMachine.state is SafetyState.Safe || stateMachine.state is SafetyState.ReadyToArm || stateMachine.state is SafetyState.Armed) {
            stateMachine.dispatch(SafetyEvent.PARAMETER_EDITED)
            publish(message = "PARAMETERS PENDING")
        }
    }

    fun selectWaveformSlot(slot: Int) {
        val selected = slot.coerceIn(0, 8)
        mutableState.value = mutableState.value.copy(
            pendingParameters = mutableState.value.pendingParameters.copy(
                waveformSlot = selected,
                values = mutableState.value.pendingParameters.values + ("waveform_slot" to selected.toDouble()),
            ),
            message = "PARAMETERS PENDING",
        )
        if (stateMachine.state is SafetyState.Safe || stateMachine.state is SafetyState.ReadyToArm || stateMachine.state is SafetyState.Armed) {
            stateMachine.dispatch(SafetyEvent.PARAMETER_EDITED)
            publish(message = "PARAMETERS PENDING")
        }
    }

    suspend fun applyPendingParameters() {
        val liveApply = stateMachine.state is SafetyState.Emitting
        if (stateMachine.state is SafetyState.EmissionRequested || stateMachine.state is SafetyState.Stopping) {
            publish(message = "The application state does not allow parameter changes.")
            return
        }
        if (stateMachine.state is SafetyState.Safe || stateMachine.state is SafetyState.ReadyToArm || stateMachine.state is SafetyState.Armed) {
            stateMachine.dispatch(SafetyEvent.PARAMETER_EDITED)
        }
        val pending = mutableState.value.pendingParameters
        if (pending.values.isEmpty()) {
            publish(message = "No parameter changes are pending.")
            return
        }
        if (liveApply && BuildConfig.TRANSPORT_MODE == "HARDWARE_VALIDATION") {
            val unsupported = pending.values.keys - setOf("output_percent", "pwm_frequency_hz", "duty_cycle_percent")
            if (unsupported.isNotEmpty()) {
                publish(message = "Only power, frequency, and duty cycle can change during active output.")
                return
            }
        }
        val command = if (liveApply && BuildConfig.TRANSPORT_MODE == "SIMULATOR")
            DeviceCommand.ApplySimulatedLiveParameters(pending.values) else DeviceCommand.ApplyParameters(pending.values)
        val action = if (liveApply) "APPLY PARAMETERS DURING ACTIVE OUTPUT" else "APPLY PARAMETERS"
        val result = send(command, action)
        if (result is CommandResult.Completed) {
            if (!liveApply) stateMachine.dispatch(SafetyEvent.STATUS_CONFIRMED_SAFE)
            mutableState.value = mutableState.value.copy(
                pendingParameters = ParameterDraft(waveformSlot = pending.waveformSlot),
                appliedWaveformSlot = pending.waveformSlot,
            )
            publish(message = if (liveApply) "SIMULATED LIVE PARAMETERS APPLIED" else "PARAMETERS APPLIED")
        } else {
            publish(message = result.message())
        }
    }

    suspend fun armAfterHold() {
        val result = send(DeviceCommand.Arm, "ARM LASER HOLD")
        val oldMessage = mutableState.value.message
        val nextMessage = if (result is CommandResult.Completed && oldMessage == "STOP CONFIRMED") null
            else if (result is CommandResult.Completed) oldMessage else result.message()
        publish(message = nextMessage)
        if (result is CommandResult.Completed) {
            mutableState.value = mutableState.value.copy(showEmissionDialog = true, message = nextMessage)
        }
    }

    fun cancelEmissionDialog() {
        mutableState.value = mutableState.value.copy(showEmissionDialog = false)
    }

    suspend fun requestEmission() {
        mutableState.value = mutableState.value.copy(showEmissionDialog = false)
        val result = send(DeviceCommand.RequestEmission, "REQUEST LASER EMISSION")
        publish(message = result.message())
    }

    suspend fun requestStop() {
        val result = send(DeviceCommand.RequestStop, "REQUEST LASER STOP")
        val message = when (result) {
            is CommandResult.Completed -> {
                if (result.response.status?.output == OutputState.OFF) {
                    stateMachine.dispatch(SafetyEvent.STATUS_CONFIRMED_SAFE)
                    "STOP CONFIRMED"
                } else STOP_NOT_CONFIRMED
            }
            else -> STOP_NOT_CONFIRMED
        }
        publish(message = message)
    }

    fun onBackground() {
        mutableState.value = mutableState.value.copy(experimentalLiveTuning = false)
        stateMachine.dispatch(SafetyEvent.APP_BACKGROUNDED)
        publish()
    }

    fun addObservation(note: String) {
        if (note.isNotBlank()) auditLogger.addObservation(mutableState.value.sessionId, note)
        publish("OBSERVATION RECORDED")
    }

    fun exportAudit() = auditLogger.exportSession(
        "version=${BuildConfig.VERSION_NAME}\nflavor=${BuildConfig.TRANSPORT_MODE}\nsession=${mutableState.value.sessionId}\n" +
            "device=${mutableState.value.status?.identity ?: "UNKNOWN"}\n",
    )

    private suspend fun send(command: DeviceCommand, action: String): CommandResult {
        val ui = mutableState.value
        return commands.send(
            command,
            action,
            ValidationContext(
                sessionId = ui.sessionId,
                identityVerified = ui.status?.identity?.verified == true,
                protocolCompatible = ui.status?.identity?.protocolVersion == "SIM-1",
                status = currentTransportStatus(),
                statusMaxAgeMillis = 5_000,
                allowedCommands = setOf(
                    DeviceCommand.Identify::class.java,
                    DeviceCommand.ReadStatus::class.java,
                    DeviceCommand.ApplyParameters::class.java,
                    DeviceCommand.ApplySimulatedLiveParameters::class.java,
                    DeviceCommand.Arm::class.java,
                    DeviceCommand.RequestEmission::class.java,
                    DeviceCommand.RequestStop::class.java,
                ),
                allowLiveHardwareTuning = true,
            ),
        )
    }

    private fun bootstrapContext(identityVerified: Boolean = false) = ValidationContext(
        sessionId = mutableState.value.sessionId,
        identityVerified = identityVerified || BuildConfig.TRANSPORT_MODE == "SIMULATOR",
        protocolCompatible = true,
        status = currentTransportStatus().copy(lastUpdateMillis = SystemClock.nowMillis()),
        statusMaxAgeMillis = 5_000,
        allowedCommands = setOf(DeviceCommand.Identify::class.java, DeviceCommand.ReadStatus::class.java),
    )

    private fun currentTransportStatus() = when (val active = transport) {
        is SimulatedBluetoothTransport -> active.status(SystemClock.nowMillis())
        is RealBluetoothTransport -> active.status(SystemClock.nowMillis())
        else -> error("Unsupported transport")
    }

    private fun publish(message: String? = mutableState.value.message) {
        mutableState.value = mutableState.value.copy(
            safetyState = stateMachine.state,
            status = currentTransportStatus(),
            message = message,
            auditLines = auditLogger.lines(),
        )
    }

    private fun CommandResult.message(): String = when (this) {
        is CommandResult.Completed -> "COMMAND ACKNOWLEDGED"
        is CommandResult.Blocked -> reasons.joinToString(" ")
        is CommandResult.Failed -> "The simulated device did not acknowledge the command."
    }

    companion object {
        const val STOP_NOT_CONFIRMED =
            "STOP NOT CONFIRMED. LASER OUTPUT STATE IS UNKNOWN."
    }
}
