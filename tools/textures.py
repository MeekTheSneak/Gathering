#!/usr/bin/env python3
"""Generates the mod's textures from a palette.

The PNGs under assets/gathering/textures/gui/sprites are the source of truth for how the
mod looks - repaint one and the screens change, and a resource pack can replace any of
them. This script exists so a whole coherent set can be regenerated from one palette
instead of being edited pixel by pixel, which is what a second theme will want.

Run from the repository root:

    python3 tools/textures.py

No dependencies: PNGs are written with zlib and struct so this runs anywhere Python does.
"""

import os
import struct
import zlib

OUT = "common/src/main/resources/assets/gathering/textures/gui/sprites"

# ---------------------------------------------------------------------------
# Palette. A theme is this block and nothing else.
# ---------------------------------------------------------------------------
PALETTE = {
    "panel":        (0x16, 0x16, 0x1C),
    "panel_edge":   (0x0A, 0x0A, 0x0D),
    "inset":        (0x0C, 0x0C, 0x10),
    "inset_edge":   (0x2C, 0x2C, 0x36),
    "accent":       (0x56, 0x82, 0xB2),
    "accent_dim":   (0x26, 0x36, 0x4A),

    # The deck list panel: deeper and bluer than the flat panels, because it is the one
    # surface that runs edge to edge and needs to read as a backdrop rather than a card.
    "list_top":     (0x28, 0x33, 0x5C),
    "list_bottom":  (0x1A, 0x21, 0x3C),
    "list_edge":    (0x6E, 0x8A, 0xC4),
    "shadow":       (0x00, 0x00, 0x00, 0x66),

    "track":        (0x12, 0x18, 0x2A),
    "thumb":        (0x4F, 0xC3, 0xD9),

    # The table surface. Dyeable later; this is the undyed default.
    "felt":         (0x1E, 0x4B, 0x33),
    "felt_weave":   (0x18, 0x3E, 0x2A),

    # The collection block: a dark cabinet with brass edges. A placeholder until the real
    # one is drawn, and deliberately not wood - it must not read as another table.
    "cabinet":      (0x3A, 0x2C, 0x22),
    "cabinet_dark": (0x2A, 0x1E, 0x17),
    "cabinet_lip":  (0x8C, 0x6E, 0x3C),

    # The shop counter: a light wooden counter with a glass display front, so it reads as a
    # till rather than as another cabinet. A placeholder until the real one is drawn.
    "counter":      (0x9A, 0x76, 0x46),
    "counter_dark": (0x6B, 0x50, 0x2E),
    "counter_top":  (0xB4, 0x8E, 0x59),
    "counter_glass": (0x63, 0x8C, 0x96),

    # The sealed box item: brown card with a printed band.
    "box":          (0x8A, 0x6A, 0x44),
    "box_dark":     (0x5E, 0x46, 0x2C),
    "box_band":     (0x2B, 0x3E, 0x66),
}

# The deck panel's right edge runs from this fraction of the width at the top to this
# fraction at the bottom. DeckScreenLayout.TAPER_TOP and TAPER_BOTTOM must match, or the
# scrollbar drawn along that edge will not sit on it. A theme replacing deck_panel.png
# keeps these.
#
# The top is short of the full width on purpose: the edge line and the shadow outside it
# need room, and at 1.0 they run off the right of the texture and the panel's top corner
# arrives visibly unfinished.
TAPER_TOP = 0.90
TAPER_BOTTOM = 0.74

# Width of the line down the tapered edge, as a fraction of panel width. Deliberately thin
# and muted: the bright thing on that edge is the scroll thumb, not the panel border.
EDGE_FRACTION = 0.010

SUPERSAMPLE = 4


def rgba(color, alpha=255):
    if len(color) == 4:
        return color
    return color + (alpha,)


# Art that is somebody's rather than this script's.
#
# Everything here was generated once and has since been repainted by hand. Running main()
# would put the generated version back, silently, and the only sign would be a texture that
# used to be good going plain again - so it refuses instead. Take a file off this list when
# you want the generator to own it again.
PROTECTED = {
    "block/table_felt.png",
    "item/card.png",
    "item/deck.png",
    "item/pack.png",
    "item/sealed.png",
}


def isProtected(path):
    tail = path.replace("\\", "/").split("/textures/", 1)
    return len(tail) == 2 and tail[1] in PROTECTED


def write_png(path, width, height, pixels):
    """pixels: list of rows, each a list of (r, g, b, a)."""
    if isProtected(path):
        raise SystemExit(
            "refusing to overwrite " + path + "\n"
            "It is hand-painted; see PROTECTED at the top of this file.")
    raw = bytearray()
    for row in pixels:
        raw.append(0)  # filter: none
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(png)
    print("wrote", path, f"({width}x{height})")


def write_mcmeta(path, border, width, height):
    with open(path + ".mcmeta", "w") as handle:
        handle.write(
            '{\n  "gui": {\n    "scaling": {\n      "type": "nine_slice",\n'
            f'      "width": {width},\n      "height": {height},\n'
            f'      "border": {border},\n      "stretch_inner": false\n'
            "    }\n  }\n}\n"
        )


def blank(width, height):
    return [[(0, 0, 0, 0) for _ in range(width)] for _ in range(height)]


# ---------------------------------------------------------------------------
# Nine-slice panels: a flat fill with a one-pixel lip and a darker outer border.
# ---------------------------------------------------------------------------
def nine_slice(name, fill, edge, lip=None, size=32, border=8):
    px = blank(size, size)
    for y in range(size):
        for x in range(size):
            depth = min(x, y, size - 1 - x, size - 1 - y)
            if depth == 0:
                color = edge
            elif depth == 1 and lip is not None:
                color = lip
            else:
                color = fill
            px[y][x] = rgba(color)
    path = f"{OUT}/{name}.png"
    write_png(path, size, size, px)
    write_mcmeta(path, border, size, size)


# ---------------------------------------------------------------------------
# The deck list panel: flush to the left, tapering on the right, with a soft shadow
# outside the taper. Stretched rather than nine-sliced, because the taper is the point.
# ---------------------------------------------------------------------------
def deck_panel(width=256, height=512):
    px = blank(width, height)
    shadow_width = width * 0.045
    edge_width = width * EDGE_FRACTION

    for y in range(height):
        for x in range(width):
            r = g = b = a = 0.0
            for sy in range(SUPERSAMPLE):
                for sx in range(SUPERSAMPLE):
                    fx = x + (sx + 0.5) / SUPERSAMPLE
                    fy = y + (sy + 0.5) / SUPERSAMPLE
                    t = fy / height
                    boundary = width * (TAPER_TOP + (TAPER_BOTTOM - TAPER_TOP) * t)

                    if fx < boundary - edge_width:
                        # Body, with a gentle vertical gradient so it is not a flat slab.
                        top = PALETTE["list_top"]
                        bottom = PALETTE["list_bottom"]
                        sample = tuple(
                            round(top[i] + (bottom[i] - top[i]) * t) for i in range(3)
                        ) + (255,)
                    elif fx < boundary:
                        sample = rgba(PALETTE["list_edge"])
                    elif fx < boundary + shadow_width:
                        fade = 1.0 - (fx - boundary) / shadow_width
                        shadow = PALETTE["shadow"]
                        sample = shadow[:3] + (round(shadow[3] * fade * fade),)
                    else:
                        sample = (0, 0, 0, 0)

                    r += sample[0] * sample[3]
                    g += sample[1] * sample[3]
                    b += sample[2] * sample[3]
                    a += sample[3]

            samples = SUPERSAMPLE * SUPERSAMPLE
            if a <= 0:
                px[y][x] = (0, 0, 0, 0)
            else:
                px[y][x] = (round(r / a), round(g / a), round(b / a), round(a / samples))

    write_png(f"{OUT}/deck_panel.png", width, height, px)


# ---------------------------------------------------------------------------
# Scrollbar: a recessed track and a bright thumb, both nine-sliced so they stretch to any
# length without the caps smearing.
# ---------------------------------------------------------------------------
def scrollbar():
    for name, fill, edge in (
        ("scroll_track", PALETTE["track"], PALETTE["panel_edge"]),
        ("scroll_thumb", PALETTE["thumb"], PALETTE["accent_dim"]),
    ):
        size = 16
        border = 4
        px = blank(size, size)
        for y in range(size):
            for x in range(size):
                depth = min(x, y, size - 1 - x, size - 1 - y)
                px[y][x] = rgba(edge if depth == 0 else fill)
        path = f"{OUT}/{name}.png"
        write_png(path, size, size, px)
        write_mcmeta(path, border, size, size)


# ---------------------------------------------------------------------------
# Mana and tap symbols.
#
# Our own art: lettered discs, not Wizards' pictographs, for the same reason the card back
# is our own. The colors are the conventional five because which color a cost is happens
# to be the information the symbol carries.
#
# Order must match ManaSymbols.NAMES exactly - the index is the glyph's codepoint.
# ---------------------------------------------------------------------------
SYMBOL_SIZE = 32

MANA_COLORS = {
    "w": (0xFF, 0xFB, 0xD5),
    "u": (0x9A, 0xD9, 0xF7),
    "b": (0xA9, 0x9F, 0x9C),
    "r": (0xF6, 0x9E, 0x81),
    "g": (0x8C, 0xCE, 0xA3),
    "c": (0xC9, 0xC4, 0xBF),
    "s": (0xD5, 0xE4, 0xEE),
}
GENERIC = (0xC9, 0xC4, 0xBF)
GLYPH_INK = (0x1A, 0x16, 0x12)
SYMBOL_RIM = (0x00, 0x00, 0x00, 0x88)

# A 5x7 bitmap face, hand-set so a symbol stays legible once it is scaled down to the
# height of a line of text. Anything not here is drawn as a plain disc.
FACE = {
    "0": (".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
    "1": ("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
    "2": (".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
    "3": ("####.", "....#", "..##.", "....#", "....#", "#...#", ".###."),
    "4": ("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
    "5": ("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
    "6": ("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
    "7": ("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
    "8": (".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
    "9": (".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
    "W": ("#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#"),
    "U": ("#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
    "B": ("####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."),
    "R": ("####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"),
    "G": (".###.", "#...#", "#....", "#..##", "#...#", "#...#", ".###."),
    "C": (".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."),
    "S": (".####", "#....", "#....", ".###.", "....#", "....#", "####."),
    "T": ("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."),
    "Q": (".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"),
    "E": ("#####", "#....", "####.", "#....", "#....", "#....", "#####"),
    "X": ("#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"),
    "Y": ("#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."),
    "Z": ("#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"),
    "P": ("####.", "#...#", "#...#", "####.", "#....", "#....", "#...."),
}


# An 11x11 pictogram, for the symbols that are pictures rather than letters.
#
# Our own drawings of the things the printed symbols depict - a spiked sun, a drop, a skull,
# a flame, a tree - rather than copies of the printed ones: the symbols on a real card are
# Wizards' trade dress, and section 15 says none of their artwork ships here. What each color
# depicts is not theirs to own; how they drew it is.
#
# Eleven across, stamped at scale 2 and clipped to the disc, because these end up nine to
# twenty-seven pixels across depending on the interface scale. Bold silhouettes only: at the
# small end anything finer is a smudge, and a shape that is not instantly the right thing is
# worse than a letter.
PICTURE = {
    # A sun: a solid middle with eight spikes off it. The spikes are the whole read - without
    # them a dark blob in a pale disc is an eye, which is what the first attempt looked like.
    "w": ("....###....",
          ".#..###..#.",
          ".##.###.##.",
          "..#######..",
          "###########",
          "##.#####.##",
          "###########",
          "..#######..",
          ".##.###.##.",
          ".#..###..#.",
          "....###...."),
    # A drop: a point at the top, widest low, round at the bottom.
    "u": (".....#.....",
          "....###....",
          "...#####...",
          "...#####...",
          "..#######..",
          ".#########.",
          ".#########.",
          ".#########.",
          ".#########.",
          "..#######..",
          "...#####..."),
    # A skull: cranium, two sockets, a row of teeth under it.
    "b": ("...#####...",
          "..#######..",
          ".#########.",
          ".#########.",
          ".##..#..##.",
          ".##..#..##.",
          ".#########.",
          "..#######..",
          "...#####...",
          "..#.#.#.#..",
          "...#####..."),
    # A flame, which is a drop's opposite: the tip leans, the sides are uneven, and a second
    # tongue comes off one shoulder. Drawn symmetrical it reads as a drop, which is the note
    # the first attempt came back with.
    "r": ("......#....",
          ".....##....",
          "....###....",
          "....####...",
          "...#####...",
          "..######.#.",
          "..#######..",
          ".#########.",
          ".#########.",
          "..#######..",
          "...#####..."),
    # A tree: a broad canopy over a short trunk.
    "g": ("...#####...",
          "..#######..",
          ".#########.",
          ".#########.",
          ".#########.",
          "..#######..",
          "...#####...",
          ".....#.....",
          ".....#.....",
          "....###....",
          "...#####..."),
    # Colorless has no element to draw, so it is the four-pointed star the frame gives it.
    "c": (".....#.....",
          "....###....",
          "....###....",
          "...#####...",
          "..#######..",
          "###########",
          "..#######..",
          "...#####...",
          "....###....",
          "....###....",
          ".....#....."),
    # Snow: arms out of a middle, in every direction.
    "s": (".....#.....",
          ".#...#...#.",
          "..#..#..#..",
          "...#.#.#...",
          "....###....",
          "###########",
          "....###....",
          "...#.#.#...",
          "..#..#..#..",
          ".#...#...#.",
          ".....#....."),
    # Tapping is a turn: a hook with an arrowhead on the end of it.
    "tap": ("...........",
            "....#####..",
            "...##...##.",
            "..##.....#.",
            "..#........",
            "..#........",
            "..#..#.....",
            "..#.###....",
            "..######...",
            "...####....",
            "....##....."),
    # Untapping is the same turn going back.
    "untap": ("...........",
              "..#####....",
              ".##...##...",
              ".#.....##..",
              "........#..",
              "........#..",
              ".....#..#..",
              "....###.#..",
              "...######..",
              "....####...",
              ".....##...."),
    # Energy, as the bolt it is counted in.
    "energy": (".....###...",
               "....###....",
               "...###.....",
               "..###......",
               ".########..",
               "....#####..",
               "...####....",
               "..####.....",
               ".###.......",
               ".##........",
               ".#........."),
    # Phyrexian mana, as a sigil of our own rather than theirs.
    "p": (".....#.....",
          "...#####...",
          "..##.#.##..",
          ".##..#..##.",
          ".#...#...#.",
          ".#...#...#.",
          ".#...#...#.",
          ".##..#..##.",
          "..##.#.##..",
          "...#####...",
          ".....#....."),
}


def over(under, above):
    """Alpha-composites `above` onto `under`, both RGBA tuples."""
    alpha = above[3] / 255.0
    if alpha <= 0:
        return under
    if under[3] == 0:
        return above[:3] + (above[3],)
    return tuple(round(above[i] * alpha + under[i] * (1 - alpha)) for i in range(3)) + (
        max(under[3], above[3]),
    )


def disc_sample(fx, fy, size, left, right, split):
    """The disc's color at one point, or transparent outside it.

    `split` picks how the two colors are divided: None for a plain disc, "diagonal" for a
    hybrid, "slash" for the Phyrexian bar.
    """
    center = size / 2.0
    radius = center - 1.0
    dx = fx - center
    dy = fy - center
    distance = (dx * dx + dy * dy) ** 0.5
    if distance > radius:
        return (0, 0, 0, 0)

    color = left
    if split == "diagonal" and (dx - dy) > 0:
        color = right
    body = color + (255,)
    if distance > radius - 1.5:
        # A dark rim so a pale disc still reads against a pale background.
        return over(body, SYMBOL_RIM)
    return body


def draw_face(px, glyph, size, scale, offset_x, offset_y):
    stamp(px, FACE.get(glyph), size, scale, offset_x, offset_y)


def draw_picture(px, name, size, scale, offset_x, offset_y):
    stamp(px, PICTURE.get(name), size, scale, offset_x, offset_y)


def stamp(px, rows, size, scale, offset_x, offset_y):
    """Inks a bitmap onto the disc, centered, clipped to the disc's own shape."""
    if rows is None:
        return
    width = len(rows[0]) * scale
    height = len(rows) * scale
    origin_x = round((size - width) / 2) + offset_x
    origin_y = round((size - height) / 2) + offset_y
    for row, bits in enumerate(rows):
        for column, bit in enumerate(bits):
            if bit != "#":
                continue
            for sy in range(scale):
                for sx in range(scale):
                    x = origin_x + column * scale + sx
                    y = origin_y + row * scale + sy
                    if 0 <= x < size and 0 <= y < size and px[y][x][3] > 0:
                        px[y][x] = rgba(GLYPH_INK)


def symbol(name):
    size = SYMBOL_SIZE
    px = blank(size, size)

    left = right = GENERIC
    split = None
    glyphs = []
    picture = None

    if name in MANA_COLORS:
        left = right = MANA_COLORS[name]
        picture = name
    elif name in ("tap", "untap", "energy"):
        picture = name
    elif name in ("x", "y", "z"):
        # Letters on a real card too: X is a letter, and drawing a picture of one would be
        # inventing a symbol nobody has seen.
        glyphs = [name.upper()]
    elif name.isdigit():
        glyphs = list(name)
    elif len(name) == 2 and name[1] == "p":
        left = right = MANA_COLORS[name[0]]
        picture = "p"
    elif len(name) == 2 and name[0] == "2":
        left, right = GENERIC, MANA_COLORS[name[1]]
        split = "diagonal"
        glyphs = []
    elif len(name) == 2:
        left, right = MANA_COLORS[name[0]], MANA_COLORS[name[1]]
        split = "diagonal"
        glyphs = []

    for y in range(size):
        for x in range(size):
            r = g = b = a = 0.0
            for sy in range(SUPERSAMPLE):
                for sx in range(SUPERSAMPLE):
                    sample = disc_sample(
                        x + (sx + 0.5) / SUPERSAMPLE,
                        y + (sy + 0.5) / SUPERSAMPLE,
                        size, left, right, split,
                    )
                    r += sample[0] * sample[3]
                    g += sample[1] * sample[3]
                    b += sample[2] * sample[3]
                    a += sample[3]
            samples = SUPERSAMPLE * SUPERSAMPLE
            px[y][x] = (0, 0, 0, 0) if a <= 0 else (
                round(r / a), round(g / a), round(b / a), round(a / samples))

    if picture is not None:
        # Scale 2 gives an 18x18 picture inside a 30px disc. Three would be 27 and would ride
        # over the rim; the pictures are square where a letter is tall, so they cannot use the
        # room a letter can.
        draw_picture(px, picture, size, 2, 0, 0)
    elif len(glyphs) == 1:
        # Scale 3 gives a 15x21 glyph inside a 30px disc: clear of the rim at the corners,
        # where a taller one pokes through and the symbol stops looking like a disc.
        draw_face(px, glyphs[0], size, 3, 0, 0)
    elif len(glyphs) == 2:
        # Two digits share the disc, so they go smaller and side by side.
        draw_face(px, glyphs[0], size, 2, -6, 0)
        draw_face(px, glyphs[1], size, 2, 6, 0)

    write_png(
        f"common/src/main/resources/assets/gathering/textures/font/mana/{name}.png",
        size, size, px)


# Must match dev.gathering.core.text.ManaSymbols.NAMES, in order: the index is the glyph.
SYMBOL_NAMES = (
    ["w", "u", "b", "r", "g", "c", "s"]
    + [str(n) for n in range(0, 21)]
    + ["x", "y", "z", "tap", "untap", "energy"]
    + ["wu", "wb", "ub", "ur", "br", "bg", "rg", "rw", "gw", "gu"]
    + ["2w", "2u", "2b", "2r", "2g"]
    + ["wp", "up", "bp", "rp", "gp"]
)

# Where the glyphs start, matching ManaSymbols.FIRST_CODEPOINT.
FIRST_CODEPOINT = 0xE000


def symbols():
    providers = []
    for index, name in enumerate(SYMBOL_NAMES):
        symbol(name)
        providers.append(
            '    {\n'
            '      "type": "bitmap",\n'
            f'      "file": "gathering:font/mana/{name}.png",\n'
            '      "ascent": 8,\n'
            '      "height": 9,\n'
            f'      "chars": ["\\u{FIRST_CODEPOINT + index:04X}"]\n'
            "    }"
        )
    path = "common/src/main/resources/assets/gathering/font/mana.json"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as handle:
        handle.write('{\n  "providers": [\n' + ",\n".join(providers) + "\n  ]\n}\n")
    print("wrote", path, f"({len(providers)} glyphs)")


# ---------------------------------------------------------------------------
# Block textures.
# ---------------------------------------------------------------------------
def felt(size=16):
    """A woven felt surface.

    Noise from a hash of the coordinates rather than a random number generator, so running
    this twice produces the same file and the repository does not churn a texture every time
    somebody regenerates the set.
    """
    px = blank(size, size)
    base = PALETTE["felt"]
    weave = PALETTE["felt_weave"]
    for y in range(size):
        for x in range(size):
            h = (x * 73856093) ^ (y * 19349663)
            h = (h ^ (h >> 13)) & 0xFFFF
            # A faint diagonal thread, plus per-pixel grain.
            thread = ((x + y) % 4 == 0)
            grain = (h % 7) - 3
            source = weave if thread else base
            px[y][x] = tuple(max(0, min(255, c + grain)) for c in source) + (255,)
    write_png(
        "common/src/main/resources/assets/gathering/textures/block/table_felt.png",
        size, size, px)


def collection(size=16):
    """A drawer front and a lid, for the block a collection lives in.

    A placeholder: flat panels with a brass lip and a handle, enough to read as a cabinet
    from across a room and no more. The real one is somebody's to draw.
    """
    base = PALETTE["cabinet"]
    dark = PALETTE["cabinet_dark"]
    lip = PALETTE["cabinet_lip"]

    def grained(x, y, color):
        h = (x * 73856093) ^ (y * 19349663)
        h = (h ^ (h >> 13)) & 0xFFFF
        grain = (h % 5) - 2
        return tuple(max(0, min(255, c + grain)) for c in color) + (255,)

    side = blank(size, size)
    for y in range(size):
        for x in range(size):
            edge = x == 0 or y == 0 or x == size - 1 or y == size - 1
            side[y][x] = grained(x, y, lip if edge else base)
    # Two drawers with a handle each.
    for drawer in (3, 10):
        for x in range(2, size - 2):
            side[drawer][x] = grained(x, drawer, dark)
            side[drawer + 4][x] = grained(x, drawer + 4, dark)
        for x in range(6, 10):
            side[drawer + 2][x] = grained(x, drawer + 2, lip)

    top = blank(size, size)
    for y in range(size):
        for x in range(size):
            edge = x == 0 or y == 0 or x == size - 1 or y == size - 1
            top[y][x] = grained(x, y, lip if edge else dark)

    out = "common/src/main/resources/assets/gathering/textures/block/"
    write_png(out + "collection.png", size, size, side)
    write_png(out + "collection_top.png", size, size, top)


def shop_counter(size=16):
    """A wooden counter with a glass front, for the block a shopkeeper works behind.

    A placeholder: a panelled side, a display window on the front and a worn top. Enough to
    read as a shop counter across a room and no more. The real one is somebody's to draw.
    """
    base = PALETTE["counter"]
    dark = PALETTE["counter_dark"]
    top_wood = PALETTE["counter_top"]
    glass = PALETTE["counter_glass"]

    def grained(x, y, color):
        h = (x * 83492791) ^ (y * 29863331)
        h = (h ^ (h >> 13)) & 0xFFFF
        grain = (h % 5) - 2
        return tuple(max(0, min(255, c + grain)) for c in color) + (255,)

    def panelled(window):
        px = blank(size, size)
        for y in range(size):
            for x in range(size):
                edge = x == 0 or y == 0 or x == size - 1 or y == size - 1
                px[y][x] = grained(x, y, dark if edge else base)
        # The worktop, along the top two rows.
        for y in (1, 2):
            for x in range(1, size - 1):
                px[y][x] = grained(x, y, top_wood)
        if window:
            # A display case below the top, with a frame around it.
            for y in range(5, size - 3):
                for x in range(3, size - 3):
                    px[y][x] = grained(x, y, glass)
            for y in range(5, size - 3):
                px[y][3] = grained(3, y, dark)
                px[y][size - 4] = grained(size - 4, y, dark)
        else:
            # A plain panel, so the back and sides do not pretend to be glass.
            for y in range(6, size - 4):
                for x in range(4, size - 4):
                    px[y][x] = grained(x, y, dark)
        return px

    top = blank(size, size)
    for y in range(size):
        for x in range(size):
            edge = x == 0 or y == 0 or x == size - 1 or y == size - 1
            top[y][x] = grained(x, y, dark if edge else top_wood)

    out = "common/src/main/resources/assets/gathering/textures/block/"
    write_png(out + "shop_counter.png", size, size, panelled(False))
    write_png(out + "shop_counter_front.png", size, size, panelled(True))
    write_png(out + "shop_counter_top.png", size, size, top)


def sealed_box(size=16):
    """A shrink-wrapped box, for anything bigger than a booster.

    A placeholder: a brown carton seen at a slight angle with a printed band across it. The
    real one is somebody's to draw, and there may end up being one per shape of product.
    """
    card = PALETTE["box"]
    shade = PALETTE["box_dark"]
    band = PALETTE["box_band"]
    px = blank(size, size)
    for y in range(3, size - 1):
        for x in range(2, size - 2):
            px[y][x] = card + (255,)
    # The lid, lighter, along the top.
    for y in range(2, 4):
        for x in range(3, size - 1):
            px[y][x] = tuple(min(255, c + 22) for c in card) + (255,)
    # The right-hand face, in shadow, so it reads as a box rather than a rectangle.
    for y in range(3, size - 1):
        px[y][size - 3] = shade + (255,)
        px[y][size - 4] = shade + (255,)
    # A printed band across the front.
    for y in range(8, 11):
        for x in range(2, size - 4):
            px[y][x] = band + (255,)
    write_png(
        "common/src/main/resources/assets/gathering/textures/item/sealed.png",
        size, size, px)


def main():
    nine_slice("panel", PALETTE["panel"], PALETTE["panel_edge"])
    nine_slice("panel_inset", PALETTE["inset"], PALETTE["inset_edge"])
    nine_slice("row_highlight", PALETTE["accent_dim"], PALETTE["accent"])
    deck_panel()
    scrollbar()
    symbols()
    felt()
    collection()
    shop_counter()
    sealed_box()


if __name__ == "__main__":
    main()
