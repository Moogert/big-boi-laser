package com.example.safelaser.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyLaserProtocolTest {
    @Test
    fun enableAndDisableAlwaysReadBackEnableState() {
        assertEquals(listOf("WEN Y", "REN"), LegacyLaserProtocol.requestLines(DeviceCommand.RequestEmission))
        assertEquals(listOf("WEN N", "REN"), LegacyLaserProtocol.requestLines(DeviceCommand.RequestStop))
    }

    @Test
    fun parametersUseDeterministicWriteThenReadOrder() {
        val command = DeviceCommand.ApplyParameters(linkedMapOf(
            "duty_cycle_percent" to 20.0,
            "output_percent" to 10.0,
            "pwm_frequency_hz" to 1_000.0,
        ))
        assertEquals(
            listOf("WCPR 10", "RCPR", "WCMF 1000", "RCMF", "WCDC 20", "RCDC"),
            LegacyLaserProtocol.requestLines(command),
        )
    }

    @Test
    fun framingUsesCrLfAndFourteenByteSegments() {
        assertEquals("WCPR 10\r\n", LegacyLaserProtocol.frame("WCPR 10").toString(Charsets.US_ASCII))
        val segments = LegacyLaserProtocol.segments("RTHUNLCKCNT LONG")
        assertTrue(segments.all { it.size <= 14 })
        assertEquals("RTHUNLCKCNT LONG\r\n", segments.flatMap { it.toList() }.toByteArray().toString(Charsets.US_ASCII))
    }

    @Test
    fun unsupportedParametersCannotBeEncoded() {
        assertThrows(IllegalArgumentException::class.java) {
            LegacyLaserProtocol.requestLines(DeviceCommand.ApplyParameters(mapOf("pulse_on_ms" to 10.0)))
        }
    }

    @Test
    fun simulatorLiveCommandCannotUseHardwareCodec() {
        assertThrows(IllegalStateException::class.java) {
            LegacyLaserProtocol.requestLines(DeviceCommand.ApplySimulatedLiveParameters(mapOf("output_percent" to 5.0)))
        }
    }
}
