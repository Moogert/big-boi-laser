# Unexpected Activation Analysis

| Trigger | Code path | Bluetooth packet | Preconditions | Current safeguard | Missing safeguard | Confidence | Recommended correction |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Enable button touch | `SettingsPage` binding to `SettingsViewModel.OpenLaser` to `BleComManager.SendMessage` | `WEN Y\r\n` | Connected state and correct arithmetic answer | Arithmetic prompt | Separate arming, status checks, interlock checks, hold action, and command acknowledgment | Supported | Use the replacement state machine and separate emission request. |
| Free-command input | `SettingsViewModel.SendFreeCmd` to `BleComManager.SendFreeCmd` | Operator text | Connected state and accepted prefix | Prefix check only | Command allowlist and safety classification | Confirmed | Remove the free-command interface. |
| Remote TCP command | `DeviceInfoViewModel.Comm_DataReceived` to `BleComManager.SendFreeCmd` | Remote text | UDP discovery and TCP connection | Prefix check only | Authentication, authorization, allowlist, and operator intent | Confirmed | Do not include remote command handling. |
| Delayed response and shared command state | View models update shared `ProtocolItem` while poll timer sends commands | UNKNOWN | Concurrent polling and operator command | Auto-reset events and boolean flags | Per-command correlation and session identifiers | Supported | Use serialized typed commands and matched acknowledgments. |
| Application background | Empty `App.OnSleep` | No direct packet found | App enters background while armed or enabled | None found | Cancel arming and request verified stop when required | Confirmed missing safeguard | Cancel arming on lifecycle changes. |
| Connection loss | `OnDeviceConnectionLost` to interface reset | No stop packet | Established connection fails | Clears interface state | Unknown-output fault and physical emergency-stop instruction | Confirmed missing safeguard | Enter `CommunicationLost` and show unknown output state. |

No analyzed code path sends `WEN Y` during application startup, page construction, or reconnection. This result does not exclude paths hidden by decompilation errors.

