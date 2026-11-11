# Build Instructions

## Requirements

- Android SDK 36
- Android Gradle Plugin 9.0.1
- A compatible Gradle release
- JDK 17 or a later compatible JDK

## Simulator build

1. Set `sdk.dir` in `local.properties`.
2. Run `gradlew assembleSimulatorDebug`.
3. Run `gradlew testSimulatorDebugUnitTest`.
4. Run `python scripts/text_lint.py`.

The default flavor does not include an active real Bluetooth transport.

## Hardware-validation build

Do not build this variant for live use without all written approvals.

The current `hardwareValidation` flavor still uses a fail-closed transport.

