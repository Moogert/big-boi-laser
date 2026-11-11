package com.example.safelaser.core

sealed interface SafetyState {
    data object Disconnected : SafetyState
    data object Connecting : SafetyState
    data object ConnectedUnverified : SafetyState
    data object Safe : SafetyState
    data object ConfigurationPending : SafetyState
    data object ReadyToArm : SafetyState
    data class Armed(val expiresAtMillis: Long) : SafetyState
    data object EmissionRequested : SafetyState
    data object Emitting : SafetyState
    data object Stopping : SafetyState
    data class Fault(val reason: FaultReason) : SafetyState
    data object InterlockOpen : SafetyState
    data object CommunicationLost : SafetyState
}

enum class FaultReason {
    ALARM,
    MALFORMED_RESPONSE,
    TIMEOUT,
    UNKNOWN_DEVICE_STATE,
    STALE_STATUS,
    STOP_NOT_CONFIRMED,
    TRANSPORT_ERROR,
}

enum class SafetyEvent {
    CONNECT_REQUESTED,
    TRANSPORT_CONNECTED,
    IDENTITY_AND_PROTOCOL_VERIFIED,
    STATUS_CONFIRMED_SAFE,
    PARAMETER_EDITED,
    PARAMETERS_APPLIED,
    PARAMETERS_REVIEWED,
    ARM_HELD,
    EMISSION_REQUESTED,
    EMISSION_CONFIRMED,
    STOP_REQUESTED,
    STOP_CONFIRMED,
    INTERLOCK_OPENED,
    INTERLOCK_CLOSED,
    CONNECTION_LOST,
    MALFORMED_RESPONSE,
    COMMUNICATION_TIMEOUT,
    STATUS_STALE,
    UNKNOWN_STATUS,
    ALARM_STARTED,
    NAVIGATED,
    APP_BACKGROUNDED,
    ARM_TIMEOUT,
    RESET_FAULT,
}

data class TransitionResult(
    val previous: SafetyState,
    val event: SafetyEvent,
    val current: SafetyState,
    val accepted: Boolean,
)

