#!/usr/bin/env python3
"""The same wooden furniture in every wood, written out from the plain one.

Five wooden things - the table, the chair, the shop counter, the collection and the Scorekeeper's
Desk - each come in every wood Minecraft has. Like a vanilla door or sign, each wood is its own
block with its own id, its own recipe out of its own planks, and its own models; the plain ids
(`table`, `chair`, ...) keep the wood they were drawn in, so a world built before this keeps its
furniture, and the other ten are named for their wood.

That is fifty blocks, and fifty blocks is two hundred and sixty files. They are written here rather
than by hand, from the plain block's own blockstate, models, item model, loot table and recipe, with
the wood swapped: a change to the dark oak table's model is a run of this away from reaching every
other table. The Java side is not generated - see GatheringContent.Woodwork.

    python3 tools/woodwork.py            write every wood's files
    python3 tools/woodwork.py --check    say what would change, and change nothing

Run it after editing a plain model, and run the gate afterwards.
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "common/src/main/resources/assets/gathering"
DATA = ROOT / "common/src/main/resources/data"
LANG = ASSETS / "lang/en_us.json"

#: Every wood, and what each names its planks and its stripped log. Bamboo's planks come off a
#: stalk rather than a tree and the two nether stems are not wood at all botanically; all three
#: are planks in the hand, which is what a player means by "in every wood".
WOODS = {
    "oak": ("oak_planks", "stripped_oak_log"),
    "spruce": ("spruce_planks", "stripped_spruce_log"),
    "birch": ("birch_planks", "stripped_birch_log"),
    "jungle": ("jungle_planks", "stripped_jungle_log"),
    "acacia": ("acacia_planks", "stripped_acacia_log"),
    "dark_oak": ("dark_oak_planks", "stripped_dark_oak_log"),
    "mangrove": ("mangrove_planks", "stripped_mangrove_log"),
    "cherry": ("cherry_planks", "stripped_cherry_log"),
    "bamboo": ("bamboo_planks", "stripped_bamboo_block"),
    "crimson": ("crimson_planks", "stripped_crimson_stem"),
    "warped": ("warped_planks", "stripped_warped_stem"),
}

#: What each wood is called in an item's name.
NAMES = {
    "oak": "Oak", "spruce": "Spruce", "birch": "Birch", "jungle": "Jungle", "acacia": "Acacia",
    "dark_oak": "Dark Oak", "mangrove": "Mangrove", "cherry": "Cherry", "bamboo": "Bamboo",
    "crimson": "Crimson", "warped": "Warped",
}

#: Each wooden thing: the block models that belong to it, and what to call one in another wood. Which wood
#: it is already drawn in is not written here - it is read from the Java enum that registers them, below,
#: because the two disagreeing is a block with no model, which is what happened: the chair is oak and the
#: rest are dark oak, this file said otherwise, and oak_chair drew as the missing model.
KINDS = {
    "table": {"models": ["table_top", "table_corner"], "name": "%s Table"},
    "chair": {"models": ["chair"], "name": "%s Chair"},
    "shop_counter": {"models": ["shop_counter"], "name": "%s Shop Counter"},
    "collection": {"models": ["collection"], "name": "%s Collection"},
    "scorekeepers_desk": {"models": ["scorekeepers_desk"], "name": "%s Scorekeeper's Desk"},
    "display_case": {"models": ["display_case", "display_case_left", "display_case_right",
                                "display_case_middle"], "name": "%s Display Case"},
}

#: Where the woods and the plain woods are declared, once, for both sides.
CONTENT = ROOT / "common/src/main/java/dev/gathering/item/GatheringContent.java"


def plainWoods() -> dict:
    """The wood each thing is already drawn in, read from GatheringContent.Woodwork."""
    import re
    source = CONTENT.read_text(encoding="utf-8")
    woods = {}
    for _, idName, wood in re.findall(r'(\w+)\((\w+_ID), "(\w+)"\)', source):
        plainId = re.search(r'String ' + idName + r' = "([a-z_]+)"', source)
        if plainId:
            woods[plainId.group(1)] = wood
    if sorted(woods) != sorted(KINDS):
        raise SystemExit("the things in GatheringContent.Woodwork are " + str(sorted(woods))
                         + ", not " + str(sorted(KINDS)))
    return woods

written = []
unchanged = []


def put(path: pathlib.Path, content: str, check: bool):
    """Writes a file, or says it would, and keeps the count."""
    before = path.read_text(encoding="utf-8") if path.exists() else None
    if before == content:
        unchanged.append(path)
        return
    written.append(path)
    if not check:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")


def asJson(value) -> str:
    return json.dumps(value, indent=2) + "\n"


def swapped(value, wood: str, plain: str):
    """The same JSON with one wood's textures in place of another's, wherever they are named."""
    plainPlanks, plainLog = WOODS[plain]
    planks, log = WOODS[wood]
    if isinstance(value, dict):
        return {key: swapped(inner, wood, plain) for key, inner in value.items()}
    if isinstance(value, list):
        return [swapped(inner, wood, plain) for inner in value]
    if isinstance(value, str):
        return (value
                .replace("minecraft:block/" + plainPlanks, "minecraft:block/" + planks)
                .replace("minecraft:block/" + plainLog, "minecraft:block/" + log))
    return value


def named(value, plainId: str, newId: str):
    """The same JSON pointing at another block's own models, loot and result."""
    if isinstance(value, dict):
        return {key: named(inner, plainId, newId) for key, inner in value.items()}
    if isinstance(value, list):
        return [named(inner, plainId, newId) for inner in value]
    if isinstance(value, str):
        for prefix in ("gathering:block/", "gathering:item/", "gathering:"):
            if value.startswith(prefix) and value[len(prefix):].split("/")[0] == plainId:
                return prefix + newId + value[len(prefix) + len(plainId):]
        return value
    return value


def modelName(model: str, plainId: str, newId: str) -> str:
    """The name of a block model that belongs to this block, in another wood."""
    return newId + model[len(plainId):] if model.startswith(plainId) else newId + "_" + model


def write(check: bool):
    # Read in order and add at the end: the language file is written by hand and sorting it would be a
    # thousand-line change nobody asked for.
    lang = json.loads(LANG.read_text(encoding="utf-8"), object_pairs_hook=__import__("collections").OrderedDict)
    plain = plainWoods()
    for plainId, kind in KINDS.items():
        plainWood = plain[plainId]
        for wood in WOODS:
            if wood == plainWood:
                continue
            newId = wood + "_" + plainId

            # The models this block is drawn from, in the new wood.
            for model in kind["models"]:
                source = json.loads((ASSETS / f"models/block/{model}.json").read_text(encoding="utf-8"))
                put(ASSETS / f"models/block/{modelName(model, plainId, newId)}.json",
                    asJson(swapped(source, wood, plainWood)), check)

            # Its blockstate, pointing at those models.
            states = json.loads((ASSETS / f"blockstates/{plainId}.json").read_text(encoding="utf-8"))
            for model in kind["models"]:
                states = named(states, model, modelName(model, plainId, newId))
            put(ASSETS / f"blockstates/{newId}.json", asJson(states), check)

            # The item, which is either a model of its own or a pointer at the block's.
            item = json.loads((ASSETS / f"models/item/{plainId}.json").read_text(encoding="utf-8"))
            item = swapped(item, wood, plainWood)
            for model in kind["models"]:
                item = named(item, model, modelName(model, plainId, newId))
            put(ASSETS / f"models/item/{newId}.json", asJson(item), check)

            # What it drops, which is itself. The collection has none: what it drops is what is in it,
            # and the block decides that in code.
            lootFile = DATA / f"gathering/loot_table/blocks/{plainId}.json"
            if lootFile.exists():
                loot = json.loads(lootFile.read_text(encoding="utf-8"))
                put(DATA / f"gathering/loot_table/blocks/{newId}.json",
                    asJson(named(loot, plainId, newId)), check)

            # And how it is made, out of this wood's planks. The shop counter has no recipe: it is the
            # village's, and a player takes one rather than making one.
            recipeFile = DATA / f"gathering/recipe/{plainId}.json"
            if recipeFile.exists():
                recipe = json.loads(recipeFile.read_text(encoding="utf-8"))
                recipe = named(recipe, plainId, newId)
                for slot in recipe.get("key", {}).values():
                    if slot.get("item", "").startswith("minecraft:") and slot["item"].endswith("_planks"):
                        slot["item"] = "minecraft:" + WOODS[wood][0]
                put(DATA / f"gathering/recipe/{newId}.json", asJson(recipe), check)

            lang["block.gathering." + newId] = kind["name"] % NAMES[wood]

        # And the plain one, named for the wood it is actually drawn in. It used to be called just
        # "Table" or "Chair" while its ten siblings said which wood they were, so the one on the shelf
        # next to Spruce Table and Birch Table was the only one that did not say - the owner asked for
        # the wood on all of them (2026-09-16). Written here rather than by hand, because the wood it
        # is drawn in is read from the Java a few lines above and the two can then never disagree.
        lang["block.gathering." + plainId] = kind["name"] % NAMES[plainWood]

    # Every one of them is chopped with an axe, like the plain ones.
    axe = json.loads((DATA / "minecraft/tags/block/mineable/axe.json").read_text(encoding="utf-8"))
    values = [value for value in axe["values"] if ":" not in value or value.startswith("gathering:")]
    for plainId in KINDS:
        for wood in WOODS:
            if wood != plain[plainId]:
                values.append("gathering:" + wood + "_" + plainId)
    axe["values"] = sorted(set(values), key=lambda value: (value.count("_"), value))
    put(DATA / "minecraft/tags/block/mineable/axe.json", asJson(axe), check)

    put(LANG, json.dumps(lang, indent=2, ensure_ascii=False) + "\n", check)


def main():
    check = "--check" in sys.argv
    write(check)
    print(f"{len(written)} file(s) {'would change' if check else 'written'}, {len(unchanged)} already current")
    return 1 if check and written else 0


if __name__ == "__main__":
    sys.exit(main())
