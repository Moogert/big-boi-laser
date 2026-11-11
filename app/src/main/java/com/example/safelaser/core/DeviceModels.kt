package com.example.safelaser.core

enum class KnownState { YES, NO, UNKNOWN }
enum class OutputState { OFF, ON, UNKNOWN }
enum class ParameterState { APPLIED, PENDING }

data class DeviceIdentity(
    val model: String,
    val serialNumber: String,
    val firmwareVersion: String,
    val protocolVersion: String,
    val verified: Boolean,
)

data class DeviceParameter(
    val id: String,
    val name: String,
    val unit: String,
    val minimum: Double,
    val maximum: Double,
    val appliedValue: Double?,
    val editedValue: Double?,
    val state: ParameterState,
)

data class ParameterDraft(
    val values: Map<String, Double> = emptyMap(),
    val waveformSlot: Int = 0,
) {
    val isEmpty: Boolean get() = values.isEmpty()
}

data class DeviceStatus(
    val identity: DeviceIdentity?,
    val output: OutputState,
    val interlockClosed: KnownState,
    val coolingAvailable: KnownState,
    val alarms: List<String>,
    val lastUpdateMillis: Long?,
    val parameters: List<DeviceParameter>,
)
