# External audit, September 2026

An outside audit of commit `57a3705` raised forty-one findings across multiplayer integrity, the collection economy, lifecycle, performance and MTG rules fidelity. Nine were reproduced against this code by its own probes.

Forty are fixed, each with a test that fails without the fix. One is partly addressed and says so.

| ID | Priority | State | Finding | What was done |
|---|---|---|---|---|
| G-01 | P1 | Fixed | Held deck components disclose the full deck to other clients | Deck and drafted-pool components sync a public form only; the owner gets the real list addressed to them. |
| G-02 | P1 | Fixed | Clients can submit completed random outcomes | Dice, coin and planar results are refused from the wire; the client/server split is decided in core and checked against the sealed event hierarchy. |
| G-03 | P1 | Fixed | Trade agreement is not bound to the offer the player saw | An agreement names the revision it was shown; a stale one is refused and both sides are re-shown the terms. |
| G-04 | P1 | Fixed | Disconnecting while a pack opens can permanently consume the pack | A pack opened for somebody who has gone is written down as owed and handed over when they next join. |
| G-05 | P1 | Fixed | Allowed ante settings exceed the ante packet limit | The ante packet is sized from the largest pot the settings allow, derived from one maximum. |
| G-06 | P1 | Fixed | The advertised verify task omits core tests and packaging checks | verify names every module and checks its own task graph. |
| G-07 | P1 | Fixed | Hidden-card notes can become identity-tracking markers | No event may name a card in another seat’s hand or library; read off each event’s own components. |
| G-08 | P2 | Fixed | A temporary library look follows depth rather than the cards looked at | A depth-limited look closes when its library changes; a whole-library search does not need to. |
| G-09 | P2 | Fixed | Some events accept nonexistent seats or commander instances | Every seat an event names must exist; commander tax and damage must name a real commander. |
| G-10 | P2 | Fixed | Sideboards escape legality and combined copy-limit checks | Bans and copy limits count the sideboard as part of the registered deck. |
| G-11 | P2 | Fixed | Commander pairing does not model the actual pairing abilities | Each printed pairing mechanic is modelled separately. |
| G-12 | P2 | Fixed | The signature spell incorrectly expands Oathbreaker color identity | The oathbreaker sets the identity; the signature spell is checked against it. |
| G-13 | P2 | Fixed | A purchased sealed box depends on the current shop rotation to open | A sealed box opens from its own set, read on demand, independently of the shop shelf. |
| G-14 | P2 | Fixed | Unresolved card metadata can turn a pack into a short delivery | A card the pipeline could not name is owed rather than dropped from the pack. |
| G-15 | P2 | Fixed | Trade logout cleanup exists but is not wired into either loader | Both loaders call one arrival and one departure path, which includes the trade cleanup. |
| G-16 | P2 | Fixed | World shutdown clears state before asynchronous work has stopped | The restock latch is reset with the world, and per-player server state is cleared on stop. |
| G-17 | P2 | Fixed | Card art push records delivery before lookup succeeds | Being-asked-about is separate from sent, so a failed lookup is retried. |
| G-18 | P2 | Fixed | Collection progress throttling carries old-world tick counts forward | The set throttle ignores a clock that has gone backwards and is cleared per world and per player. |
| G-19 | P2 | Fixed | Forced table removal does not complete the normal session lifecycle | A forced removal ends the game the way any ending does before returning the decks. |
| G-20 | P2 | Fixed | An unrestorable session can leave a table unusable without a recovery path | An unreadable game says so, and crouching sets it aside to a file and returns the decks and pot. |
| G-21 | P2 | Fixed | Table closing is neither addressed to all viewers nor scoped to one table | Closing names its table and reaches everybody watching. |
| G-22 | P2 | Fixed | Multi-table cards extend beyond the clickable world surface | The world hit test and the camera span the whole cluster. |
| G-23 | P2 | Fixed | Metadata requests can fill a shared unbounded work queue | Metadata requests answer from memory and queue against a per-player budget. |
| G-24 | P2 | Fixed | Cache lookups perform disk reads on the game thread | The collection page reads memory only on the game thread. |
| G-25 | P2 | Fixed | Replay controls repeatedly read whole files on the server thread | Replay headers are read from the front of the file and cached; frames are rate-limited per watcher. |
| G-26 | P2 | Fixed | The texture cache enforces an entry count rather than its byte budget | Textures are evicted by bytes, with a separate allowance for the crisp tier. |
| G-27 | P2 | Fixed | Image decoding and pixel processing run on the render thread | Decoding and pixel work happen off the render thread. |
| G-28 | P2 | Fixed | Search throttling can discard the final query and display stale results | Searching settles before it sends, and each answer names the search it answers. |
| G-29 | P2 | Fixed | Some collection import failures never complete the screen’s request | Every import failure finishes the screen waiting for it. |
| G-30 | P2 | Fixed | Finishing a deck closes the builder before the server confirms success | The builder waits for the server’s answer and keeps the selection when a build is refused. |
| G-31 | P2 | Fixed | The sideboarding screen does not request its own missing metadata | The sideboard screen asks for the names of its own cards. |
| G-32 | P2 | Fixed | Dismissing a deck context menu can also act on the card beneath it | Dismissing a deck menu consumes the click. |
| G-33 | P2 | Fixed | Deck scrolling does not reserve the same space as rendering | One rectangle decides where the deck list is drawn, hit and scrolled. |
| G-34 | P3 | Fixed | Cursor inspection always selects the front art of a double-faced card | The panel beside the cursor shows the side the card is showing. |
| G-35 | P2 | Fixed | Replay responses can outlive their screen and interrupt playback intent | A pending rewind is not the end of a replay, and leaving the list cancels the watch. |
| G-36 | P2 | Fixed | Putting a storied card into a deck drops its provenance | A deck keeps the histories of the cards sleeved into it and hands one back when a card is taken out. |
| G-37 | P3 | Fixed | Pack history is attached to the lowest-rarity card | The pack story goes to the highest-rarity card. |
| G-38 | P2 | Fixed | Cached legality can remain indefinitely stale | The deck check says when it is reading card data more than a fortnight old, and asks for it again. |
| G-39 | P2 | Partly | Published booster collation is only approximated in important cases | Colour balancing and companion-set resolution are not implemented. What the mod does claim is now written down in the design brief, and an approximated pack is disclosed every time one is opened. |
| G-40 | P2 | Fixed | Dependency ranges advertise more Minecraft/NeoForge versions than were built | The mod advertises exactly the versions it was built and verified against. |
| G-41 | P3 | Fixed | Non-success image HTTP responses leave their body stream unclosed | Every image response body is closed, whatever its status. |

## The one that is not finished

G-39 asks for booster collation that reproduces the published product. Colour balancing is not implemented and a sheet whose cards are printed in a companion set loses those cards when that file has not been read. Both are recorded by the reader, and a pack cut without a published arrangement tells the player so every time it is opened. What the mod claims a pack to be is now stated in the design brief under "Booster fidelity, stated"; closing the finding means resolving companion sets before using an official arrangement and implementing colour balancing.

