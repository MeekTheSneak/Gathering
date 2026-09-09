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

## What is still open, stated plainly

- **G-39 — booster collation fidelity.** Colour balancing and complete companion-set handling
  are not implemented. The contract is written down in the design brief and a made-up pack
  says so in the chat line that hands it over. This stays open until it is built and exercised
  against complete real product data.
- **G-24 / G-25 — the remaining blocking work.** `DeckCheck` still reads the store on the game
  thread once per deck check, and the first load of a long replay and its re-folds are still
  synchronous. Both are bounded, both are once-per-action rather than per-frame, and neither
  has been profiled. They are smaller than they were and they are not finished.
- **Anything only a graphical client can answer.** The scripted client run (`tools/shots.sh`)
  drives real screens and asserts as it goes, but it is not proof about frame times, and no
  third-party modpack has been tested against this.

## The first pass, for the record

All forty-one original findings and their fixes are listed in the review's own status table;
the ones the second pass reclassified are the twelve above plus G-39. The rest — the public
deck redaction, server-authored random outcomes, combined sideboard validation, the ante
bound, the texture eviction rewrite, the complete `verify` task, hidden-card note targeting,
library looks, phantom seats, Oathbreaker identity, the shop's product catalog, the trade
logout hook, the collection tick throttles, session recovery, table closing, the pack-history
rarity, dependency ranges and the unclosed image streams — the second pass confirmed as
addressed, and they are unchanged since.
