# Quality baseline

What the five tasks cost before the quality project changed them, so afterwards there is
something to compare against rather than a feeling.

Written at commit `16b2573`, which is two commits past `3ff8f34` — the revision the handoff
guide's source links are pinned to. The two commits between are the second external review's
fixes (V-01 to V-08) and the quality pass on them; nothing in this project reverts them.

## Environment

| | |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248 (primary) |
| Fabric Loom | 1.9.2 (secondary) |
| Java | OpenJDK 21.0.10 |
| Gradle | 8.14.3 |
| Machine | headless Linux container, no GPU; the scripted client renders through xvfb |
| Card cache | warm for the fixture cards the scripted run uses; cold for everything else |

The machine matters for what follows. **There are no human observations in this document and
none can be taken here**: this is a container with no person in front of it. Frame-time
percentiles measured through xvfb software rendering are not a number anybody's real machine
would see, so they are not published as if they were. What *is* measured here is the thing
that does not depend on hardware or on who is holding the mouse: **how many deliberate
gestures each task takes**, counted off the code paths that handle them.

Every count below is a gesture a player has to decide to make. Moving the mouse to a card is
not counted; pressing a button is. Waiting for the server is not counted. Typing a word counts
as one entry, noted separately, because a name that has to be retyped every time is a
different cost from a button.

## The five tasks, as they stand

### 1. Draw, play, tap, pass — one turn's core loop

**4 gestures.** `2` draws. A left-drag from the hand to the felt plays. `E` taps the card
under the cursor. `0` (or Enter) ends the turn.

Nothing here needs improving on count; the cost is discovery. All four are on the F1 list and
nowhere else — a player who never presses F1 has no way to learn that `2` draws, because the
menus name the verbs but the number row is only written into `TableScreen.shortcuts()` as bare
English literals (`"2"`, `"E"`, `"R"`), not read from anything a player can change. **The keys
are not rebindable.** A left-handed player, or one whose keyboard layout puts those keys
somewhere else, has no recourse.

### 2. Add a named counter to several selected cards

**6 gestures** for three cards and a counter name already used at this table: three
shift-clicks, right-click, "Counters", and one press of the named button `CountersScreen`
offers. `CountersScreen.namedAtThisTable()` already collects names in play, which is the
useful half of a favorites list.

**6 gestures plus typing the name** when the name is new to the table — and again the next
time, in a fresh game, for a counter the player uses constantly. Nothing remembers what *this
player* names; only what this table currently shows.

### 3. Create a familiar token

**2 gestures** when the card names its own tokens: right-click it, take "Make a %s". This is
already good, and it comes from Scryfall's `all_parts`, so it only works from a card that makes
that token.

**3 gestures plus typing** otherwise: right-click the felt, "Make a token", type the name,
confirm. A player who makes the same Treasure token forty times a game types "Treasure" forty
times. There is no recent list and no pinned list.

### 4. Inspect the lower card in an overlap

**Not reachable.** `TableScreen.frontMostAt` walks the table list from the front and returns
the first card the point is inside; every gesture that names a card — Alt to read, right-click
for the menu, click to select — goes through it. There is no way to address a card that is
underneath another one.

What a player actually does is **3 gestures and two changes everyone at the table can see**:
drag the top card off, Alt-read the one beneath, drag the top card back. `TableStacking` already
draws a pile leaning so the edges show, which is what makes the lower card visible enough to
want to read — and then there is nothing to do about it.

This is the sharpest finding in the baseline. It is not a cost in gestures; it is a task the
player cannot do at all without moving other people's cards.

### 5. Find the missing cards for a target deck

**No bounded gesture count, because there is no view that answers the question.**

`screen.gathering.missing.*` exists and is good, but it is per *set*: "MOM — 214 still to
find". A player whose goal is "the deck I am trying to build" has to open the deck contents
(1 gesture), then search the collection for each card by name in turn (2 gestures each) and
hold the comparison in their head. For a 100-card Commander list that is on the order of two
hundred deliberate gestures and no total at the end.

The pieces are all there — `Wants`, the collection search, `DeckFromCollection`'s counting —
and none of them are pointed at a deck.

## What the counts say

| Task | Gestures now | The real cost |
|---|---|---|
| 1. Draw / play / tap / pass | 4 | discovery, and keys that cannot be rebound |
| 2. Named counter on 3 cards | 6, or 6 + typing | nothing remembers this player's counters |
| 3. Familiar token | 2, or 3 + typing | no recents, no pins |
| 4. Read the card underneath | unreachable (3 + two public moves) | cannot be done without touching the board |
| 5. Missing cards for a deck | unbounded | the question has no screen |

Tasks 1 and 3 are close to right and should be left alone except for discovery. Tasks 2 and 5
are missing a memory and a view respectively. Task 4 is a hole.

## Human and hardware measurements, unmeasured

The guide asks for time-to-completion, mistakes, and a newcomer's route through the controls.
None of that is in this document, and none of it is inferred. Specifically **not measured**:

- Time to complete any of the five tasks, by anyone.
- Mistakes and wrong turns a first-time player makes.
- Whether a newcomer discovers F1 at all.
- Frame-time percentiles and worst stalls on a crowded board, on real hardware.
- Input-confirmation latency over a real network.

`docs/playtest.md` is the form. Running it needs people and a machine with a graphics card,
and this project will hand back exactly which numbers are still blank rather than filling them
in from a container.

## Artwork

`docs/art-hashes.txt` holds the SHA-256 of all 2,152 textures, sprite sheets, `.mcmeta` files
and sounds in the repository as of this commit. `tools/artcheck.py` compares the tree against
it and fails on a changed or missing file. Nothing in this project may change one; the check
is how that is proved rather than asserted.
