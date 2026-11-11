# Developer Guide

## Design rules

Keep operator actions outside transport classes.

Send every command through `ValidatedCommandService`.

Add each new application state to the explicit transition table and its tests.

Do not add a real command until the command evidence matrix has confirmed evidence and human approval.

Do not add automatic retries for hazardous commands.

## Simulator

`SimulatedBluetoothTransport` provides deterministic responses for the required fault scenarios.

Add simulator behavior before code uses a new protocol capability.

Use a fake clock for timeout tests.

## Protocol

`UnknownProtocolCodec` fails closed.

Replace it only after a hardware engineer approves packet layout, units, ranges, and acknowledgments.

Keep command encoding independent from the Android Bluetooth API.

## Audit data

Log structured commands instead of raw personal data.

Do not log passwords, credentials, or personal information.

