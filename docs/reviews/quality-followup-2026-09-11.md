# Gathering — audit of the latest quality improvements

Reviewed September 11, 2026. Repository: [MeekTheSneak/Gathering](https://github.com/MeekTheSneak/Gathering).

**Verdict: meaningful progress, with four confirmed issues that should be fixed before these additions are considered polished.** The existing automated checks pass. Targeted probes at production boundaries nevertheless reproduce tutorial failures, an arrangement that changes a card's zone, and loss of a working reward definition after a malformed edit. An actual client screenshot also shows unusable menu text at supported accessibility settings.

This is a review of the **19 commits from `8aea9323` to `9fa4f737`**, comprising 113 changed files, 7,194 insertions and 273 deletions. The GitHub default-branch HEAD was checked again at the end and remained `9fa4f73786b03b4191c0e529482eee1e1d7b3050`. The review followed affected callers, lifecycle hooks, shared game authorization, persistence, documentation and tests beyond the changed lines. It is not a claim that every possible defect in the entire mod has been excluded.

**Provisional quality assessment for the reviewed additions: 7/10.** The architecture and coverage have improved, but the confirmed player-facing failures keep this below 8. This is a judgment of readiness, not a measured score for the entire mod. I continued into client and visual checks after the ordinary suite passed; the result remains below 8. Raising it now requires implementation fixes and revalidation. This audit follows the owner's review-first instruction and does not change production code or textures.

## Confirmed findings

All four are P2: concrete defects to resolve in the next corrective batch. They are separate from speculative improvements or features that have not yet been implemented.

| ID | Finding | Evidence | Primary files |
|---|---|---|---|
| QF-01 | Opening the tutorial's counter editor destroys the lesson; the editor also reads the wrong board | Two failing probes in the actual Minecraft client | `TableScreen.java`, `CountersScreen.java` |
| QF-02 | Applying a stale Tidy preview can move a card back from the graveyard | Failing actual-client handler probe using the shared game session | `TableScreen.java` |
| QF-03 | Malformed edits discard previously valid rewards during reload | Failing added NeoForge GameTest | `Rewards.java`, `RewardsGameTest.java` |
| QF-04 | Independent text/control sizes make action-menu labels overlap | Screenshot from the running client at text 200%, controls 75% | `ContextMenu.java`, `GuiText.java` |

### QF-01 — The tutorial's counter-screen path is broken in two places

**Where:** [TableScreen.java:954](https://github.com/MeekTheSneak/Gathering/blob/9fa4f73786b03b4191c0e529482eee1e1d7b3050/common/src/main/java/dev/gathering/client/TableScreen.java#L954), [TableScreen.java:4922](https://github.com/MeekTheSneak/Gathering/blob/9fa4f73786b03b4191c0e529482eee1e1d7b3050/common/src/main/java/dev/gathering/client/TableScreen.java#L4922), [CountersScreen.java:369](https://github.com/MeekTheSneak/Gathering/blob/9fa4f73786b03b4191c0e529482eee1e1d7b3050/common/src/main/java/dev/gathering/client/CountersScreen.java#L369), [CountersScreen.java:561](https://github.com/MeekTheSneak/Gathering/blob/9fa4f73786b03b4191c0e529482eee1e1d7b3050/common/src/main/java/dev/gathering/client/CountersScreen.java#L561).

**Player trigger:** Start the local pre-match lesson, play a sample card, then open its **Counters** editor through the ordinary card menu. This is a normal route to the lesson's counter action, not an unusual packet or damaged save.

`openCounters` replaces the table screen using `Minecraft.setScreen`. Minecraft calls the previous screen's `removed()`, which unconditionally stops the tutorial and clears `TutorialDemo` whenever that screen is in demo mode. Opening a child screen is therefore treated as abandoning the entire lesson. The client probe observed `demo running=false` immediately after the real table-to-counters transition.

There is a second, independent defect. `CountersScreen` looks up its board and seat through `ClientTableState`, while the local lesson intentionally lives in `TutorialDemo`. Its `change` handler returns without sending anything when that real-table seat is absent; its next tick closes the screen when that real-table board is absent. A separate probe kept a fresh demo alive and invoked the actual counter handler: the sample card still had zero +1/+1 counters when it should have had one.

**Impact:** A player who uses the menu can be kicked out of the teaching flow and fail to perform the counter step. A direct counter shortcut may work, so this does not establish that every route through the lesson fails. Fixing only `removed()` will leave the editor broken.

**Required repair:** Give the lesson a lifecycle that survives temporary child screens and ends on explicit lesson exit, completion, disconnect, or an unrelated screen transition. Pass a consistent board/seat/action context to child screens so they can operate on either the local lesson or the real table. Keep the existing protection that prevents demo actions from reaching a real table. Do not place simulated cards into real table state merely to satisfy these lookups.

**Acceptance:** In an actual client, complete the counter step using the menu, return to the same lesson, and continue. Also test a child-screen detour and return, explicit exit, Escape at the lesson root, disconnect, and successful handoff to the real table. Assert both lesson progress and isolation of the real inventory/table. Review other child screens for the same lifecycle and lookup pattern; their individual behavior was not all reproduced here.

### QF-02 — Tidy applies an old plan as new zone-changing moves

**Where:** [TableScreen.java:4865](https://github.com/MeekTheSneak/Gathering/blob/9fa4f73786b03b4191c0e529482eee1e1d7b3050/common/src/main/java/dev/gathering/client/TableScreen.java#L4865), [TableScreen.java:4886](https://github.com/MeekTheSneak/Gathering/blob/9fa4f73786b03b4191c0e529482eee1e1d7b3050/common/src/main/java/dev/gathering/client/TableScreen.java#L4886). The permissive public-zone behavior also follows from [Authorization.java:201](https://github.com/MeekTheSneak/Gathering/blob/9fa4f73786b03b4191c0e529482eee1e1d7b3050/core/src/main/java/dev/gathering/core/game/Authorization.java#L201).

**Reproduction:** Put two cards on your battlefield, select them and generate the arrangement preview. Before applying it, move one selected card to the graveyard. Apply the preview. The probe observed **zero cards remaining in the graveyard, expected one**: Tidy returned the card to the battlefield.

The preview stores card IDs and planned positions. Applying it checks for a seat and a nonempty plan, then emits an ordinary `CardMoved` to the battlefield for every stored ID. It does not recheck the card's current zone or whether the plan still describes the board. The shared manual-tabletop authorization allows ordinary moves from public zones; it cannot distinguish an intentional graveyard move from an obsolete arrangement command.

**Impact:** A convenience tool silently changes game state beyond arrangement when a board changes while a preview is open. Multiplayer updates make this especially relevant. Changed attachments or rotation are related stale-state cases worth testing, but the runtime reproduction here specifically covers the graveyard move.

**Required repair:** Cancel or recompute a preview when its relevant cards change, and revalidate before applying. To cover the network race after a client-side check, consider a narrowly scoped position operation with server-checked expected zone/session/relationship preconditions. Preserve intentional manual zone moves elsewhere; adding a broad Magic rules engine or restricting all public moves is not the solution.

**Acceptance:** Generate a preview, change a selected card's zone, then apply; the card must remain in its new zone. Cover changed attachment, tapped/rotated state, controller, removal and session end, plus a normal unchanged preview. Verify stale handling across actual server updates, not only a static plan test.

### QF-03 — A malformed reward edit loses the last good definition

**Where:** [Rewards.java:157](https://github.com/MeekTheSneak/Gathering/blob/9fa4f73786b03b4191c0e529482eee1e1d7b3050/common/src/main/java/dev/gathering/service/Rewards.java#L157), especially [Rewards.java:181](https://github.com/MeekTheSneak/Gathering/blob/9fa4f73786b03b4191c0e529482eee1e1d7b3050/common/src/main/java/dev/gathering/service/Rewards.java#L181). Existing test: [RewardsGameTest.java:100](https://github.com/MeekTheSneak/Gathering/blob/9fa4f73786b03b4191c0e529482eee1e1d7b3050/neoforge/src/main/java/dev/gathering/neoforge/test/RewardsGameTest.java#L100). Intended behavior: [pack-authors.md:53](https://github.com/MeekTheSneak/Gathering/blob/9fa4f73786b03b4191c0e529482eee1e1d7b3050/docs/pack-authors.md#L53).

**Reproduction:** Load a valid reward JSON and retain the working snapshot. Edit that same file into malformed JSON, then reload. The previous reward disappears: `audit_existing present=false`.

`readInto` records a per-file error and skips that file. `reload` then publishes `Map.copyOf(built)` even when errors were collected. It preserves the previous snapshot if listing the folder fails, but not if an existing definition fails to parse or validate. This contradicts the documented all-or-nothing reload behavior.

The existing regression adds an unrelated broken file beside an unchanged valid file. That proves the valid file can be read again; it does not prove preservation of the last good snapshot when the working file itself is damaged.

**Impact:** A normal editing mistake can disable a server's configured reward and the progression that depends on it until the file is corrected and reloaded. This finding concerns the available reward definitions; it is not evidence that already granted items are deleted.

**Required repair:** Build and validate the candidate snapshot separately. Publish it only when the reload satisfies the documented success contract; otherwise preserve the previous map and expose useful diagnostics. Preserve intentional removal of a definition during a successful reload. Distinguish a valid empty directory from an error according to the chosen documented policy.

**Acceptance:** Overwrite an existing valid file with malformed JSON and with a semantically invalid definition; verify exact previous-snapshot equality. Test a mixed reload containing one valid change and one invalid edit, a corrected reload, and successful deliberate deletion. Retain the new probe alongside broader coverage.

### QF-04 — Accessibility text scaling breaks menu layout

**Where:** [ContextMenu.java:41](https://github.com/MeekTheSneak/Gathering/blob/9fa4f73786b03b4191c0e529482eee1e1d7b3050/common/src/main/java/dev/gathering/client/ContextMenu.java#L41), [ContextMenu.java:106](https://github.com/MeekTheSneak/Gathering/blob/9fa4f73786b03b4191c0e529482eee1e1d7b3050/common/src/main/java/dev/gathering/client/ContextMenu.java#L106), [ContextMenu.java:169](https://github.com/MeekTheSneak/Gathering/blob/9fa4f73786b03b4191c0e529482eee1e1d7b3050/common/src/main/java/dev/gathering/client/ContextMenu.java#L169), [GuiText.java:298](https://github.com/MeekTheSneak/Gathering/blob/9fa4f73786b03b4191c0e529482eee1e1d7b3050/common/src/main/java/dev/gathering/client/GuiText.java#L298).

**Reproduction:** Set text size to **200%** and control size to **75%**, then open a card action menu. These are accepted settings. The captured client image shows large labels such as Tap and Untap spilling across neighboring rows, mixed with smaller labels. The menu is hard to read reliably.

Rows are sized from control scale alone: a base 12-pixel row becomes 9 pixels. `GuiText` can independently draw a fitting short label at twice its native size. Width fitting does not account for row height, and the menu measures columns and shortcut space using unscaled font widths. Longer labels can fall back to native scale, producing inconsistent sizes within the same menu.

**Required repair:** Measure rendered text and lay out the menu from the same metrics. Row height must accommodate the chosen text size as well as the requested minimum hit target. Measure label and shortcut space consistently. If the result cannot fit, provide bounded wrapping, scrolling or another readable layout. Keep text and interaction settings independent without allowing text to occupy neighboring targets.

There is also a related source inconsistency to address in that repair: `GuiText.width()` predicts a scaled/clamped width, but `drawFitted()` sometimes falls back to native width. Centered labels can therefore be positioned using a width different from the one actually drawn. This was identified by reading the two methods, not by a separate centering screenshot.

**Acceptance:** Inspect actual rendering at 75%, 100% and 200% text/control combinations, including large text with small controls, small windows, changed Minecraft GUI scale, long labels and shortcuts. Confirm readable labels and matching click targets. A test that only proves each preference is read cannot establish this behavior.

Screenshot: `Gathering-quality-followup-menu-scaling.png` in this handoff. It is a raw client capture; no mod textures were changed.

## What improved, and the earlier review's status

| Earlier item | Finding in this revision |
|---|---|
| QP-01: owned deck consumed by practice | Earlier regression now passes; intake guards and retirement handling are present. |
| QP-02: orphaned practice session after disconnect | Shared disconnect cleanup is present and the earlier regression passes. |
| QP-03: starter booster loses chosen color during recovery | Recovery carries the color field and the earlier regression passes. |
| QP-04: tutorial restart misses the first action | Earlier regression passes; the new demo has its own restart handling. This does not cover the QF-01 screen transition. |
| QP-05: hardcoded/rebound keys and misleading help | Production dispatch/help changes address the cited paths; the key scanner passes. An exhaustive physical-key playtest was not performed. |
| QP-06: pending-work helper not integrated | Import and builder screens now create, resolve, display and forget pending requests. This closes the earlier missing-production-caller gap; broad slow-network playtesting remains. |
| QP-07: replace separate practice table with pre-match local lesson | The local lesson, first-sit entry and network isolation are implemented. The intended direction is followed, but QF-01 prevents calling the normal screen flow complete. |

Pinned counters/tokens, reachable settings, ownership cues, arrangement previews, acquisition hints, server profiles and configurable rewards are substantive additions. The separation between core logic, shared Minecraft code and loader adapters remains useful. The reward schema and documentation are a practical start for pack authors, subject to QF-03.

The new work also acknowledges a real performance cost: bulk actions still build/broadcast a full view per card and recipient. That is an existing measured limitation, not a newly discovered regression in this report.

## Further quality work after the confirmed defects

1. **Bound Tidy consistently with other bulk actions.** Its new apply loop bypasses the existing `BulkLimit` path and can submit every planned card. Share the bound or pace the work with clear feedback. Measure a crowded four-player board with spectators before choosing a batching strategy; retain intermediate game-event and privacy semantics.
2. **Finish inspection of buried cards.** The repository's own `quality-after.md` acknowledges that the chooser reaches a buried card's menu while full-size reading still follows the top card under the pointer. Keep the selected inspection target through that flow. A chooser is only part of the Commander-board objective.
3. **Keep production-boundary regressions.** The misses here concern screen replacement, a live board changing after planning, replacing an existing persisted definition, and rendering combined settings. Add those tests alongside existing helper tests. Static scanners remain useful structural checks; they cannot establish that the feature works for a player.
4. **Reconcile completion records.** Update `docs/working-record.md` and the backlog against these results. The record still contains conflicting statements about remaining work and the previously withdrawn late-scene issue. A single current status with dated evidence is more useful than accumulating contradictory completion prose.

## Verification performed

| Check | Result |
|---|---|
| Unmodified `./gradlew verify --console=plain` | Passed; 41 tasks, 58 seconds |
| Core test XML | 1,599 reported tests: 1,598 executed, 1 skipped, zero failures/errors |
| Existing NeoForge GameTests | 399 required tests passed |
| Existing Fabric GameTests | 10 required tests passed |
| Four earlier `QualityReviewGameTest` regressions | Included in the passing NeoForge baseline |
| Fourteen repository static checks | All exited zero |
| Added reward GameTest | Failed as expected; run had 400 tests, with one required failure |
| Actual NeoForge client handler probes | Three explicit failure markers: stale arrangement, demo teardown, demo counter lookup |
| Visual check in actual client | Overlapping menu labels reproduced and captured |
| Art check / changed asset paths | 2,152 art pieces checked, none changed/missing/new; no PNG/OGG changes in the reviewed diff |

Runtime: Minecraft 1.21.1, NeoForge 21.1.248, Java 21.0.12.1; repository-pinned Fabric loader/API for the Fabric gate. The tests used an isolated archive, disposable development world/config and a DMU MTGJSON fixture. Production files in the original clone were not edited. Two audit-only Java harnesses were added to the disposable archive after the unmodified baseline passed.

The client harness invokes real screen/game handlers using reflection and captures the real framebuffer. It is stronger evidence than a helper-only test, but **not a full human playthrough, physical mouse/keyboard test or multiplayer latency run**. It starts the development scene only to obtain a client/world, runs these probes, and exits early. It does not complete the full 315-step scene. The client intentionally exits normally even when a probe prints FAIL, so its Gradle `BUILD SUCCESSFUL` must not be interpreted as a passing probe result; inspect the explicit markers in the supplied log.

The first sandboxed graphical launch stalled at the window service. A separately approved graphical run completed and reproduced the failures; that environment problem is not reported as a mod defect.

No exact Create/Aeronautics/Cataclysm combination was installed and played for this audit. Both loader gates passing does not establish compatibility with those mods. Moving-structure behavior, boss reward balance, full-catalog economics, four-player usability and long-session performance remain unverified in their target environments.

## Developer handoff

Start with QF-01 and QF-02, then QF-03 and QF-04. For each, reproduce the supplied failure, implement the narrow repair, run the boundary test and relevant existing gate, and report the observed result. Do not mark an item closed from a new helper test alone. No textures need changing for any of these repairs.

Use `Gathering-quality-followup-evidence.zip`: it contains this review, the findings CSV, both audit harnesses at their repository-relative destinations, baseline/static/probe logs, the screenshot, core test XML, and reproduction instructions. These harnesses are development evidence, not files to ship in a release JAR.
