# Project Tasks

## Safety controls

- [x] Prohibit live laser operation.
- [x] Prohibit Bluetooth writes during analysis.
- [x] Keep real Bluetooth transport disabled by default.
- [x] Record unknown protocol facts as `UNKNOWN`.
- [ ] Get hardware engineer approval before a hardware-validation build.
- [ ] Get laser safety officer approval before live validation.

## Evidence and static analysis

- [x] Preserve the supplied APK and verify its hash.
- [x] Record APK package, version, SDK, component, permission, architecture, and signing data.
- [x] Decode Android resources.
- [x] Extract the Xamarin assembly store.
- [x] Decompile the application assemblies.
- [x] Trace the confirmed enable, disable, free-command, and remote-command writes.
- [ ] Complete the evidence matrix for every maintenance and diagnostic command.
- [ ] Get human translation review for every extracted operator string.
- [x] Record each currently supported unexpected-emission path.

## Replacement application

- [x] Implement the safety state machine.
- [x] Implement the validated command service.
- [x] Keep the hardware protocol codec fail-closed while evidence is incomplete.
- [x] Implement the simulated Bluetooth transport.
- [x] Keep the real Bluetooth transport fail-closed.
- [x] Implement the allowlisted hardware-validation BLE transport.
- [x] Add device scanning, single-device automatic connection, and multiple-device selection.
- [x] Add append-only session traffic and command audit export.
- [x] Add English, Spanish, Simplified Chinese, and French resources.
- [x] Add text size, high contrast, reduced motion, and system-bar inset handling.
- [x] Implement the main operator status interface.
- [x] Complete the parameter review and apply interface.
- [x] Add simulator-only experimental live parameter tuning.
- [x] Add cleaning configuration, sweep settings, and persistent bounded command logs.
- [x] Put the stop request at the top of every operating screen.
- [x] Use dark mode by default.
- [ ] Complete human usability changes after simulator tests.
- [x] Implement the audit logger model.

## Windows controller

- [x] Create the loopback-only Windows controller source and browser interface.
- [x] Add typed protocol, simulator, Windows BLE transport, token authorization, audit export, and accessibility controls.
- [x] Compile and publish the Windows controller with the installed organization-approved .NET SDK.
- [x] Run the Windows simulator smoke test, including token rejection and stop confirmation.
- [ ] Run controlled Windows hardware-connection validation.
- [x] Freeze Android development after locale and apply-rule correction.

## Verification

- [ ] Expand the transition test matrix to enumerate every event and state pair.
- [x] Test the critical permitted and prohibited state transitions.
- [ ] Add behavior assertions for every simulator fault scenario.
- [x] Add regression tests for startup, connection, arming cancellation, stop failure, and connection loss.
- [x] Run the operator-text lint.
- [x] Run all current unit tests.
- [x] Build and inspect the simulator-only LSO APK.
- [x] Build and inspect the hardware-validation and blocked release APKs.
- [ ] Map a verified device interlock-status response before configuration or emission validation.
- [ ] Run instrumented interface tests on an Android emulator.

## Documentation

- [x] Create all required analysis document files.
- [x] Complete the simulator operator guide.
- [x] Complete the developer guide.
- [x] Complete the controlled hardware-validation plan.
- [x] Record unresolved questions and human-review items.
