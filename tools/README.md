# tools

Two kinds of thing, and nothing else. Everything here is run by hand or by the gate; none of
it is needed to build the mod, and none of it ships in the jar.

## Checks — run by `smoke.sh`, and worth running on their own

| | |
|---|---|
| `gate.sh` | Everything that has to pass before a change is called working, in one command: `./gradlew verify` (both loaders, `:core:test`, the architecture fences, datagen and **both** loaders' in-world tests), then the sixteen static checks, then a check that the in-world tests discovered a nonzero count on both loaders - a suite that finds nothing passes - and that the run logged no failure a table tick or tournament clock contained rather than crashing on. `--quick` is the build and the checks only, for iterating, and says it is not the gate. Reports by exit code and names the stage that broke, because reading a build's output for the wrong word is a real way to report a green gate that is not one - `gradlew build \| grep error:` says nothing when a test fails, since a failing test prints `FAILED`. It ran `build` rather than `verify` until an audit found the two gates disagreeing. |
| `smoke.sh` | Boots every target the mod claims to run on and says whether it did. Also runs every check below. |
| `../gradlew verify` | The gate: build, unit tests, data generation, and both loaders' in-world tests. |
| `shots.sh` | Drives a real client through the scripted session in `DevScene` and leaves the pictures in `neoforge/run/screenshots`. |
| `quietly.sh` | Runs a scripted client without taking the screen or the speakers: `tools/quietly.sh neoforge/run ./gradlew :neoforge:runClient -Pdevscene`. Mutes the game directory's master volume before the game reads it (and puts it back afterwards), and on a Mac hands focus back to whatever app was in front each time the game window comes forward. Every scripted client run on a machine somebody is using goes through this. |
| `langcheck.py` | Every translation key the mod asks for exists, and every entry is asked for. |
| `doccheck.py` | No javadoc block sits directly above another, which is how a comment ends up describing nothing. |
| `scenecheck.py` | The scripted session's step numbers run 0, 1, 2 … with no holes. |
| `plotcheck.py` | No game test writes blocks outside the plot it was given. |
| `gesturecheck.py` | One gesture per verb, across the context menus, the mat buttons and the keys. |
| `spritecheck.py` | Every element the mod draws has art, in every look - and no client class paints a colored rectangle a theme could not change, but for the two that tint by meaning. |
| `statecheck.py` | Every in-memory holder is emptied when the thing that filled it goes away. |
| `texturecheck.py` | Every texture a model names exists, and every texture that ships is named by something. |
| `voicecheck.py` | Punctuation that reads as writing rather than as interface: em dashes, trailing ellipses, and the semicolon that joins two thoughts a player did not ask to have joined. Checked mechanically because each is a character that is either there or not, and because each arrives looking reasonable in the one line being written and reads as an essay once there are forty. Phrasing is not checked - "not this, but that", the second sentence that restates the first - because a checker that guessed at those would be wrong often enough to be turned off. |
| `spellcheck.py` | American spelling, which CLAUDE.md asks for throughout and nothing enforced: a public `practising()`, "centred", "grey", "labelled" had all drifted in. Reads comments, javadoc, declared names and every `en_us.json` value. Not string literals - some of them are phases written into a save, and renaming one is a migration rather than a spelling fix - and not another project's names, since `BlockBehaviour` is Minecraft's and spelling it any other way does not compile. |
| `coretestcheck.py` | Every test class in `:core` ran at least one test, matched source file to Gradle's result file. The in-world suites print a count the gate reads; `:core` prints none, and a Gradle test task that finds nothing exits 0 - which is how fourteen jqwik properties inside JUnit `@Nested` classes went unrun. Run by `gate.sh` after the build, not by `--quick`. |
| `recipecheck.py` | Every block can be made, and every recipe reaches the recipe book. A block registered, modeled and named is still a creative tab entry until something can be laid out to make it, and a recipe no advancement grants never appears in the book. `--write` writes the missing unlocks; recipes themselves are never written, because what a block is made of is a decision. |

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
| `woodwork.py` | The same five wooden blocks in every wood, written out from the plain one: models, blockstates, item models, loot, recipes and names. `--check` says what would change. |
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
