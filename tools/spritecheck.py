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

import json
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


def themes_declared():
    """Every theme a file declares, and which folder of art each one names.

    Read from the JSON rather than from Java, because that is where a theme lives now: a
    pack adds one by adding a file, so a check that read a list in the source would be
    checking something nobody edits.
    """
    folder = os.path.join(
        ROOT, "common", "src", "main", "resources", "assets", "gathering", "gui_themes")
    found = {}
    if not os.path.isdir(folder):
        return found, None
    for entry in sorted(os.listdir(folder)):
        if not entry.endswith(".json"):
            continue
        said = json.load(open(os.path.join(folder, entry)))
        art = said.get("sprites", "gathering:" + entry[:-5])
        found[entry[:-5]] = art.split(":", 1)[-1]
    default = re.search(
        r'DEFAULT\s*=\s*\n?\s*ResourceLocation\.fromNamespaceAndPath\(\s*'
        r'Gathering\.MOD_ID,\s*GuiTheme\.DEFAULT_FOLDER\)',
        open(os.path.join(JAVA, "GuiThemes.java")).read())
    fallback = re.search(
        r'DEFAULT_FOLDER\s*=\s*"([a-z0-9_]+)"',
        open(os.path.join(JAVA, "GuiTheme.java")).read())
    return found, (fallback.group(1) if default and fallback else None)


#: The set that shows what every element is, rather than what it looks like.
TEMPLATE_FOLDER = "template"


def seen_through(where):
    """Whether any pixel of this file is fully transparent.

    Pillow is not a dependency of the build, only of the tool that paints the art, so a
    machine without it skips this one check rather than failing a build over it.
    """
    try:
        from PIL import Image
    except ImportError:
        return False
    with Image.open(where) as art:
        alpha = art.convert("RGBA").getchannel("A")
        return alpha.getextrema()[0] == 0


#: The label every button's words are set in, and the contrast they need against its face.
#: Both live in tools/gui_art.py as TEXT and LEAST_CONTRAST; kept in step by hand because
#: this script must run without importing the painter, which needs Pillow.
LABEL = (0xE8, 0xE4, 0xDC)
LEAST_CONTRAST = 4.2

#: How much of a face a tone must cover before a word is read against it.
SHARE_OF_THE_MIDDLE = 20


def faintest(where):
    """The worst contrast the label sits on, over the part of this sprite it sits on.

    The middle of the nine-slice and nothing else. A button's outline and its one-pixel
    bevel are the brightest and darkest things in the file and neither has a word on it - the
    label is drawn across the stretched middle, which is the only region this can honestly
    ask about. Measured against the tones that actually cover that middle rather than every
    tone in it: several of these faces are deliberately grained - Retro's varies by a few
    levels per pixel, on purpose - and one pixel in sixty-four sitting six percent under the
    floor is not a word anybody struggles with. A tone has to cover a twentieth of the middle
    before it counts, which still catches a lit band, a rule, or a whole face.
    """
    try:
        from PIL import Image
    except ImportError:
        return None
    left, top, right, bottom = borderOf(where + ".mcmeta")
    with Image.open(where) as art:
        rgba = art.convert("RGBA")
        read = rgba.load()
        middle = [read[x, y]
                  for y in range(top, max(top + 1, rgba.height - bottom))
                  for x in range(left, max(left + 1, rgba.width - right))]
    seen = {}
    for pixel in middle:
        if pixel[3] > 0x40:
            seen[pixel[:3]] = seen.get(pixel[:3], 0) + 1
    if not seen:
        return None
    enough = max(1, sum(seen.values()) // SHARE_OF_THE_MIDDLE)
    covering = [tone for tone, count in seen.items() if count >= enough]
    return min(contrast(LABEL, tone) for tone in covering or seen)


def borderOf(where):
    """A sprite's nine-slice border, as four sides. Nothing stretched has one."""
    if not os.path.isfile(where):
        return 0, 0, 0, 0
    said = json.load(open(where))["gui"]["scaling"]
    if said.get("type") != "nine_slice":
        return 0, 0, 0, 0
    edge = said["border"]
    if isinstance(edge, int):
        return edge, edge, edge, edge
    return edge["left"], edge["top"], edge["right"], edge["bottom"]


def luminance(color):
    channels = []
    for value in color:
        share = value / 255.0
        channels.append(share / 12.92 if share <= 0.03928
                        else ((share + 0.055) / 1.055) ** 2.4)
    return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]


def contrast(one, two):
    first, second = luminance(one), luminance(two)
    high, low = max(first, second), min(first, second)
    return (high + 0.05) / (low + 0.05)


def main():
    problems = []
    notes = []

    java = elements_in_java()
    generator = elements_in_generator()
    themes, default = themes_declared()
    if default is None:
        problems.append("GuiThemes does not name a default theme folder")
        default = ""
    if default and default not in themes.values():
        problems.append(f"no theme file declares the default folder '{default}'")

    if java != generator:
        only_java = [name for name in java if name not in generator]
        only_generator = [name for name in generator if name not in java]
        for name in only_java:
            problems.append(f"{name} is an Element but tools/gui_art.py never paints it")
        for name in only_generator:
            problems.append(f"tools/gui_art.py paints {name} but nothing draws it")
        if not only_java and not only_generator:
            problems.append("the element lists hold the same names in a different order")

    for theme, art in themes.items():
        folder = os.path.join(SPRITES, art)
        if not os.path.isdir(folder):
            problems.append(f"the {theme} theme names {art}, which has no folder of art")
            continue
        missing = [name for name in java
                   if not os.path.isfile(os.path.join(folder, name + ".png"))]
        no_meta = [name for name in java
                   if os.path.isfile(os.path.join(folder, name + ".png"))
                   and not os.path.isfile(os.path.join(folder, name + ".png.mcmeta"))]
        for name in no_meta:
            problems.append(f"{art}/{name}.png has no .mcmeta saying how it stretches")
        if art == default:
            for name in missing:
                problems.append(f"the default theme is missing {name}.png")
        elif missing:
            notes.append(f"{theme} inherits {len(missing)} element(s) from {default}")

        spare = [entry[:-4] for entry in sorted(os.listdir(folder))
                 if entry.endswith(".png") and entry[:-4] not in java]
        for name in spare:
            notes.append(f"{art}/{name}.png is art nothing draws")

    # The template set exists so nobody has to open a blank square to work out what an
    # element is or how big it may be. A see-through file in it is the one thing it must not
    # have, and the elements most worth a template - the tints and the washes - are exactly
    # the ones that are see-through everywhere else.
    template = os.path.join(SPRITES, TEMPLATE_FOLDER)
    if os.path.isdir(template):
        for name in java:
            where = os.path.join(template, name + ".png")
            if os.path.isfile(where) and seen_through(where):
                problems.append(
                    f"{TEMPLATE_FOLDER}/{name}.png has transparent pixels, so it opens as"
                    " nothing in an image editor")

    # And a button's face is dark enough for the words on it. The faces are cut off somebody
    # else's sheet and laid on each look's own ramp, and his are drawn to carry an icon: laid
    # on unchecked they came out between 2.2 and 3.5 against the label colour in every theme,
    # which is a button you can see and a word you cannot. tools/gui_art.py darkens them, and
    # this is what says it still does.
    faces = [name for name in java if name.startswith("button")]
    if faces:
        for theme, art in sorted(themes.items()):
            folder = os.path.join(SPRITES, art)
            if art == TEMPLATE_FOLDER:
                continue
            for name in faces:
                where = os.path.join(folder, name + ".png")
                if not os.path.isfile(where):
                    continue
                worst = faintest(where)
                if worst is not None and worst < LEAST_CONTRAST:
                    problems.append(
                        f"{art}/{name}.png has a face at {worst:.2f} against the label colour,"
                        f" under the {LEAST_CONTRAST} floor - the words on it cannot be read")

    # And the spinner really turns. Eight frames built in a loop is the shape of the oldest
    # Python mistake there is - a lambda closing over the loop variable instead of capturing
    # it - and the result is eight identical files, which look exactly like a spinner that
    # has stopped rather than like a bug.
    frames = [name for name in java if name.startswith("spinner_")]
    if frames:
        folder = os.path.join(SPRITES, default)
        seen = {}
        for name in frames:
            where = os.path.join(folder, name + ".png")
            if os.path.isfile(where):
                seen.setdefault(open(where, "rb").read(), []).append(name)
        same = [names for names in seen.values() if len(names) > 1]
        for names in same:
            problems.append(
                "the spinner does not turn: " + ", ".join(names) + " are the same picture")

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
