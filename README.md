# Gathering

A Minecraft mod for **1.21.1** (NeoForge and Fabric) that turns a multiblock table into a
full tabletop card game surface: TTS-style manual mechanics with real hidden information,
plus an optional server-configurable collection economy.

> **Unofficial Fan Content.** Not approved or endorsed by Wizards of the Coast. Portions of
> the materials used are property of Wizards of the Coast. ©Wizards of the Coast LLC.
>
> Card data and images come from [Scryfall](https://scryfall.com). The mod ships no card
> images and redistributes no card data; every client fetches and caches its own.

The full design is in [`docs/design-brief.md`](docs/design-brief.md). Project conventions,
version pins, and the verification gate are in [`DIALECT.md`](DIALECT.md). Read both before
writing code.

## What this is

Two fantasies, one card system:

- **Play.** Four friends sit at a table inside a shared world and play Commander with their
  real decklists. The mod is the table, the cards, and the hands. The rules live in your
  heads — there is **no in-game rules enforcement, ever**.
- **Collect.** On a configured server, cards are also things you find, open, buy, and trade,
  with gym nights at arena tables.

And a short list of things it is deliberately **not**: a rules engine, an AI opponent, a
real-money anything, a card-art redistribution vehicle, or a physics sandbox.

## Layout

| Module | What lives there | Rule |
|---|---|---|
| `core` | Pure logic — card identity, decklist parsing, the Scryfall client and cache, deck import | no `net.minecraft`, no loader |
| `common` | Minecraft-facing, loader-agnostic — items, components, blocks, payloads | no loader imports |
| `neoforge` | NeoForge entry point. Primary development target | full gate |
| `fabric` | Fabric entry point. Port target | verified per phase |

Both rules are enforced by the build, not by convention: `core` has no Minecraft on its
classpath at all, and `common` is compiled against vanilla Minecraft only, so a loader class
is a compile error. `:core:checkCorePure` and `:common:checkNoLoaderImports` back that up.

The point of the split is to make the layer that can be checked in milliseconds as large as
possible.

## Building

**Gradle must run on Java 21**, not merely compile with it: Minecraft 1.21.1 requires it and
Loom sets Minecraft up inside the Gradle daemon, so a Java 17 daemon fails the build for both
loaders. Set `JAVA_HOME` to a JDK 21 and run `./gradlew --stop` to drop any stale daemon; the
build fails early with instructions if you forget. Everything else is pinned in
`gradle.properties`.

```bash
./gradlew verify               # the gate: build + runData + runGameTestServer
./gradlew :core:test           # the fast loop - pure core only, seconds
./gradlew :neoforge:runClient  # play it
```

`verify` is the bar. Every stage of it has been confirmed capable of failing, because a gate
that cannot fail manufactures confidence rather than providing it.

Four more checks sit beside it, and each exists because something got through the others:

```bash
python3 tools/langcheck.py     # every translation key written out exists, and none is stale
tools/smoke.sh                 # boot all four targets: both loaders, client and server
tools/shots.sh                 # drive a real client through a scripted game, photograph it
tools/preview                  # render the pure layout arithmetic straight to PNG
```

`langcheck.py` reads the source and `sounds.json` rather than a list somebody maintains.
`smoke.sh` exists because a loader can serve every class without its assets: it compiles,
builds, passes every test, boots, registers everything, logs happily, and then draws missing
textures and raw translation keys. Fabric shipped exactly that until somebody read the
resource pack list on startup - so a loader is not working until it has been booted.

`shots.sh` drives a real client through a whole game - sit down, deal, play a card, open a
graveyard, scry, surveil, resize the window, stand up and watch - asserting at each step and
leaving a numbered set of pictures behind. A step that stops working fails the run rather
than quietly producing a duller picture. It is the only check that can see whether a thing
looks like anything, and most of the interface faults in this repository were found by
looking at its output rather than by reading code.

## Status

**Phase 0 - the pipeline.** Done.

- [x] Multiloader scaffold with enforced layer fences
- [x] Card identity (`{scryfall_id, foil, custom_id?}`) and card metadata
- [x] Decklist parser - Moxfield, Archidekt, Arena, MTGO, deckstats, plain
- [x] Scryfall client: batched collection resolution, rate limiting, retry, disk cache
- [x] Deck import from pasted text, or from an Archidekt link. Moxfield is recognised and
      deliberately never fetched - it refuses third-party readers, so the mod says so and
      tells you where its export button is rather than hammering it
- [x] Card and deck items carrying the data component, on both loaders
- [x] Verification gate: JUnit + jqwik, data generation, headless game tests
- [x] Networking: import, metadata, table actions, all round-trip tested
- [x] In-game decklist import screen, reached with `/gathering import`
- [x] Zoom overlay: hold a key over a card to read it at full resolution

**Phase 1 - the solo table.** Playable.

- [x] `GameSession` as an event-sourced state machine - the board is the fold of the log
- [x] Zones, the full v1 verb set, seeded deterministic shuffles
- [x] Visibility rules, with the invariant suite by example and by property
- [x] Data-driven format validator (8 presets), run before a formatted game and never during
- [x] Table multiblock, seats, and a one-click path from walking up to dealt
- [x] Two views of one board: the felt on the window, and the real table seen from above
- [x] Free placement at any angle, drag between zones, box select, attachments, tokens
- [x] Tabletop Simulator's controls, plus the nine verbs its Magic table binds to the number row
- [x] Mat buttons, named zones, counters, commander damage and tax, turn and phase marker
- [x] Undo, concede, session persistence across a restart, spectators
- [x] Cards that travel between zones rather than teleporting, and audible tables

**Phase 2 - the real game.** Under way: multiplayer sessions, per-player visibility sync and
spectator rendering are in; the group playtest that gates the rest is not.

Phases 3-4 (collection and draft, arenas) are described in the design brief, section 14.

### Playing it

Craft a table, place it, and it builds itself into a two-by-two multiblock. Import a deck
with `/gathering import` - paste a decklist in any of the six formats above, or an Archidekt
link - then walk up to the table holding the deck and right-click it. That deals: you are sat down, shuffled, and
holding seven. Crouch and right-click instead to choose a format first, or free play if you
would rather nobody's deck be checked.

Hold **Left Alt** over any card, anywhere, to read it full size with its oracle text. Press
**F1** at the table for every key. Press **V** to swap between playing on the window and
playing on the table itself.

## Two rules the code is built around

**There is no rules engine, and there never will be.** During play the mod moves cards,
tracks numbers and shows things. It never says no. You may tap an already-tapped creature,
set your life to minus eleven, or draw six cards on turn one; the log attributes every action
by name, and that attribution is the mechanism. The single exception is a static deck check
before a formatted game starts, exactly like a tournament deck check, and it ends the moment
the game does not.

**Hidden information is real.** Card identity for a hidden zone never reaches a client that
is not entitled to it, so a modified client learns nothing. Face-down cards travel as opaque
markers regenerated on every flip. Shuffles derive from a session seed that is never logged,
never sent, and never printed. This is the one security property of the mod and it has its
own test suite, which must never regress.

## Four rules the interface is built around

**A word on the felt is written whole or not at all.** A label shrunk to fit stops being a
word before it stops being drawn, and where that happens is not a fraction of its natural
size - it is where the font's own pixels stop getting a screen pixel each. So a set of
labels is measured from its longest and written all at one size or not at all, and anything
with no room for its name says what it is when the cursor rests on it instead.

**A screen takes as little of the window as its job needs.** The table is the thing being
played, and anything drawn over it is in the way for as long as it is up. The order of
preference is: write it on the felt, then a tooltip, then a popup the size of its contents,
then a panel, and only then the whole window - and nothing has earned the whole window. Every
screen the table opens draws the board behind it, because most of what is decided on them is
decided by looking at the board.

**A number a player keeps is kept where they look for it.** Commander tax lives under the
commander it belongs to - written across the foot of its command slot, where pressing it
records another cast and right-clicking takes one back. No panel, no menu, and no second
number counting the one card everyone can already see is in there.

**Nothing teleports.** A card that changes zones crosses the felt to get there, and everybody
watching sees it cross - worked out by comparing two boards rather than by being told, so the
movement is public while the card's identity stays exactly as private as it was. A shuffle,
which moves nothing anybody may look at, shakes the pile instead. Both views draw it, and the
table makes the noise as well.

## How a card reaches your screen

Worth knowing, because it explains most of the architecture:

1. A card item carries three fields: a Scryfall printing id, a foil flag, and nothing else.
2. The server resolves printings through a batched, rate-limited Scryfall client and keeps
   Scryfall's own JSON in a disk cache. A hundred-card decklist costs two requests cold and
   none warm.
3. The server sends a client display metadata **only** for cards that client is entitled to
   see. That is the whole security property: a client cannot leak a hand it was never told
   about, however modified it is.
4. That metadata includes image **URLs**, never image bytes. Each client fetches art from
   Scryfall itself, off-thread, into its own disk cache, with a capped number of textures
   resident in VRAM.

No card art ships in the jar and none crosses this mod's network.

## Licence

Mod code is [MIT](LICENSE). It contains no Wizards of the Coast assets and no Scryfall data.
