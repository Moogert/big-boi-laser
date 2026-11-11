# Windows Controller Validation

## Launch

Install the organization-approved .NET SDK. This workstation uses its approved .NET 10 SDK to build the Windows target. From `windows-controller`, run `run-controller.cmd` for the deterministic simulator. Use `dotnet run --project LaserController.Windows.csproj -- --hardware` only for the controlled hardware-validation transport.

The host listens only on `127.0.0.1`. It creates a random per-process token and opens a browser URL that includes that token. Do not expose the port with a proxy, port-forwarding rule, or remote browser session.

## Command boundary

The browser has no Bluetooth capability. It can call only typed local endpoints. The backend accepts the recovered identity, status, enable, disable, power, frequency, duty-cycle, and experimental waveform command families. It has no free-command endpoint.

## Validation record

Use the simulator to validate the browser, accessibility controls, translations, command review, audit recording, and ZIP export. The hardware transport must first validate BWT discovery, FFF0/FFF6 discovery, and `RBWT` identity traffic. Retain the exported audit after every connection session.

The published executable passed a simulator smoke check for loopback-token rejection, automatic single-device simulation, identity verification, and disable confirmation. This is not hardware validation.

## Android freeze

The Android application now uses the supported AppCompat locale API and saveable navigation state. Reviewed setting changes do not require a stopped output state. Treat the Android build as feature-frozen after this verification. Add new controller capability only to the Windows implementation.
