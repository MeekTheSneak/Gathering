#!/usr/bin/env python3
"""Gives the mana badges a middle pixel, once.

A disc thirty pixels across has no middle: its centre is the corner where four of them meet.
So there was no pixel to draw a symbol around, and no way to say where a symbol should go
except by eye.

Thirty-one has a middle. This inserts one column and one row at each badge's centre, which
turns the even disc into an odd one without resampling anything - every colour stays exactly
the colour it was, and the cost is that the centre column and row are drawn twice. On a
smoothly lit sphere that is invisible, and it is the standard way to change a circle's parity
in pixel art; scaling 32 to 33 instead would turn eight flat colours into hundreds.

The marks are only padded to the new canvas. Their ink is left exactly as drawn - where a
mark sits in its own canvas stopped mattering when tools/mana_art.py started standing it on
the badge's middle pixel itself.

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

#: The last column of the old disc's left half. Its duplicate goes in after it, so the grown
#: disc runs from 1 to 31 and its middle is pixel 16.
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


def grown(image):
    """The badge with its centre column and row drawn twice."""
    out = Image.new("RGBA", (NOW, NOW), (0, 0, 0, 0))
    # Four pastes rather than pixel by pixel, so the colours cannot be touched at all.
    out.paste(image.crop((0, 0, SEAM + 1, SEAM + 1)), (0, 0))
    out.paste(image.crop((SEAM, 0, WAS, SEAM + 1)), (SEAM + 1, 0))
    out.paste(image.crop((0, SEAM, SEAM + 1, WAS)), (0, SEAM + 1))
    out.paste(image.crop((SEAM, SEAM, WAS, WAS)), (SEAM + 1, SEAM + 1))
    return out


def padded(image):
    """The mark in the bigger canvas, its ink exactly where it was."""
    out = Image.new("RGBA", (NOW, NOW), (0, 0, 0, 0))
    out.paste(image, (0, 0))
    return out


def walk(root):
    for here, _, files in os.walk(root):
        for name in sorted(files):
            if name.endswith(".png"):
                yield os.path.join(here, name)


def main():
    if not os.path.isdir(BADGES):
        raise SystemExit("run this from the repository root")

    badges = 0
    for path in walk(BADGES):
        image = Image.open(path).convert("RGBA")
        if image.size == (WAS, WAS):
            if inkBox(image) != (1, 1, WAS - 2, WAS - 2):
                raise SystemExit(f"{path} is not a {WAS}x{WAS} badge with a {WAS - 2}px disc")
            image = grown(image)
            image.save(path)
            badges += 1
        if image.size != (NOW, NOW) or inkBox(image) != (1, 1, NOW - 2, NOW - 2):
            raise SystemExit(f"{path} is not a {NOW - 2}px disc on the middle pixel")

    marks = 0
    for path in walk(MARKS):
        image = Image.open(path).convert("RGBA")
        if image.size == (WAS, WAS):
            padded(image).save(path)
            marks += 1

    print(f"grew {badges} badges to {NOW}x{NOW} with a {NOW - 2}px disc,"
          f" and padded {marks} marks to match")
    return 0


if __name__ == "__main__":
    sys.exit(main())
