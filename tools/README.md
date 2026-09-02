# tools

Two kinds of thing, and nothing else. Everything here is run by hand or by the gate; none of
it is needed to build the mod, and none of it ships in the jar.

## Checks — run by `smoke.sh`, and worth running on their own

| | |
|---|---|
| `smoke.sh` | Boots every target the mod claims to run on and says whether it did. Also runs every check below. |
| `shots.sh` | Drives a real client through the scripted session in `DevScene` and leaves the pictures in `neoforge/run/screenshots`. |
| `langcheck.py` | Every translation key the mod asks for exists, and every entry is asked for. |
| `doccheck.py` | No javadoc block sits directly above another, which is how a comment ends up describing nothing. |
| `scenecheck.py` | The scripted session's step numbers run 0, 1, 2 … with no holes. |
| `plotcheck.py` | No game test writes blocks outside the plot it was given. |
| `gesturecheck.py` | One gesture per verb, across the context menus, the mat buttons and the keys. |
| `spritecheck.py` | Every element the mod draws has art, in every look. |
| `statecheck.py` | Every in-memory holder is emptied when the thing that filled it goes away. |

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
