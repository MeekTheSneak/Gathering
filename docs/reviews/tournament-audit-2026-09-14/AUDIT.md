# Gathering tournament audit

Reviewed September 14, 2026, against commit [`63bd0410bd8f0c3a83f5c03ff2e3d068240fff9e`](https://github.com/MeekTheSneak/Gathering/commit/63bd0410bd8f0c3a83f5c03ff2e3d068240fff9e), freshly fetched from `origin/claude/new-session-beye3i`.

**Ten distinct defects were reproduced.** The most important involve malformed client input, stale tournament authority over reused tables, prize durability, and round progression. I would address the P1 findings before using this implementation for tournaments with valuable prizes.

This is an audit, not a fixes package. Production code, textures, and sounds are unchanged. The accompanying patch adds regression tests only; those tests currently fail because they assert the intended behavior.

## Scope and evidence

The tournament changes span 95 files and roughly 11,500 added lines, including tests, documentation, and development scenes. Review focused on the tournament engine, settings and result codecs, server dispatch, table allocation, signup/opening integration, pool provenance, persistence, prizes, records, lifecycle hooks, and relevant client consumers. This is not a guarantee that every new line or interaction is defect-free.

Before adding audit tests, the unchanged checkout passed `tools/gate.sh`: both loaders, all fourteen static checks, **471 NeoForge / 13 Fabric in-world tests**. After adding the probes:

- Two focused core tests ran; both failed, demonstrating score overflow and incorrect bye selection.
- The final NeoForge run executed **480 tests**. The original 471 passed; all nine new audit tests failed on the intended assertions.
- One defect—score overflow—has both core and world/codec coverage, so eleven failing assertions establish ten distinct findings.

The probes use production handlers and codecs wherever practical. Restart coverage models loss of the transient callback by round-tripping saved event state and discarding its scheduled work; it does not launch two separate server processes. The prize probe injects a filesystem replacement failure for one test event. These distinctions matter when interpreting the evidence.

I did not rerun the graphical tour, test socket-level disconnect behavior, load-test a public server, or run Aeronautics/Cataclysm combinations. The working record itself says the latest graphical fixes have not completed a fresh tour because of memory-related terminations. Existing scripts and prior successful helper tests do not close the findings below.

## Findings by priority

P1 means fix promptly before relying on the affected feature. P2 means a material correctness defect to address next. Priorities are not CVSS ratings.

| ID | Priority | Finding | Reproduction |
|---|---|---|---|
| TN-01 | P1 | Overflowing reported scores poison outbound event views | Core + server handler/codec |
| TN-02 | P1 | An old event can terminate a different event's active match | Server action handler |
| TN-03 | P1 | Failed prize persistence still consumes the deposited items | Injected save failure |
| TN-04 | P1 | A player's withdrawal can permanently stall round advancement | Server action + elapsed ticks |
| TN-05 | P1 | Restarting between rounds loses the only advancement callback | Save round trip + elapsed ticks |
| TN-06 | P1 | Limited registration accepts a pool from an older event at the same coordinates | READY handler |
| TN-07 | P2 | Start now can enter Swiss while an unfinished pod blocks its matches | START_NOW handler |
| TN-08 | P2 | Only the first table cluster in a venue is split for matches | BEGIN handler, two table rows |
| TN-09 | P2 | Pairing after a drop can erase a survivor's previous win and award the wrong bye | Pure pairing test |
| TN-10 | P2 | Opponent reports are displayed from the wrong perspective | REPORT handler + produced view |

## TN-01 — Reject overflowing scores before storing or broadcasting them

**Location:** [`core/src/main/java/dev/gathering/core/tournament/MatchResult.java:17`](https://github.com/MeekTheSneak/Gathering/blob/63bd0410bd8f0c3a83f5c03ff2e3d068240fff9e/core/src/main/java/dev/gathering/core/tournament/MatchResult.java#L17). Follow through `EventViews.result`, `Events.report`, and `common/src/main/java/dev/gathering/network/EventViewPayload.java`.

**Trigger:** A registered participant sends a report with large positive score fields. The constructor checks `winsA + winsB + draws > 9` using `int` arithmetic. That sum can overflow to a negative number and pass validation. The request decoder accepts full varints, so the UI's small score buttons do not protect this boundary.

**Observed:** The probe submitted the report through `EventViews.act`, then encoded the resulting production view. Encoding failed with `EncoderException: String too big (was 21 characters, max 16)`. The invalid report is already in event state and the event save precedes the response. This can break affected participants' updates beyond the initial request. If both reports confirm an oversized result, it also enters the public pairing result and record calculations. Actual client disconnections were not tested.

**Fix direction:** Validate each field independently and compute totals without overflow. Reject invalid reports with an explicit refusal; do not silently convert them to a 0–0 result as the current fallback does. Check consistency with the configured match length while preserving supported timed/drawn outcomes. Validate loaded scores too, with a recovery path for already-poisoned saves. Do not solve this by merely widening the outbound string limit.

**Guard:** `rejectsOverflowingScore`; `malformedReportMustNotPoisonOutboundView`. Add boundary, negative, malformed-total, and both-viewer tests.

## TN-02 — Terminal events retain destructive authority over reused tables

**Location:** [`common/src/main/java/dev/gathering/server/events/Events.java:450`](https://github.com/MeekTheSneak/Gathering/blob/63bd0410bd8f0c3a83f5c03ff2e3d068240fff9e/common/src/main/java/dev/gathering/server/events/Events.java#L450), especially unconditional `finishUp` after `apply`, plus `clearTables:594` and `finishUp:646`.

**Trigger:** Event A has ended or been cancelled. Its tables are reused by event B. A's original host presses Cancel again on A, or resends that action. The host check authorizes the caller against A; `Tournament.cancel()` returns the same terminal tournament. Nevertheless, `Events.cancel()` runs cleanup against A's historical table coordinates.

**Observed:** The probe put an active game on a reused table, invoked CANCEL for the old cancelled event, and found that the newer game's session had been destroyed. The newer tournament's own state remains active. This needs no administrator privilege; stale controls are sufficient. The same cleanup can affect unrelated sessions or signups at those coordinates.

**Fix direction:** Make finalization idempotent and tied to a real state transition. Before destructive table operations, verify that the table/session still belongs to this event and round. Coordinates alone are not ownership. Apply the same terminal-state and ownership checks to other old-event host controls, particularly adding tables and label updates.

**Guard:** `cancellingAnOldEventMustNotEndANewMatch`. Extend to finished events, different hosts, labels, held decks, and a newer signup.

## TN-03 — Prize deposits acknowledge and consume items despite failed saves

**Location:** [`common/src/main/java/dev/gathering/server/events/EventPrizes.java:53`](https://github.com/MeekTheSneak/Gathering/blob/63bd0410bd8f0c3a83f5c03ff2e3d068240fff9e/common/src/main/java/dev/gathering/server/events/EventPrizes.java#L53), `EventStore.write:59`, and `Events.save:918`.

**Trigger:** A host deposits a prize while the event file cannot be replaced—for example because of a filesystem failure. The code copies the item into in-memory event state, empties the inventory slot, and announces success before attempting persistence. `EventStore.write` returns failure, but its caller ignores that result.

**Observed:** With replacement deliberately blocked for this event alone, depositing three diamonds still emptied the hand. The log confirmed the write failure. No durable event record protected the newly deposited items. A subsequent restart before a successful save can therefore lose them.

**Fix direction:** Define an acknowledged, recoverable custody transfer rather than a void save call. Refuse or roll back a failed deposit and retain recoverable state on failed payouts. Review payout, offline delivery, and finalization as the same conservation problem. Atomic replacement of the event file alone does not atomically save both event custody and player inventory.

**Guard:** `failedPrizeSaveMustNotConsumeTheItem`. Add durable payout/restart tests and full-inventory cases. A duplicated payout after a save failure is a related risk from the current ordering, but was not reproduced in this pass.

## TN-04 — Self-withdrawal skips the round-completion transition

**Location:** [`common/src/main/java/dev/gathering/server/events/Events.java:275`](https://github.com/MeekTheSneak/Gathering/blob/63bd0410bd8f0c3a83f5c03ff2e3d068240fff9e/common/src/main/java/dev/gathering/server/events/Events.java#L275).

**Trigger:** A player uses the normal Drop/Withdraw action while their match is the round's last unresolved pairing. `Tournament.drop()` concedes and confirms that pairing. Unlike the host's `dropPlayer`, the self-withdrawal path never calls `afterResult`.

**Observed:** The pairing became confirmed, but the tournament remained in round one after 340 server ticks—past the normal 300-tick transition delay. The periodic round handler returns immediately for complete rounds, so it cannot repair the missing transition. Players cannot re-report an already-confirmed pairing to advance it normally.

**Fix direction:** Centralize completion handling after every mutation that may complete a round, including self-drop, disconnect resolution, and automatic outcomes. Schedule once per event/round rather than relying on individual buttons to remember it.

**Guard:** `withdrawingTheLastMatchMustAdvanceTheRound`. Cover both the final Swiss round and a cut round, and withdrawal while another pairing remains unfinished.

## TN-05 — A restart during the inter-round pause strands the tournament

**Location:** [`common/src/main/java/dev/gathering/server/events/Events.java:608`](https://github.com/MeekTheSneak/Gathering/blob/63bd0410bd8f0c3a83f5c03ff2e3d068240fff9e/common/src/main/java/dev/gathering/server/events/Events.java#L608), `roundTick:720`, `EventStore`, and `ServerState.clear`.

**Trigger:** All results are confirmed and saved; the next round is scheduled in `ServerTicks` for fifteen seconds later. The server stops before that callback runs. Scheduled work is cleared on shutdown, and no saved transition/deadline recreates it when the event loads.

**Observed:** The test confirmed a result through the real handler, round-tripped its saved event, removed its transient callback to model restart, and resumed ticks. The complete round remained stuck. The loader restores event data, while `roundTick` explicitly skips complete rounds.

**Fix direction:** Persist or derive a pending round transition and resume it on load, keyed to the expected round/version. Make advancement and final prize processing idempotent. Also reconcile absent players after load: `goneSince` is cleared and currently rebuilt only from new logout events, so a player who never reconnects does not automatically regain the advertised grace timeout.

**Guard:** `completedRoundMustResumeAfterReload`. Follow with a two-process restart test before, during, and after finalization, including absent players. The absent-player timeout issue is source-observed, not separately reproduced here.

## TN-06 — Coordinates are being used as tournament pool identity

**Location:** [`common/src/main/java/dev/gathering/server/events/Events.java:361`](https://github.com/MeekTheSneak/Gathering/blob/63bd0410bd8f0c3a83f5c03ff2e3d068240fff9e/common/src/main/java/dev/gathering/server/events/Events.java#L361); also `PodEvents.begin`, `DraftActions.handOutThePools`, and `DraftedPool`.

**Trigger:** A player retains a pool from an earlier event at the same table coordinates, then submits it during a new limited event. `ready()` accepts a pool whose `fromPod` string matches any event table's coordinates. It does not establish which event allocated the pool, who received it, or even that this event has finished opening its packs. Dimension is absent from that string too.

**Observed:** A coordinate-matching pool was accepted and the entrant marked ready in a new event that had never opened packs. The probe uses unresolved card metadata, which follows the existing deck check's “no opinion” path; it tests event provenance, not card legality. A legal retained pool has the same missing event-identity check. No malicious packet editing of the item is needed for the retained-pool scenario.

**Fix direction:** Assign event/pod UUIDs and record each entrant's allocated pool on the server when opening/drafting finishes. READY must match that allocation and a completed preparation stage, rather than trusting an item's coordinates. Keep legacy casual pools readable without treating them as credentials for a new tournament. Specify temporary custody and final ownership separately for sponsored play.

**Guard:** `oldPoolCannotReadyANewUnopenedEvent`. Add two successive events at one table, equal coordinates in different dimensions, traded pools, and a legitimate current pool with known metadata.

## TN-07 — Start now can advance while the pod still owns the table

**Location:** [`common/src/main/java/dev/gathering/server/events/Events.java:410`](https://github.com/MeekTheSneak/Gathering/blob/63bd0410bd8f0c3a83f5c03ff2e3d068240fff9e/common/src/main/java/dev/gathering/server/events/Events.java#L410), `startPlay:478`, and `TableSessions.start`.

**Trigger:** A limited tournament is PREPARING with a signup or draft still running. The host presses Start now. The handler checks only PREPARING. `clearTables()` does not close that signup/draft, splitting can be refused, but the tournament is still changed to SWISS. `seatRound()` does not recover from a refused match start.

**Observed:** The event entered SWISS while the signup remained and no game session could start. This is available through an ordinary host control, not just a crafted packet.

**Fix direction:** Separate opening, drafting, and deck-building substates. A build-time override must not implicitly skip unresolved pack custody. Preflight and check table availability/splitting/start outcomes before committing the transition; explain refusals. Review cancellation during an active draft as well: current cleanup handles signups and matches, but does not explicitly terminate the draft through its card-return path.

**Guard:** `startNowCannotSkipAnUnfinishedPod`. Extend to an in-flight opening, an active draft, missing tables, and a legitimate build-time override.

## TN-08 — Multiple long-table rows are not all split

**Location:** [`common/src/main/java/dev/gathering/server/events/Events.java:486`](https://github.com/MeekTheSneak/Gathering/blob/63bd0410bd8f0c3a83f5c03ff2e3d068240fff9e/common/src/main/java/dev/gathering/server/events/Events.java#L486).

**Trigger:** A venue contains two separate rows, each consisting of two touching tables. The host adds both rows and begins an eight-player constructed event. `startPlay()` calls `TablesApart.set` only for the cluster containing `state.tables.get(0)`.

**Observed:** The second row remained joined. Its numbered pairings therefore resolve against one shared cluster/session instead of independent two-player tables; later seating can displace the earlier pair. The existing test of eight physically separate tables does not exercise this topology.

**Fix direction:** Identify and process every unique cluster in the venue, verify the resulting independent table/session mapping, and reject partial setup before seating. Apply symmetric cleanup/restoration per cluster without touching a newer event's tables.

**Guard:** `everyVenueClusterMustSplitBeforePairing`. Assert unique session ownership and participant membership for every pairing, not only the split flag, after the fix.

## TN-09 — Dropping a player changes how surviving results are counted for pairing

**Location:** [`core/src/main/java/dev/gathering/core/tournament/SwissPairer.java:42`](https://github.com/MeekTheSneak/Gathering/blob/63bd0410bd8f0c3a83f5c03ff2e3d068240fff9e/core/src/main/java/dev/gathering/core/tournament/SwissPairer.java#L42) and [`Standings.java:60`](https://github.com/MeekTheSneak/Gathering/blob/63bd0410bd8f0c3a83f5c03ff2e3d068240fff9e/core/src/main/java/dev/gathering/core/tournament/Standings.java#L60).

**Trigger:** Pairing passes only still-active entrants into `Standings.of`, but that function needs historical opponents too. If pairing side A has dropped, its missing tally causes the entire historical match to be skipped, including surviving side B's result. Dropping side B also removes opponent information.

**Observed:** A four-player fixture had B beat A and C beat D. After A dropped, D was the only winless active player. Round-two pairing nevertheless awarded B the bye: B's win had vanished from the temporary standings used for pairing. The full tournament standings still counted B's three points.

**Fix direction:** Compute standings and opponent percentages from the complete entrant/history set, then filter the pairing candidates. Do not discard historical opponents when filtering active entrants. Preserve intentional dropped-player treatment in final places separately.

**Guard:** `byeSelectionRetainsResultsAgainstDroppedOpponents`. Test a dropped opponent in either pairing position, later drops, and consistency between displayed standings and pairing order.

## TN-10 — The opponent's score is reversed in the receiver's view

**Location:** [`common/src/main/java/dev/gathering/server/events/EventViews.java:157`](https://github.com/MeekTheSneak/Gathering/blob/63bd0410bd8f0c3a83f5c03ff2e3d068240fff9e/common/src/main/java/dev/gathering/server/events/EventViews.java#L157), consumed by `EventScreen.java:316`.

**Trigger:** B reports losing 1–2 to A. `Pairing.reportB` is already normalized to A's perspective. `viewFor` flips it again when producing A's `theirReport`, and fails to flip A's report for B.

**Observed:** A's view said the opponent reported 1–2, although the receiver-relative score should be 2–1. This can guide a player to click the opposite result and create a dispute. Backend confirmation normalizes reports correctly; the proven defect is in the produced UI view.

**Fix direction:** For A, expose `reportB` unchanged; for B, expose `reportA.flipped()`. Centralize perspective conversion rather than repeating ternaries across fields.

**Guard:** `opponentsReportMustUseTheViewersPerspective`. Add both viewers, one report, matching reports, disputes, draws, and a graphical check of the displayed wording.

## Additional source-observed gaps to investigate

These are separate from the ten reproduced findings. They should not be represented as measured exploits or completed runtime tests.

1. **Tournament request and storage budgets.** `EventViews.act` has no tournament-specific rate limiter. Repeat CHECK_IN actions construct a new tournament and synchronously write/compress the full event; broadcasts rebuild views per participant. Event creation/cancellation can also grow the retained event map and files without a lifetime cap under the default zero host cooldown. Screen row limits do not bound this work. Add no-op detection, per-connection and global budgets, bounded retention/pagination, and measured load tests. Preserve acknowledged durability when moving writes off-thread.
2. **Clock contract.** The design says clocks use real time while the server is running; `roundTicks++` and `buildTicks++` count ticks instead. At 10 TPS, a nominal 50-minute round takes about 100 minutes of wall time. Use a monotonic elapsed-time clock with persisted remaining duration and explicit shutdown pause semantics, or explicitly revise the product contract. Disconnect grace and draft clocks deserve the same review.
3. **Sponsored/contributor-return tournament play.** Pod completion sends those cards to their final owners immediately; draft participants receive no pool, and the sealed return path only attaches `DraftedPool` for PLAYERS_KEEP. Tournament READY requires a pool. The ownership policies work as standalone pod handouts, but their tournament deck-building path needs a concrete end-to-end test and a defined temporary-play custody model.
4. **Audit trail.** The design promises a per-event audit log. The tournament save stores current aggregates and reports, not an append-only actor/action/history record. Host/admin settlement, cancellation, and prize transfers lack such a durable trail in the reviewed implementation. Add attributable, bounded records for disputes and custody; do not log hidden cards or seeds.
5. **Capacity and recovery preflight.** Limited BEGIN changes phase before verifying that every registered entrant can be seated or a pod can be created; an insufficient-seat condition only warns. A 256-player tournament limit does not make a single eight-seat pod support that field. Define multiple-pod allocation or refuse unsupported capacity before consuming the transition. Also distinguish unloaded tables from a completed pod when deciding whether the build clock should run.

## Positive checks and limits

The basic security structure is useful: actor identity comes from the server connection; host/admin checks exist on privileged event actions; private rating commands require permission level 2; public event payloads omit raw rating/seed fields; existing draft actions authorize the acting drafter; async pack opening rechecks signup and seating before consuming packs. Those controls narrow the review surface, but the lifecycle and provenance gaps above bypass assumptions made between individually reasonable methods.

The rating tests passing does not establish resistance to coordinated account farming, and this audit did not simulate that. Likewise, no arbitrary code execution or hidden-card disclosure was demonstrated; the proven security issues concern malformed scores, stale cleanup authority, and pool credentials.

## Recommended implementation order

1. TN-01 input validation and poisoned-save recovery; TN-02 ownership/idempotency; TN-03 custody durability.
2. TN-04/TN-05 together: one restart-safe completion/finalization mechanism, preserving separate regression cases.
3. TN-06/TN-07: authoritative allocation and explicit preparation substates.
4. TN-08/TN-09/TN-10: venue topology, historical standings, and result perspective.
5. Targeted tests for the additional gaps, then the full gate, a fresh graphical tour, and a real multi-player event with restart/disconnect/full-inventory cases.

Do not merge the test patch as evidence that fixes are done: it is a failing guard suite. Preserve each assertion while fixing the production path, and document any fixture adjustment. Rebase the findings against newer commits before implementation. No textures or sounds need to change for these fixes.
