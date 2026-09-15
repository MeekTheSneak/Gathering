# Rewards, for pack authors

This mod can hand somebody sealed product by name. A pack decides *when*; this decides *what*.

## Why it is only half a feature

Your pack already has advancements, loot tables, recipes and (on most setups) a command
scheduler for deciding when something happens. A second trigger system inside this mod would be
a worse copy of the one the game ships with, and you would have to learn it before you could use
any of this. So there is no trigger here at all.

You define a named reward, and you fire it from whatever you already use:

```
/gathering grant boss_drop @p
```

## The file

One JSON file per reward, in `config/gathering-rewards/`:

```json
{
  "id": "boss_drop",
  "set": "j25",
  "product": "default",
  "color": "W",
  "count": 2,
  "required_mods": ["cataclysm"]
}
```

| Field | | |
|---|---|---|
| `id` | required | what you call it in `/gathering grant`. Lower-cased. |
| `set` | required | the set code the product comes from. |
| `product` | required | which product of that set. |
| `color` | optional | one of `W`, `U`, `B`, `R`, `G`. Restricts which arrangements the pack may open as — see `PackComponent`. |
| `count` | optional, default 1 | how many, 1 to 64. |
| `required_mods` | optional | mod ids that must be installed for this reward to be grantable. At most 16. |

Every string is capped at 64 characters. **There is no field that can name a URL, a class, a
command or a file**, and a value that looks like an address is refused rather than ignored.
Nothing here fetches anything.

## Reloading

```
/gathering rewards            # what loaded, and anything wrong with it
/gathering rewards reload     # read the folder again
```

**A bad reload changes nothing.** If a file will not parse, or the folder cannot be read, the
previous set stays exactly as it was. A trailing comma in one file must not become a server
where nothing is granted and nobody knows why. Problems name the file and the field:

```
toomany.json: count: is 9999. It has to be between 1 and 64
```

The whole set is swapped in at once, so nothing ever sees half a reload.

## Missing mods are not errors

A reward naming `required_mods` you have not installed **loads quietly and is simply not
grantable**. `/gathering grant` refuses it rather than handing over something else. This is
deliberate: a pack that ships rewards for four boss mods and expects two of them installed
should not fail to load, and should not silently give a plain pack instead of the boss one.

## Loader support

The reward contract is **loader-generic**. It is the same folder, the same JSON and the same
commands on NeoForge and on Fabric, and `required_mods` is answered by whichever loader is
running. Both loaders are built and game-tested in this repository.

## Playing well with other mods

The aim is that Gathering can go into any 1.21.1 pack. What that rests on, and how each part was checked:

- **Loaders.** NeoForge 21.1.80 or later (every in-world test run on 21.1.80, and the tour's opening in a
  client), and Fabric Loader 0.15.11 with Fabric API 0.102.0 or later (the Fabric in-world tests and a client
  boot run on exactly those). The metadata asks for those floors, not for the versions the mod was built on.
- **Nothing of the game's is replaced.** Tags it adds to (`mineable`, `acquirable_job_site`) say
  `"replace": false`; card packs are added to loot tables as an extra pool (a global loot modifier on NeoForge,
  Fabric's loot event on Fabric), never by rewriting a table; villages get their card shop by adding to the
  house pool, not replacing it; recipes and blocks are in the `gathering` namespace.
- **Three code hooks, all optional.** A camera hook for the view down onto a table, one that hides entities
  standing in that view, and one that keeps a creative deck's cards on the creative inventory packet. Each is
  marked so that if another mod has changed the same vanilla method, the game still starts and only that one
  behavior is lost (the creative one has a second line of defense that restores the cards anyway).
- **Keys.** The table's verbs are read only inside the table's screen. On NeoForge they are marked as screen
  keys, so they share Q, 1 or / with the game without a conflict. On Fabric, which keeps vanilla's
  one-mapping-per-key lookup, they are kept out of that lookup: with the defaults on Q, / and 1 they had taken
  Drop, the command line and the first hotbar slot from the game (found and fixed with `:fabric:runKeyScene`).
  Fabric's Controls screen may still mark those keys red, because it compares every mapping there; nothing is
  actually taken.
- **Create, Create Aeronautics and Sable** are supported and tested (below). No other mod is required, and a
  reward file naming a mod that is not installed loads quietly and is not granted.
- **Cataclysm needs nothing.** Gathering has no Cataclysm code: the only thing it can do with Cataclysm, or any
  boss mod, is what a pack author's reward file asks - a card pack as a boss reward - which uses the same
  contract as every other mod and is tested against an absent mod. What has not been run is an example file
  against Cataclysm's real loot table and advancement names, which is a pack author's file, not the mod.

## What has *not* been verified

Stated plainly, because a compatibility claim nobody has run is worse than none:

- **Cataclysm and Create examples are not included and not verified.** Writing a
  `required_mods: ["cataclysm"]` file is supported by the contract above and tested against an
  absent mod — but no example has been run against the actual Cataclysm or Create artifacts at
  any specific version, so no claim is made about their advancement ids, loot table paths or
  recipe types.
- **Create Aeronautics (1.3.2, on Sable 2.0.5):** tables work on its vehicles and physics objects.
  Sable keeps a moving structure's blocks in a region of their own, so everything that compared a
  table's block position with the world - which side a player clicked from, where a tournament
  seats a player, the pointer to a seat, the camera over the board and clicks on the board -
  goes through Sable's companion library, bundled in this mod's jar (MIT; plain world positions
  when Sable is not installed). In-world tests assemble a real table into a Sable structure, turn
  it, and check each; they run with `./gradlew runPackGameTestServer` and the Sable jar in
  `neoforge/runs/pack-tests/mods`. What those tests cannot reach is a real client on a moving
  airship. On a client the seated camera, the pointer on the felt and the seat marker follow where
  Sable draws the structure each frame (its render pose), not where it is this tick, so they move
  with the ship rather than a tick ahead of it; nobody has sat at a table on a flying one yet.
  A table carried onto a ship takes everything with it once: the game being played, the decks held
  for it and any staked pot go aboard, nothing is handed back where it stood, and a tournament's
  table keeps its number. Set back down, the same. A Scorekeeper's Desk takes signing up with it.
  One known gap: a ship set down **rotated** ends a table's game (handing everything back once on the
  ship), because the table's corners change roles as they turn.
- **Create (6.0.10) display boards:** a Display Link against a Scorekeeper's Desk offers
  *Tournament*, set in the link's own settings to show the standings, pairings, round and clock,
  final places, prizes or sign-ups of the tournament the desk runs. Against a table it offers
  This Table's Match and Life Totals. A Clipboard used on the desk takes down this round's pairings,
  ticking each whose result is in, and the standings. In-world tested with Create installed; standings and pairings
  on a powered display board and a table's match on a sign have been seen in a real client.
- **Create contraptions** (bearings, pistons, gantries, trains) leave tables and collections where
  they are, as a piston does: a game and a collection's cards belong to a block position a
  contraption's moving copy of the world does not have. For a table that travels, use Create
  Aeronautics, whose structures keep their blocks in the world. A Scorekeeper's Desk is carried;
  its host uses it again where it stops to bring signing up back to it.
- **Create Deployers open boosters:** an empty-handed Deployer opens a booster lying loose on the
  ground (facing any way) or on a Depot or belt it faces sideways, turning it into its cards there.
  Facing *down* onto a Depot or belt does not work: Create gives that press to its belt processing,
  which ignores an empty hand. A Deployer holding a booster opens it into its own inventory.
- The contract has not been exercised in a real modpack. What is tested is the loading,
  bounding, reload and absent-mod behavior described above.

If you wire one of these up, the thing worth reporting back is the exact mod version and the
trigger you used, since that is the part this repository cannot check for itself.
