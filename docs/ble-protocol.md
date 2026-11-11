# Bluetooth Protocol

## Transport

The app scans for device names that contain `BWT`.

The service UUID starts with `0000fff0`. The communication characteristic UUID starts with `0000fff6`.

The characteristic must support read, write, and notifications. The exact 128-bit UUID suffix is not present in the managed check and remains `UNKNOWN`.

The protocol sends default-encoded text followed by carriage return and line feed. Responses also end with carriage return and line feed.

Commands longer than 14 characters can use multiple characteristic writes. The app does not add a packet checksum or CRC.

## Connection sequence

The app sends `RBWT` up to two times. It waits two seconds after each request.

The response `BWTLASER` marks the device as connected. The app then sends `OLOGIN` and multiple information requests.

The app starts a two-second poll timer after identification.

## Safety limits

Method names and strings do not prove physical behavior. The replacement real transport implements no command from this table.

Hardware engineers must approve command meaning, range, unit, acknowledgment, and failure behavior before implementation.

