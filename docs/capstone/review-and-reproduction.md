# Review record and reproduction guide

Review date: 2026-10-30.

## Error-handling coverage

| Area | Change or finding | Remaining work |
| --- | --- | --- |
| Windows HTTP API | Structured failures; invalidate verification after exceptions | Separate validation failures from transport faults; request cancellation |
| Command names | Reject numeric enum aliases | Independent protocol-boundary allowlist tests |
| Parameters | Reject absent values and invalid waveform input | Verified device limits and exact read-back |
| BLE scan | Stop watcher in finally | Discovery and disconnect fault injection |
| Browser | Display unknown output on uncaught request or socket failures | Timeouts, safe DOM rendering, stale-status handling |
| Audit | Existing append-only JSONL | Concurrent writes, disk-full handling, repeat export, export snapshot consistency |
| Android | No new exhaustive audit claimed | Review coroutine exceptions, lifecycle cancellation, permissions, and audit I/O |
| Release artifacts | Previously compiled executables exist | Rebuild after changes; do not confuse old binary with reviewed source |

## Reproduction

Work from the repository root. Hash `evidence/original.apk` with `Get-FileHash -Algorithm SHA256`. Compare against the inventory before and after analysis.

Record `dotnet --info`, then run `dotnet build windows-controller/LaserController.Windows.csproj -c Release`. Run the controller with `--simulate --no-browser`. Record the URL locally, but redact its token in submitted logs. Test missing-token rejection, malformed JSON, numeric command names, missing parameter values, invalid waveform values, and empty observations. Confirm failures do not assert physical output off. No live hardware is needed for these checks.

For Android, record Java and Gradle versions and run the repository's unit-test and simulator build tasks. Retain complete logs and exit codes. An elapsed tool timeout does not establish test success or failure. Record localization and accessibility checks separately from unit tests.

## Required future fault tests

All cases below are planned unless an attached execution record states otherwise. AI-generated test code must be reviewed for an independent expected result. A test that repeats the implementation's assumptions is not protocol validation.

- Permission denied, no adapter, missing service, notification subscription failure.
- Partial and duplicate frames, malformed values, mismatched read-back, timeout, disconnect.
- Concurrent apply and stop, queued requests from an old session, cancellation during write.
- Unwritable audit directory, disk full, simultaneous audit events, repeated exports.
- Browser disconnect, server failure, invalid token, large payload, unexpected device names.

For each test record: source revision/hash, environment, setup, input, expected result, actual result, log location, and pass/fail/not-run. Leave actual results blank until execution.

| Requirement | Minimum acceptance evidence | Current submission status |
| --- | --- | --- |
| Exact command allowlist | Reject unknown names, numeric aliases, and arbitrary payload fields without TX | Tests required |
| Correct framing | Every possible split of a representative CRLF response yields one complete frame | Implementation gap; tests required |
| Verified apply | Mismatch or timeout leaves value pending; subsequent changes are not sent | Implementation gap; tests required |
| Stop priority | Stop removes queued configuration work; record actual ordering and latency | Implementation gap; tests required |
| Session isolation | Old-session responses and queued commands cannot update the new session | Tests required |
| Truthful status | Disconnect or stale status yields unknown; no false stop confirmation | Implementation gap; tests required |
| Audit integrity | Each action and TX/RX has a record; disk failure is visible; repeated exports succeed | Implementation gap; tests required |
| Interface robustness | Four locales, keyboard tasks, scaling, network failure, preserved pending edits | Tests required |

Report counts of passed, failed, and not-run cases separately. Retain failures in the submission. Publish known defects with reproduction steps and their effect on the proposed product. A simulator-only result must remain labeled simulator-only.

## Demonstration outline

1. Identify the original artifact and show its hash.
2. Trace one operator action to a decompiled method and transmitted command.
3. Explain observed facts versus inferred physical meaning.
4. Launch the simulator and demonstrate review/apply and audit capture.
5. Demonstrate rejected input and a communication failure.
6. Present unresolved findings and next experiments.

## Submission review

Use APA citations and include AI attribution under the stated course policy. Target 8–12 pages and verify the final document is at most 20 pages. Confirm authorship, redistribution permissions, citation consistency, redaction of private data, and correspondence between submitted source and binaries. Claims of compliance, manufacturer approval, or successful hardware validation require actual supporting records.
