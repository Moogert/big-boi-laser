# Safe Laser Controller Replacement

This repository contains Android and Windows controller prototypes and a reverse-engineering capstone draft. Start with `docs/capstone/report.md` for findings, limits, and the proposed test program.

The project does not claim that the replacement is safe for live laser operation.

The default Windows launcher uses a deterministic simulated device. Hardware transport is experimental and has unresolved response-validation gaps. Do not treat an executable build or simulator pass as hardware validation.

Read `docs/operator-guide.md` before simulator use.

Read `docs/developer-guide.md` before a code change.

The original vendor APK, decompiled work files, local audit logs, and generated binaries are intentionally absent from this repository. The evidence hash and analysis locations are documented in `docs/apk-inventory.md`. A researcher must obtain the source artifact independently and verify its hash before reproducing static analysis.

