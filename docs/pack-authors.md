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

## What has *not* been verified

Stated plainly, because a compatibility claim nobody has run is worse than none:

- **Cataclysm and Create examples are not included and not verified.** Writing a
  `required_mods: ["cataclysm"]` file is supported by the contract above and tested against an
  absent mod — but no example has been run against the actual Cataclysm or Create artifacts at
  any specific version, so no claim is made about their advancement ids, loot table paths or
  recipe types.
- **Create: Aeronautics is separately unverified.** Do not infer anything about moving
  structures from ordinary Create support; nothing here has been tested with it at all.
- The contract has not been exercised in a real modpack. What is tested is the loading,
  bounding, reload and absent-mod behavior described above.

If you wire one of these up, the thing worth reporting back is the exact mod version and the
trigger you used, since that is the part this repository cannot check for itself.
