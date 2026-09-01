#!/usr/bin/env python3
"""Gives the mana badges and their marks a middle pixel, once.

Anything an even number of pixels across has no middle: its centre is the corner where four
of them meet. A thirty-pixel disc and a mark eighteen rows tall are both like that, and two
things with no middle cannot be lined up on one - which is why a symmetric symbol could never
be drawn to sit properly on one of these badges.

So both ends get an odd size, the same way and for the same reason:

  * a badge's disc grows from thirty to thirty-one, by drawing its centre column and row
    twice. Nothing is resampled, so every colour stays exactly the colour it was; the cost is
    one duplicated line through the middle, which on a smoothly lit sphere is invisible.
    Scaling 32 to 33 instead would turn eight flat colours into hundreds.
  * a mark whose ink is an even number of pixels wide or tall grows by one the same way, and
    is then placed on the middle pixel of its own canvas.

After this every full-size mark has a middle pixel and sits on the badge's, and
``tools/mana_art.py`` refuses to assemble one that does not.

The half marks a hybrid uses are left alone: those sit in one half of a badge rather than on
its centre, so the centre is not what they are lined up against.

Run once, by hand. The art on disk is the source of truth afterwards.

    python3 tools/orb_grow.py
"""
import os
import sys

from PIL import Image

BADGES = "art/mana/badges"
MARKS = "art/mana/symbols"

WAS = 32
NOW = 33

#: The middle pixel of the new canvas, which is what everything is lined up on.
MIDDLE = NOW // 2

#: The last column of the old disc's left half. Its duplicate goes in after it, so the grown
#: disc runs from 1 to 31 and its middle is the pixel above.
SEAM = 15


def inkBox(image):
    """The opaque bounding box, as (left, top, right, bottom) inclusive, or None."""
    px = image.load()
    ink = [(x, y) for y in range(image.size[1]) for x in range(image.size[0]) if px[x, y][3]]
    if not ink:
        return None
    xs = [p[0] for p in ink]
    ys = [p[1] for p in ink]
    return min(xs), min(ys), max(xs), max(ys)


def grownBadge(image):
    """The badge with its centre column and row drawn twice."""
    out = Image.new("RGBA", (NOW, NOW), (0, 0, 0, 0))
    # Four pastes rather than pixel by pixel, so the colours cannot be touched at all.
    out.paste(image.crop((0, 0, SEAM + 1, SEAM + 1)), (0, 0))
    out.paste(image.crop((SEAM, 0, WAS, SEAM + 1)), (SEAM + 1, 0))
    out.paste(image.crop((0, SEAM, SEAM + 1, WAS)), (0, SEAM + 1))
    out.paste(image.crop((SEAM, SEAM, WAS, WAS)), (SEAM + 1, SEAM + 1))
    return out


def odd(strip, horizontal):
    """The ink with its own middle line drawn twice, if it has no middle line."""
    width, height = strip.size
    span = width if horizontal else height
    if span % 2:
        return strip
    seam = span // 2 - 1
    out = Image.new("RGBA", (width + 1, height) if horizontal else (width, height + 1),
                    (0, 0, 0, 0))
    if horizontal:
        out.paste(strip.crop((0, 0, seam + 1, height)), (0, 0))
        out.paste(strip.crop((seam, 0, width, height)), (seam + 1, 0))
    else:
        out.paste(strip.crop((0, 0, width, seam + 1)), (0, 0))
        out.paste(strip.crop((0, seam, width, height)), (0, seam + 1))
    return out


def middled(image, path):
    """The mark given a middle pixel and stood on the canvas's."""
    box = inkBox(image)
    if box is None:
        return image
    left, top, right, bottom = box
    strip = odd(odd(image.crop((left, top, right + 1, bottom + 1)), True), False)
    if strip.size[0] > NOW or strip.size[1] > NOW:
        raise SystemExit(f"{path} is too big to centre once it has a middle pixel")
    out = Image.new("RGBA", (NOW, NOW), (0, 0, 0, 0))
    out.paste(strip, (MIDDLE - strip.size[0] // 2, MIDDLE - strip.size[1] // 2))
    return out


def walk(root, deep):
    for here, _, files in os.walk(root):
        if not deep and here != root:
            continue
        for name in sorted(files):
            if name.endswith(".png"):
                yield os.path.join(here, name)


def main():
    if not os.path.isdir(BADGES):
        raise SystemExit("run this from the repository root")

    badges = 0
    for path in walk(BADGES, True):
        image = Image.open(path).convert("RGBA")
        if image.size == (WAS, WAS):
            if inkBox(image) != (1, 1, WAS - 2, WAS - 2):
                raise SystemExit(f"{path} is not a {WAS}x{WAS} badge with a {WAS - 2}px disc")
            image = grownBadge(image)
            badges += 1
        box = inkBox(image)
        if image.size != (NOW, NOW) or box != (1, 1, NOW - 2, NOW - 2):
            raise SystemExit(f"{path} is {image.size} with ink at {box}, which is not a"
                             f" {NOW - 2}px disc on the middle pixel")
        image.save(path)

    # Only the full-size marks. A half mark sits in one half of a badge rather than on its
    # centre, so the centre is not the thing it is lined up against.
    marks = 0
    for path in walk(MARKS, False):
        image = Image.open(path).convert("RGBA")
        if image.size not in ((WAS, WAS), (NOW, NOW)):
            raise SystemExit(f"{path} is {image.size}, which is neither size")
        out = middled(image.crop((0, 0, NOW, NOW)) if image.size == (WAS, WAS) else image, path)
        if out.tobytes() != image.tobytes():
            marks += 1
        out.save(path)

    halves = 0
    for path in walk(MARKS, True):
        if os.path.dirname(path) == MARKS:
            continue
        image = Image.open(path).convert("RGBA")
        if image.size == (WAS, WAS):
            padded = Image.new("RGBA", (NOW, NOW), (0, 0, 0, 0))
            padded.paste(image, (0, 0))
            padded.save(path)
            halves += 1

    print(f"{badges} badges grown, {marks} marks given a middle pixel and centred,"
          f" {halves} half marks padded")
    return 0


if __name__ == "__main__":
    sys.exit(main())
