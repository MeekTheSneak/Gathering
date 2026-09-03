# tools

Two kinds of thing, and nothing else. Everything here is run by hand or by the gate; none of
it is needed to build the mod, and none of it ships in the jar.

## Checks — run by `smoke.sh`, and worth running on their own

| | |
|---|---|
| `smoke.sh` | Boots every target the mod claims to run on and says whether it did. Also runs every check below. |
| `../gradlew verify` | The gate: build, unit tests, data generation, and both loaders' in-world tests. |
| `shots.sh` | Drives a real client through the scripted session in `DevScene` and leaves the pictures in `neoforge/run/screenshots`. |
| `langcheck.py` | Every translation key the mod asks for exists, and every entry is asked for. |
| `doccheck.py` | No javadoc block sits directly above another, which is how a comment ends up describing nothing. |
| `scenecheck.py` | The scripted session's step numbers run 0, 1, 2 … with no holes. |
| `plotcheck.py` | No game test writes blocks outside the plot it was given. |
| `gesturecheck.py` | One gesture per verb, across the context menus, the mat buttons and the keys. |
| `spritecheck.py` | Every element the mod draws has art, in every look. |
| `statecheck.py` | Every in-memory holder is emptied when the thing that filled it goes away. |
| `texturecheck.py` | Every texture a model names exists, and every texture that ships is named by something. |

## Looking at what the game drew

| | |
|---|---|
| `crop.py` | Cuts a rectangle out of a shot and magnifies it by whole pixels, so a badge or a shadow can be judged. `crop.py <shot> <out> <x0> <y0> <x1> <y1> [scale]` |

## Hooks — run by Claude Code, not by hand

| | |
|---|---|
| `sync-branch.sh` | Before any file is written, checks this clone still agrees with `origin` and fast-forwards it if not. |
| `session-start.sh` | The same at session start, plus dropping a repo-local git identity that would make commits show as Unverified. |

The session runs in a container that is reclaimed and restored from an earlier snapshot,
sometimes mid-turn - on 2026-09-03 the clone came back 142 commits behind while work was going
on, so edits landed on old code and the code was described as it had been rather than as it
is. The remote is the only thing a snapshot cannot roll back, so the check reaches it: at most
once every ninety seconds, because a fetch is about four hundred milliseconds and an edit
should not wait that long. What records the last check lives in `.git`, so a restored snapshot
brings back an old one and the very next edit really checks. Neither script ever blocks a tool
call; both are wired up in `.claude/settings.json`.

Only pushed work survives a restore. That is the whole reason this repo commits and pushes
every finished piece rather than at the end.

## Art — the generators, and the one file they share

The PNGs they write are checked in, so the build never runs any of this. Run one when you
have changed what it draws from.

| | |
|---|---|
| `gui_art.py` | Every screen sprite, once per look. Writes `textures/gui/sprites` and the look files beside them. |
| `mana_art.py` | Assembles each mana symbol from its badge and its mark under `art/mana`. `--check` fails if what ships is no longer its parts. |
| `block_art.py` | The block faces: a collection, a shop counter, a sealed box. |
| `card_back.py` | The card back and the plain sleeve. |
| `village.py` | The local game store, as a village building, in every biome it appears in. |
| `villager_guide.py` | The shopkeeper's UV map, to paint a profession texture by. |
| `install_villager.py` | Copies a painted shopkeeper from `art/villager` to where the game reads it. |
| `pack_cut.py` | Cuts BDragon1727's sprites off his sheets into `art/gui/parts`. Ran once, with the sheets on disk; kept because it is the record of where those files came from. See `art/CREDITS.md`. |
| `pngwrite.py` | Writes a PNG, and refuses to write over one somebody has painted by hand. Shared by the generators above. |
| `nbtio.py` | A small NBT reader and writer, enough for the structure templates `village.py` writes. |

## Everything else

| | |
|---|---|
| `preview/` | Renders a screen's layout to a PNG without a game, for looking at spacing. |
| `session-start.sh` | Puts the workspace back where the last session left it. Not part of the mod. |
