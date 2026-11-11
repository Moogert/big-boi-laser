# Architecture Decision Record

## Decision

Use native Kotlin, Jetpack Compose, coroutines, immutable interface state, and Android BLE APIs.

## Reasons

Kotlin gives direct control of Android lifecycle events and Bluetooth callbacks.

Jetpack Compose supports explicit interface state and automated interface tests.

The design does not inherit the obsolete Xamarin.Forms runtime or its direct view-model writes.

The project separates the operator interface, safety state machine, command validator, protocol codec, transport, simulator, and audit logger.

## Safety boundary

The operator interface cannot access a Bluetooth characteristic.

The validated command service is the only command path.

The real transport always fails closed. It contains no known or guessed protocol command.

## Build variants

`simulator` is the default product flavor.

`hardwareValidation` includes the disabled real transport stub. A source change and formal review must enable it.

`releaseGate` is a blocked release flavor. It cannot communicate with hardware.

