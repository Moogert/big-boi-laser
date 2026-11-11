# LSO Candidate Release Checklist

- [ ] The original APK hash matches the recorded evidence hash.
- [ ] Unit tests pass.
- [ ] Operator text checks pass.
- [ ] The simulator APK builds successfully.
- [ ] The APK contains no Bluetooth permissions.
- [ ] The release artifact SHA-256 is recorded.
- [ ] The release artifact is labeled `SIMULATOR ONLY`.
- [ ] The LSO receives the simulator test plan.
- [ ] The LSO receives the unresolved-questions list.
- [ ] No real laser is present during usability testing.
- [ ] Findings are recorded and reviewed before another build.

This candidate is not a hardware release. Do not publish or sign a hardware-enabled APK.
