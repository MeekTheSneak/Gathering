# Gathering: quality and polish audit

15 September 2026 · Audit and recommendations only

## Assessment

Gathering is making meaningful progress. Its current source includes the first-sit local tutorial, manual tabletop controls, loaners, configurable rewards, accessibility preferences, Create display integration, Ponder scenes, and a much stronger regression suite. The next substantial improvement is making these systems behave consistently through an entire player workflow.

Create offers useful examples of contextual instruction, spatial assistance, and coordinated interaction behavior. Simulated offers useful examples of teaching physical cause and effect and coupling failure feedback with the state a player sees. These are specific implementation lessons, not evidence that either reference repository is flawless. Their source was inspected; neither reference mod was built or independently playtested for this comparison.

The current gate passes. A separate graphical probe nevertheless reproduced keyboard-focus loss and accessibility failures in production screens. This is a concrete example of why a green gate alone cannot establish this level of polish.

## Version and evidence boundary

| Repository | Inspected revision | Scope |
|---|---|---|
| Gathering | `4de7a183f7f6e542d379d804eea060008497127f` on `claude/new-session-beye3i` | Latest development branch fetched for this audit; isolated checkout |
| Create | `0924e93639ad5f61cfc39a221d909e16f2893df1` on `mc1.21.1/dev` | Selected interaction, teaching, placement, and CI implementations |
| Simulated-Project | `50443d00afa06e0982b45f40cd686f7ecf978132` on `main` | Selected shared/physics-assembler teaching, feedback, and CI implementations |

Simulated-Project contains multiple modules, including Aeronautics. Its checked-in properties target Minecraft 1.21.1. Gathering's branch is identified deliberately: this review does not assume GitHub's default branch contains the newest development work.

**Validation performed:** the unchanged Gathering checkout passed `tools/gate.sh`, including both-loader verification, all 14 named static checks, discovered in-world tests (Fabric 16; NeoForge 524), and the check for failures swallowed by ticks. Core test XML reports 1,751 tests, zero failures/errors, one skipped. The prior tournament regression cases now pass; they are not reissued here as unresolved findings.

After that baseline, an audit-only NeoForge client probe opened the actual SettingsScreen and EventScreen, exercised keyboard activation and refresh, and captured screenshots and widget bounds. It completed successfully. The tournament payload was synthetic: an eight-player Modern event in Swiss round one. This validates client presentation under that state, not networking, actual match play, or server authorization. Screens were 854 × 480 GUI units, rendered at 1708 × 960 pixels.

No production code, textures, or other art was changed. The probe lives only in the isolated checkout's GameTest source set. No commits or pushes were made. Full modpack multiplayer, moving-airship interaction, controller use, and a complete graphical tour of all screens were not performed. No claim of a comprehensive security clearance or measured FPS improvement is made.

## Confirmed findings

### PQ-01 · P2 · Event control-size preference is ignored

**Evidence:** at 100% and 200% control size, every measured EventScreen widget retained the same rectangle. Result buttons stayed 47 × 18 GUI units; tabs stayed 98 × 16. In the same run, SettingsScreen controls correctly grew from 18 to 36 units tall, confirming that the preference changed successfully.

**Cause:** EventScreen builds fixed-height rows, tabs, and a capped panel without deriving geometry from the control-size preference. See [EventScreen initialization](https://github.com/MeekTheSneak/Gathering/blob/4de7a183f7f6e542d379d804eea060008497127f/common/src/main/java/dev/gathering/client/EventScreen.java#L77).

**Impact:** players who need larger targets cannot obtain them in a major new workflow. This is reproducible even in a reasonably sized window.

**Change:** introduce a small pure layout calculation for this screen using the existing layout-helper approach. Derive minimum target sizes from control scale; introduce scrolling or responsive pagination when content cannot fit. Keep Done and primary status reachable. Do not solve this by silently shrinking the requested targets.

**Acceptance:** assert actual production widget rectangles at 100/150/200% independently of text scale; exercise small supported windows and GUI scales. Verify that every action remains reachable by keyboard and mouse after reflow.

### PQ-02 · P2 · Larger event text outgrows its row layout

**Evidence:** [100% text](evidence/audit-event-100.png) versus [200% text](evidence/audit-event-text-200.png). The larger title and status lines crowd their neighbors; short tab/result labels enlarge while longer labels fit down, producing visibly inconsistent sizing. The unchanged layout leaves insufficient vertical room for the requested text.

**Cause:** text helpers honor text scale, but EventScreen continues to advance by fixed line heights and place controls at fixed offsets. See [rendering and line metrics](https://github.com/MeekTheSneak/Gathering/blob/4de7a183f7f6e542d379d804eea060008497127f/common/src/main/java/dev/gathering/client/EventScreen.java#L310).

**Change:** use measured text line heights and wrapping to lay out the header, match status, report controls, and prize summary. Provide sufficient row height before fitting text. Preserve text size independently from control size and decorative art.

**Acceptance:** screenshots plus geometry assertions at all supported combinations of text/control scale; long event and opponent names; representative long translations; standings, host pairings, and result selection. Test real widgets as well as the pure layout helper. A screenshot with smaller fallback text is not proof that the requested accessibility size works.

### PQ-03 · P2 · Refreshes discard keyboard focus

**Evidence:** the client probe focused “Reduced motion: Off,” pressed Enter, and observed `after=null` following the settings rebuild. It also focused “Begin” on the event host tab, delivered the same event snapshot, and observed `after=null`.

**Cause:** [SettingsScreen.tick](https://github.com/MeekTheSneak/Gathering/blob/4de7a183f7f6e542d379d804eea060008497127f/common/src/main/java/dev/gathering/client/SettingsScreen.java#L161) rebuilds widgets after a preference change; [EventScreen.accept](https://github.com/MeekTheSneak/Gathering/blob/4de7a183f7f6e542d379d804eea060008497127f/common/src/main/java/dev/gathering/client/EventScreen.java#L48) rebuilds on every matching snapshot. Neither path restores the logical focused control.

**Impact:** keyboard users lose their place after a setting change or a server refresh. Repeated asynchronous event updates can interrupt navigation even when the visible structure has not changed.

**Change:** update existing labels and enabled states for nonstructural changes. Where a rebuild is necessary, restore focus by a stable action identity, together with valid page/selection state. If the action disappears, choose a predictable safe neighbor. Do not restore focus by old widget index, which can point at a different action after a phase change.

**Acceptance:** navigate only by keyboard while applying snapshots; change each setting; resize and change tabs; remove the focused action through a phase change. Confirm preserved identity or a deliberate fallback, and no accidental destructive activation.

### PQ-04 · P2 · Host controls do not communicate the current phase

**Evidence:** [host screen during Swiss](evidence/audit-host-during-swiss.png) offers active “Open check-in,” “Begin,” and “Start now” alongside ongoing-event actions. The help line still explains how Begin ends registration. The [host widget builder](https://github.com/MeekTheSneak/Gathering/blob/4de7a183f7f6e542d379d804eea060008497127f/common/src/main/java/dev/gathering/client/EventScreen.java#L262) creates these controls unconditionally.

**Impact:** the screen invites actions that do not apply to the stage shown in its own header. Players have to discover the workflow through server refusals. This finding is about presentation, not a demonstrated authorization bypass; server transition checks remain necessary and are not removed by the suggested fix.

**Change:** derive action availability and explanations from the event state and viewer role. Show the next useful host action prominently; disable or omit inapplicable actions with a discoverable reason. Keep cancellation deliberately separated and confirmed with consequences. Avoid inventing a second, divergent tournament transition engine on the client.

**Acceptance:** a phase × role matrix covering signup, check-in, preparation, Swiss, cut, finished, and cancelled; valid and invalid transitions; stale snapshots; reconnect. Check both visible availability and independent server rejection of forged requests.

## What to borrow from the reference repositories

| Quality principle | Concrete reference | Application to Gathering |
|---|---|---|
| Teach at the point of use | Create's [ItemDescription](https://github.com/Creators-of-Create/Create/blob/0924e93639ad5f61cfc39a221d909e16f2893df1/src/main/java/com/simibubi/create/foundation/item/ItemDescription.java) organizes summaries, conditions, and controls | Extend existing item descriptions and contextual help around actual tasks; use remapped bindings in instructions |
| Coordinate overlapping information | Create's [GoggleOverlayRenderer](https://github.com/Creators-of-Create/Create/blob/0924e93639ad5f61cfc39a221d909e16f2893df1/src/main/java/com/simibubi/create/content/equipment/goggles/GoggleOverlayRenderer.java) checks context and competing overlays | Define when table hints, inspection, tutorial, and status can appear together; suppress redundant or conflicting overlays |
| Keep failures actionable | Create's [IDisplayAssemblyExceptions](https://github.com/Creators-of-Create/Create/blob/0924e93639ad5f61cfc39a221d909e16f2893df1/src/main/java/com/simibubi/create/content/contraptions/IDisplayAssemblyExceptions.java) exposes the last assembly failure | Keep a relevant refusal visible in the screen where the action happened, with a recovery step |
| Make world interactions predictable | Create's [ValueSettingsInputHandler](https://github.com/Creators-of-Create/Create/blob/0924e93639ad5f61cfc39a221d909e16f2893df1/src/main/java/com/simibubi/create/foundation/blockEntity/behaviour/ValueSettingsInputHandler.java) checks interaction availability and hit context | Use consistent eligibility and gesture presentation rather than scattered special-case prompts |
| Preview spatial intent | Create's [PoleHelper](https://github.com/Creators-of-Create/Create/blob/0924e93639ad5f61cfc39a221d909e16f2893df1/src/main/java/com/simibubi/create/foundation/placement/PoleHelper.java) computes candidate placement offsets | Show the table footprint and joining outcome before consuming an item |
| Teach cause and effect | Simulated's [PhysicsAssemblerScenes](https://github.com/Creators-of-Aeronautics/Simulated-Project/blob/50443d00afa06e0982b45f40cd686f7ecf978132/simulated/common/src/main/java/dev/simulated_team/simulated/ponder/scenes/PhysicsAssemblerScenes.java) uses contextual pointing, progression, and world animation | Give a short, relevant sequence for hosting, sideboarding, trading, or arranging a board using existing assets |
| Couple feedback and visible state | Simulated's [PhysicsAssemblerFailedPacket](https://github.com/Creators-of-Aeronautics/Simulated-Project/blob/50443d00afa06e0982b45f40cd686f7ecf978132/simulated/common/src/main/java/dev/simulated_team/simulated/network/packets/physics_assembler/PhysicsAssemblerFailedPacket.java) resets visible lever state and plays failure feedback | Ensure a refused or completed request clears pending UI and supplies the matching explanation; honor sound preferences |
| Automate release evidence | Create's [build workflow](https://github.com/Creators-of-Create/Create/blob/0924e93639ad5f61cfc39a221d909e16f2893df1/.github/workflows/build.yml) and Simulated's [GameTest workflow](https://github.com/Creators-of-Aeronautics/Simulated-Project/blob/50443d00afa06e0982b45f40cd686f7ecf978132/.github/workflows/gametests.yml) run verification in CI | Run Gathering's canonical gate in CI and retain evidence; qualify optional integrations separately |

These are selected patterns, not a recommendation to copy source or change Gathering's visual identity. Gathering already has table/desk Ponder and contextual descriptions. Its own [recent comparative study](https://github.com/MeekTheSneak/Gathering/blob/4de7a183f7f6e542d379d804eea060008497127f/docs/reviews/create-aeronautics-study-2026-09-14.md) covers several completed adoptions; this review credits that work rather than proposing it again.

## Improvement roadmap beyond those defects

### PQ-05 · P2 · Finish the action-to-feedback loop

[EventScreen.send](https://github.com/MeekTheSneak/Gathering/blob/4de7a183f7f6e542d379d804eea060008497127f/common/src/main/java/dev/gathering/client/EventScreen.java#L68) sends actions without a request identity; [EventActionPayload](https://github.com/MeekTheSneak/Gathering/blob/4de7a183f7f6e542d379d804eea060008497127f/common/src/main/java/dev/gathering/network/EventActionPayload.java) carries the action but no correlated outcome. [EventViews.act](https://github.com/MeekTheSneak/Gathering/blob/4de7a183f7f6e542d379d804eea060008497127f/common/src/main/java/dev/gathering/server/events/EventViews.java#L43) dispatches it and refreshes the view. Server message paths can explain refusal in chat, but that is not a dependable status surface inside the event screen. Rate limiting already exists; do not report it as missing.

Add a bounded request/result contract for event mutations: request identity, action, success or localized reason, and relevant state revision if revisions are introduced. Render pending, completed, or refused status in the originating screen. Reuse the existing pending-work machinery where appropriate; do not create another unused helper. Server authority remains independent, and repeated requests involving property must be handled safely at the domain level. A client timeout must never imply a financial/item operation failed and may safely be retried.

For example, reporting a result should visibly say “Waiting for server,” then “Reported; waiting for opponent” or a useful refusal. It should not leave the player guessing whether another click is required. Pair restrained existing audio with the visual status, under Gathering's volume and reduced-motion settings. Do not add mandatory sound-only information.

### PQ-06 · P3 · Preview table placement and joining

[TableBlockItem](https://github.com/MeekTheSneak/Gathering/blob/4de7a183f7f6e542d379d804eea060008497127f/common/src/main/java/dev/gathering/block/TableBlockItem.java#L30) already checks the full placement and supplies some refusal reasons after a failed attempt. Add an optional footprint/outline preview showing occupied cells and valid joining before placement. Use the existing table model and outline primitives; no new textures are needed.

Share the placement calculation or a structured assessment with server placement rather than implementing a second approximation. The client preview is advisory; server checks still determine success. Include block obstruction, line-only joining, maximum size, placement permissions, and coordinate transformation on supported structures. Keep the initial implementation small: clear footprint and reason first, elaborate animation only if useful.

### PQ-07 · P2 · Turn integration claims into repeatable qualifications

[pack-authors.md](https://github.com/MeekTheSneak/Gathering/blob/4de7a183f7f6e542d379d804eea060008497127f/docs/pack-authors.md#L76) documents important limits: actual flying-table client use remains untested; rotated disassembly ends the game with return of property; Cataclysm reward examples are not verified. Normal Create contraptions intentionally leave tables/collections behind. These are existing documented limitations, not newly discovered exploits.

Maintain an exact-version matrix for standalone NeoForge/Fabric, Create, Aeronautics/Sable, and supported pack combinations. Use real connected clients for input, camera, interpolation, payload negotiation, reconnect, and inventory custody. The standard green gate does not load the complete optional pack. Repair the pack test fixture's connection negotiation rather than excluding failing player-dependent tests.

Prioritize assemble → move/rotate → play → reconnect → disassemble → restart with decks, stakes, prizes, and tournament registration present. Require every owned item to have exactly one recoverable destination. Distinguish intentionally ended games from silent state loss. Put movement limits where a player attempts the operation, not only in developer documentation.

### PQ-08 · P3 · Extend contextual learning through complete tasks

Keep the first-sit local overlay and its isolation from real cards. Expand optional help where players need it: “finish this match,” “sideboard and return,” “register and host,” “inspect an attachment pile,” and “trade a duplicate toward a deck.” Reuse the existing tutorial and Ponder entry points before adding infrastructure.

Each lesson needs an entry point in production, a visible completion condition, skip/replay, and correct remapped keys. Keep essential instruction available without Create and on Fabric. If new-content discovery markers are added, version lesson identities and store a small bounded preference; avoid writes every render frame. Simulated's scene-discovery implementation is inspiration, not code to copy wholesale.

Measure this with players who know Magic but do not know Gathering: can they finish the task unaided, and where do they hesitate? Test crowded Commander boards with four players, attachments, tokens, counters, overlapping cards, and hidden hands. Preserve the manual tabletop philosophy rather than adding automatic rules resolution to compensate for unclear controls.

### PQ-09 · P2 · Put the existing gate and integration evidence into CI

No Gathering GitHub workflow was present in the inspected checkout. Add a pull-request/push workflow running the canonical gate under Java 21 with reproducible data fixtures and separate optional integration jobs. Upload failing test reports and relevant logs. Keep release publishing separate from ordinary validation and do not require secrets for untrusted pull requests.

Record the exact revision, fixture identity, discovered test counts, and integration versions. Preserve the existing checks that detect missing GameTests and swallowed tick failures. Add a small actual-client interaction suite for focus, reflow, remapped keys, and action status; those are the demonstrated blind spots here. Introduce issue forms requesting versions, reproduction steps, logs, and expected/actual behavior.

### PQ-10 · P3 · Optimize measured workloads and simplify ownership

Avoid a full rewrite or a line-count target. Gathering's core/common/loader split and existing extracted UI helpers are useful foundations. Start with EventScreen's layout, action availability, and refresh lifecycle, where behavior is visibly inconsistent.

Create a repeatable performance scene: four Commander players, roughly 200 public objects including attachments and tokens, counters, multiple visible tables, and event updates. Capture client frame-time percentiles, allocations, server tick cost, snapshot bytes, and input-to-visible-response latency. Include added network latency and fresh versus warm card caches. Establish budgets from a documented baseline rather than inventing universal FPS promises.

One source-level candidate is [EventViews.broadcast/viewFor](https://github.com/MeekTheSneak/Gathering/blob/4de7a183f7f6e542d379d804eea060008497127f/common/src/main/java/dev/gathering/server/events/EventViews.java#L152), which computes standings-backed public rows separately per recipient. Profile before changing it. If significant, calculate an immutable common public projection once per broadcast/revision, then add recipient-specific fields separately. Never share private opponent/hand information through a common cache. Bound retained data and invalidate on every relevant event mutation.

Also finish one verified pack-author example before expanding the reward API. The existing config-driven reward contract already exists; it does not need replacement with a new framework. Choose exact installed mod versions, test an actual trigger, document reload and failure behavior, and keep balance optional. A first-pack-to-personal-deck playtest should measure time and duplicate usefulness before adjusting reward rates.

## Recommended order

1. Fix PQ-01 through PQ-04 together in a narrowly scoped event/accessibility change, with the production client regressions.
2. Complete PQ-05 and establish PQ-09 so new behavior is exercised automatically.
3. Qualify PQ-07 before stronger compatibility claims or additional moving-table features.
4. Implement PQ-06 and PQ-08 in small player-tested slices.
5. Profile PQ-10 and optimize only demonstrated costs; ship a verified integration example.

A credible completion claim includes observable player behavior and reproduction evidence for the exact new revision. More buttons, a smaller line count, and more unit tests are useful only when they improve that result.
