#!/usr/bin/env python3
"""Every texture a model asks for exists, and every texture that ships is asked for.

Two failures, both silent until somebody looks at the right pixel.

A model naming a texture that is not there draws the purple checkerboard, and only where that
item happens to be on screen. Data generation catches it for the models it writes and says
nothing about the hand-authored ones.

A texture nothing names is dead weight that reads as art somebody forgot to wire up. This is
the direction that has actually gone wrong: item/deck.png was deleted as unreferenced because
the search covered the checked-in models and not the generated ones, and it took a datagen run
to notice - the model that names it is written by the generator into src/generated.

Run from the repository root:

    python3 tools/texturecheck.py
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NAMESPACE = "gathering"

#: Where models live, generated and hand-authored alike.
MODEL_ROOTS = [
    "common/src/main/resources/assets/gathering/models",
    "common/src/generated/resources/assets/gathering/models",
    "neoforge/src/generated/resources/assets/gathering/models",
]

#: Where the textures those models name live.
TEXTURES = "common/src/main/resources/assets/gathering/textures"

#: Textures reached from code rather than from a model, with what reaches them.
#:
#: A renderer that builds a ResourceLocation by hand is a reference no model file records, so
#: the only honest way to know is to name them here and let the check confirm the code still
#: says so.
FROM_CODE = {
    "item/pack.png": "common/src/main/java/dev/gathering/client/PackFaceRenderer.java",
}

#: Whole trees that are not named one at a time.
#:
#: The GUI sprite atlas is loaded wholesale by the game and checked by spritecheck.py; card
#: faces, the mana font and the villager skins are all reached by paths built at runtime.
NOT_NAMED_BY_MODELS = ("gui/", "card/", "font/", "entity/")


def texturesNamedByModels():
    """Every gathering:-namespaced texture any model asks for."""
    named = {}
    for root in MODEL_ROOTS:
        folder = os.path.join(ROOT, root)
        for where, _, files in os.walk(folder):
            for name in files:
                if not name.endswith(".json"):
                    continue
                path = os.path.join(where, name)
                with open(path) as handle:
                    model = json.load(handle)
                for key, value in (model.get("textures") or {}).items():
                    if not isinstance(value, str) or not value.startswith(NAMESPACE + ":"):
                        continue
                    named.setdefault(value[len(NAMESPACE) + 1:] + ".png", []).append(
                        os.path.relpath(path, ROOT))
    return named


def texturesOnDisk():
    found = set()
    folder = os.path.join(ROOT, TEXTURES)
    for where, _, files in os.walk(folder):
        for name in files:
            if name.endswith(".png"):
                found.add(os.path.relpath(os.path.join(where, name), folder))
    return found


def main():
    named = texturesNamedByModels()
    onDisk = texturesOnDisk()
    problems = []

    for texture, models in sorted(named.items()):
        if texture not in onDisk:
            problems.append(f"{texture} is named by {models[0]} and is not there")

    for reached, by in sorted(FROM_CODE.items()):
        if reached not in onDisk:
            problems.append(f"{reached} is reached from {by} and is not there")
        elif NAMESPACE not in open(os.path.join(ROOT, by)).read():
            problems.append(f"{by} no longer looks like it reaches {reached}")

    for texture in sorted(onDisk):
        if texture.replace("\\", "/").startswith(NOT_NAMED_BY_MODELS):
            continue
        if texture not in named and texture not in FROM_CODE:
            problems.append(f"{texture} is on disk and nothing names it")

    for line in problems:
        print("  " + line)
    print(f"{len(named)} textures named by models, {len(onDisk)} on disk, "
          f"{len(problems)} problems")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
