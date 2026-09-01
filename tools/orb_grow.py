#!/usr/bin/env python3
"""Grows the mana badges from a thirty-pixel disc to a thirty-one pixel one, once.

A disc thirty pixels across has no middle pixel: its centre is the corner where four of them
meet, half a pixel down and right of where every mark's own centre sits. So a mark drawn
symmetrically about its middle pixel lands half a pixel off the badge it is pressed into, and
there is no way to draw one that does not - which is what made putting symbols on these hard.

Thirty-one has a middle. This inserts one column and one row at the badge's centre, which
turns the even disc into an odd one without resampling anything: every colour stays exactly
the colour it was, and the only cost is that the badge's centre column and row are drawn
twice. On a smoothly lit sphere that is invisible, and it is the standard way to change a
circle's parity in pixel art - the alternative, scaling 32 to 33, would turn eight flat
colours into hundreds.

The marks are padded rather than split: their ink is already centred on pixel sixteen, which
is exactly where the grown disc's centre lands, so leaving them alone is what puts the two in
register for the first time.

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

#: The last column of the disc's left half. The duplicate goes in after it, so the grown disc
#: runs from 1 to 31 and its middle is pixel 16 - the pixel every mark is already drawn about.
SEAM = 15


def grown(image):
    """The badge with its centre column and row drawn twice."""
    out = Image.new("RGBA", (NOW, NOW), (0, 0, 0, 0))
    # Left of the seam, then the seam again, then the rest - in both axes. Done as four
    # pastes rather than pixel by pixel so the colours cannot be touched at all.
    out.paste(image.crop((0, 0, SEAM + 1, SEAM + 1)), (0, 0))
    out.paste(image.crop((SEAM, 0, WAS, SEAM + 1)), (SEAM + 1, 0))
    out.paste(image.crop((0, SEAM, SEAM + 1, WAS)), (0, SEAM + 1))
    out.paste(image.crop((SEAM, SEAM, WAS, WAS)), (SEAM + 1, SEAM + 1))
    return out


def padded(image):
    """The mark in a bigger canvas, its ink exactly where it was."""
    out = Image.new("RGBA", (NOW, NOW), (0, 0, 0, 0))
    out.paste(image, (0, 0))
    return out


def discOf(image):
    """The opaque bounding box, as (left, top, right, bottom) inclusive."""
    px = image.load()
    ink = [(x, y) for y in range(image.size[1]) for x in range(image.size[0]) if px[x, y][3]]
    if not ink:
        return None
    xs = [p[0] for p in ink]
    ys = [p[1] for p in ink]
    return min(xs), min(ys), max(xs), max(ys)


def walk(root):
    for here, _, files in os.walk(root):
        for name in sorted(files):
            if name.endswith(".png"):
                yield os.path.join(here, name)


def main():
    if not os.path.isdir(BADGES):
        raise SystemExit("run this from the repository root")

    done = 0
    for path in walk(BADGES):
        image = Image.open(path).convert("RGBA")
        if image.size == (NOW, NOW):
            continue
        if image.size != (WAS, WAS) or discOf(image) != (1, 1, WAS - 2, WAS - 2):
            raise SystemExit(f"{path} is not a {WAS}x{WAS} badge with a {WAS - 2}px disc")
        out = grown(image)
        box = discOf(out)
        if box != (1, 1, NOW - 2, NOW - 2):
            raise SystemExit(f"{path} grew to {box}, which is not centred")
        out.save(path)
        done += 1

    marks = 0
    for path in walk(MARKS):
        image = Image.open(path).convert("RGBA")
        if image.size == (NOW, NOW):
            continue
        if image.size != (WAS, WAS):
            raise SystemExit(f"{path} is not {WAS}x{WAS}")
        padded(image).save(path)
        marks += 1

    print(f"grew {done} badges to {NOW}x{NOW} with a {NOW - 2}px disc,"
          f" and padded {marks} marks to match")
    return 0


if __name__ == "__main__":
    sys.exit(main())
