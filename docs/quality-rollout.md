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

Not started.

## Phase 3 — frequent actions and discovery (Q09-Q12)

Not started.

## Phase 4 — crowded Commander boards (Q13-Q16)

Not started.

## Phase 5 — collection progression (Q17-Q19)

Not started.

## Phase 6 — modpack integration (Q20-Q24)

Not started.

## Phase 7 — feedback, accessibility and acceptance (Q25-Q27)

Not started.
