package com.example.safelaser.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyStateMachineTest {
    private class FakeClock(var value: Long = 0L) : Clock {
        override fun nowMillis() = value
    }

    @Test
    fun initialStateIsDisconnected() {
        assertEquals(SafetyState.Disconnected, SafetyStateMachine(FakeClock()).state)
    }

    @Test
    fun connectionDoesNotArmLaser() {
        val machine = SafetyStateMachine(FakeClock())
        machine.dispatch(SafetyEvent.CONNECT_REQUESTED)
        machine.dispatch(SafetyEvent.TRANSPORT_CONNECTED)
        assertEquals(SafetyState.ConnectedUnverified, machine.state)
    }

    @Test
    fun deliberateFlowCanReachEmitting() {
        val machine = readyMachine(FakeClock())
        assertTrue(machine.dispatch(SafetyEvent.ARM_HELD).accepted)
        assertTrue(machine.dispatch(SafetyEvent.EMISSION_REQUESTED).accepted)
        assertTrue(machine.dispatch(SafetyEvent.EMISSION_CONFIRMED).accepted)
        assertEquals(SafetyState.Emitting, machine.state)
    }

    @Test
    fun emissionRequestIsRejectedBeforeArming() {
        val machine = readyMachine(FakeClock())
        assertFalse(machine.dispatch(SafetyEvent.EMISSION_REQUESTED).accepted)
        assertEquals(SafetyState.ReadyToArm, machine.state)
    }

    @Test
    fun backgroundCancelsArming() {
        val machine = readyMachine(FakeClock())
        machine.dispatch(SafetyEvent.ARM_HELD)
        machine.dispatch(SafetyEvent.APP_BACKGROUNDED)
        assertEquals(SafetyState.Safe, machine.state)
    }

    @Test
    fun parameterEditCancelsArming() {
        val machine = readyMachine(FakeClock())
        machine.dispatch(SafetyEvent.ARM_HELD)
        machine.dispatch(SafetyEvent.PARAMETER_EDITED)
        assertEquals(SafetyState.ConfigurationPending, machine.state)
    }

    @Test
    fun connectionLossNeverRestoresArmedState() {
        val machine = readyMachine(FakeClock())
        machine.dispatch(SafetyEvent.ARM_HELD)
        machine.dispatch(SafetyEvent.CONNECTION_LOST)
        machine.dispatch(SafetyEvent.CONNECT_REQUESTED)
        machine.dispatch(SafetyEvent.TRANSPORT_CONNECTED)
        assertEquals(SafetyState.ConnectedUnverified, machine.state)
    }

    @Test
    fun malformedResponseCreatesFault() {
        val machine = readyMachine(FakeClock())
        machine.dispatch(SafetyEvent.MALFORMED_RESPONSE)
        assertEquals(SafetyState.Fault(FaultReason.MALFORMED_RESPONSE), machine.state)
    }

    @Test
    fun interlockOpeningCancelsArming() {
        val machine = readyMachine(FakeClock())
        machine.dispatch(SafetyEvent.ARM_HELD)
        machine.dispatch(SafetyEvent.INTERLOCK_OPENED)
        assertEquals(SafetyState.InterlockOpen, machine.state)
    }

    @Test
    fun stopHasPathFromEmittingAndNeedsConfirmation() {
        val machine = readyMachine(FakeClock())
        machine.dispatch(SafetyEvent.ARM_HELD)
        machine.dispatch(SafetyEvent.EMISSION_REQUESTED)
        machine.dispatch(SafetyEvent.EMISSION_CONFIRMED)
        machine.dispatch(SafetyEvent.STOP_REQUESTED)
        assertEquals(SafetyState.Stopping, machine.state)
        machine.dispatch(SafetyEvent.STOP_CONFIRMED)
        assertEquals(SafetyState.Safe, machine.state)
    }

    private fun readyMachine(clock: FakeClock): SafetyStateMachine = SafetyStateMachine(clock).also {
        it.dispatch(SafetyEvent.CONNECT_REQUESTED)
        it.dispatch(SafetyEvent.TRANSPORT_CONNECTED)
        it.dispatch(SafetyEvent.IDENTITY_AND_PROTOCOL_VERIFIED)
        it.dispatch(SafetyEvent.STATUS_CONFIRMED_SAFE)
    }
}

