# Implementation guide for Claude

Read `AUDIT.md` first. This handoff concerns Gathering commit `4de7a183f7f6e542d379d804eea060008497127f`. Inspect the current diff before working: later commits may already address a ticket. This package contains recommendations and an audit-only diagnostic probe, not completed fixes.

## Working constraints

- Keep Minecraft 1.21.1, both supported loaders, manual tabletop behavior, hidden-information boundaries, save compatibility, and existing ownership safeguards intact.
- Do not change textures or other hand-authored art. Use existing rendering primitives for previews and feedback.
- Preserve the first-sit client-only tutorial. Do not recreate a separate mandatory practice table.
- Keep Create/Aeronautics integrations optional; essential instructions and screens must work without them.
- Use small behavior-focused changes. Do not rewrite the mod, chase a line-count target, or add an abstraction without a production caller.
- Run the repository's canonical gate. A green helper test is insufficient: trace production input → server decision → visible response, and test that full path where it changes.

## Slice 1: Event UI and accessibility — PQ-01 to PQ-04

**Existing files to inspect/change as needed:**

- `common/src/main/java/dev/gathering/client/EventScreen.java`
- `common/src/main/java/dev/gathering/client/SettingsScreen.java`
- `common/src/main/java/dev/gathering/client/ClientSettings.java`
- `common/src/main/java/dev/gathering/client/GuiText.java`
- `core/src/main/java/dev/gathering/core/ui/SettingsLayout.java`
- `core/src/main/java/dev/gathering/core/ui/DeckScreenLayout.java` — existing pattern to study, not an instruction to modify it
- `core/src/main/java/dev/gathering/core/tournament/Tournament.java`
- `common/src/main/java/dev/gathering/network/EventViewPayload.java`

**Possible new files, explicitly proposals:**

- `core/src/main/java/dev/gathering/core/ui/EventScreenLayout.java`
- `core/src/main/java/dev/gathering/core/ui/EventActionAvailability.java`

Keep Minecraft widgets in common/client. A pure layout helper should take dimensions, content metrics, and accessibility preferences and return geometry. Availability should derive from authoritative domain rules or server-supplied presentation capabilities; never trust it as an authorization check.

Preserve logical focus and selection across updates. Recompute structure only when necessary; update ordinary labels/status in place. Confirm cancellation/drop consequences deliberately, without making routine drawing or other reversible tabletop actions confirmation-heavy.

**Required behavior evidence:**

1. Actual event control bounds grow at 150/200% control scale.
2. Header, status, report options, standings, and host controls remain legible at enlarged text, with reachable overflow.
3. Keyboard focus survives a preference change and an unchanged event snapshot.
4. Focus falls back predictably when its action ceases to exist.
5. Every phase and viewer role shows applicable actions and useful reasons for unavailable ones.
6. Server guards reject invalid/forged transitions independently of presentation.

Start by adapting `evidence/PolishAuditScene.java` into meaningful assertions in the existing client test infrastructure. The supplied probe logs and photographs the bug; it is not a passing regression test and deliberately completes even when it observes the current defects. Do not copy its synthetic fixture into production.

## Slice 2: Action outcome contract — PQ-05

**Existing files:**

- `common/src/main/java/dev/gathering/network/EventActionPayload.java`
- `common/src/main/java/dev/gathering/server/events/EventViews.java`
- `common/src/main/java/dev/gathering/server/events/Events.java`
- `common/src/main/java/dev/gathering/server/events/EventPrizes.java`
- `common/src/main/java/dev/gathering/client/EventScreen.java`
- `common/src/main/java/dev/gathering/client/PendingWork.java`

**Possible new file:** `common/src/main/java/dev/gathering/network/EventActionResultPayload.java`.

Add correlated results with localized, useful refusal reasons. Follow existing protocol/versioning and both-loader registration conventions. Make every pending state terminate on success, refusal, disconnect, screen dismissal, or an explicit uncertain timeout state. A timeout cannot authorize replaying an item transfer. Keep pending maps bounded and ensure repeated/stale messages cannot duplicate mutations or resolve another request.

Test delayed responses, duplicate clicks, out-of-order responses, host departure, stale phase, full inventory, and reconnect. Verify the visible status on the real screen, not just a callback in a unit test. Keep private information out of shared result payloads and logs.

## Slice 3: Integration qualification and CI — PQ-07/PQ-09

**Existing entry points:**

- `neoforge/build.gradle` — existing pack runs
- `common/src/main/java/dev/gathering/platform/WorldSpace.java`
- `neoforge/src/main/java/dev/gathering/neoforge/compat/SableWorldSpace.java`
- `common/src/main/java/dev/gathering/server/TableCustody.java`
- `tools/gate.sh`
- `docs/pack-authors.md`
- `docs/working-record.md`

**Proposed files:**

- `.github/workflows/verify.yml`
- `.github/ISSUE_TEMPLATE/bug.yml`
- `docs/compatibility-matrix.md`

Reuse the pack run profiles; do not silently add optional mods to every standard test run. Pin versions and record artifact provenance. Correct mock-player negotiation if needed; do not disable integration tests to obtain green output. Separate pure/core, standalone loader, optional pack, and actual client evidence in the matrix.

The most important end-to-end scenario is moving a live table containing owned cards/stakes while a tournament references it, then rotating, reconnecting, disassembling, and restarting. Assert inventory conservation and exactly one owner/recovery location throughout. Test ordinary Create contraptions' intended refusal separately from Sable movement support. Test real render-pose picking and camera behavior with connected clients.

CI must not expose secrets or grant publishing credentials to pull-request code. Use reproducible permitted test fixtures, retain logs on failure, and report discovered counts. Do not claim a pack is qualified when only the standard gate ran.

## Slice 4: Spatial and instructional polish — PQ-06/PQ-08

**Existing files:**

- `common/src/main/java/dev/gathering/block/TableBlockItem.java`
- `common/src/main/java/dev/gathering/block/TableBlock.java`
- `common/src/main/java/dev/gathering/block/TableClusters.java`
- `common/src/main/java/dev/gathering/client/Tutorial.java`
- `common/src/main/java/dev/gathering/client/TutorialPanel.java`
- `common/src/main/java/dev/gathering/client/TutorialDemo.java`
- `core/src/main/java/dev/gathering/core/tutorial/TutorialStep.java`
- `neoforge/src/main/java/dev/gathering/neoforge/compat/create/client/TablePonderScene.java`
- `neoforge/src/main/java/dev/gathering/neoforge/compat/create/client/DeskPonderScene.java`

**Possible new file:** `common/src/main/java/dev/gathering/client/TablePlacementPreview.java`.

First deliver a footprint and refusal explanation using existing assets. Use the same placement assessment as the server, with the client acting only as a preview. Do not request or load distant chunks to render it. Verify obstruction, permissions, joining limits, and supported moving coordinates.

Then deliver one complete contextual lesson, such as reporting and confirming a match. Require a production entry point, skip/replay, rebound keys, visible progress, and isolation from owned cards. Verify behavior both with Create and without it. Prefer one thoroughly usable task over a catalogue of untested scenes.

## Slice 5: Profiling and pack-author example — PQ-10

Start with `common/src/main/java/dev/gathering/server/events/EventViews.java` for event projection cost, and `docs/pack-authors.md` for the reward contract. Do not cache or redesign first.

Record a reproducible crowded-board baseline on named hardware and versions, then change only a measured bottleneck. If common public event projections are shared, compute recipient-specific fields separately and test information isolation explicitly. Include before/after percentiles, allocation and payload measurements, and functional equivalence. A shorter file is not evidence of lower runtime cost.

For a reward example, choose an actual installed mod version and verify its real trigger. Do not invent Cataclysm advancement identifiers or Create recipe contracts. Keep balance configurable and test absent dependencies/reload. Measure progression with real collection/play scenarios before changing reward rates.

## Completion report per slice

Report the exact revision and changed files, the player behavior changed, the production execution path, tests actually run with counts, screenshots when layout changes, and remaining limitations. Re-read nearby constructors, serialization/recovery paths, keybindings, and both-loader registrations after edits. Exercise combinations with unchanged systems: tutorial + deck intake, moving table + tournament, prize + full inventory, accessibility + refresh. Mark untested runtime claims explicitly.

Do not mark a ticket complete because its helper compiles or its description sounds finished. Close it when the user-facing behavior and its safeguards are demonstrated.
