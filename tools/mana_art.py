#!/usr/bin/env python3
"""Puts the mana symbols together out of their parts, and says when they have drifted.

A symbol is a badge with a mark pressed into it, and the two are kept apart on purpose. The
badges are a set and want to stay one; the mark is the part somebody will want to redraw. So a
mark is a silhouette - shape and coverage, no colour - and the badge it lands on says what ink
it is drawn in. That is what lets one sun sit on six different badges, and lets you repaint the
sun without touching any of them.

    art/mana/badges/<symbol>.png     one per symbol, hybrids already cut
    art/mana/symbols/<mark>.png      full size, for a plain badge
    art/mana/symbols/half-tl/<mark>.png, half-br/<mark>.png   for the two halves of a hybrid
    art/mana/ink.json                what colour each badge draws its marks in

Where they came from: an earlier set was lost with an unpushed commit, and tools/refit.py
rebuilt it from two screenshots. This script does not draw them - it assembles them, so that
what ships is always exactly the parts on disk.

    python3 tools/mana_art.py            # write the font textures and mana.json
    python3 tools/mana_art.py --check    # fail if what ships is not the parts on disk
    python3 tools/mana_art.py --marks    # what every mark measures and where it lands
    python3 tools/mana_art.py --guide    # rewrite the template a mark is drawn on

How to draw one is in art/mana/README.md, with a template to draw on beside it.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import textures  # noqa: E402  - the PNG writer, kept in one place

OUT = "common/src/main/resources/assets/gathering/textures/font/mana"
FONT = "common/src/main/resources/assets/gathering/font/mana.json"
BADGES = "art/mana/badges"
MARKS = "art/mana/symbols"
INK = "art/mana/ink.json"
SIZE = textures.SYMBOL_SIZE

# The badge: a thirty-pixel circle in a thirty-two pixel canvas, cut corner to corner when it
# carries two colours, with the second colour on the bottom-right so that each half's mark
# lands on its own colour rather than across the cut.
#: The one pixel everything is lined up on: the middle of the canvas, the middle of the
#: badge's disc, and the middle of every full-size mark. That there is a single pixel to name
#: here is the whole reason the disc is an odd number across - see tools/orb_grow.py.
MIDDLE = SIZE // 2


def keyFor(name):
    """The badge under a symbol, as (base, second) colour names rather than colours."""
    if name.isdigit() or name in ("x", "y", "z", "tap", "untap"):
        return "generic", None
    if name == "energy":
        return "energy", None
    if len(name) == 1:
        return name, None
    if name.endswith("p"):
        return name[0], None
    if name[0] == "2":
        return "generic", name[1]
    return name[0], name[1]


def regions(name):
    """Which parts of a symbol take a mark, and which badge colour each of them wears."""
    base, second = keyFor(name)
    if not second:
        return [("full", base)]
    return [("tl", base), ("br", second)]


def markName(name, region):
    """Which mark file a symbol's region draws from."""
    if region == "full":
        return "p" if len(name) == 2 and name.endswith("p") else name
    return name[0] if region == "tl" else name[1]


def markPath(region, name):
    if region == "full":
        return os.path.join(MARKS, name + ".png")
    return os.path.join(MARKS, "half-" + region, name + ".png")


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


def inkBox(mark):
    """The mark's opaque bounding box, as (left, top, right, bottom), or None."""
    ink = [(x, y) for y in range(SIZE) for x in range(SIZE) if mark[y][x][3]]
    if not ink:
        return None
    xs = [p[0] for p in ink]
    ys = [p[1] for p in ink]
    return min(xs), min(ys), max(xs), max(ys)


def centred(mark):
    """The mark moved so its middle sits on the badge's middle pixel.

    Where a symbol happens to sit in its own canvas is not something anybody should have to
    get right by hand. It is drawn; this is what puts it in the middle.

    A mark an odd number of pixels across has a middle pixel of its own, and it lands exactly
    on the badge's. An even one has a seam instead, so it straddles that pixel with the extra
    column or row on the right and below - a rule rather than a judgement, and one that is
    only ever half a source pixel away from the ideal. These are drawn at thirty-three and
    shown at nine, so half a pixel here is an eighth of one on the screen.
    """
    box = inkBox(mark)
    if box is None:
        return mark
    left, top, right, bottom = box
    dx = MIDDLE - (left + right) // 2
    dy = MIDDLE - (top + bottom) // 2
    if not dx and not dy:
        return mark
    clear = (0, 0, 0, 0)
    return [[mark[y - dy][x - dx]
             if 0 <= y - dy < SIZE and 0 <= x - dx < SIZE else clear
             for x in range(SIZE)]
            for y in range(SIZE)]


def press(badge, mark, ink):
    """Cut a mark into a badge, in the badge's own ink. Coverage comes from the mark's alpha,
    so a soft edge stays soft, and nothing is ever drawn outside the badge."""
    out = [row[:] for row in badge]
    for y in range(SIZE):
        for x in range(SIZE):
            amount = mark[y][x][3] / 255.0
            if not amount or not out[y][x][3]:
                continue
            out[y][x] = tuple(round(a + (b - a) * amount)
                              for a, b in zip(out[y][x][:3], ink[:3])) + (255,)
    return out


def inks():
    with open(INK) as handle:
        raw = json.load(handle)
    return {key: tuple(int(value[i:i + 2], 16) for i in (1, 3, 5))
            for key, value in raw.items()}


def build(name, ink):
    """One symbol: its badge, with each of its marks pressed in."""
    art = read_png(os.path.join(BADGES, name + ".png"))
    for region, key in regions(name):
        path = markPath(region, markName(name, region))
        mark = read_png(path)
        # A full-size mark is stood on the badge's middle pixel. A half mark is not: it sits
        # in one half of a hybrid, so the centre is not what it is lined up against.
        if region == "full":
            mark = centred(mark)
        art = press(art, mark, ink[key])
    return art


def check(ink):
    wrong = [name for name in textures.SYMBOL_NAMES
             if build(name, ink) != read_png(os.path.join(OUT, name + ".png"))]
    if wrong:
        raise SystemExit("stale, rerun tools/mana_art.py: " + ", ".join(wrong))
    print(f"{len(textures.SYMBOL_NAMES)} symbols match their badge and marks")


def marks():
    """What every mark measures and where it will land. For drawing new ones."""
    print(f"canvas {SIZE}x{SIZE}, middle pixel ({MIDDLE},{MIDDLE}),"
          f" badge disc {SIZE - 2} across")
    print(f"{'mark':<18}{'ink':>9}  {'middle':>8}  note")
    for name in sorted({markName(n, r) for n in textures.SYMBOL_NAMES for r, _ in regions(n)
                        if r == "full"}):
        path = markPath("full", name)
        box = inkBox(read_png(path))
        if box is None:
            print(f"{name:<18}{'empty':>9}")
            continue
        left, top, right, bottom = box
        wide, tall = right - left + 1, bottom - top + 1
        # After centring, which is what the assembler does to it on the way in.
        even = [word for word, span in (("width", wide), ("height", tall)) if not span % 2]
        say = "on the middle pixel" if not even else (
            f"straddles it - {' and '.join(even)} even, so it has no middle of its own")
        print(f"{name:<18}{wide:>4}x{tall:<4}  {'yes' if wide % 2 and tall % 2 else 'no':>8}  {say}")
    print("\nDraw a mark odd in both directions and it lands exactly on the middle pixel."
          "\nAn even one straddles it, which is an eighth of a screen pixel out - see"
          " art/mana/README.md")


#: A layer to draw a mark on top of, written by --guide.
GUIDE = "art/mana/guide.png"

#: The guide's own colours. Loud on purpose - this is never part of a symbol, and a guide
#: that could be mistaken for art is a guide somebody ships by accident.
GUIDE_FIELD = (255, 255, 255, 26)
GUIDE_RIM = (255, 96, 96, 64)
GUIDE_CROSS = (0, 200, 255, 48)
GUIDE_MIDDLE = (255, 0, 255, 255)

#: How far in from the disc's edge its dark rim reaches. A mark drawn over the rim loses its
#: edge against it, which is why the guide marks that ring rather than leaving it blank.
RIM_DEPTH = 2


def guide():
    """Writes the template a mark is drawn on top of."""
    badge = read_png(os.path.join(BADGES, "c.png"))
    radius = (SIZE - 2) / 2.0
    rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            away = ((x - MIDDLE) ** 2 + (y - MIDDLE) ** 2) ** 0.5
            if x == MIDDLE and y == MIDDLE:
                row.append(GUIDE_MIDDLE)
            elif not badge[y][x][3]:
                row.append((0, 0, 0, 0))
            elif x == MIDDLE or y == MIDDLE:
                row.append(GUIDE_CROSS)
            elif away > radius - RIM_DEPTH:
                row.append(GUIDE_RIM)
            else:
                row.append(GUIDE_FIELD)
        rows.append(row)
    write_png(GUIDE, rows)
    print(f"wrote {GUIDE}: {SIZE}x{SIZE}, middle pixel ({MIDDLE},{MIDDLE}) in magenta,"
          f" the rim in red, the room a mark has in white")


def main(argv):
    ink = inks()
    if "--guide" in argv:
        return guide()
    if "--marks" in argv:
        return marks()
    if "--check" in argv:
        return check(ink)
    for name in textures.SYMBOL_NAMES:
        write_png(os.path.join(OUT, name + ".png"), build(name, ink))
    font(textures.SYMBOL_NAMES, FONT)
    print(f"assembled {len(textures.SYMBOL_NAMES)} symbols from {BADGES} and {MARKS}")


if __name__ == "__main__":
    main(sys.argv[1:])
