# Safety Findings

## High-priority findings

1. The settings screen can send `WEN Y` after one arithmetic prompt.
2. The app uses the same button to send enable and disable commands.
3. The app claims output success after a 500-millisecond interface-state check.
4. The app exposes an unrestricted free-command input.
5. A TCP peer can forward device commands through the app.
6. The lifecycle methods do not cancel an enabled state.
7. Connection loss clears interface data but does not confirm laser output state.
8. The command layer has no safety state machine or command allowlist.
9. Polling and operator commands share mutable synchronization flags.
10. The app starts automatic polling after identity response.

The evidence supports multiple credible activation risks. It does not prove the cause of the reported event.

