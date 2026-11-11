package com.example.safelaser.audit

import android.content.Context
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileAuditLogger(context: Context) : AuditLogger {
    private val directory = File(context.filesDir, "audit").apply { mkdirs() }
    private var file = File(directory, "session-${System.currentTimeMillis()}-${UUID.randomUUID()}.jsonl")

    override fun log(entry: AuditEntry) {
        file.appendText(entry.toJson() + "\n", Charsets.UTF_8)
    }

    override fun lines(): List<String> =
        if (file.exists()) file.readLines(Charsets.UTF_8).takeLast(DISPLAY_LINES).reversed() else emptyList()

    fun startSession(sessionId: String) {
        file = File(directory, "session-${System.currentTimeMillis()}-${sessionId.safe()}.jsonl")
    }

    fun activeFile(): File = file

    fun exportSession(buildMetadata: String): File {
        val zip = File(directory, file.nameWithoutExtension + ".zip")
        ZipOutputStream(zip.outputStream().buffered()).use { output ->
            output.putNextEntry(ZipEntry(file.name))
            file.inputStream().use { it.copyTo(output) }
            output.closeEntry()
            output.putNextEntry(ZipEntry("summary.txt"))
            val commandCount = file.useLines { lines -> lines.count { it.contains("\"type\":\"command\"") } }
            val trafficCount = file.useLines { lines -> lines.count { it.contains("\"type\":\"traffic\"") } }
            output.write("Commands: $commandCount\nTraffic records: $trafficCount\n".toByteArray())
            output.closeEntry()
            output.putNextEntry(ZipEntry("build.txt"))
            output.write(buildMetadata.toByteArray())
            output.closeEntry()
        }
        return zip
    }

    fun addObservation(sessionId: String, note: String) {
        val clean = note.replace('\n', ' ').trim()
        file.appendText("{\"type\":\"observation\",\"wallClockMillis\":${System.currentTimeMillis()},\"sessionId\":\"${sessionId.json()}\",\"note\":\"${clean.json()}\"}\n")
    }

    fun logTraffic(sessionId: String, direction: String, text: String, hex: String) {
        file.appendText("{\"type\":\"traffic\",\"wallClockMillis\":${System.currentTimeMillis()},\"monotonicMillis\":${android.os.SystemClock.elapsedRealtime()},\"sessionId\":\"${sessionId.json()}\",\"direction\":\"${direction.json()}\",\"text\":\"${text.json()}\",\"hex\":\"${hex.json()}\"}\n")
    }

    private fun AuditEntry.toJson() = "{" + listOf(
        "\"type\":\"command\"",
        "\"wallClockMillis\":$wallClockMillis",
        "\"monotonicMillis\":$timestampMillis",
        "\"sessionId\":\"${sessionId.json()}\"",
        "\"operatorAction\":\"${operatorAction.json()}\"",
        "\"command\":\"${(command::class.simpleName ?: "UNKNOWN").json()}\"",
        "\"safetyClass\":\"${command.safetyClass}\"",
        "\"applicationStateBefore\":\"${deviceStateBefore.json()}\"",
        "\"applicationStateAfter\":\"${deviceStateAfter.json()}\"",
        "\"characteristicUuid\":\"${characteristicUuid.json()}\"",
        "\"writeResult\":\"${writeResult.json()}\"",
        "\"acknowledgmentResult\":\"${acknowledgmentResult.json()}\"",
        "\"payload\":${structuredPayload.entries.joinToString(prefix = "{", postfix = "}") { "\"${it.key.json()}\":\"${it.value.json()}\"" }}",
        "\"transmittedLines\":${transmittedLines.joinToString(prefix = "[", postfix = "]") { "\"${it.json()}\"" }}",
        "\"receivedLines\":${receivedLines.joinToString(prefix = "[", postfix = "]") { "\"${it.json()}\"" }}",
    ).joinToString(",") + "}"

    private fun String.json() = replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n")
    private fun String.safe() = replace(Regex("[^A-Za-z0-9._-]"), "_")

    private companion object {
        const val DISPLAY_LINES = 2_000
    }
}
