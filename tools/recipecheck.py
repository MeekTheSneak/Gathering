#!/usr/bin/env python3
"""Every block can be made, and every recipe can be found.

Two things a block needs before a player in survival can have one, and neither of them is
visible from the block's own code:

1. **A recipe.** A block registered, modeled, named and given a loot table is still a creative
   tab entry until something can be laid out to make it. The shop counter was exactly that for
   its whole life, and nobody noticed because everything that tests it puts one down directly.
2. **An unlock.** A recipe with no advancement granting it never reaches the recipe book, so a
   player has to already know the pattern to use it - which, for a mod's own furniture, means
   reading a wiki. Vanilla grants each of its recipes from an advancement that fires when you
   first hold one of the ingredients; this does the same.

    python3 tools/recipecheck.py            say what is missing
    python3 tools/recipecheck.py --write    write the missing unlocks

Recipes themselves are never written here: what a block is made of is a decision.
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
BLOCKSTATES = ROOT / "common/src/main/resources/assets/gathering/blockstates"
RECIPES = ROOT / "common/src/main/resources/data/gathering/recipe"
UNLOCKS = ROOT / "common/src/main/resources/data/gathering/advancement/recipes"

#: Blocks a player is never meant to make. Each one needs a reason, because "it has no recipe"
#: and "it is not craftable" look identical from here.
NOT_CRAFTED = {}


def resultOf(recipe: dict) -> str:
    """What a recipe makes, as a plain id."""
    result = recipe.get("result")
    if isinstance(result, str):
        return result.split(":")[-1]
    if isinstance(result, dict):
        made = result.get("id") or result.get("item") or ""
        return made.split(":")[-1]
    return ""


def ingredientsOf(recipe: dict) -> list:
    """Every item a recipe can be started from, as ids, in the order they are written."""
    found = []

    def take(slot):
        if isinstance(slot, list):
            for one in slot:
                take(one)
        elif isinstance(slot, dict):
            if "item" in slot:
                found.append(slot["item"])
            elif "tag" in slot:
                found.append("#" + slot["tag"])
        elif isinstance(slot, str):
            found.append(slot)

    for key in ("key", "ingredients", "ingredient"):
        value = recipe.get(key)
        if isinstance(value, dict) and key == "key":
            for slot in value.values():
                take(slot)
        elif value is not None:
            take(value)
    return list(dict.fromkeys(found))


def unlockFor(name: str, recipe: dict) -> dict:
    """The advancement that puts this recipe in the book, in vanilla's own shape."""
    criteria = {}
    names = []
    for ingredient in ingredientsOf(recipe):
        criterion = "has_" + ingredient.lstrip("#").split(":")[-1]
        if criterion in criteria:
            continue
        item = {"tag": ingredient[1:]} if ingredient.startswith("#") else {"items": [ingredient]}
        criteria[criterion] = {
            "trigger": "minecraft:inventory_changed",
            "conditions": {"items": [item]},
        }
        names.append(criterion)
    criteria["has_the_recipe"] = {
        "trigger": "minecraft:recipe_unlocked",
        "conditions": {"recipe": "gathering:" + name},
    }
    names.append("has_the_recipe")
    return {
        "parent": "minecraft:recipes/root",
        "criteria": criteria,
        "requirements": [names],
        "rewards": {"recipes": ["gathering:" + name]},
    }


def main() -> int:
    write = "--write" in sys.argv
    recipes = {}
    for path in sorted(RECIPES.glob("*.json")):
        recipes[path.stem] = json.loads(path.read_text(encoding="utf-8"))

    made = {resultOf(recipe) for recipe in recipes.values()}
    uncraftable = sorted(
        path.stem for path in BLOCKSTATES.glob("*.json")
        if path.stem not in made and path.stem not in NOT_CRAFTED)

    granted = set()
    for path in UNLOCKS.rglob("*.json"):
        for recipe in json.loads(path.read_text(encoding="utf-8")).get("rewards", {}).get("recipes", []):
            granted.add(recipe.split(":")[-1])
    unfindable = sorted(name for name in recipes if name not in granted)

    for name in uncraftable:
        print("no recipe:  " + name)
    written = 0
    for name in unfindable:
        if not write:
            print("no unlock:  " + name)
            continue
        path = UNLOCKS / "decorations" / (name + ".json")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(unlockFor(name, recipes[name]), indent=2) + "\n", encoding="utf-8")
        written += 1

    print(f"{len(list(BLOCKSTATES.glob('*.json')))} blocks, {len(recipes)} recipes, "
          f"{len(uncraftable)} without a recipe, "
          + (f"{written} unlock(s) written" if write else f"{len(unfindable)} without an unlock"))
    # a check that finds nothing to check must fail rather than pass - DIALECT.md
    if not recipes or not list(BLOCKSTATES.glob("*.json")):
        print("recipecheck: no recipes or no blocks found, so nothing was checked")
        return 1
    return 1 if uncraftable or (unfindable and not write) else 0


if __name__ == "__main__":
    sys.exit(main())
