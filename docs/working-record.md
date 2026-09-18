# Working record

The one place that says what is actually true right now. `CLAUDE.md` says how work is done;
this says where it has got to.

**Read this before continuing after a context compaction, and read the source it points at.
Everything here is a lead to verify, not proof.** Test counts are pasted from the run that
produced them and from nowhere else.

Last updated 2026-09-14, after the security review, memory audit and a rules and tournament pass (see "Rules and tournament pass"). The gate is green at 503 NeoForge and 14 Fabric in-world tests. The quality backlog is 20 of 28 done and the cleanup roadmap 12 of 14 rows done; everything still open on either needs a person, a graphical client run, or another mod's files - see "What is left, and why each one needs you".

## Owner's five tournament reports (2026-09-17)

Five things the owner asked for after playing the tournament flow. All five are written; **none
has been seen in a running game** - no `runClient`, no `tools/shots.sh`, no game-test run from this
session, because another process held `neoforge/run`.

1. **Create-screen settings did not survive.** Every choice now lives in one pure
   `core/.../tournament/EventDraft`, which the create screen hands to a subscreen and takes back.
   The real defect was `withKind`: choosing the kind that was already chosen put the pack settings
   back to their defaults. Proved by reverting it - `EventDraftTest:41` and `:53` fail. The draft is
   also kept against the desk (`client/EventDrafts`) so closing and reopening the screen at the same
   block finds it as it was; Create clears it.
2. **Add tables needed a chair.** It needed the impossible: the button sent `BlockPos.ZERO` and the
   server refused every press for being out of reach of it. `Events.addTables` now works from the
   host's own position on the server - the nearest long table within three blocks - and the payload's
   position is read by nothing.
3. **One tournament per host.** Gone from `EventRecords.whyNotHost`. A tournament is one per
   Scorekeeper's Desk, which `hostAtDesk` has always enforced. A free desk beside a tournament that
   already has a desk now offers hosting rather than adopting it; one with nowhere to sign up - after
   its desk is broken - is still adopted in one click.
4. **Prizes before creation.** `PrizeOffer` (place plus hotbar slot, pure) travels on
   `CreateEventPayload`; `EventPrizes.putUpAtCreation` takes the items from the host's own hotbar
   once the event exists, one prize per slot, saving before anything leaves the hand.
5. **Escape left to the world.** `EventCreateScreen` is a `ChildScreen`; `EventListScreen`,
   `EventScreen` and `PodLobbyScreen` remember the screen they were opened over, as `DraftScreen`
   already did; `PodCreateScreen` opened from `TableSetupScreen` goes back to it.

Verified: `:core:test` 1937 tests, 0 failures, 5 skipped; `:neoforge:compileJava`,
`:fabric:compileJava`, `:neoforge:compileGametestJava`; langcheck, doccheck, spellcheck, keycheck,
prefcheck, statecheck, savecheck, runcheck all exit 0. Not verified: the new game tests in
`neoforge/src/gametest/.../server/events/EventHostingGameTest.java` compile but have never run, and
no screen has been looked at.
## Three client reports from the owner (2026-09-17)

Uncommitted in a worktree. **The full gate has not been run on this**: another process held
`neoforge/run`, so neither loader's in-world tests, datagen nor the scripted client were run.
What did run: `:core:test` (232 test classes, every one of them ran, 1952 tests in all, by
`tools/coretestcheck.py`), `:neoforge:compileJava`, `:fabric:compileJava`,
`:neoforge:compileGametestJava`, and all seventeen static checks, each exiting zero.

**1. Labels over a table or a desk lost pieces of themselves up close.** Two causes, both
fixed in `common/src/main/java/dev/gathering/client/FloatingLabel.java`. The writing was
drawn depth-tested, so every block the camera-facing sheet passed through took a bite out of
it - worst exactly where somebody stands close enough to read it; both passes are
`Font.DisplayMode.SEE_THROUGH` now. And at a fixed world scale a line is four windows wide
from half a block away, so `dev.gathering.core.ui.LabelStandoff` never lets the sheet come
nearer than 2.5 blocks. The desk's culling box was a block and a half across and a long
tournament name reaches further than that; it is sized from `LabelStandoff.reach()` now.
Whether the labels *read* well up close is a judgment call and has not been looked at in game.

**2. A notice given while a screen was open was drawn under the screen.** The action bar and
the chat window are both drawn before the screen that covers them.
`common/src/main/java/dev/gathering/client/ScreenNotice.java` decides at the moment of
speaking: on the screen while there is one, over the hotbar when there is not. Placement and
fade are `dev.gathering.core.ui.NoticeLine` - across the top, never over the bottom row of
buttons. The server says it through `dev.gathering.server.Notices` and the new
`NoticePayload` (protocol 27). Every action-bar notice in the mod is routed through it; chat
is untouched, because chat is the log rather than the answer to a click.

**3. The view moved when it should not have.** Two halves, both in
`common/src/main/java/dev/gathering/client/ViewKeeper.java`. A screen now gives back the view
it opened over, watched from the client tick so every way out of a screen is covered rather
than the ones with an `onClose`. And the read key holds the view completely still instead of
fencing it inside 22 degrees: what the mouse asks for is added up for the card's tilt and
none of it reaches the world. The hold is answered from each loader's existing camera hook,
which is the one moment in a frame after the mouse has been applied and before the world is
drawn with it.

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

## Owner's economy pass (2026-09-17): common packs, the Mana Coin, village shops, creative order

Four reports from the owner, all four addressed. **Not gated and not played.** `:core:test`,
both loaders' `compileJava`, `:neoforge:compileGametestJava` and `:neoforge:runData` were run;
the in-world tests were **compiled and not run** - another process held `neoforge/run` - so
every game test below is unverified behavior with a compiled assertion behind it.

**1. Packs were much too rare.** The odds were one chest in eight, one treasure catch in twelve
and one brushed block in twenty: about a pack and a half in an hour of exploring, which is why
the shop, and therefore emeralds, was the only real way into a collection. Now about half of
ordinary loot chests, every chest at the end of something, one treasure catch in four, one
brushed block in four, and a new **mobs** source at one in a hundred and fifty - roughly seven
boosters an hour. The mob source is gated on `LAST_DAMAGE_PLAYER` (vanilla's own
`killed_by_player` parameter) and pays the ordinary booster only, never a coin, so a spawner
farm makes commons and buys nothing. `LootYieldTest` does that arithmetic against the constants
so the sentence cannot quietly stop being true.

**2. The rare packs got rarer, and there are more of them.** `BoosterOdds` classified the rare
kind by `equals("collector")`, so a box topper or a promo pack was priced as a draft booster.
It is now a word match over collector / topper / promo / vip / premium / gift / special, at
weight 1 against 200 in an ordinary chest and 25 against 200 in one worth an expedition - under
one pack in two hundred, and about one in ten. A jqwik property holds that band over every
combination of kinds a real set has been sold in.

**3. The Mana Coin replaced emeralds as the shop's price.** `gathering:mana_coin`, found in
chests and nowhere else: one ordinary chest in five holds one to three, one expedition chest in
two holds two to five, and a card shop's own stock chest holds three to six. About seven or
eight coins in an hour of exploring against a booster costing one, so a display box is five
hours or so and an end city raid is about twenty coins. **Its texture is the owner's and is not
drawn**: `common/src/main/resources/assets/gathering/textures/item/mana_coin.png` does not
exist, `tools/texturecheck.py` fails on exactly that one file, and nothing points at another
texture to hide it.

  - `sealed_price_item` and `sealed_price_block` both default to the coin with
    `sealed_price_block_worth = 1`, because there is no block of coins. **`ShopPrice` refused
    every price over one slot when there was no larger denomination**, which would have emptied
    every shopkeeper above novice on a default server; it now pays a dear thing as two piles of
    the same coin. `ShopPriceTest#withoutABlockBothSlotsAreTheSameThing` was run against the
    unfixed method and failed (`Expecting Optional to contain ShopPrice[blocks=64, loose=1] but
    was empty`).
  - **Known limit, stated in the config file:** two slots caps a coin economy at 128 coins, so a
    case of six display boxes is not on the counter at all. A master shopkeeper whose only stock
    is cases therefore offers nothing; a server that wants to sell them names a real block item.

**4. Village shops, and the creative menu.** `village_shop_weight` 8 to 20. The plains house
pool weighs 87, so eight was one draw in eleven; twenty is one in five and about nine villages
in ten. The creative menu is now one shared order (`GatheringContent.creativeItems()`) instead
of two hand-written lists, one per loader, and each family runs through `WOODS` in order with
the plain member in its own wood's place - `table` is the dark oak table, `chair` is the oak
chair - then its stone variants.

**What the owner must do before this can be gated:** draw
`common/src/main/resources/assets/gathering/textures/item/mana_coin.png` (a 16x16 item texture,
a swirl of all five mana colors), then `tools/artcheck.py --write` to sign it in.

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
| Minors, batch one | A whole-library search now closes when a card it never looked at arrives in the library (`aSearchClosesWhenACardArrives`, proved). A client's event may not carry the redaction's stand-in id `CardIdentity.STAND_IN` (`EventCodec.readFromAClient`; stored games still read as written). A public log line refuses anything but words, seats, card references, zones and numbers - every existing call site checked. `/gathering events` names an event by its whole name or a unique beginning, and `official` refuses an id no event had (`anEventIsNamedExactlyOrNotAtAll`, proved). A join prompt replaced by another screen answers watch. The event list keeps its page to one the list has (`ListScroll.pageWithin`). A settings file lookup that throws gives the defaults. Dead `resetAccessibility`/`resetFeedback` removed. `RecentThings` bounds lines by lines. Tests restore the client sender they replace (`ClientNetworking.boundSender`/`restoreSender`), which exposed a tutorial test that passed only on another test's leftover sender. Sideboard moves on left-click only. Naming a commander keeps the builder's page. The key list is measured once a frame. A set-progress click opens the row under the press. A box selection ends only on its own button. The command-zone entry reads the board when pressed. Box lookups are budgeted. Four interface strings tightened. A settings key missing from a section that exists joins that section (`amissingsettingjoinsitsownsection`, proved). Checked and not a defect: the pack wrapper is timed whenever a server runs |
| Archive after the switch | Archive packs could drop after collecting was switched off: `Archive.rollFor` never asked the setting, and a walk begun before the switch could publish after it. The roll asks now, and every walk carries a number that a newer warm or a clear supersedes. `aFullArchiveDropsNothingWithCollectingOff` proved failing without the roll check |
| Shopkeeper mid-trade | `Shopkeepers.refresh` runs as a player right-clicks, before the game decides the villager is busy, so a turnover moved a trading player's offers under their open screen. A villager already trading is left alone. `aShopkeeperMidTradeKeepsTheirStock` (with `CardShop.stockForTesting`) proved failing without it |
| :core discovery | `tools/coretestcheck.py`, a gate stage after the build: every `:core` test source file must have run at least one test, matched to Gradle's result files by name (suite names are display names). 218 classes, 1,867 tests. Proved by hiding one class's results |
| Archive over all of history | By the owner's rule the Archive Pack holds every real paper printing in Magic's history that play cannot reach, not the remainder of the newest eight sets in play. `ArchiveAudit` (core, pure): a set drawn from reaches its booster sheets and, with the shop open, its sellable products; a set not drawn from reaches nothing; tokens, memorabilia, minigames, Alchemy, digital-only, oversized and non-card layouts are not audited. `Archive.warm` walks every set Scryfall lists, sets in play first, publishing the sheet every 25 sets; each set's facts are kept in `archive-audit/` beside the card cache (`ArchiveFacts`) and read again when Scryfall's count for the set moves, after 30 days, or when a set now drawn from was never read for its reach. Printings are read without filling the metadata cache. `ArchiveAuditTest` proved against ignoring sets in play; `ArchiveFactsGameTest` proved against trusting a grown set. **Not verified against the network:** the first full walk has not been run end to end; a set over 7,000 printings (40 search pages) is read short and left out until it reads whole; a product naming a deck from another set counts as unreachable, so the archive may hold a few cards a shop could sell |
| Replays, by the owner's rule | A casual game is its players' own and a tournament's match is hidden until the tournament is over, then anybody's. The replay header now records the tournament (format 4; format 3 reads as casual). `Events.eventOfGameEndingAt` places a game explicitly: the event whose tables are being cleared, or the unfinished event at that table - never "whichever event once used this table", which would publish casual games at old tournament tables. `modes.replays` now defaults to `participants` and governs casual games only; `off` still keeps nothing. Guards: `casualGamesAreTheirPlayersAndTournamentMatchesGoPublicAfterward` (proved against the old rule) and `casualReplaysAreTheirPlayersByDefault` (proved against the old default) |
| Command-made cards | By the owner's rule, a card a command made says so in its history: `HowItCame.SPAWNED`, who ran it and which command. `/gathering card` and `foil` stamp the card; `pack open` stamps every card out of it; `pack give`, and `rewards grant` when a player typed it, stamp the pack, and every card opened out of it - by hand, by wrapper, or by a Create deployer - carries the stamp first. A stamped pack handed back after a failed opening keeps its stamp. `SpawnedCardGameTest`, both halves proved. **Not carried:** cards owed on disk to a player who left mid-opening come back unstamped (`Owed` stores identities only) |
| Tutorial decline | `Tutorial.skip`, `TutorialProgress.skip` and its `skipped` flag, and `tutorial.gathering.offer.no` removed: nothing could reach them. Leaving the lesson before the end is still recorded as not finishing |
| Who is sent which board | Nothing tested that the server builds each player's view for that player's own seat. `TableBroadcast.builtForTesting` sees each view one step before the wire (a stand-in player cannot take a payload), and `everyPlayerIsSentTheirOwnSeatsBoard` checks two seated players and a bystander by what each can read; proved failing with every seated player addressed as seat 0. The test audit's worry that three broadcast-counting tests race other tables was checked and is not so: each resets, ticks its own table by hand and reads inside one synchronous body on the server thread, so no other table broadcasts in between |
| Settings theme | A theme id with a quote in it was written back bare and the file no longer parsed. The reader is forgiving line by line, so only the theme was lost - not every setting, as reported. Theme ids are refused unless they are a resource location, on load and on set; `aquoteinthethemedoesnotcostthewholefile` asserts the written file parses; proved failing. Seen while proving it, not fixed: a key with no line to replace is appended under a fresh copy of its section heading each time |
| Rendering review | `FoilSheen.paintFlat` passed height over width where `CardMesh` takes width over height, so the shine on a card in the hand cut its corners twice as deep and stretched its grain; `CardMesh.aspectOf` is now the one rule and both foil paths use it (`theAspectIsWidthOverHeight`; the client call site itself has no automated test). `GuiGlow`'s three glows each run as one batched flush rather than a draw call per fill. `CardSleeves.emblem` parses each sleeve once. `TiltedFace` thins its shine mesh to cells of at least 12 px, so a pack-spread card gets 9x12 rather than 26x36. **None of these has been looked at in the scripted client yet** |
| Trade lists | Carried cards and their piles stopped at what fit, with no scroll and nothing said - eight rows at GUI scale 4 against up to sixty-four. Each column scrolls and says how many more. **Not yet run in the scripted client; no automated guard** |
| Import command | `/gathering import` said "import started" straight after a refusal. `DecklistImport.importFor` returns the refusal; the command reports it and returns 0. `theImportCommandSaysWhenItWasRefused` runs the real command as a non-operator; proved failing |
| Rewards problems | `/gathering rewards` needs no permission and printed every reload problem, and an IOException's message is usually an absolute server path. Problems now go to operators only. **Not guarded by a test** |
| Checks that could pass on nothing | `runcheck` now reads commands and both loaders, matches the Async forms and `thenAcceptBoth`, fails on a missing folder or zero completions, and reads an excuse written above the line; proved against the old loaner-reload command, which answered through a bare `server.execute` and is now `ServerRun.onTheServerThread`. `LoanerDecks.read` swapped the shelf from a worker thread with no run check; it has one. `langcheck`, `doccheck`, `recipecheck` and `voicecheck` fail when there is nothing to check; `doccheck` and `voicecheck` proved |
| Fishing on NeoForge | Packs and archive packs never came out of fishing on NeoForge. The bobber rolls `gameplay/fishing`, which reaches `gameplay/fishing/treasure` as a nested entry, and NeoForge runs global loot modifiers only for the table a roll starts from (`LootTable.getRandomItems` calls `modifyLoot`; `NestedLootTable` calls `getRandomItemsRaw`, which does not) - checked in the 21.1.248 sources. `NestedLootPools` adds the same `PackLootEntry` pool Fabric adds, and `PackLootModifier` leaves that table alone so a direct roll is not doubled. `theTreasureFishingReachesHasAPackPool` proved failing without the listener. A real pack roll is not tested: it needs the set list, which a test server has none of |
| Import locked after a quit | `DecklistImport`'s in-flight marker came off only when the lookup completed, and a lookup still queued when the card worker shut down never did. `DecklistImport.clear()` in `ServerState`; `statecheck` proved to fail without the call |
| Deployers | Opened boosters with collecting switched off, and warned on every press at an archive pack. `DeployerPacks.aDeployerMayOpen`. `aPressOpensNothingWhileCollectingIsOff` proved failing on `runPackGameTestServer`, where all five deployer tests pass. That run reports 277 other failures, every one `Payload sable:dimension_physics may not be sent to the client` against mock players - not this change, and not in the gate. **Open: unexplained** |
| Scene budget | `scenecheck` did not count `waitHere`, skipped any expression it could not evaluate, and counted `pack.cloth().advance(1f / 60f)` as scene time. It now counts both helpers, ignores their own declarations, and fails on a wait it cannot size |
| Properties that never ran | jqwik finds inner classes by `@Group` and ignores JUnit's `@Nested`, so 14 properties in `HandFanTest`, `BoardGeometryTest` and `TableTopTest` had never run. One, `aCardDroppedWhereItIsDrawnStaysPut`, had been wrong since `rectOf` moved to measuring from the middle (and sized its down-axis tolerance from the mat's width); production was right. `TestHygieneTest.everyPropertyIsInAClassJqwikFinds` fails on a property in a non-group inner class; proved by removing one `@Group` |
| core/ui review | Clicks on the risen hand card fell through across most of it (`HandFan.onLifted` tests only the card that is up; the old every-card test was deleted). The camera's card-size floor overrode "show everything" at GUI scale 3-4 and for eight seats (`TableCamera.lowestScale` lets a whole surface reach 140 px). `DeckScreenLayout` sized the card for a text box it then dropped. `ArrangeSelection` read a card one unit lower as a row further down. Each with a guard proved failing against the old code. The review's Minors are not yet done |
| Tests that could not fail | From the round-two test audit. `artSentNamesOnlyWhatTheViewShowed` was f(x) subset of f(x); `aRivalsHandIsNeverNamedInSomebodyElsesArt` read both sides out of the rival's own view on a fixture where both seats held the same printings. Both now check against the board, with `GameFixtures.twoPlayersWithDifferentCards`, and both fail when the hand rule in `VisibilityRules` is switched off. `ViewCodecTest` derived the seat from the verb, so no generated game ever had a face-down card; now two factors, a morph path, and a jqwik coverage floor that failed before the change. `TestHygieneTest`'s regex missed `@Property(tries = N)`, which is 93 of 146 properties; replaced by an annotation-run scan with a self-test pinning six shapes, three of which the old regex missed. `AnteTest`'s draw property asserted a size sum; now a card-for-card prefix and an exact stake count. `informationBoundariesAreHardInEveryMode` now runs every mode and asserts the refusal, not only the advice |
| Guards that were missing | `noCardIsEverInTwoPlacesOrNone`: every card in exactly one zone, before and after rewinding half the game; proved against a zone removal that keeps duplicates. `SessionSeedTest`: the seed's `toString` names no four-byte run of it; proved against a hex `toString`. `aGameStillBeingPlayedIsNotKept`: the one fence before a historian view; proved by deleting the `ended()` check |
| The scripted run's own checks | `FAILURES` is synchronized (31 fails come from the server thread). Five `FAIL` lines that bypassed the counter now call `fail`. The watcher and seatless-player gesture checks compare the board before and after rather than asserting nothing. The set-symbol check fails on a symbol the client gave up on. The picker check fails when it checked nothing. Seven table placements go through `roomForATableAt`. A step waiting in place resets the stuck clock (`waitHere`), which step 8's four phases had nearly exhausted. `-PdevsceneTo`/`From` say so in the final line, the jump fails by name when it skipped the game's setup, and both flags work on Fabric. New steps 382-385 drive the real `DecklistImportScreen` with a typo and assert the named error. **Not yet run under `tools/shots.sh`** |
| shots.sh budget | 12 min default against 12.7 min of the scene's own scripted waiting, so the documented command could not finish. Now 30 min, the run prints its elapsed time, and `scenecheck` fails when the waiting passes three quarters of the smallest budget; proved at a 900 s budget |
| Undo across a hand | `revealsInformation` asked whether the destination was public, where the rule is whether somebody who could not see the card now can. Another seat's hand is hidden without being secret from the person holding it, so a card - or a whole hand in one `ZoneMoved` - could be pushed across, read, and then rewound by one player alone with nobody's consent. Both events now ask `GameEvent.intoTheOpen`. Three guards in `GameSessionTest`; the two positive ones proved failing without it |
| A save's readable half | The open half of a `StoredSession` carries every event that *grants* sight - a library searched, a hand shown - and was unauthenticated and unbound to the sealed half. Editing it in a world folder granted a permanent live library search the running server would refuse outright. `SessionCipher.seal`/`open` now take the open half as GCM associated data, so editing either half stops the other opening. Format 2; format 1 still reads, so games in progress in an existing world survive. Proved failing in the unbound format |
| Replays in the clear | Two comments in `Replays.keep` said replays sat in the server's own directory and not the world folder. They did not, and never had: `folder()` is `<save>/gathering/replays` and says so. On the strength of those comments the shuffle seed and every library in decklist order went down in plain, inside the very folder the session cipher exists to survive being copied. The secret half is now sealed with the server's session key. Format 3. `whatIsOnTheShelfIsSealed` byte-scans the file for the seed and for a library card, and opens a historian's frame off the end to prove it is a seal rather than a loss; proved failing without it |
| A peek left open | `PileScreen` sent `LibraryClosed` from `onClose()`, which the game calls only when the player closes a screen. Dying, disconnecting and every screen the server pushes over the top replace it instead, and a whole-library search is the one look the fold deliberately never closes on its own - so the server went on building that client a view with their whole library in it for the rest of the game. Moved to `removed()` |
| Spelling | `tools/spellcheck.py`, gate check 17. Comments, javadoc, declared names and every lang value, against a list of British forms; not string literals, since some are phases written into a save, and not Minecraft's or Create's own `*Behaviour` names. Fifty-eight found and fixed, including the public `TutorialDemo.practising()` |
| Deck picker slot | The picker read the inventory once when it opened and sent a raw slot number, which the server trusted. A deck swapped into that slot between the read and the press went down at the seat instead - a different deck than the row that was clicked, and nothing undoes putting a deck down. `ChooseDeckPayload` now carries the handle of the deck the row was drawn from and `TableJoining.choose` refuses when the slot no longer holds it. Protocol 26. `adeckThatMovedIsNotTheOneThatGoesDown` proved failing without the check ("a deck the player was not looking at went down at the seat: library 60") and asserts both halves: the swapped deck stays put, the read deck still goes down |
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

Verified: gate green (570/16).

### Eighteenth batch: cards deleted by a deck in creative, and a chair with no model (2026-09-16)

The owner's first find of the new playtest, and one the same playtest turned up in the log:

- **Cards put into a carried deck were deleted** (creative). The creative menu sends the client's copy of a
  stack back to the server, and a deck that crossed the wire has every card hidden; the server took the list
  it had kept for that deck and threw the rest away, so a card the client had just put in went nowhere.
  `DeckVault.real` now keeps the cards it has and adds whatever the arriving copy carries **face up** - which
  can only be a card the client just put in, since everything that crossed the wire is hidden. Ticking the
  same stack again adds nothing twice. With that, the client no longer has to tell the server what it did:
  `CreativeDeckEditPayload`, `CreativeDeckHook` and both loaders' bindings are gone (protocol 21), and one
  mechanism does the job two did. Guard: `DeckVaultGameTest.cardsPutIntoACarriedDeckSurviveTheCreativeMenu`.
- **`oak_chair` drew as the missing model.** The chair was already drawn in oak and the other four in dark
  oak; the generator assumed dark oak for all five, so the block registered as `oak_chair` while the files on
  disk were `dark_oak_chair`. Each kind now carries its own plain wood (`Woodwork.plainWood`), `WOODS` lists
  all eleven, and `tools/woodwork.py` reads the plain woods out of the Java rather than keeping its own copy.
  Guard: the tour asks the client whether every block it registers has a model of its own -
  "all 58 blocks are drawn by a model of their own" - which is how this was found at all.

Verified: gate green (571/16); tour steps 0-3, no failures.

### Nineteenth batch: the owner's furniture, v4 (2026-09-16)

`Gathering-furniture-v4.zip` adds three stone chairs - cobblestone, blackstone and crying obsidian - each a
`MaterialChairBlock` extending the wooden chair, so seats, table claims, spectating and dismount cleanup are
the ones already tested. Each has its own model and a collision shape generated from the same cuboids, a
white wool cushion recolored by dye through the `FurnitureDye.FELT` state the other furniture uses, a recipe
of five of its material plus one white wool, a pickaxe tag, loot and a creative-tab entry on both loaders.
No new textures: cobblestone, polished blackstone, crying obsidian and white wool are vanilla.

`upgrade-from-v3.patch` did not apply cleanly - the three places it adds a chair to (the language file and
both loaders' creative tabs) are the three places the wood variants already occupy. Applied with `git apply
-3` and the three conflicts resolved by keeping both: the stone chairs first, in the order the package lists
them, then the wooden ones in the order the woods are listed.

Verified: gate green (573/16), the two new tests being `MaterialChairGameTest`'s.

### Twentieth batch: a table goes where it was pointed (2026-09-16)

The owner's third find: "Tables should place from the center block (clicked block = center), and must not
place clipping into other objects."

- **The clicked block is the middle now**, not the north-west corner. A table aimed at a spot used to grow
  east and south out of it, so the player pointed at one block and got a table two blocks away, over
  whatever was there. `TablePart.MIDDLE.originFrom(clickedPos)` is the whole change; nothing else knows
  where the click was.
- **Eight of the nine blocks never went through an obstruction check.** Vanilla checks the block the player
  clicked; the other eight were written straight into the world, so a table went down through a player, an
  armor stand or a boat and left them inside it. `TableBlock.whyItWillNotGoHere` now asks each of the nine
  against the collision shape it will have, twice: once ignoring the player, so "something else is standing
  there" and "you are standing there" are different messages, because only one of them is fixed by
  stepping back.
- **Every refusal says why.** It returned a bare boolean and then asked `TableClusters` for a reason, which
  had one for two of the five refusals - a table blocked by a neighbor's settled seats simply would not
  place, with nothing said. One method, one reason, five messages.

Guards: `TableGameTest.aTableGoesDownAroundTheBlockYouClick`, `.aTableWillNotGoDownThroughSomethingStandingInIt`
and `.aTableWillNotGoDownThroughThePlayerPlacingIt`, all three through the player's own game mode and the
item's own placement. Proven: with the middle restored to the corner only the first fails; with the
obstruction check removed only the other two do.

Verified: gate green (576/16).

### Twenty-first batch: every block can be made, and none of them hides its neighbors (2026-09-16)

Two more of the owner's findings, both of them true of a whole class of block rather than the one noticed.

- **#8, a recipe for every block.** The shop counter was the one block a player could not make - it was
  the village's, taken rather than crafted - so it and its ten woods now cost five planks, two wool, a
  gold ingot and a sheet of paper, which is the counter's own model read as a recipe. And **no Gathering
  recipe reached the recipe book**: a recipe with no advancement granting it never appears there, so all
  sixty-one needed a player to already know the pattern. `tools/recipecheck.py` is a new gate stage: every
  block has a recipe, every recipe has an unlock, and `--write` writes the unlocks. It wrote fifty-eight.
- **#4, blocks hiding their neighbors.** The shop counter and the Scorekeeper's Desk never said they were
  not cubes. The counter's cabinet stands a pixel back from the front of the block and stops three pixels
  short of the top, so the block under one lost its top face and you could see through the floor along the
  front of it. Both have `noOcclusion()` now, and their two overrides that only mean anything to a block
  that occludes are gone.

Guards: `CraftingGameTest.aShopCounterCanBeCrafted`, `recipecheck` itself, and
`BlockOcclusionGameTest.nothingThatIsNotACubeHidesItsNeighbors`, which asks it of every block the mod
registers rather than of the one that was reported. Proven: without the two `noOcclusion()` calls it names
all twenty-two - the counter and the desk in each of the eleven woods.

`ScorekeepersDeskGameTest.aDeskIsShapedLikeWhatItLooksLike` asked for `useShapeForLightOcclusion`, which
only means anything to a block that occludes at all; it asks `canOcclude` now, which is the same claim
made the stronger way.

Verified: gate green (578/16).

### Twenty-second batch: the pack and the deck box, as things rather than pictures (2026-09-16)

Four of the owner's findings, all of them about what a pack or a deck box looks like.

- **#5, the wrapper stretched when a pack opened.** The screen cut rows one to fourteen out of a sixteen-row
  wrapper - losing the white top of the crimp and the fold at the bottom, drawing the first row of the body
  as crimp - and laid them along a pack two thirds as wide as it was tall. All sixteen rows now, each piece
  along the share of the pack it takes up in the picture, and the pack drawn at the printed bag's own shape.
  The numbers moved to `PackWrapper` in `:core`, where `PackWrapperTest.theWrapperIsDrawnWhole` can check
  they still add up to the whole picture without a window.
- **#10, a pack was flat.** Two quads a hundredth of a block apart, so held in the third person - where a
  player is seen from the side - it was a line. Six faces now, the four edges taking a pixel of the
  wrapper's own border so the sides are the color the front is, and the third-person pose is vanilla's
  held-flat-item angle rather than square on.
- **#6, the tear.** The strip you tear off used to stop existing column by column. It comes away now:
  the freed part of the crimp is drawn again above the tear, lifting clear of the pack and leaning back as
  more of it comes free, fading as it goes. The light out of the tear builds as the tear crosses instead of
  sitting at one strength. And it makes a noise - there was none at all. A crinkle every fourteenth of the
  way across, rising in pitch, and a rip when it comes apart, under the player's own table-sound setting.
  Vanilla sounds: every sound this mod ships is the owner's, and putting a new one in the jar is not this
  project's call.
- **#2, the deck box in the first person.** Drawn at 0.85 of full size and turned thirty degrees the wrong
  way, so a box nearly a block tall filled the corner of the screen. Vanilla's block angle and 0.45.

Verified: gate green (578/16), and the scripted client photographed the pack screen - `39-a-sealed-pack`
shows the whole wrapper at its own shape, `40-tearing-it-open` shows the strip peeled up and leaning back
off the torn half with the glow along the ragged edge, and `38-sealed-packs` shows the held pack with a
visible top edge. The run was started at step 120 and the steps after the pack failed for want of the
setup those earlier steps do; nothing there is a claim about them.

Not done: the third-person pack was not photographed - the tour has no step that stands back from the
player - so its new angle is reasoned from vanilla's and is for the owner to look at.


### Twenty-third batch: a lock on a collection, and a case to show one card in (2026-09-16)

- **#9, who may use a collection.** The rights have been there since collections were - a list of who may
  take and a list of who may add - and **nothing could change either of them**, so in practice every
  collection was its owner's forever and nobody else's ever. Now: `CollectionRights` carries a third
  right, looking, and whether the collection is open to everybody; `CollectionKeys` is the only thing that
  changes any of it, and only for the owner; and `CollectionKeysScreen` is where the owner does it - a
  lock, a name to let somebody in by, and a row per person with Look, Take and Add on it. Whoever may take
  or add may look, because taking from a box you cannot see into is not a thing anybody could do.
  Protocol 22.
  The lock is asked in `CollectionView.at`, which is the one place every payload naming a position goes
  through, so a closed collection answers nothing at all - not a page, not a count, not whether a card is
  in it. A collection saved before the lock existed loads open, which is what it was.
  Guards: `CollectionKeysGameTest`, seven of them, reading through `CollectionView.pageFor` - the same
  path every page a screen shows comes down. Proven: with looking always allowed, three fail; with the
  owner check gone, the stranger one fails.
  **A first draft of that stranger test passed for the wrong reason** and the proof run is what showed it:
  it used a *locked* collection, which refuses a stranger before ownership is ever considered, so it would
  have passed whether or not anybody checked who owned the block. It uses an open one now, where the only
  thing between a stranger and the keys is that the collection is not theirs.
- **#7, a display case.** One card, under glass, for a room to look at: `DisplayCaseBlock` and its block
  entity, drawn in the world through the same renderer a card in the hand goes through. A card goes in
  with a right-click and comes back out with an empty hand, both the owner's; anybody may look, because a
  case is glass. What a case holds is sent to every client that can see the block, so **what goes in is
  turned face up on the way**, and a case with somebody else's card in it cannot be broken by anybody
  else. Glass, planks and wool - no new textures. Guards: `DisplayCaseGameTest`, five of them through the
  block's own right-click; proven by reverting the face-up turn and the owner checks.

`tools/recipecheck.py` caught the display case shipping without a recipe unlock before the gate did, which
is what it is for.

Verified: gate green (592/16).

Then a review pass over the two of them, which found four things:

- A row on the sharing screen showed whether somebody **may** look rather than whether they are **on the
  looking list**, so while the collection was open every row's Look lit up - including for people who had
  only been allowed to take - and pressing it changed nothing visible.
- The three rights are anchored to the right of a row at a fixed width; in a narrow window the leftmost
  hung off the left edge of the panel and over the name it belongs to. They squeeze to fit now, down to a
  floor.
- The case's card was built into an item stack every frame, in a block entity renderer. It is built when
  the card changes.
- An unused parameter.

**And the scripted tour loses its table part way through, which is not this work.** Both runs today failed
from "the table went away before a pot could go on it" onward; a run of the same tour on `8a567411` - the
commit before any of the table, occlusion, pack, collection or display-case work - produces the same five
failures in the same order. It is older than all of it and still unexplained.

The tour also **crashed** at step 203 rather than failing it: the step took its seat with `orElseThrow`, so
a run that had lost its board somewhere earlier took the client down and the remaining hundred and seventy
steps with it - including every step that would have said what was wrong. It fails the step now.

### Twenty-fourth batch: a deck box made of plastic, and a case you can see into (2026-09-16)

The owner asked for the deck box to stop being wool: "some sort of more solid looking material like a
plastic. And the top doesn't need to be so bulky. And it doesn't need the fake label on the front."

- **White concrete rather than white wool** for the body and the lid, which is the flattest, most uniform
  white vanilla has and the closest thing it owns to moulded plastic. The dark oak foot went with it, to
  black concrete: one box, one substance.
- **The lid was three pixels deep and stood a whole pixel proud on every side**, which is most of a third
  box sitting on top of the second one. It is one and a half deep and half a pixel proud now.
- **The label is gone.** A blank plaque on every deck box in the world said nothing.
- The clasp moved into the step the lid's overhang leaves, so it sits under the lip rather than floating
  in front of where the label used to be.

Guard: `texturecheck` now also asks that every model a color handler tints still has a face saying
`tintindex`. Losing that is silent - the item simply comes out white - and it has happened once already,
when a furniture package arrived with the deck box redrawn without one. Proven by taking the tintindexes
out: the check names the model and the handler.

And, found by photographing it: **the display case drew its glass solid**, so the card inside was a card
nobody could see - which is the whole block. A model using a transparent texture has to say which layer it
draws in; it says `render_type` for NeoForge and registers the same layer through Fabric's own map.

Verified: gate green (592/16), and the scripted client photographed all four -
`111-who-may-use-this-collection`, `112-a-locked-collection`, `113-a-card-under-glass` (glass you can see
through, card standing inside) and `114-deck-boxes` (four boxes, three colors, no label, slim lid).
The tour's own new steps went from three failures to none: two were the scene asking whether the lock had
gone on in the same tick it pressed the button, and the third was a case put down a block below the
player, which the client had and nobody could see.

**The full tour still does not finish**, and neither of the reasons is this work: it loses its table part
way through - identically on `8a567411`, before any of it - and then sticks on the guided lesson asking
for COUNT while the scene presses TAP, repeating until the run gives up at step 305 of 380. The new steps
were run directly with `-PdevsceneFrom=376`, which they are now self-sufficient enough to allow.

### Twenty-fifth batch: the owner's second playtest, first six (2026-09-16)

- **#2, cards put into a deck loading for ever and coming out blank - found at last.** The deck's handle
  was minted only when a deck reached a player's *hand*, so a deck made and fiddled with in the creative
  menu had none; with no handle there was nothing to remember its real cards under, and the first hidden
  copy the menu handed back was kept as-is. From then on that deck really did hold stand-ins: its list
  loaded for ever and a card taken out of it was blank. The handle is minted the first time the server
  ticks a deck with real cards in it now. Two more locks on the same door: a hidden copy nothing can
  restore has its stand-ins dropped rather than kept, and `DeckEdits` refuses an edit that names a hidden
  card instead of handing back a blank one. Guards: `DeckVaultGameTest.aDeckNeverHeldIsStillPutBackTogether`
  and `.aHiddenDeckNothingRemembersKeepsNoStandIns`, both proven; and
  `.aCardRightClickedOntoADeckInCreativeIsStillThere`, which goes through the item's own
  `overrideStackedOnOther` rather than setting the component by hand the way the older check did.
- **#1 and #10, the pack in the hand and on the floor.** Vanilla's flat-item pose tipped it onto a
  diagonal, which is a pose for a thing with no thickness; it uses vanilla's block pose now, which reads
  as a pack being carried. And it stood on its face on the ground - it stands upright. Both were looked
  at: `115-a-pack-held-and-dropped`, through a new scene step that switches to the third person, which
  nothing else in the tour does.
- **#4, letting everyone look but not take.** It was the default and *only* the default: the screen had a
  lock and nothing else, so any other arrangement had to be built one name at a time. What anybody at all
  may do is now the same three switches a named player gets - look, take, add - so a donation box, a
  lending library and a display shelf are things the owner sets rather than hopes for. Protocol 23.
- **#6, the wood in the name.** "Table" and "Chair" sat on the shelf beside Spruce Table and Birch Chair
  without saying what they were. `tools/woodwork.py` writes the plain names too now, from the same wood it
  reads out of the Java, so the two can never disagree.
- **#7, the wooden chairs' cushion**, dyed like the stone ones - and, found doing it, **the ten wooden
  counters, desks and cabinets had the felt property with nothing registered to tint it**: dyeing a spruce
  cabinet changed its block state and not one pixel of its color. One list, `everyDyedBlock`, registered by
  both loaders. Guard: `FurnitureDyeGameTest.everythingThatTakesDyeHasACushion`, proven.

Verified: gate green (596/16).

### Twenty-sixth batch: opening a pack one card at a time (2026-09-16)

The owner asked to overhaul the pack ceremony outright: a stack you thumb through rather than a spread,
the card before a good one lit by what is coming, and a noise to match - "look at Pokemon TCG Pocket".
This is the first half of it, the turn. The cloth tear is the second and is not built.

- **`PackReveal`** in `:core` owns the whole ceremony's arithmetic - the order, what is in front, what is
  behind it, whether to shine, and what the turn is worth - so all of it is checked without a window.
  Tiers rather than rarities, because a showcase common is a card people shout about and its rarity says
  common. Four tests and three properties, one of which is that the light never promises something the
  pack does not still have to give.
- **`PackTurning`** on the client is the stack: the cards left drawn behind the one in front, the front
  card dragged aside with the mouse or turned with a key, the one just taken off flying out over the top
  of everything, and the next one underneath it all along. The light belongs to the card underneath, so it
  stays with the stack while the top one is dragged away.
- **Sounds**: a shimmer while something worth announcing is next, grander for a mythic than a rare, and an
  impact when the card lands. **These are vanilla sounds standing in for the owner's** - four places where
  four of their own would go, one line each.
- The spread is still there, at the end, as what you got.

Their own `TestHygieneTest` caught that the three new properties were being skipped: jqwik needs `@Label`,
and `@DisplayName` on a `@Property` makes the engine pass it over silently.

Verified: gate green (596/16), and the scripted client went through it - `41a-one-card-at-a-time` shows
one card, the stack behind it and the count; the run reports "the pack promised something on 1 of its
cards", which is the card before the mythic and no other.

### Twenty-seventh batch: the owner's notes on the ceremony, and a case for a shop (2026-09-16)

Played, and four things came back about the turn:

- **The stack was a fan.** Cards behind the front one were drawn stepping away down and to the right as
  boxes, which is a hand of cards rather than a pack. They are exactly behind now, and the one underneath
  is drawn as itself - so pulling the top card aside reveals the card that was under it all along, which
  is the whole gesture.
- **A swiped card jumped back to the middle** before flying out, because the travel started from nought
  and the drag had already moved it. It carries on from wherever the hand let go, angle included.
- **Two lights, told apart by what they do.** The next card's sits on the stack and breathes; the card in
  front has its own, steady, tight against it, and travels with it as it is dragged. Pulling the top card
  aside pulls one light away from the other, which is the difference said without words.
- **A showcase mythic announced itself as a showcase.** The tier for a special version was checked first
  and *shadowed* the rarity, so the biggest card in the pack got the smaller noise - the owner pulled one
  and said so. Rarity decides now and the showcase tier is gone, here and in the spread's own glow, where
  it was making showcase mythics purple rather than orange.

And **#5 and #9, the display case**: it was a tall box holding one card. It is a cabinet with a glass top
at a shop counter's own height and depth, so the two stand in a row; it holds four, standing in a row and
leaning back the way a case you look down into shows them; its lining takes dye; and it comes in all
eleven woods. A case saved holding one card keeps it.

**#3, the collection screen's overlapping elements** - which this work caused. The footer's writing stops
short of a number that meant "clear of two buttons in the corner", and the Share button made it three, so
the hint was drawn straight over it. The number is worked out from the buttons now. The tour's overlap
check compares widget against widget and writing is not a widget, so it could not have caught it; there is
a check for this one.

Verified: gate green (597/16), and `41a-one-card-at-a-time` shows one card and no fan behind it.

### Twenty-eighth batch: sharp corners, and the 429 (2026-09-16)

- **Two ways the cards were getting sharp corners.** A card is a rectangle with its corners cut, and both
  of these filled the cut back in. The glow was drawn as square rings, so its corner sat exactly where the
  card has none; it follows the card's own cut now. And the card underneath was drawn whenever there was
  one, so two cards exactly in line showed each other through their corner cuts and the pair read as one
  card with square corners - it is drawn only once the top card has actually moved off it, which is the
  only time any of it is visible anyway.
- **The 429.** Scryfall answers a throttled request with `Retry-After`, saying exactly how long it wants
  to be left alone, and the mod was not reading it: it waited its own five hundred milliseconds and asked
  again, which is asking too soon by definition, and four attempts of that is a pack that cannot be
  opened - which is what the owner kept getting. `HttpReply` carries the header now, the limiter is held
  for as long as the far end asked (or our own doubling, whichever is longer), and the wait is bounded so
  a far end that says "an hour" cannot hang a card lookup for one. Two tests, the first proven.

Verified: gate green (597/16).

### Twenty-ninth batch: the wrapper is a sheet of foil now (2026-09-16)

The owner asked for the pack to be torn for real rather than along a line the mod drew.

**`PackCloth`** in `:core`: a grid of points held together by links, run with Verlet integration and
position constraints - each point remembers where it was, a link is satisfied by moving its two ends
toward each other a few times a step, and a link stretched too far is simply removed. That last part is
the whole trick: a tear is a link being deleted, not a force being exceeded, which is why this is stable
where a spring solver would explode.

Two things had to be true for it to feel like a booster rather than a bedsheet:

- **A hand takes hold of a piece, not a point.** Holding one point and dragging pulls that point out
  through its own neighbours, which is a hole rather than a tear - the first version did exactly that and
  the strip never came off at all.
- **The seam gives first.** A booster has a notch cut in it, and that is what a notch is. The crimp row
  tears at 1.42 times its resting length and everything else at 2.4, so an ordinary pull peels the strip
  along the crimp while a hard pull somewhere else still rips it there.

It is **deterministic** - fixed step, fixed iteration count, and what wobble there is comes from the
pack's own seed - which is what lets any of it be checked at all, and what keeps two people watching one
screen seeing one wrapper. Six tests and three properties, including that the same pack pulled the same
way tears identically and that a four-second stall cannot fling the sheet off the screen.

Three real defects came out of wiring it up, each of which a scripted tear could never have had:

- The sheet was run from the wall clock inside the wrapper's own drawing - and that drawing **stops the
  instant the wrapper opens**, so the one frame that mattered was the one frame nothing asked about.
- The opening was **edge-detected**. The sheet can be run from anywhere, so an edge between two frames is
  an edge nothing sees; it asks whether the wrapper is open, not whether it became open just now.
- The scripted hand dragged forty times inside a millisecond, and a simulation given no time does
  nothing. The tour runs the sheet a frame at a time as it drags.

Verified: gate green (597/16), and `40-tearing-it-open` shows the crimp peeled up and creased over itself
with the body torn in an arc beneath it - foil, rather than a line.

### Thirtieth batch: five from the next playtest (2026-09-16)

- **#5, combining two cards deleted the deck** - and it was mine. The lock that strips stand-ins off a
  hidden copy nothing remembers can strip it to *nothing*, and an empty deck is removed a few lines
  later - so a rule meant to stop cards loading for ever deleted the item instead. Losing the cards is
  bad; losing the box the player is looking at is worse, and leaves nothing to say anything went wrong.
  Stripping now only happens when something real is left, and the empty-deck removal never fires for a
  deck whose cards merely arrived hidden. Guards: `twoCardsPutTogetherStayADeck`, through the item's own
  gesture, and `aHiddenDeckNothingRemembersIsNotThrownAway`, which failed before the fix.
- **#1, no word when a pack does not open.** Everything after the click is somebody else's server
  answering at its own pace, and a throttled one can now be waited on for half a minute. The click is
  acknowledged the moment it happens, and a failure says the pack is back in the inventory - because "it
  did not open" and "it is gone" look identical from the hotbar.
- **#3, the glow was clearly broken.** It drew one single-pixel outline per pixel of spread with a
  staircase at each corner, every ring cut to the same size however far out it was - so successive rings
  did not nest and the corners came out as hatching. Ten rounded rectangles now, piled up outermost
  first, which is how the round glow next door has always worked.
- **#4, corners still flashed square on a swipe.** The card underneath was drawn during the fly-out, when
  the new front card is back in the middle and exactly over it - two cards in line show each other
  through their corner cuts. It is drawn only while the top card is actually off to one side.
- **#2, it tore like paper rather than like a pack.** The whole sheet was loose, so pulling stretched the
  *body* about and the tear wandered into the artwork. A wrapper is stretched tight over a block of cards
  and a block of cards does not billow: everything below the seam is held exactly where it is, and the
  foil itself is far stronger than the crimp. The strip now peels off along the top as one piece. Which
  part of the seam gives first is still entirely the hand's - the tear is yours, the line it follows is
  the pack's, as on a real one.

Verified: gate green (599/16), and `40-tearing-it-open` shows a square pack with the crimp peeled up off
it as a single creased piece.

### Thirty-first batch: the owner's deck box and display case, v5 (2026-09-16)

`Gathering-deck-and-display-v5.zip`, cut against `eb5ea673` - the commit before this session's last one -
so `source-changes.patch` applied clean with no conflicts.

- **The deck box** is a closed flip-top now: a fitted lid with a recessed seam, a broad folding flap and a
  tapered closure tab, in white concrete with gray concrete seams. My version was the right proportions
  and nothing else - a plain box with a band round the top. Theirs reads as a deck box at inventory size,
  which is the only size most people will ever see it at. The shell still takes the deck's own color;
  twenty-four faces carry a tintindex, which `texturecheck` checks.
- **The display case** is flush: a full-width base with no projecting shelf, and a lid at y=15 exactly
  matching the shop counter's felt surface, so a case sits between two counters as one run of furniture.
  Its collision shape is the same envelope, which also makes it simpler than the three-part shape I had.
  All eleven woods regenerated; `woodwork.py` reported nothing left to write, which is the generator
  agreeing with what the package shipped.

The tour's display-case step could not tell a broken sync from a card that had not arrived - the card in
it is looked up on somebody else's server, and a throttled one now keeps us waiting. It asks the server
whether it has a card at all: if it does and this client does not, that is a fault; if neither does, the
run says so and checks nothing, the way the pack-symbol step already did.

Verified: gate green (599/16), and photographed - `114-deck-boxes` shows the flip-top tinted four ways,
`113-a-card-under-glass` the flush case at counter height.

### Thirty-second batch: pockets, tilts, joins and the crimp (2026-09-16)

- **Cards in a deck still loaded for ever in creative - and the handle was only half of it.** The real
  list is pushed by `tellTheOwner`, which only ever ran for a deck **in a hand**. A deck in a pocket -
  which is where one sits while somebody adds cards to it in the creative menu - was never told about, so
  the client had nothing but the public copy and every card in it read as loading. Pushes are filed by
  deck handle now, the way the client has always filed them, and every deck a player owns is described to
  them wherever it is. It still goes only to its owner about their own inventory.
  Two old checks said the opposite and had to go: one asserted a pocketed deck is told nothing, which was
  the bug written down; the other **passed for the wrong reason** - it handed its deck to
  `Inventory.add`, which empties the stack it is given, so it was ticking an empty stack.
- **The cards in a display case leaned the wrong way** - tops to the ceiling, faces to the floor - and not
  far enough to read. They tip the other way and further, toward somebody standing over the case.
- **Cases side by side are one case.** Two ends back to back read as a row of boxes; a case now knows
  whether another of its own kind, facing the same way, is against either end and drops that end - so the
  glass, the lining and the lid run straight through. `tools/casejoin.py` cuts the three joined models out
  of the plain one by two rules and no judgement, so a new case model regenerates all four rather than
  somebody hand-editing three copies of the owner's work. Guards: a row of three, and a case turned the
  other way that must not join.
- **The tear was in the wrong place.** The wrapper is sixteen rows and the crimp is the top four, but the
  sheet had nineteen rows, so the seam fell at four and a half and rounded to five - a sixteenth of the
  artwork came away with the crimp. Seventeen rows now, so the rows land on sixteenths and the seam is
  exactly the crimp's edge.
- **And the crimp comes off whole.** Past the point where it is plainly off, the rest of the seam goes at
  once, rather than the last few links being worried apart - there was no moment it came away, only a
  gradual giving up. The solver is stiffer too, so the strip holds its shape as it peels.
- **Packs took for ever.** Honouring a `Retry-After` of up to thirty seconds holds the whole rate limiter,
  and opening one pack is a set read a page at a time - so four generous waits became minutes of nothing.
  Capped at five seconds: long enough to be a real pause, short enough that four of them is a wait
  somebody will sit through.

Verified: gate green (602/16), and `40-tearing-it-open` shows the crimp off as a single piece with the
body square and whole beneath it.

### Thirty-third batch: the gaps, worked through (2026-09-16)

The owner asked what had been missed and then asked for all of it.

- **The lesson is not broken for players.** Run on its own, from step 296, the guided first game completes
  every step. What breaks it is the state the rest of the tour leaves behind. But looking found a real
  robustness hole worth closing anyway: the TAP and COUNT steps compared **totals**, so a learner who taps
  one card and untaps another - or moves a counter from one card to another - nets zero and the lesson
  sits there having watched them do the thing it asked for. In a tutorial, fumbling is the normal case.
  It now asks whether any one card *gained*, per card, so nothing done elsewhere can mask it.
  `TutorialEvidence` is pure and tested; the client reads the boards and owns none of the rule.
- **The tour reaches the end for the first time.** A step that would not move ended the whole run, so one
  stuck step cost the remaining seventy-seven - which is how that many steps went unrun for months. A
  stuck step is now stepped over, up to a dozen, and the run carries on. It reached step 382 of 382 and
  took 185 photographs instead of about a hundred.
  What it reports is **one fault, not a hundred and twenty-seven**: nearly every failure is "there was no
  board", downstream of the table going away directly after the ante steps. That is the pre-existing
  fault - a run on `8a567411` does it identically - and it is now precisely located and cheap to chase.
  **It is not fixed.**
- **Handing a collection over.** `ownedNowBy` existed and was called by nothing but its own test, so
  whoever put a collection down owned it for ever - and since the owner travels in the item, a cabinet
  whose owner had stopped playing was shut to everybody, which the lock on looking made worse. The
  sharing screen has a Hand over beside Done; the new owner comes off every list and the old owner keeps
  nothing. Protocol 24.
- **The wire.** Every test here runs with stand-in players, who have no connection, so nothing that
  crosses to a client is ever carried anywhere. The newest and least exercised formats now round-trip:
  the sharing screen's four messages, handing over, and `OpenCollectionPayload` - which packs three
  answers into the bits of one number because a stream codec takes at most six parts, and packing is
  exactly where a silent wire fault lives. Every combination of the three is checked.
  This narrows the gap rather than closing it: the round trip proves the codec, not the delivery.

Verified: gate green (605/16).

### Thirty-fourth batch: the second loader, looked at (2026-09-16)

Every photograph this project has ever taken was NeoForge. Fabric builds, passes its own in-world
tests and ships, and nobody had ever *looked* at it - so the wrapper simulation, the reveal, the case
and the tinting were all unseen on half the mod's audience. The scripted tour ran on Fabric.

It came back with 128 failures against NeoForge's 127, and comparing the two texts found one real
difference and one real defect behind it:

- **A table said `!` where it meant "free play".** The top row offers the sentence at several
  lengths and the board takes the first that fits, and the rung below "Turn 1 - Somebody · free play"
  was a bare exclamation mark, with the meaning only in a tooltip nobody knows to hover. The two
  loaders differ only in what they call the development player - `Dev` against `Player840` - and six
  more letters was the whole distance between the words and the mark. Most real names are longer
  than `Dev`.
  The name goes first now. Whose turn it is is already up in the seat columns with a face and a color
  beside it; "for keeps" and "free play" are said nowhere else on the board. The mark is still there,
  after every way of saying it in words.
  The guard caught a flaw in the first attempt at the fix, which appended the shortened rungs after
  the bare mark rather than before it - so the mark still won, and the board still said `!`.
- Everything else Fabric reported, NeoForge reported too, including the table going away after the
  ante steps. **The wrapper tears, falls and reveals on Fabric exactly as it does on NeoForge**
  (`fabric/run/screenshots/40-tearing-it-open.png`), and the deck boxes carry their colors.

Verified: gate green (607/16), and the guard fails without the fix - the ladder without it reads
`Turn 1 - Somebodyorother · free play`, then `Turn 1 - Somebodyorother · !`, then nothing.

- **A collection said `...` where it meant "1 found".** The same shape of fault, found by the same
  comparison, and this one both loaders had. The footer's three things - the count, the hint, and the
  two corner buttons - were laid out with the hint taking all it wanted and the count shrinking into
  whatever remained, so on an ordinary window the count was drawn as a bare ellipsis while
  "Click to take one - right-click for four" ran the width of the screen. The count is the fact this
  screen exists to report; the hint is advice, and advice cut short is not advice.
  So the count goes first, at the longest of three true lengths that fits, and the hint takes what is
  left, at the longest of its three. Both are now said in full on the same window that showed
  `...` before: `1 found - page 1 of 1   Click to take one`.
  The guard is the scripted run's own cut-short check, and it failed three times on the way here -
  first on the count, then on the shortest count when the hint still took everything, then not at all.

Verified: gate green (607/16); steps 125-140 of the scripted run, 0 failures, and the footer
photographed with both lines whole (`43-searching-a-collection.png`).

### Thirty-fifth batch: the table that went away, and the text read through (2026-09-16)

**The table-vanishing fault is found and named.** It was never a mod fault. A table is nine blocks,
three by three, and taking any one of them out takes the other eight with it - which is correct, and
is what made this so hard to see. The scripted run lays a cabinet, a counter, a desk and a row of one
table per wood down beside wherever the player happens to be standing at step 132. For a long time it
happened to be standing clear. The run then gained steps, the player finished the step before a
little further along, and the cabinet went down at `(4,-61,0)` - two blocks past where the table
`(3,-61,-2)` looks like it ends, and still inside it.

Found by asking the table after every tick instead of only where a step wanted it, then by a stack
trace on the block's own removal. That per-tick question is now a permanent part of the run, and it
is the real repair: **one failure naming the step, instead of a hundred and twenty saying "there was
no board"**. The placement is checked against the footprint it actually needs before anything is put
down, and the run says so rather than quietly taking a table out of the world.

**The interface text read through end to end**, at the owner's asking, for the tells of machine
writing: em dashes, trailing ellipses, semicolons joining two thoughts, the second sentence that
restates the first, "not this, but that", and the conversational button. 1,471 lines and seven guide
pages; about fifty rewritten. Among them one that was simply **wrong**: the borrow-a-deck screen said
"Yours to keep" over a shelf of decks that are the one kind of deck that cannot be kept, which every
other line about a loaner says plainly.

`tools/voicecheck.py` now holds the punctuation half of that line mechanically, and is in the gate as
its sixteenth check. It checks only what is a character being present or absent; phrasing needs a
reader, and a checker that guessed at phrasing would be wrong often enough to get turned off.

Verified: gate green (607/16). `voicecheck` fails on the text as it stood before this batch, naming
twelve lines. **The full scripted run has not been re-run since the placement fix** - the last full
run still carried it, and the run that would have confirmed it was cut short. The fix is right by
construction and by the arithmetic above, and it is unconfirmed.

### Thirty-sixth batch: confirmed, and three the owner asked for (2026-09-16)

**The placement fix is confirmed: the scripted run went from 127 failures to 12.** The table now
stands to step 377 of 382 rather than dying at 132, and the five steps of cascade left behind it are
the display case laying itself down the same way the cabinet did - fixed the same way. What the run
reports now is twelve separate faults instead of one fault reported a hundred and twenty-seven times.

One of the twelve was mine: a scene check spelled the word "No thanks" out by hand, so renaming that
button to "Decline" in the text audit read as the loaner screen having no way out of it. It asks the
mod for the label now, which is what it should always have done.

- **The display case locks.** Whoever puts it down owns it, which was already true; crouch and
  right-click now shuts the glass, and again opens it. The lock holds against the owner's own hand as
  much as anybody else's, which is the point of it: every gesture on this block was already the
  owner's alone, so a lock that only shut strangers out would have changed nothing. What it is for is
  a case somebody walks past every day, where one empty-handed click on the way past pockets the card.
- **The glow on a card being swiped follows the card.** It was drawn around the rectangle the card was
  asked for while the card itself slid, dropped and leaned - so it sat level and full width around a
  card that was neither, and came away from the edge that had turned away from the eye. The lens that
  draws the card now says where its corners land and the light is drawn around that. Its own guard
  caught a wrong assumption on the way: a turned card is genuinely not centered on the box it was
  given, because perspective magnifies the near edge.
- **A face-down card has its corners cut.** A face has come rounded all along, because the art arrives
  with transparent corners, so a face-down card beside a face-up one was the only square thing on the
  table. The two back textures are square and they are the owner's, so the shape is made by what is
  drawn: the middle in one piece and each row of a corner as its own slice. A handful of extra draws
  rather than a mesh, because a board can hold sixty face-down cards.

Verified: gate green (615/16), up from 607 by the eight guards this added.

- **The tear is a pixel higher, and the pack keeps its own top row.** A square of the wrapper carries
  the row of the picture above its lower edge, so tearing along the links under the crimp's last row
  destroyed the pack's *first* row rather than the crimp's *last* one. The strip still came away
  whole and the artwork underneath was a pixel short at the top, which is what "the tear is one pixel
  too low" looks like from the outside. One row up, the square that goes is the bottom row of the
  crimp, which is the row a tear is supposed to consume.
  Read off the picture rather than guessed: the wrapper is sixteen rows, rows nought to three are the
  striped crimp and row four is the first orange row of the pack.

Verified: gate green (615/16). The guard fails without the fix, saying "the tear took row 4 of the
pack's own artwork", and `40-tearing-it-open.png` shows the body square and whole under the strip.

### Thirty-seventh batch: the blank cards in creative, found properly (2026-09-16)

The owner reported this three times and it was patched twice without being understood. Investigated
this time by instrumenting every boundary and running once, rather than by reading and guessing.

**What the evidence said.** The same steps, survival then creative:

```
survival:  combine side=client, combine side=server
           tick carriedRedacted=false carriedCards=2 vaultHad=true  -> 3 rows, 0 unnamed, 1 card back
creative:  combine side=client            (and no server line at all)
           tick carriedRedacted=true  carriedCards=2 vaultHad=false -> 2 rows, 1 unnamed, no card back
```

**The cause, which was in none of the places it had been looked for.** The creative menu never
replays the click on the server - it sends the resulting *stack*. And a deck component has exactly
one wire format, `PUBLIC_STREAM_CODEC`, which replaces every card with a stand-in so that carrying a
deck past somebody does not hand them your list. That format is symmetric, so it redacted the deck on
the way **to** the server too. What arrived was a deck of stand-ins under a handle nothing had seen,
and the real list existed nowhere: not on the item, not in the vault, not on the wire. The recovery
that already existed had nothing to recover from - `vaultHad=false` is the whole bug in one word.

Everything downstream followed, including the part that looked like a second bug: the take was
refused outright by the `edit.card().isHidden()` guard, which is why a card came back *missing*
rather than blank.

**The fix.** The client says what it made, during the click, so it is on the wire ahead of the stack
the menu sends after it. It goes to `DeckVault`, which is where the server already looks to put a
redacted deck back together, so the repair happens on the path that was already there.
Believed only from a player in creative, who can conjure any card from the menu they are standing in,
so it grants nothing that was not already theirs; a survival player is refused and does not need it,
because for them the server ran the click itself. Through a seam like `DeckScreenHook`, because a
common class naming the client's networking crashes a dedicated server. Protocol 25.

Verified: gate green (620/16). Three of the four new guards fail without the fix, one of them showing
the stand-ins still on the item after a tick. End to end, the scripted run's own creative pass now
reads exactly as its survival pass does: `3 rows, 0 unnamed` and `1 card(s) with a printing, 0 blank`.

### Thirty-eighth batch: the tournament that could never start (2026-09-16)

**The scripted run finishes with nothing to report: 382 of 382 steps, 0 failures.** It was 127 this
morning.

The last fault was the tournament, and it was one fault wearing four faces - no round one, no Settle
button, no result buttons, no final places - all downstream of a single chat line the run had been
printing all along: `Table 10 is in use, so play cannot start.`

**"Free" meant two different things in two places.** When a desk picks its tables, free meant "no
other event has claimed it". When play starts, it meant "nothing is standing on it" - no game, pod,
signup, match, deck or pot. So a desk would claim a table with something on it and then refuse to
start the whole tournament because that table was in use. Found by tracing every member of the table
line and every condition: `pod=true`, and nothing else.

The pod is the part that makes it a dead end rather than an inconvenience. A draft holds its pools
until every drafter is back, which is right - the cards are theirs and must not evaporate - so a
table with a drafter still logged off is a table the host **cannot** clear. They were told to clear
it anyway, and there was nothing else to try.

Both halves are fixed. A desk claims only tables nothing is standing on, so the two answers agree.
And a table that cannot be pulled apart is given up rather than refusing the event: the tournament
plays on the rest and the host is told which table went, so a stale draft in the corner of a shop no
longer stops the night.

Also this batch: the sharing screen's lock. `CollectionRights` became three rights for everyone -
look, take and add - and the scripted run went on pressing a button labeled "Anyone may look" that
nothing draws any more, which read as the screen having lost its button. It presses Look, and the two
strings the old single toggle used are gone.

**Documents against reality.** The design brief said per-wood variants "were not built"; there are
ten woods of every piece of furniture, generated by `tools/woodwork.py`. Corrected. Its other
promises hold where checked - the torn edge does glow `0xFFD24A` for a rare and `0xFF7A18` for a
mythic, which is the yellow and orange it specifies, and undo is wired through all three modes. The
eight unfinished backlog tickets all wait on a person playing the mod or on another mod's files, not
on code.

Verified: gate green (621/16), and the scripted run reached step 382 of 382 with 0 failures.

### Thirty-ninth batch: a whole-codebase review, and the two worst things it found (2026-09-16)

Seven reviewers over ~110,000 lines of main source: the recent diff, and six bounded areas covering
the whole mod. Each was told the project's own rule that a comment describing a guarantee does not
establish it, and asked to find the enforcing code or report that it could not. The findings are in
the session transcript; these are the two that had to be fixed before anything else.

**The visibility invariant was violated by an ordinary click.** `GameFold.movedCard` closed the
revealed-top window when a card *left* a library and when one arrived on *top* of one, and a card
moved from a library to the same library was neither. The window is positional - "the first N of this
library are face up to the room" - so putting the revealed top card on the bottom slid it onto the
next card and handed that identity to every opponent and every spectator. Repeat it and the window
walks the whole shuffled library, in order. This is how cascade, Bolas's Citadel and every
reveal-until effect resolve, and the card menu offers it.

Fixed by asking the question that actually matters rather than by adding a third move to the list:
the window is kept only while it still holds the cards that were revealed into it. That closes the
class, and it keeps the behaviour the old rule got right - tucking a card under a revealed top does
not disturb it, because the cards in the window have not changed.

**Why the suite missed it.** Every case in `RevealedTopTest` asserted `revealedIn(...) == 0`, which is
the mod's own bookkeeping answering a question about itself, and the property suite's library oracle
asks `openCardsOf` - the same function the visibility rules use to decide. A window whose count is
right and whose contents are somebody else's cards reads as correct to both. The new guard asks the
only question that means anything: which identities does a spectator actually receive. It fails
without the fix on the first move, naming the card.

**And the deck vault could be written over by anybody.** The payload added yesterday was filed by
deck handle alone - and a handle is not a secret. It rides on the item in its own component, which is
sent to every client that can see the item. So it was keyed on something broadcast, and a creative
player who had walked past a deck could rewrite what the server believed was in it. Now filed under
the player who made the deck, in the shape `CreativeDecks` already used; it is refused unless the
deck is the one shape the gesture can produce; and it is rate-limited like every other costly
request. Three guards, including a stranger failing to write over somebody else's deck.

Verified: gate green (623/16). The library guard fails without its fix, naming the card a spectator
was shown.

### Fortieth batch: every Critical the review found (2026-09-16)

All ten are fixed. The full list is `docs/reviews/full-review-2026-09-16.md`; the eight beyond the
two in the last batch:

- **A seat given up outlived its game.** The records were saved to disk and never retired, and a
  give-up says "somebody may take this board" - which permits another player being seated at it and
  sent the hand that goes with it. A seat given up on Monday said yes again on Friday. Retired when a
  game begins. The second reach into the visibility invariant this review found.
- **And the same set was emptied wholesale at its ceiling**, which ordinary play reached, stranding
  every live seat whose player had walked away for the rest of its game with nothing said. The oldest
  goes now, one at a time - the argument `Owed.MOST_OWED` already makes about its own ceiling.
- **A tournament that could not start gave up its tables first and refused afterwards**, so the next
  press of Start found none to pull apart, skipped the check, paired a round and seated nobody. Mine,
  from the last batch. It decides before it moves anything now, and numbers the tables before any of
  them go, because the number is a position in the list.
- **Adding a table still used the weak "free"**, and the next round's `clearTables` ended the game on
  it, handed back the decks and unwound the ante of players who had nothing to do with the event.
- **A restart with nobody online destroyed a tournament.** The away-from-board grace ran against the
  whole field at once, conceded every match, dropped every entrant and recorded the event as
  finished - rated, prizes paid. Terminal, silent, nobody at fault. The clock is about a player
  walking away, so it now runs only for players this server has actually seen.
- **A draft handed its pools out before striking the pod off**, so a crash between the two handed
  everybody a second pool and every card in the draft existed twice. Struck off first, with the pack
  record carried past it - which a test caught immediately, because ending a pod also forgets it.
- **A pasted deck link could take the server down.** The quantity came unbounded off somebody else's
  server and was built into a list before anything checked its size. Clamped where it is read, and
  the flattening stops one past what an item can hold.
- **The client could evict the table you were sitting at**, closing your own game screen mid-turn and
  again on the next board to arrive. Oldest first now, and never the seated one.

Four new guards, each proven to fail without its fix. Two of them were wrong first and said so: a
lone table is never "in use" to `TablesApart` because a line of one has no shape to change, and the
record a test cares about must not be the oldest one in the set.

Verified: gate green (627/16).

### Forty-first batch: the Importants that touch property (2026-09-16)

The five the review found where a card could duplicate or vanish, plus the checks around them.

- **A deck could come back holding twice what went in.** The vault's repair takes the cards it
  remembers and then re-adds anything in the arriving copy that is not a stand-in, on the reading
  that a wire copy has every card hidden. It decided that with `isRedacted`, which asks whether
  *any* card is hidden - so a copy of a hundred real cards with one stand-in among them read as a
  hundred cards just added. It now believes the copy only when the stand-ins account for exactly the
  cards it knows, which is what a wire copy is; anything else is put back as last known.
  The first attempt at this was wrong and two tests said so: the legitimate case - a creative player
  right-clicking a card onto a deck - *is* a mixed copy, and refusing all mixed copies refused it.
- **A saved game was destroyed by a failure to save it.** `writeSession` returned on a missing key
  or an encryption error, which skipped writing the bytes entirely - so a table restored from disk,
  holding both the live game and the sealed copy it came from, was saved with neither. It falls
  through to the copy it found now. Losing the moves since it was opened is bad; losing the game is
  worse.
- **And one unlucky read made that permanent.** `SessionKeyring` latched "tried" before the attempt,
  so a single `IOException` at boot meant no game on that server opened or saved again. Latched on
  success now, and `forget()` - whose javadoc said it was called when a server stops, and which was
  called nowhere - is in the teardown list.
- **Card histories survived one path and not the other.** Putting a card into a deck one at a time
  kept its story; doing forty at once through the deck builder threw every one away, and taking from
  a collection by identity pruned the oldest story for that printing outright. A story is the one
  thing in this mod a player cannot get back. Both bulk paths carry it now.
- **A printing with an unusual color identity would disconnect everybody it was sent to.** The
  reader took at most eight colors of at most eight characters; the writer had no limit, and a
  mis-set length is not one broken field, it is every field after it in the packet. Trimmed on the
  way in, like the token list beside it that already was.

With them: the undo rule no longer reads an empty table as unanimous consent, and no longer says
"only seated players" while checking whether a seat exists; copying a card out of a hand or library
is an information boundary, because it puts that card's name on the battlefield; a retired secret
verb no longer refuses a whole saved session; two payloads that wrote a file or spawned items per
packet are budgeted like their neighbours; and `tablecheck` asks about every position a serverbound
payload carries rather than only ones called `table`, which found two that were not covered.

Verified: gate green (628/16). The duplication guard fails without its fix, saying a deck of four
came back holding eight.

### Forty-second batch: the tournament and data Importants (2026-09-16)

- **"Who will play" had three definitions and two answers.** `beginPreparing` and the host screen
  asked the phase; the seat check before a limited event asked whether it was a large event, which is
  a different question with nothing tying them together. A large event whose host never opened
  check-in counted nobody, so the check that refuses a pod too big for its table passed every time.
  One accessor on `Tournament` now, and all three ask it.
- **`begin` warned and carried on.** The join of the home table threw its answer away and the seat
  count was a message rather than a refusal - both after the event had already been moved into
  preparing, which has no way back to sign-up. A home table with anything on it left the line split,
  reported half its seats, stood most of the pod up, and the only exit was to call the event off.
  Both are asked before anything is committed now.
- **A limited event took two hundred and fifty-six sign-ups** and found out at Begin that a pod holds
  eight. Refused at the ninth, which is one message to one person at the moment they ask.
- **`turnPassed` counted an extra turn and returned without saving it** on one branch.
- **A booster slot count came off somebody else's file and sized an array.** A tampered or corrupted
  set file saying two billion was an eight-gigabyte allocation the first time anybody opened that
  pack. Bounded at 256, the way the sealed reader beside it already bounds its counts.
- **The card list for a set stopped at eight pages and said nothing.** That list is what the coverage
  auditor computes the completeness guarantee from, so a set past fourteen hundred printings - Secret
  Lair, the list-shaped sets - had cards that were never reported as unobtainable and never swept
  into the Archive Pack. Simply unreachable, with nothing anywhere saying so, which the faucet code
  names as the worse of the two ways to be wrong. Forty pages now, and it returns whether that was
  all of them, because `:core` has no logger and is not getting one: the caller that has one says it.
- **The card cache wrote straight into its file**, so a crash mid-write left half a card and two
  workers could interleave into one. Written whole and moved into place, like the collation cache
  five files away. And a cache that cannot be written no longer fails the import whose data already
  arrived.

Verified: gate green (628/16).

### Forty-third batch: the client Importants (2026-09-16)

- **A menu acted on the board it was opened against.** Boards keep arriving the whole time a menu is
  open, and a palette stays open while somebody types - so "turn right" read an angle that had
  already changed and sent that angle plus fifteen, putting the card back where it had been, and the
  tap filter read a card as untapped that somebody else had tapped since. Menu actions read the live
  board now, falling back to the captured one only for a screen whose table has gone.
- **Three per-table maps grew for the whole connection.** Only the boards were bounded; the pot, the
  terms and the away seats were filled by payloads the server sends about any position it likes, and
  nothing cleaned them. Eviction goes through `forget` now, so everything about a table leaves with it.
- **Two caches validated before they looked up.** The art cache parsed a URI and lowercased a host for
  every card on every frame before consulting the answer, and the set symbols built a three-part
  string key the same way - both re-deciding something that cannot change for a given input. A board
  of sixty cards was thousands of URI parses a second on the render thread. The answer comes first
  now, and nothing about what may be fetched has changed, because nothing reaches either cache
  without passing the check.
- **A flight was asked about once per card.** `isFlying` takes a lock shared with the network thread
  and copies a list; at sixty cards that was sixty of each per frame, at the moment the board is
  busiest. Asked once for the frame now.
- **Reduced motion never pruned what it was skipping**, so the note of everything a player moved grew
  for the session - and only for the players who turned the setting on.
- **The set symbol had silently stopped being drawn.** It was printed through the lens the whole pack
  used to use, and when the wrapper became a sheet that tears, the lens went and took the only call
  with it - while the comments went on describing a symbol nobody was drawing. It is on the cloth
  now, which is better than where it was: it creases and tears with the paper.
  Photographed (`39-a-sealed-pack.png`).

Verified: gate green (628/16).

### Forty-fourth batch: the Minors worth doing (2026-09-16)

Mostly small, and two of them player-visible.

- **Handing a collection over is asked twice.** One press on a typed name gave away everything in a
  cabinet, permanently, and a transposition that happens to be somebody real is all it took. The
  second press is the confirmation, and it names who they are about to hand it to - which the first
  could not, because until the name resolves there is nobody to name. Every other irreversible thing
  in this mod is confirmed; this was not.
- **A full case said it "already has a card in it"**, which stopped being true when it started
  holding four.
- **The lesson said "Practice cards. They cannot be kept."**, which is the exact line `DIALECT.md`
  uses as its example of one sentence too many. It says "Practice cards."
- **The crimp's size was written out twice** - once in the picture's own constants and once in the
  solver - in the one place two numbers drifting apart would tear the pack in the wrong row, with
  nothing to say so. Derived from one now.
- **The action budget swept the bucket it had just debited**, so past its bound every successful
  spend reset everybody to a full burst, and the refusals - which are what a flood is made of - swept
  nothing. Swept before the debit.
- **Two wire fields had no bound**: a custom id took vanilla's 32,767 characters inside payloads
  carrying a thousand cards, and the summary list was the one unbounded list in the package.
- **A shop counter holding exactly one set divided by zero**, unreachable today and one line to
  close.
- **Two maps outlived the players in them**: a pending hand-over, and the question a table asks about
  a deck, which held an item stack per player and was swept only wholesale at 512.

Verified: gate green (628/16).

### Forty-fifth batch: the last of the findings (2026-09-16)

- **The block entity renderer built four components per seat per table per frame.** It runs for every
  table in the world; the table screen already keeps exactly this array for exactly this reason.
- **The foil allocated two arrays per vertex**, which on a card being read is about seven thousand a
  frame, and a fresh `Random` per card per frame besides. Written into scratch and reseeded.
- **The host's advice was a key joined from a string off the wire, per frame** - so a phase this build
  does not know drew the raw translation key as the panel's text. Matched against the phases it knows.
- **The deck-site fetch was the one of the mod's three outward paths with no rate limiter**, no
  retries and no `Retry-After`, in a file whose own comment names all three and says being a good
  citizen at each should not be three copies of the same loop drifting apart. It goes through the
  fetcher now, and keeps its own wording: the fetcher's message is about a request, and the person
  reading it pasted a link.

**Every Critical and every Important from the review is closed.** One is documented rather than
bounded - the metadata store holds every card it reads for the run, and now says so instead of
claiming a large cache costs disk and not heap.

Verified: gate green (628/16), and **both loaders reach step 382 of 382 with zero failures**, which
is the first time that has been true of Fabric.

### Forty-sixth batch: the second review round begins (2026-09-17)

The first round read about a seventh of the mod. Ten reviewers were sent over all of it, every file
assigned to exactly one of them. Three came back before the rest hit a rate limit; this is their
first crop.

- **A resized sharing screen crashed the client.** How many rows fit is worked out from the window,
  so growing it - or dropping the GUI scale - raised the count while the scroll stayed where the
  player left it, and the row loop read past the end of the list, out of `Screen#init`, which nothing
  catches. Two sibling list screens clamped for this reason and the third did not. The clamp is one
  rule in `ListScroll` now, used by all four call sites, with a test that fails without it.
- **The deck builder could be left with a dead Finish button.** Its outstanding request was dropped
  on `removed`, which also fires for a detour - opening Sleeves puts a screen in front and comes back
  to the same builder. So the answer to a Finish already in flight was thrown away, and the builder
  returned with Finish greyed out, a full selection and nothing said, the deck having in fact been
  built. Dropped on `onClose` now, and neither button that leaves the screen is offered while an
  answer is owed.
- **Taking from a collection failed silently once the box was gone.** No message, no page, and the
  click still played the sound that means it worked - for as long as the player kept trying. It says
  so now, the way building from the box already did.
- **A click on the action palette's heading ran the first action in the list.** The row arithmetic
  subtracted in doubles and cast to int, which truncates toward zero, so the band above the first row
  read as row nought. For a seated player that is a random discard, with no gesture behind it.
- **Walking back to a table replayed everything missed at once** - every library rattling together,
  every pointed-at card ringing, every sound on top of the others. Only the *first* board was guarded
  against that. A resumed watch is now told apart from the next moment by the same measure the card
  flights beside it use.
- **A deck built from a list into a collection skipped the text cleaning** the two sibling paths
  apply, so a decklist's own `Name:` line reached other players raw - and a name carrying formatting
  codes can hide what is drawn after it or pass itself off as the server speaking.
- **Two settings the server reads were not settable.** `collection.starter_set` and
  `starter_product` are read, consumed and documented, and were in neither the known-keys list nor
  the default file - so the command said no such setting, they never listed, and the config reader
  printed a note at every start saying a line the server was reading was not a setting. An operator
  following the design brief was told twice that a working feature did not exist.
- **Two lists were written in place** beside two that are written whole and moved, one of them in a
  class whose own doc says it does the safe thing.
- **Three handlers had missed the rate-limiting sweep**, including one that broadcasts to every
  seated player per packet.

Verified: gate green (628/16).

Also in this batch, not yet looked at in a window: the deck box reshaped to the proportions of the cards
standing in it - eight across, twelve up, eight back, with a lid band, a cap, a hinge along the back and
the catch on the front - because it was very nearly a cube, which is a box for anything (#9b).

Still to do from that list: the collection screen's overlapping elements (#3), a multi-card display case
that fits beside a counter, in every wood and dyeable (#5, #9), and the deck box looking more like a deck
box (#9b). The owner also asked whether the pack tear could be a real cloth simulation (#11) - answered in
conversation, not built.

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

## 2026-09-17: second minor batch, and the owner's playtest

Committed through a green gate (641 NeoForge in-world tests, 16 Fabric): the second batch of minor
fixes (core/ui layouts, pack-opening rendering, DevScene hardening, statecheck, tablecheck and
texturecheck rules) and spellcheck rewritten onto a Java tokenizer, which found and renamed six
British-spelled names that the old line matcher could not see.

Still unverified: the scripted tour was stopped partway for the owner's playtest. Before it
stopped it logged FAILs at "a click on the felt of a replay moved the game", "no two looked-up
cards to put together" and "no inventory to put two cards together in". Not yet read.

Found in the owner's playtest log (client closed normally after about 28 minutes): the Archive
audit walk trips Scryfall's rate limit (32 "Could not audit <set> for the archive ... HTTP 429")
and MTGJSON's collation fetch for CON answers HTTP 301.

The owner's playtest list, worked on 2026-09-17 (not yet through the gate at the time of writing):

1. **Results like the Companion app.** The grid of every outcome (sixteen buttons for a best of three) is
   replaced by three rows of counts - You, the opponent, Draws - and one Submit, which reads Confirm when
   the counts match what the opponent reported and is grayed with the reason when they are not a result
   or were already sent. The host's settle grid is the same counts with Record. Pure rules in
   `ResultTally` (core tests); `AccessibilityProbe` stages 12-13 rewritten for the counts (not run).
2. **Archive packs held precon cards.** Cause: "all" loot sets meant expansion and core sets only, so no
   Commander, Masters or Jumpstart set was ever in play and their cards were all archive. "All" is now
   every paper set anything was sold for (`SetRelease.everySold`, test).
3. **Display case glass stretched.** All 44 case models sampled 14x14 texels of glass over faces 5.5 tall.
   UVs now one texel per pixel; `texturecheck` fails any face drawing glass at another density (330 faces
   before the fix).
4. **A detailed card view with history in the collection block.** Shift-clicking a card in a collection (or
   any click where the box is read-only) opens `CardOverviewScreen`: the card as large as the window
   allows, its full text, how many copies the box holds, and each storied copy's history, newest first,
   scrolled with the wheel; Take one, Take four, Turn over, Back. The server answers
   `CollectionCardAskPayload` with `CollectionCardPayload` (at most 32 histories and a count of the rest),
   only for a player standing at the box. Layout pure and property-tested at every window size
   (`CardOverviewLayout`); `CardStoryGameTest.theOverviewHearsEveryCopysHistory` checks what is told.
   Not yet seen on screen and not in the scripted tour.
5. **Display case cards overlapped.** 0.218 wide at 0.21 apart. Now `DisplayCaseRow` (0.31 tall, 0.215
   apart), tested to leave a gap and stay inside the glass; guard proven with the old numbers.
6. **Languages.** Owner's rule: foreign-only printings (a Japanese bonus sheet, a Japan-only promo) stay;
   another language's copy of an English card does not. `CardMetadata` keeps `lang` and
   `illustration_id`; `ForeignPrintings` drops copies from the printing chooser (by shared artwork) and
   from a set's archive (a set wholly in another language that is not a promo set); a booster that names
   no kind no longer falls back to a Japanese product. Guards proven. A default-language setting was not
   built: the owner said to skip it if it was not easy, and it is not (translations have their own ids).
7. **Alt card view cut text off.** `CardTextFit` (pure, property-tested; guard proven) fits every face's
   text: more room first, then smaller text to a floor, then columns; history goes before rules text
   shrinks. Draft, Pile and Trade no longer suppress the view; Sideboard adds a text-only panel. Not seen on
   screen.
8. **Mouse Tweaks.** A deck on the cursor swept over cards with right-click held puts each in, with or
   without Mouse Tweaks: `DragSweep` (pure, tested) decides the clicks, `DeckSweep` makes them as ordinary
   right-clicks, a mixin on both loaders reaches the screen's slot click, and NeoForge's listeners run at
   HIGHEST so Mouse Tweaks never drops the deck into an empty slot mid-sweep. Not run with Mouse Tweaks
   installed.
9. **Collection mode with every set by default.** Collecting was already on by default with loot_sets
   ["all"]; "all" now really means every set, per 2.
10. **An archive pack per set.** Built: a pack names a set family (`PackComponent` kind), loot picks one of
    every family at random, and only that family is audited when opened - no walk over history at start,
    which is what Scryfall rate-limited. An empty family opens as another (up to four), then the pack is
    handed back. Pack shows the family's set symbol and "Archive Pack: SOS". Old archive packs open as a
    random family. Facts files bumped to version 2.

## 2026-09-17: loading speed (the owner's request to optimize Scryfall and MTGJSON use)

Measured first: one Scryfall worker at 100 ms for everything, so a pack waited behind set searches and
audits (627 HTTP 429s in the logs, one pack failed on one); card art fetched only when a card was turned
over, two at a time; MTGJSON set files uncompressed (450 MB cached), refetched weekly, reparsed per run,
The List's 17 MB file parsed again for every set; a set with no collation searched in full on every pack;
CON's 301 not followed.

Done here (gate pending at the time of writing):
- Scryfall work runs in lanes on its one worker: player lookups (packs, decks, names, tokens, imports)
  before server needs before background (refresh, audits, index warm). `findAll` and `card` answer at once
  from memory when every card is known, with no queue at all.
- A set's printings are read once per run (`everyPrintingIn`), not on every made-up pack.
- Requests ask for gzip and unpack it (MTGJSON's NEO file: 5.8 MB to 1.7 MB on the wire); same-host
  redirects are followed, up to three (fixes Conflux), other hosts refused (`HttpFetcherTest`).
- MTGJSON files of a set released over 120 days ago are trusted for 90 days rather than 7 (a `.released`
  file beside each); a companion set's printings and colors are read once per run; the last two parsed
  set files are kept so a set's packs and products do not parse twice (`MtgjsonFeedTest`).
- A pack's card art starts downloading as the pack screen opens, six at a time instead of two.
- A pack whose card names fail to come back is asked once more after two seconds before it is handed back.
- The card art cache is trimmed to 768 MB, least recently used first, once past 1 GB (`CacheTrim`).
- Bulk data (Scryfall's daily card file as a local index): see the next section.

Scripted tour on 2026-09-17 (after the playtest batch): reached 387 of 387 with 8 failures. Fixed: the
tournament steps still pressed the old "2-0" result buttons (now the counts and Record); a gentle pull
opened a pack, from the second minor batch weakening the seam's diagonals (reverted, guarded by
`PackClothTest.aShortPullDoesNotOpenIt`, proven failing with the weakened seam). Not explained, and
not from this work: a second run with the camera change reverted failed the same four ways, so it was put
back - "framing the whole table gives a mat 158 by 72 on the block and 131 by 59 on the screen" (the run's
window was 427x240), "a card that was pointed at is not ringed", "the row of tables in every wood would have
gone down on a table already standing" and "no other tables stood up".

Chased on 2026-09-17, after the bulk-data work:
- **The tables were a world left over from the last run.** The run makes a world of its own with a fixed
  name and Minecraft kept it between runs, so last run's tables were still standing and the scenes that
  check for room refused. The run now throws that world away before making it.
- **The tables within one run** were the run's own, from a scene an hour earlier: the scenes work out a
  corner from wherever the player is, and the player walks. A scene now takes away any of its own tables
  standing in the way rather than refusing, and the run starts from a fresh world.
After those fixes the run reached 387 of 387 with one failure left, the framing one below.
- **Two views, two sizes** (block 158x72, screen 131x59, at a window of 427x240): not a regression. The
  seated view fits the whole table into what is left after the life totals (16 px) and the hand (76 px) -
  148 px of 240 - while the view on the block uses the whole window, so at a small window the seated one
  cannot be within a tenth of it without cropping. It needs the owner's call: let the board run under the
  hand when framing everything, or accept the difference at small windows.
- **The ring when pointing** was the client taking a quiet table for a rejoin. The run printed
  `log.gathering.pinged(Seat,Card:ById)` - the line and its card were there - and the client threw the
  news away: it read "no board for more than eight seconds" as "this log ran on without us", and a table
  where nothing is happening sends nothing. The rule is now how much is unread rather than how long it has
  been (`LogCatchUp`, tested): a handful of lines is news, dozens are a history to take in quietly.
- A run where the machine's real mouse is used at the same time fails wholesale (114 failures, cascading
  from a hover that landed nowhere): the run parks the real cursor. Runs must be left alone.


## 2026-09-17: card lookups from Scryfall's bulk file

Owner-approved: a local copy of Scryfall's `default_cards` bulk file per server, so card lookups stop
waiting on the rate-limited API. About 110 MB of disk once built, a minute or two on first start, new
printings a day late, the per-request API kept as the fallback. `cards.bulk_data = true` by default;
false turns it off. This is a copy each server downloads for itself, not card data shipped or relayed.

**How it works.** `core/.../scryfall/bulk/`: `BulkRefresh` asks `GET /bulk-data` once (through
`HttpFetcher`) and downloads only when `updated_at` differs from the built index and the last build is
at least 20 hours old. Scryfall's list no longer carries `download_uri`, only `jsonl_download_uri`
(gzipped JSON lines); both are read, gzip or not, told apart by the bytes. The address must be https on
`data.scryfall.io` exactly (`BulkCatalog`). The download goes to a temp file, then `BulkIndexBuilder`
streams it one card at a time into `cards.dat` (each card's whole JSON, deflated against a dictionary of
field names and URL shapes) and `cards.idx` (id-sorted offsets, prices, dates, flags, set and number, name
and set lists; CRC and version checked on open), in a directory of its own; a rename and an atomic
pointer swap make it current. `BulkCardIndex` answers by id, name (split and double-faced halves),
name in set, set and number, a set's printings, a card's printings, and tokens. In `:common`,
`BulkCardData` (owned and closed by `CardDataService`) builds on its own thread and answers on two
more; `CardDataService` asks it first for `card`, `findByName`, `findAll`, `printingsOf`,
`everyPrintingIn`, `everyPrintingToAudit` and `tokensNamed`, and goes the old way when it is not ready
or misses. Imports resolve through `BulkFirstStore`. Hits are kept in memory (`DiskCardMetadataStore.remember`,
dated by the file's `updated_at`) so `peek` works; they are not written out as per-card files.
`DeckCheck` waits for a card the copy has, as it does for one on disk.

**Verified.** `:core:test` 1920 tests, 0 failures (`BulkCardIndexTest` 11, `BulkRefreshTest` 6, config);
`coretestcheck`, `langcheck`, `doccheck`, `spellcheck`, `statecheck`, `runcheck`, `savecheck` pass;
`:neoforge:compileJava :fabric:compileJava :neoforge:compileGametestJava` build. Guards proven failing
with the fix reverted: host allowlist removed (host test fails); old index deleted before building
(failed-build and failed-download tests fail); reader parsing the whole array first (streaming test
fails). A real run of the core refresher on this machine, outside the gate: 118,179 printings,
download and build 12-14 s, `cards.dat` 103 MB and `cards.idx` 7 MB, open 0.04-0.09 s, about 15 MB of
heap; a second run said UNCHANGED in 0.3 s. Compared with the live API: set `blb` (398) and `lea` (295)
identical in order; Fire // Ice printings identical in order; Sol Ring's 136 the same set, not the same
order; Lightning Bolt 69 against 67; tokens Cat 8 against 8 with 3 different printings chosen.

**Not the same as the API, known.** Name searches hide what Scryfall hides as far as it was measured
(tokens, emblems, art cards, memorabilia sets, playtest cards); two Arena-only Lightning Bolts Scryfall
leaves out are still listed, for reasons not found. Ties in price order, and which printing stands for a
token, are not Scryfall's. A name with no printing named now resolves to the cheapest priced printing
rather than the collection endpoint's choice - the same rule the in-memory store already applied.

**Unverified.** Nothing in a running server: not the game test server, not a client. The game test
server will download the file into its own run directory on first start, which the gate has never done.
The real download is not exercised by any test. Windows file deletion of a replaced index that is still
open is best-effort and retried at the next start, never seen.

## 2026-09-17: the owner's second playtest list

**Cards destroyed by putting them in a deck (his 1), fixed.** In the creative inventory the client does
its clicks itself and sends the slots afterwards, and the copy of a deck a client holds has the deck's
own cards hidden - so the server put its own deck back over the client's, dropping the card that had
just gone in, while the slot the card came from arrived empty in the same breath. `CreativeDecks` now
keeps any real card the client added to a hidden copy (`CreativeDeckGameTest.acardPutIntoaDeckInCreativeSurvives`,
proven failing without it: "1 required tests failed"). The sweep gesture no longer clicks slots on the
client at all: it sends the slots it crossed (`DeckSweepPayload`) and the server does the inserts through
the same method a single right-click uses (`DeckSweeps`, three in-world tests). The creative inventory
renumbers its slots, so the client maps them through `InventorySlots` (pure, tested).

**The pack ceremony (his 5).** The whole top now comes away as one piece - every link across the seam is
cut at the moment it opens, rather than the strip hanging by its diagonals (`PackClothTest.theTopComesOffInOnePiece`);
the cards wait 900 ms after the top comes off before the first one turns; and the rarity glow is brighter
while the pack stands open, which is the moment it is for.

Still to do from that list: the display case glass edge at joins, loot and the Mana Coin, the village
shop, tournament settings and hosting, the labels above blocks, notices over an open screen, the camera,
the sideboard in the deck builder, the chair backs and the creative menu order.

## 2026-09-17: two model reports from the owner's play

**The glass edge down every join.** Making the case glass one texel to a pixel (playtest item 3 above)
left u following the model's own x. On a case standing alone that is right and always was: the glass is
sixteen texels square with a one-texel frame line round the outside, so with u = x the frame texels fall
at x 0-1 and 15-16, exactly under the corner posts and the end rails - the texture's frame and the case's
woodwork are the same line, which is why nobody ever saw a glass edge on a single case. Joining two cases
takes the post away and runs the pane the last pixel out to the block's edge, so that frame texel was
drawn in the open, and the neighbor drew its own a pixel away: the bright double line the owner saw.

`tools/casejoin.py` now slides the window one texel off each joined edge (its docstring carries the whole
reasoning). An end case draws the frame line once, on the last pixel of its own outer edge where the post
still stands; a middle is sixteen pixels of pane against fourteen texels of interior, so it is cut at the
block's midline and each half slid off its own edge - the cut falls between two interior texels that are
both fully transparent, so there is nothing there to see. A row of any length now reads as one pane with
an edge only where the glass stops. Density is unchanged and `texturecheck`'s rule did not need to move:
418 glass faces over the 44 models, all one texel to a pixel, all inside the texture. Proved still failing
by putting a 14x14 window back on one middle face.

**The chair back floating over the seat.** Adding the dyeable cushion (2026-09-16) cut the seat from two
pixels to one, y 8-9, and raised the back posts to start at y 10, on top of the cushion. But the posts
stand at z 12.5-14.5 and the cushion only reached z 14, so the last half pixel of each post had nothing
under it for a whole pixel of height and the back hung over the seat with daylight beneath it. The posts
now start at y 9, on the seat itself, as the three stone chairs' backs always have, and the cushion stops
at z 12.5, flush against them - no overlap, so no two faces in the same plane. Their side UVs went from 13
to 14 to match the pixel of height gained. Eleven wooden chairs, written from the plain one by
`tools/woodwork.py`; the stone chairs were already right and did not change.

**Unverified.** Neither has been looked at in a running game: no client ran for this. What is checked is
the arithmetic - every glass window inside the texture at one texel to a pixel, and no gap left under a
chair back - and that is not the same as looking at it.
## 2026-09-17: a sideboard in the deck builder

Reported by the owner: the collection block's builder had no way to put a card in a sideboard,
while the deck list already moved cards between the two.

`DeckBuild` now holds three piles instead of two. `aside` puts a copy beside the deck, `moved`
carries one copy between any two piles in either direction, `without(printing, pile)` takes it
out of the pile it was clicked in, and `printingsOf` and `copiesOf` count both lists - so a card
set aside is no longer a card the box can be asked for a second time. Both lists share one
`MOST_CARDS` bound, because both become one deck item.

`BuilderList` (`:core`) places the list column: a heading and its rows per section, empty
sections left out, the scroll limit and whether a line is wholly inside the window. The builder
screen had that arithmetic inline and measured its own height while drawing, which with a second
section at the foot meant whatever the height was short by was exactly what could not be scrolled
to.

**Gestures.** Left-click still adds from the box and takes a row back out - the acts that repeat.
Right-click now opens the deck screen's own menu in both halves: the sideboard and the command
zone from the box, every pile a card is not already in from a row. That replaces right-click
meaning "make commander", which is the gesture DIALECT warns turns a deck builder into a
Commander deck builder. The sideboard entry is grayed when the box has no copy left, as a
left-click on the same card already refuses. Hint line updated.

`BuildDeckPayload` carries the sideboard, bounded against what the deck and commander have left
of `MOST_CARDS`. `CollectionView.build` claims those cards through the same `claim` call - out of
the box, history kept, counted when missing - and puts them in the deck's sideboard.

**Verified.** `:core:test` 1943 tests, 0 failures, 5 skipped; `:neoforge:compileJava`,
`:fabric:compileJava` and `:neoforge:compileGametestJava` build; `langcheck`, `doccheck`,
`spellcheck`, `voicecheck`, `statecheck`, `savecheck`, `scenecheck` and `gesturecheck` pass.
Guard proven failing with its fix reverted: with `printingsOf` counting the deck alone,
`DeckBuildTest > copies of a printing count across both piles, so the box is not asked twice`
fails with `expected: 3 but was: 2`.

**Unverified.** `DeckBuilderGameTest > aSideboardComesOutOfTheBoxToo` compiles and has not been
run - the in-world server was not run from this worktree. Nothing about the screen was seen
drawn: the menu over the box grid, the sideboard heading and its count, and the scrolling of two
sections all need a client. `DevScene.buildADeck` now takes the commander and the sideboard off
the menu, and that scene has not been run either.

**The Mana Coin's art (2026-09-17).** The owner asked for a stand-in to play with rather than waiting
on his own: `tools/manacoin.py` draws it, reading the five colors out of the mod's own mana orbs so the
coin and a card's pips are the same five, and striking a dark rim round a swirl of them. It is a
stand-in and says so; the owner replaces it whenever he likes, and the script is how it was made rather
than a grid of pixels nobody can reason about. Art is otherwise still the owner's - texturecheck's
OWED list is the way to name art that is promised but not drawn.

The scripted run after the whole list: 387 of 387, one failure - the two views' framing, which the
owner has left alone for now.

## 2026-09-17: the card a deck really was destroying

The owner reported it twice, and the first fix missed it. Chased with a step of the scripted run
(386-390: a deck and a card in the hotbar, the creative menu, the deck picked up, the card right-clicked
into it, the deck put back, and then the *server's* inventory read) - which reproduced it at once and is
now the guard.

**What was happening.** The creative inventory does its clicks on the client and sends the slots it
changed afterwards. A deck crosses the wire with its cards hidden - and `DeckComponent`'s public codec
hides them in *both* directions, so the card the client put in came back to the server as a stand-in,
while the slot the card came from arrived empty in the same breath. The card was destroyed by being put
into a deck. Nothing in the server ever saw the real card, which is why keeping "what the client added"
could not work.

**The fix.** The gesture is told to the server, which does it against its own copy: `DeckSweepPayload`
now carries the deck's handle for the one case where the server cannot see the cursor - the creative
inventory keeps it to itself - and `DeckSweeps` inserts from the named slots into the deck the vault
holds under that handle, for a player who is actually in creative. `CreativeDecks` then restores a
hidden copy from the vault rather than from the stack it remembered when the deck left its slot, which
is the deck as it was before the card went in.

Two things this turned up on the way: the sweep's mixin was never registered in either loader's
`gathering.mixins.json` - lost in a merge - so sweeping a deck over cards did nothing at all; and the
creative screen overrides `slotClicked` without calling the one the ordinary container screen has, so a
hook there never fires. Both fixed, with the click hook on the creative screen itself.

**Also from the owner's playtest:** every wooden shop counter is a shopkeeper's job site, not only the
dark oak one; a settings file written by an older version is brought up to date where a value is still
the old default (`SettingsUpgrade`, tested) - which is why his shop still wanted emeralds and his
villages still hardly built a shop; and the shelf turns over every hour rather than every four, so a
session sees it move.


## 2026-09-17: defect hunt over everything since `2e3f97c1`

Five read-only reviewers were given a bounded slice each - tournaments, the card pipeline, decks and
the collection, client presentation, economy and config - the requirements and the diff, and no
narrative about the implementation. Between them they raised thirty-odd findings. What follows is what
was investigated and fixed; each fix's guard was run against the code without it first, except where
said otherwise.

**Cards were being duplicated in the creative inventory.** The fix earlier today has the server do the
insert it is told about; the client does the same click on its own copy; and `CreativeDecks` then
added every face-up card of that copy on top of the deck the server now held. Every card went in
twice, and repeating the gesture minted pairs at will. `CreativeDecks.notAlreadyIn` now takes what the
deck has gained since it left its slot off what the copy is offering, so a card the server already put
in is not put in again - and a card it never saw still is, which is what happens when the payload does
not arrive. Guard: `acardPutIntoaDeckInCreativeGoesInOnce`.

**The sweep named the wrong slots outside the creative inventory's own tab.** Its inventory tab puts a
wrapper in front of each of the player's menu slots; every other tab builds the bottom row fresh over
the inventory, so the same row is numbered 0-8 there and 36-44 there. Reading the container slot
straight named the crafting square and the armor, so a sweep on any category or search tab did nothing
at all. `InventorySlots.creativeSlot` does the arithmetic now, and both loaders' mixins use it
(`CreativeSlotsTest`, shown failing on the old arithmetic).

**Moving a card into the command zone ate a second copy.** `DeckBuild.moved` took the card out of its
pile and handed it to `led`, which took another copy out. Two copies in the deck, one made commander,
and the other was gone; a sideboard copy promoted ate the mainboard's. `DeckBuildCommanderMoveTest`.

**Every server older than the settings upgrade was still showing hidden information.** `modes.replays`
stopped defaulting to `public` before `SettingsUpgrade` was written, so it was not in the list of
defaults that move - and a file predating it still let any player watch any finished casual game back,
hand by hand. Now carried like the rest (`SettingsUpgradeReplaysTest`). Beside it: a settings file
that could not be **written** made the server drop every setting it had just read successfully and run
on defaults, which a read-only config mount does on every start; the rewrite is now beside-and-move,
and a failure keeps the values that were read.

**On Fabric only the dark oak counter was a job site.** The "every wooden counter" fix reached one
loader: Fabric registered the plain block rather than the states `GatheringVillagers.createCounterPoi`
builds, so a spruce shop was a shop no villager there would work at.

**The archive could strike a family off for a network failure.** An audit that could not be done comes
back as an empty remainder, which is the same shape as "nothing left" - so a throttled Scryfall took
families out of the archive one at a time, permanently for the run. `remainderOf` now says whether the
audit was whole, and only a whole one strikes a family off.

**A same-origin redirect that spelled out its port was refused** - `https://host:443/...` compared 443
against the request's -1 - which is exactly the MTGJSON move the following exists for
(`RedirectDefaultPortTest`).

**The camera could be left locked with no key to release it.** In press-to-inspect, the latch survived
the card going away: put the card down and the camera stayed locked; pick another up and it snapped
full-screen again; unbind the read key in between and there was no key left to press. The latch is now
released when there is no card in hand, which is the gesture a player would try.

**A catch-up swallowed "it's your turn".** Walking back to a table with more unread than
`LogCatchUp.STILL_NEWS` marked the log read and returned before the turn was noticed - and at a table
waiting on you no further board arrives, so it was never told at all. The lines are still marked read
without their sounds; the turn is noticed either way. Source-verified: neither test set reaches this
client-side path, and no guard was written for it.

**Tournaments.** A prize is now promised by name as well as by slot, so a slot whose contents changed
between the create screen and the tournament being made is passed over rather than emptied
(`aPrizeSlotHoldingSomethingElseIsPassedOver`). A desk a tournament moves away from lets go of it -
nothing ever called `runs(null)`, so the old desk went on claiming it, refused to host anything else,
and pulled the tournament back on one press
(`afreeDeskOffersHostingRatherThanMovingATournamentWithADesk`). A second use of a desk already running
one of your own tournaments moves nothing: it used to move your other tournament here and leave this
one with nowhere to sign up, which means registrations from anywhere in the world
(`asecondUseOfaBusyDeskLeavesBothTournamentsWhereTheyAre`). Taking somebody else's desk over on a
second use still works - the first attempt at this broke it, and the gate caught it. And a tally may
report no more drawn games than the match had room for (`ResultTallyDrawsTest`).

**`DeckVault` is forgotten with the player**, which it was not: up to 256 decks of a thousand cards
were kept for everybody who had ever logged in.

**Raised and not yet done**, in the reviewers' own order of severity: the create screen forgets a draft
before the server answers; no bound on how many tournaments one host may run, and `EventViews.create`
sits outside `withinBudget`; `EventDrafts` is keyed by position without dimension; `CardDataService`'s
`setPrintings` memo and the lookups it seeds are unbounded and never expire; `warm()` can throw
`RejectedExecutionException` onto the server thread; a replaced bulk index's file handle leaks when the
server stops within two minutes of a rebuild; `Archive.factsFor` counts a failed re-read as a whole
audit; a long sweep is silently truncated by the shared action budget and the sweeping player hears
nothing; `CreativeDecks.inInventory` copies a live deck, which the creative hotbar save can duplicate;
fitting a card's text costs about six hundred font wraps on a cache-miss frame; a neighbouring table's
label can be pulled in front of the board you are seated at; the master shopkeeper's tier is priced
above what `ShopPrice` will sell, so it is empty on a default server; setting `sealed_price_item` alone
produces a mixed-currency price; the archive pack ignores the loot source list and the player-kill
gate; and the shop's own chest hands out coins with no source gate at all.

## 2026-09-17: the README, and the next of the review's findings

**The README was rewritten.** It had drifted: it described the table as two blocks by two (it has
been three by three since the owner's playtest on the 15th), said the shop takes emeralds,
counted thirteen looks where twelve ship, named nine static checks where there are eighteen, called
`./gradlew verify` the gate, and did not mention tournaments, the Scorekeeper's Desk, display cases,
the collection block, the shop counter, chairs, the Mana Coin, loaner decks, replays, the tutorial,
card stories, cube draft or the eleven woods everything comes in. Every claim in the new one was
checked against the code. `docs/themes.md` (which still described four looks named Felt, Slate and
Walnut, and a `future` look that does not exist) and `tools/README.md` ("the sixteen static checks")
were corrected with it.

**The master shopkeeper had nothing to sell.** A trade is paid in two slots, so the most that can
change hands is 128 of a currency with no larger denomination - and the Mana Coin has none, while a
case is worth over two hundred boosters. The offer was simply dropped, so the reward of training a
shopkeeper all the way up was an empty counter. A counter now stocks only what its price can be paid
in, and a level with nothing it can sell falls back to the dearest level below it
(`thingsTooDearToHandOverAreNotStocked`, shown failing without the filter). **The owner may want to
decide the other half of this:** a case cannot be bought for coins at all on default prices, and the
honest fixes are a larger denomination or a bulk discount, both of which are his call.

**An archive pack could come off a boss nobody fought.** One in two boss kills is the most generous
roll in the mod, and the archive is the only path to a server's long tail - so a wither farm handed
out the one thing a player cannot buy, faster than anything else in the game. `ArchiveDrops` says
which sources need a player and `SealedLoot` passes that through (`abossNobodyFoughtDropsNoArchivePack`
in world, `abossNeedsAPlayer` in core). The archive's *source list* is unchanged and deliberate: which
tables it comes out of is a rule, not a setting.

**The sweeping player could not hear the sweep.** The insert sound is played by both sides - the call
excludes the player it is given, which on a client means them alone and on a server means everybody
else - so a click the server makes on its own plays for the whole room except the person who made it.
The payload now says whether the client made the click itself, and the server tells the sweeper
otherwise.

**A test had been writing into another test's plot.** `everyMaterialOfTableIsStillATable` put fifteen
tables in a row four apart: sixty blocks across a plot seventeen blocks wide. It passed for months and
started failing the day an unrelated test was added, because that changed which plot it landed in.
The test now puts one table down at a time and takes it up again. `tools/plotcheck.py` exists for
exactly this and could not see it, because it only reads coordinates written out as numbers - it now
refuses a placement it cannot read, which found one more (a venue of eight tables, in bounds but
unreadable, since written out).

**Corrected straight afterwards, by the owner.** The rewrite said an Archidekt link cannot be
fetched. It can, and always could: `DeckImporter.importText` sends a recognized link to
`ArchidektDeckSource`, which reads the deck from Archidekt's public API and gets the Scryfall id of
every card, so a link resolves to exact printings where a text export can only name cards. The
reviewer who checked the claim read `DecklistParser` alone - which is pure and cannot fetch anything,
so it tells the player to paste an export - and I took that as the whole answer without following the
import path myself. Moxfield really is link-unfetchable (403 to third parties), and the two were
wrongly lumped together. The lesson is the one already written down: a reviewer's finding is a lead
to verify, including when it contradicts something that was already there and correct.
