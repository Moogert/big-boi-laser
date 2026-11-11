# APK Inventory

## Evidence

| Field | Value |
| --- | --- |
| Evidence file | `evidence/original.apk` |
| Original path | `C:\Users\wubbenhj\Downloads\base.apk.1.1.apk.1.1_tmp.apk.1.1_tmp` |
| SHA-256 | `E5F99A90DE8D6D4A104B2A37B2F88C3187CDB29EEA3DAE337B177BADD01B0DB5` |
| File size | 36,758,520 bytes |
| Original modified time | 2026-07-31 13:06:46 UTC |
| Evidence protection | Read-only file attribute |

## Package metadata

| Field | Value | Evidence |
| --- | --- | --- |
| Application label | `BWTLaser` | Decoded `AndroidManifest.xml` |
| Package name | `com.BWTLaser.laser` | Decoded `AndroidManifest.xml` |
| Version name | `2.2.2` | Apktool `apktool.yml` |
| Version code | `32` | Apktool `apktool.yml` |
| Minimum Android SDK | 23 | Apktool `apktool.yml` |
| Target Android SDK | 29 | Apktool `apktool.yml` |
| Compile Android SDK | 29 | Decoded `AndroidManifest.xml` |
| Architectures | `arm64-v8a`, `armeabi-v7a`, `x86_64` | APK `lib/` entries |

## Requested permissions

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.WRITE_EXTERNAL_STORAGE`
- `android.permission.READ_EXTERNAL_STORAGE`
- `android.permission.REQUEST_INSTALL_PACKAGES`
- `android.permission.BLUETOOTH`
- `android.permission.BLUETOOTH_ADMIN`
- `android.permission.BLUETOOTH_PRIVILEGED`
- `android.permission.ACCESS_COARSE_LOCATION`
- `android.permission.ACCESS_FINE_LOCATION`
- `android.permission.ACCESS_WIFI_STATE`

The app requires the `android.hardware.bluetooth_le` feature.

## Components

Activities: `GuideActivity`, `MainActivity`, `SplashActivity`, `IntermediateActivity`, and `WebAuthenticatorIntermediateActivity`.

Service: `KeepAliveService`.

Receivers: battery, energy saver, connectivity, and power-save receivers.

Providers: the application file provider, Xamarin Essentials file provider, and Mono runtime provider.

## Signing certificate

| Field | Value |
| --- | --- |
| Serial number | `52DC339C` |
| Valid from | 2022-08-19 02:40:41 EDT |
| Valid until | 2052-08-11 02:40:41 EDT |
| Signature algorithm | SHA256withRSA |
| Public key | 2048-bit RSA |
| SHA-1 | `B2:47:4F:CC:A1:A3:59:1B:34:BC:FE:E1:01:FE:6B:B1:0B:D5:DE:82` |
| SHA-256 | `D7:F9:84:27:27:C4:30:DB:97:D6:55:D9:C3:D2:7E:CA:A9:DE:F8:D7:EE:00:C3:5D:31:CE:74:1D:94:B2:2A:55` |

The certificate subject contains characters that the local key tool did not decode. The subject remains `UNKNOWN`.

## Tools

| Tool | Version | Result |
| --- | --- | --- |
| Apktool | 3.0.3 | Resource and manifest decode succeeded. |
| JADX | 1.5.6 | DEX decompilation completed with four errors. |
| pyxamstore | Source from `jakev/pyxamstore` | All 82 managed assemblies extracted. |
| ILSpyCmd | 9.1.0.7988 | Both application assemblies decompiled. |
| Java keytool | Bundled with JADX 1.5.6 | Certificate data extracted. |

The project preserves the failed JADX result in `analysis/work/jadx.log`. The work directory is not source evidence.

