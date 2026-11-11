# Operator Guide

## Scope

Use this app only with the simulated device.

Do not connect this build to a laser system.

The physical emergency stop is the primary emergency control.

## Connect the simulated device

1. Start the app.
2. Make sure that the screen shows `SIMULATOR BUILD`.
3. Select `CONNECT SIMULATED DEVICE`.
4. Make sure that the device identity is `SIM-0001`.
5. Make sure that `LASER OUTPUT IS OFF` is shown.

## Arm the simulated laser system

1. Review the applied parameters.
2. Make sure that the interlock is closed.
3. Make sure that the cooling system has no fault.
4. Make sure that no alarm is active.
5. Hold `ARM LASER` for two seconds.
6. Review the arming summary.

The armed state ends after a short time.

The armed state also ends when the app moves to the background.

## Request simulated laser emission

1. Complete the arming procedure.
2. Review the emission-request dialog.
3. Select `REQUEST LASER EMISSION`.
4. Wait for the simulated command acknowledgment.
5. Read the laser output state.

## Request a simulated stop

1. Select `REQUEST LASER STOP`.
2. Wait for the simulated command acknowledgment.
3. Make sure that `STOP CONFIRMED` is shown.
4. Make sure that `LASER OUTPUT IS OFF` is shown.

If `STOP NOT CONFIRMED` is shown, use the physical emergency stop.

Follow the manufacturer shutdown procedure.

