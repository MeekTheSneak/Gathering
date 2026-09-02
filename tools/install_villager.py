#!/usr/bin/env python3
"""Copies the villager textures from where they are drawn to where the game reads them.

art/villager/ is the working folder; the game only ever looks under assets/. GitHub's web
uploader cannot create a file deep in a path that does not exist yet, so a new drawing lands
in art/ and then nothing happens in the world - which looks exactly like a texture that did
not work, and has cost this project three round trips already.

    python3 tools/install_villager.py

Refuses anything that is not a 64x64 RGBA image, because the wrong size is silently wrong:
the game stretches whatever it finds across the same UVs and the result is a smeared villager
rather than an error.
"""
import shutil
import sys
from pathlib import Path

from PIL import Image

ASSETS = Path("common/src/main/resources/assets/gathering/textures/entity")
DRAWN = Path("art/villager")


def found(words, without=()):
    """The drawing whose name contains all of these words, however it was capitalised.

    By what is in the name rather than by an exact filename, because these arrive from
    whoever drew them and "ShopkeeperZombie.png", "shopkeeper_zombie.png" and
    "zombie shopkeeper.png" are all obviously the same intent. A rule that only accepts one
    spelling turns a drawing into a silent no-op, which is the exact failure this script was
    written to stop.
    """
    for path in sorted(DRAWN.glob("*.png")):
        name = path.stem.lower().replace("_", "").replace("-", "").replace(" ", "")
        if all(word in name for word in words) and not any(
                word in name for word in (*without, "uv")):
            return path
    return None


def main():
    # drawn here -> shipped here. The living one is the shopkeeper that is not the zombie,
    # said as an exclusion rather than by picking the first match: "ShopkeeperZombie.png"
    # sorts before "shopkeeper.png", so first-match found the zombie and the living drawing
    # was quietly never installed.
    pairs = [
        (found(("shopkeeper",), without=("zombie",)),
         ASSETS / "villager/profession/shopkeeper.png"),
        (found(("shopkeeper", "zombie")),
         ASSETS / "zombie_villager/profession/shopkeeper.png"),
    ]

    moved, problems = 0, []
    for drawn, target in pairs:
        if drawn is None or not drawn.is_file():
            continue
        source = str(drawn)
        image = Image.open(drawn)
        if image.size != (64, 64):
            problems.append(f"{source} is {image.size[0]}x{image.size[1]}, and has to be 64x64")
            continue
        if image.mode != "RGBA":
            problems.append(f"{source} is {image.mode}, and has to be RGBA - a profession"
                            " texture is an overlay and needs real transparency")
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        same = target.is_file() and target.read_bytes() == drawn.read_bytes()
        shutil.copyfile(drawn, target)
        print(f"{'unchanged' if same else 'installed'}  {source} -> {target}")
        moved += 0 if same else 1

    for problem in problems:
        print(f"refused: {problem}", file=sys.stderr)
    if problems:
        return 1
    print(f"{moved} texture(s) changed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
