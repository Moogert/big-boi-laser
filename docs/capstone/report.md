# Static analysis and replacement of a Bluetooth laser controller

## Abstract

This project investigates a Xamarin Android application and develops replacement controller prototypes for Android and Windows. Static analysis recovers application structure and selected command paths. A deterministic simulator permits software demonstrations without operating the physical device. The principal research result is an evidence-linked protocol inventory and an account of the limitations of reconstructing device behavior from client software alone. The Windows implementation remains incomplete, especially for response correlation and hardware-state verification.

## Research questions

1. Which operator actions lead to Bluetooth writes in the supplied application?
2. Which protocol properties can be supported by static evidence?
3. How can a replacement separate operator intent, validation, transport, and audit records?
4. Which conclusions require manufacturer information or physical measurement?
5. Can verified interoperability and troubleshooting records support a commercially useful product?

## Artifact and method

The supplied artifact is `com.BWTLaser.laser`, version 2.2.2, version code 32. The recorded size is 36,758,520 bytes. Its recorded SHA-256 is `E5F99A90DE8D6D4A104B2A37B2F88C3187CDB29EEA3DAE337B177BADD01B0DB5`. See `../apk-inventory.md` for metadata sources and permissions.

The workflow preserves the original artifact, decodes Android resources, extracts Xamarin application assemblies, and traces decompiled event handlers through protocol writes. Application assemblies are BWTLasers and BWTLasers.Android. Tool versions, extraction failures, and exact locations must accompany any reproduced analysis. Decompiled line numbers depend on the tool version and are not original source line numbers.

The analysis distinguishes observed strings and call paths from inferred physical meanings. A command name or UI label does not establish the actual physical output, interlock state, or allowable operating envelope.

## Findings

The command evidence matrix links `SettingsViewModel.PowerSetAsync`, `FrequencySetAsync`, and `DutyCycleSetAsync` to `WCPR`, `WCMF`, and `WCDC`. `SettingsViewModel.OpenLaser` links enable and disable requests to `WEN Y` and `WEN N`, followed by enable-state queries. `BleComManager.ConnectLaser` and the `DeviceInfoViewModel` identity handler support the `RBWT` identification path. The original also contains free-command and remote-command paths; the replacement excludes these interfaces.

These paths support the inference that the original client offered configuration writes without a demonstrated universal stopped-output prerequisite. They do not prove that a given firmware accepts changes during emission or that changing a parameter has the intended material effect. Frequency units, complete response grammar, and interlock mapping require further evidence. Consult `../command-evidence-matrix.md` rather than treating simulator responses as protocol documentation.

## Replacement design

The Android prototype uses Kotlin and Compose. The Windows prototype hosts an HTML interface on loopback and owns BLE operations in C#. The browser submits named operations rather than arbitrary device strings. A random process token protects API access. The Windows build currently targets the installed .NET 10 environment, a deviation from the initial .NET 8 proposal.

Both implementation and evidence need separate review. The presence of a typed command endpoint does not by itself establish complete range checks, fresh state, correct acknowledgments, or stop priority. Error handling must preserve uncertainty rather than convert communication failure into an output-off assertion.

## Evaluation and limitations

The prior Windows smoke test covered token rejection, a single simulated device, simulated identification, and simulated disable. It did not validate physical Bluetooth operation. Earlier Android builds and tests are recorded separately; the last attempted rebuild in the conversation did not finish within the command timeout. These historical results require reproduction for a final graded submission.

Review on 2026-10-30 identified Windows gaps: the semaphore is not a priority stop queue; received fragments are split per notification rather than accumulated into complete frames; parameter requests are copied into applied state without exact read-back comparison; output inference is incomplete; status freshness and the hold-to-arm flow are not fully enforced; audit metadata and localization are incomplete. The boolean output model cannot represent unknown state. Therefore, the existing executable must not be described as a validated hardware controller.

This review added centralized API failure responses, invalidation after command exceptions, strict named-enum validation, required-value checks, observation bounds, scan cleanup, and browser connection-failure reporting. These changes improve failure visibility but do not close the protocol or concurrency gaps.

## Marketability and startup hypothesis

The proposed initial customer is a small laser-cleaning business or equipment integrator that needs repeatable configuration and useful troubleshooting records. This is a customer hypothesis, not a measured market segment. The proposed value is a documented compatibility layer with reviewed parameter changes and exportable session records. No customer interviews, willingness-to-pay results, market-size estimate, or revenue claims are established in this draft.

The alternatives are the vendor application, manual operating records, and custom integration work. The prototype would need to demonstrate a measurable advantage in setup time, troubleshooting time, or record completeness. Dark mode and translation alone are weak differentiators. A maintained firmware compatibility matrix, regression suite, and documented integration process are more defensible assets, although a vendor could implement similar features.

Customer discovery should test the commercial opportunity before expanding development. This approach follows the National Science Foundation's (NSF, n.d.) emphasis on learning from potential customers, partners, and stakeholders. Proposed interviews should cover operators, owners who approve purchases, and equipment distributors. Ask for recent examples of configuration or troubleshooting problems, their cost, existing workarounds, and purchasing constraints. Do not count interest in a demonstration as willingness to pay.

| Hypothesis | Proposed experiment | Decision evidence |
| --- | --- | --- |
| Session records reduce troubleshooting effort | Compare the same simulator fault task with and without exported records | Completion time, diagnosis accuracy, missing information |
| Reviewed changes reduce entry errors | Counterbalanced simulator tasks using baseline and prototype interfaces | Error rate, completion rate, time, assistance required |
| Buyers will fund support | Present a defined pilot scope after problem interviews | Paid pilot commitment or a documented reason for rejection |
| Integration support is economically viable | Track engineering and support time for one verified firmware version | Cost per supported installation and recurring support burden |

A potential model is a per-installation license with paid compatibility and support services. Pricing is untested. Estimate contribution per installation as revenue minus onboarding labor, support labor, third-party fees, and other variable costs. Break-even volume is fixed cost divided by positive contribution per installation. No numerical forecast is justified until these inputs are measured.

Commercial release depends on verified protocol behavior, permission to distribute relevant artifacts, maintainable firmware support, and clear product responsibilities. The next milestone is a reproducible simulator demonstration and customer discovery, followed by a narrowly scoped integration pilot when the technical gaps are closed. The current prototype is not ready for a commercial hardware pilot.

## Functional acceptance strategy

The grading evidence should be a traceable requirement-to-test record, not screenshots of successful launches. Each test must identify the source revision, input, independent expected result, actual result, and retained log. Use captured or specification-derived response fixtures where available. Simulator responses invented by the implementation cannot independently validate the recovered protocol.

Tests must cover valid behavior and rejection behavior: split frames, duplicate responses, mismatched read-back, missing acknowledgments, disconnects, stale sessions, concurrent stop/apply requests, and audit-write failures. A failed or missing response must not produce a confirmed applied value or output-off claim. Measure stop scheduling relative to the current in-flight transaction and assert that no queued configuration write follows a stop request. These are proposed acceptance criteria; current code does not satisfy all of them. The supporting review record defines the required test evidence.

## AI assistance and authorship

OpenAI Codex assisted with code inspection, implementation changes, test planning, and report drafting. Course policy permits this use with attribution. AI output is treated as assistance rather than protocol evidence. The student must review the submission and independently verify functional claims. The submission guide provides space to record the student's completed reviews and tests without implying work already performed.

## Conclusion

Static client analysis can recover useful command paths and reveal risky architectural patterns. It cannot establish physical safety or substitute for a device protocol specification. The contribution is a reproducible investigation and a prototype with explicitly documented limits. Completion requires exact response parsing, independent validation of state transitions, meaningful fault-injection tests, and a reviewed device specification.

## References

*BWTLaser* (Version 2.2.2) [Android application package]. (n.d.). [Privately supplied artifact; package `com.BWTLaser.laser`; SHA-256 recorded in the Artifact and method section].

National Science Foundation. (n.d.). *About teams: NSF I-Corps*. https://www.nsf.gov/funding/initiatives/i-corps/about-teams

The APK entry uses the title in the author position because the publisher's bibliographic identity has not been verified. Do not infer a legal publisher from the application label. The APK is the primary source for the findings above; method names provide the locators. Supporting project evidence is included in `../apk-inventory.md`, `../command-evidence-matrix.md`, `../ble-protocol.md`, `../unexpected-activation-analysis.md`, and `../manufacturer-manual-evidence.md`. These are submission artifacts, not independent corroborating publications. Add verified manufacturer and tool bibliographic details when those sources are cited in the final report. Use APA hanging indents in the formatted reference list.
