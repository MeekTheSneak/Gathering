# Gathering: review of quality improvements in progress

Reviewed September 11, 2026. Repository: [MeekTheSneak/Gathering](https://github.com/MeekTheSneak/Gathering).

**Verdict: the direction is good, but stabilize the implemented features before adding the next phase.** The shared action catalogue, real key mappings, menu-backed action search, offline practice board, and persisted recents are useful improvements. This is more than cosmetic work. However, I reproduced four integration defects, including permanent loss of an owned deck, and found two further issues with controls and the claimed completion of pending feedback.

Provisional quality for this reviewed increment: **7/10**. This is a judgment of implementation readiness, not a measured score for the entire mod or its future feature set. I would not call it an 8/10 release while the collection-loss path is present. The remaining Commander, progression, pack-author integration, and accessibility phases are unfinished work, not regressions merely because they are not implemented yet.

## QP-07 · Owner-requested change · Replace practice with a pre-match tutorial overlay

**This is the requested implementation direction for Claude.** The owner wants the teaching table to appear as a short interactive overlay when a player first sits at a normal table, before they enter the real tabletop controls. They should not have to find, build, or start a separate practice table. This is a local demonstration before play, not instructions that make the player practice on their actual match.

This decision supersedes recommendations to retain or expand the server-backed practice-session design in QP-01, QP-02, and QP-04, and the corresponding practice architecture in the earlier quality project guide. Those findings remain evidence about the reviewed revision. Their item-preservation, cleanup, and navigation requirements still apply to the replacement and migration. This document update adds requirements; it does not claim the replacement has been implemented or tested.

### Player experience

- On the first successful sit-down, show the introductory table overlay before enabling the normal tabletop screen. Provide a clear Skip control immediately. It should work at an empty table without a loaded deck, loaner, or card download.
- Teach draw → play → tap → add a counter → inspect a face-up demonstration opponent card → pass, one short prompt at a time. Use interactive sample cards and the real controls, including current key bindings and equivalent mouse/menu actions. Aim for roughly a minute; do not force timed progress.
- Keep the actual game available to everyone else. The overlay must not start, pause, end, or change a real match. If other players are already playing, the newcomer can skip immediately and join their current view.
- Finish or skip into the normal table view, using the latest authorized server state. If the table or seat is no longer available, close safely to the world; never recreate a stale session or auto-place a real deck.
- Remember offered/skipped/completed locally so automatic onboarding happens once per client profile, not every table or world. Keep Replay tutorial available in the normal table menu. A disconnect aborts the current demonstration without marking it completed; manual replay remains available.
- Provide Restart and instruction review. Restart restores the entire sample scenario, including library, hand, counters, tapping, and step baselines. It must work even after the sample library is exhausted.

### Coding direction

Reuse table drawing, picking, action descriptions, menu builders, and key mappings where practical. Extract a small display/input interface if needed so normal play and the demonstration share controls while using separate state and action destinations. Proposed names such as `TutorialDemoState` or `TutorialActionSink` are suggestions, not files that already exist.

The demonstration owns fresh in-memory sample state, with no physical blocks, server practice session, actual seat allocation, or persistent demonstration world. It may reuse pure game events or the core fold locally where that avoids duplicate behavior. It needs only a deterministic six-action scenario, not an AI opponent or Magic rules engine.

Make the action destination explicit. Tutorial actions must go only to the local demonstration model; they must never fall through to `ClientNetworking.send`, a real table action callback, inventory handling, or deck intake. Do not build tutorial controls from closures already bound to a real match. Keep local sample IDs/caches separate from live board IDs, and never copy real hidden cards into the sample state. Normal networking may continue in the background, but cannot update or advance the demonstration.

Reuse `TutorialProgress`, `TutorialPanel`, `TableShortcuts`, and the shared action catalogue as appropriate. Refactor `Tutorial` and the relevant `TableScreen` entry points so the demonstration does not depend on a `BlockPos`-keyed server board. Advance from successful local demonstration transitions or actual sample-card inspection. Baselines must be established before the next input. Use client ticks or monotonic elapsed time for transitions, not frame counts.

Use existing art and simple rendered labels for sample cards. **Do not change textures.** The overlay needs to remain readable at small window sizes and large GUI/text scales, with keyboard access and reduced-motion behavior. Starter items remain a separate server-controlled collection feature: opening, completing, skipping, or replaying this local overlay must not itself grant items or change starter eligibility. Preserve the intended reward policy separately, and do not trust a client completion flag as authority for a reward.

### Retire the old practice path safely

Remove normal UI entry points that create server practice sessions. Disable or safely retire old START payload handling as well; hiding the button alone leaves the old intake/lifecycle paths reachable. Remove obsolete practice logic only after inspecting its callers and save compatibility.

Existing saves may contain practice flags, demonstration occupants, or held owned decks. Provide idempotent migration/cleanup that removes temporary sessions and demonstration seats while preserving real held property exactly once. Never mint items from generated sample cards. If old data does not reliably distinguish property provenance, preserve it for recovery instead of discarding it or automatically converting every sample to an item. Ordinary tables, held decks, and active matches must remain intact.

### Acceptance checks for Claude

1. A first-time player sits at an empty normal table and completes all six sample actions offline, without placing or consuming a deck. Skip and Replay tutorial work; reconnect/relaunch does not cause repeated automatic offers or fake completion.
2. Every tutorial action produces zero gameplay mutation packets and zero changes to real inventory, held decks, seats, session state, or rewards. Test this with a live table in the background and a send-spy/assertion on tutorial action routing.
3. A second player's real match continues while the overlay is open. A real board update, table removal, seat change, disconnect, or world change cannot advance the sample tutorial or route sample actions into the real game.
4. Restart after exhaustion and Next/Back followed immediately by an action behave correctly. Remapped controls, mouse alternatives, and displayed prompts agree. Inspection addresses the demonstration opponent's visible card.
5. Completion/Skip releases the input capture and shows the latest real view; small windows, large text, and reduced motion remain usable. Closing the overlay never emits the old practice STOP operation against a real match.
6. Migration tests preserve owned property exactly once, remove old demonstration state, and are safe to run twice. Ordinary multiplayer saves and legacy booster recovery still pass their tests.

The supplied practice GameTests reproduce the old implementation and are not a requirement to retain its obsolete APIs. When the old feature is removed, replace those fixtures with equivalent overlay-isolation and legacy-save migration tests. Keep the QP-03 colored-booster recovery regression and other unaffected coverage. Update Q06–Q08 in the project backlog to this new design, with implementation and human validation reported separately.

## Revision and review boundary

- Reviewed head: [`8aea9323`](https://github.com/MeekTheSneak/Gathering/commit/8aea932386227d034928c1d9313abbaf6fe55c8b). A final remote HEAD check still matched this revision.
- Compared with the previous review baseline: [`3ff8f34a`](https://github.com/MeekTheSneak/Gathering/commit/3ff8f34ae5c5fd2573e2fb95e85b772ea8bc8fcc). The range contains 11 commits, 130 changed files, 13,466 added lines, and 956 removed lines, including substantial documentation and artwork manifests.
- Minecraft 1.21.1; NeoForge 21.1.248 is the pinned primary target. Fabric is also built and its loader tests were run. Local validation used Java 21.0.12.1 and Gradle 8.14.3.
- Reviewed new feature plumbing, input paths, practice lifecycle, collection boundaries, persistence, and the related tests. This is a focused review of the quality increment, not a fresh certification of every earlier audit finding.
- A separate archive was used. No production source, texture, or sound was changed; no commit or push was made. Four review-only tests were added to that archive.

## What is working better

The action palette builds from existing menu entries and reuses their callbacks, reducing the chance that search and right-click actions diverge. Search excludes rows without catalogue IDs rather than indexing hidden card identities. Both loaders register the same shortcut definitions. Recents store bounded names with a server scope rather than retaining live card-instance IDs.

At the reviewed revision, practice is a real server session with offline custom paper stock and an explicit demonstration seat. Its offline teaching content can be reused. The owner has since requested replacing its server lifecycle with the local overlay specified in QP-07.

Previous regression tests have been retained in the repository and pass. The new shared server-tick scheduler also replaces the earlier recursive rescheduling pattern, and the replay changes transfer worker-owned state without reading the access-ordered server map from a worker. Those are useful structural corrections.

Artwork handling respects the constraint: the revision comparison contains no PNG or OGG changes, and `artcheck` reports **2,152 pieces checked, 0 changed, 0 missing, 0 new**.

## Validation performed

| Check | Result |
|---|---|
| Unmodified `./gradlew verify` | Passed; builds, architecture checks, data generation, and both loaders' game tests |
| Core XML results | 1,544 reported tests: 1,543 passed, 1 skipped, 0 failures/errors |
| Existing NeoForge GameTests | 362/362 passed |
| Fabric GameTests | 10/10 passed |
| Eleven Python static checks | All passed |
| NeoForge after adding four review probes | 366 total: existing 362 passed; all four new acceptance tests failed as described below |

The static checks were langcheck, doccheck, scenecheck, plotcheck, gesturecheck, spritecheck, statecheck, savecheck, runcheck, texturecheck, and artcheck. Their success does not establish that every named lifecycle method is actually wired into the right path; QP-02 and QP-06 illustrate that limitation.

No graphical client run, human first-game trial, four-human Commander session, or Create/Aeronautics/Cataclysm modpack session was performed in this review. Repository-written reports of scripted client runs are not counted as my own verification. Background card-service warnings, including rate limiting, appeared in logs; the four new failures were local assertions independent of those downloads.

## Actionable findings

P1 means fix before release because player property can be lost. P2 means correct before treating the feature as finished. QP-06 is explicitly an implementation gap, not an observed runtime failure in an integrated feature.

### QP-01 · P1 · Practice can consume and destroy an owned deck

**Evidence:** Reproduced by added NeoForge GameTest.

A practice library starts with 20 cards. Draw all 20, then put a physical deck down at the same seat. `commitDeck` only checks whether the library is nonempty; it does not reject practice sessions. It loads the deck, records it as held property, and shrinks the item. When practice ends, `giveBack` discards every held deck solely because the table is marked practice. The owned item disappears.

The probe uses a physical deck containing custom paper stock to avoid external metadata. It follows the public `TableBlock.putDown` path with the stack in the player's main hand. After stopping practice, there are **zero matching decks in inventory or on the floor**, where one existed before. No forged packet, administrator action, or production-code modification is needed.

**Fix:** Refuse real deck intake at the shared commit boundary while practice is active, including loaners and delayed callbacks. Check eligibility again when asynchronous validation completes. Keep generated practice cards separate from owned inventory, and give held property explicit provenance if the table can ever contain both. Inspect existing saved practice tables for real held decks and preserve that property during migration. Do not simply remove the return guard: that would weaken protection against creating practice items.

**Acceptance:** The supplied test passes. Add a second-player joining case, the loaner path, delayed validation across a session change, and break/end/reload cases. In every case, owned decks remain exactly once and generated practice cards never become collection items.

**Source:** [TableBlock.java:704](https://github.com/MeekTheSneak/Gathering/blob/8aea932386227d034928c1d9313abbaf6fe55c8b/common/src/main/java/dev/gathering/block/TableBlock.java#L704), [TableSessions.java:310](https://github.com/MeekTheSneak/Gathering/blob/8aea932386227d034928c1d9313abbaf6fe55c8b/common/src/main/java/dev/gathering/block/TableSessions.java#L310).

**Reproduction:** `QualityReviewGameTest.practiceMustNotDestroyAnOwnedDeck`.

### QP-02 · P2 · Disconnect leaves an orphaned practice game

**Evidence:** Reproduced through the shared disconnect hook.

Start practice and disconnect. Both loaders call `PlayerGone.left`, but that hook never stops the player's practice session or releases the demonstration seat. The supplied test invokes this actual shared hook and finds the practice game still present. Meanwhile, client disconnect cleanup calls `Tutorial.clear`, losing the local tutorial association.

On reconnect, the table still has a game, so requesting a new practice session is refused. The client no longer knows it was teaching at that table. The practice flag is also persisted, so a restart does not inherently repair the mismatch. Existing practice tests exercise explicit stop and deck return; they do not exercise disconnect cleanup.

**Fix:** Give a practice session an explicit learner, dimension/table identity, and lifecycle. Add idempotent cleanup to the shared disconnect path, and settle its demonstration seat and temporary session together. Define what should happen on world reload or chunk unload. If resuming practice is the intended design, implement an explicit server-confirmed resume handshake instead of retaining server state while discarding the client state. Do not end ordinary multiplayer games on disconnect.

**Acceptance:** The supplied disconnect test passes for cleanup policy; add both loader hook coverage and save/reopen coverage. Confirm another player can use the table afterward and that ordinary sessions survive logout as before.

**Source:** [PlayerGone.java:34](https://github.com/MeekTheSneak/Gathering/blob/8aea932386227d034928c1d9313abbaf6fe55c8b/common/src/main/java/dev/gathering/server/PlayerGone.java#L34), [PracticeTable.java:187](https://github.com/MeekTheSneak/Gathering/blob/8aea932386227d034928c1d9313abbaf6fe55c8b/common/src/main/java/dev/gathering/server/PracticeTable.java#L187), [Tutorial.java:293](https://github.com/MeekTheSneak/Gathering/blob/8aea932386227d034928c1d9313abbaf6fe55c8b/common/src/main/java/dev/gathering/client/Tutorial.java#L293).

**Reproduction:** `QualityReviewGameTest.disconnectMustCleanUpPractice`.

### QP-03 · P2 · Recovery strips the chosen color from a starter booster

**Evidence:** Reproduced at the production receipt/recovery boundary.

The new `PackComponent` includes a color, and normal opening passes it through. The durable receipt still records only set and product kind. If an opening is interrupted before settlement, recovery reconstructs the pack through the two-argument constructor and loses the color restriction.

The probe makes the exact `Owed.opening` call used by `PackItem.use`, leaves that receipt pending, and runs recovery. A `j25/default/W` booster returns as `j25/default/""`. This is a receipt-boundary reproduction, not a claim that a real process was forcibly crashed during the test. The recovered pack can now open in any color, breaking the player's two-color starter choice.

**Fix:** Persist the full sealed-product identity, including optional color, in opening receipts and pack debts. Keep readers compatible with existing uncolored receipt lines. Preserve the same fields through refunds, disconnect recovery, and migration; audit every constructor/copy site when an item gains identity fields.

**Acceptance:** The supplied round-trip test passes, older receipt formats remain readable, and recovery preserves set, kind, and color for both chosen starters without awarding extra packs.

**Source:** [PackItem.java:54](https://github.com/MeekTheSneak/Gathering/blob/8aea932386227d034928c1d9313abbaf6fe55c8b/common/src/main/java/dev/gathering/item/PackItem.java#L54), [Owed.java:89](https://github.com/MeekTheSneak/Gathering/blob/8aea932386227d034928c1d9313abbaf6fe55c8b/common/src/main/java/dev/gathering/server/Owed.java#L89), [Owed.java:253](https://github.com/MeekTheSneak/Gathering/blob/8aea932386227d034928c1d9313abbaf6fe55c8b/common/src/main/java/dev/gathering/server/Owed.java#L253).

**Reproduction:** `QualityReviewGameTest.recoveredStarterMustKeepItsChosenColor`.

### QP-04 · P2 · Tutorial restart can ignore the first successful action

**Evidence:** Reproduced with confirmed server board updates.

`restart`, `back`, and `forward` clear `turnWhenTheStepBegan`. The next board update then becomes a new baseline and returns without checking whether the action completed the instruction. If the learner acts before another board refresh arrives, that successful action is ignored.

The probe first proves a normal confirmed draw advances DRAW to PLAY. It then restarts the tutorial, draws one card, and feeds back the confirmed board. The instruction incorrectly remains DRAW. This is timing-dependent in the client: an unrelated refresh can conceal it. Restart also resets instructions without replenishing the 20-card practice library, so repeated practice can eventually ask for an impossible draw.

**Fix:** Retain the last confirmed board and establish the new step's baseline when navigation happens, before the next action. For a true restart, reset the practice scenario through a validated server operation, or provide another explicit way to restore its playable starting state. Do not treat the first post-action board as a pre-action baseline.

**Acceptance:** The supplied test passes; add Next/Back followed immediately by an action, delayed/refused actions, and restart after exhausting the library. Advancement must depend on confirmed relevant activity and must not require repeating a successful action.

**Source:** [Tutorial.java:139](https://github.com/MeekTheSneak/Gathering/blob/8aea932386227d034928c1d9313abbaf6fe55c8b/common/src/main/java/dev/gathering/client/Tutorial.java#L139), [Tutorial.java:181](https://github.com/MeekTheSneak/Gathering/blob/8aea932386227d034928c1d9313abbaf6fe55c8b/common/src/main/java/dev/gathering/client/Tutorial.java#L181).

**Reproduction:** `QualityReviewGameTest.firstDrawAfterRestartMustAdvanceTheTutorial`.

### QP-05 · P2 · Rebound keys are still overridden or taught incorrectly

**Evidence:** Verified by direct input-dispatch and translation inspection; no graphical reproduction run.

The new key mappings are useful, but `TableScreen.keyPressed` still intercepts literal L to toggle the log **before** resolving the current bindings. Rebind the log to Z and draw to L: L still opens the log, although Controls and the draw menu say it should draw. Unbinding the log also leaves its hardcoded L shortcut active. Spectators take the early no-seat return, so their log access also bypasses the new mapping.

The F1 help only interpolates bindings for untap-all, draw, shuffle, and palette. Other rebindable actions still have literal instructions such as `3 - scry`, `0 or Enter - end turn`, and `L - game log`. This makes discoverability unreliable after customization.

**Fix:** Resolve bound actions before fallback keys, including non-mutating spectator actions. Remove hardcoded duplicates for registered actions or model deliberate secondary shortcuts explicitly. Generate every rebindable action's help text from its current mapping, and handle reserved keys/conflicts consistently.

**Acceptance:** Test log→Z, draw→L; unbind the log; rebind scry and pass; repeat log checks as a spectator. Verify Controls, menus, tutorial prompts, and F1 help agree with actual input. These client behaviors require a client test or manual verification, not only a pure mapping test.

**Source:** [TableScreen.java:4617](https://github.com/MeekTheSneak/Gathering/blob/8aea932386227d034928c1d9313abbaf6fe55c8b/common/src/main/java/dev/gathering/client/TableScreen.java#L4617), [TableScreen.java:2771](https://github.com/MeekTheSneak/Gathering/blob/8aea932386227d034928c1d9313abbaf6fe55c8b/common/src/main/java/dev/gathering/client/TableScreen.java#L2771).

### QP-06 · P2 · Q05 is a tested helper, not integrated pending-action feedback

**Evidence:** Implementation/acceptance gap confirmed by production call-site search.

Q05 is marked Done and described as exact operation feedback. However, production code does not call `PendingWork.sent`, `confirmed`, `refused`, `of`, or `worthMentioning`. Outside the helper itself, the only production reference is disconnect cleanup calling `clear`; the other callers are tests. No request actually carries an ID created by this helper, and no screen renders its state. It also has no explicit session identity.

Existing operation-specific import/build handling should be retained; this finding does not claim those older protections disappeared. It means the newly advertised general pending/reconciliation feature has not been connected to user actions. A unit-tested state container cannot satisfy the visible slow-request requirement by itself.

**Fix:** Mark Q05 partial until an actual request/send/acknowledgment/render path uses the contract. Start with one slow operation; associate the existing operation ID with server/session generation, display a delayed pending cue, handle authoritative completion/refusal, and reconcile unknown outcomes without resending mutations. Separate the short delay before showing a pending notice from the longer threshold for declaring the outcome unknown. Expand deliberately rather than replacing working operation-specific safeguards wholesale.

**Acceptance:** Introduce controlled latency and stale/mismatched responses through the real UI path. Show that the user sees pending feedback, that a new session cannot inherit an old result, and that timeout/reconnect cannot repeat a mutation. Helper-only tests remain useful but are insufficient.

**Source:** [PendingWork.java:77](https://github.com/MeekTheSneak/Gathering/blob/8aea932386227d034928c1d9313abbaf6fe55c8b/common/src/main/java/dev/gathering/client/PendingWork.java#L77), [ClientState.java:31](https://github.com/MeekTheSneak/Gathering/blob/8aea932386227d034928c1d9313abbaf6fe55c8b/common/src/main/java/dev/gathering/client/ClientState.java#L31), [quality-backlog.csv:7](https://github.com/MeekTheSneak/Gathering/blob/8aea932386227d034928c1d9313abbaf6fe55c8b/docs/quality-backlog.csv#L7).

## Improvements to make while completing the current phase

1. **Keep rendering free of lifecycle timing.** Tutorial completion waits 240 rendered frames, then sends STOP and opens the starter screen from `render`. That is about four seconds at 60 FPS, one second at 240 FPS, and sixteen seconds at 15 FPS. Use a monotonic deadline or client ticks for the intended delay, and perform the transition from update logic. This is a source-verified polish issue; no FPS comparison was run. See [TableScreen.java:665](https://github.com/MeekTheSneak/Gathering/blob/8aea932386227d034928c1d9313abbaf6fe55c8b/common/src/main/java/dev/gathering/client/TableScreen.java#L665) and [TableScreen.java:1103](https://github.com/MeekTheSneak/Gathering/blob/8aea932386227d034928c1d9313abbaf6fe55c8b/common/src/main/java/dev/gathering/client/TableScreen.java#L1103).

2. **Measure bulk actions before calling them optimized.** The client now clips a gesture to 128 targets, but the ordinary server action handler still sends a table broadcast for every successful event. A 100-card gesture can therefore cause 100 board broadcasts. Measure event folding, serialization, packet bytes, and frame time on the four-seat fixture. If needed, validate individual events normally but coalesce outgoing updates while preserving required hidden-information boundaries and acknowledgment semantics. A client cap alone is not a server work budget. See [TableActions.java:80](https://github.com/MeekTheSneak/Gathering/blob/8aea932386227d034928c1d9313abbaf6fe55c8b/common/src/main/java/dev/gathering/server/TableActions.java#L80).

3. **Decide whether starters are welcome grants or completion rewards.** `StarterBoosters` enforces one grant per player UUID per world, but does not verify tutorial completion; a direct valid `StarterPayload` can request the grant. Its tests likewise call `give` on players who have never practiced. If the desired product behavior is an unconditional starter grant, document that. If it is a completion reward, keep eligibility on the server and add an explicit claim acknowledgment. Do not treat local tutorial progress as proof. This is a policy/contract decision rather than an unbounded duplication finding. See [StarterBoosters.java:103](https://github.com/MeekTheSneak/Gathering/blob/8aea932386227d034928c1d9313abbaf6fe55c8b/common/src/main/java/dev/gathering/server/StarterBoosters.java#L103) and [StarterBoosters.java:241](https://github.com/MeekTheSneak/Gathering/blob/8aea932386227d034928c1d9313abbaf6fe55c8b/common/src/main/java/dev/gathering/server/StarterBoosters.java#L241).

4. **Update progress records to describe actual integration.** Q10 and Q11 still say Not started although bulk limits/refusal aggregation and recents are present. Q11's pinned favorites remain unimplemented. Q05 says Done despite the missing production path. Several new display/motion/sound settings are foundations for later phases rather than active behavior; count them as such. Record implementation, automated evidence, and human validation separately.

5. **Test entry points and transitions, not only helpers.** Add fixtures for disconnect, reconnect, restart, held inventory, asynchronous completion, and remapped input. Keep the tutorial's normal-path test, but run the same action after each navigation path. For the scripted tour's documented late-stage inactive board, establish why it is inactive and reset the fixture deliberately; moving assertions earlier should not substitute for understanding the state transition.

6. **Keep extracting coherent responsibilities.** `TableScreen.java` is now roughly 5,900 lines. `ActionPalette` is a useful extraction; tutorial lifecycle and shared input dispatch are good next boundaries. Put repeated historical audit narratives in developer documentation and leave concise invariants beside the code. Avoid a broad rewrite or a line-count target: preserving behavior and making boundaries testable matter more than deleting lines.

## What remains in the project

| Workstream | Assessment of this revision |
|---|---|
| Guided first game | Replace the server practice feature with QP-07; retain QP-01/QP-02 property and migration safeguards and QP-04 navigation coverage |
| Frequent actions | Meaningful progress through mappings, palette, bounded selection, and recents; finish rebinding/help, favorites, and actual before/after trials |
| Crowded Commander boards | Mostly later work; no human four-player usability or performance sign-off here |
| Collecting progression | Starter choice added; broader time-to-deck measurement and configurable progression are still separate work |
| Modpack integration | No exact-version Create/Aeronautics/Cataclysm compatibility claim established by this review |
| Feedback/accessibility | Preferences and helpers exist, but visible pending feedback and the broader scale/motion/sound integrations still require end-to-end work |

## Recommended next handoff to the developer

Implement **QP-07: the local pre-match tutorial overlay**, replacing the separate server practice feature. Protect owned property immediately while retiring the old path, and include cleanup/migration for existing practice saves. QP-01, QP-02, and QP-04 describe the old design's failures; use their invariants to guide replacement tests rather than rebuilding a more elaborate server practice system.

QP-03 colored-booster recovery, QP-05 keybinding/help consistency, and QP-06 real-operation pending feedback still need correction independently. Run the full existing gate and new overlay/migration checks, then validate one first-time user flow and one live multiplayer handoff. Leave textures unchanged. See QP-07 above for the exact experience, code boundaries, and acceptance checks.

## Evidence package

`Gathering-quality-progress-evidence.zip` contains this report, the findings CSV, `QualityReviewGameTest.java`, a reproduction README, verification/static/probe logs, and core test XML. The four failures are expected on the reviewed revision. The tests are acceptance specifications for the fixes, not patches to the mod.
