# Working record

The one place that says what is actually true right now. `CLAUDE.md` says how work is done;
this says where it has got to.

**Read this before continuing after a context compaction, and read the source it points at.
Everything here is a lead to verify, not proof.** Test counts are pasted from the run that
produced them and from nowhere else.

Last updated 2026-09-14, after the security review, memory audit and a rules and tournament pass (see "Rules and tournament pass"). The gate is green at 503 NeoForge and 14 Fabric in-world tests. The quality backlog is 20 of 28 done and the cleanup roadmap 12 of 14 rows done; everything still open on either needs a person, a graphical client run, or another mod's files - see "What is left, and why each one needs you".

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

## Create, Create Aeronautics and Sable (2026-09-14, CL-14)

The owner supplied the pack's jars: Create 1.21.1-6.0.10, Create Aeronautics bundled 1.3.2, Sable
2.0.5. Opt-in runs `runPackClient`, `runPackServer` and `runPackGameTestServer` load whatever jars are
in `neoforge/runs/pack/mods` and `neoforge/runs/pack-tests/mods`; the gate never uses them.

**All in-world tests with the pack loaded.** With Sable (alone or with Aeronautics) every test that
makes a stand-in player fails, 186 of them, on "Payload sable:dimension_physics / simulated:end_sea
may not be sent to the client": those mods send every player a payload the test server's embedded
connection has not negotiated. Every other test passes. That is a limit of the harness, not a
failure of this mod, and it is why the structure tests below use no player.

**Tables on moving structures.** `WorldSpace` (common, a service) converts between a table's block
coordinates and the world; NeoForge implements it with Sable's companion library, bundled via jarJar.
Used for the clicked side (`TableBlock`), tournament seating (`Events.chairInWorld`), the registration
distance and nearby check (entity distances, which Sable corrects), the seat pointer (`EventHud`,
`EventPointers`), the seated camera (`TableCameraView`) and board picking (`TablePointer`). Three
in-world tests registered only when Sable is installed (`PackGameTests`, `SableTablesGameTest`) assemble
a table into a structure and check where it is, which side a player at a turned table is at, and where
a chair is - each shown to fail with the conversion removed (and one fixed after it passed anyway,
because it compared against the same conversion). On a client the conversions use Sable's render pose
(where the structure is drawn this frame) rather than its logical pose, so the seated camera and pointer
do not sit a tick ahead of a moving ship (2026-09-15; the pack scene's still structure is unchanged at
0.515 blocks). **Not verified:** a real client on a moving vehicle.

**Create display boards.** The owner asked for the tournament to be read off a host block, with what
is shown chosen. A Display Link against a **Scorekeeper's Desk** offers one source, *Tournament*
(`TournamentDisplaySource`), with a *Show* setting in the link's screen: Standings (the record left
out on a display board, which is too narrow for it), Pairings, Round and clock, Final places, Prizes,
Signed up. Against a table it offers This Table's Match and Life Totals; a table no longer offers the
whole tournament. They read `EventBoard` and `TableBoard` in common - public results only, as the
event screen shows them - and are registered only when Create is installed (`CreateCompat`).
`EventBoardGameTest` (gate) checks the data; `CreateDisplayGameTest` (pack tests) places real Display
Links on a table and on a desk and reads every choice of *Show*, and was proved failing with the
setting ignored and with the tournament attached to tables.

**Scorekeeper's Desk** (`ScorekeepersDeskBlock`, both loaders). The host's click on a free desk links
their unfinished tournament and makes the desk its registration point; anybody else's click shows the
tournament; another host (or the same host, for another of their tournaments) takes a desk over only by using it
twice within ten seconds - the first use says whose desk it is - and the tournament that loses it loses
its registration point; a finished or cancelled tournament's desk is free; breaking the desk clears the
registration point. A host running more than one tournament links the one whose tables are nearest the
desk - found in the pack scene, where a tournament left over from an earlier run was linked in place of
the one beside the desk (guard `aHostOfTwoLinksTheOneBesideTheDesk`, proved failing; the scene's save
is now cleared before each run, as `shots.sh` does for DevScene). Crafted paper-book-paper over planks.
Model and shapes are vanilla's lectern (no texture of its own - the owner's to draw).
`ScorekeepersDeskGameTest` (8, gate) uses the desk the way a player's click does, hand and all:
linking, look-only for a passer-by, a second use taking over (and one too late not), a host's own desk
taking signing up back, two tournaments, a free finished desk, breaking, its shape, save and load; plus
`CraftingGameTest.aScorekeepersDeskCanBeCrafted`. Each proved failing with its code broken.

**Desk polish from the Create study** (`docs/reviews/create-aeronautics-study-2026-09-14.md`): the desk,
the Collection and the Shop Counter say what they are for in their tooltips (guard
`BlockTooltipGameTest`, proved failing); a linked desk floats its tournament's name and phase, kept up
by its own once-a-second tick and sent as the label only (guard `aLinkedDeskLabelsItsTournament`,
proved failing both with the tick removed and with the event id in the client tag); linking turns a
lectern's page. Seen in the pack scene: "Friday Night / Round 1 of 2" over the desk.

**Ponder scene for the tables, and a renderer defect it found.** `TablePonderScene` (all four tables,
and the wooden one in Create's Display Link sources tag). `TableMiniatureRenderer` now draws a game only
for a table in the client's own level: elsewhere (Ponder, a Create schematic preview) it looked the game
up by position alone and drew a real board on a stranger, and ran `TablePointer.capture` with the wrong
projection. The pack scene files a real board under the scene table's position and photographs the
scene: with the guard off the board drew (seen), with it nothing does. Event labels there face north.
Not a gate test - it needs a client with Create.

**Create contraptions** leave tables and collections in place (their `PushReaction.BLOCK`, which Create's
movement check honors) and carry a desk. Pack test `CreateContraptionGameTest`, proved failing with the
table's push reaction made normal. Documented for pack authors. A table on a moving Create contraption
is not supported: its game lives at a block position the contraption's copy of the world lacks.

**Ponder scene for the desk** (`compat/create/client`, NeoForge with Create, client only): W over the
desk teaches it on Create's Display Link schematic. Pack scene checks the scene exists, the desk is in
Create's Display Link sources tag, every recorded line equals the lang file's (7 lines), and photographs
it mid-scene: the board shows standings and the desk's label "Friday Night / Round 3 of 4". A client scene
run, not a gate test. Its first run found the
label invisible (faced to the player's camera, not Ponder's) and that was fixed. The line check was shown failing with one lang line changed.

**Tour polish (2026-09-15)**, from the fresh tour's photographs: the host's Settle toggle was an
11-pixel button in a line of text, its art broken and its word off its face - host pairing rows are 16
tall now; the collection footer's first line was drawn over the grid's bottom wall; the event screen's
own-row highlight was a painted rectangle, against the rule that every tint is a theme's sprite (now
`ROW_HIGHLIGHT`); its page arrows looked live with one page (grayed, like the event list's); the pick
clock buttons sharing the "Packs each" row say what they are on hover. Two more painted colors, found looking for
others like the highlight: the action palette's picked row (now `ROW_HIGHLIGHT`) and the replay ruler's
track, fill and head (now the theme's scroll track and thumb, sliced thin enough for a 6-pixel ruler);
both photographed in a later tour, which passed. Left as they are: the card pointing ring and `GuiGlow`'s
disc, which tint by meaning - the design brief's exception for color that is information. `tools/spritecheck.py`
now fails on any `graphics.fill(` in a client class other than those two (proved by putting the
highlight's fill back), and its first run found a fourth, the verb button's reminder mark, now the
theme's lit pip - **not photographed**: the tour reaches no verb reminder. Rerun tour: failures 0, and the
Settle row, footer and highlight photographed and looked at. The tooltip and grayed arrows were not
photographed.

**Found and fixed: a table carried onto a Sable ship doubled its decks (2026-09-15).** A new pack test
held a deck on a table and assembled it into a Sable structure: the copy on the ship held the deck
and one more lay on the ground where the table stood. Sable writes a block entity down, loads the copy
in the ship's region, then clears the old blocks - and clearing a table ends its game and hands back
its decks and pot, while the copy still held them (and a live copy of the game). Staked cards the same.
A table now carries an identity in its saved data (`TableCustody`, a random UUID - it names, it decides
nothing); removed while a loaded copy with that identity stands elsewhere, it was carried, and hands
nothing back; anyone looking at its board is told it has gone. Sable loads the copy into the level
before its data, so a table is counted only once its identity is read or written. Pack test
`aDeckOnATableCarriedOffIsNeitherLostNorDoubled` (failed 1+1 before, 1+0 after); gate test
`aCarriedTableTakesItsDeckWithItOnce` copies a table's data the way any mover does, proved failing with
the check off, and breaks the copy afterwards to see the deck come back once. Set down again with
Sable's `moveBlocks` (the call Create Aeronautics' disassembly makes): 1 deck on the table, 0 on the
ground.

**A desk carried onto a ship takes signing up with it.** It used to clear the registration point, as a
broken desk does. A desk now notes, when a copy is loaded from its save made this tick, where the copy
went; if the old desk goes that same tick it was carried, and signing up follows. A copy whose original
stays moves nothing. Gate test `aCarriedDeskTakesSigningUpWithIt` (copy then carry), proved failing;
pack test `aDeskCarriedOffTakesSigningUpWithIt` with a real Sable assembly. And a game in progress: pack test
`aGameInProgressGoesOnAboardTheStructure` starts one, carries the table, and finds it aboard with its
whole log (3 lines before, 3 after) and none left where the table stood. Not checked: a move made aboard.

**Independent review of the custody fix** found it had introduced a loss: any copy sharing the
identity - a creative pick with data, `/clone`, a pasted structure - made breaking the original hand
back nothing. Fixed: a table counts as carried only to a copy loaded from its own latest save made in
the same tick (guard `aCopiedTableDoesNotStopTheOriginalHandingBack`, proved failing with the check
loosened; 521 gate tests and both Sable pack checks pass after). Also found, not fixed: a ship set
down **with a rotation** moves a table's origin role to another corner, Sable loads the data into a
block with no block entity, and the table's game ends and hands back once on the ship (nothing lost
or doubled, but the match is over, and a dropped deck lands in the ship's region); an ante question
(`Antes.ASKING`) and the event's table list kept the old position - **since fixed**: a carried table
moves both (`TableCustody.moved`), so a tournament table carried onto a ship keeps its number (gate test
`aCarriedTableStaysInItsTournament`, proved failing; pack test `aTournamentTableCarriedOffKeepsItsNumber`). A review of that
found a long table (two joined, one game kept on the first) could be listed twice after a carry, its
second table moved onto the first's new spot when a block of the second was cleared while the first still
stood. Each table now moves to where its own copy went (the first's, offset, only when it has none), and
never onto a position the event already lists. Guard `aCarriedLongTableKeepsBothNumbers` clears the
second table's corners first; proved failing with the move taken from the first table's copy. Open, ops
only: a command block cloning a table every tick and a player breaking the original that tick would
count as a carry (nothing handed back; the clone holds the keeping).

**Fabric tour (2026-09-15).** `:fabric:runClient -Pdevscene` after tonight's client changes: reached step
344 of 344, `[devscene] failures: 0`. Its world is now cleared before each run as NeoForge's is.

**Third independent review** (protocol check, comparator, clipboard) found, all fixed: building a
desk's update tag stored the fresh label, so the next refresh saw no change and players already watching
were never told (guard in `aLinkedDeskLabelsItsTournament`, proved failing); a comparator kept a stale 15
after its chunk reloaded with the round over (the desk tells neighbors once after loading - guard
`aComparatorLeftLitIsPutOutWhenTheDeskLoads`, proved failing); **both renderers' `getRenderBoundingBox`
had never been called on NeoForge** - `:common` compiles without NeoForge, so a `TableBlockEntity`
parameter produced no bridge for the erased `BlockEntity` signature, and the table's board was culled with
its corner block since that method was written (the pack scene now asks through NeoForge's interface:
failed with 1 by 1 boxes before the fix, 17 by 3 and 4 by 4 after); the clipboard wrote a cancelled
tournament's pages, and worked for spectators. A Fabric client from before the protocol check has no
channel to be asked on; registry sync refuses it for the desk it lacks (documented, not tested).

**Create Clipboard on the desk** (`DeskClipboard`, NeoForge with Create): writes the round's pairings,
ticked once confirmed, and the standings, in pages; a desk running nothing leaves the clipboard alone;
crouching is left to Create's placing. Pack test `aClipboardOnADeskTakesDownTheRound` (fake player,
real use), proved failing with the ticks removed; photographed in the pack scene as a Clipboard screen.

**Scripted tour after tonight's client changes (2026-09-15).** A first run straight from Gradle
failed 7 steps - it had inherited `run/saves/GatheringDevScene` from an earlier run (made 21:34 the day
before), whose tournaments and decks the steps did not expect; `runClient -Pdevscene` now clears it, as
`tools/shots.sh` does. The rerun on a fresh world: `[devscene] failures: 0`. Looked at: the board on
the block and a numbered table's floating label, both drawn as before the renderer guard.

**Desk comparator and "Time" label.** A comparator beside a linked desk reads 15 while the round being
played has had time called, 0 otherwise; the label says "Time: extra turns". Guard
`aDeskSignalsWhenTimeIsCalled` runs the round's real clock through and reads a real comparator; proved
failing with the signal removed. In the pack scene's real client, running the round's clock out lit a redstone lamp fed by a
comparator at the desk (checked on the server, and photographed with the label reading "Time: extra
turns").

**Second independent review** (Create, Ponder, labels; a reviewer agent given the requirements and the
diff) found, all fixed: the desk's label was culled with the desk's own cube on NeoForge (a render box
now covers it); an old desk kept saying "Sign up here" after sign-up moved (it says "Signing up" unless
it is the registration point - guard in `aLinkedDeskLabelsItsTournament`, proved failing); a desk sent
a blank label on chunk load and never refreshed beyond simulation distance (the label is worked out
when the update tag is built); Ponder's plugin list is unguarded and mod constructors run in parallel
(the plugin is added in client setup's queued work); the label refresh built a whole board each second
(a cheap `EventBoard.labelAtDesk`); the Create tooltip line showed on Fabric, which has no Display Link
support (NeoForge only); the label's lines were built every frame (kept until the label changes).
It found no dedicated-server, hidden-information or Fabric registration problem.

**Independent review of the desk** (a reviewer agent, given the requirements and the diff) found, all
fixed: (1) a host's own desk could never take signing up back once it moved elsewhere - after *Register
here*, a second desk, or a Create contraption carrying the desk, whose removal clears the point - now
it does; (2) the desk was a full solid cube wearing a lectern's model, hiding neighbors' faces and
blocking light - it has the lectern's shapes now; (3) takeover was by sneaking, which vanilla gives to
the held item, so a host holding a deck could never do it and the tests, which went around the click
path, could not see that - takeover is now a second use within ten seconds and the tests click the
real way with an item in hand; (4) the sign-up count included dropped players the list under it left
out. It also noted that Display Links set to the three removed per-view sources (never released) would
need re-picking.
**Not verified:** the *Show* selector scrolled by hand (the scene sets the choice and opens Create's
screen over it, which reads it back and keeps it); Fabric has the block and recipe but no Create.

**Deployers open boosters.** An empty-handed Deployer tears a booster open (`DeployerPacks`) when it
presses a booster lying loose on the ground, facing any way, or a booster on a Depot or belt it faces
**sideways**. Not one facing *down* onto a Depot or belt: Create hands that press to its own belt
processing, which ignores an empty hand, so nothing reaches this mod. The first version claimed down
onto a Depot worked; a pack test with a real powered Deployer showed it never pressed, and the design
changed to the two routes Create does deliver - the right-click-block event (sideways) and the
entity-interact event (loose). The booster stays while its cards are drawn and is swapped for them in
one step: on a Depot the way Create's own deployer recipes turn one item into several, loose as card
items beside it. Taken away in between, nothing is consumed and nothing comes out. A Deployer *holding*
a booster opens it too, by the pack's own right-click, into its inventory. Pack tests: a powered
Deployer facing a Depot, and one pointing down at a loose booster, each start a draw; a press starts
one draw; a booster becomes all fifteen cards on a Depot or on the ground; one taken away yields none.
The last ones are given cards rather than waiting on Scryfall, because a version that waited passed
when a rate-limited draw failed, which tested nothing. Each guard fails when its code is removed.

**Found on the way: a removed opener lost their cards.** A player who opened a booster and died before
the cards arrived was handed them as the old, removed entity - nowhere. So was a Deployer's stand-in
broken with the Deployer. Cards for a removed opener are now owed and handed over when they are next
here - in the archive pack's path too, which had the same check. Guard
`PackOpenGameTest.aRemovedOpenerCountsAsGone`, proved failing. (A first version waited on a real pack
and timed out in the gate when Scryfall rate limited the run; it asks the question directly now.)

**Seen in a real client with the pack** (`runPackClient -Ppackscene`, Create + Aeronautics + Sable):
the client starts and plays with all three; the host's click on a Scorekeeper's Desk links it and opens
the tournament; a Display Link on the desk writes the standings to a powered 4x3 Create display board,
and set to pairings rewrites it; Create's own link screen shows the *Show* setting reading "Pairings"
and closing it keeps that choice; a link on a table writes its match to an oak sign (a sign is too
narrow for the whole line, which it cuts off); a table assembled into a Sable structure and
turned 25 degrees renders on its platform; sitting at it opens the seated board, and the board on the
block puts its camera half a block from the table's real position. On a display board the standings
leave out the win-loss-draw record, which a four-wide board cut off mid-bracket. One start-up crashed
inside Veil (bundled with Sable, no Gathering frame on the stack) and the rerun started cleanly.

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

Left, and why: a cut game restarted by hand rather than by the event starts at
random.

**Duel Commander and Premodern** are formats now. Duel Commander (duelcommander.org): Commander's
100-card singleton decks, one on one at twenty life, its own ban list (Scryfall's `duel`), and no
commander damage - so a preset now says whether its command zone counts commander damage. Premodern
is a sixty-card format on Scryfall's `premodern` legality. The table setup screen, with twelve
choices, also stopped running off a 240-pixel-high window: its height was a formula that missed a
row, and the last row of buttons covered the line saying what is chosen. On a short window it now
goes four formats across in a wider panel with the guided first game sharing the event row.

**Second independent review** (reviewer agent, read only, given the requirements and the diff of
the drawn-game, chair, player-count and choose-to-draw work). Five findings, all fixed:
- A cut match drawn in its deciding game ended "drawn", which a cut cannot be, and left the players
  no game to play. A cut match level on games now plays its last game again (`MatchRules.needsAWinner`,
  saved as `needs_a_winner`); the owner's "a draw counts as a game" holds everywhere else.
- A best of three that went won, drawn, drawn was announced as a drawn match and the pot went back,
  while the tournament scored it 1-0 for the player ahead. The player ahead when the games run out
  now takes the match, and drawn games are counted in the suggested result and at time.
- A player who stood up while the other moved into their chair was given the same chair: both got the
  same games. One player found now puts the other in the chair left over.
- "Choose to draw" could be handed back and forth forever. The first turn marker now records that the
  choice was made (board view version 5), and it is refused after that.
- The catalogue path offered it in a game of one.
Guards: `MatchStateTest.aCutMatchLevelOnGamesPlaysAnother` and `theGamesRunningOutGoToWhoeverWonMore`,
`DrawChosenTest` (handing it back), `EventsGameTest.aPlayerWhoStoodUpKeepsTheOtherChair` - each shown to
fail on the old code. Left: players who swap chairs between games still swap the running score, because
the score and the held decks are kept by chair.

**Choosing to draw** (MTR 2.2). The table starts the chosen player playing; passing the turn to draw
instead made the other player's first turn "turn 2", so the first-draw reminder went to nobody and
the log showed a pass. The felt's menu now offers "Choose to draw" to the player going first on
turn one: the first turn goes to the next player and stays turn one, logged as a choice. Refused
from anybody else or later. `DrawChosenTest`, proved failing without the fold. Protocol 12.

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

## Polish audit (2026-09-15), slice 1: PQ-01 to PQ-04

Response: `docs/reviews/polish-audit-2026-09-15/RESPONSE.md`. The tournament screen now follows
control size and text size (`EventScreenLayout`), and pages what a small window cannot hold instead
of shrinking it. Buttons in a row share one label size. Focus survives rebuilds by action name
(`FocusKeeper`). Host controls are grayed with reasons from `HostActions`, the rule the server also
refuses by (protocol 13). Call off asks first.

Verified: gate green (525/16). `AccessibilityProbe` reports `failures: 0`, and every guard was shown
failing with its fix removed. The in-world guard `ahostsStaleOrForgedControlsAreRefused` also fails
without the server refusal. Core tests: `HostActionsTest` 6, `EventScreenLayoutTest` 5.

Owner requests the same day:
- Every screen at every GUI scale: DevScene now re-lays each photographed screen at GUI scales 1
  up to the largest and checks the geometry.
- Scripted clients muted and in the background: `tools/quietly.sh`.

The first quiet tours failed their animation checks (card in flight, library shaking). This looked
like the hidden window, but the game ran at 54 fps behind other windows. The real cause: a probe run
that had lost keyboard focus pressed "Reduced motion" on and could not press it off again, and the
dev run kept it. The probe now restores reduced motion along with the sizes. `quietly.sh` also turns
off Minecraft's spoken first-launch narrator prompt for good: that uses the system voice, which the
game's volume doesn't touch.

The first quiet tour, run after a probe that had crashed partway through, inherited 200/200 sizes
at GUI scale 4. That exposed two real problems:
- The host tab's fixed three columns cut "Prize: place N" short and had no room in a small window.
  It is now a grid with as many columns as its longest label allows, paged beside Done. The probe
  checks every host control is reachable at 427x240 at 200/200.
- The probe did not restore sizes when a run ended early. It now restores them however it ends, and
  writes them before stopping.

The turn sounds are the owner's recordings (2026-09-15), converted from MP3 to mono Ogg Vorbis and
signed into `docs/art-hashes.txt`:
- `your_turn` replaces the vanilla bell when the turn comes to your seat.
- `pass_turn` plays on the table log's turn-passed line, and is skipped when `your_turn` plays.
DevScene listens to the sound engine and asserts both.

Fixed: `ClientSettings` and `RecentThings` write a change 20 ticks after it is made, and nothing
wrote them when the game closed, so a change made less than a second before quitting was lost. The
evidence was a probe run that restored its sizes as its last act and left the file at 200/200.
`ClientTicks.stopping()` now runs from NeoForge's `GameShuttingDownEvent` and Fabric's
`CLIENT_STOPPING`. The probe no longer writes the file itself, and afterwards the file held the
restored sizes. Fabric's hook was not run in a client.

Tour, 2026-09-15, run quietly: `failures: 0`, reached step 344 of 344, all three sound checks heard.
The GUI-scale sweep laid out 27 screens at scales 1 to 4 with no failures. Not swept: SettingsScreen
and DecklistImportScreen, which the tour never photographs, and TableScreen, whose camera makes a
sweep move later steps (its resizes are checked by their own steps). Gate green, 525/16.

Not verified: a person using it by keyboard, longer translations, the event screen on Fabric in a
real client.

## Owner request (2026-09-15): what to borrow from Charta

The owner asked for everything worth borrowing from Charta (lucaargolo/charta, MPL-2.0; ideas only,
no code), polished rather than thrown on. A stair stands in for the chair model unless a better
one is worth making. In order, each committed on its own after the gate and a tour run:

1. **Decks look like decks in the world.** A pile on the table in the world is as tall as the cards
   in it, with its edges showing and the top card on top.
   - Must preserve: hidden information (a library is sleeves, the count is already public), the
     screen board, and aiming at a pile.
   - Must still work: cards flying in and out of the stack, and a shuffle's rattle.
2. **Player strip.** Each seat's column in the board's top row shows the player's face beside their
   seat mark and name, and marks whose turn it is. Faces come from the game's own skins, so no new
   art. A free chair or an absent player shows no face.
3. **Where something changed.** A pile or mat briefly glows in the acting seat's color when cards
   arrive in it, on the screen and in the world. Reduced motion keeps a steady mark and drops the
   fade.
4. **Table settings in view.** The board says the format and match length, with a marker when a
   table plays for keeps or differs from standard, so somebody sitting down sees what they are
   joining.
5. **How to play.** A "?" on the board opens a page per topic, written as Markdown in the language
   folder, with the player's own key bindings filled in. Resource packs and translators can replace
   it.
6. **Chairs.** A chair placed at a table's seat edge lets a player sit.
   - Sitting takes that seat, faces the table, and puts them at the board.
   - Standing up (sneak) gives up the seat as the board's Stand up does.
   - Breaking the chair, a restart, or the table changing shape ends the sit without losing cards.
   - Model: vanilla stair pieces.

Progress (2026-09-15), what each now does:

1. **Stacks.** `PileThickness` sizes a pile at half a millimeter of sleeved card per card against the
   card's width, capped at 100 cards. `TableMiniatureRenderer` draws banded sides in the owner's
   sleeve color, puts the top card and count on top, lights the top when aimed at, and flies cards
   above the tallest pile. On the block, `TableScreen.onTopOfAPile` aims at a pile's top
   (`TableTop.raisedBy`).
   - Tour: a 25-card library stands 71 units, and all four edges of its top pick it. Proven failing
     without the pointer change.
   - Photographed from beside the table (`61b`).
2. **Player strip.** Faces come from the skin the client has, or the default skin for the player's
   id, faded while away. A thin glow marks whose turn it is; the owner found the first ring hid the
   face. `SeatStrip` puts seats in two rows when one row would leave a seat no room for a face, name
   and life, with the turn on the first row and the table's terms on the second. Resting on a seat
   gives name, life, hand, library, graveyard, exile and counters.
   - Tour: two faces, and eight seats in two rows at 427 wide and one row at GUI scale 1.
3. **Arrival glow.** `Arrival` fades for 1.4 s, holding steady under reduced motion.
   `ClientCardFlights` records arrivals, marked from landing. It is drawn with `GuiGlow.around` on
   the screen and a band on the block.
   - Tour: the rival's milled graveyard is lit. Proven failing without it.
4. **Table terms.** `TableTerms` and `TableTermsPayload` (protocol 14) are sent with every board.
   The turn column says the format, match and game, with for keeps and free play in amber, and the
   tooltip says everything. A walk-up game is free play.
   - `TableTermsGameTest` (3), proven failing.
   - Tour: free play and for keeps.
5. **How to play.** `GuideScreen` has seven Markdown pages under `assets/gathering/guide/en_us`
   (`GuidePage` parser), with keys filled from bindings (`[2]` for draw). It opens from a "?" at
   the end of the top row. `GuideLayout` switches between a column of topics, rows of topics, and
   one topic at a time.
   - Found by reading the click path: the top row counted as felt, so the "?" could not be clicked.
     Clicks now reach it first, and the tour clicks it through the board.
6. **Chairs.** `ChairBlock`, the unsaved `ChairSeat` entity and `Chairs`. The model is vanilla oak
   planks and stripped oak, with no new art. Sitting goes through the same `TableBlock.sitAt` as
   clicking an edge, and getting up through `TableBlock.standUp`. Leaving from the board gets you out
   of the chair. Disconnecting keeps the seat.
   - Recipe: stick, three planks, two sticks.
   - `ChairGameTest` (6): five proven failing; the away-from-a-table case has nothing to remove.
   - Crafting test.

Not reproduced: one tour run showed four cursor-to-card failures that neither the run before it nor
the run after it showed. They were a drag out of the graveyard, a hover on the block, and key 9 at a
card. In that run's photo the hovered hand card was raised over the graveyard. The logs are
identical up to that step, and the next run with the same code passed all 348 steps. Cause unknown.
The scripted run's cursor is reflection into the mouse handler, since GLFW ignores
`glfwSetCursorPos` for an unfocused window; a pointer moved over the game's window is the leading
guess. Watch for it again.

Found in the pictures and fixed:
- The chair's back rose above the block with texture coordinates that ran off its texture, and came
  out green and pink; the UVs are now explicit.
- The board's top row showed through the guide's panel: the board draws some text raised in depth.
  `ChildScreen` now clears depth after drawing the screen underneath.
- On two rows the turn kept a third of the width while the terms had their own row. It now keeps a
  fifth, for the seats' names.
- The guide is drawn at a raised depth, because clearing depth in `ChildScreen` alone did not stop
  the board's top row showing through.

Verified for this batch: gate green (535/16), and the tour clean at 348 of 348 twice. Every new
check was shown failing without its code; the away-from-a-table chair test is the exception. Photos
looked at: eight at a table in one and two rows, a seat's tooltip, chairs at the tables, the guide,
the terms and the arrival glow.

Still open: the tour's side-on photo of a deck (`61b`) catches the seated board rather than the
world, because something reopens the board between aiming and shooting. An earlier run's photo did
show the stack standing with its sides.

## Owner playtest (2026-09-15), fourteen findings

The owner played the build at `0c66c961` and reported:

1. Tables become 3x3, for more board and a centered chair. Graveyard, exile and library slots are the
   size of the cards. Tables still merge end to end. Old 2x2 tables in existing worlds need not work.
2. Seats come only from chairs; clicking the table no longer seats anybody. The first player may sit
   at any side; after that only the opposite side works. Merged tables seat along their long sides.
3. The tutorial no longer plays.
4. No walk-up game from right-clicking a table with a deck: free play is in the format menu.
5. A confusing icon appears after a mulligan (the cards-owed-to-the-bottom mark).
6. Stacking cards is unreliable: dropping onto a stack sometimes starts a new stack from its cards.
7. Every stack on the table grows in height, not only the library; the library looked see-through
   under its top card.
8. The player does not sit properly in the chair.
9. The top row's tooltip is cut off by the top of the screen.
10. Searching the library needs typing, and smaller cards so more show at once.
11. Stacked cards flicker on the table in the world.
12. The mat buttons flicker on the table in the world.
13. A pack's cards reach the inventory on right-click, before the pack is opened.
14. The pack reveal highlights only the last rare and uses heavy borders: wanted a pulsing glow behind
    every rare and mythic, and a different (purple) glow for showcase treatments. The Done button sits
    behind the cards and is too dark to see.

Order: the bugs first (3, 13, 14, 9, 5, 11, 12, 8), then stacking (6, 7), then search (10), then the
table rework (4, 2, 1).

### First batch: 3, 13, 14, 9, 5, 11, 12, 8, 10 and half of 7

- **3, the tutorial.** It was not broken. The scripted tour finished the lesson in the shared dev
  config, so the owner's run saw a finished lesson. `DevScene` now captures the lesson flags at its
  first step and restores them at the end; the dev config was reset.
- **13, pack cards early.** A pack opened with the ceremony is now held under a wrapper token
  (`PackWrappers`), written to `Owed` as `wrapped` lines, and given only when the client says the
  pack was torn (`PackTornPayload`, protocol 15) or after ninety seconds. `PackWrapperGameTest` (4)
  was shown failing without the hold.
- **14, the reveal.** A pulsing glow behind every rare (gold), mythic (orange) and special treatment
  (purple: showcase, extended art, borderless, full art, textured, serialized, and similar), read from
  Scryfall into `CardMetadata.specialTreatment` and `CardSummary.special`. The ring on the last card
  is gone. Done sits centered under the cards with a glow behind it. Photo `41` looked at.
- **9, the tooltip.** Seat and terms tooltips are placed under the strip. Photo `107a` looked at.
- **5, the mulligan mark.** The pip in the Mulligan button's corner is gone. What the rules still ask
  (cards owed to the bottom; going first, no draw) is a worded band over the hand, like the "hand face
  up" band. The tour mulligans twice at the table of eight (the first is free there) and asserts the
  band. The first tour run expected a band after one free mulligan, which was the check's mistake.
- **11 and 12, flicker on the block.** Two causes. The mat buttons' recess was on exactly the plane
  of the mat's felt, and their edge on the landing wash's plane. And every step between flat layers
  was a fixed ten-thousandth of a block, under what the depth buffer resolves a few blocks away at a
  shallow angle. `FlatLayers` now picks the step from the camera's distance (a hair up close, up to
  0.004 blocks far off), every flat thing names its layer, cards start above everything on the mats,
  counts draw their own backing a step under the digits (the font put its backing a hundred-thousandth
  of a block behind), and notes and counters on a stacked card ride on that card rather than at a
  fixed height under the cards on top of it. `FlatLayersTest` was shown failing with the old step.
  **Flicker cannot be seen in a still photo; this is unverified until somebody looks.**
- **7, the see-through library.** The pile's side walls were wound clockwise seen from outside, so
  the world culled the walls facing the camera and drew the inside of the far ones. The winding is now
  `PileThickness.sideCorners`, tested (shown failing with the old order). Every stack growing is still
  to do.
- **8, the chair.** A rider's feet are 0.6 below what they ride and the hip joint about 0.7 above the
  feet; the seat entity sat at the seat's height less 0.35, which put the hips a quarter block into the
  chair. It now sits where the thighs lie on the seat. `ChairGameTest.aSitterSitsOnTheSeat` was shown
  failing (thighs at 0.261 against a seat at 0.625).
- **10, search.** A pile being read with eight or more cards, and a searched library always, has a
  search box focused on opening. Every word typed must appear in a name, type line, rules text or
  note (`PileSearch`, tested). Cards on a reading screen are 60 high rather than 84, and the box may
  take 86% of the window. The tour opens a library search at the table of eight, types a search
  that matches nothing, checks the grid empties, and clears it.

Verified for this batch: gate green (540/16) twice, the second time with search. One tour run reached
the end with one failure, the free-mulligan expectation, since corrected; that run predates the search
steps and the two-mulligan step, **which have not yet run**. Photos looked at: the pack reveal (`41`),
a seat's tooltip (`107a`), the mulligan step (`107c`, before the fix).

### Second batch: 6 and 7 (stacking), 4, 2 and 1 (the table rework)

- **6, stacking.** Two causes. A card let go on a stack landed wherever the cursor was, and a stack
  was worked out pair by pair: a card on the top card but a hair further from the bottom than the
  stacking distance counted one card under it, which is the owner's "starts a new stack with cards
  in the current stack". A stack is now whole (`TableStacking.piles` joins a card to the stack of
  the topmost earlier card it lies on, and knows each card's bottom card), and a card, a selection or
  a whole pile let go over a card snaps onto that stack's spot, on both boards. The seated board
  rings the stack a card would join and draws the footprint there. Lifting a whole stack uses the
  same stacks. `TableStackingTest.aStackIsWhole` and the reference walk were shown failing with a
  pair-by-pair rule; the tour carries a card over a card well off its middle, checks the stack it
  would join, lets go, and checks it landed on the stack's spot.
- **7, every stack grows.** On the block a stack on the felt stands as tall as its cards: each card
  a slab one card thick (or the depth step, if larger), squared on the one below at its own angle,
  with its edges in the sleeve color, alternately shaded. No lean on the block; the seated board
  still leans. The pointer on the block aims at the top of a tall stack. **Not looked at in a photo
  yet.**
- **4, no walk-up game.** Right-clicking a table with a deck never starts a game: with no game on
  it the deck stays in hand, the player is told to choose a game, and the choice of game opens.
- **2, chairs only.** Right-clicking a table never seats anybody; crouching on one does not either.
  A chair at the middle of an edge is the only way to sit. At a table on its own the first sitter
  may use any edge, which turns the table to be played across that pair (`TableBlockEntity.turned`,
  saved and sent to clients); after that only the opposite edge seats. A chair against a table but
  off the middle, or at an edge that seats nobody, refuses and says which. Sitting at an idle table
  opens the choice of game (which offers the lesson to a first-timer); a game starting opens the
  board for everybody seated and offers a loaner to anybody without a deck (`TableSetup.begun`);
  the loaner is no longer offered on sitting at an idle table, where it covered the choice of game.
  Between games of a match, the plain click from a chair starts the next one. Guards, each shown
  failing against the old behavior: `ChairGameTest.aTableOnItsOwnTurnsToItsFirstSitter`,
  `aChairOffTheMiddleOfAnEdgeIsNotASeat`, `aDeckOnATableStartsNoGame`,
  `MatchGameTest.crouchingOnATableSeatsNobodyAndStartsNothing` (which replaces the test that
  crouching seats you).
- **1, three-by-three tables.** `TableCell.BLOCKS_PER_TABLE` is 3 and `TablePart` has nine parts
  (corners with legs; edges and middle felt over apron; the crying obsidian table's column is now in
  its middle block). New models `*_table_top` and `crying_obsidian_table_middle`; blockstates for
  all nine parts. Lines of tables run either way: a line north to south seats along its east and
  west sides (`TableCluster.turned`, `seatsAsLaidOut`), and the board is laid out as the same line
  east to west and turned a quarter in the world - by the block renderer, `TableTop.inTheWorld`
  for the pointer, and the camera (`yawTurned`). A mat holds fifteen cards across, a tenth bigger in
  the world than eleven across a two-block table, with the zone gaps and edge margin tightened so
  the four zones down a two-player mat are the size of the cards (`zonesAreTheSizeOfTheCards`); the
  life box is a little shorter so two facing ones still fit between the mats. Seats are at the
  middle of an edge. The village card shops are rebuilt fourteen by nine with two tables and their
  chairs (`tools/village.py`); the test templates are larger (`empty` 5x4x5, `tables` 17x4x14) and
  test tables are spaced three apart; the Create ponder scene shows one table with its chairs,
  because two do not fit on Create's five-block plate. Guide page, tooltips, messages and the
  design brief say the new rules.
- The tour sits in a chair, is shown the choice of game, puts a deck down with no game (and checks
  none started), chooses free play, starts, and puts the deck down. From there it holds the seat
  the way an event seats a player, without a chair, because the rest of it teleports the player
  and getting out of a chair gives the seat up.

Verified for this batch: gate green (543/16) with every check above shown failing first; core tests
green. A tour ran clean at 356 of 356 on the stacking and search half before the table rework; **the
tour has not yet run on the table rework**, so the new opening (chair, choice of game, deck) is
exercised by in-world tests only.

Known and open from this batch:
- `AmbientBoardGameTest.achangedboardisstillsent` failed once in a gate run and passed in the two
  in-world runs straight after. It counts boards through a static counter every test shares; not
  yet reproduced or explained.
- `tools/plotcheck.py` reads tests from `neoforge/src/main/java/.../test`, which no longer exists,
  so it checks nothing ("0 test placements checked"). Pointed at the real directory it reports 25
  placements outside a template 3 blocks across, from files mixing the `empty` and `tables`
  templates. Left as it was; the templates are now large enough for a 3x3 table either way.
- `TableClusters.sideFrom` is now used only by `SableTablesGameTest`; nothing in play asks which
  edge a player is standing at.
- Not verified in a photo or by hand: a turned table drawn, pointed at and framed by the camera;
  stacks standing on the block; the village shops; the ponder scene. Old two-block tables in existing
  worlds are not migrated, by the owner's decision.

### Third batch: the owner's notes during the rework (2026-09-15)

- **Tour on the rework.** A full run reached 358 of 358 with no failures, sitting in a chair, choosing
  free play and putting a deck down for real; its photo of the turned table showed only felt. A run
  from step 356 (`-PdevsceneFrom`, new, see TESTING.md) drew the turned board correctly, and the step
  now also asserts the renderer drew a turned board. Two later full runs broke on the known cursor
  flake - hover and menu steps that had passed - one early and one at step 54a, both while the machine
  was in use; the last section was run on its own since (363 of 363, no failures).
- **The set progress bars** were stretched whole rather than sliced: the room rule wanted a nine-slice
  to have a middle as tall as a border, the bar is painted eleven tall with borders of four, and it is
  drawn at eleven. Art drawn no smaller than it was painted is now always sliced
  (`SpriteFrames.maySlice`; the test was shown failing with the old rule). **Not seen in a photo since;
  the section of the tour that shows it needs the whole tour.**
- **White wool** on every table's top, corners locked to the world's texture direction (`uvlock`), dye
  still tinting it. What the board on a table writes straight onto the felt - zone names, button words,
  the lines round groups - is dark on a light felt and light on a dark one (`FeltContrast`, tested).
  `table_felt.png` stays as the advancement background, recorded in texturecheck.
- **Mat buttons** in the seated view carry their words at every zoom, fitted to the button and
  centered; the legibility rule still governs the zone names. Photographed at the table of eight.
- **A refused break.** Breaking a table somebody sits at was refused on the server after the client had
  already broken the block, and a broken corner took the client's copy of the table with it: the table
  came back blank, a Commander board without its command zone. The table is now sent again two ticks
  after a refusal, past the client's acknowledgement of the swing (sent at once, it arrived before
  there was a table to apply it to). The tour swings at a seated Commander table and checks the
  command zone survives; it failed before the fix, and with the resend sent immediately.
- **Stacks on the block** are a real sleeved card thick per card, the same as the piles in the zones,
  rather than the depth step when that was more - which grew with distance, so a stack grew as the
  camera went further off. The face of a card lying square under another is not drawn. A card is now
  0.7 mm to 66 mm (sleeved) rather than 0.5, so a sixty-card deck is about two thirds of a card tall.
  Photographed side on, stacks of four and twelve.
- `TableScreen` board labels, the pile box rebuilding before it draws new cards (a frame drawn between
  cut the footer short), and the tour's mat-button helper aiming at the board on screen.

### Fourth batch: the owner's notes after the third (2026-09-15)

- **The reminder over the hand** (mulligan owed, going first) and the "hand face up" band were a strip
  the width of the hand drawn eleven pixels tall with art painted sixteen, so the art was squashed
  whole and read as three bars. Each is now one framed label sized to its line, centered over the
  hand, never shorter than its art. No reminders during the lesson, which gives its own directions.
  Photographed at the table of eight.
- **The pack reveal.** No glow behind Done. A revealed card is drawn in front of its glow in depth as
  well as order, because a card turned toward the cursor leans its edges back past the flat glow.
  **Not seen with real card art**: the tour's test printings have none, so the photo shows the glow
  round the art-less fallback only.
- `-PdevsceneTo=M` stops a scripted run after step `M` (TESTING.md).
- The last full tour broke at step 307: the lesson moved past "read a card" before the script asked,
  because the card the script had just played was under a cursor that, with the window focused, really
  hovered it. A harness race, not a lesson change; recorded here with the cursor flake above.

### Fifth batch: the owner's playtest after the fourth, and the update audit (2026-09-15)

The owner reported ten things; this batch is 1, 5, 6, 7, 8, 9 and the audit (10). The join and
spectate prompt with a deck picker (2), illegal decks allowed with a warning to the table (3), the
Scorekeeper's Desk as the only way to host (4), chairs at edges that seat nobody seating spectators,
and an away-from-board hold on an empty seat are the next batch.

- **1, a deck made from two cards loaded forever, and a card taken out while it loaded came out
  blank.** In creative the inventory is the creative menu, which sends the client's copy of every
  stack it moves back to the server; the client's copy of a deck is the public one, every card hidden,
  so the server's deck was overwritten with hidden cards. The server now keeps each deck's real list
  by its handle (`DeckVault`) and a deck turning up redacted is restored from it on its next inventory
  tick; cards a creative player puts into a deck, or two cards made into one, are declared by the
  client (`CreativeDeckEditPayload`, protocol 16, accepted only from a creative player and only face-up
  cards, at most 64). `DeckVaultGameTest` (2); tour steps 363-368 make a deck from two loose cards in
  survival and in the creative menu, open it, check every row is named, take a card out and check it is
  not blank. The steps were shown failing without the restore ("1 unnamed", "1 blank"). A deck already
  spoiled in an existing world before this fix cannot be recovered.
- **5, a pixel at the start of the set progress bar.** A fill narrower than its art's borders was
  sliced into nothing but border. The fill is drawn at least 48 wide and clipped to its true width.
- **6, wanted cards were yellow like rares.** Green now.
- **7, "# more below".** Gone from both set screens; a scrollbar (`ListScrollbar`,
  `ListScroll.thumb`, `ListScreenLayout.scrollbar`, tested) takes its place.
- **8, a card clicked out of a collection with a deck in hand goes into the deck.** Intentional and
  documented on `CollectionView.take` and in the footer hint; asked of the owner rather than changed.
- **9, mat corners still flickered.** Each frame was four overlapping quads, so every corner was drawn
  twice on the same plane. Frames are now strips that meet without overlap (`FrameStrips`, tested,
  shown failing with the old overlap) on mats, groups, slots and the aimed-at top. **Flicker is not
  visible in a photo; unverified until somebody looks.**
- **10, the update audit** (`Gathering-update-audit-2c00769c.zip`), its tests added unchanged as
  `CurrentAuditGameTest`:
  - UA-01, a broken chair handing over a hidden hand. A seat whose last occupant still has cards in any
    zone of a running game is theirs: the claim is refused ("Somebody's cards are on that seat") and the
    session's seat mapping skips it (`TableSessions.boardBelongsToAnother`). A chair somebody sits in
    at a game cannot be broken by anybody else. `ChairGameTest.aBoardWaitsForItsOwnerWhenTheirChairGoes`
    removes the chair without a player, so the refused break cannot pass it; shown failing without the
    ownership check.
  - UA-02, a refused mount keeping the seat. `Chairs.sit` mounts first, claims second, and backs out of
    the chair if the table says no.
  - UA-03, standing up into a wall. `ChairSeat` looks behind, to either side and the back diagonals, at
    three heights, for a place a player fits.
  - UA-04, plotcheck checked nothing. It reads the real test directory, fails on finding none, and
    takes each method's own template. It found eight placements spilling out of `empty`; those tests
    use `tables` now. 130 placements checked.

Verified for this batch: gate green (549/16); tour steps 363-368 passed in survival and creative. Not
run since: the full tour.

### Sixth batch: tournaments are hosted at the Scorekeeper's Desk (2026-09-15)

The owner's item 4 from the fifth batch's playtest: hosting belongs to the desk, not the table.

- A free desk (running nothing, or a tournament that is over) opens the tournament list with **Host
  one**; the list opened by `/gathering events` grays it out and says to use a desk. The create screen
  names the desk (`CreateEventPayload.desk`, protocol 17), and `Events.hostAtDesk` plays the tournament
  at the free tables within 16 blocks across and 4 up or down, nearest long table first (the first is
  where a draft or sealed event opens its packs), and makes the desk its desk and registration point.
  A desk running an unfinished tournament hosts nothing more. No free table nearby still creates it;
  the host adds tables standing at them. A host with a tournament elsewhere still takes a free desk in
  one click, as before.
- The table's setup screen no longer offers Tournaments. Its draft or sealed pod stays: that is a
  game at the table, not a tournament. `Events.create` from a table is gone (nothing called it but the
  removed route).
- Guide page, desk tooltip, ponder text and `docs/tournaments.md` say the new route.
- `ScorekeepersDeskGameTest.aDeskHostsATournamentAtTheTablesNearIt` goes through the payload's handler;
  shown failing with the filter that leaves out another tournament's table removed. The tour's
  tournament section now uses a desk beside the practice table (steps 296-369 ran with no failures;
  photo `100-tournaments` shows Host one active).

Verified: gate green (550/16).

### Seventh batch: joining a game, choosing a deck, decks not legal, chairs that watch (2026-09-15)

The owner's items 2 and 3 from the fifth batch's playtest, and their note that chairs at edges nobody
plays at should watch.

- **Joining or watching.** Sitting in the chair at a free seat of a game already on no longer takes the
  seat: the player is asked **Join** or **Watch** (`JoinTableScreen`, `JoinTablePromptPayload` and
  `JoinTableAnswerPayload`). The chair holds the seat while they answer, since nobody can sit in an
  occupied chair. Watching opens the board as a spectator; closing the panel watches. Right-clicking the
  table from that chair asks again, or, with no game on, gives them the seat. A player whose board it is
  (they sat there last) is seated straight back, as before. Refusals (somebody's seat, somebody's cards)
  are said before sitting, not after.
- **Chairs that watch.** A chair against a table but off the middle of an edge, or at an edge nobody
  plays at, now seats a watcher (`ChairSeat.watchingAt`) instead of refusing, with a line over the
  hotbar saying so. A game starting opens the board for watchers too.
- **Choosing a deck.** Right-clicking the table with a deck at a game on no longer puts it down. The
  decks in the inventory are listed (`DeckPickerScreen`, `OpenDeckPickerPayload`), with **Borrow one**
  where the server lends decks. It opens after joining, for everybody seated when a game starts (in place
  of the loaner offer), and on right-clicking the table from a seat with no deck down. A row sends only
  its slot (`ChooseDeckPayload`); the server reads the deck out of the slot and checks it is still the
  same stack if the check has to wait for card data (`DeckCameFrom.THEIR_INVENTORY`).
- **Decks not legal.** At a table somebody chose a format for, a deck chosen from the list that fails the
  check is asked about (`DeckNotLegalScreen`, `DeckNotLegalPayload`): the problems, and "Use this deck
  anyway?". Use anyway plays it only if the slot still holds the exact stack asked about; the table
  (seated and watchers) is told who is playing a deck not legal in which format and why, in gold. The
  table keeps that note with the held deck (`TableBlockEntity.NotLegal`, saved with it) and tells
  everybody who sits down or watches while it is down; it goes when the deck is handed back. A tournament
  that locked a player's deck still refuses any other.
- Guide page, table and deck tooltips and the ponder text say the new flow. Protocol 18.
- Tests: `JoiningGameTest` (5), each shown failing with its feature removed (no question, no watching
  seat, the click still committing, anyway ignored, a swapped deck played on the old answer, a watcher
  unable to take a free seat). `ChairGameTest.aChairOffTheMiddleOfAnEdgeWatches` replaces the test that
  such a chair refuses, and the turning test stands the watcher up before moving chairs: the owner changed
  those rules. `ReviewRoundTwoGameTest`'s reflective call fills the two new arguments (no slot, not
  anyway); the case it checks is unchanged.
- `AmbientBoardGameTest.achangedboardisstillsent`, the known flake: it had nobody in the room to send a
  board to, since the room's board skips seated players, and passed only when another test's stand-in
  happened to stand within range. The new tests changed the layout and it failed every run; it now has a
  watcher of its own.
- Tour: step 8 chooses the deck from the list after Start (photo `03a-your-decks`); steps 369-371 put the
  join question and the not-legal question up the way the server does and photograph them
  (`108-join-or-watch`, `109-not-legal-use-anyway`, `110-choosing-another-deck`), all laid out at GUI
  scales 1 to 4. Runs of steps 0-16 and 369-371: no failures. Photos looked at.

Verified: gate green (555/16). **Not verified:** two real players - the single-player tour cannot sit a
second player down, so the question arriving from a real chair, and the gold line reaching a second
client, are covered by in-world tests only.

### Eighth batch: away from the board (2026-09-15)

The owner's rule: getting up without conceding keeps the seat for eight minutes, nobody else may sit
there, the player may give it up early, and the others may vote it free - unanimously, and only in a
game of four or more.

- **Kept.** Getting out of a chair (standing up, knocked out, the chair gone) in the middle of a game
  the player has not conceded, with cards on their board or a deck down - or between games of a match
  with their deck held - keeps the seat instead of giving it up (`AwayFromBoard.keepsTheSeat`, from
  `Chairs.gotUp`). The player and the table are told. Anybody sitting in that chair is refused with who
  is away and how long is left. Sitting back down carries on and the table is told.
- **Freed.** When the eight minutes run out (checked once a second on the server tick); when the player
  chooses Leave table or concedes; or when every other player seated at the table, online and not away
  themselves, votes to free it from the table's menu - a game of four or more only
  (`AwayVotePayload`). The deck the table holds goes back to its owner wherever they are
  (`TableBlock.giveUpSeat`, which works for a player who has left the server).
- **A seat given up may be taken with its board.** The UA-01 rule stays for a seat left behind (a chair
  knocked away, a player gone from the server): its board waits for its owner. A seat given up - time,
  vote, Leave table or conceding while away - is marked, and the next player to sit there takes it
  board and all, hand included. This is a judgment call; the alternative is a board nobody can ever
  play again. Asked of the owner below.
- **Shown.** Beside every board a player is sent: which seats are kept, seconds left, votes and whether
  this player may vote (`TableAwayPayload`). The top row reads "(away 7:42)" and its tooltip the time
  and the votes; the table's menu has "Vote to free X's seat (1 of 3)". Photo `05a` looked at.
- Not saved: after a restart a player who was away keeps the seat as a player who left the server does.
  Leaving the server while away does not stop the clock.
- The tour's harness holds its seat without a chair; it now clears the kept seat so the eight minutes do
  not run out partway through the tour. Protocol 19. Guide, design brief updated.
- Tests: `AwayFromBoardGameTest` (4) - kept and refused to another, back in the chair; freed at eight
  minutes and not before, and taken by the next player with the board; freed by the third of three
  votes at a game of four and not before; one vote at a game of two frees nothing. Shown failing with
  getting up giving the seat up, with the given-up mark ignored, with the four-player limit removed, and
  with one vote enough.

Verified: gate green (559/16); tour steps 0-30, no failures. **Not verified with two real clients.**

### Ninth batch: the owner's answers on taken-over seats and collection clicks (2026-09-15)

- **A taken-over seat's deck.** The owner: whoever takes over the seat may see and play the deck that
  was in it, and after the game it goes back to its owner, never to them. A seat freed while its player
  was away (time, vote, conceding while away) now leaves the deck in the table's keeping
  (`TableBlock.giveUpSeat(..., keepTheDeck)`), so the next player plays it. When that game ends with
  another to come, every held deck whose owner is not the player holding its seat goes back to the owner
  (`TableSessions.returnDecksNotBeingPlayedByTheirOwners`); when the match ends every deck goes back as
  before - to the owner wherever they are, kept for them if they are off the server, never to the chair.
  Leave table still hands the leaving player's deck back at once. Guard:
  `AwayFromBoardGameTest.aTakenOverSeatsDeckGoesBackToItsOwnerAfterTheGame`, shown failing with the deck
  handed back when the seat was freed and with the end-of-game return removed.
  `DeckCustodyGameTest.sideboardingKeepsWhoseDeckItIs` held a deck for an absent owner during a game and
  then edited it between games; under the new rule that deck goes home when the game ends, so the fixture
  holds it once the game is over. What it checks - an edit keeps the owner and the pool - is unchanged.
- **Collection clicks.** The owner chose the inventory: a card clicked out of a collection comes out loose
  whatever is in hand, and no longer goes into a deck in hand. The footer always says "Click to take one".
  `CollectionBlockGameTest.cardsComeOutLooseWithADeckInHand` replaces the test that it went into the deck.

Verified: gate green (560/16).

### Tenth batch: away from the board when leaving the server, and through a restart (2026-09-15)

- **Leaving the server mid-game** starts the same eight-minute hold, with the same vote, instead of
  keeping the seat and its cards for ever (`Chairs.gotUp` no longer returns early for a disconnected
  player when `AwayFromBoard.keepsTheSeat`). The table is told the player left the server. With no game
  on, leaving the server keeps the seat as before. Guard:
  `AwayFromBoardGameTest.leavingTheServerMidGameKeepsTheSeatForEightMinutes`, shown failing with the old
  early return.
- **Saved.** Kept seats (time left, name, votes) and seats given up are written to
  `<save>/tables/away_from_board.dat` on every change and every 20 seconds while anybody is away, and read
  back the first time they are asked for after a start. Time left rather than a moment, since the server's
  tick count starts again every launch; a restart loses at most 20 seconds of anybody's eight minutes.
  Guard: `AwayFromBoardGameTest.aKeptSeatSurvivesARestart` (time left counted from the restart, freed on
  time, the given-up mark kept), shown failing with the file not read.
- Verified: gate green (562/16).

### Eleventh batch: the top cut by player count, and starter boosters for finishing the lesson (2026-09-15)

- **Top cut.** The owner: the mod should pick the cut itself. A new tournament's top cut is **Auto** by
  default (`EventSettings.AUTO_CUT`), which follows the Magic Tournament Rules' Appendix E (checked against
  the judges' copy of the table): none below 9 players, the top 4 for 9-16, the top 8 from 17. Appendix E's
  top 8 for 9-16 applies only when the playoff is a booster draft, which the mod does not run. The host can
  still choose None, Top 4 or Top 8, and nothing is cut below 9 whatever is chosen. The event screen says
  "top cut by player count". Auto rounds for 9-16 players are now 5 (Appendix E), not 4. **One deliberate
  difference:** Appendix E runs 5-8 players as single elimination; the mod keeps 3 Swiss rounds with no cut
  so nobody is out after one match. Core test `theTopCutIsDecidedByThePlayerCountUnlessTheHostChose`, shown
  failing with no automatic cut; the three cut tests that played "4 rounds" for 9 players now play however
  many rounds the count gives.
- **Starter boosters need the finished lesson.** The lesson runs on a board the client builds, so the server
  cannot watch it. The client now says when the lesson began and when it was finished, with the steps done
  (`LessonPayload`). `LessonRecords` writes a player down as finished (`<save>/starter/lesson_finished.txt`)
  only if it began, finished at least 10 seconds later, and every step was done; `StarterBoosters.give`
  refuses otherwise ("The starter boosters are for finishing the lesson on this world"). A client rewritten
  to lie about playing the lesson cannot be told apart from here, and still gets the packs once; a direct
  request without a lesson, or a replayed finish, is refused. The two lists share `SavedPlayerList`.
  Tests: `StarterBoostersGameTest.nopacksbeforethelessonisfinished` and `onlyALessonReallyPlayedIsWrittenDown`,
  shown failing with the check and the time limit removed; the existing starter tests have the player finish
  the lesson first. `TutorialDemoGameTest.thewholelessonsendsnothing` now allows exactly the lesson's own
  began/finished notice and still fails on anything about the game; the tutorial tests bind a sender, as a
  client always has. The tour takes the player off the list before the lesson and checks the server has them
  down as finished after it (tour 296-372, no failures; the starter packs were given).
- Protocol 20. Verified: gate green (564/16).

### Twelfth batch: compatibility with other mods and older loaders (2026-09-15)

The owner: as compatible with as many mods as possible, as few things that could conflict as possible.
An audit of what the mod touches that is not its own, with each finding checked by running it:

- **Fabric stole three of the game's keys.** Fabric keeps vanilla's key lookup, one mapping per key, rebuilt
  from every mapping; the table's verbs default to Q, / and 1 among others, and took Drop, the command line
  and hotbar slot 1 from the game with the mod installed. Found with a new scripted Fabric client,
  `:fabric:runKeyScene` (testmod `KeyScene`), which presses each of the game's own mappings: "vanilla keys
  taken: 3". The table's verbs are now removed from `KeyMapping.ALL` after registering (access widener on
  that field, no mixin) and still match inside the table's screen and sit in the Controls screen: "taken: 0",
  and Q and 2 still read as untap and draw. On NeoForge, whose lookup holds several mappings per key, they are
  marked `KeyConflictContext.GUI`. The tour's opening (keys pressed on the board) ran clean after.
- **The NeoForge version range was untrue.** `[21.1.0,21.2)` was never tried. On 21.1.80 the mod did not load
  at all ("IModBusEvent events are not allowed on the common NeoForge bus"): three subscribers relied on newer
  NeoForge working out the bus. They name the mod bus now; every in-world test passes on 21.1.80 (564), and the
  tour's opening ran in a 21.1.80 client. The bundled Sable companion needs 21.1.80, so the range is now
  `[21.1.80,21.2)`, what was run.
- **Fabric asked for the newest loader and API** (0.19.3, 0.116.15), cutting out packs a release behind. The
  Fabric in-world tests (16) and a client boot pass on Fabric Loader 0.15.11 with Fabric API 0.102.0; those
  are the floors in `fabric.mod.json` now (`fabric_loader_min_version`, `fabric_api_min_version`), separate
  from the versions it is built against.
- **Mixins are optional.** Each injection is `require = 0`, so another mod changing the same vanilla method
  costs one behavior rather than a crash at launch. The creative deck hook is backed by `DeckVault`.
- Checked and left: tags are `replace: false`; loot is added as a pool, never a rewrite; the village shop is
  added to the house pool; recipes are namespaced shapes over tags; the only cancelled events are on the mod's
  own blocks and its own tooltip.
- `docs/pack-authors.md` has a "Playing well with other mods" section, including why Cataclysm needs no
  verification of its own. TESTING.md lists the key and loader-floor runs.

Verified: gate green (564/16); tour steps 0-40 on NeoForge 21.1.248 (the table's keys pressed, Q for untap among them) and 0-16 on 21.1.80, no failures.

**Not verified:** a real pack with many mods at once; Fabric's Controls screen still colors shared keys red
(nothing is taken); NeoForge 21.1.0-21.1.79 (below the Sable companion's floor, so no longer admitted).

### Thirteenth batch: Sable's companion no longer ships inside Gathering (2026-09-15)

The owner did not want another author's library redistributed in Gathering. Sable carries its companion
library (1.6.0) in its own jar, so Gathering no longer bundles a copy:

- The companion is `compileOnly`; the `META-INF/services` entry that always loaded `SableWorldSpace` is gone,
  and `GatheringNeoForge` installs it (`WorldSpace.use`, through a static method on the class, so nothing naming
  the library is loaded before asking) only when `sablecompanion` is loaded. Without it tables use plain world
  positions, as the companion's own default did.
- **The NeoForge floor drops to 21.1.1**: the 21.1.80 floor was the bundled companion's. Every in-world test
  (564) passes on NeoForge 21.1.1, and the tour's opening ran in a 21.1.1 client.
- **With Sable:** `runPackGameTestServer` - the Sable table tests pass; with the install switched off six of them
  fail ("the world-space service in use is ... not Sable's"), so they are what checks it. The remaining 232
  failures are the known harness problem (Sable sending its own payload to stand-in players), identical with
  and without the switch. `runPackClient -Ppackscene` ran clean (0 failures): a table on a turned Sable
  structure, seated at it, the camera within 1.0 block of the table in the world.
- The pack scene had three stale failures of its own from earlier batches, fixed here: its sign's Display Link
  was placed inside the now three-block table (breaking it, so there was no table renderer and no source); the
  two Ponder lines the owner's flow changed in the lang file had not been changed in the scenes themselves; and
  its line-count floor still expected the two-table scene.
- Docs: pack-authors and TESTING.md say what is bundled (nothing) and the 21.1.1 floor.
- Verified: gate green (564/16); the built NeoForge jar has no `META-INF/jarjar` and asks for NeoForge [21.1.1,21.2).

### Fourteenth batch: the owner's collection block art (2026-09-15)

The owner sent a model and a texture for the collection block (`Assets/Collection Block`).

- `textures/block/collection.png` is the owner's 64x64 sheet, and `collection_top.png` is gone: the new model
  draws the whole block from one sheet, and a texture nothing names fails texturecheck. Both are signed into
  `docs/art-hashes.txt` (`artcheck --write`).
- `models/block/collection.json` is the owner's four cubes - the body and three drawer fronts - with the
  Blockbench-only fields dropped, its texture named in this mod's namespace, `minecraft:block/block` as the
  parent for the standard item display, and the particle taken from the same sheet (the file named a vanilla
  barrel texture by a path that does not resolve).
- **The block now has a front**, so it needed a facing: `CollectionBlock.FACING`, set to face whoever puts it
  down, with the four turned variants in the blockstate. A collection in a world from before keeps its default
  and faces north until it is broken and put down again.
- `artcheck` skipped `neoforge/run` but not `neoforge/runs`, where the runs against other mods' jars write their
  screenshots, so signing the art in first put sixteen of those on the list - which the next clone would have
  reported as missing art. Both spellings are skipped now, on both loaders.
- The tour stands a collection block on the grass in front of the player and photographs it
  (`41b-a-collection-block`); the picture was looked at: three drawers, gold handles, front to the camera.

Verified: gate green (564/16).

### Fifteenth batch: the collection block's faces, and what a table really is (2026-09-15)

The owner's three notes on the new collection block, and the same question asked of the tables:

- **The drawer sides.** The exported model pointed each drawer's sides, top and bottom at the strip of stray
  pixels along the bottom edge of the sheet (the teal, pink and green slivers). Each drawer now takes those
  faces from a slice of its own front, and the drawers' backs - inside the cabinet, never drawn - are gone.
  Photographed from the corner (`41b-a-collection-block`) and looked at.
- **Blocks beside it went invisible.** It was registered as a full cube, so the game hid the faces of whatever
  touched it, and its back sits a pixel in and its drawers stand two pixels out. `noOcclusion()` now, as the
  tables already had.
- **Hitboxes follow the models.** The collection block's outline is the cabinet and its three drawer fronts,
  turned with it. A table's is the felt and its apron, plus the leg on a corner or the crying obsidian table's
  plinth in the middle - it was a square down to the floor.
- **And a table's collision is the same**, so a player can get under one, which the owner asked for: it used to
  be solid between the legs. Standing on the felt is unchanged. Guard:
  `TableGameTest.aTableIsSolidOnlyWhereItIsDrawn` (nothing under the middle, the felt still holds, a corner
  still has its leg), shown failing with the old square block.

Verified: gate green (565/16).

### Sixteenth batch: the owner's furniture, v2 then v3 (2026-09-15/16)

The owner sent `Gathering-furniture-and-deckbox.zip` (v2) and then `Gathering-furniture-v3.zip`, which replaces
it. v2 was applied and pushed (`bf50175d`); v3 is what stands now.

- **v2** was three models on a 64x64 atlas of its own. One thing it dropped was a deck's color: the flat item
  was tinted by `DeckItem.tintOf` and the new case had no tinted faces, so every box came out the same teal.
  The case's leather was marked `tintindex: 0` here, and `86-a-shelf-of-decks` showed eight colors again.
- **v3** is simpler and vanilla-textured: no atlas at all, every model drawn from `dark_oak_planks`,
  `white_wool`, `gold_block` and `sandstone_top`, five to eight cuboids each. Its own `source-changes.patch`
  from `3e4b8854` applied once v2's files were rewound to that commit (its `upgrade-from-v2.patch` did not
  apply: its idea of v2 included table models this repository never had).
- It brings: the counter, desk, cabinet and deck box as simple models; four table structures (wooden legs,
  cobblestone L-supports, stepped blackstone feet, the crying obsidian pedestal) with outlines and collision
  that follow them, keeping the owner's "get under a table"; and **dyeable fabric** on the counter, desk and
  cabinet, through a shared `FurnitureDye.FELT` blockstate and a dye in hand, with the deck box's white body
  still taking the deck's own color.
- **The owner's own cabinet art goes** (asked and answered, 2026-09-16): `BlockFullTexture.png` and the model
  built from it are replaced by v3's vanilla-textured cabinet, which can be dyed. Both are in git and in the
  owner's Assets folder.
- The drop's prebuilt jars were not used; this is its patch built here.

Verified: gate green (567/16), which includes v3's own `FurnitureDyeGameTest` (dye consumed in survival, not
in creative, the same color twice costs nothing, a collection's owner only, and the collection and desk keep
what they hold). **Not verified:** any of it in first or third person, Fabric's client drawing it, dyeing
looked at by eye.

### Seventeenth batch: every wooden thing in every wood (2026-09-16)

The owner: the wooden blocks should come in every wood, the table included. Five wooden things - table,
chair, shop counter, collection and Scorekeeper's Desk - in eleven woods, which is fifty new blocks.

- **Each wood is its own block**, the way a vanilla door or sign is: `spruce_table`, `cherry_chair`,
  `warped_scorekeepers_desk`. The plain ids keep the wood they were drawn in, so a world built before this
  keeps its furniture: `table`, `shop_counter`, `collection` and `scorekeepers_desk` are dark oak, `chair` is
  oak. Bamboo, crimson and warped are in: they are planks in the hand, which is what "every wood" means.
- **The Java side is a list, not fifty declarations**: `GatheringContent.Woodwork` and `WoodVariant`, walked
  by both loaders to register blocks and items, to fill the block entities' valid blocks (a table, collection
  or desk whose type was not told about it quietly has no block entity at all), and to fill the creative tab.
- **The assets are generated**: `tools/woodwork.py` writes each wood's blockstate, block models, item model,
  loot table, recipe and name from the plain block's own, swapping the planks and the stripped log. 242 files.
  Editing a plain model and running it again carries the change to every wood; `--check` says what would
  change and changes nothing.
- **Recipes name their wood.** They took any planks before - and the chair took a mixture - which cannot tell
  fifty blocks apart, so every recipe now asks for one wood's planks, as vanilla's do. The collection's recipe
  gains four planks at its corners, which is where its wood is chosen; the shop counter has no recipe in any
  wood, as before.
- Tests: `WoodVariantsGameTest` (all fifty registered, each the kind of block it is named for, each placed
  keeping the block entity its plain twin has) and `CraftingGameTest.theWoodYouLayOutIsTheWoodYouGet` (spruce
  table, cherry chair, warped desk, bamboo collection); the four plain crafting tests now lay out the plain
  block's own wood.
- The tour stands a table of every wood in a row and photographs it (`41c-tables-in-every-wood`); looked at.

Verified: gate green (572/16).

## Decisions needed from the owner

1. ~~Should a drawn game use up one of a match's games?~~ **Decided by the owner (2026-09-14): yes,
   the last game included.** A drawn decider, or a drawn best of one, used to be played again
   because the game number stayed on the last game; it now ends the match, drawn.
2. ~~Appendix E for 9-16 players~~ **Decided by the owner (2026-09-15):** the mod picks the cut by player
   count. Done in the eleventh batch.
3. ~~Are starter boosters a welcome grant or a completion reward?~~ **Decided by the owner (2026-09-15):**
   a reward for finishing the lesson, checked on the server. Done in the eleventh batch.
4. ~~A seat freed while its player was away: take it with the board, or clear it?~~ **Decided by the owner
   (2026-09-15):** the next player takes it and plays that deck; the deck goes back to its owner after
   the game.
5. ~~Left-clicking a card out of a collection with a deck in hand~~ **Decided by the owner (2026-09-15):**
   it goes to the inventory.

## Next concrete action

0. ~~Fabric has no protocol handshake.~~ **Done 2026-09-15:** `ProtocolCheck` asks a joining client
   for its number while the connection is configured and turns a different one away with a message
   naming both versions. `GatheringProtocol.VERSION` is the one number both loaders use. Verified with
   two real clients (`:fabric:runProtocolHost`, `:fabric:runProtocolJoin -PpretendProtocol=11`, a LAN
   world so no EULA is accepted for anybody): the joiner on 11 was turned away with "This server runs
   Gathering's protocol 12 and your game has 11...", and one on 12 joined and played. Fabric in-world
   test `aClientOnAnotherProtocolIsTurnedAway` (registration and rule), shown failing with the check
   unregistered; the same class now also checks the desk's block, item and block entity on Fabric.

1. **A person plays the lesson, a real game, and a tournament run from a Scorekeeper's Desk** - and, with
   a second player, the new table flows: Join or Watch from a chair, the list of decks, a deck not legal
   played anyway, and getting up mid-game (the kept seat, sitting back down, the vote at four players).
   Those are covered by in-world tests and single-player photos only. The
   scripted tour passes on this machine (`[devscene] failures: 0`, 2026-09-15, after the renderer,
   label, Settle, footer and sprite changes) and its screenshots were looked at; how it feels to
   somebody who has not read the code is still unknown. The pick clock tooltips are now asserted by
   the tour (proved failing with them removed). Not yet seen by anyone: Create's Show
   selector scrolled by hand, and a table on a moving Aeronautics ship.
2. The rest of CL-10 (mode context, pointer controller, shared action binding), each extraction
   paired with a tour run. CL-08 is done (release jars inspected).
3. CL-14: Create 6.0.10, Aeronautics 1.3.2 and Sable 2.0.5 are qualified by the pack tests and the
   pack scene; Cataclysm's version is still unknown, and the crowded-board timing matrix is not run.
4. The owner decisions above (Appendix E for 9-16 players; starter boosters).
