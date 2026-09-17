# Whole-codebase review, 2026-09-16

Seven reviewers over ~110,000 lines of main source: one on the week's diff, six on bounded areas
covering the whole mod. Each was given the project's rule that a comment describing a guarantee does
not establish it, and asked to find the enforcing code or report that it could not.

Status column: **fixed** / **open** / **rejected** (with the reason).

---

## Critical

| # | Where | What | Status |
|---|---|---|---|
| C1 | `core/.../game/GameFold.java:301` | Revealed-top window slides onto unrevealed cards when a card moves within its own library. Publishes the whole shuffled library, in order, to every opponent and spectator. Violates the visibility invariant. | **fixed** |
| C2 | `server/DeckVault.java` + `DeckEdits.made` | Vault keyed on the deck handle alone, and the handle is `networkSynchronized` to every client that can see the item. A creative player who walked past a deck could rewrite what the server believed was in it. | **fixed** |
| C3 | `server/AwayFromBoard.java:371-376` | `GIVEN_UP` records are never retired and persist across sessions and restarts. A stale record makes `boardBelongsToAnother` return false, so another player is seated on your board and `TableBroadcast.seatedAt` sends them your hand. Second visibility leak. | **fixed** |
| C4 | `server/AwayFromBoard.java:372-373` | The same set is wiped wholesale at 1024 entries, which ordinary play reaches. That strands live seats: nobody can take a board whose owner left, for the rest of the game, with no message. | **fixed** |
| C5 | `server/events/Events.java:798-808` | `startPlay` empties `state.tables` *before* refusing. A second press then skips the guard, starts the tournament with no tables, and writes the empty list to disk on the next save. Mine, from the 38th batch. | **fixed** |
| C6 | `server/events/Events.java:274` | `addTables` still uses the weak "free" test. The added table's live game is then ended by `clearTables`, returning bystanders' decks and unwinding their ante, unasked. | **fixed** |
| C7 | `server/events/Events.java:1150-1171`, `:984-991` | A restart with nobody online runs the away-from-board grace clock for the whole field, concedes every match, drops every entrant, and marks the event finished - rated, prizes paid. Terminal. | **fixed** |
| C8 | `server/DraftActions.java:128-167` | Pools are handed out before the pod is struck off. A crash between them re-enters `handOutThePools` on restart and every drafter gets a second pool. | **fixed** |
| C9 | `core/.../deck/ArchidektDeckSource.java:101` | `quantity` is taken unbounded from the remote response and materialized before any limit applies. One pasted link with `"quantity": 2000000000` OOMs the server. | **fixed** |
| C10 | `client/ClientTableState.java:83-89` | Board eviction picks an arbitrary entry, not the oldest, and does not exclude the table you are seated at. Evicting yours closes your own game screen mid-turn. | **fixed** |

## Important

**Property (cards must not duplicate or vanish)**
- `server/PocketCards.java:48-68`, `CollectionView.java:542` - bulk card-into-deck paths destroy card provenance; the single-card path preserves it. Winning a card in an ante and then deck-building deletes its story.
- `server/DeckVault.java:68-88` - `isRedacted()` is `anyMatch` but `real()` assumes `allMatch`, so a deck of 100 real + 1 hidden entry is treated as a wire copy with 100 additions and comes back with 200 cards.
- `block/TableBlockEntity.java:1431-1448` - `writeSession` returns early on a key or encryption failure, dropping the live game *and* the sealed copy it was written to protect.
- `server/SessionKeyring.java:45` - `forget()` has no callers and the `tried` latch is set before the attempt, so one transient read failure means no game on the server opens or saves again.
- `block/TableBlockEntity.java:880-901` - `endSession` clears held decks with none of the loud backstop its javadoc promises (the pot gets one).

**Tournaments**
- **[fixed]** `Events.java:568-571` vs `Tournament.beginPreparing` vs `HostActions.playersIfBegunNow` - "who will actually play" defined three times, two ways.
- **[fixed]** `Events.java:594`, `:597-599` - `begin` discards a `TablesApart.set` refusal the sibling call site checks, and warns it cannot seat everybody while proceeding anyway, into a phase with no way back.
- **[fixed]** `Tournament.MOST_PLAYERS` vs `PodSettings.mostPlayers()` - limited events accept 256 sign-ups and enforce 8 only at Begin.
- **[fixed]** `Events.java:1188-1192` - `turnPassed` mutates and returns without saving.

**Client**
- **[fixed]** `ClientTableState.java:37-46` - `POTS`, `TERMS`, `AWAY` unbounded and not cleaned on eviction; a server can drive the growth.
- **[fixed]** `ClientCardImages.java:148-166` - full `URI` parse + `toLowerCase` per card per frame before the cache is consulted.
- **[fixed]** `ClientSetSymbols.java:106-111` - same shape: five allocations to build a cache key, per call, per frame.
- **[fixed]** `TableScreen.java:4571-4585, 6258` - context-menu and palette actions act on the board captured when the menu opened, not the live one. Sends stale angles and wrong tap states under ordinary multiplayer timing.
- **[fixed]** `ClientCardFlights.java:267-276` - `isFlying` takes a global monitor and copies a list once per card per frame.
- **[fixed]** `ClientCardFlights.java:122-147` - with reduced motion on, `OWN_DOING` is never pruned.
- **[fixed]** `PackOpeningScreen.java:534-552` - `drawSymbol` is dead; the wrapper's set symbol documented in the class javadoc is no longer drawn.
- `TableMiniatureRenderer.java:509-528` - `Component.translatable` and two enum array clones per verb per seat per table per frame.
- `FoilSheen.java:177-178` - two `float[4]` per vertex; ~7,500 arrays per frame on a read foil, plus a `new Random` per card per frame.
- `EventScreen.java:657` - translation key built by concatenation from a wire string, per frame; unknown phase renders the raw key as UI.

**Data and economy**
- **[fixed]** `core/.../booster/BoosterOpener.java:77` - slot count from external data sizes an array with no bound.
- **[fixed]** `core/.../scryfall/ScryfallClient.java:111-124` - `everyPrintingIn` truncates at 8 pages and says nothing, so the Archive Pack's completeness guarantee has a silent hole.
- **[fixed]** `core/.../scryfall/DiskCardMetadataStore.java:115-125` - non-atomic cache write, while the collation cache five files away is atomic.
- `core/.../deck/ArchidektDeckSource.java:56` - deck-site fetch bypasses `HttpFetcher`, so no limiter, no retry, no `Retry-After`.
- `InMemoryCardMetadataStore.java:20-23` - unbounded heap growth, contradicting `DiskCardMetadataStore`'s "costs disk and not heap".

**Networking**
- `network/CardSummary.java:107-131` - `colorIdentity` trimmed on decode but not on encode; a printing with >8 entries writes a packet the far side cannot read, disconnecting every recipient.
- `network/GatheringProtocol.java:207` - `MarkWantedPayload` writes a file per packet with no budget.
- `network/GatheringProtocol.java:233` - `CollectionTakePayload` is the one collection payload with no throttle.
- **[fixed]** `tools/tablecheck.py:32` - matches on the component *name* `table`; `DraftPickPayload.pod` and `EventActionPayload.at` carry table positions and are silently uncovered.

**Game core**
- **[fixed]** `GameSession.java:240-246` - `undo` treats "nobody occupies a seat" as unanimous consent, so an unconsented rewind across a reveal succeeds in that window.
- **[fixed]** `GameEvent.java:625-627` - `TokenCopyCreated` does not report as an information boundary when copying from a hand or library, so it can be silently and unilaterally undone after the table saw it.
- **[fixed]** `persistence/SessionCodec.java:205` - a retired secret verb makes a whole saved session refuse to open.

**Collections**
- **[fixed]** `server/CollectionKeys.java:143-158` - handing a collection over is one click, name-typed, irreversible, unconfirmed.

## Minor

Sixty-odd, mostly: comments that promise more than the code delivers, per-frame allocations,
inconsistent clocks (`System.currentTimeMillis` where the area uses tick counts), dead lang keys,
`display_case_full` saying "already has a card in it" when a case holds four, and
`tutorial.gathering.practice_note` being verbatim the string `DIALECT.md` uses as its bad example.

## Rejected / corrected reviewer reasoning

- The ordering worry about `DeckMadePayload` was unfounded: the system self-heals across arbitrary
  reordering, because `inventoryTick` refuses to strip a redacted deck to nothing and the vault
  refuses to store a redacted copy. The javadoc's ordering claim should be replaced with that.
- `instabuild` is not forgeable: `handlePlayerAbilities` only writes `flying`, clamped by `mayfly`.
- The display case cannot become unopenable: the lock toggle sits after the owner check, and
  `getDrops` builds a fresh stack so neither owner nor lock rides into the item.
