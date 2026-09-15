# Gathering polish audit — 15 September 2026

Give Claude this whole folder or its ZIP.

- **AUDIT.md:** findings, comparison sources, priorities, and evidence limits.
- **CLAUDE-IMPLEMENTATION-GUIDE.md:** specific existing/proposed files, implementation slices, and acceptance checks.
- **evidence/:** baseline gate log, core test totals, successful client-probe log, six original screenshots, and the audit-only probe source and memory init file.

The four PQ-01–PQ-04 findings are reproduced presentation/input defects. PQ-05–PQ-10 are improvement or qualification work, with their source observations and limits stated separately. This package does not contain production fixes or a new full security audit.

## Reproduce the graphical observations

Use a disposable checkout of Gathering `4de7a183f7f6e542d379d804eea060008497127f` with Java 21 and the project's normal development dependencies. Run `tools/gate.sh` first with the documented MTGJSON fixture setup. The audit used a DMU fixture.

Copy `evidence/PolishAuditScene.java` to:

`neoforge/src/gametest/java/dev/gathering/client/PolishAuditScene.java`

From the checkout root run:

```sh
./gradlew :neoforge:runClient -PscreenRefactorScene=polish
```

The probe requires a graphical desktop and starts the real Minecraft client. It temporarily changes settings in that checkout's development run directory, opens production screens with a synthetic event view, logs `[polish-audit]` observations, saves `audit-*.png` in `neoforge/run/screenshots/`, and exits. It does not connect to a game server. Use a disposable run directory, not personal gameplay settings. The supplied memory init file is optional for constrained machines; it caps launched run heaps at 2 GiB.

A successful Gradle exit means the probe completed; it does **not** mean the observed UI behavior is correct. Read the logs and screenshots. Convert these observations into explicit regression assertions when implementing fixes.

The baseline gate ran before adding this probe. No production code or art changed. Reference repositories were studied from source; full gameplay/modpack qualification remains separate.
