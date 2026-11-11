# LSO Simulator Test Plan

## Scope

Use only the `simulatorDebug` APK. Do not connect this build to a laser. The APK has no Android Bluetooth permissions and contains no active hardware transport path.

This review evaluates application behavior. It does not prove physical laser safety.

## Required controls

1. Confirm that the app opens in dark mode.
2. Confirm that `REQUEST LASER STOP` is at the top of every screen.
3. Confirm that the physical emergency-stop notice is beside the operating status.
4. Confirm that a new connection starts disarmed.
5. Confirm that parameter edits remain pending until review and apply.
6. Confirm that arming requires a timed hold.
7. Confirm that emission requires a separate request.
8. Request a stop. Confirm that the app reports `STOP CONFIRMED` only after the simulator acknowledgment.
9. Arm the simulator again. Confirm that `STOP CONFIRMED` is removed.
10. Review the command log after each command.

## Experimental simulated live tuning

1. Open `CONFIG` or `SWEEP`.
2. Confirm that hardware live tuning is shown as disabled.
3. Select `REVIEW EXPERIMENTAL MODE`.
4. Read the simulator-only warning.
5. Enable simulator live tuning.
6. Start simulated emission.
7. Edit one parameter and review the pending value.
8. Apply the change.
9. Confirm that the simulator remains in `Emitting`.
10. Confirm that the log contains `APPLY PARAMETERS DURING SIMULATED EMISSION`.
11. Disable experimental mode.
12. Confirm that active-emission parameter application is blocked.

## Stop failure review

Use the stop-acknowledgment-failure simulator scenario in automated tests. Confirm that the app does not report a completed stop. Confirm that it tells the operator to use the physical emergency stop and follow the manufacturer shutdown procedure.

## Record

Record the APK SHA-256, app version, test device, Android version, tester, date, result, observations, and every unexpected behavior. Record the time to locate and use the stop control on each screen.

## Release gate

Do not approve hardware use from this simulator review. Hardware validation requires written protocol confirmation, reviewed parameter limits, a controlled test procedure, an independent stop method, a qualified laser safety officer, and a hardware engineer.
