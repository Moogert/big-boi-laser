# Verification Record

## 2026-08-03

The project preserved the original APK as a read-only evidence file.

The final SHA-256 matched the initial SHA-256.

Apktool decoded the manifest and resources.

JADX completed with four errors. The project preserves this incomplete result in the ignored analysis work directory.

Pyxamstore extracted all 82 managed assemblies.

ILSpyCmd decompiled `BWTLasers` and `BWTLasers.Android`.

Gradle ran `testSimulatorDebugUnitTest` and `assembleSimulatorDebug` successfully.

The text lint and its Python tests passed.

Android `aapt` found no Bluetooth permission in the simulator APK.

The simulator APK package is `com.example.safelaser.simulator`.

The simulator APK size is 18,390,323 bytes.

The build reported one native-symbol stripping warning for `libandroidx.graphics.path.so`.

The build also reported an SDK XML version warning. These warnings did not fail the build.

