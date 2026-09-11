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
| The separate server-backed practice table | **Retired.** No UI creates one, `PracticePayload.START` answers and starts nothing, and `PracticeTable.retire` takes leftovers in old saves apart from the table's own ticker. `PracticeTable.start` is kept, reachable from nothing in production, so the migration tests can build the legacy shape |
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
| Q11 recent **and pinned** token/counter names, per server, across restarts | 5 in-world tests in `RecentThingsGameTest` read the file back, switch servers and check nothing about a card reaches the disk. **The claim that such a test already existed was wrong** - only the pure `RecentsTest` existed, and `RecentThings` had no test at all |
| Ping rings the card and plays a sound | Scripted run asserts the ring; was previously a javadoc promise with no implementation |
| **QP-07 local tutorial overlay** — `TutorialDemo` builds a board in client memory from the pure core fold, and the table screen draws it | 8 in-world tests in `TutorialDemoGameTest`; the whole six-step lesson runs with a spy bound in place of the client sender and sends **nothing** |
| **Tutorial isolation is structural, not promised** — `ClientTableActions.send` routes on the demonstration's position, and `ClientNetworking.send` drops any `AtATable` payload addressed there | Both halves tested, including the negative case that a payload for a real table still goes. `tools/tablecheck.py` fails the build if a payload grows a table position without implementing `AtATable` — proved failing by reverting one |

### Implemented but NOT integrated — do not describe these as finished

| What | The gap |
|---|---|
| ~~Q05 `PendingWork`~~ | **Integrated.** Both screens that wait on the server - the decklist import and the deck builder - mint their id with `sent`, resolve it with `confirmed` or `refused`, draw `noteFor` while waiting and `forget` it on close. What it added is the state neither of them had: both used to wait for ever, with the button inactive until an answer arrived, so a reply that never came left a dead button and no reason. `worthMentioning` is the one method still without a production caller; `noteFor` answers the same question and is what the screens use |


### The settings that did nothing

Eight of the thirteen client preferences were never read by production code. They persisted,
clamped, round-tripped through the file and had tests proving they did - the tests tested the
setting rather than the behavior, which is why it survived so long. `waitingAfterMillis` was a
ninth until the pending-work batch earlier this session.

All thirteen are wired now and `tools/prefcheck.py` fails the build if one stops being read.
What each does: the sound toggle and volume gate `TableSounds` (silence is arranged by not
asking the sound engine, not by asking it for a sound at zero); reduced motion stops card
flights and the library shake but keeps the noise, because reducing motion is not removing
feedback; hold-to-inspect switches the read key between held and pressed; effect intensity
fades the foil sheen on **both** paths, the card in the world and the card being read;
the turn notification rings vanilla's bell and says "Your turn" for two and a half seconds;
text scale is a *request* honored wherever a line has room, so nothing can be pushed out of a
panel by turning it up; control scale sizes the context menu's rows, which are measured, drawn
and hit-tested from one number so they cannot disagree.

**Only reduced motion is verified by a test.** The rest are about pixels - a sheen, a text
size, a row height - which a dedicated server cannot draw. `prefcheck` proves they are read;
it cannot prove they look right.

### Known defects, open

| Id | What | Where |
|---|---|---|
| — | Client clips a gesture to 128 targets, but the server broadcasts the whole board per event, so a 100-card gesture is 100 broadcasts. Unmeasured | `TableActions.java` |
| — | A retry after "no answer yet" is only offered where it cannot cost anything - a decklist becomes a deck out of nothing, so the button comes back; a build from a collection takes real cards, so it does not. Whether that is the right split for the builder is a judgment, not a proof | `DecklistImportScreen.java`, `DeckBuilderScreen.java` |
| — | Scripted tour's board stops accepting moves in its late steps. Assertions were moved earlier rather than the transition being understood | `DevScene.java` |

Everything the September review raised is now closed. What is left above is a measurement
nobody has taken, a judgment call worth revisiting, and a scripted-run mystery that was worked
around rather than understood.

### Fixed this session, with the evidence

| Id | What was wrong, and what closed it |
|---|---|
| QP-05 | The binding lookup now happens *before* the seat check, so rebinding the log to Z moves it, unbinding it silences L, and a watcher reaches the three verbs that need no chair (log, framing, palette) instead of none. The key list interpolates 14 lines rather than 4, including the two that name more than one verb. The replay's own hardcoded `L` went the same way, and its camera section no longer promises that Home shows the whole table when Home goes to the start of the recording. `tools/keycheck.py` fails the build on a rebindable key written down in an input handler, or a help line with the wrong number of slots — both proved failing |
| QP-06 | See Q05 above. The policy - silence while young, "no answer yet" when overdue, never "failed" - lives in `PendingWork.noteFor` rather than in the screens, because a screen cannot be loaded in a test at all: a dedicated server refuses every client class one is built from. Two in-world tests cover it |
| Frame count | The tutorial's completion counted 240 **rendered frames** - four seconds at sixty a second, sixteen at fifteen, and never on a paused window. It is a 4,000 ms deadline read off `Util.getMillis`, taken on the tick, and `tick` now returns as soon as the lesson hands over — without that return the old screen read on, found an empty view and closed the screen it had just opened |

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

**The arrival, end to end.** Sit down, learn the controls on the local board, pick two colors,
land at the table. The colors screen used to close to the world, which left somebody who had
just been taught the controls standing in a field looking for the table they had learned them
for. Both ways out of it - taking the packs and pressing Escape - now go to the table, and both
ask what is still there rather than assuming.

**Retired, with migration.** Nothing creates a practice table. The START payload answers and
starts nothing, so an older client gets a sentence instead of a session. A leftover in an old
save is taken apart by `PracticeTable.retire`, from the ticker the table already has, the first
time its chunk loads - the flag comes off first and the session is ended second, so the ordinary
return path hands a real held deck back to whoever put it down instead of discarding it. That
order is the safety argument: interrupted between the two, the deck is still on an ordinary
table and the next ending returns it.

**Not done, and not to be described as done:**

- Nothing graphical has been run. The scripted client has not been run since before this batch.
  Remapped controls, small windows, large GUI scale and reduced motion are **unverified** for the
  overlay; the six steps are verified as state transitions, not as presses.
- `PracticeTable.start` and its eight lifecycle tests still exist. They are reachable from
  nothing in production and are what the migration tests build a legacy table with. Deleting
  them means deleting two of the external reviewer's own probes, which is the owner's call.

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
