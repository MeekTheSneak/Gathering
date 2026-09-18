#!/usr/bin/env python3
"""Draws the trophy's one face, which a tournament of more than four players leaves behind.

Art in this project is the owner's and this script does not change that: he asked for this one on
2026-09-18, along with the Mana Coin before it, to play with until he draws it himself. Kept as a
script rather than as a hand-placed grid of pixels so the thing it is made of is written down.

The trophy is a block. The cup - a plinth, a stem, a bowl and two handles - is built out of cuboids
in `models/block/trophy.json`, so what a texture has to be here is the metal those cuboids are made
of rather than a picture of a cup. The item in the hand is that same block seen small, which is why
there is no separate item sprite.

Drawn in **gray**, on purpose. Every trophy carries a color of its own, chosen when it is won, and
the game multiplies this picture by that color - so a picture painted in any color at all would come
out muddied by it. Gray lets the cup be gold, or green, or whatever the afternoon was.

    python3 tools/trophy.py            # writes the texture if it has changed
    python3 tools/trophy.py --check    # says whether it would change anything, writes nothing
"""
import pathlib
import struct
import sys
import zlib

ROOT = pathlib.Path(__file__).resolve().parent.parent

#: The trophy is a block, so the cup is built out of cuboids and wears one plain face on every side:
#: what a texture has to be here is metal rather than a picture of a cup. The item in the hand is that
#: same block seen small, which is why there is no separate item sprite.
METAL = ROOT / "common/src/main/resources/assets/gathering/textures/block/trophy.png"

#: A block face is sixteen pixels square, like everything else in the world.
SIZE = 16

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


def metal():
    """The block's one face: brushed metal, in gray, so the tint decides what metal it is."""
    rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            # A faint vertical grain, brighter toward the top left, the way the cup is lit.
            grain = ((x * 7 + y * 3) % 5) - 2
            shade = 0xC4 - (y * 3) + grain * 4
            level = max(0x6A, min(0xEE, shade))
            row.append((level, level, level, 255))
        rows.append(row)
    return rows


def main():
    checking = "--check" in sys.argv
    wanted = {METAL: written(metal())}
    changed = [path for path, bytes_ in wanted.items()
               if not path.is_file() or path.read_bytes() != bytes_]
    if not changed:
        print("trophy: unchanged")
        return 0
    if checking:
        print("trophy: would be written:", ", ".join(path.name for path in changed))
        return 1
    for path in changed:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(wanted[path])
    print("trophy: written " + ", ".join(path.name for path in changed))
    return 0


if __name__ == "__main__":
    sys.exit(main())
