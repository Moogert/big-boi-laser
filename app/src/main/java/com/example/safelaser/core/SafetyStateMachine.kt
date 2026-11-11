package com.example.safelaser.core

class SafetyStateMachine(
    private val clock: Clock,
    private val armDurationMillis: Long = 10_000L,
) {
    var state: SafetyState = SafetyState.Disconnected
        private set

    fun dispatch(event: SafetyEvent): TransitionResult {
        val previous = state
        val next = globalFault(event) ?: transition(previous, event)
        if (next != null) state = next
        return TransitionResult(previous, event, state, next != null)
    }

    private fun globalFault(event: SafetyEvent): SafetyState? = when (event) {
        SafetyEvent.CONNECTION_LOST -> SafetyState.CommunicationLost
        SafetyEvent.MALFORMED_RESPONSE -> SafetyState.Fault(FaultReason.MALFORMED_RESPONSE)
        SafetyEvent.COMMUNICATION_TIMEOUT -> SafetyState.Fault(FaultReason.TIMEOUT)
        SafetyEvent.STATUS_STALE -> SafetyState.Fault(FaultReason.STALE_STATUS)
        SafetyEvent.UNKNOWN_STATUS -> SafetyState.Fault(FaultReason.UNKNOWN_DEVICE_STATE)
        SafetyEvent.ALARM_STARTED -> SafetyState.Fault(FaultReason.ALARM)
        SafetyEvent.INTERLOCK_OPENED -> SafetyState.InterlockOpen
        else -> null
    }

    private fun transition(state: SafetyState, event: SafetyEvent): SafetyState? = when (state) {
        SafetyState.Disconnected -> when (event) {
            SafetyEvent.CONNECT_REQUESTED -> SafetyState.Connecting
            else -> null
        }
        SafetyState.Connecting -> when (event) {
            SafetyEvent.TRANSPORT_CONNECTED -> SafetyState.ConnectedUnverified
            else -> null
        }
        SafetyState.ConnectedUnverified -> when (event) {
            SafetyEvent.IDENTITY_AND_PROTOCOL_VERIFIED -> SafetyState.Safe
            else -> null
        }
        SafetyState.Safe -> when (event) {
            SafetyEvent.PARAMETER_EDITED -> SafetyState.ConfigurationPending
            SafetyEvent.STATUS_CONFIRMED_SAFE -> SafetyState.ReadyToArm
            else -> null
        }
        SafetyState.ConfigurationPending -> when (event) {
            SafetyEvent.PARAMETERS_APPLIED -> SafetyState.Safe
            else -> null
        }
        SafetyState.ReadyToArm -> when (event) {
            SafetyEvent.PARAMETER_EDITED -> SafetyState.ConfigurationPending
            SafetyEvent.ARM_HELD -> SafetyState.Armed(clock.nowMillis() + armDurationMillis)
            else -> null
        }
        is SafetyState.Armed -> when (event) {
            SafetyEvent.EMISSION_REQUESTED -> {
                if (clock.nowMillis() <= state.expiresAtMillis) SafetyState.EmissionRequested else SafetyState.Safe
            }
            SafetyEvent.PARAMETER_EDITED -> SafetyState.ConfigurationPending
            SafetyEvent.NAVIGATED,
            SafetyEvent.APP_BACKGROUNDED,
            SafetyEvent.ARM_TIMEOUT -> SafetyState.Safe
            else -> null
        }
        SafetyState.EmissionRequested -> when (event) {
            SafetyEvent.EMISSION_CONFIRMED -> SafetyState.Emitting
            SafetyEvent.STOP_REQUESTED -> SafetyState.Stopping
            else -> null
        }
        SafetyState.Emitting -> when (event) {
            SafetyEvent.STOP_REQUESTED -> SafetyState.Stopping
            else -> null
        }
        SafetyState.Stopping -> when (event) {
            SafetyEvent.STOP_CONFIRMED -> SafetyState.Safe
            SafetyEvent.COMMUNICATION_TIMEOUT -> SafetyState.Fault(FaultReason.STOP_NOT_CONFIRMED)
            else -> null
        }
        is SafetyState.Fault -> when (event) {
            SafetyEvent.RESET_FAULT -> SafetyState.ConnectedUnverified
            SafetyEvent.STOP_REQUESTED -> SafetyState.Stopping
            else -> null
        }
        SafetyState.InterlockOpen -> when (event) {
            SafetyEvent.INTERLOCK_CLOSED -> SafetyState.ConnectedUnverified
            SafetyEvent.STOP_REQUESTED -> SafetyState.Stopping
            else -> null
        }
        SafetyState.CommunicationLost -> when (event) {
            SafetyEvent.CONNECT_REQUESTED -> SafetyState.Connecting
            else -> null
        }
    }
}

fun interface Clock {
    fun nowMillis(): Long
}

object SystemClock : Clock {
    override fun nowMillis(): Long = android.os.SystemClock.elapsedRealtime()
}

