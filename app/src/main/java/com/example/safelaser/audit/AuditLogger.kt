package com.example.safelaser.audit

import com.example.safelaser.core.SafetyState
import com.example.safelaser.protocol.DeviceCommand

data class AuditEntry(
    val timestampMillis: Long,
    val sessionId: String,
    val applicationState: SafetyState,
    val operatorAction: String,
    val command: DeviceCommand,
    val structuredPayload: Map<String, String>,
    val characteristicUuid: String,
    val writeResult: String,
    val acknowledgmentResult: String,
    val deviceStateBefore: String,
    val deviceStateAfter: String,
    val wallClockMillis: Long = System.currentTimeMillis(),
    val transmittedLines: List<String> = emptyList(),
    val receivedLines: List<String> = emptyList(),
)

interface AuditLogger {
    fun log(entry: AuditEntry)
    fun lines(): List<String>
}

class InMemoryAuditLogger : AuditLogger {
    private val mutableEntries = mutableListOf<AuditEntry>()
    val entries: List<AuditEntry> get() = mutableEntries.toList()
    override fun log(entry: AuditEntry) {
        mutableEntries += entry
    }
    override fun lines(): List<String> = mutableEntries.map(AuditEntry::displayLine)
}

fun AuditEntry.displayLine(): String =
    "$timestampMillis | $sessionId | $operatorAction | ${command::class.simpleName} | $writeResult | $acknowledgmentResult"
