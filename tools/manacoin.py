#!/usr/bin/env python3
"""Draws the Mana Coin, the shop's currency.

Art in this project is the owner's and this script does not change that: he asked for this one on
2026-09-17 - "just a swirl of the mana orbs that we have without any of the icons" - to play with
until he draws it himself. Kept as a script rather than as a hand-placed grid of pixels so the thing
it is made of is written down: the five colors are read out of the mod's own mana orbs
(`assets/gathering/textures/font/mana/{w,u,b,r,g}.png`) rather than picked, so the coin and the orbs
on a card are the same five colors.

The shape: a struck coin seen face on. A dark rim, a lighter bevel inside it, and the five colors
swirling out of the middle - each one a fifth of the turn, the whole pattern twisted with the radius
so it reads as a swirl rather than as a pie. White at the top and clockwise from there, which is the
order the color pie has been drawn in since the back of the card.

    python3 tools/manacoin.py            # writes the texture if it has changed
    python3 tools/manacoin.py --check    # says whether it would change anything, writes nothing
"""
import math
import pathlib
import struct
import sys
import zlib

ROOT = pathlib.Path(__file__).resolve().parent.parent

ORBS = ROOT / "common/src/main/resources/assets/gathering/textures/font/mana"

COIN = ROOT / "common/src/main/resources/assets/gathering/textures/item/mana_coin.png"

#: An item texture is sixteen pixels square, like everything else in the hand.
SIZE = 16

#: Clockwise from the top, which is the order the five are always drawn in.
ORDER = "wubrg"

#: How far the swirl twists between the middle and the rim, in turns.
#: Two thirds: enough that every band curves plainly at sixteen pixels, and not so much that a band
#: wraps past the one behind it and the coin reads as rings rather than as a swirl.
TWIST = 0.66


def readPng(path):
    """One RGBA png as rows of bytes. Only what this script writes and the orbs use: 8-bit RGBA."""
    data = path.read_bytes()
    at = 8
    pixels = b""
    width = height = None
    while at < len(data):
        length = struct.unpack(">I", data[at:at + 4])[0]
        kind = data[at + 4:at + 8]
        chunk = data[at + 8:at + 8 + length]
        at += 12 + length
        if kind == b"IHDR":
            width, height, depth, color = struct.unpack(">IIBB", chunk[:10])
            if depth != 8 or color != 6:
                raise SystemExit(f"{path} is not 8-bit RGBA")
        elif kind == b"IDAT":
            pixels += chunk
    raw = zlib.decompress(pixels)
    stride = width * 4
    rows = []
    previous = bytearray(stride)
    at = 0
    for _ in range(height):
        filtering = raw[at]
        at += 1
        line = bytearray(raw[at:at + stride])
        at += stride
        for x in range(stride):
            left = line[x - 4] if x >= 4 else 0
            up = previous[x]
            corner = previous[x - 4] if x >= 4 else 0
            if filtering == 1:
                line[x] = (line[x] + left) & 255
            elif filtering == 2:
                line[x] = (line[x] + up) & 255
            elif filtering == 3:
                line[x] = (line[x] + (left + up) // 2) & 255
            elif filtering == 4:
                guess = left + up - corner
                a, b, c = abs(guess - left), abs(guess - up), abs(guess - corner)
                line[x] = (line[x] + (left if a <= b and a <= c else up if b <= c else corner)) & 255
        rows.append(bytes(line))
        previous = line
    return width, height, rows


def orbColor(letter):
    """The color of one mana orb: the most of it there is, ignoring its outline and its shine."""
    width, height, rows = readPng(ORBS / f"{letter}.png")
    middle = (width / 2, height / 2)
    inside = []
    for y, row in enumerate(rows):
        for x in range(width):
            red, green, blue, alpha = row[x * 4:x * 4 + 4]
            # The orb's own body: inside its ring, and not the outline that draws it.
            if alpha < 220 or math.hypot(x - middle[0], y - middle[1]) > width * 0.45:
                continue
            inside.append((red, green, blue))
    if not inside:
        raise SystemExit(f"{letter}: the orb has no color in it, which cannot be right")
    # The lit half of what is in there. An orb is a symbol drawn on a colored disc with a shadow
    # under it, and both the symbol and the shadow are nearly black on all five - so the color
    # somebody would name is the bright half, and the most of that.
    lightest = sorted(inside, key=lambda rgb: sum(rgb))[len(inside) // 2:]
    counts = {}
    for color in lightest:
        counts[color] = counts.get(color, 0) + 1
    return max(counts.items(), key=lambda pair: (pair[1], sum(pair[0])))[0]


def lit(color, by):
    """The same color lighter (positive) or darker (negative), by a fraction."""
    if by >= 0:
        return tuple(round(part + (255 - part) * by) for part in color)
    return tuple(round(part * (1 + by)) for part in color)


def coin(colors):
    """Every pixel of the coin, as (red, green, blue, alpha)."""
    middle = (SIZE - 1) / 2
    rim = SIZE / 2 - 0.5
    # The metal: all five darkened together, so the rim belongs to the coin rather than to any color.
    edge = lit(tuple(round(sum(part) / len(colors)) for part in zip(*colors)), -0.55)
    rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            fromMiddle = math.hypot(x - middle, y - middle)
            if fromMiddle > rim + 0.35:
                row.append((0, 0, 0, 0))
                continue
            # Clockwise from the top, twisted outward: the same fifth of the turn is a different
            # color at the rim than it is in the middle, which is what makes it a swirl.
            turn = (math.atan2(y - middle, x - middle) + math.pi / 2) / (2 * math.pi)
            turn = (turn + TWIST * (fromMiddle / rim)) % 1.0
            color = colors[int(turn * len(ORDER)) % len(ORDER)]
            if fromMiddle > rim - 0.6:
                # The struck edge, one color all the way round: five bright ends meeting the air read
                # as a flower rather than as a coin, and an edge is what tells a coin from a marble.
                row.append(edge + (255,))
            elif fromMiddle > rim - 1.6:
                row.append(lit(color, -0.28) + (255,))
            else:
                # Lighter toward the middle, where a struck coin catches the light.
                row.append(lit(color, 0.30 * (1 - fromMiddle / rim)) + (255,))
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
    colors = [orbColor(letter) for letter in ORDER]
    drawn = written(coin(colors))
    was = COIN.read_bytes() if COIN.is_file() else b""
    if drawn == was:
        print(f"mana coin: unchanged, from orbs {colors}")
        return 0
    if checking:
        print("mana coin: would be written")
        return 1
    COIN.parent.mkdir(parents=True, exist_ok=True)
    COIN.write_bytes(drawn)
    print(f"mana coin: written, {len(drawn)} bytes, from orbs {colors}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
