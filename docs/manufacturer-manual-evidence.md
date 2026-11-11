# Manufacturer Manual Evidence

## Applicable scope

The supplied FWH30-D10C manual covers cleaning and welding controller functions. This project implements cleaning terminology only. Welding and wire-feed functions are outside the current scope.

## Parameter evidence

The manual identifies laser power, PWM frequency, duty cycle, swing frequency, swing dimensions, pulse timing, and scan settings. The manual contains inconsistent duty-cycle limits. One section states 1 percent to 100 percent. Another section states 1 percent to 99 percent. The simulator uses the conservative 1 percent to 99 percent range pending engineering review.

Other simulator limits are provisional interface limits. They are not approved hardware limits.

## Processing configuration

The manual describes changes between cleaning configurations that require hardware component replacement and a power cycle. The application does not model these changes as dynamic parameters.

## Live parameter changes

The manual discusses remote parameter adjustment. It does not establish that parameter writes are permitted during active laser emission. It does not define safe update rates, atomic updates, failure behavior, or acknowledgments for active-emission changes.

Live parameter application is enabled only in the deterministic simulator and the separately labeled hardware-validation flavor. The hardware path requires explicit experimental-mode confirmation, verified read-back, and all validator gates. Manufacturer confirmation remains required before a production release.

## Original application dynamic-update behavior

Static analysis shows that `SettingsViewModel.PowerSetAsync`, `FrequencySetAsync`, and `DutyCycleSetAsync` check the Bluetooth connection and the value range. They do not check the `REN` enable state before sending `WCPR`, `WCMF`, or `WCDC`. Each method immediately sends the matching read command. `AfterSaleSettingPage` keeps these actions on the same screen as the enable action. This supports an inference that the original interface permitted parameter updates while enabled. It does not prove the physical effect or safe update behavior.
