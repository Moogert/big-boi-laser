# Hardware Validation Build

## Build identity

The `hardwareValidationDebug` APK is a controlled engineering build. It scans only for device names that contain `BWT`. It requires the recovered FFF0 service and FFF6 communication characteristic.

The simulator and release-gate APKs do not request Bluetooth permissions.

## Implemented command allowlist

- `RBWT`
- `REN`
- `WEN Y`
- `WEN N`
- `RCPR` and `WCPR n`
- `RCMF`, `RCMFH`, and `WCMF n`
- `RCDC`, `RCDCL`, and `WCDC n`
- `RNWAVE n`

No free-command input exists. The transport cannot accept arbitrary text from the interface.

## Known validation gate

The recovered protocol evidence does not identify a device interlock-status command. The application reports the interlock as `UNKNOWN`. The validated command service blocks configuration and emission commands while that state is unknown. Do not replace this value with an assumption. Add the verified status command and response mapping after the engineer provides it.

## First connection procedure

1. Install the hardware-validation APK only on the designated test device.
2. Record the APK SHA-256.
3. Open the enclosure test record.
4. Select `SCAN FOR BWT LASER`.
5. Approve the Android Bluetooth permissions.
6. Confirm the discovered device identity and full GATT UUIDs in the audit.
7. Export the audit after the connection test.
8. Compare every transmitted and received line with the engineer’s protocol response.

Do not install the simulator or release-gate APK as a substitute for this build.
