# Original Interface Screen Inventory

| Screen | Type | Main purpose | Safety relevance |
| --- | --- | --- | --- |
| Main page | `MainPage` | Hosts the status and alarm views. | Shows operating status. |
| Device list | `DeviceListPage` | Scans for names that contain `BWT`. | Starts Bluetooth connection. |
| Device status | `DeviceInfoPage` | Shows status, parameters, alarms, and device data. | Polls laser state. |
| Settings | `SettingsPage` | Changes power, frequency, duty cycle, modes, and enable state. | Can request laser output. |
| Alarm | `AlarmPage` | Shows alarm information. | Reports device alarms. |
| Input and output | `IOPage` | Shows hardware input and output state. | Can affect state interpretation. |
| Password | `PasswordPage` | Handles access and device lock data. | Can change permitted functions. |
| After-sales settings | `AfterSaleSettingPage` | Provides service settings. | Contains privileged operations. |
| Device information | `DeviceInfoPage` | Shows firmware and hardware identifiers. | Supports identity review. |
| Guide | `GuideActivity` | Shows first-use guide content. | Contains `进入` and `跳过` controls. |

