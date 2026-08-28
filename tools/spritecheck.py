#!/usr/bin/env python3
"""Checks that every drawn element has art, in every theme.

Three lists have to agree or a screen draws the missing-texture checkerboard:
the ``Element`` enum in ``GatheringSprites``, the ``ELEMENTS`` table in
``tools/gui_art.py``, and the PNGs on disk. Nothing about a missing sprite is
loud at runtime - Minecraft draws purple and carries on - so it is checked here
instead.

The default theme has to be complete, because it is what every other theme falls
back to. Other themes may leave elements out on purpose; those are reported as a
note rather than as a failure, so a half-painted theme is visible without being
an error.

Run it from the repo root:  python3 tools/spritecheck.py
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(
    ROOT, "common", "src", "main", "java", "dev", "gathering", "client")
SPRITES = os.path.join(
    ROOT, "common", "src", "main", "resources", "assets", "gathering",
    "textures", "gui", "sprites")


def elements_in_java():
    text = open(os.path.join(JAVA, "GatheringSprites.java")).read()
    body = text.split("public enum Element {", 1)[1].split("\n        private final String name;", 1)[0]
    return [name for name in re.findall(r'^\s*[A-Z][A-Z0-9_]*\("([a-z0-9_]+)"\)', body, re.M)]


def elements_in_generator():
    text = open(os.path.join(ROOT, "tools", "gui_art.py")).read()
    body = text.split("ELEMENTS = [", 1)[1].split("\n]", 1)[0]
    return re.findall(r'^\s*\("([a-z0-9_]+)",', body, re.M)


def themes_in_java():
    text = open(os.path.join(JAVA, "GuiTheme.java")).read()
    themes = re.findall(r'^\s*[A-Z][A-Z0-9_]*\("([a-z0-9_]+)"\)', text, re.M)
    default = re.search(r"DEFAULT\s*=\s*([A-Z_]+);", text).group(1)
    order = re.findall(r'^\s*([A-Z][A-Z0-9_]*)\("[a-z0-9_]+"\)', text, re.M)
    return themes, themes[order.index(default)]


def main():
    problems = []
    notes = []

    java = elements_in_java()
    generator = elements_in_generator()
    themes, default = themes_in_java()

    if java != generator:
        only_java = [name for name in java if name not in generator]
        only_generator = [name for name in generator if name not in java]
        for name in only_java:
            problems.append(f"{name} is an Element but tools/gui_art.py never paints it")
        for name in only_generator:
            problems.append(f"tools/gui_art.py paints {name} but nothing draws it")
        if not only_java and not only_generator:
            problems.append("the element lists hold the same names in a different order")

    for theme in themes:
        folder = os.path.join(SPRITES, theme)
        if not os.path.isdir(folder):
            problems.append(f"the {theme} theme has no folder of art")
            continue
        missing = [name for name in java
                   if not os.path.isfile(os.path.join(folder, name + ".png"))]
        no_meta = [name for name in java
                   if os.path.isfile(os.path.join(folder, name + ".png"))
                   and not os.path.isfile(os.path.join(folder, name + ".png.mcmeta"))]
        for name in no_meta:
            problems.append(f"{theme}/{name}.png has no .mcmeta saying how it stretches")
        if theme == default:
            for name in missing:
                problems.append(f"the default theme is missing {name}.png")
        elif missing:
            notes.append(f"{theme} inherits {len(missing)} element(s) from {default}")

        spare = [entry[:-4] for entry in sorted(os.listdir(folder))
                 if entry.endswith(".png") and entry[:-4] not in java]
        for name in spare:
            notes.append(f"{theme}/{name}.png is art nothing draws")

    for note in notes:
        print(f"note: {note}")
    for problem in problems:
        print(f"error: {problem}", file=sys.stderr)
    if problems:
        return 1
    print(f"{len(java)} elements, {len(themes)} themes, all present")
    return 0


if __name__ == "__main__":
    sys.exit(main())
