# Original Application Architecture

The APK uses Xamarin.Forms and .NET for Android.

`SplashActivity` starts the Android application. `App` creates `MainPage`.

`MainViewModel` owns `AlarmViewModel` and `DeviceInfoViewModel`. `DeviceInfoViewModel` creates `BleComManager` and `SettingsViewModel`.

`BleComManager` scans, connects, subscribes to notifications, frames text, and writes commands. View models call it directly.

The original interface has no separate safety state machine. It has no central command validator.

The app also opens UDP and TCP adapters. A remote peer can forward commands to `BleComManager.SendFreeCmd`.

The replacement excludes all remote-command and free-command paths.

