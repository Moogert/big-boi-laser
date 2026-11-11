package com.example.safelaser.simulator

import com.example.safelaser.core.OutputState
import com.example.safelaser.protocol.DeviceCommand
import com.example.safelaser.protocol.DeviceResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedBluetoothTransportTest {
    @Test
    fun startsWithOutputOff() = runTest {
        val simulator = SimulatedBluetoothTransport()
        assertEquals(OutputState.OFF, simulator.status().output)
    }

    @Test
    fun stopAcknowledgmentFailureIsDeterministic() = runTest {
        val simulator = SimulatedBluetoothTransport(SimulatorScenario.STOP_ACKNOWLEDGMENT_FAILURE)
        simulator.connect()
        assertEquals(DeviceResponse.MissingAcknowledgment, simulator.send(DeviceCommand.RequestStop))
    }

    @Test
    fun rejectedParameterIsReported() = runTest {
        val simulator = SimulatedBluetoothTransport(SimulatorScenario.REJECTED_PARAMETER)
        simulator.connect()
        assertTrue(simulator.send(DeviceCommand.ApplyParameters(mapOf("Output setting" to 10.0))) is DeviceResponse.Rejected)
    }

    @Test
    fun connectionLossDuringEmissionDoesNotAcknowledgeEmission() = runTest {
        val simulator = SimulatedBluetoothTransport(SimulatorScenario.CONNECTION_LOSS_DURING_EMISSION)
        simulator.connect()
        assertEquals(DeviceResponse.MissingAcknowledgment, simulator.send(DeviceCommand.RequestEmission))
    }
}

