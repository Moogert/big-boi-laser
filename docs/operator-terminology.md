# Operator Terminology

The application must use only the approved terms in operator text.

| Item | Approved term | Definition | Prohibited synonyms | Safety relevance | Example |
| --- | --- | --- | --- | --- | --- |
| Physical laser system | laser system | The complete physical system that can produce laser output. | unit, machine, equipment | The term separates hardware from the app. | The laser system reports an alarm. |
| Android application | app | The Android software in this project. | controller, software control | The app is not the physical laser system. | The app checks the interlock state. |
| Bluetooth connection | Bluetooth connection | The BLE communication link between the app and the laser system. | link, wireless link, online connection | The term identifies the communication path. | The Bluetooth connection is lost. |
| Operator | operator | The person who uses the app. | user, technician, worker | Instructions must identify the responsible person. | The operator reviews the applied parameters. |
| Laser output | laser output | The hazardous optical output from the laser system. | beam, light, fire | The term identifies the hazardous output. | LASER OUTPUT IS OFF |
| Armed state | laser armed | A temporary state that permits a separate emission request. | enabled, engaged, ready | Armed does not mean emitting. | LASER ARMED |
| Emission state | laser output is on | A confirmed state where the laser system reports output. | active, running, firing | The term states confirmed output. | LASER OUTPUT IS ON |
| Stop request | stop request | A command request to stop laser output. | stop action, shutdown command | A request is not a confirmed result. | STOP REQUEST SENT |
| Physical emergency stop | physical emergency stop | The primary physical emergency control. | app stop, emergency button | The app cannot replace this control. | Use the physical emergency stop. |
| Interlock | interlock | The hardware safety circuit that can inhibit laser output. | lock, safety loop | The term prevents confusion with software state. | INTERLOCK OPEN |
| Cooling system | cooling system | The system that removes heat from the laser system. | cooler, chiller | Cooling faults can prohibit arming. | COOLING SYSTEM FAULT |
| Fault | fault | A condition that makes the app enter its fault state. | problem, issue, error | The term causes defined protective behavior. | The app reports a communication fault. |
| Alarm | alarm | A device-reported condition that prohibits arming. | alert, warning | The term identifies device status. | The laser system reports an alarm. |
| Configured parameter | configured parameter | A parameter value stored for review in the app. | setting, option | The value is not necessarily applied. | Review each configured parameter. |
| Applied parameter | applied parameter | A parameter value that the laser system acknowledged. | current setting, saved value | The term requires device acknowledgment. | PARAMETERS APPLIED |
| Pending parameter | pending parameter | An edited parameter that the laser system has not acknowledged. | draft setting, new value | The term prevents an unsupported applied claim. | PARAMETERS PENDING |
| Device status update | device status update | A valid status message from the verified laser system. | refresh, report | The term defines status age. | The last device status update is stale. |
| Command acknowledgment | command acknowledgment | A valid response that matches the sent command and session. | success, reply, confirmation | The term separates a write result from device action. | The app did not get a command acknowledgment. |
| Communication timeout | communication timeout | The end of the permitted response time without a valid response. | delay, no reply | The condition causes a fault. | A communication timeout occurred. |
| Connection loss | connection loss | The loss of an established Bluetooth connection. | disconnect, dropout | The condition makes the output state unknown. | Connection loss occurred. |

## Required distinction

Use `STOP REQUEST SENT` after the app sends a stop request.

Use `STOP CONFIRMED` only after a valid device status update shows that laser output is off.

Use `STOP NOT CONFIRMED` when the app does not get the required command acknowledgment or status.

