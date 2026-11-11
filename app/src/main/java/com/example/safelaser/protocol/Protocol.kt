package com.example.safelaser.protocol

import com.example.safelaser.core.DeviceStatus

enum class SafetyClass { INFORMATION, CONFIGURATION, MOVEMENT, EMISSION, STOP, UNKNOWN }
enum class RetryPolicy { NEVER, SAFE_INFORMATION_ONLY }

sealed interface DeviceCommand {
    val safetyClass: SafetyClass
    val retryPolicy: RetryPolicy

    data object Identify : DeviceCommand {
        override val safetyClass = SafetyClass.INFORMATION
        override val retryPolicy = RetryPolicy.SAFE_INFORMATION_ONLY
    }
    data object ReadStatus : DeviceCommand {
        override val safetyClass = SafetyClass.INFORMATION
        override val retryPolicy = RetryPolicy.SAFE_INFORMATION_ONLY
    }
    data class ApplyParameters(val values: Map<String, Double>) : DeviceCommand {
        override val safetyClass = SafetyClass.CONFIGURATION
        override val retryPolicy = RetryPolicy.NEVER
    }
    /** This command is simulator-only. Real transports must reject it. */
    data class ApplySimulatedLiveParameters(val values: Map<String, Double>) : DeviceCommand {
        override val safetyClass = SafetyClass.CONFIGURATION
        override val retryPolicy = RetryPolicy.NEVER
    }
    data object Arm : DeviceCommand {
        override val safetyClass = SafetyClass.EMISSION
        override val retryPolicy = RetryPolicy.NEVER
    }
    data object RequestEmission : DeviceCommand {
        override val safetyClass = SafetyClass.EMISSION
        override val retryPolicy = RetryPolicy.NEVER
    }
    data object RequestStop : DeviceCommand {
        override val safetyClass = SafetyClass.STOP
        override val retryPolicy = RetryPolicy.NEVER
    }
}

sealed interface DeviceResponse {
    data class Acknowledged(
        val command: DeviceCommand,
        val status: DeviceStatus?,
        val transmittedLines: List<String> = emptyList(),
        val receivedLines: List<String> = emptyList(),
    ) : DeviceResponse
    data class Rejected(val reason: String) : DeviceResponse
    data object MissingAcknowledgment : DeviceResponse
    data object Malformed : DeviceResponse
}

/** Exact allowlist recovered from BWTLasers.SettingsViewModel. */
object LegacyLaserProtocol {
    const val SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"
    const val CHARACTERISTIC_UUID = "0000fff6-0000-1000-8000-00805f9b34fb"
    const val MAX_WRITE_BYTES = 14

    fun requestLines(command: DeviceCommand): List<String> = when (command) {
        DeviceCommand.Identify -> listOf("RBWT")
        DeviceCommand.ReadStatus -> listOf("REN", "RCPR", "RCMF", "RCDC", "RCMFH", "RCDCL")
        DeviceCommand.Arm -> emptyList()
        DeviceCommand.RequestEmission -> listOf("WEN Y", "REN")
        DeviceCommand.RequestStop -> listOf("WEN N", "REN")
        is DeviceCommand.ApplyParameters -> parameterLines(command.values)
        is DeviceCommand.ApplySimulatedLiveParameters -> error("A simulator command cannot use the hardware protocol.")
    }

    private fun parameterLines(values: Map<String, Double>): List<String> = buildList {
        values["output_percent"]?.let { add("WCPR ${format(it)}"); add("RCPR") }
        values["pwm_frequency_hz"]?.let { add("WCMF ${format(it)}"); add("RCMF") }
        values["duty_cycle_percent"]?.let { add("WCDC ${format(it)}"); add("RCDC") }
        values["waveform_slot"]?.let { add("RNWAVE ${format(it)}") }
        val unsupported = values.keys - setOf("output_percent", "pwm_frequency_hz", "duty_cycle_percent", "waveform_slot")
        require(unsupported.isEmpty()) { "The hardware parameter is not allowlisted: ${unsupported.joinToString()}" }
    }

    fun frame(line: String): ByteArray = "$line\r\n".toByteArray(Charsets.US_ASCII)

    fun segments(line: String): List<ByteArray> = frame(line).toList().chunked(MAX_WRITE_BYTES)
        .map { chunk -> chunk.toByteArray() }

    fun expectedResponse(commandLine: String): String? = when {
        commandLine == "RBWT" -> "BWTLASER"
        commandLine == "REN" -> "REN"
        commandLine == "RCPR" -> "RCPR"
        commandLine == "RCMF" -> "RCMF"
        commandLine == "RCDC" -> "RCDC"
        commandLine == "RCMFH" -> "RCMFH"
        commandLine == "RCDCL" -> "RCDCL"
        commandLine.startsWith("W") -> null
        commandLine.startsWith("RNWAVE ") -> null
        else -> error("The command is not allowlisted: $commandLine")
    }

    private fun format(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}

interface ProtocolCodec {
    fun encode(command: DeviceCommand): ByteArray
    fun decode(packet: ByteArray): DeviceResponse
}

class UnknownProtocolCodec : ProtocolCodec {
    override fun encode(command: DeviceCommand): ByteArray =
        error("The hardware protocol is not approved. Real commands are disabled.")

    override fun decode(packet: ByteArray): DeviceResponse = DeviceResponse.Malformed
}
