#!/usr/bin/env python3
"""Draws the trophy, which a tournament of more than four players leaves behind.

Art in this project is the owner's and this script does not change that: he asked for this one on
2026-09-18, along with the Mana Coin before it, to play with until he draws it himself. Kept as a
script rather than as a hand-placed grid of pixels so the thing it is made of is written down.

Drawn in **gray**, on purpose. Every trophy carries a color of its own, chosen when it is won, and
the game multiplies this picture by that color - so a picture painted in any color at all would come
out muddied by it. Gray lets the cup be gold, or green, or whatever the afternoon was.

The shape: a cup seen face on. A bowl with a lip, two handles, a stem and a plinth. Sixteen pixels is
not much room for a trophy, so the shapes are separated by shadow rather than by outline: an outline
at this size eats the bowl.

    python3 tools/trophy.py            # writes the texture if it has changed
    python3 tools/trophy.py --check    # says whether it would change anything, writes nothing
"""
import pathlib
import struct
import sys
import zlib

ROOT = pathlib.Path(__file__).resolve().parent.parent

TROPHY = ROOT / "common/src/main/resources/assets/gathering/textures/item/trophy.png"

#: An item texture is sixteen pixels square, like everything else in the hand.
SIZE = 16

#: The grays the cup is built from, lit from the upper left as every other item here is.
SHINE = (0xF2, 0xF2, 0xF2)
LIGHT = (0xD8, 0xD8, 0xD8)
BODY = (0xB4, 0xB4, 0xB4)
SHADE = (0x86, 0x86, 0x86)
DEEP = (0x5A, 0x5A, 0x5A)

#: The cup, row by row, drawn as a picture so the shape is read rather than computed.
#: " " nothing, "." deep, "-" shade, "#" body, "+" light, "*" shine.
CUP = [
    "                ",
    "   ##########   ",
    "  .+********-.  ",
    "  .+########-.  ",
    " .#.+######-.#. ",
    " .#.+######-.#. ",
    " .#..######..#. ",
    " .#-.-####-.-#. ",
    "  .-..####..-.  ",
    "    .-####-.    ",
    "     .####.     ",
    "      .##.      ",
    "      .##.      ",
    "    .-######-.  ",
    "   .+########-. ",
    "   .----------. ",
]

INK = {" ": None, ".": DEEP, "-": SHADE, "#": BODY, "+": LIGHT, "*": SHINE}


def pixels():
    """Every pixel of the trophy, as (red, green, blue, alpha)."""
    rows = []
    for line in CUP:
        row = []
        for mark in line.ljust(SIZE)[:SIZE]:
            color = INK[mark]
            row.append((0, 0, 0, 0) if color is None else color + (255,))
        rows.append(row)
    return rows


def written(rows):
    """Those pixels as the bytes of a png."""
    raw = b""
    for row in rows:
        raw += b"\x00" + b"".join(bytes(pixel) for pixel in row)

    def chunk(kind, body):
        return (struct.pack(">I", len(body)) + kind + body
                + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF))

    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9))
            + chunk(b"IEND", b""))


def main():
    checking = "--check" in sys.argv
    drawn = written(pixels())
    was = TROPHY.read_bytes() if TROPHY.is_file() else b""
    if drawn == was:
        print("trophy: unchanged")
        return 0
    if checking:
        print("trophy: would be written")
        return 1
    TROPHY.parent.mkdir(parents=True, exist_ok=True)
    TROPHY.write_bytes(drawn)
    print(f"trophy: written, {len(drawn)} bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
