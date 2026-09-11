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

## Where the work is

The quality project (`docs/quality-backlog.csv`) is the current spine. Phases 0–2 and Q09 are
done and written up in `docs/quality-rollout.md`. Q10 is next.

Branch: `claude/new-session-beye3i`.
