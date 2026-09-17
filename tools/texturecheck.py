#!/usr/bin/env python3
"""Every texture a model asks for exists, every texture that ships is asked for, and what is
dyed says it is dyed.

Two failures, both silent until somebody looks at the right pixel.

A model naming a texture that is not there draws the purple checkerboard, and only where that
item happens to be on screen. Data generation catches it for the models it writes and says
nothing about the hand-authored ones.

A texture nothing names is dead weight that reads as art somebody forgot to wire up. This is
the direction that has actually gone wrong: item/deck.png was deleted as unreferenced because
the search covered the checked-in models and not the generated ones, and it took a datagen run
to notice - the model that names it is written by the generator into src/generated.

The third is the same kind of silence from the other end. A face is tinted by a color handler
only where the model puts a tintindex on it, so a model redrawn without one loses its color and
nothing anywhere fails - the item simply comes out white. That is not hypothetical: a furniture
package arrived with the deck box redrawn and no tintindex, and every deck in the world went
white until somebody looked at a picture. So the models whose faces are tinted from code are
named below, and each one has to keep at least one face saying so.

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
    # The advancement tab's background. The tables' tops were this until the owner asked for white
    # wool, and it is still the cloth behind the mod's advancements.
    "block/table_felt.png": "common/src/main/resources/data/gathering/advancement/root.json",
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


#: Where Minecraft's own textures can be read, once the NeoForge build has unpacked them.
VANILLA_JARS = "neoforge/build/moddev/artifacts/*-client-extra-aka-minecraft-resources.jar"


def vanillaNamedByModels():
    """Every minecraft:-namespaced texture a model asks for, which are most of them."""
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
                for value in (model.get("textures") or {}).values():
                    if not isinstance(value, str) or value.startswith("#"):
                        continue
                    if value.startswith("minecraft:") or ":" not in value:
                        bare = value.split(":", 1)[-1]
                        named.setdefault("assets/minecraft/textures/" + bare + ".png", []).append(
                            os.path.relpath(path, ROOT))
    return named


def vanillaTexturesThatAreNotThere():
    """Minecraft textures models name that this version of Minecraft does not have.

    These were skipped entirely, five hundred and more of them: a model asking for a vanilla
    texture that was renamed between versions draws the purple and black checkerboard, and
    nothing said so. Read against the game's own resources, when a build has unpacked them."""
    import glob
    import zipfile
    jars = sorted(glob.glob(os.path.join(ROOT, VANILLA_JARS)))
    if not jars:
        return [], False
    with zipfile.ZipFile(jars[-1]) as jar:
        have = set(jar.namelist())
    missing = []
    for texture, models in sorted(vanillaNamedByModels().items()):
        if texture not in have:
            missing.append(f"{texture.replace('assets/minecraft/textures/', 'minecraft:')[:-len('.png')]} is named by "
                           f"{models[0]} and Minecraft has no such texture")
    return missing, True


def texturesOnDisk():
    found = set()
    folder = os.path.join(ROOT, TEXTURES)
    for where, _, files in os.walk(folder):
        for name in files:
            if name.endswith(".png"):
                found.add(os.path.relpath(os.path.join(where, name), folder))
    return found


#: Models whose faces are colored by a handler in code, and what colors them. A model here with no
#: tinted face at all is a thing that has quietly stopped being dyeable.
TINTED = {
    "item/deck_box.json": "DeckItem.tintOf",
}


def modelsMissingTheirTint():
    """The tinted models that no longer say any face is tinted."""
    missing = []
    for model, by in sorted(TINTED.items()):
        found = None
        for root in MODEL_ROOTS:
            path = os.path.join(ROOT, root, model)
            if os.path.exists(path):
                found = path
                break
        if found is None:
            missing.append(f"{model} is tinted by {by} and is not there")
            continue
        drawn = json.load(open(found, encoding="utf-8"))
        tinted = any("tintindex" in face
                     for element in drawn.get("elements", [])
                     for face in element.get("faces", {}).values())
        if not tinted:
            missing.append(f"{model} is colored by {by} and no face of it says tintindex")
    return missing


#: Textures drawn at one texel to a pixel of model, wherever a face uses them. Glass is a grid of
#: panes with a frame line round its edge; squeezed into a face of another shape the lines thicken
#: on one axis, which is how the display case's sides came to look stretched.
UNSTRETCHED = ("minecraft:block/glass",)

#: Which two axes of an element a face of each direction spans, as indexes into from and to.
FACE_AXES = {"north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2)}


def stretchedFaces():
    """Faces drawing an unstretched texture with a uv of a different size from the face."""
    stretched = []
    for root in MODEL_ROOTS:
        base = os.path.join(ROOT, root)
        for folder, _, files in os.walk(base):
            for name in sorted(files):
                if not name.endswith(".json"):
                    continue
                path = os.path.join(folder, name)
                model = json.load(open(path, encoding="utf-8"))
                textures = model.get("textures", {})
                for element in model.get("elements", []):
                    for direction, face in element.get("faces", {}).items():
                        texture = face.get("texture", "")
                        resolved = textures.get(texture[1:], texture) if texture.startswith("#") else texture
                        uv = face.get("uv")
                        if resolved not in UNSTRETCHED or uv is None or direction not in FACE_AXES:
                            continue
                        across, down = FACE_AXES[direction]
                        wide = abs(element["to"][across] - element["from"][across])
                        tall = abs(element["to"][down] - element["from"][down])
                        if abs(abs(uv[2] - uv[0]) - wide) > 0.01 or abs(abs(uv[3] - uv[1]) - tall) > 0.01:
                            stretched.append(f"{os.path.relpath(path, ROOT)}: {element.get('name', 'an element')} "
                                             f"{direction} draws {resolved} {uv} over a face {wide}x{tall}")
    return stretched


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
        elif reached[:-len(".png")] not in open(os.path.join(ROOT, by)).read():
            # By the texture's own path, not the mod's name: every file that reaches a texture of
            # this mod says "gathering" somewhere, so that asked nothing about this texture.
            problems.append(f"{by} no longer names {reached[:-len('.png')]}, so nothing reaches {reached}")

    for texture in sorted(onDisk):
        if texture.replace("\\", "/").startswith(NOT_NAMED_BY_MODELS):
            continue
        if texture not in named and texture not in FROM_CODE:
            problems.append(f"{texture} is on disk and nothing names it")

    problems.extend(modelsMissingTheirTint())
    problems.extend(stretchedFaces())

    vanillaMissing, vanillaRead = vanillaTexturesThatAreNotThere()
    problems.extend(vanillaMissing)

    if not named or not onDisk:
        problems.append("no textures named by models or none on disk, so nothing was checked")

    for line in problems:
        print("  " + line)
    print(f"{len(named)} textures named by models, {len(onDisk)} on disk, "
          + (f"{len(vanillaNamedByModels())} of Minecraft's checked, " if vanillaRead
             else "Minecraft's own not checked (no build has unpacked them), ")
          + f"{len(problems)} problems")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
