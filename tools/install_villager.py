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

# drawn here -> shipped here
PAIRS = [
    ("art/villager/shopkeeper.png", ASSETS / "villager/profession/shopkeeper.png"),
    ("art/villager/shopkeeper_zombie.png", ASSETS / "zombie_villager/profession/shopkeeper.png"),
]


def main():
    moved, problems = 0, []
    for source, target in PAIRS:
        drawn = Path(source)
        if not drawn.is_file():
            continue
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
