#!/usr/bin/env python3
"""Draws the mod's own mana symbols: a badge in the color it means, with a mark on it.

Why these exist. The printed symbols are Wizards' trademarks and their Fan Content Policy
names them on the list you may not use, so the mod wants its own.

Two halves, kept apart on purpose. A symbol is a badge with something pressed into it, and the
two are worth repainting separately: the badges are a set and want to stay one, the marks are
the part somebody will want to redraw. Both live under art/mana/ as ordinary PNGs, this script
layers them into the textures the font loads, and neither is ever edited inside the jar.

Badges are rewritten every run. A mark that already exists is left alone - it was put there on
purpose, by this script the first time and by somebody with a pencil after that, and painting
over it silently is the one thing a generator must never do. Delete a mark to have it redrawn.

    python3 tools/mana_art.py            # writes the pngs and the font
    python3 tools/mana_art.py --sheet    # and a contact sheet to look at
    python3 tools/mana_art.py --gloss    # the lit-sphere finish, for comparison
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import textures  # noqa: E402  - the PNG writer, kept in one place

OUT = "common/src/main/resources/assets/gathering/textures/font/mana"
FONT = "common/src/main/resources/assets/gathering/font/mana.json"
BADGES = "art/mana/badges"
MARKS = "art/mana/symbols"
SIZE = textures.SYMBOL_SIZE

# Muted on purpose. The mod's own art - the card back, the sleeve, the deck box, the felt - is
# matte and low contrast, and a set of saturated badges beside it reads as imported. The hues
# carry the color and the values stay close together, so black is a violet grey rather than a
# charcoal: it still reads as its own color beside colorless, and it still takes dark ink.
BASE = {
    "w": (0xD8, 0xCB, 0xA6),
    "u": (0x7A, 0xA3, 0xC2),
    "b": (0x8B, 0x80, 0x96),
    "r": (0xC4, 0x7B, 0x6B),
    "g": (0x83, 0xAB, 0x84),
    "c": (0xB0, 0xB4, 0xB8),
    "s": (0xC3, 0xD6, 0xDF),
    "generic": (0xBD, 0xB6, 0xAE),
}

# One ink for every mark, on every badge. This is why the badges are all light: a mark is a
# file somebody will open and repaint, and a mark whose color the layering step decides is a
# mark they cannot really own. Light badges, dark ink, and the color says the color.
INK = (0x26, 0x22, 0x1F)


def mix(one, two, amount):
    return tuple(round(a + (b - a) * amount) for a, b in zip(one, two))


def lighter(color, amount):
    return mix(color, (255, 255, 255), amount)


def darker(color, amount):
    return mix(color, (0, 0, 0), amount)


def badge(base, split=None, gloss=False):
    """A round badge, 32 across.

    Matte by default: a body, one darker crescent along the bottom right, a lighter arc along
    the top left, and a dark rim - four tones, which is how the rest of the mod's art is drawn
    and how a Minecraft item is drawn. The lit-sphere version is kept behind a flag because it
    looked better on its own and worse beside everything else.
    """
    tones = [lighter(base, 0.18), base, darker(base, 0.22), darker(base, 0.38)]
    other = [lighter(split, 0.18), split, darker(split, 0.22), darker(split, 0.38)] if split \
        else tones
    rim = darker(base, 0.62)

    middle = SIZE / 2.0
    radius = middle - 1.0
    px = textures.blank(SIZE, SIZE)
    for y in range(SIZE):
        for x in range(SIZE):
            dx = (x + 0.5) - middle
            dy = (y + 0.5) - middle
            away = (dx * dx + dy * dy) ** 0.5
            if away > radius:
                continue
            # Which half, for a hybrid. The seam runs bottom left to top right, which puts the
            # halves top-left and bottom-right - where the marks go, and where a printed
            # hybrid puts them. The other way round leaves both marks astride the seam.
            here = other if (split and (dx + dy) > 0) else tones
            # The rim, and on a hybrid the cut itself, so the two halves stay two halves even
            # where the marks crowd the middle.
            if away > radius - 1.2 or (split and abs(dx + dy) < 1.0):
                px[y][x] = textures.rgba(rim)
                continue
            # Lit from above, not along the seam: bands parallel to a hybrid's cut make the
            # two halves read as stripes instead of halves.
            lean = (dx * 0.3 + dy) / (radius * 1.3)
            if gloss:
                toward = (((dx / radius) + 0.42) ** 2 + ((dy / radius) + 0.45) ** 2) ** 0.5
                px[y][x] = textures.rgba(here[min(3, max(0, int(toward * 2.6)))])
                continue
            # Three bands rather than a gradient: lit arc, body, shaded crescent.
            band = 0 if lean < -0.22 else (1 if lean < 0.18 else 2)
            px[y][x] = textures.rgba(here[band])
    return px


# ---------------------------------------------------------------------------
# The marks. Seventeen across, drawn here so the first version of each file is
# something rather than nothing - and then never touched again.
# ---------------------------------------------------------------------------
SHAPE = {
    "w": [  # sun
        "........#........",
        "........#........",
        "..#.....#.....#..",
        "...#.........#...",
        "......#####......",
        ".....#######.....",
        "....#########....",
        "....#########....",
        "###.#########.###",
        "....#########....",
        "....#########....",
        ".....#######.....",
        "......#####......",
        "...#.........#...",
        "..#.....#.....#..",
        "........#........",
        "........#........",
    ],
    "u": [  # drop
        ".................",
        "........#........",
        "........#........",
        ".......###.......",
        ".......###.......",
        "......#####......",
        ".....#######.....",
        "....#########....",
        "...###########...",
        "...###########...",
        "...###########...",
        "...###########...",
        "...###########...",
        "....#########....",
        ".....#######.....",
        ".......###.......",
        ".................",
    ],
    "b": [  # skull
        ".................",
        "....#########....",
        "..#############..",
        ".###############.",
        ".###############.",
        ".##...#####...##.",
        ".##...#####...##.",
        ".#######.#######.",
        ".######...######.",
        ".###############.",
        "..#############..",
        "...###########...",
        "....#########....",
        ".....#######.....",
        ".....#.#.#.#.....",
        ".................",
        ".................",
    ],
    "r": [  # flame
        ".........#.......",
        "........##.......",
        "........###......",
        ".......####......",
        "...#...####......",
        "..##..#####......",
        "..###.#####......",
        "..##########.....",
        "..##########.....",
        ".############....",
        ".############....",
        ".############....",
        ".############....",
        "..##########.....",
        "...########......",
        ".....####........",
        ".................",
    ],
    "g": [  # tree
        ".................",
        "......#####......",
        "....#########....",
        "...###########...",
        "..#############..",
        "..#############..",
        ".###############.",
        ".###############.",
        "..#############..",
        "..#############..",
        "...###########...",
        ".....#######.....",
        ".......###.......",
        ".......###.......",
        ".......###.......",
        "......#####......",
        ".................",
    ],
    "c": [  # four-pointed star
        "........#........",
        "........#........",
        ".......###.......",
        ".......###.......",
        "......#####......",
        "......#####......",
        ".....#######.....",
        "....#########....",
        "#################",
        "....#########....",
        ".....#######.....",
        "......#####......",
        "......#####......",
        ".......###.......",
        ".......###.......",
        "........#........",
        "........#........",
    ],
    "s": [  # snowflake
        "........#........",
        "....#...#...#....",
        ".....#..#..#.....",
        "..#...#.#.#...#..",
        "...#...###...#...",
        "....#..###..#....",
        ".....#.###.#.....",
        "......#####......",
        "###..#######..###",
        "......#####......",
        ".....#.###.#.....",
        "....#..###..#....",
        "...#...###...#...",
        "..#...#.#.#...#..",
        ".....#..#..#.....",
        "....#...#...#....",
        "........#........",
    ],
    "tap": [  # an arrow swung a quarter turn
        ".................",
        ".................",
        ".###.............",
        "..###............",
        "...###...........",
        "....###..........",
        ".....###.........",
        "......###....#...",
        ".......###..##...",
        "........######...",
        "..........####...",
        ".........#####...",
        "........######...",
        ".......#######...",
        ".................",
        ".................",
        ".................",
    ],
    "energy": [  # bolt
        ".................",
        ".........#####...",
        "........#####....",
        ".......#####.....",
        "......#####......",
        ".....#####.......",
        "....##########...",
        "...##########....",
        ".........####....",
        "........####.....",
        ".......####......",
        "......####.......",
        ".....####........",
        "....####.........",
        "...####..........",
        "...###...........",
        ".................",
    ],
}

# Untap is tap the other way about, which is exactly what it means.
SHAPE["untap"] = ["".join(reversed(row)) for row in reversed(SHAPE["tap"])]

# Numerals, five by seven, the plainest shapes that stay legible at nine pixels tall.
FIGURE = {
    "0": ["#####", "#...#", "#...#", "#...#", "#...#", "#...#", "#####"],
    "1": ["..#..", ".##..", "..#..", "..#..", "..#..", "..#..", "#####"],
    "2": ["#####", "....#", "....#", "#####", "#....", "#....", "#####"],
    "3": ["#####", "....#", "....#", "#####", "....#", "....#", "#####"],
    "4": ["#...#", "#...#", "#...#", "#####", "....#", "....#", "....#"],
    "5": ["#####", "#....", "#....", "#####", "....#", "....#", "#####"],
    "6": ["#####", "#....", "#....", "#####", "#...#", "#...#", "#####"],
    "7": ["#####", "....#", "....#", "...#.", "..#..", "..#..", "..#.."],
    "8": ["#####", "#...#", "#...#", "#####", "#...#", "#...#", "#####"],
    "9": ["#####", "#...#", "#...#", "#####", "....#", "....#", "#####"],
    "x": ["#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"],
    "y": ["#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."],
    "z": ["#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"],
}


def phyrexian():
    """Phi: a ring with a stem through it. Drawn rather than typed - a ring is one line of
    arithmetic and seventeen rows of guesswork."""
    rows = []
    for y in range(17):
        row = ""
        for x in range(17):
            dx, dy = x - 8, y - 8.5
            away = (dx * dx + dy * dy * 1.15) ** 0.5
            ring = 3.4 < away <= 6.4
            stem = 7 <= x <= 9
            row += "#" if ring or stem else "."
        rows.append(row)
    return rows


SHAPE["p"] = phyrexian()


def stamp(px, rows, left, top, color):
    """Lay a grid of '#' onto a canvas, opaque, in one color."""
    for y, row in enumerate(rows):
        for x, cell in enumerate(row):
            if cell == "#":
                px[top + y][left + x] = textures.rgba(color)


def mark(name):
    """A mark on its own transparent canvas, the size of a badge."""
    px = textures.blank(SIZE, SIZE)
    if name in SHAPE:
        rows = SHAPE[name]
        stamp(px, rows, (SIZE - len(rows[0])) // 2, (SIZE - len(rows)) // 2, INK)
        return px
    return digits(px, name)


def digits(px, text):
    """One or two figures, centered. One figure gets the room to be three times the size."""
    scale = 3 if len(text) == 1 else 2
    width = len(text) * 5 * scale + (len(text) - 1) * scale
    left = (SIZE - width) // 2
    top = (SIZE - 7 * scale) // 2
    for figure in text:
        rows = FIGURE[figure]
        for y, row in enumerate(rows):
            for x, cell in enumerate(row):
                if cell != "#":
                    continue
                for dy in range(scale):
                    for dx in range(scale):
                        px[top + y * scale + dy][left + x * scale + dx] = textures.rgba(INK)
        left += 6 * scale
    return px


# ---------------------------------------------------------------------------
# Reading a mark back. Somebody repaints these; the point of the whole split is
# that what comes off disk wins over what this script would have drawn.
# ---------------------------------------------------------------------------
def unpack(line, depth, width):
    """One byte per sample, from a row packed at fewer bits than that."""
    per = 8 // depth
    mask = (1 << depth) - 1
    out = bytearray(width)
    for x in range(width):
        byte = line[x // per]
        out[x] = (byte >> (8 - depth * (x % per + 1))) & mask
    return out


def read_png(path):
    import zlib

    raw = open(path, "rb").read()
    if raw[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit(path + " is not a png")
    width = height = depth = kind = None
    body = b""
    palette, clear = b"", b""
    at = 8
    while at < len(raw):
        length = int.from_bytes(raw[at:at + 4], "big")
        tag = raw[at + 4:at + 8]
        data = raw[at + 8:at + 8 + length]
        if tag == b"IHDR":
            width = int.from_bytes(data[0:4], "big")
            height = int.from_bytes(data[4:8], "big")
            depth, kind = data[8], data[9]
        elif tag == b"PLTE":
            palette = data
        elif tag == b"tRNS":
            clear = data
        elif tag == b"IDAT":
            body += data
        at += length + 12
    # Color types 0, 2, 3 and 6: grey, rgb, palette, rgba. An image editor will hand back any
    # of them - a repainted mark saved as an indexed png is the common case.
    if kind not in (0, 2, 3, 6) or depth not in (1, 2, 4, 8) \
            or (depth != 8 and kind not in (0, 3)):
        raise SystemExit(path + ": cannot read depth " + str(depth) + " type " + str(kind))
    channels = {0: 1, 2: 3, 3: 1, 6: 4}[kind]
    flat = zlib.decompress(body)
    stride = (width * channels * depth + 7) // 8
    out, previous = [], bytearray(stride)
    at = 0
    for _ in range(height):
        filter_kind = flat[at]
        line = bytearray(flat[at + 1:at + 1 + stride])
        at += 1 + stride
        for i in range(stride):
            left = line[i - channels] if i >= channels else 0
            up = previous[i]
            corner = previous[i - channels] if i >= channels else 0
            if filter_kind == 1:
                line[i] = (line[i] + left) & 0xFF
            elif filter_kind == 2:
                line[i] = (line[i] + up) & 0xFF
            elif filter_kind == 3:
                line[i] = (line[i] + (left + up) // 2) & 0xFF
            elif filter_kind == 4:
                guess = left + up - corner
                one, two, three = abs(guess - left), abs(guess - up), abs(guess - corner)
                near = left if one <= two and one <= three else (up if two <= three else corner)
                line[i] = (line[i] + near) & 0xFF
        if depth < 8:
            line = unpack(line, depth, width)
        row = []
        for x in range(width):
            piece = line[x * channels:(x + 1) * channels]
            if kind == 6:
                row.append(tuple(piece))
            elif kind == 2:
                row.append(tuple(piece) + (255,))
            elif kind == 0:
                row.append((piece[0],) * 3 + (255,))
            else:
                index = piece[0]
                row.append(tuple(palette[index * 3:index * 3 + 3])
                           + (clear[index] if index < len(clear) else 255,))
        out.append(row)
        previous = line
    return out


def write_png(path, px):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    textures.write_png(path, len(px[0]), len(px), px)


# ---------------------------------------------------------------------------
# Which badge, and what goes on it.
# ---------------------------------------------------------------------------
COLORS = "wubrg"


def badgeFor(name):
    """The badge under a symbol, as (base, split). Split is the bottom-right half."""
    if name.isdigit() or name in ("x", "y", "z", "tap", "untap"):
        return BASE["generic"], None
    if name == "energy":
        return BASE["c"], None
    if len(name) == 1:
        return BASE[name], None
    if name.endswith("p"):
        return BASE[name[0]], None
    if name[0] == "2":
        return BASE["generic"], BASE[name[1]]
    return BASE[name[0]], BASE[name[1]]


def markFor(name):
    """What is pressed into it, as (mark, where) with where one of full, tl, br."""
    if name.isdigit() or len(name) == 1 or name in ("x", "y", "z", "tap", "untap", "energy"):
        return [(name, "full")]
    if name.endswith("p"):
        return [("p", "full")]
    if name[0] == "2":
        return [("2", "tl"), (name[1], "br")]
    return [(name[0], "tl"), (name[1], "br")]


MARK_NAMES = sorted(
    {m for n in textures.SYMBOL_NAMES for m, _ in markFor(n)},
    key=lambda n: (n.isdigit(), int(n) if n.isdigit() else 0, n),
)

# Where a half-size mark sits, and how big it gets. A hybrid badge is cut corner to corner, so
# each mark has to clear the seam and still sit inside the rim.
HALF_SIZE = 12
PLACE = {"tl": (5, 5), "br": (SIZE - HALF_SIZE - 5, SIZE - HALF_SIZE - 5)}


def bounds(px):
    """The box the ink actually occupies. A mark is drawn with room around it; a half-size
    copy has none to spare, so it is the ink that gets scaled, not the canvas."""
    left, top, right, bottom = SIZE, SIZE, -1, -1
    for y in range(SIZE):
        for x in range(SIZE):
            if px[y][x][3]:
                left, top = min(left, x), min(top, y)
                right, bottom = max(right, x), max(bottom, y)
    if right < 0:
        return 0, 0, SIZE, SIZE
    return left, top, right + 1, bottom + 1


def half(px):
    """A mark taken down to half size, averaging over alpha so the edges stay soft."""
    left, top, right, bottom = bounds(px)
    span = max(right - left, bottom - top)
    left -= (span - (right - left)) // 2
    top -= (span - (bottom - top)) // 2
    small = textures.blank(HALF_SIZE, HALF_SIZE)
    step = span / HALF_SIZE
    for y in range(HALF_SIZE):
        for x in range(HALF_SIZE):
            r = g = b = a = weight = 0.0
            count = 0
            for sy in range(top + int(y * step), top + max(int((y + 1) * step),
                                                           int(y * step) + 1)):
                for sx in range(left + int(x * step), left + max(int((x + 1) * step),
                                                                 int(x * step) + 1)):
                    if not (0 <= sy < SIZE and 0 <= sx < SIZE):
                        continue
                    pr, pg, pb, pa = px[sy][sx]
                    r += pr * pa
                    g += pg * pa
                    b += pb * pa
                    a += pa
                    weight += pa
                    count += 1
            if count == 0 or weight == 0:
                continue
            small[y][x] = (round(r / weight), round(g / weight), round(b / weight),
                           round(a / count))
    return small


def press(under, over, left, top):
    """Alpha-over, clipped to where the badge already is - a mark never spills off the rim."""
    for y, row in enumerate(over):
        for x, (r, g, b, a) in enumerate(row):
            if a == 0:
                continue
            ty, tx = top + y, left + x
            if not (0 <= ty < len(under) and 0 <= tx < len(under[0])):
                continue
            br, bg, bb, ba = under[ty][tx]
            if ba == 0:
                continue
            amount = a / 255.0
            under[ty][tx] = (round(br + (r - br) * amount),
                             round(bg + (g - bg) * amount),
                             round(bb + (b - bb) * amount), ba)


def layer(name, marks, gloss=False):
    base, split = badgeFor(name)
    px = badge(base, split, gloss)
    for what, where in markFor(name):
        if where == "full":
            press(px, marks[what], 0, 0)
        else:
            left, top = PLACE[where]
            press(px, half(marks[what]), left, top)
    return px


def contact(px_by_name, path, scale=3, across=10, ground=(0x2B, 0x27, 0x24)):
    """Every symbol on one sheet, big enough to argue about."""
    names = list(px_by_name)
    cell = SIZE * scale + scale * 2
    down = (len(names) + across - 1) // across
    sheet = [[textures.rgba(ground) for _ in range(cell * across)] for _ in range(cell * down)]
    for index, name in enumerate(names):
        ox = (index % across) * cell + scale
        oy = (index // across) * cell + scale
        art = px_by_name[name]
        for y in range(SIZE):
            for x in range(SIZE):
                r, g, b, a = art[y][x]
                if a == 0:
                    continue
                amount = a / 255.0
                for dy in range(scale):
                    for dx in range(scale):
                        br, bg, bb, _ = sheet[oy + y * scale + dy][ox + x * scale + dx]
                        sheet[oy + y * scale + dy][ox + x * scale + dx] = (
                            round(br + (r - br) * amount), round(bg + (g - bg) * amount),
                            round(bb + (b - bb) * amount), 255)
    write_png(path, sheet)


def font(names, path):
    providers = []
    for index, name in enumerate(names):
        providers.append(
            "    {\n"
            '      "type": "bitmap",\n'
            f'      "file": "gathering:font/mana/{name}.png",\n'
            '      "ascent": 8,\n'
            '      "height": 9,\n'
            f'      "chars": ["\\u{textures.FIRST_CODEPOINT + index:04X}"]\n'
            "    }"
        )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as handle:
        handle.write('{\n  "providers": [\n' + ",\n".join(providers) + "\n  ]\n}\n")


def main(argv):
    gloss = "--gloss" in argv
    sheet = "--sheet" in argv
    into = None
    if "--preview" in argv:
        into = argv[argv.index("--preview") + 1]

    out = os.path.join(into, "font", "mana") if into else OUT
    badges = os.path.join(into, "badges") if into else BADGES
    symbols = MARKS

    drawn = 0
    for name in MARK_NAMES:
        path = os.path.join(symbols, name + ".png")
        if not os.path.exists(path):
            write_png(path, mark(name))
            drawn += 1
    marks = {name: read_png(os.path.join(symbols, name + ".png")) for name in MARK_NAMES}

    made = {}
    for name in textures.SYMBOL_NAMES:
        base, split = badgeFor(name)
        write_png(os.path.join(badges, name + ".png"), badge(base, split, gloss))
        made[name] = layer(name, marks, gloss)
        write_png(os.path.join(out, name + ".png"), made[name])

    font(textures.SYMBOL_NAMES, os.path.join(into, "mana.json") if into else FONT)
    if sheet:
        contact(made, os.path.join(into or ".", "contact.png"))
    print(f"drew {drawn} new marks, kept {len(MARK_NAMES) - drawn}, "
          f"wrote {len(made)} symbols{' (gloss)' if gloss else ''}")


if __name__ == "__main__":
    main(sys.argv[1:])
