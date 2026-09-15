# Working record

The one place that says what is actually true right now. `CLAUDE.md` says how work is done;
this says where it has got to.

**Read this before continuing after a context compaction, and read the source it points at.
Everything here is a lead to verify, not proof.** Test counts are pasted from the run that
produced them and from nowhere else.

Last updated 2026-09-13, after the cleanup roadmap and table presentation refactor. The quality backlog is 20 of 28 done and the cleanup roadmap 12 of 14 rows done; everything still open on either needs a person, a graphical client run, or another mod's files - see "What is left, and why each one needs you".

## Table presentation refactor on the finished cleanup baseline

Based on `690753b8cf51bbcad31eb50819b2eaabff1f75eb`, preserving the completed cleanup work.
`TableScreen` is now 6,101 lines instead of 6,469. `TableCardRenderer` owns painting and
`TableReplayControls` owns transport input/painting using the existing `ReplayStrip`.
`CardCounterLabels` prepares text for immutable view identities, bounded by both retained
entries and per-card counter/name limits. Font widths and wrapping remain live. No server
protocol, save format, game rule, texture or sound changed.

See `docs/refactoring/table-presentation.md` for the boundaries, cache contracts and commands.
The final canonical gate passed: 1,645 core tests reported (one dataset-dependent skip,
no failures), 427 NeoForge and 10 Fabric in-world tests, and all fourteen static checks.
Nine of the NeoForge tests cover the new cache.
The focused client probe matched the baseline's wide-card, narrow-card and replay frames
exactly (1,708 by 960, zero changed pixels in each). Replay keys, bar dragging/release and
modal closing produced the same requests as before. The final allocation experiment measured
2,440–2,536 bytes/card for uncached text preparation and zero for warm-cache lookup;
continuous misses measured 2,472–2,648. This measures preparation, not FPS or all rendering.

**The full graphical tour is not green, including on the untouched baseline.** Both clean
runs reached step 303 of 315 and reported the same distinct failures: initial setup/tutorial
interference, the late lesson waiting for PLAY when the script expects TAP, and an existing
`screen.gathering.table.seat_marked` truncation. The repeated failure count was 731 in each
run because a stuck step repeats its assertion; it is not 731 separate defects. A first
refactor run stopped earlier at token input; a clean repeat with passive prompt logging
accepted “Treasure,” opened AmountScreen and passed the remembered-token menu. Its original
cause was not established. Do not turn these results into a claim of a clean full playtest.

The full-tour comparison preceded the final cache-retention limits; the final focused probe
was rerun afterward. These limits have dedicated tests and leave oversized cards drawable.
This pass does not close the remaining graphical/modpack work or CL-08's DevScene relocation.

### Integrated, and what its tour found

Applied to `claude/new-session-beye3i` as supplied (`b3592aea`, gate 427/10), then a second
commit restoring the explanatory comments the move had cut down to one-liners and bringing the
new classes into the repository's brace style (`016f2256`, gate 427/10).

The bundle's full graphical tour failed identically on the untouched baseline, so those
failures were already in the branch. Investigated from its log and the source, not rerun here:

- **The tour predated the local lesson.** A fresh profile is offered the guided first game the
  moment it sits down or asks a table for a game, so the tour's first table screen became the
  lesson and every setup-screen check after it failed. The lesson section then asked whether the
  lesson was running *at the practice table's position* and read the board from there - the
  retired design - while the demonstration lives at its own position; the draw step passed, the
  play step found no board, and the run stuck on PLAY at step 303. **Fixed in the script:** the
  tour marks the lesson as offered when it enters the world, the lesson section reads the
  demonstration's position, and the ending now waits for the color wheel to appear by itself
  and checks it hands back to the table's setup screen - the real arrival - instead of pressing
  Leave and opening the wheel by hand.
- **A real text defect.** `screen.gathering.table.seat_marked` was cut to an ellipsis: on a
  427-pixel window with several seats, each column in the top strip is about sixty pixels and
  the full "name - life | hand | library" line cannot fit even at the smallest text size.
  **Fixed:** the strip now picks the longest of three forms that fits whole - the full line,
  then name and life, then life alone beside the seat's mark - and a free chair has a short form
  too. Nothing the short forms drop is lost; it is on the mat.

### The scripted client, run on this machine (2026-09-13)

The tour was run here on macOS (`./gradlew :neoforge:runClient -Pdevscene`, Gradle heap 1.5 GB
and client heap 3 GB - the defaults ran the machine out of memory once). Seven runs; the last
two reached **315 of 315 with zero failures**. What the runs and the screenshots found, all fixed:

| Found | How | Fix |
|---|---|---|
| Scryfall was down for maintenance | first run: deck import HTTP 503, every later step empty | waited it out; not a mod defect |
| Setup screen: "First time? Learn the controls." ran through Cancel and Start | screenshot | the sentence is the Learn button's tooltip |
| Lesson strip read "(empty) - 40 life (away)" twice, turn "Seat 1" | screenshot | the lesson seats both chairs: this player by name, and "Demo" |
| Lesson step 5 asked to read the card opposite, which was off the top of the window | screenshot; the script passed only by calling the read hook directly | the lesson frames the whole table, turned from the learner's chair |
| Lesson panel covered the learner's own Draw and Untap buttons | screenshot | the panel narrows to stop short of the mats (to 120 wide) |
| Back and Skip vanished under the panel from step 5 on | screenshot | the lesson buttons are re-placed under the panel every frame |
| "Start over" crowded its button at the narrower width | screenshot | label is "Restart" |
| A finished lesson reported as unfinished | tour FAIL: it closes itself after a 4 s linger | step checks the recorded completion once the lesson has gone |
| Foil tilt steps saw no tilt when the machine was in use | tour FAIL on one run only | the tour turns off pause-on-lost-focus |
| The lesson's play step dropped the card on the other chair's mat after the reframe | tour FAIL | drops on the learner's mat as the board reports it |
| The pot drew over the top strip's text | screenshot | the pot is clipped between the strip and the hand |
| "Mulligan" and "Shuffle" lost their last letter to the button frame | screenshot | verb labels fit inside a 3-pixel inset |
| One coin flip was re-announced across the felt every time a dialog closed | contact sheet: the same flip in four later shots | each table remembers the last roll it announced; `RollAnnouncementGameTest`, **shown to fail** on the old logic |
| A replay's first frame offered "Free seat - sit down with a deck" | contact sheet | replays say "Free seat" |

All 153 screenshots were reviewed, the lesson and the changed screens at full size and the rest
as contact sheets. Eight runs in all; the last four reached 315/315 with zero failures.

Seen and **not fixed**, recorded here instead:

- A card the server cannot find stays "Loading" on every screen for good. The tour's draft uses
  invented printings, so every draft slot said it; real cards resolve. The fix needs the server
  to answer "not found" for a metadata request, which is a protocol change.
- A table-talk line in the HUD is drawn over the card-reading panel in the world.
- On the board drawn on the block, the life box and near cards can sit under the top strip's
  text; the world cannot be clipped the way the seated screen's pot now is. **A scripted run is not a playtest**; nothing here says how the lesson feels to a
person, only that it can be completed with the keys it names and now shows what it describes.

## Refactoring opportunities review (2026-09-14) and owner requests since

An external review of `10960959` proposed six items (RF-01..RF-06). Progress, newest last:

| Item | State | Evidence |
|---|---|---|
| RF-01 DevScene out of release jars | **Done** (`219e758c`) | Both jars: 0 DevScene classes (Fabric jar -116,856 bytes). Gate 428/10. NeoForge tour from the new dev source set 315/315, 0 failures. Fabric tour 315/315 with **one failure at step 181** (key 9 did not put a card under the library) - open, not investigated |
| Owner: other players' hands and life counters fixed to the table, not the camera | **Done** (`f4f79bb5`) | The only screen-anchored pieces left on the seated board are the top strip, your own hand and screen UI. Tour 315/315, 0 failures |
| RF-02 shared client payload application | **Done** (`769532c4`) | One typed route list; both loaders check coverage at client start. Gate 428/10; NeoForge tour 315/315, 0 failures. Fabric client not re-toured for this change |
| Owner: several different tokens with one name (Cats, Elves) | **Done** (`569ef142`) | Exact-name search, a chooser when tokens differ, card rows make the linked printing, remembered rows keep the variant. Protocol 4. Gate 431/10; choice test shown to fail on make-the-newest |
| Owner: ante pot out of the way of the board | **Done** (`544b2175`) | A column past the east edge of the whole surface; "whole table" frames it. Pure properties for 1-8 seats. Gate 431/10. The tour's pot check passed, but its photograph had no pot - see the next row |
| Fix: other hands, pot and table talk invisible to anyone not seated | **Done** (`92c4abc0`) | Clip band bottom read the top of an absent hand (0). `TableScreenLayout#tableArea`; layout property shown to fail on the old edge; the pot step now checks the drawn band. Regressed in `f4f79bb5` for other hands; pot and talk older |
| RF-03 held-deck custody as one value | **Done** (`f782685a`) | One ordered `Map<SeatId, HeldDeck>`; NBT unchanged. Six custody tests; run against the old code, the four new guards failed and the two behaviors meant to stay the same passed. Found and fixed: sideboarding re-owned the deck to the editor; an offline owner's deck went to the chair's occupant; `endSession` and malformed loads left orphan pools. Gate 437/10. A malformed held deck is still logged and not written back (unchanged) |
| RF-04 stage A: gesture owner | **Done** (`b0476ea5`) | `TableGesture` (pure) owns the carried card, the drag latch, box and pan starts; transitions unit-tested. Gate 440/10. Graphical run with it and every change above: **315/315, 0 failures** |
| RF-04 stage B: one mode | **Done** (`1c987e51`) | `TableMode` (playing / learning / watching) replaces the replay and demo flags one for one. Gate 440/10. Graphical run killed for low memory at step ~300 with four failures before it: the replay scrub check (a fixed wait on a rate-limited frame) and a late set-progress screen over the collection with its knock-ons; both passed in the stage A run. Re-run with smaller heaps: **315/315, 0 failures** |
| RF-04 stage C: action bindings | **Not done, on purpose** | Keys, menu rows and palette rows already dispatch through the one `doAction` switch and the palette is built from the menus, so a binding layer would be the second registry the review says not to add |
| RF-05 world counter label profiling | **Measured; no change** | A temporary in-world measurement (run once, not committed) of `markUp`'s preparation on four dense Commander boards, 200 cards, 15 per board with counters, 2 walkers, 1 written on: 28-41 us and 89 KB per frame, measured beside the test server. Well under 1% of a frame; below the review's own threshold for a cache. Glyph drawing not measured |
| RF-06 missing-metadata outcomes | **Done** (`5c5bfe61`) | `CardsUnresolvedPayload` (missing / unavailable), `UnresolvedCards` client tracker (4096 cap, missing believed 30 min, unavailable never blocks retry). All seven "Loading" sites ask `ClientCardCache#unnamed`. Protocol 5. In-world sorting and cache tests; failed-batch test shown to fail when an outage is reported as missing. Gate 440/10. Not yet seen on screen: no scripted step reaches a missing or unavailable card |
| Owner: reorder the hand by dragging | **Done** (`1617ba4d`) | Drop over the strip takes the nearest place and sends `HandSorted`; the fan parts under the carried card. Pure placement properties; tour step 183-184 drags the first card to the end and checks the server's order. Gate 440/10 |

**Open from the tour runs:**
- The second run after the pot change failed cursor-hover steps (life counter, graveyard, written
  card tooltips) and the pen for power and toughness (steps 164-206) while the owner was using the
  machine; the run before it passed them with the same hover code. Treated as the real cursor over
  the window, not proven.
- Two runs after the token change showed card-targeting steps (172-181: reading a written card,
  freezing, keys 7 and 9) failing when a run before them had passed the same steps with the same
  code; the failing run used a smaller heap. Not reproduced deliberately, not explained.
- Step 172 (a written card's tooltip) failed in three runs: the cursor was pointed five steps before
  the read. Fixed in the script (`cbcc3fbf`); the next run passed it.
- The machine repeatedly killed tour runs for low memory while other applications were open.
  Tours need roughly 5 GB free (1-1.5 GB Gradle, 3 GB client).

## Tournaments (design in `docs/tournaments.md`)

The owner reviewed and approved a tournament design on 2026-09-14; decisions, abuse rules and the
build order T1-T7 are in `docs/tournaments.md`. Progress:

| Step | State | Evidence |
|---|---|---|
| T1a pure event rules (`PodSettings`, `PodLobby`, `PodShares`, host pick count) | **Done** (`c41a8caa`) | Core tests incl. a conservation property; set-named-twice guard shown to fail; pod codec v2 reads v1. Gate 440/10 |
| T1b packs held by a sign-up (`PodSignup`, `PodSignups`) | **Done** (`7519a00e`) | 9 in-world tests following each pack through put-in, refusal, standing up, cancel, owner away (owed), restart, unreadable settings, broken table, nothing else starting; broken-table guard shown to fail. Creative players now lose the pack they put in, like a deck. Gate 449/10 |
| T1c opening into a draft or sealed pools (`PodEvents`, `PodRecord`, `PackOpening.draw`) | **Done** (`39fbeb68`) | 6 in-world tests with supplied pack contents (no pipeline in tests), cards followed by identity; three guards shown to fail. Real drawing only exercised by the graphical client. Gate 455/10 |
| T1d screens (create, sign-up) and payloads, protocol 6 | **Committed** (`4b8845f1`), graphical run pending | Tour steps 315-321 create a one-pack sealed event through the screens and check the pool. Gate 455/10 |

| T2 tables played apart (`TablesApart`, `playsApart`) | **Done** (`fb3c5ff7`) | NeoForge split and lock tests; Fabric: three games on a long table played apart, and a sign-up's packs coming back. Protocol 7 |
| T3 tournament engine (pure, `core/tournament`) | **Done** (`0647cd29`) | Swiss with folded seeds and no rematches, byes, drops, standings with the 33% floor, time and extra turns, top-cut bracket, codec. Seed-fold guard shown to fail |
| T4-T7 events in the world, venues, records, top cut and prizes | **Done** (`f2ae9390`) | 8 in-world tests: seating, time and extra turns, 5-minute grace, suggested result, locked deck, save round trip, ratings (6-player minimum, pair limit, exclusion, void), prizes. Protocol 8. Guards shown to fail: locked deck, extra turns, pair limit |
| Tour of the tournament screens | **Run** (tour after `f2ae9390`) | 337 steps, 2 failures, both the script's: it tried to settle a second round that the rules had rightly dropped the never-online opponent from. Screenshots 100-105 reviewed; found and fixed a clipped "Constructed", a "Pick" button narrower than its word (now "Settle"), "None" for no top cut, and no pointer or move shown after round one |
| Pick clock, registration point, practice board, config, abuse guards | **Done** (the commit after `f2ae9390`) | Gate 471/13. See "Tournament finishing batch" below. Graphical run of the new steps pending |

### Tournament finishing batch

- **Pick clock** (`PickClock` pure, `PickClocks` on the table tick, `DraftViewPayload.secondsLeft`,
  countdown on the draft screen). Found while testing it: **a table with no game on it never
  ticked**, so the pick clock and the hand-back of an unreadable sign-up's packs did nothing in a
  world. The sign-up test called the tick by hand and hid it; it now waits for the real ticker.
  Both guards fail with the old ticker condition.
- **Ratings loophole closed:** an empty set of played tables used to mean "every result counts",
  so an event whose results were all typed in, with no game at any table, moved ratings. Guard
  shown to fail with the old condition. Official events at full weight versus half is tested on
  a one-round event, exactly twice.
- **Seating in a venue:** a round used to teleport anybody within 24 blocks, which in a hall of
  tables is everybody. Now only a player already sitting at that long table is moved; the rest
  get the pointer (owner decision). 16 players on 8 separate tables: seated, matches started,
  labels numbered, nobody moved. Guard shown to fail with the old rule.
- **Registration point**, **host cooldown** and **rated minimum** as config
  (`events.host_cooldown_minutes`, `events.rated_min_players`, readable by `/gathering settings`),
  **practice board** on the event screen, tour steps for practice and the pointer.
- Saves: tournament codec v2 and pod record v2 add the clock; both read v1, tested byte for byte.
  Protocol 9.
- **Tour after `564c1665`:** 340 steps, 1 failure: resting the cursor on the graveyard (step
  ~44) named nothing, the same hover step that has failed before while the real cursor was over
  the window; unrelated code, not reproduced. Screenshots 100-105 and 102a reviewed, and they
  found two real defects the automated checks had passed: **the practice board was empty**
  (it dealt only the main deck, and a drafted pool is all sideboard; the tour's step checked
  only that practice had started), and **a player moved into their seat faced away from the
  table** (north and south yaws swapped). Both fixed; the tour now counts the practice library
  and checks the facing. The event-kind buttons were still cramped; labels narrowed.
- **Tour after `f87447c5`:** 341 steps, 1 failure (the same graveyard hover). Confirmed on screen:
  practice deals the 23-card pool; the host is seated facing the table. Found: **the floating
  table label was cut in half** (letters and their backing drawn at one depth, so turned to the
  camera half of each line was lost) and, from a chair, filled the top of the view; "Draft" and
  "Sealed" still touched their borders. Fixed: backing and words drawn in two passes (the words
  with a polygon offset, as sign text is), the label hidden within 3 blocks of the table's
  middle, a seated player looks down at the felt, and the create screen's left column is the
  wider one. The tour now photographs the seat and then the label from 8 blocks back.
  Three tour runs of these fixes were killed by the machine for low memory; the fourth, with a
  1.8 GB client heap and no Gradle daemon, completed.
- **Tour of `81e22a4f`:** 342 steps, **0 failures** (the graveyard hover passed this time).
  Seen: the whole label "Table 1 / Dev - Opponent / 49:50" from 8 blocks back, the seated view
  looking down across the mat, and "Constructed" fitting. The right column's "Any" and "Locked"
  then touched their borders, so the columns are split 21:19 with more padding per toggle (not
  yet re-photographed).

### Tournament audit (2026-09-14) and fixes

An external audit of `63bd0410` (`docs/reviews/tournament-audit-2026-09-14/`) reproduced ten
defects with failing guards. All ten confirmed in the source and fixed; the response, finding by
finding, is `RESPONSE.md` beside the audit. The audit's 2 core and 9 in-world guards pass; 6 more
in-world guards (`EventsIntegrityGameTest`) and 5 core tests cover the cases it asked for, and
each in-world one was shown to fail with its fix removed. Gate 486/13 (`2860be8a`). Tour after
the fixes: **342 steps, 0 failures**, the tournament created, played through a round, advanced by
the round clock and finished. The two
guards that first still failed after the fix (advancement after withdrawal and after reload) did
so because the test server ticks faster than real time, and the pause between rounds had been
moved to real time; it is counted in ticks again.

Still open: a sign-up locked by an opening that never completes stays locked until restart (the
lock is not saved); prize descriptions use server-side item names; no two-process restart,
socket disconnect or load test; one pod per event.

## Security review (2026-09-14)

Asked for by the owner. Full record, finding by finding, with what was not fixed and why:
`docs/reviews/security-review-2026-09-14.md`. Two critical ways to create unlimited cards (loaner
decks; cube drafts), an absent player's deck and stake left on the table for others, a replay
showing an opponent's deck mid-match, an uncapped game log and card-lookup queue a client could
use to lag the server, and a set of medium and low items - all fixed. 10 new in-world guards and
7 new core tests, each in-world guard shown to fail with its fix removed; one existing test
rewritten because it asserted the theft path. Protocol 10.

Also fixed after: creative mode wiped a moved deck's cards (a server hook on the creative slot
packet, both loaders, guard on each; the Fabric guard caught that Fabric loaded the mod's mixin
config on the client only, so it now loads on both sides with the camera hooks still client-only). The first move budget (30/s, burst 256) dropped moves in
the scripted tour's pile steps; raised to 60/s, burst 1,000.

Gate 495/14 (`20a645df`). Tour after the fixes: 342 steps, 1 failure - a watcher's hover tooltip on
a life counter, the same class of real-cursor hover step that has failed one at a time across runs
(graveyard, Draw button, life counter) with no related change; the loaner, draft, ante and
tournament steps all passed.

Open, for the owner: seated players may draw, mill or shuffle another seat's library by design;
pack and shuffle randomness is
`SecureRandom`, an undocumented but stronger exception to the `level.getRandom()` rule.

## Memory audit (2026-09-14)

Asked for by the owner. Every static collection, static reference to a world, player or screen,
texture registration, thread pool and in-memory cache on both sides was checked for how it is
emptied.

**Sound:** the only static world reference (`ServerRun.running`) is cleared on stop; no static
players, entities, block entities or screens; client textures are released against a byte budget
and set-symbol textures past a cap; every per-player server cache is forgotten in
`PlayerGone.left` and every holder is cleared on stop (`statecheck` enforces the second);
client per-table state is dropped per table and on disconnect; the card and collation workers
are shut down on server stop on both loaders; the tick scheduler and every throttle map have a
bound.

**Fixed:**
- **Card metadata was the one large grower.** Every card a server has looked up is held for name
  lookups, measured at **5.3 KB per card** on the 3,037-card development cache (about half a
  gigabyte for every English printing). A heap histogram showed 62 strings per card, most of
  them repeats: 21 format names in every legality table, set names, type lines, artists. The
  codec now shares repeated words and whole legality tables (bounded pools), and prices are a
  compact immutable map. Measured again: strings 188,000 to 70,000, live heap for the cache
  about 16 MB to 9.7 MB. Guard: `ScryfallCardCodecTest.repeatedPartsOfCardsAreKeptOnce`, shown
  to fail without the shared tables.
- Per-player budgets added in the security review (moves, lookups, event actions) are now
  forgotten when a player leaves, not only when the server stops.
- `Events.goneSince` is swept on each disconnect of players no event is waiting on.
- The rating ledger kept every pair of players who had ever met, saved with the records; pairs
  whose meetings have all aged out of the seven-day window are dropped.
- Every HTTP transport made its own JDK client, each with a selector thread, again for every
  single-player world opened; they now share one.

Not changed: image URLs are still six strings per card face (about a fifth of what remains),
because each is distinct and shortening them would change the saved cache format.

## Rules and tournament pass (2026-09-14, while the owner was away)

Checked against the Comprehensive Rules and the Magic Tournament Rules. The MTR sections were
read from the judges' rules blog (MTR 2.2, 2.4, Appendix E), not recalled. Nothing here enforces
a rule of the game: each is a count shown, a default the table starts from, or tournament
procedure the event already ran.

**Changed:**
- **London mulligan counted** (CR 103.5, free first mulligan in multiplayer 103.5c): the seat
  shows how many cards are owed to the bottom, and putting a card from hand on the bottom of the
  library counts one off. Nothing makes anybody do it.
- **Loss thresholds shown in red**: life 0, poison 10, commander damage 21. Displayed, never acted on.
- **Tournament draft timing** (MTR Appendix B) as a pick clock choice, "Tourney".
- **Who plays first** (MTR 2.2): random for a Swiss match's first game; the higher Swiss seed for
  a cut match's first game, even when an upset put them in the second chair; the loser of the
  previous game after that; and after a drawn game, whoever went first in it. The mod picks
  *play*; the player can pass the turn. Saved as `drawn_game_chooser` beside `last_winner`.
  The log event's boolean became a one-byte reason in the same place, so old logs read the same.
- **Result buttons cover every way a match ends** (1-0 at time, 1-1, 0-0-1, 0-0 intentional
  draw), in two rows; a cut offers no draws, which the server refused anyway. The host's settle
  buttons moved to their own row; a dozen of them overlapped Done.
- **A cut match tied on games at the end of extra turns goes to the higher life total** (MTR 2.4).
  Tied on life too, it stays open for the host, and the table is told so instead of "recorded".
- **Shopkeeper test flake fixed:** `lookingTwiceChangesNothing` failed once in the gate because the
  shop shelf is swapped by a worker and by other tests' settings between its two looks. It retries
  on later ticks; a look that really restocks still fails on every one.
- **Swiss round counts past 128 players follow Appendix E** (8 to 226, 9 to 409, 10 beyond);
  they had kept doubling.
- **Standings in columns** (#, player, points, W-L-D, OMW%, GW%, OGW%).
- **Sealed events gray out the pick clock and picks-at-a-time** instead of taking presses that did nothing.

**Bug fixed - it crashed the server:** in a best-of-one event, a player gone five minutes
conceded 2-0, which is not a best-of-one result. Settling it threw inside the server tick, and the
mutation run shows what that did: "Exception in server tick loop", the whole server down. A
concession is now the games it takes to win a match of the event's length. Guard:
`EventsGameTest.aplayerGoneFromABestOfOneConcedesOneGameToNone`. And because one tournament's clock
should never be able to do that, each event's tick now runs contained: a failure is logged and told
to the host once, and the server and every other event carry on
(`EventsIntegrityGameTest.aFailingEventClockDoesNotStopTheServer`, proved failing without the
catch). A table's own tick (pick clock, signup hand-back, ambient board) is contained the same way;
that one has no reproduced failure behind it and no test of its own. Because contained failures no
longer stop an in-world run, the gate gained a stage that fails on any such failure logged in the run, except the
test that breaks a clock on purpose.

**Guards proved to fail without their fixes** (sources mutated, run, restored, tree confirmed
clean): the best-of-one concession (crashed the run), `thehigherseedplaysfirstinacutmatch` and
`whoeverwentfirstinadrawngamegoesfirstagain` (2 required tests failed, exactly those).

- **Building time follows Appendix B:** 25 minutes after a draft, 30 for sealed (it was 30 for
  both). The create screen follows the kind until the host sets their own.
- **Oathbreaker has no commander damage** (oathbreakermtg.org's rules have no such rule, and a
  signature spell never deals combat damage). The counters panel offered a 21-damage row for every
  opposing oathbreaker and signature spell; it now shows those rows in Commander only. Guard
  `TableGameTest.onlyCommanderCountsCommanderDamage`, proved failing without the fix.
- **The host's settle buttons are two rows**, and the drop buttons are as wide as their labels.
  The tour now photographs them (`105a-settling-a-table`) and fails if any host button is too
  narrow for its label - the first photograph showed "1-0-1", "Drop 1st" and "Drop 2nd" clipped.

- **Going first in a two-player game says "no draw this turn"** (rule 103.8a; nobody skips in
  multiplayer, 103.8c) under the status row for that player's first turn. Nothing is drawn or
  withheld: the table never draws for anybody.
- **Choice screens go to one column when a name does not fit half the panel.** The dungeon
  picker showed three names shrunk by different amounts beside "Undercity" at full size. The tour
  now lists every button narrower than its label at each photograph (report only: icon buttons
  carry narration labels and would all fail).

**Independent review of this pass** (a reviewer agent given the requirements and the diff, read
only). Nine findings; checked, and these fixed:
- The "lost the last game" log line showed its raw key: the reason's name had changed and the
  key is built from it, where langcheck cannot see. Guard `StartingPlayerTextTest`, proved failing.
- A drawn best of one, or a drawn last game, threw away who had chosen for it, so the replay went
  to chance or gave the wrong reason. Guard in `MatchStateTest`, proved failing.
- Result grids lacked 2-0-1 and 2-1-1 in a best of three, and 2-0, 1-0 and 0-0-1 in a best of five.
  A best of five with a game unfinished and somebody ahead is still offered only without the
  unfinished game (thirty buttons otherwise; the count only moves game-win percentage).
- A cut match tied on games and life said "the host decides" while the next turn pass could record
  it. MTR 2.4's sudden death is what that already did, so the message now says to play on.
- On a window shorter than the panel, the host's settle buttons covered the last pairings. Pages
  now hold as many rows as fit above them.
- A choice screen with many long names went one to a row and pushed Cancel off a short screen; it
  falls back to two across when one column will not fit.
- Switching an event to Sealed and back to Draft lost the pick clock.

Left, and why: a first player who passes the turn to draw leaves the opponent starting on turn two
unreminded (reminders only); a cut game restarted by hand rather than by the event starts at
random.

**A chair somebody sat in and left before their deck went down is not a player.** It kept the name,
so a two-player game counted three: the first mulligan went free (103.5c) and the first-draw
reminder never showed. Players are now boards with a name and cards. Guard
`LondonMulliganTest.aChairWithANameAndNoDeckIsNotAPlayer`, proved failing.

**Results follow players, not chairs.** A tournament table read wins and life by chair - the first
player in chair 0 - so a pair who swapped chairs were suggested, timed out and tie-broken with each
other's games. They are now found in the game by who is sitting where. Guard
`EventsGameTest.theSuggestedResultFollowsPlayersWhoSwapChairs`, proved failing. A match's running
score is still kept by chair between games, so swapping chairs between games still swaps it.

**Checked and left alone:** five extra turns after time (MTR 2.4, the setting's default is right);
tiebreakers and their 33% floors, byes as 2-0 and left out of opponents (Appendix C); top-8 bracket
seeding; deck validation.

**Differences from the MTR kept on purpose, for the owner:**
- Round one is paired by rating seed, not at random (the anti-sandbagging decision).
- Appendix E runs 9-16 players as 5 rounds and a top 4 outside draft-playoff events; the mod
  plays 4 rounds, and its cut is the host's choice.
- A drawn game uses up one of the match's games, the last one included (the owner's decision); the
  MTR (2.1) plays on until somebody has won the games needed.

## Owner-approved requirements, and what they superseded

| Decision | State |
|---|---|
| **QP-07: the guided first game is a local interactive overlay** shown when a player first sits at an ordinary table, before real play | **Approved and implemented.** The overlay is isolated, the old server-backed path is retired, and legacy practice tables in saves are migrated on their first tick. Graphically unverified. Full spec in `docs/reviews/quality-progress-2026-09-11.md` |
| The separate server-backed practice table | **Retired.** No UI creates one, `PracticePayload.START` answers and starts nothing, and `PracticeTable.retire` takes leftovers in old saves apart from the table's own ticker. Creating one is no longer in the jar: the migration tests build the legacy shape with `LegacyPracticeTables`, a fixture in the game-test source set (CL-09) |
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
| — | **Measured, not fixed.** A gesture on a selection costs one full board build per card *per recipient*: six taps sent six boards to one seated player and two sent two (`BulkBroadcastGameTest`). At a four-seat table with two onlookers a hundred-card gesture is about six hundred view builds, each walking every zone of every seat through the visibility rules. Coalescing was considered and **not** done - see below | `TableActions.java`, `TableBroadcast.java` |
| — | A retry after "no answer yet" is only offered where it cannot cost anything - a decklist becomes a deck out of nothing, so the button comes back; a build from a collection takes real cards, so it does not. Whether that is the right split for the builder is a judgment, not a proof | `DecklistImportScreen.java`, `DeckBuilderScreen.java` |

**One entry was withdrawn rather than fixed.** The record listed "the scripted tour's board
stops accepting moves in its late steps, assertions moved earlier rather than the transition
being understood" as an open defect. It is not one: step 104 stands the player up on purpose
to look at the table as a watcher, and step 105 asserts the seat is gone. Every step after
that has no seat by design, so the palette check sitting before it is correct placement rather
than a workaround. `DevScene` now names the step, so the next person reading it does not have
to rediscover this.

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
- *(Resolved by CL-09.)* Practice creation moved out of production into a game-test fixture. See the cleanup roadmap progress section.

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
| `tools/gate.sh --game` | `gate green` — all thirteen checks as they stood then. Both the flag and the count are historical: the gate is one command now and covers both loaders. See the CL-13 note below | after the fixes |
| `tools/shots.sh` | Last clean run was before these fixes: `reached step 315 of 315`, `failures: 0` | on `8aea932` |

### What has never been verified, by anyone

- **No human has played this mod.** Every claim about how it feels comes from a headless gate
  or a scripted client that this project also wrote.
- No four-player session, no modpack session, no Create/Aeronautics/Cataclysm compatibility
  claim.
- No graphical run has exercised a *remapped* key. QP-05 exists because nothing ever rebinds
  anything before pressing it.

## The follow-up audit, and what it found

An external audit of `9fa4f737` ran the graphical client this session could not, and found
four defects - every one of them in work done this session. All four are fixed; the review is
kept at `docs/reviews/quality-followup-2026-09-11.md` with its screenshot.

| | What was wrong | Fixed by |
|---|---|---|
| **QF-01** | Opening the counters editor from the lesson **tore the lesson down on the way in**. `removed()` fires whenever a screen is replaced, including by its own child, so a normal route to the lesson's fourth step abandoned it. The editor also looked its board and seat up through `ClientTableState`, which answered only for real tables - so it found no seat, sent nothing, and closed itself on the next tick | Cleanup moved to `onClose()`, which means "this player is leaving"; `ClientTableState.viewOf` answers for the demonstration, so every screen keyed by a position agrees |
| **QF-02** | Applying a **stale** Tidy preview moved a card back out of the graveyard. The plan stored ids and positions and rechecked nothing | `ArrangeSelection.isStale` / `stillStanding` in core; the preview is dropped on the tick when a planned card leaves, and filtered again at the press for the frames in between |
| **QF-03** | A malformed edit to a **working** reward file discarded that reward. `readInto` skipped the bad file and `reload` published the rest, against the documented all-or-nothing contract | Nothing is published unless the whole folder reads; a clean reload still drops a deliberately deleted definition |
| **QF-04** | Text and control scales are independent, and the card menu sized its rows from the control scale alone - so at text 200% with controls 75% the rows **drew through one another**. Columns were measured unscaled too, so short labels rendered at full size and long ones were squeezed: one menu in three sizes | `InterfaceScale.rowHeightFor` in core, tested across every pair of scales; columns measured at the size text is actually drawn; the row height is decided once per menu so measuring, drawing and picking cannot disagree |

**The lesson, again.** Three of these four are the same shape as the ones found earlier in the
session: the gate was green, the tests passed, and the thing was broken where a player would
meet it. QF-01 in particular is a lifecycle hook doing the right thing at the wrong moments -
exactly what the workflow's "check every exit" step exists to catch, and it did not, because I
checked which exits existed and not which ones `removed()` actually fires on.

**What this does not prove.** Only QF-03's fix is verified end to end by a test I can run.
QF-01's lookup and QF-04's rendering are verified by rules extracted into core and by
reasoning; the client probes that found them need a display. The audit's own harnesses are the
way to confirm them, and they are in the bundle.

## CL-08: the tests were shipping in the jar

Every copy of the mod anybody installed carried all seventy-five NeoForge game-test classes,
because they lived in `neoforge/src/main`. `:fabric` had solved this long ago with a `testmod`
source set whose jar carries none - NeoForge was the outlier, and mirroring what already
worked here was the whole fix.

The 69 test files now live in `neoforge/src/gametest`. The jar carries **0** test classes and
the suite still reports **402**, which is the pair that matters: moving tests somewhere the
runner cannot see them would have been the obvious way to get this wrong, and the gate's new
zero-discovery stage exists precisely to catch it.

**DevScene is still in the jar, deliberately.** It is 245 KB and the single largest file in the
repository, and its own javadoc says it is "never referenced by anything that ships" - which is
untrue: `ClientTicks.tick` calls `DevScene.tick` directly, and `ClientTicks` ships. Removing
that reference means something else must register the scene when its property is set, and that
registration is the one thing here that **cannot be verified without a display**. Getting it
wrong breaks `tools/shots.sh`, which is the tool most worth running right now. It should be
done after a known-good graphical run exists to compare against, not before.

## CL-13: there were two gates, and they disagreed

`tools/gate.sh` ran `./gradlew build`. `verify` ran `:core:test`, the architecture fences,
datagen, **and both loaders' in-world tests**, plus its own check that it still covers what it
claims. Neither ran the other. So "gate green" in every report before this one meant NeoForge's
game tests and no datagen, and Fabric's ten in-world tests were only ever run by whoever
happened to type `verify`.

Nothing was hiding behind it - the first full run passed, 402 and 10 - but that was luck rather
than knowledge, and it is exactly the shape of thing the gate exists to stop being luck.

There is one gate now. `tools/gate.sh` runs verify, then the fourteen static checks, then a new
stage: **both loaders must report a nonzero test count**. A suite that discovers nothing passes,
so a renamed annotation or a source set that quietly stopped being scanned would have read as
green. That assertion was checked against all four cases it exists for - two counts, one
count, a zero, and none - before being relied on.

`--quick` is the build and the checks, for iterating, and it says it is not the gate when it
finishes. `CLAUDE.md` and `tools/README.md` now describe what actually runs; the latter had
said "the eight checks" since there were eight.

## The cleanup audit: two follow-ons fixed, a roadmap open

A second external audit of `0e2502ed` re-ran the client probes. **Three of the four earlier
fixes hold** - the lesson survives opening a child screen, the counters editor reads the
lesson, and a card moved to a graveyard stays there. Two probes failed, and both were
incompleteness in those same fixes rather than new ground:

- **CL-01 - Tidy still overwrote a newer rotation.** The staleness check asked whether a card
  was still on the battlefield, but the plan also carried the angle the card had when the
  preview was drawn. Turn a card afterwards and applying straightened it. The class javadoc
  already claimed "nothing here returns an angle, so nothing here can straighten one" - the
  code disagreed. A `Spot` is now two coordinates with no angle in it at all, and the move is
  built with the rotation the card has at the moment of applying. A card that has since become
  attached to another is skipped too, for the same reason a graveyard card is.
- **CL-02 - the menu still ran off the screen.** Fixing the vertical overlap by measuring
  columns at the asked text size made the menu 792 pixels wide in a 427-pixel viewport: one
  bug traded for another. `MenuFit` in core now solves all three constraints together - honour
  the asked size where it fits, wrap into columns when too tall, and only then shrink,
  uniformly. Tested against the audit's own viewport at every pair of sizes.

The rest of the audit is a fourteen-item roadmap (`docs/reviews/cleanup-2026-09-13-roadmap.csv`)
covering release packaging, bulk-action batching, snapshot reuse, search caching and
decomposing `TableScreen`. Its status column is kept current and is the per-item record; the
notes below are what a status line cannot hold.

### Cleanup roadmap progress

| Id | State | The part worth knowing |
|---|---|---|
| CL-01, CL-02 | Done | Above |
| CL-13 | Done | Below, "there were two gates" |
| CL-08 | Partial | Tests out of the jar; DevScene is not, and needs a display to verify |
| CL-05a | Done | The query is parsed once per request. 5.893 ms to 2.141 ms at 10k rows, on this machine, with an equivalence test over twelve queries |
| CL-04 | Done for the board | A quiet table sends nothing after its first push, keyed on `GameSession.revision()` and the audience. `AmbientBoardGameTest` checks both halves: silence, and that a changed board and a newly arrived spectator are still sent one. **The first commit of this (`a3bd7b69`) went in with the gate red** - a pipeline read `tail`'s exit code rather than the gate's - and was fixed in the next commit. Commits now check the gate's own exit code |
| CL-11a | Done | `Prompts` and core `ListScroll`; four panels and two lists |
| CL-09 | Done | Below |
| CL-05b | Done | Below |
| CL-06 | Done, not seen | Below |
| CL-10 | Partial, not seen | Below |
| CL-11b | Done | Below |
| CL-03 | Done, not timed live | Below; supersedes "Why the bulk broadcast was measured and left alone" |
| CL-07 | Done, profiled | Below |
| CL-12 | Done, profiled, no threads | Below |

**CL-09.** `PracticeTable` now holds only what production needs: `retire`, the answers to an
old client's START and STOP, `isPracticeAt` and the demonstration seat. Creation lives in
`neoforge/src/gametest/.../LegacyPracticeTables.java`, unchanged in what it builds.

Doing it found a real property-loss path first. STOP still ran the old ending, which discards
whatever the table holds. The ticker retires a legacy table on its first tick, but an old client
can send STOP before that - and in a save written before the intake guard, the table can be
holding a deck somebody built. Reproduced at zero copies (`astopbeforethefirsttickkeepsarealdeck`),
fixed by routing STOP through `retire`, which takes the flag off before ending anything.

Two things went with creation, and are recorded rather than quietly dropped:

- The per-learner map that let a disconnect end a practice game. Only a live start ever filled
  it, so it could only be empty. The external reviewer's `disconnectMustCleanUpPractice` probe
  was adapted rather than deleted: same fixture, same shared disconnect hook, same three
  things that must not be left behind, but checked after the table has ticked. **Shown to
  fail** with the ticker's retirement disabled, and only that test failed.
- Two tests of the old start path's refusals (a table with a game on it, a table somebody else
  is at). They described what an unreachable feature would have declined. The STOP-on-a-real-
  game test was kept and now goes through the production network entry point. The suite went
  406 to 404 for exactly those two.

Six `message.gathering.practice_*` strings only the old start's outcomes used were removed;
`practice_retired` stays.

**CL-05b.** Paging a collection used to search it again for every page. `CollectionView` now
keeps each player's last ordered answer and reads it back only when everything it was built
from is unchanged: the same box object, the same revision of its counts (new,
`CollectionBlockEntity.revision()`, moved by every count change and by a load), the same cards
loose in that player's pockets, the same card service at the same generation of what it knows
(new, `InMemoryCardMetadataStore.generation()`, moved by every store), and the same question.
The generation is read before any card is looked up, so a name landing mid-build files the
answer as already stale.

`CollectionResultsGameTest` has one test per input plus the saving itself (three pages, one
search). **All six safety tests were shown to fail** against a deliberately weak cache keyed on
the box position and shared between players; only those six failed. The page is built by a
new `pageFor` so a test can read what a stand-in player cannot receive.

Not measured: the saving per page flip. CL-05a measured the search it skips at about 2 ms for
ten thousand distinct cards; what a page flip now costs on a real server is inferred, not timed.

**CL-06.** `BoardPresentation` in core works out what a board looks like before it is put on
a screen - each seat's piles, attachments, and every visible card by id - once per view, kept
by identity in a small `Memo`. The seated `TableScreen` and the block's `TableMiniatureRenderer`
both read it; before, the screen worked it out at least twice a frame and the renderer once per
table per frame. Rectangles are still computed per frame, because they move with the window,
camera and held card when the board does not.

`TableStacking.piles` answers depth, pile size and burial in one pass over a grid of cells just
wider than the stacking distance. The audit warned that grid bucketing can change stacking
behavior; this one compares exactly the same pairs the old walk did, and a jqwik property checks
all three answers against the plain definition on clustered boards with cell-edge and table-edge
positions. **Shown to fail** with the neighbouring cells left out.

Measured on this machine, pile work a frame used to do versus one pass: 0.085 ms to 0.031 ms at
200 cards, 0.586 ms to 0.116 ms at 800. Those figures understate the old cost - the benchmark's
"before" already used the new depths - and an unchanged frame now does none of it. They are core
microbenchmarks, not frame times.

`TutorialDemo.board()` built a fresh view on every call, many times a frame, which would also have
defeated the memo. It is now rebuilt only when the demonstration's `GameSession.revision()` moves.

**One visible change, not seen.** The block used to count a card attached to another at its own
recorded spot, which the seated board stopped doing because it made a lone creature with an aura
read as a pile. Both now use the seated rule, so a card dropped on an aura's old spot no longer
leans on the block. `BoardPresentationTest` pins the rule; nobody has looked at it.

**CL-10, partly.** Three pieces, one per responsibility, as the audit asked:

- *Presentation*: CL-06 above.
- *The replay transport*: `ReplayStrip` in core now owns where the four buttons, the bar and
  the count go and what a click on the strip means. The screen drew it, hit-tested it and
  scrubbed with it, working the rectangles out separately each time. `ReplayStripTest` checks
  that each button answers for itself, nothing overlaps, the whole strip height answers for the
  bar, drags clamp, and a click on the bar fills it back to the click within a step.
- *Inspecting a card picked from a pile*: the audit's one behavior change. Choosing a card from
  "Others here" opened its menu over the pile's top card, and the read key read whatever was
  under the cursor - the top card - so the chosen card could not be read. While a card menu is
  open the read key now reads that menu's card, looked up in the current board so a card that
  has left or turned face down is not read from a stale picture.

**Not done, and why it stopped here.** A mode context for live/demo/replay, a pointer controller
and one action binding shared by menu, palette and keys. Nothing automated exercises
`TableScreen` - client classes cannot load in the game-test server - so every extraction is
verified by the compiler and by reading. The three above are small enough for that. The
remaining ones move input handling, and should be paired with a scripted client run.
**All three pieces above are graphically unverified.**

**CL-11b.** `GatheringProtocol` in common is the one list of payloads: type, codec, direction,
and for serverbound ones the handler. Each loader walks it through a generic helper that keeps
type, codec and handler agreeing at compile time - no cast, no reflection - and adds only what
is its own: NeoForge's protocol version and handler thread and its context-to-player check,
Fabric's type registries. NeoForge's file went from 499 lines to 79, Fabric's from 335 to 56.

Checked before the switch rather than assumed: a script read both loaders' registrations and
the new list and found the same 34 serverbound and 22 clientbound types in all three. One small
difference went away in passing - four NeoForge handlers cast the context's player without
checking it; every route now checks. Both game-test servers boot on the shared list, and
`ProtocolGameTest` checks each id is listed once, in one direction, in this mod's namespace.
What a client does with a payload is still wired in each client bootstrap, so a dedicated server
still names no client class.

Not run: a real dedicated server and a real client connecting across the two loaders. The game-
test servers prove registration succeeds, not that a live connection negotiates.

**CL-03.** A verb on a selection - tap, freeze, turn, counters, moves to a zone, grouping, tidy -
now goes as one `TableActionsPayload` instead of one payload per card, and the table is shown
its board once instead of once per card per viewer. The three reasons this was left alone
earlier (the section below) were each answered rather than set aside:

1. *Game-end ordering.* The server applies each move through `TableActions.apply` - the same
   method a single action uses now - and after every move asks whether the game is finished.
   If it is, it shows that board and settles immediately, before looking at the next move,
   which then finds the game put away exactly as a separate packet would have.
2. *One event per card as the authority argument.* Kept whole. Nothing new crosses the wire:
   the batch is a list of the same encoded events, each judged alone by every gate. A refused
   move is refused without taking its neighbours with it.
3. *Unmeasured benefit.* Still not timed on a live server. What is measured is the count: one
   board where there were eight, in the game tests; the audit's core microbenchmark put 128
   changes at six viewers at 43.60 ms against 1.97 ms for final views only.

Bounded on the server regardless of the client: at most `BulkLimit.MOST_AT_ONCE` moves applied
per payload whatever it was built with, 512 bytes per move at decode, and the client splits a
selection into batches under 16 KB. A single move, or an event too big for a batch, goes as a
single action. `PROTOCOL_VERSION` is 3. The single-action path is unchanged, and
`BulkBroadcastGameTest` still pins that it costs one board per move.

Separately, everybody watching a table who is not seated now shares one public view per
broadcast - built and encoded once, sent to each - where each used to get their own copy of
an identical view. Seated players still get their own view.

`BatchedActionsGameTest`: one board for eight moves; every gate on every move; game-end
settling mid-batch; the server-side bound; out-of-reach; shared spectator view. **Shown to
fail**: a board after every move fails the first; no bound, no mid-batch settle and a view per
spectator fail the other three, and nothing else.

Not verified: the client half. `ClientTableActions.sendAll` and the screen's four bulk verbs are
compiled and read, not run - no automated check loads client classes - and nobody has watched a
selection animate after the change. Card flights are worked out from the difference between
boards, so one board should still move every card; that is reasoning, not observation.

**CL-07, profiled first.** The roadmap said to measure before touching state copying, so the
measurement came first: a core benchmark folding 128 ordinary events - 64 taps and 64 moves on a
four-seat table with 80% of each library on the battlefield - best of seven trials after warmup,
with thread allocation counters.

| Cards | Before | After |
|---:|---|---|
| 400 | 2.40 ms, 8.70 MB | 1.81 ms, 3.95 MB |
| 1,600 | 6.65 ms, 32.23 MB | 3.09 ms, 14.64 MB |

The cause was the record's constructor copying every map it was given, every time, including
maps the previous state had built a line earlier and maps it was passing on unchanged -
changing whose turn it is copied the whole card map. `FrozenMap` lets a state keep a map it owns
outright: `of` copies anything it has not seen before, `adopt` (package-private) takes a map
built on the line before without copying, and neither hands out anything writable. Nothing about
the event-sourced model changes; undo, restore and refold go through the same transitions and the
whole core suite passes untouched.

`FrozenMapTest`: an earlier state is unchanged by every later one; nothing a state hands out can
be written to, down to an entry's `setValue` and a zone's list; a caller's map is copied, so
changing it afterwards changes no state (**shown to fail** with `of` adopting a caller's map);
insertion order is kept. No location index was added - nothing measured asked for one.

These are core microbenchmark numbers, not server tick times.

**CL-12, profiled first, and the profile said no worker.** Measured on this machine: saving a
12,000-event session (`StoredSession.of`, encoding and sealing) about 1.3 ms, 2.4 MB; writing a
400 KB replay 0.4 ms mean, 0.6 ms worst over 35 writes; listing a 40-replay shelf 0.1 ms. A game
ends once and a table saves on the autosave, so a background writer - with its own shutdown
drain, stale-destination and "recorded before it was" risks, all of which the audit listed -
would buy about two milliseconds once per game. Not done, on purpose.

What the profiling turned up instead were two write-safety defects, both fixed synchronously:

- **A settings change that failed to write was lost.** `ClientSettings` marked itself saved
  before trying to write, so a briefly unwritable folder - a full disk, a sync client's lock -
  dropped the change for good. It now stays unsaved until a write succeeds, warns once rather
  than every second, and a failed schema migration is retried the same way. The new check in
  `ClientPreferencesGameTest` blocks the folder, changes a setting, unblocks it and expects the
  change on disk; **shown to fail** on the old code.
- **An interrupted write left half a file.** Settings and replays are now written beside the
  target and moved into place, as `RecentThings` already did. A half-written settings file used
  to be refused on the next launch, fall back to defaults and be written back over everything
  the player had chosen; a half-written replay sat on the shelf under the real suffix. The
  interruption itself is not tested - it would need killing a process mid-write.

`Replays.keep` also copied the whole record list twice and now copies it once.

Not measured: close and save latency of a long session on a real server with a real disk.

### Independent review of the cleanup batch

A reviewer agent was given the ten requirements above and the diff `daf8aa9a~1..HEAD`, before
any narrative, read-only. **No high or medium findings**; it checked each requirement against
the enforcing code (listed in its report: batch gates and ordering, spectator exclusion, cache
keys and revision bumps, FrozenMap mutators and adoption sites, grid-cell arithmetic, payload
parity and client-class isolation, practice retirement order, the read-key lookup). Five low
findings, investigated:

1. *`TableActionsPayload`'s comment claimed any payload meeting its bounds fits a packet.* It
   does not: the bounds allow about 66 KB against a 32,767-byte serverbound limit. Only the
   client's 16 KB split keeps batches sendable. The server-side bound is unaffected. **Comment
   corrected** to say so and to route construction through `sendAll`.
2. *A failed atomic write left its `.writing` file behind*, unlisted and never deleted - one per
   game on a nearly full disk for replays. **Fixed**: both writers delete the leftover.
3. *First-launch settings were still written straight over the file*, while the new comment
   implied every write was protected. **Fixed**: both settings writes share one helper, and its
   comment now says it protects against a process stopping, not a power loss (no fsync).
4. *Any metadata store invalidates every collection answer*, including the names a page's own
   lookup fetches, so page two of a cold collection searches again. Conservative and correct;
   left as is. Noted with it: the cache holds up to 64 collection block entities strongly until
   disconnect.
5. *The block now piles attached cards differently.* Intended and recorded under CL-06.

Gate after the fixes: green, 418/10.

The audit also measured the bulk-broadcast cost independently and agrees with the number
recorded above: 128 changes across 400 cards cost 43.60 ms and 95 MB where six final views
would cost 1.97 ms and 7 MB. That is the strongest single argument for CL-03, and it is still
a synthetic core benchmark rather than a live server.

## What is left, and why each one needs you

Nothing below is blocked on work anybody could do in this repository. Each is blocked on
something this machine does not have.

| | What it needs |
|---|---|
| **Q08** Validate first-game usability | The scripted graphical client (`tools/shots.sh`, ~18 min) and then a person. Nothing graphical has been run this session at all, so the tutorial overlay, the settings screen, the owner badges, the arrange outlines and the under-cursor chooser have never been looked at. |
| **Q16** Crowded-board interaction checks | Four-seat fixtures with screenshots and frame times, on real hardware. Frame times measured through software rendering in a container are not numbers anybody's machine would see. |
| **Q19** Playtest and tune the collection journey | People playing for long enough to have an opinion. `docs/playtest.md` is the form. |
| **Q22 / Q23** Cataclysm and Create examples | The actual mod artifacts at exact versions, to check advancement ids, loot table paths, recipe types and reload behaviour. The reward contract they would use is done and tested against an absent mod. |
| **Q24** Pack-author guide | Written (`docs/pack-authors.md`), and it states plainly which parts are unverified. It cannot be finished until Q22/Q23 are. |
| **Q27** Release acceptance | Everything above, plus a human sign-off. |
| **Q12** second half | Time-to-completion, mistakes and newcomer observations. The gesture-count half is done in `docs/quality-after.md`. |
| **CL-08** second half | DevScene out of the release jars. `ClientTicks` calls it directly, so it needs a development hook that registers it only in a dev run - and the only way to know the scripted client still starts afterwards is to start it. Do it after a known-good graphical run. |
| **CL-10** remainder | Mode context, pointer controller and one action binding for menu, palette and keys. These move input handling in a screen no automated check loads; each needs a scripted client run beside it. |
| **CL-14** Modpack qualification | Exact installed versions of Create, Aeronautics and Cataclysm, and real hardware for the p50/p95/p99 matrix. |
| Live timings for CL-03/05/06/07/12 | Every number recorded for those is a core microbenchmark or a game-test count on this machine. Server tick and client frame times under a real crowded game have not been taken. |

**The most valuable single thing anybody can do next is run the scripted client and look at the
pictures.** Everything client-side from the quality project and the cleanup roadmap is unrendered.

## Why the bulk broadcast was measured and left alone

*Superseded by CL-03 above, which answers each of the three reasons below. Kept because the
reasons are what the implementation had to satisfy.*

Deferring the broadcast to the end of the tick would collapse a hundred boards into one, and
the arithmetic says that is worth having. It was not done, for three reasons that a later
session should weigh again rather than inherit:

1. **Game-end ordering.** `TableActions.handle` broadcasts and *then* calls
   `TableMatch.settleIfFinished`, deliberately: "a move that ended the game is still a move,
   and everybody should see the board it ended on before it is taken away." A deferred
   broadcast settles the match first, and the last board of a game is the one nobody would get.
   Any coalescing has to flush before settling, which means knowing the move ended the game
   before asking whether it did.
2. **The client's one-event-per-card model is a deliberate property**, not an oversight:
   "what gets sent is the same events one card at a time would have sent, so a selection cannot
   do anything a sequence of ordinary moves could not". Batching on the wire would weaken the
   argument that a selection grants no new authority.
3. **The benefit is unmeasured in the units that matter.** The count is known; what it costs in
   frame time or bandwidth on real hardware is not, and this machine cannot say. The working
   agreement is explicit - measure before adding caching or concurrency - and a correctness
   risk taken against an unmeasured benefit is the wrong trade.

What is now in place is the number and a test that fails if the shape of the growth changes in
either direction, so whoever does take this on starts from a measurement.

## The quality pass, and what it found in this session's own work

A sweep for public methods called only from tests turned up 55, most of them legitimate - test
seams and the state queries tests use to check things. Four had no caller anywhere, and three
of those were dead limbs left by this session's own changes:

- **`acceptPracticeBoard` could never do anything, and ran every frame.** Retiring practice
  removed the only caller of `Tutorial.expectAt`, so `expecting` was always null, so the
  method returned immediately - from the render path, on every frame, for ever. Gone, along
  with `expectAt`, `expectedAt` and `Tutorial.restart`, which had become unreachable the same
  way.
- **Two branches pretended a non-demo tutorial still existed.** `leaveTheTutorial` and the
  completion handler both had a `!demo` path, and one of them still sent the retired
  `PracticePayload.STOP`. Unreachable, and misleading to anybody reading it.
- **`PendingWork.worthMentioning` was a second definition of "overdue"**, redundant with
  `noteFor` since the integration landed. Two ways to decide whether something is late is one
  more than a screen can be shown at once.

A separate read of this session's own new code found two more:

- **The settings screen rebuilt its widgets from inside a button press**, which clears the list
  the click is still being dispatched over. Every other screen in the mod rebuilds from a tick
  or a scroll. It defers to the tick now.
- **The settings screen offered a text size the setter refused.** Its steps began at 50 while
  `SMALLEST_SCALE` is 75, so choosing it showed 50, stored 75 and drew at 75 - a control
  lying about what it just did, with every part of it looking right on its own. The steps now
  come from `ClientSettings.offeredSteps()`, beside the bounds that clamp them, and a test
  walks every offered value through the real setter. Proved failing by putting 50 back.

Also checked and sound: `ClientTableState.acceptPayload` runs through `enqueueWork`, so the
settings read it now does is on the client thread rather than the network one.

## The pattern worth remembering

Four times this session a feature turned out to be documented, tested and never connected:

- `PendingWork` — a state container with no production caller, marked Done in the backlog.
- `RecentThings.counters()` — counter names written to disk on every use and read by nobody.
- The turn notification — a setting for a feature that did not exist.
- **Eight of thirteen client preferences** — all persisted, all clamped, all tested, none read.

Every one of them had passing tests. The tests tested the setting, the helper or the round
trip; none tested the behaviour. `tools/prefcheck.py` and `tools/tablecheck.py` now fail the
build for two of those shapes, but the general lesson is the one in the workflow already: **a
helper is a foundation until a real player action reaches it and its result reaches the
player.**

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

1. ~~Should a drawn game use up one of a match's games?~~ **Decided by the owner (2026-09-14): yes,
   the last game included.** A drawn decider, or a drawn best of one, used to be played again
   because the game number stayed on the last game; it now ends the match, drawn.
2. **Appendix E runs 9-16 players as five Swiss rounds and a top 4** unless the playoff is a
   booster draft; the mod plays four, with the host choosing the cut.
3. **Are starter boosters a welcome grant or a completion reward?** `StarterBoosters` enforces
   one grant per player per world but never checks that the tutorial was finished, and a valid
   `StarterPayload` can ask for them directly. Either answer is fine; the code should say which.
   Local tutorial progress must not be the proof either way.

## Next concrete action

1. **A person plays the lesson and a real game.** The scripted tour now completes cleanly on this machine (315/315) and its screenshots were reviewed; what is left is how it feels to somebody who has not read the code (`./gradlew :neoforge:runClient -Pdevscene`
   on macOS; `tools/shots.sh` under Xvfb). Every client-side change since the last clean run is
   unseen: the tutorial overlay, menu fitting, the arrange preview, pile and attachment drawing
   on both views, the replay strip, reading a card chosen from a pile, and a selection verb
   animating after it became one batch.
2. With a known-good run to compare against: the second half of CL-08 (DevScene out of the
   release jar behind a development hook) and the rest of CL-10 (mode context, pointer
   controller, shared action binding). Both change what the scripted client exercises.
3. CL-14 once the pack's exact Create, Aeronautics and Cataclysm versions are known.
