# Controlled Hardware-Validation Plan

## Approval gate

Do not start this procedure without written approval from the laser safety officer.

Do not start this procedure without written approval from the hardware engineer.

Do not use software as the primary safety control.

## Required controls

1. Get the manufacturer-approved hardware disable method.
2. Disconnect the emission source when the manufacturer permits this action.
3. Install a vendor-approved dummy load when one is available.
4. Test the physical emergency stop.
5. Test the key control.
6. Test each hardware interlock.
7. Get the correct laser safety eyewear.
8. Establish a controlled exclusion area.
9. Install approved beam containment.
10. Assign two qualified persons to each test.
11. Prepare Bluetooth packet capture equipment.
12. Approve a written test matrix.

## Before each test

1. If a required control is not available, stop the procedure.
2. Make sure that the emission source cannot produce hazardous output.
3. Make sure that the physical emergency stop is accessible.
4. Make sure that the key control prevents unauthorized use.
5. Make sure that each interlock reports the correct state.
6. Record the app version and laser system identity.
7. Record the firmware version.
8. Record the expected command and response.

## Test method

1. Start Bluetooth packet capture.
2. Connect the hardware-validation build.
3. Verify the device identity.
4. Get a current device status update.
5. Compare each packet with the approved protocol specification.
6. Test one command at a time.
7. Record each command acknowledgment.
8. Test each failure condition with the hardware output disabled.
9. Stop the test after an unexpected response.
10. Use the physical emergency stop after an unconfirmed stop request.

## Completion

1. Disconnect the app.
2. Use the manufacturer shutdown procedure.
3. Save the packet capture and test record.
4. Record each difference from the approved test matrix.
5. Require human review before the next test phase.

