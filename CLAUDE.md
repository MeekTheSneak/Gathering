# Gathering — working agreement

A Minecraft 1.21.1 mod (NeoForge 21.1.248 primary, Fabric secondary) that turns a 2×2 table
multiblock into a Tabletop-Simulator-style card game surface: genuine hidden information, and
**no rules enforcement, ever**.

Read this first, then `DIALECT.md`. Between them they carry everything that cannot be inferred
from the code.

| What | Where |
|---|---|
| Conventions, verification, and 87 project-specific gotchas | `DIALECT.md` |
| What the mod is and is not, phase plan, locked decisions | `docs/design-brief.md` |
| The quality project: 28 tickets, Q00–Q27 | `docs/quality-backlog.csv` |
| What each quality phase actually changed, and what is unverified | `docs/quality-rollout.md` |
| The numbers the quality work is measured against | `docs/quality-baseline.md` |
| How the checks work, and how to run them | `TESTING.md`, `tools/README.md` |
| **What is actually true right now** — implemented, verified, still unverified, next action | **`docs/working-record.md`** |
| External reviews of this repository, with their findings | `docs/reviews/` |

## The gate

Nothing is done until this exits zero. **Never report something as working that the gate has
not confirmed.**

```
tools/gate.sh --game
```

Thirteen checks: gradle build (all unit tests), then `langcheck`, `doccheck`, `scenecheck`,
`plotcheck`, `gesturecheck`, `spritecheck`, `statecheck`, `savecheck`, `runcheck`,
`texturecheck`, `artcheck`, and the in-world game tests.

Run the slow stages **one at a time** — they share `neoforge/run` and fight if two are going.

## Testing functionally *and* visually

The gate does not look at pixels. A separate scripted client does:

```
tools/shots.sh      # DevScene drives a real client under Xvfb and photographs it
tools/preview       # renders pure layout to PNG, no game needed
```

`tools/shots.sh` takes about 18 minutes and writes to `neoforge/run/screenshots/`. **Look at
the pictures.** The scripted client has repeatedly caught what the headless gate cannot: an
unreachable offer, overlapping text, a bad text scale, a mis-timed screenshot, a palette that
drew correctly and pressed nothing.

Extend `DevScene` to exercise every new interaction. A step that only photographs is weaker
than a step that photographs *and* asserts the thing happened.

## Standing goals, in priority order

1. **Correctness first.** Every change goes through the gate.
2. **Test functionally and visually, in fine detail.** Fix what looks or feels wrong.
3. **Polish to the standard of Create, Astral Sorcery or Alex's Caves** — consistent feedback
   on every interaction, sensible defaults, nothing that needs explaining, no dead ends, no
   silent failures, good in-game discoverability, tooltips that answer the question being
   asked. **Not textures: the repository owner does those.**
4. **Fewest possible steps for the player.** Every flow as short as it can honestly be.
5. **A full quality pass every third or fourth iteration** — bugs, dead code, duplicated rules
   that could drift, unused strings, per-frame cost, thread safety across the client/server
   boundary.
6. **Work the design brief's remaining phases in order**, never at the cost of the polish above.

Every iteration: commit and push verified work with a clear message; keep the task list
current; **if you find a bug, add a guard that fails without the fix** — and prove the guard
fails, rather than assuming it would. Prefer finishing one thing completely over starting
several.

## Hard rules

- **No rules enforcement.** The mod never decides whether a play is legal. Any seated player
  may move any public card; the log says who did.
- **No player-supplied image URLs, anywhere.** The custom playmat image is a settled no —
  `docs/design-brief.md:313`. Do not re-raise it; it is the owner's call and it has been made.
- **The visibility invariant.** No hidden identity reaches an unentitled client. Everything
  that reads the board for a player goes through `VisibilityRules`; what crosses the wire is a
  `GameView`, never a `GameState`; `Viewer.Historian` never appears in `allViews`.
- **The shuffle seed is never logged, sent, or printed during play.**
- **All server randomness uses `level.getRandom()`.** The one deliberate exception is
  `UUID.randomUUID()` for unguessable trade, receipt, deck and practice handles, documented
  where it is used.
- **No real-world prices.** SSRF allowlists on `DeckLink` and `ArtHosts` — Scryfall only.
- **Textures are the owner's.** `tools/artcheck.py` holds the SHA-256 of all 2,152 textures,
  sprite sheets, `.mcmeta` files and sounds, and the gate fails on any change. No phase of this
  project may alter one.

## Writing

American spelling throughout — `color`, `center`, `behavior` — in identifiers, prose, lang
keys and commit messages alike.

Interface text is terse. **Buttons are verbs. Labels are nouns. Neither is a sentence.**
"Choose", not "Take these two". See `DIALECT.md`'s *Interface text* section.

Commit messages say what changed and why, in the same register as the code comments, and end
with the co-author and session trailers. **Never put a model identifier in a commit message, PR
title or body, code comment, or any other pushed artifact.**

## How work is done here

This workflow is owner-set and applies to every change. It is not a summary of good
intentions; each numbered item exists because something went wrong without it.

**1. Keep the working record current.** `docs/working-record.md` is the one place that says
what is implemented, integrated, verified and still unverified, what the known defects are,
and what the next concrete action is. Update it as work happens, not afterwards. After a
context compaction, read it and the relevant source before continuing. **Treat earlier
conversational summaries, code comments and claims of success as leads to verify, not
proof. Never reconstruct test counts from memory** - paste them from the run that produced
them.

**2. Define observable success before implementing.** For each bounded change write down:
the player action that starts it; the visible result; the state it may change; the property,
hidden information and unrelated state it must preserve; and what happens on refusal, delay,
cancellation, disconnect and restart. Name the real production entry point and destination.
**A helper is a foundation until a real player action reaches it and its result reaches the
player.** Work in small batches and finish the integration review before stacking more on
top.

**3. Review the whole affected path.** Input, dispatch, validation, mutation, persistence,
response, rendering, cleanup. Read the neighbours, not only the changed lines, and search for
every caller, constructor, serializer, copy, recovery path and lifecycle hook the change
touches. Adding a field to an item means checking save/load, network codecs, refunds,
recovery, trading and old-data defaults. Adding a key binding means checking earlier
hardcoded handlers, other screens, spectators, help text and conflicts. Adding a lifecycle
means checking every exit, including the ones that skip the Close button. **A comment
describing a guarantee does not establish it.**

**4. Review again afterwards, as a separate pass.** Stop thinking about what you meant to
build and read what the code permits. Start from the player-facing requirement, then use the
diff to find the changes. Ask: what else can operate on this state? What if the action
happens twice, or out of order? What goes stale between request and completion? What if a
screen, seat, item, world or connection changes underneath it? Can an ordinary action cause
loss, duplication, disclosure or a stuck interface? Is the limit enforced where the work
actually happens? Which "finished" feature exists only in tests and documentation? For every
safety claim, find the enforcing code and try to build a counterexample. Use an independent
reviewer agent with a bounded scope where one is available, and give it the requirements and
the diff before any narrative about the implementation; investigate what it says rather than
accepting or dismissing it. Otherwise label the pass as self-review.

**5. Test interactions and failure boundaries**, chosen by the risk of this change rather
than by combination. The ones that keep finding real defects here: a new feature combined
with existing inventory and deck handling; disconnect, reconnect, save and reload; delayed
completion after ownership changed; duplicate requests and stale responses; persistence
failure; remapped or unbound controls; restart after a demonstration ran out of cards; more
than one player; old saves and defaults for new fields. Use real entry points. For anything
touching property, account for where it is before and after: inventory, held state, pending
delivery, saved state, world drops. Prefer a deterministic clock to a sleep or a timing
comparison.

**6. Failures are information.** Do not weaken an assertion, move a scenario somewhere it
passes, change an expected result to match broken behavior, or drop a test to get back to
green. If a fixture is genuinely invalid, say exactly why and replace it with one that keeps
the coverage. Reproduce a bug before fixing it where practical, and keep the regression.

**7. Verify what the player experiences.** Use the graphical client for interface changes -
real input, remapped controls, cancellation, screen transitions. **A screenshot proves
appearance at one moment; it proves nothing about interaction, authorization, persistence or
multiplayer.** Where graphical or modpack testing is not possible, do the source and
automated work and then state plainly what is still unverified. A scripted run is not a
human playtest and must never be described as one.

**8. Finish honestly.** Before calling anything complete: its production path is connected;
the behavior and its important interactions were reviewed; the checks passed; known material
defects are fixed; the documentation matches the behavior; and the remaining limits are
stated. Do not hunt for a quota of bugs, do not award yourself a score instead of evidence,
and do not promise none remain. End each batch with what now works for the player, what
verification actually ran, what the review found and corrected, and what is still incomplete
or unverified.

## Where the work is

The quality project (`docs/quality-backlog.csv`) is the current spine, but
**`docs/working-record.md` is where its real state lives** - the backlog has been wrong about
its own rows before. The next piece is QP-07, the local tutorial overlay, specified in
`docs/reviews/quality-progress-2026-09-11.md`.

Branch: `claude/new-session-beye3i`.
