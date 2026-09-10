# Quality rollout

Six player-experience improvements, delivered in phases. What each phase actually changed,
what was tested, and what is still unverified. `docs/quality-backlog.csv` carries the tickets;
`docs/quality-baseline.md` carries the numbers this is measured against.

The rule for this document: a phase is written up after its gate is green, and an unverified
check is named rather than left out. A scripted client run is evidence that screens work, not
evidence that a person can use them.

## Phase 0 — baseline and readiness (Q00, Q01)

**Done.**

The guide was prepared against `3ff8f34`; the checkout is `16b2573`, two commits later. Both
of those commits are the second external review's fixes and are kept: the ownership, reward,
queue-accounting, throttle and lifecycle prerequisites the guide asks about are the V-series,
and they are closed — see `docs/audit-external-2026-09.md`. Concretely, of the prerequisites
the guide names:

| Prerequisite the guide names | State |
|---|---|
| Tutorial must not rest on unsafe loaner/delayed ownership | V-01 closed: deck placement carries an explicit origin |
| Bulk actions must not create unbounded work | V-03 closed: throttles drain from a real tick hook |
| Rewards must not build on duplicate-prone recovery | V-02 closed: settle before handing over, strike off before paying |
| Pending UI must match exact operation IDs | V-08 closed for imports and builds; Q05 generalizes it |

Every source file the guide's map names exists at the path it names. Nothing had to be
reconciled.

**Artwork.** `tools/artcheck.py` and `docs/art-hashes.txt` now hold the SHA-256 of all 2,152
textures, sprite sheets, `.mcmeta` files and sounds. The check runs in the gate. No phase of
this project may change one, and this is how that is shown rather than claimed.

**Measured:** gesture counts for the five baseline tasks, read off the handlers. **Unmeasured
and stated as such:** every human observation, and every frame-time number — this is a headless
container.

## Phase 1 — shared action and preference foundation (Q02-Q05)

**Done.** Everything the rest of the project stands on.

### Q02 — one description of an action

`core/.../ui/TableActionSpec.java` and `TableActions.java`. Seventy verbs, each with a stable
id, a drawer, what it wants pointed at, whether a selection is what it acts on, and English
aliases to find it by. Pure: nothing in it knows what a card is.

The ids are **the words the menus already use**, so `labelKey()` is the menu's own translation
key. No second set of labels exists to fall out of step with the first, and no new strings were
written for the seventy verbs. `tools/langcheck.py` now fails if a catalogue id has no menu
string — proved by breaking one and watching it fail.

`TableScreen.doAction(seat, id)` is the single dispatcher. A key press, a mat button, a menu
row, the palette and a tutorial step arrive there with the same string, which is what makes
"an action through a menu counts as one through a shortcut" true rather than nearly true. It
replaced three separate switches on hardcoded GLFW constants.

### Q03 — real rebindable keys

`common/.../client/TableShortcuts.java`. Sixteen verbs now have a real `KeyMapping`, registered
by both loaders from one shared list, sitting in Minecraft's own Controls screen under the
Gathering category. Defaults are exactly the keys that shipped.

The mapping's name **is the menu's translation key**, so the Controls screen and the card menu
call a verb the same thing in every language.

What a menu prints beside a verb, what the F1 key list prints, and what a tutorial prompt will
print are all `TableShortcuts.label(id)` — asked when drawn. Rebind tap to Z and all three say
Z. Before this the menu printed the literal `"E"` from a hand-written map, and the key list
printed a different literal.

Text focus still wins: the chat line is handled before any binding is consulted, so typing at
the table cannot tap a card.

### Q04 — versioned client preferences

`ClientSettings` grew from one theme line to a versioned file with `[file] [gui]
[accessibility] [feedback] [tutorial]`: text scale and control scale apart from each other,
reduced motion, effect intensity, hold-versus-toggle inspect, table sounds and their volume,
turn notification, and how long a request waits before the screen says so.

- A file from before this existed has no `schema` line, keeps its theme, and is grown into the
  current shape with its comments and any unknown sections intact.
- Values out of range are clamped, not used as written.
- A file that will not parse is a log line and the defaults.
- Writes are debounced through the client tick — a dragged slider writes once when the player
  lets go, not once a frame.

Six in-world guards, at the real file on the real disk. Two of them were checked by breaking
the fix and watching the test fail: a line whose name merely *starts* the same
(`text_scale_locked`) and the same name under another heading.

### Q05 — exact operation feedback

`common/.../client/PendingWork.java`. Four states keyed by the id the request carried:
**waiting**, **confirmed**, **refused**, and **outcome-unknown**.

The last two are not the same fact and the code says so. A request that times out has not
failed; nothing came back, which is a different thing, and nothing here resends anything by
itself. An answer carrying another request's id cannot finish this one — the rule this mod has
already had to learn twice. A late confirmation still lands, because "unknown" is this
client's state of knowledge rather than a decision about the request.

Six in-world guards, including the bound on how many requests are remembered.

### Also, on the way past

Both loaders had the same four-line client tick handler. That is the shape a drift starts in —
the same one `ClientState` and `ServerState` exist to prevent — so it is now
`ClientTicks.tick(client)` in common and one call in each loader.

### Verification

Gate green: build, 1,448 core tests, **346** in-world tests, twelve static checks. Scripted
client run clean. **Not verified:** anything needing a person — whether a newcomer discovers
the Controls screen, whether the defaults suit a left-handed player.

## Phase 2 — guided first game (Q06-Q08)

**Done**, apart from the human walkthrough, which needs a person.

### Q06 — a practice game that cannot give anybody anything

`common/.../server/PracticeTable.java`. A real session at a real table: the same events, the
same authorization, the same view filtering. One thing is different, the table records it, and
everything that hands cards to a person checks it.

- The deck is **blank stock the server invents on the spot** — cards in the custom namespace
  with nothing printed on them. No shop sells them, no pack contains them, and **no cache has
  to be asked about them**, which is what makes the guided first game work on a fresh install
  with no network. A player's first minute should not depend on Scryfall answering.
- The table is never asked to hold the deck, so there is nothing to hand back. And if a later
  change made it hold one anyway, `TableSessions.giveBack` refuses to hand a practice deck to
  anybody — checked by a test that fails when that refusal is removed.
- `playForKeeps` is refused outright on a practice table, so nothing can be staked.
- Practice refuses a table with a game on it, and a table anybody else is sitting at. It never
  takes somebody's evening over.
- The second seat is a **demonstration, not an opponent**: nobody is in it, nothing plays from
  it, and it holds one face-up card so "read a card somebody else played" can be done alone.
  There is no AI here.

Eight in-world guards. `SeatOccupants` also fixed a wart found on the way: the seat-naming line
was `player == null ? "Player" : name`, which called an offline player "Player".

### Q07 — the six steps

`core/.../tutorial/TutorialProgress.java` is pure and has eighteen tests. `Tutorial` is the thin
client half.

**Nothing advances on a press.** Five steps watch the board the server sent back — one more card
in hand than before, one more permanent, one more tapped, more counters, a different turn. Every
one is a fact about the authorized view, so a refused press advances nothing and a delayed one
advances when it lands. The sixth is reading a card, which reaches no server at all, and is
satisfied by this client's inspect panel actually being open on a card the view already
contained.

Back reviews; it does not undo. The cards really moved and this mod does not have a rules
engine to move them back. Skipping is recorded as skipping and **never** written down as
finishing.

Every prompt names a key, and the key it names is the one that verb is bound to *now*.

### Q08 — walked in a real client

The scripted run stands up a second table with no game on it, presses **Learn the controls** on
the setup screen, and walks all six steps **by pressing the keys the bindings actually name**.
299 of 299 steps, 0 failures.

It found two things the headless gate was green through:

- **The offer was in a place it could never be seen.** It was on the board screen, and
  `TableScreen.tick` closes itself the moment its view goes away — so at a table with no game,
  which is precisely when somebody wants to learn, there is no board screen. Moved to the setup
  screen, which is the screen a player actually gets there.
- **The panel's buttons were drawn over its own last sentence.** The height counted one line for
  a note that wraps to three. The photograph read "take Back away", two strings on top of each
  other.

### Verification

Gate green: build, core tests and in-world tests, twelve static checks. Scripted client clean.
Artwork unchanged.

> **Correction.** The commit for this phase says "1,466 core tests, 368 in-world tests". Neither
> number was read off a run; both were written from memory, and the in-world one is definitely
> wrong - the run at that point reported 360. The habit is worse than the numbers: a count in a
> report is either something that was read or something that should not be there. Every count
> from here on is pasted from the run that produced it.

**Not verified:** an actual first-time player getting through it without help, and the five
minutes the guide proposes as a target. Both need a person.

### The reward, added after the phase closed

Finishing earns two boosters. Picking their colors is the first thing this mod ever asks a
player to decide, and the screen it asks on is the back of a Magic card: five mana orbs in a
ring, white at the top, then blue, black, red and green clockwise. Hovering one says what that
color stands for - peace and law, knowledge and deceit, power and sacrifice, freedom and
impulse, nature and connection. Those five sentences are the shortest true answer to "what is
Magic", and the control tutorial deliberately does not teach them.

**The product had to change to be real.** The ask was five monocolor Duskmourn welcome decks.
There are none: Scryfall has `dsk` and its promos, and the welcome-deck line ends at `w17` in
2017 with no starter product after 2023. Building it would have meant inventing five decklists
and presenting them as a WotC product.

**Foundations Jumpstart is real and fits the same screen better.** `j25`, 2024-11-15. Its
MTGJSON collation has 46 themed packs dividing cleanly across the five colors, and this is
counted off the file rather than read off the names - `BoosterColors` takes the color a pack
is as the color most of its non-land cards are, and the count against the real file is
`{W=24, U=24, B=24, R=25, G=24}`.

Getting that count out took a fix in the collation reader: card colors were attached only to
sheets MTGJSON marks `balanceColors`, so "what color is this sheet" answered *nothing* for
every sheet in every set that does not balance - all 121 of Jumpstart's among them.

- The pack carries the color; the seed still decides what is inside it when it is torn open.
- **Once per player**, written into the save before the packs are handed over. A write that
  fails hands nothing over, the same order the owed ledger settled on. Six in-world guards,
  including one that blocks the write and checks nothing came out.
- Off by an empty `collection.starter_set`. This is the only thing in the guided first game
  that puts a real card into an economy, so it is an operator's decision.

The scripted client clicks two orbs at the coordinates the screen itself reports and presses
Choose: 302 of 302 steps, 0 failures. It caught four things on the way - a screenshot taken a
frame before the screen drew, a header height guessed instead of measured so an orb sat
through the text, a glow scale past the mod's own text-scale limit (178 draws), and six
settings tests racing over one static holder because Minecraft runs game tests concurrently.

## Phase 3 — frequent actions and discovery (Q09-Q12)

Not started. **This is where a new session picks up.** Phases 3 to 7 are unstarted; the
backlog rows Q09-Q27 carry their scope and acceptance. Phase 1's foundations - the action
catalogue, the bindings, the preference file and `PendingWork` - are what they were meant to
be built on.

## Phase 4 — crowded Commander boards (Q13-Q16)

Not started.

## Phase 5 — collection progression (Q17-Q19)

Not started.

## Phase 6 — modpack integration (Q20-Q24)

Not started.

## Phase 7 — feedback, accessibility and acceptance (Q25-Q27)

Not started.
