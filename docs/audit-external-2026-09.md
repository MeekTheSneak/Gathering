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
| R-06 | P2 | Fixed | Normal deck edits still erase the new card-history field | Every functional copy on `DeckComponent` carries the histories. The test that promised a sleeving round trip now performs one: insert, insert again, rename, recolour, resleeve, move between sections, cross the wire, and take the card back out through the real TAKE handler. |
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

- **G-39 — booster collation fidelity. Done.** Colours are balanced. A slot of five or more
  off a sheet the published data calls balanced takes one card of each of white, blue, black,
  red and green first, each drawn from that colour's own share of the sheet at that share's own
  weights, and the rest of the slot drawn from the whole sheet as before; the slot is then
  shuffled so a pack does not arrive in WUBRG order. That is the shape of the physical cut
  rather than a rejection loop, so it costs one draw per card and a seed still opens exactly
  one pack. Companion sets were already resolved before an arrangement is used, and their
  colours are joined the same way. Verified against the real Dominaria United file fetched from
  MTGJSON: both of its balanced sheets balance, and forty packs of each arrangement hold all
  five colours. Disabling the rule fails that test. What is still approximate is stated in the
  design brief: a sheet the data calls balanced that cannot be balanced here — an empty colour
  column, or no colours read for its cards — is drawn by weight and says so in the notes.
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

## The first pass, for the record

All forty-one original findings and their fixes are listed in the review's own status table;
the ones the second pass reclassified are the twelve above plus G-39. The rest — the public
deck redaction, server-authored random outcomes, combined sideboard validation, the ante
bound, the texture eviction rewrite, the complete `verify` task, hidden-card note targeting,
library looks, phantom seats, Oathbreaker identity, the shop's product catalog, the trade
logout hook, the collection tick throttles, session recovery, table closing, the pack-history
rarity, dependency ranges and the unclosed image streams — the second pass confirmed as
addressed, and they are unchanged since.
