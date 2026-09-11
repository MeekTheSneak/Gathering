# Working record

The one place that says what is actually true right now. `CLAUDE.md` says how work is done;
this says where it has got to.

**Read this before continuing after a context compaction, and read the source it points at.
Everything here is a lead to verify, not proof.** Test counts are pasted from the run that
produced them and from nowhere else.

Last updated at `52dda92` plus the QP-07 batch described below.

## Owner-approved requirements, and what they superseded

| Decision | State |
|---|---|
| **QP-07: the guided first game is a local interactive overlay** shown when a player first sits at an ordinary table, before real play | **Approved. The overlay is implemented and isolated; retiring the old path and migrating saves is not done.** Full spec in `docs/reviews/quality-progress-2026-09-11.md` |
| The separate server-backed practice table | **Superseded by QP-07.** No ordinary UI now starts one, but `PracticePayload.START` is still handled and the save flag still exists. Must be retired safely, with migration for saves that already hold one |
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
| **QP-07 local tutorial overlay** — `TutorialDemo` builds a board in client memory from the pure core fold, and the table screen draws it | 8 in-world tests in `TutorialDemoGameTest`; the whole six-step lesson runs with a spy bound in place of the client sender and sends **nothing** |
| **Tutorial isolation is structural, not promised** — `ClientTableActions.send` routes on the demonstration's position, and `ClientNetworking.send` drops any `AtATable` payload addressed there | Both halves tested, including the negative case that a payload for a real table still goes. `tools/tablecheck.py` fails the build if a payload grows a table position without implementing `AtATable` — proved failing by reverting one |

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
| — | ~~Tutorial completion counts 240 **rendered frames**~~ **Fixed.** It is a 4,000 ms deadline read off `Util.getMillis`, taken on the tick, and `tick` now returns as soon as the lesson hands over — without that return the old screen read on, found an empty view and closed the screen it had just opened | `TableScreen.java` |
| — | Client clips a gesture to 128 targets, but the server broadcasts the whole board per event, so a 100-card gesture is 100 broadcasts. Unmeasured | `TableActions.java` |
| — | Scripted tour's board stops accepting moves in its late steps. Assertions were moved earlier rather than the transition being understood | `DevScene.java` |

### QP-07, what is done and what is not

**Done and in the gate.** The lesson is a local board: `TutorialDemo` builds a two-seat
`GameSession` in client memory, deals twenty blank cards a side, puts one written card face up
in front of the seat nobody is in, and hands the table screen a `GameView` through the ordinary
`VisibilityRules`. It needs no server, no seat, no block, no deck and no card download, so it
works on a fresh install with no network. `TableScreen.learning` opens it; the first sit-down of
a client's life gets it automatically, once, from either sit-down path.

**Isolation is by construction.** The demonstration's board is filed at a position no table can
occupy, and two choke points route on that position rather than on a screen's idea of its own
mode: `ClientTableActions.send` for every game verb, `ClientNetworking.send` for whole payloads.
That second one matters because the counters screen and the pile screen send their own moves,
and one of the six steps happens in the counters screen. `AtATable` made "which table is this
for" a question that can be asked of a payload without knowing which payload it is.

**Not done, and not to be described as done:**

- The old practice path is not retired. `PracticePayload.START` is still handled, `PracticeTable`
  still exists, and `TableBlockEntity` still saves a `practice` flag. No ordinary UI reaches it
  any more, which is exactly the state the review warned is insufficient.
- **No migration exists** for saves that already contain a practice session, a demonstration
  occupant, or a real held deck at a practice table. This is the next action.
- Nothing graphical has been run. The scripted client has not been run since before this batch.
  Remapped controls, small windows, large GUI scale and reduced motion are **unverified** for the
  overlay; the six steps are verified only as state transitions, not as presses.

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

## The starter boosters question, answered by the code

The record above had this open: *welcome grant or completion reward?* Reading
`StarterBoosters.handle` settles what is **currently true**, whatever the documentation says.
The server checks three things — two distinct colors, the server's own `givesAStarter` setting,
and a once-per-player ledger in the save. **It has never checked whether anybody finished the
tutorial.** So it is already a welcome grant that the tutorial happens to be the usual door to,
and a client has always been able to ask for it without doing a lesson at all.

That is why moving the lesson onto the client changes nothing about reward authority: there was
no client completion flag being trusted, because there was no check. The color screen is still
offered on finishing, because that is the moment the question makes sense to ask, and finishing
a second time is still told "already" by the same ledger.

**This is a statement about the code, not a decision.** Whether it *should* be a welcome grant
is still the owner's to make.

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
