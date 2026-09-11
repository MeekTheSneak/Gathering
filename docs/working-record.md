# Working record

The one place that says what is actually true right now. `CLAUDE.md` says how work is done;
this says where it has got to.

**Read this before continuing after a context compaction, and read the source it points at.
Everything here is a lead to verify, not proof.** Test counts are pasted from the run that
produced them and from nowhere else.

Last updated at `8aea932` plus the batch described below, which is now committed.

## Owner-approved requirements, and what they superseded

| Decision | State |
|---|---|
| **QP-07: the guided first game is a local interactive overlay** shown when a player first sits at an ordinary table, before real play | **Approved, not started.** Full spec in `docs/reviews/quality-progress-2026-09-11.md` |
| The separate server-backed practice table | **Superseded by QP-07.** Still present in the code; must be retired safely, with migration for saves that already hold one |
| No rules enforcement, ever | Standing |
| No player-supplied image URLs; the custom playmat is settled no | Standing — `docs/design-brief.md:313` |
| Textures and sounds are the owner's | Standing — `artcheck` holds 2,152 hashes |
| Starter boosters: welcome grant or completion reward? | **Open. Nobody has decided.** See "Decisions needed" below |

## Where the work is

The quality project (`docs/quality-backlog.csv`) is the spine. An external review of `8aea932`
is in `docs/reviews/quality-progress-2026-09-11.md`; its findings CSV is beside it.

### Implemented, integrated and verified

| What | Evidence |
|---|---|
| Q02–Q04 action catalogue, key mappings, client settings | In the gate since `06f18ae` |
| Q09 action palette — searches the menus themselves and runs their own callbacks | Gate green; scripted run asserts a searched verb taps the card |
| Q10 bulk bound (`BulkLimit`) and refusal folding (`Refusals`) | Both guards proved to fail without their fix |
| Q11 recent token/counter names, per server, across restarts | In-world test reads the file back; asserts no instance ids reach disk |
| Ping rings the card and plays a sound | Scripted run asserts the ring; was previously a javadoc promise with no implementation |

### Implemented but NOT integrated — do not describe these as finished

| What | The gap |
|---|---|
| **Q05 `PendingWork`** | **No production code calls `sent`, `confirmed`, `refused`, `of` or `worthMentioning`.** Only `clear`, from disconnect cleanup. No request carries an id from it and no screen renders its state. The backlog says Done; that is wrong and is being corrected. A unit-tested state container is a foundation, not the feature |
| Q11 pinned favorites | Recents exist; pinning does not |

### Known defects, open

| Id | What | Where |
|---|---|---|
| QP-05 | `TableScreen.keyPressed` intercepts literal `L` for the log **before** resolving bindings, so rebinding the log to Z leaves L opening it, and unbinding it leaves L working. Spectators take the early no-seat return and bypass mappings too. F1 help interpolates bindings for only four actions; the rest print literal keys | `TableScreen.java` — the `GLFW_KEY_L` branch and `KEY_LIST_ACTIONS` |
| QP-06 | Q05 as above | `PendingWork.java`, `ClientState.java` |
| — | Tutorial completion counts 240 **rendered frames** before sending STOP — four seconds at 60fps, sixteen at 15fps — and does the transition from `render` | `TableScreen.java` |
| — | Client clips a gesture to 128 targets, but the server broadcasts the whole board per event, so a 100-card gesture is 100 broadcasts. Unmeasured | `TableActions.java` |
| — | Scripted tour's board stops accepting moves in its late steps. Assertions were moved earlier rather than the transition being understood | `DevScene.java` |

### Fixed, committed

All four were reproduced failing on `8aea932` first, using the reviewer's own probes, now kept
at `neoforge/src/main/java/dev/gathering/neoforge/test/QualityReviewGameTest.java`.

| Id | Fix |
|---|---|
| QP-01 (P1, property loss) | Real deck intake is refused while a table is in practice, at the one shared commit boundary every route passes through. The return guard was **not** loosened — that would mint decks from invented cards |
| QP-02 | A practice session now records its learner; `PlayerGone.left` ends it, idempotently. Ordinary games still outlast a logout |
| QP-03 | Opening and refund receipts carry the colour. Fields are dash-padded so a three-field line written before the colour existed still reads, as no colour |
| QP-04 | Restart/Back/Forward take their baseline from the last confirmed board at the moment the step changes, instead of clearing it and spending the player's next action establishing it |

## Verification actually run

Paste results here from the run that produced them. Nothing in this section is from memory.

| Command | Result | When |
|---|---|---|
| `./gradlew :neoforge:runGameTestServer` on `8aea932` + reviewer probes | `4 required tests failed` — all four reproduced as the review described | before the fixes |
| `./gradlew :neoforge:runGameTestServer` after the fixes | `All 366 required tests passed` | after the fixes |
| `tools/gate.sh --game` | `gate green` — all thirteen checks. The gate summarises and does not print a test count; the 366 above is from the direct run, not inferred from this one | after the fixes |
| `tools/shots.sh` | Last clean run was before these fixes: `reached step 315 of 315`, `failures: 0` | on `8aea932` |

### What has never been verified, by anyone

- **No human has played this mod.** Every claim about how it feels comes from a headless gate
  or a scripted client that this project also wrote.
- No four-player session, no modpack session, no Create/Aeronautics/Cataclysm compatibility
  claim.
- No graphical run has exercised a *remapped* key. QP-05 exists because nothing ever rebinds
  anything before pressing it.

## Decisions needed from the owner

1. **Are starter boosters a welcome grant or a completion reward?** `StarterBoosters` enforces
   one grant per player per world but never checks that the tutorial was finished, and a valid
   `StarterPayload` can ask for them directly. Either answer is fine; the code should say which.
   Local tutorial progress must not be the proof either way.

## Next concrete action

1. **QP-07** — the local overlay, replacing server practice. Acceptance checks are listed in
   the review; the short version is that a tutorial action must produce zero gameplay packets
   and zero changes to inventory, seats, rewards or any real match, proved with a send-spy and
   a live table in the background. Retiring the old practice path needs idempotent migration
   for saves that already hold a practice table with a real deck on it.
2. QP-05, then QP-06.
3. Re-run `tools/shots.sh`: the last clean scripted run predates these fixes.
