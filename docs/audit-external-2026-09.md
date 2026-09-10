# External audit, September 2026, and the review of the fixes

Two passes by an outside auditor, and this is the honest record of both.

The **first pass**, against commit `57a3705`, raised forty-one findings (G-01…G-41) across
multiplayer integrity, the collection economy, lifecycle, performance and MTG rules fidelity.
Nine were reproduced against this code by the auditor's own probes.

The **second pass**, against commit `afab38c`, reviewed those fixes. It classified them as
twenty-three addressed, fifteen partial, one regression, one not fixed, and one openly
unfinished — against this repository's own claim at the time that "forty are fixed, each with
a test that fails without the fix". **That claim was wrong**, and the way it was wrong is worth
writing down rather than quietly correcting: several of the tests exercised the helper a fix
added rather than the path a player takes through it. `keeping`/`storyOf`/`withoutStoryOf` all
worked; the deck mutators around them threw the histories away, and no test went through one.
The pairing helper accepted a Background; the validator that decides whether a deck is legal
still rejected it, and every test asked the helper. A fix is finished when the thing a player
does works, not when the piece it was built out of does.

The second pass raised twelve residual findings (R-01…R-12), four of them with acceptance
tests written to fail. All twelve are now addressed, along with the review's separate
follow-up list. Where a fix is bounded rather than complete, that is stated below rather than
rounded up.

## The residual findings

| ID | Priority | State | Finding | What was done |
|---|---|---|---|---|
| R-01 | P1 | Fixed | Pack recovery still has an unrecorded shutdown window | A receipt is written to the save *before* the pack is consumed, and settled on every path out of the opening — replaced by the cards if the player has gone, removed once they are holding either the cards or the pack. A server stopped mid-opening leaves the receipt, and the next join hands back exactly one pack. If the receipt cannot be written, the pack is not taken. |
| R-02 | P1 | Fixed | Trade revisions are reused across different trade sessions | Every table carries an unguessable identity of its own, sent in the view and named by an agreement alongside the revision. An agreement left over from a closed trade names a table that has gone and is refused. |
| R-03 | P1 | Fixed | Owed rewards are stored per installation, not per world | Owed rewards, replays, want lists and set-aside broken games now live under the running save. Only downloaded card and collation metadata stays global, which is the point of caching it. `tools/savecheck.py` fails the build if anything else reaches for the game directory. |
| R-04 | P2 | Fixed | The recovery ledger can discard or replay owed rewards | The ceiling refuses new entries and shouts rather than deleting old ones, the list is written through a temporary file and an atomic move, and every write says whether it worked — a caller told "no" must not treat the property as safeguarded. |
| R-05 | P2 | Fixed | Opening a replay immediately cancels its own playback | The list screen asked `Minecraft.screen` whether a replay was opening, and `setScreen` calls `removed()` before assigning the new screen — so the guard never fired and every normal opening cancelled itself. The replay controller says so itself now. |
| R-06 | P2 | Fixed | Normal deck edits still erase the new card-history field | Every functional copy on `DeckComponent` carries the histories. The test that promised a sleeving round trip now performs one: insert, insert again, rename, recolor, resleeve, move between sections, cross the wire, and take the card back out through the real TAKE handler. |
| R-07 | P2 | Fixed | Forced anchor removal still bypasses session completion | `TableSessions.end` gained an overload taking the block entity the removal hook already holds, instead of rediscovering a table the world has just replaced. |
| R-08 | P2 | Fixed | Background pairing passes its helper but fails full deck validation | Eligibility is asked of the whole command zone, so a card admitted by its partner's mechanic is eligible. A refused pair is told which mechanic it missed rather than always "both have Partner". |
| R-09 | P2 | Fixed | The legality refresh request only returns the same stale cache | `CachingCardSource.refresh` goes past the cache and stores what comes back; `CardDataService.refresh` coalesces and bounds it. The staleness scan reads timestamps from memory rather than stat-ing the disk on the game thread. |
| R-10 | P2 | Fixed | The camera now mixes cluster-centered placement with single-table offsets | Focus, pan reach and framing all come from `TableFraming`, which is pure and tested against clusters of one to four tables. |
| R-11 | P2 | Fixed | Shutdown still clears global state before stopping its writers | `ServerRun` stamps a generation; both loaders stop the executors before clearing state; `tools/runcheck.py` fails the build if any completion in the server or service packages is not bound to the world that asked. |
| R-12 | P2 | Fixed | Metadata failures and queue limits are only partly handled | Only printings that actually came back are recorded as sent; the rest are written off for five minutes and asked again. Lookup accounting is keyed by connection and bounded across everybody as well as per player. The sideboard screen re-asks for names that never arrived. |

## The review's follow-up list

| Item | What was done |
|---|---|
| G-28: the final search can still disappear | A search inside the throttle is kept, not dropped, and runs when the throttle lifts; a newer one replaces it. |
| G-30: acknowledgement is not a complete operation protocol | `finish()` refuses a second press, the button greys out while one is outstanding, and the request and its result carry the press they belong to. |
| Owner deck cache identity | The owner's deck push is numbered, and the client drops one that arrives after a newer one, instead of matching on name and card count. |
| Protocol and release hygiene | The NeoForge protocol is `2` and the mod is `0.2.0`. Several payload shapes changed incompatibly; a mixed pair now refuses to connect instead of failing inside a decoder. |
| Documentation claims | This file. |

## The three that were open, and are not any more

- **G-39 — booster collation fidelity. Done.** Colors are balanced. A slot of five or more
  off a sheet the published data calls balanced takes one card of each of white, blue, black,
  red and green first, each drawn from that color's own share of the sheet at that share's own
  weights, and the rest of the slot drawn from the whole sheet as before; the slot is then
  shuffled so a pack does not arrive in WUBRG order. That is the shape of the physical cut
  rather than a rejection loop, so it costs one draw per card and a seed still opens exactly
  one pack. Companion sets were already resolved before an arrangement is used, and their
  colors are joined the same way. Verified against the real Dominaria United file fetched from
  MTGJSON: both of its balanced sheets balance, and forty packs of each arrangement hold all
  five colors. Disabling the rule fails that test. What is still approximate is stated in the
  design brief: a sheet the data calls balanced that cannot be balanced here — an empty color
  column, or no colors read for its cards — is drawn by weight and says so in the notes.
- **G-24 — the deck check off the game thread. Done.** It answers from the in-memory index or
  says it cannot yet; the caller shows "Checking your deck…", the cache files are read on the
  card thread, and the deck goes down when the answer arrives. Never the network: waiting on
  Scryfall would hold a player at the table for somebody else's timeout, so an unwarmed
  printing is read from disk and a printing that is not on the disk stays unknown, which the
  check reads as no opinion. The retry happens once, so a card nothing has ever heard of
  cannot loop.
- **G-25 — the replay's first load and its re-folds. Done, and measured.** A step forward is
  one record applied to a board that is already there, and stays on the server thread. Opening
  a replay reads a whole file, and a scrub backwards folds the game again from the front; both
  moved to a replay thread, with one job per watcher so two folds cannot race on one held
  game. The numbers, printed by the game test on a 605-step game: **open 3–10 ms, one step
  forward 1.5–2 ms, rewind into the middle 25–27 ms.** Twenty-five milliseconds is half a tick,
  per frame, per watcher, on a scrubber somebody is dragging — which is what the split is for.
  The test asserts the *rule* and prints the numbers: a rewind has to stay multiples dearer
  than a step forward, or the split is ceremony and should go. It does not assert a microsecond
  budget — two drafts did, and the same code printed 1.4 ms, 2.3 ms and 11.7 ms across three
  runs of a shared test server. A wall-clock budget inside a run of three hundred tests
  measures the machine and its neighbours.

## And one the profiling found on the way past

Rewriting the collection's own scale test the same way — a ratio against a small box rather
than an absolute budget — turned up something the absolute version had been passing over for
its whole life. `CardTally` is a value: every `plus` and `take` copies the whole map. A
collection block held one, so **a hundred puts and takes cost 2 ms on a box of eight and 251 ms
on a box of ten thousand** — and sleeving a hundred-card deck out of a large box is a hundred
of those, in one tick. The box now keeps its counts in a plain map that a put touches one entry
of, and builds the value form when somebody asks for it. The same measurement afterwards: **686
µs at eight rows, 436 µs at ten thousand.** The old test passed throughout, because 251 ms is
under a 1.5-second budget; what it was really measuring was whether the machine was busy, and
it had started failing whenever anything else in the run allocated.

## What is still open, stated plainly

- **Anything only a graphical client can answer beyond what the scripted run covers.** The
  scripted client run (`tools/shots.sh`) drives real screens and asserts as it goes, and it
  found things nothing headless could — but it is not a frame-time measurement, and no
  third-party modpack has been tested against this. Both need somebody's own machine.

## What the scripted client found that nothing else could

Neither audit pass launched a graphical client, and neither had this repository since the
fixes went in. Running `tools/shots.sh` against the closed-out code found two things the whole
gate was green through:

- **Watching a game back did not work at all.** The throttle added for G-25 - two ticks between
  frames for one watcher, to bound a dragged scrubber - dropped what it refused instead of
  deferring it. Picking a game within two ticks of any other frame request meant the opening
  frame was thrown away, the client sat on the list screen, and after five seconds it gave up
  silently. That is a fix for one finding breaking a feature, shipped, with three hundred and
  twenty-three in-world tests and eleven static checks all green. The throttle now keeps the
  newest request and answers it when the gap is up, and the client asks once more for a frame
  that never came.
- **The scene was not reproducing its own input.** Talking at the table opens a line on the
  chat key and swallows the character that key echoes, because a window sends a key event and
  then a character event for one press. The scripted run skipped the echo, so its first letter
  was eaten and it asserted the table had not heard a line the table was never given.

Both are fixed and the run is clean: no failures, a hundred and forty-four pictures. The
lesson is the one the review already made about helpers and paths, one level further out - a
gate that only runs headless proves what happens without a window, and a table game is a thing
somebody looks at.

## The second review, and the eight it raised

A second external pass reviewed the fix commits and raised eight findings, V-01 to V-08. Three
of them were regressions the fixes themselves introduced, and one was a correction to a static
check this repository wrote and had been trusting. All eight are closed.

The single most useful thing in the bundle was not a finding at all. It was four lines of
mapped 1.21.1 source:

```
BlockableEventLoop.scheduleExecutables()  ->  !isSameThread()
MinecraftServer.scheduleExecutables()     ->  super.scheduleExecutables() && !isStopped()
BlockableEventLoop.execute(Runnable)      ->  scheduleExecutables() ? submit(r) : r.run()
```

`server.execute` is therefore **neither a next-tick scheduler nor a shutdown fence**. Called on
the server thread it runs the task inline, immediately. Called after the server has stopped it
runs the task inline on the calling thread. This repository had built two things on the
opposite belief, and had written a static check that asserted it:

- **V-03, a regression.** The G-28 search throttle and the G-25 replay throttle both "waited for
  the next tick" by re-queueing through `server.execute` and comparing tick numbers. On the
  server thread that is a plain recursive call, and the tick cannot advance inside it. Two
  searches in one tick recursed to `StackOverflowError`. Both throttles now go through
  `ServerTicks`, a real tick hook that holds a deadline per key and drains it from the server's
  own tick, removing everything due before running any of it so a task that re-queues lands on
  a later tick.
- **V-06.** `tools/runcheck.py` accepted `player.server.execute(...)` as proof that a completion
  could not run after its server stopped, and said so in a comment. That was false. The check
  now rejects a bare `server.execute` outright and accepts only the generation-checked fences
  in `ServerRun` — or a written `// runcheck:` exemption saying why. `ServerRun.onServerThread`
  reads the server and the run generation **at submission time**, which is the whole point: a
  completion built inside the callback reads whichever server is running when it lands.

The other six:

- **V-01 (P1), a regression.** The delayed deck check treated "this stack is not in the player's
  inventory" as "this must be a table-issued loaner", so a deck moved into a chest while the
  check was running was taken back out and put on the table. Placement now carries an explicit
  origin — out of a hand, or made by the table — and a hand-held deck must still be the same
  stack in the same hand, with the session and the seat revalidated at completion.
- **V-04.** Disconnecting released a player's share of the global metadata budget while their
  lookups were still queued, so six reconnects admitted 768 lookups against a cap of 512. The
  global count now falls only when a job actually completes; forgetting a connection clears
  only that connection's personal entry.
- **V-05, a regression.** The replay worker read `OPEN`, an access-ordered map documented as
  server-thread-only, from the worker thread — where a `get` mutates the link order. The replay
  is now taken off the map on the server thread and handed to the worker, which returns an
  immutable result. Each job carries a number, and a completion installs nothing unless that
  exact job still owns the slot.
- **V-07.** The client kept one decklist per *hand* and decided whether it was the right one by
  comparing the name and card count against the item — which two sixty-card decks both called
  "Deck" match exactly. A deck item now carries an opaque handle, minted once, and both the
  push and the cache are keyed by it. Appearance is not identity.
- **V-08.** A screen waiting for an answer accepted an untagged one. Every import request now
  names itself and every answer carries that name back, on both the builder and the import
  screen; an absent name is somebody else's answer, not a wildcard. Three collection-build
  refusals that had only ever written to chat now answer the waiting screen too, which was a
  dead end in its own right.

## V-02, where this repository and the review disagree

V-02 is closed, but not the way the review's test asserted, and the reasoning is worth writing
down rather than burying.

Half of it was real and is fixed: `PackOpening` handed the cards over and settled the receipt
afterwards. A settle that failed left a receipt on disk promising a pack for cards the player
was already holding, and the next join made good on it. Settling now comes first on every path,
including the archive, and nothing is handed over if it fails.

The other half is a genuine difference of opinion. `Owed.deliver` strikes a debt off the ledger
before paying it, so a rewrite that fails hands over nothing and the whole debt waits for the
next join. The review's test blocks the ledger's write path and asserts that exactly one pack
is delivered. That is not reachable. An acknowledgment that cannot be written down cannot be
made durable by any ordering, so under an unwritable ledger there are exactly two available
behaviors: pay now and pay again after a restart, or hold the debt and pay it when the write
works. The review's own body says "simply deleting first exchanges duplication for loss" — but
it is not loss. The ledger is untouched; the debt is intact and paid in full on the next join
that can write.

Between owing somebody a booster for another minute and printing one, the minute is the cheap
one. A lost card is a complaint somebody can answer. A printed one is an economy nobody can
trust.

So the acceptance test in `ReviewRoundTwoGameTest` asserts the invariant the review was
actually reaching for, and asserts it harder than the original did: delivery is called twice
against a blocked ledger and must hand over nothing, the block is then lifted and delivery
called twice more, and exactly one pack must have arrived in total. Never twice, and never
lost.

## The audit's own tests, kept

`ReviewRegressionGameTest` and `ReviewRoundTwoGameTest` are in the repository's game tests
rather than in a bundle beside it, so a future change that reopens one of these findings fails
the gate. Two adaptations were needed and both are written down where they are made: the V-01
test looks its target method up by name rather than by signature, because the fix added the two
arguments that say where the deck came from; and the V-02 test asserts the invariant above.
Nothing else was changed, and no assertion was inverted.

## The first pass, for the record

All forty-one original findings and their fixes are listed in the review's own status table;
the ones the second pass reclassified are the twelve above plus G-39. The rest — the public
deck redaction, server-authored random outcomes, combined sideboard validation, the ante
bound, the texture eviction rewrite, the complete `verify` task, hidden-card note targeting,
library looks, phantom seats, Oathbreaker identity, the shop's product catalog, the trade
logout hook, the collection tick throttles, session recovery, table closing, the pack-history
rarity, dependency ranges and the unclosed image streams — the second pass confirmed as
addressed, and they are unchanged since.
