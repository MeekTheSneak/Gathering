# Tournament audit handoff

Baseline: `63bd0410bd8f0c3a83f5c03ff2e3d068240fff9e` on `claude/new-session-beye3i`.

- `AUDIT.md`: ten reproduced findings, five additional investigation areas, fixes and acceptance criteria.
- `FINDINGS.csv`: prioritized implementation queue with exact source files and guard names.
- `audit-regression-tests.patch`: two new test files, **no production fixes**.
- `tests/`: the same test files laid out by repository path. Use either these files or the patch, not both.
- `evidence/`: original green gate, core probe output, final world probe output, and compact results.

Apply in an isolated checkout of the reviewed commit, or adapt carefully to newer code:

```sh
git apply --check /path/to/audit-regression-tests.patch
git apply /path/to/audit-regression-tests.patch
./gradlew :core:test --tests '*TournamentAuditTest'
./gradlew :neoforge:runGameTestServer
```

Use Java 21 and the repository's normal dependency/data setup. The audit used the existing cached Gradle runtime and a DMU MTGJSON fixture. It did not modify the Gradle build or production implementation.

**Expected on the reviewed, unfixed commit:** 2/2 core audit tests fail; the NeoForge run reports 480 tests, with the nine audit guards failing and the original 471 passing. The original gate passed with 471 NeoForge / 13 Fabric tests before this patch.

The world guards run in the disposable GameTest world. The prize failure test creates a blocking directory at its unique test event's save path and removes it in `finally`; it does not change filesystem permissions or target another event. The restart probe round-trips event state and discards its transient callback rather than restarting an actual server process.

After implementing fixes, run the guards and the full `tools/gate.sh`. Follow the additional multiplayer, recovery, and graphical acceptance checks in the audit. Passing these guards alone does not prove tournament correctness or modpack compatibility.
