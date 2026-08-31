#!/usr/bin/env python3
"""Paints the mod's GUI art: every element, once per look.

Every rectangle the mod draws is a PNG under
``assets/gathering/textures/gui/sprites/<look>/<element>.png``. This paints all of them.

The style is the one the mana orbs are drawn in, which is the style a Minecraft interface is
drawn in: a hard near-black outline, a lit bevel along the top and left, a flat body, a shaded
bevel along the bottom and right. Nothing is soft and nothing is a gradient, because at the
size these are drawn a gradient is three colors that look like a mistake.

Bodies stay dark in every look. Screens draw their text in fixed light colors - cream on the
panels, grey for the quiet lines - and a look that turned a panel pale would be a look nobody
could read. So a look is carried by its outline, its bevel and its accent, which is also how
the dark half of a pixel-art button sheet does it.

A look is a palette and nothing else; every element is painted from it by the same code. That
is what keeps eight of them consistent, and what makes a ninth an entry in one table.

Run it from the repo root:  python3 tools/gui_art.py
"""

import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPRITES = os.path.join(
    ROOT, "common", "src", "main", "resources", "assets", "gathering",
    "textures", "gui", "sprites")
THEME_FILES = os.path.join(
    ROOT, "common", "src", "main", "resources", "assets", "gathering", "gui_themes")

# ---------------------------------------------------------------- the palette


# The three ways one color becomes another. Everything below is built out of these.
def rgba(color, alpha=255):
    return (color[0], color[1], color[2], alpha)


def mix(one, two, amount):
    return tuple(round(a + (b - a) * amount) for a, b in zip(one[:3], two[:3]))


def darker(color, amount):
    return mix(color, (0, 0, 0), amount)


def lighter(color, amount):
    return mix(color, (255, 255, 255), amount)


#: What every screen writes with, and the least contrast a field carrying it may have.
#: 4.2 rather than 4.5 because the font draws a shadow, which the arithmetic cannot see.
TEXT = (0xE8, 0xE4, 0xDC)
LEAST_CONTRAST = 4.2


def luminance(color):
    def channel(value):
        v = value / 255.0
        return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4
    return (0.2126 * channel(color[0]) + 0.7152 * channel(color[1])
            + 0.0722 * channel(color[2]))


def contrast(one, two):
    a, b = luminance(one), luminance(two)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)


def readable(color, least=LEAST_CONTRAST):
    """The same color, darkened just until the text on it can be read.

    A look is free to be as pale or as saturated as it likes right up to the point where a
    name on it stops being a name. This is where that point is, applied rather than trusted -
    Bubble came out at 2.8 to one and looked lovely and said nothing.
    """
    for step in range(0, 70):
        tone = darker(color, step / 100.0)
        if contrast(tone, TEXT) >= least:
            return tone
    return tone


class Look:
    """One look's colors. Everything drawn below comes out of these and nothing else."""

    def __init__(self, name, order, ink, bevel, shade, body, sunk, accent, glow,
                 warn, good, paper, rule, cloth, wash, style="flat"):
        self.name = name
        self.order = order
        self.style = style      # how its edges are built - see plate()
        self.ink = ink          # the outline, on everything
        self.bevel = bevel      # lit edge, top and left
        self.shade = shade      # shaded edge, bottom and right
        self.body = readable(body)   # the flat middle of a raised thing
        self.sunk = readable(sunk)   # and of a recessed one, which is darker
        self.accent = accent    # focus, selection, the cursor
        self.glow = glow        # the accent's light end, for a two-tone ring
        self.warn = warn        # where a thing is about to land, and what is set
        self.good = good        # a bar filling
        self.paper = paper      # card stock, which is light because a card is
        self.rule = rule        # the line inside it
        self.cloth = cloth      # the table behind everything
        self.wash = wash        # what the scrims and tints are tinted toward


LOOKS = {
    # Neutral on purpose: warm stone and a cool accent, so nothing in it argues with card art.
    "basic": Look(
        "Basic", 0,
        ink=(0x14, 0x13, 0x12), bevel=(0xA0, 0x9A, 0x90), shade=(0x26, 0x24, 0x21),
        body=(0x3E, 0x3B, 0x37), sunk=(0x1E, 0x1D, 0x1B),
        accent=(0x7F, 0xB6, 0xC8), glow=(0xC7, 0xE6, 0xF0),
        warn=(0xE0, 0xB1, 0x5A), good=(0x6F, 0xA8, 0x76),
        paper=(0xEF, 0xE9, 0xDC), rule=(0xB6, 0xA9, 0x8C),
        cloth=(0x33, 0x38, 0x35), wash=(0x1E, 0x1D, 0x1A)),

    "blue": Look(
        "Blue", 10,
        ink=(0x0C, 0x14, 0x1E), bevel=(0x6E, 0xA8, 0xD4), shade=(0x18, 0x2C, 0x40),
        body=(0x28, 0x44, 0x5E), sunk=(0x14, 0x24, 0x33),
        accent=(0x62, 0xD6, 0xF0), glow=(0xBF, 0xEE, 0xFA),
        warn=(0xE6, 0xC2, 0x6A), good=(0x62, 0xC0, 0xA6),
        paper=(0xE6, 0xEE, 0xF6), rule=(0x9C, 0xB4, 0xCA),
        cloth=(0x1B, 0x30, 0x42), wash=(0x0E, 0x1E, 0x2E)),

    "red": Look(
        "Red", 20,
        ink=(0x1A, 0x0C, 0x0C), bevel=(0xD8, 0x84, 0x6E), shade=(0x40, 0x1C, 0x18),
        body=(0x5C, 0x2A, 0x24), sunk=(0x33, 0x16, 0x13),
        accent=(0xF0, 0x8A, 0x62), glow=(0xFA, 0xCE, 0xB6),
        warn=(0xF0, 0xC4, 0x70), good=(0xB4, 0xA8, 0x58),
        paper=(0xF4, 0xE7, 0xDF), rule=(0xC6, 0x9C, 0x8C),
        cloth=(0x3A, 0x1E, 0x1A), wash=(0x2A, 0x10, 0x0E)),

    "yellow": Look(
        "Yellow", 30,
        ink=(0x1A, 0x14, 0x08), bevel=(0xD8, 0xB4, 0x62), shade=(0x40, 0x32, 0x14),
        body=(0x5C, 0x48, 0x1E), sunk=(0x33, 0x28, 0x11),
        accent=(0xF2, 0xCE, 0x6E), glow=(0xFC, 0xEE, 0xC0),
        warn=(0xF2, 0xA0, 0x50), good=(0x9E, 0xB4, 0x58),
        paper=(0xF6, 0xEE, 0xD8), rule=(0xC6, 0xB0, 0x82),
        cloth=(0x3A, 0x30, 0x18), wash=(0x2A, 0x20, 0x0A)),

    "pink": Look(
        "Pink", 40,
        ink=(0x18, 0x0C, 0x16), bevel=(0xD8, 0x84, 0xC2), shade=(0x3C, 0x1A, 0x34),
        body=(0x56, 0x28, 0x4C), sunk=(0x30, 0x16, 0x2A),
        accent=(0xF6, 0x8E, 0xD4), glow=(0xFC, 0xCE, 0xEE),
        warn=(0xF2, 0xC4, 0x88), good=(0x8E, 0xC6, 0xB0),
        paper=(0xF6, 0xE6, 0xF0), rule=(0xC6, 0x98, 0xB6),
        cloth=(0x34, 0x1A, 0x2E), wash=(0x24, 0x0E, 0x1E)),

    # Future Sight: bone and pale silver, thin warm-grey lines, a cool blue in the corner of
    # it. High-key art on a dark body, which is as close as a readable panel gets to that frame.
    "future": Look(
        "Future Sight", 50,
        ink=(0x22, 0x20, 0x1C), bevel=(0xF0, 0xEA, 0xD8), shade=(0x44, 0x43, 0x40),
        body=(0x56, 0x56, 0x56), sunk=(0x33, 0x33, 0x34),
        accent=(0xA8, 0xC4, 0xD8), glow=(0xE8, 0xF2, 0xF8),
        warn=(0xD8, 0xB8, 0x88), good=(0x9C, 0xB8, 0xA0),
        paper=(0xF4, 0xEF, 0xE2), rule=(0xC0, 0xB6, 0xA0),
        cloth=(0x3E, 0x3C, 0x36), wash=(0x26, 0x25, 0x22), style="future"),

    # Bubble: rounded and sweet. Its bevel is wider and brighter than anything else here,
    # which is what makes a flat rectangle read as blown rather than cut.
    "bubble": Look(
        "Bubble", 60,
        ink=(0x14, 0x1C, 0x24), bevel=(0xA8, 0xE8, 0xE0), shade=(0x24, 0x40, 0x48),
        body=(0x30, 0x56, 0x5E), sunk=(0x1A, 0x30, 0x36),
        accent=(0x8E, 0xF0, 0xD8), glow=(0xDE, 0xFC, 0xF4),
        warn=(0xF6, 0xB0, 0xD0), good=(0x9E, 0xE8, 0xA8),
        paper=(0xE8, 0xF8, 0xF6), rule=(0xA0, 0xC8, 0xC4),
        cloth=(0x1E, 0x3A, 0x40), wash=(0x10, 0x26, 0x2C), style="bubble"),

    # Retro: the brown-bordered frame. Sepia, tarnished gold, and a parchment stock.
    "retro": Look(
        "Retro", 70,
        ink=(0x18, 0x10, 0x0A), bevel=(0xC2, 0xA0, 0x70), shade=(0x36, 0x26, 0x18),
        body=(0x4E, 0x3A, 0x26), sunk=(0x2C, 0x20, 0x15),
        accent=(0xD9, 0xA4, 0x41), glow=(0xF2, 0xDC, 0xA8),
        warn=(0xE0, 0x8C, 0x44), good=(0x8C, 0x9E, 0x58),
        paper=(0xEE, 0xE0, 0xBE), rule=(0xA8, 0x8C, 0x5C),
        cloth=(0x3A, 0x2A, 0x1A), wash=(0x26, 0x1A, 0x0E), style="retro"),
}

#: The one every other look falls back to, and the only one that has to be complete.
BASE = "basic"

# ---------------------------------------------------------------- the template

#: What the template look marks the edge of every texture with. Nothing else in the mod is
#: this color, so a magenta line on screen is always the edge of a sprite.
BOUNDS = (0xFF, 0x00, 0xFF, 0xFF)

#: And where a nine-slice cuts, which is the other thing an artist has to know before they
#: start: paint outside these lines and it will not stretch the way you expect.
GUIDE = (0x00, 0xE5, 0xFF, 0xC0)

#: What the art is laid over, so an element that is nearly transparent - a tint, a wash, a
#: ring round nothing - still opens as something you can see and measure.
STENCIL = (0x3A, 0x3A, 0x3A, 0xFF)


def stencil(image, kind, size, border):
    """The base art, made visible, with its edges and its nine-slice cuts drawn on.

    Every element gets one. The point is that no file in the set opens as a blank transparent
    square: a tint that is six percent alpha is invisible in an image editor, and the elements
    that are invisible are exactly the ones somebody would want a template for.
    """
    wide, high = image.size
    out = Image.new("RGBA", (wide, high), STENCIL)
    out.alpha_composite(image.convert("RGBA"))
    paint = ImageDraw.Draw(out)
    if kind == "nine_slice":
        for at in (border, wide - border):
            paint.line([(at, 0), (at, high - 1)], fill=GUIDE)
        for at in (border, high - border):
            paint.line([(0, at), (wide - 1, at)], fill=GUIDE)
    paint.rectangle([0, 0, wide - 1, high - 1], outline=BOUNDS)
    return out


# ---------------------------------------------------------------- painting


# How round each style's corners are, in pixels of a 32-wide source.
# Never more than a quarter of the source, which is exactly the nine-slice border on all three
# sizes the mod uses (32/8, 16/4, 8/2). Round further than the border and the curve spills into
# the stretched edge strips, which then repeat down a long panel as a row of scallops.
# How round each style's corners are, in pixels of a 32-wide source. Never more than a quarter
# of it, which is exactly the nine-slice border at all three sizes the mod uses (32/8, 16/4,
# 8/2): round further than the border and the curve spills into the stretched edge strips,
# which then repeat down a long panel as a row of scallops.
ROUNDING = {"flat": 0, "retro": 0, "future": 8, "bubble": 8}

#: Future Sight rounds two opposite corners hard and leaves the other two nearly square. That
#: asymmetry is the frame: a sweep entering at one corner and leaving at the other, rather than
#: a box with its edges filed off. As (top-left, top-right, bottom-right, bottom-left).
SWEEP = (1.0, 0.18, 1.0, 0.18)

#: The noise repeats on this, so a nine-slice's tiled middle joins itself without a seam.
PERIOD = 16


def scatter(x, y, seed):
    """A repeatable number in 0..1 for a lattice point, wrapped so the noise tiles."""
    x, y = x % (PERIOD + 1), y % (PERIOD + 1)
    value = (x * 374761393 + y * 668265263 + seed * 1013904223) & 0xFFFFFFFF
    value = ((value ^ (value >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((value ^ (value >> 16)) & 0xFFFF) / 0xFFFF


def smooth(x, y, cell, seed):
    """Value noise: a lattice of random numbers, smoothly interpolated. Blotches rather than
    static, which is what stone and board look like and what a per-pixel speckle never does."""
    gx, gy = x / cell, y / cell
    x0, y0 = int(gx), int(gy)
    fx, fy = gx - x0, gy - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    top = scatter(x0, y0, seed) + (scatter(x0 + 1, y0, seed) - scatter(x0, y0, seed)) * fx
    low = (scatter(x0, y0 + 1, seed)
           + (scatter(x0 + 1, y0 + 1, seed) - scatter(x0, y0 + 1, seed)) * fx)
    return top + (low - top) * fy


def mottle(x, y, seed=0):
    """Two octaves of it, centered on zero: the mottling on an old card's border."""
    return (smooth(x, y, 4, seed) * 0.62 + smooth(x, y, 2, seed + 31) * 0.38) - 0.5


def weathered(color, x, y, amount, seed=0):
    """A color with the mottling laid over it."""
    step = mottle(x, y, seed) * 2 * amount
    return mix(color, (255, 255, 255) if step > 0 else (0, 0, 0), abs(step))


def sideOf(x, y, size):
    """Which edge of a rectangle a pixel belongs to. A bevel that only knows a diagonal cannot
    put a gloss along the top and a bounce along the bottom, which is most of what makes a
    surface look like one."""
    room = ((x, "left"), (size - 1 - x, "right"), (y, "top"), (size - 1 - y, "bottom"))
    return min(room)[1]


def depthAt(x, y, size, radius, sweep=None):
    """How many pixels inside the shape a pixel is, or None if the rounding cut it away.

    Each corner may round by a different amount, which is how a frame gets a sweep rather than
    a fillet: two corners open right up and the other two stay nearly square.
    """
    depth = min(x, y, size - 1 - x, size - 1 - y)
    if radius <= 0:
        return depth
    left, top = x < size / 2, y < size / 2
    which = 0 if (left and top) else 1 if top else 2 if not left else 3
    here = radius * (sweep[which] if sweep else 1.0)
    if here < 1.0:
        return depth
    cx = here - 0.5 if left else size - 0.5 - here
    cy = here - 0.5 if top else size - 0.5 - here
    if (x - cx) * (1 if left else -1) > 0 or (y - cy) * (1 if top else -1) > 0:
        return depth
    away = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
    if away > here - 0.15:
        return None
    return min(depth, int(here - away))


def cornerDepth(x, y, size):
    """How far a pixel is from the nearest corner, along the diagonal."""
    return min(x, size - 1 - x) + min(y, size - 1 - y)


def bandOf(size):
    """How wide a nine-slice's border is, from the size of its source.

    Every nine-slice the mod uses is a quarter: 32/8, 16/4, 8/2. It matters because everything
    at that depth or deeper is the stretched middle, so a frame drawn without knowing it comes
    out fine on a panel and swallows a button whole.
    """
    return max(2, size // 4)


def ramp(tones, steps):
    """A run of colors from the first tone to the second, for a band that has to read as
    metal rather than as a line."""
    first, last = tones
    return [mix(first, last, step / max(1, steps - 1)) for step in range(steps)]


# ---------------------------------------------------------------------------
# Four constructions. Three of the looks are a card frame rather than a color, so they are
# built rather than tinted.
# ---------------------------------------------------------------------------

def flatPlate(size, tones, sunken, alpha):
    """The Minecraft button: outline, one lit pixel, one quiet step, body."""
    ink, lit, low, body = tones
    top, bottom = (low, lit) if sunken else (lit, low)
    band = bandOf(size)
    image = Image.new("RGBA", (size, size))
    pixels = image.load()
    for y in range(size):
        for x in range(size):
            depth = min(x, y, size - 1 - x, size - 1 - y)
            outward = top if x + y < size - 1 else bottom
            if depth >= band:
                tone = body
            elif depth == 0:
                tone = ink
            elif depth == 1:
                tone = outward
            elif depth == 2:
                tone = mix(body, outward, 0.45)
            elif depth == 3:
                tone = mix(body, outward, 0.18)
            else:
                tone = body
            pixels[x, y] = rgba(tone, alpha)
    return image


def futurePlate(size, tones, sunken, alpha, glow):
    """The Future Sight frame.

    Four things make that frame what it is and all four are here. Two opposite corners open
    into a wide sweep while the other two stay nearly square, so the border reads as something
    entering and leaving rather than as a box. The band itself is chrome - four steps from
    near-white at the outer edge down into shadow, not one bevel line - and it closes on a dark
    inner edge. Inside that the field is left plain for a pixel and then a single hairline is
    struck, which is the doubled line that frame draws everywhere. And a boss is set into each
    long edge, the row of little discs running down the sweep; the edges of a nine-slice tile,
    so one boss in the art is a row of them down the panel.
    """
    ink, lit, low, body = tones
    band = bandOf(size)
    radius = min(ROUNDING["future"], band)
    steps = max(1, band - 3)
    chrome = ramp((lit, mix(low, body, 0.4)), steps)
    if sunken:
        chrome = list(reversed(chrome))
    image = Image.new("RGBA", (size, size))
    pixels = image.load()
    middle = size / 2.0
    for y in range(size):
        for x in range(size):
            depth = depthAt(x, y, size, radius, SWEEP)
            if depth is None:
                continue
            if depth >= band:
                tone = body
            elif depth == 0:
                tone = mix(ink, body, 0.35)
            elif depth <= steps:
                tone = chrome[depth - 1]
            elif depth == steps + 1:
                tone = mix(ink, body, 0.55)
            elif depth == band - 1 and band >= 7:
                tone = mix(lit, body, 0.45)
            else:
                tone = body
            pixels[x, y] = rgba(tone, alpha)

    # The bosses: a small disc set into the middle of each long edge, ringed like the ones
    # running down that frame's sweep. Only where the band is wide enough to hold one.
    if size >= 32:
        for at in ((3.0, middle), (size - 3.0, middle), (middle, 3.0), (middle, size - 3.0)):
            for y in range(size):
                for x in range(size):
                    if pixels[x, y][3] == 0:
                        continue
                    away = ((x + 0.5 - at[0]) ** 2 + (y + 0.5 - at[1]) ** 2) ** 0.5
                    if away < 1.4:
                        pixels[x, y] = rgba(glow, alpha)
                    elif away < 2.4:
                        pixels[x, y] = rgba(mix(ink, body, 0.5), alpha)
    return image


def retroPlate(size, tones, sunken, alpha, rule):
    """The old card border.

    A raised band of mottled stone, lit across its width rather than by one bevel line: light
    at the outer edge, mid, then shadow, so the frame has thickness. A groove cut into it, a
    hairline of tarnished gold holding the field, and a dark line under the gold so the gold
    sits in something. The field inside is board with a finer grain, because the border and the
    card face are two materials and they were two materials on the card.

    Then a stud at each corner, which is the ornament that frame steps into its corners.
    """
    ink, lit, low, body = tones
    width = bandOf(size)
    steps = max(1, width - 5)
    stone = ramp((lit, low), steps)
    if sunken:
        stone = list(reversed(stone))
    image = Image.new("RGBA", (size, size))
    pixels = image.load()
    for y in range(size):
        for x in range(size):
            depth = min(x, y, size - 1 - x, size - 1 - y)
            if depth >= width:
                tone = weathered(body, x, y, 0.04, 11)
            elif depth == 0:
                tone = ink
            elif depth <= steps:
                tone = weathered(stone[depth - 1], x, y, 0.18, 3)
            elif depth == steps + 1 and width >= 6:
                tone = ink
            elif depth == width - 2 or (width < 6 and depth == steps + 1):
                tone = rule
            elif depth == width - 1:
                tone = darker(ink, 0.2)
            else:
                tone = weathered(body, x, y, 0.04, 11)
            pixels[x, y] = rgba(tone, alpha)

    # The stud: a small block of gold set into each corner of the band, with a dark eye in it.
    if size >= 16:
        reach = width // 2 + 2
        for y in range(size):
            for x in range(size):
                depth = min(x, y, size - 1 - x, size - 1 - y)
                near = cornerDepth(x, y, size)
                if 0 < depth <= max(1, steps) and near <= reach:
                    pixels[x, y] = rgba(rule if near > 1 else darker(rule, 0.55), alpha)
    return image


def bubblePlate(size, tones, sunken, alpha, glow):
    """Blown rather than cut.

    A surface, not an edge. The rim is bright all round and brightest along the top, the whole
    upper band is glossed toward the light, the bottom band carries the bounce that comes back
    off whatever it is sitting on, and a specular sits off the top-left corner rather than on
    it. The gloss and the bounce depend only on which edge a pixel is on and how deep it is, so
    they stay uniform along a nine-slice's strips and a wide panel keeps one continuous
    highlight along its top rather than a row of repeats.
    """
    ink, lit, low, body = tones
    band = bandOf(size)
    radius = min(ROUNDING["bubble"], band)
    image = Image.new("RGBA", (size, size))
    pixels = image.load()
    shine = min(1.0, luminance(body) * 5.0)
    for y in range(size):
        for x in range(size):
            depth = depthAt(x, y, size, radius)
            if depth is None:
                continue
            side = sideOf(x, y, size)
            up = side == "top"
            down = side == "bottom"
            if sunken:
                up, down = down, up
            if depth >= band:
                tone = body
            elif depth == 0:
                tone = mix(ink, lit, 0.45 if up else 0.15)
            elif depth == 1:
                tone = lighter(lit, 0.35) if up else lit
            elif depth == 2:
                tone = mix(body, lit, 0.7 if up else 0.42)
            elif up:
                tone = mix(body, lit, 0.42 * (band - depth) / max(1, band - 3))
            elif down:
                tone = mix(body, glow, 0.16)
            else:
                tone = mix(body, low, 0.3) if depth == 3 else body
            # The specular, kept inside the top-left corner tile. A nine-slice repeats
            # everything else, and a highlight in the repeated part is not a highlight - it is
            # a field of little chevrons across the whole panel, which is what it was.
            if not sunken and shine and x < band and y < band:
                away = (((x + 0.5) - band * 0.46) ** 2
                        + ((y + 0.5) - band * 0.42) ** 2) ** 0.5
                if away < band * 0.4 and depth >= 2:
                    tone = mix(tone, glow, (0.6 if away < band * 0.22 else 0.34) * shine)
            pixels[x, y] = rgba(tone, alpha)
    return image


def plate(size, look, sunken=False, body=None, alpha=255, ink=None, lit=None, low=None):
    """A raised or recessed rectangle, built the way its look builds things."""
    tones = (ink if ink is not None else look.ink,
             lit if lit is not None else look.bevel,
             low if low is not None else look.shade,
             body if body is not None else (look.sunk if sunken else look.body))
    if look.style == "future":
        return futurePlate(size, tones, sunken, alpha, look.glow)
    if look.style == "retro":
        return retroPlate(size, tones, sunken, alpha, look.accent)
    if look.style == "bubble":
        return bubblePlate(size, tones, sunken, alpha, look.glow)
    return flatPlate(size, tones, sunken, alpha)


def bar(size, look, kind):
    """A progress bar, built the way a pixel-art bar is built rather than as a small panel.

    Capped ends, a hard outline, and the fill lit along its top and shaded along its bottom so
    it reads as something sitting in the track rather than as a second rectangle drawn over
    it. Drawn from the same palette as everything else; the shape is the idiom, which is worth
    having whatever look is on, so unlike the panels this one does not vary by style.
    """
    ink = darker(look.ink, 0.2)
    face = {"track": look.sunk, "fill": look.good, "done": look.warn}[kind]
    band = bandOf(size)
    image = Image.new("RGBA", (size, size))
    pixels = image.load()
    for y in range(size):
        for x in range(size):
            depth = depthAt(x, y, size, band)
            if depth is None:
                continue
            if depth == 0:
                tone = ink
            elif kind == "track":
                # A track is a hole: dark at the top where the wall shades it, and never
                # bright, or a bar that has not started looks like one that has.
                tone = darker(face, 0.35) if y < size / 2 else face
            elif depth == 1:
                tone = lighter(face, 0.45) if y < size / 2 else darker(face, 0.35)
            else:
                tone = face
            pixels[x, y] = rgba(tone)
    return image


def wash(size, color, alpha=255):
    return Image.new("RGBA", (size, size), rgba(color, alpha))


def ring(size, color, alpha=255, thickness=1, fill=None, fill_alpha=0, inner=None):
    """An outline round nothing, or round a wash. The optional inner tone is a second, lighter
    line just inside the first, which is what stops a bright ring reading as a sticker laid on
    top of the thing it is marking."""
    image = Image.new("RGBA", (size, size),
                      rgba(fill, fill_alpha) if fill is not None else (0, 0, 0, 0))
    pixels = image.load()
    for y in range(size):
        for x in range(size):
            depth = min(x, y, size - 1 - x, size - 1 - y)
            if depth < thickness:
                pixels[x, y] = rgba(color, alpha)
            elif inner is not None and depth == thickness:
                pixels[x, y] = rgba(inner, alpha)
    return image


def stock(size, look, dark=False):
    """Blank paper - and in the looks that are a card frame, a small card frame.

    Retro gets the pressed border and the board it is printed on; Future Sight a rounded sheet
    with its hairline and its corner pips; Bubble a rounded slab with the light on it. Which is
    the point: a blank card in a look should look like a card in that look.
    """
    face = darker(look.paper, 0.90) if dark else look.paper
    rule = look.accent if dark else look.rule
    tones = (look.ink, lighter(rule, 0.45), rule, face)
    if look.style == "retro":
        return retroPlate(size, tones, False, 255, rule)
    if look.style == "future":
        return futurePlate(size, tones, False, 255, mix(face, rule, 0.5))
    if look.style == "bubble":
        return bubblePlate(size, tones, False, 255, lighter(face, 0.5))
    image = Image.new("RGBA", (size, size), rgba(face))
    pixels = image.load()
    for y in range(size):
        for x in range(size):
            depth = min(x, y, size - 1 - x, size - 1 - y)
            if depth == 0:
                pixels[x, y] = rgba(look.ink)
            elif depth == 2:
                pixels[x, y] = rgba(rule)
    return image


def cloth(size, look):
    """The table. Never a slab of one color: a weave for the flat looks, mottled board for
    Retro, and for Future Sight a faint rule, because that frame's ground is smooth."""
    image = Image.new("RGBA", (size, size), rgba(look.cloth))
    pixels = image.load()
    for y in range(size):
        for x in range(size):
            if look.style == "retro":
                pixels[x, y] = rgba(weathered(look.cloth, x, y, 0.13, 5))
            elif look.style == "future":
                pixels[x, y] = rgba(mix(look.cloth, look.bevel, 0.04 if (x + y) % 8 == 0 else 0))
            elif look.style == "bubble":
                near = ((x - 8) ** 2 + (y - 8) ** 2) ** 0.5
                pixels[x, y] = rgba(mix(look.cloth, look.bevel, 0.05 if near < 3 else 0))
            elif (x // 2 + y // 2) % 2 == 0:
                pixels[x, y] = rgba(lighter(look.cloth, 0.05))
    return image


#: Where the deck list panel's right edge sits, top and bottom, as a fraction of its width.
TAPER_TOP, TAPER_BOTTOM = 0.90, 0.74


def deck_panel(look, width=256, height=512):
    """The deck list: flush left, tapering right.

    Stretched rather than nine-sliced, which means it is one picture rather than nine tiles -
    so this is the one element that can carry something spanning the whole panel. Each of the
    three built looks uses that for the thing a nine-slice cannot do: Future Sight for the
    sweep its frame is drawn around, Retro for a mottled stone border with a gold rule inside
    it, Bubble for the light running down the whole length of it.
    """
    style = look.style
    image = Image.new("RGBA", (width, height))
    pixels = image.load()
    sweep = width * 0.95
    # Darker than a button: this is a large field with a list of names on it, and the text is
    # drawn in a fixed light color whatever the look.
    field = readable(mix(look.sunk, look.body, 0.30))
    for y in range(height):
        edge = width * (TAPER_TOP + (TAPER_BOTTOM - TAPER_TOP) * (y / height))
        for x in range(width):
            if x > edge:
                continue
            depth = min(x, y, height - 1 - y, edge - x)
            if style == "retro":
                if depth < 2:
                    tone = look.ink
                elif depth < 7:
                    tone = weathered(look.bevel if depth < 4 else look.shade, x, y, 0.20, 3)
                elif depth < 9:
                    tone = look.ink
                elif depth < 11:
                    tone = look.accent
                else:
                    tone = weathered(field, x, y, 0.14, 11)
            elif style == "future":
                # The sweep: one wide arc struck through the panel, the way that frame is laid
                # out around a circle. Two hairlines and a corner pip, as everywhere else.
                away = abs(((x - width * 0.1) ** 2 + (y - height * 0.5) ** 2) ** 0.5 - sweep * 0.9)
                tone = field
                if away < 1.6:
                    tone = mix(field, look.bevel, 0.55)
                elif away < 5:
                    tone = mix(field, look.bevel, 0.16)
                if depth < 2:
                    tone = mix(look.ink, look.body, 0.3)
                elif depth in (4, 5):
                    tone = look.bevel
            elif style == "bubble":
                run = 1.0 - abs((x / max(edge, 1)) - 0.22) * 2.2
                tone = mix(field, look.glow, max(0.0, run) * 0.24)
                if depth < 2:
                    tone = look.ink
                elif depth < 5:
                    tone = look.bevel if x + y < width else look.shade
                elif depth < 7:
                    tone = mix(tone, look.bevel, 0.35)
                elif depth < 9:
                    tone = mix(tone, look.bevel, 0.12)
            else:
                if depth < 2:
                    tone = look.ink
                elif depth < 4:
                    tone = look.bevel if x + y < width else look.shade
                elif depth < 6:
                    tone = mix(field, look.bevel, 0.28)
                elif depth < 8:
                    tone = mix(field, look.bevel, 0.10)
                else:
                    tone = field
            pixels[x, y] = rgba(tone)
    return image


# ---------------------------------------------------------------- the elements

# A nine-slice's inner region is either stretched or tiled. Tiling is one draw call per tile,
# so an element that covers a mat or a whole screen has to stretch: a tiled 16-pixel ring
# round a 200-pixel mat is fifty draw calls a frame for a line.
NINE_32 = ("nine_slice", 32, 8, True)
NINE_16 = ("nine_slice", 16, 4, True)
NINE_8 = ("nine_slice", 8, 2, True)
NINE_32_TILED = ("nine_slice", 32, 8, False)
NINE_16_TILED = ("nine_slice", 16, 4, False)
STRETCH = ("stretch", 0, 0, False)

#: Every element the mod draws, how it stretches, and how it is painted from a look.
#:
#: Keep this in step with ``GatheringSprites.Element`` - ``tools/spritecheck.py`` fails the
#: build if it drifts. The alphas are the ones the Java code used to pass to ``fill``, moved
#: here unchanged: the alpha is the design, and only the color follows the look.
ELEMENTS = [
    # Structure.
    ("panel", NINE_32_TILED, lambda k: plate(32, k)),
    ("panel_inset", NINE_32_TILED, lambda k: plate(32, k, sunken=True)),
    ("row_highlight", NINE_32_TILED,
     lambda k: plate(32, k, body=k.accent, alpha=0x38, ink=k.accent,
                     lit=lighter(k.accent, 0.4), low=k.accent)),
    ("deck_panel", STRETCH, deck_panel),
    ("scroll_track", NINE_16_TILED, lambda k: plate(16, k, sunken=True)),
    ("scroll_thumb", NINE_16_TILED,
     lambda k: plate(16, k, body=darker(k.accent, 0.45), lit=k.glow, low=darker(k.accent, 0.7))),
    ("button", NINE_16, lambda k: plate(16, k, body=mix(k.body, k.bevel, 0.10))),
    ("button_hover", NINE_16,
     lambda k: plate(16, k, body=mix(k.body, k.accent, 0.34), lit=k.glow,
                     low=darker(k.accent, 0.62))),
    ("button_off", NINE_16,
     lambda k: plate(16, k, body=darker(k.body, 0.45), lit=k.shade,
                     low=darker(k.shade, 0.35))),

    # Whole-screen washes.
    ("screen_scrim", STRETCH, lambda k: wash(16, k.wash, 0x80)),
    ("screen_backdrop", STRETCH, lambda k: wash(16, k.wash, 0xE8)),
    ("pack_backdrop", STRETCH, lambda k: wash(16, k.wash, 0xC8)),
    ("sets_backdrop", STRETCH, lambda k: wash(16, k.wash, 0xC0)),
    ("inspect_backdrop", STRETCH, lambda k: wash(16, darker(k.wash, 0.5), 0xE6)),

    # The table.
    ("table_felt", STRETCH, lambda k: cloth(32, k)),
    ("seat_mat", NINE_32, lambda k: ring(32, k.bevel, 0x50, fill=k.bevel, fill_alpha=0x22)),
    ("seat_mat_mine", NINE_32, lambda k: ring(32, k.accent, 0x70, fill=k.accent, fill_alpha=0x30)),
    ("seat_divider", STRETCH, lambda k: wash(16, k.bevel, 0x66)),
    ("zone_border", NINE_16, lambda k: ring(16, k.bevel, 0x66)),
    ("seat_ring", NINE_16, lambda k: ring(16, k.bevel)),
    ("focus_ring", NINE_16, lambda k: ring(16, k.accent, inner=darker(k.accent, 0.55))),
    ("hover_ring", NINE_16, lambda k: ring(16, k.glow)),
    ("chosen_ring", NINE_16, lambda k: ring(16, k.accent, thickness=2, inner=k.glow)),
    ("select_box", NINE_16,
     lambda k: ring(16, k.accent, fill=k.accent, fill_alpha=0x20)),
    ("aimed_pile", NINE_16,
     lambda k: ring(16, k.glow, thickness=2, fill=k.accent, fill_alpha=0x99)),
    ("life_backing", NINE_16, lambda k: plate(16, k, sunken=True, alpha=0xB0)),
    ("talk_backdrop", NINE_16, lambda k: plate(16, k, sunken=True, alpha=0x99)),
    ("talk_typing", NINE_16, lambda k: plate(16, k, sunken=True, alpha=0xCC)),
    ("pile_badge", NINE_16, lambda k: plate(16, k, alpha=0xE0)),
    ("exposed_band", NINE_16,
     lambda k: plate(16, k, body=darker(k.warn, 0.72), alpha=0xC8,
                     lit=darker(k.warn, 0.3), low=darker(k.warn, 0.82))),
    ("counter_band", STRETCH, lambda k: wash(16, darker(k.wash, 0.4), 0xC0)),
    ("tax_backing", STRETCH, lambda k: wash(16, darker(k.wash, 0.4), 0xB0)),
    ("tax_lit", STRETCH, lambda k: wash(16, darker(k.accent, 0.82), 0xD0)),
    ("ghost_tint", STRETCH, lambda k: wash(16, darker(k.wash, 0.6), 0x50)),

    # Cards on the felt.
    ("card_shadow", STRETCH, lambda k: wash(16, (0, 0, 0), 0x99)),
    ("card_cast", STRETCH, lambda k: wash(16, (0, 0, 0), 0x59)),
    ("card_footprint", NINE_16, lambda k: ring(16, k.warn, 0xBF)),
    ("tapped_tint", STRETCH, lambda k: wash(16, (0, 0, 0), 0x60)),
    ("frozen_tint", STRETCH, lambda k: wash(16, lighter(k.glow, 0.25), 0x38)),
    ("frozen_edge", STRETCH, lambda k: wash(16, lighter(k.glow, 0.6))),
    ("name_backdrop", STRETCH, lambda k: wash(16, darker(k.wash, 0.4), 0xC0)),
    ("card_placeholder", NINE_16,
     lambda k: plate(16, k, sunken=True, body=darker(k.sunk, 0.4), alpha=0xE6)),
    ("strength_badge", NINE_16,
     lambda k: plate(16, k, body=darker(k.warn, 0.75), alpha=0xE6,
                     lit=k.warn, low=darker(k.warn, 0.85))),
    ("paper_blank", NINE_32, lambda k: stock(32, k)),
    ("paper_emblem", NINE_32, lambda k: stock(32, k, dark=True)),

    # Lists, menus and buttons.
    ("row_odd", STRETCH, lambda k: wash(16, k.bevel, 0x18)),
    ("row_hover", STRETCH, lambda k: wash(16, k.bevel, 0x30)),
    ("menu_rule", STRETCH, lambda k: wash(16, mix(k.body, k.bevel, 0.35))),
    ("chosen_fill", NINE_16, lambda k: wash(16, k.accent, 0x60)),
    ("drag_landing", STRETCH, lambda k: wash(16, k.warn)),
    ("sent_away", STRETCH, lambda k: wash(16, k.wash, 0xB0)),
    ("filter_on", STRETCH, lambda k: wash(16, k.warn)),
    ("wanted_mark", STRETCH, lambda k: wash(16, lighter(k.warn, 0.2))),

    # Sealed product.
    ("pack_wrapper_edge", STRETCH, lambda k: wash(16, k.ink)),
    ("pack_spark", STRETCH, lambda k: wash(16, lighter(k.glow, 0.7))),
    ("rarity_ring", NINE_16, lambda k: ring(16, k.glow, thickness=2, inner=k.accent)),

    # Progress bars.
    ("bar_track", NINE_8, lambda k: bar(8, k, "track")),
    ("bar_fill", NINE_8, lambda k: bar(8, k, "fill")),
    ("bar_done", NINE_8, lambda k: bar(8, k, "done")),
]

# ---------------------------------------------------------------- writing


def mcmeta(kind, size, border, stretch_inner):
    if kind == "stretch":
        scaling = '      "type": "stretch"\n'
    else:
        scaling = (
            '      "type": "nine_slice",\n'
            f'      "width": {size},\n'
            f'      "height": {size},\n'
            f'      "border": {border},\n'
            f'      "stretch_inner": {"true" if stretch_inner else "false"}\n')
    return '{\n  "gui": {\n    "scaling": {\n' + scaling + "    }\n  }\n}\n"


#: Where the template sits in the list: last, because it is a thing to paint from rather than
#: a thing to play with.
TEMPLATE_ORDER = 9000


def theme_file(key, order):
    return ('{\n'
            f'  "name": "theme.gathering.{key}",\n'
            f'  "sprites": "gathering:{key}",\n'
            f'  "order": {order}\n'
            '}\n')


# ---------------------------------------------------------------- the contact sheet

#: How many times life size each element is drawn on the sheet. Big enough to see a one-pixel
#: border, small enough that fifty-odd of them fit on a page somebody can read.
BLOWN_UP = 4

SHEET_COLUMNS = 6
SHEET_CELL = 148
SHEET_LABEL = 50
SHEET_MARGIN = 22
SHEET_TITLE = 62

SHEET_FONTS = (
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
)

SHEET_PAPER = (0x16, 0x18, 0x1C, 0xFF)
SHEET_CELL_PAPER = (0x22, 0x25, 0x2A, 0xFF)
SHEET_INK = (0xE8, 0xE4, 0xDC, 0xFF)
SHEET_DIM = (0x9A, 0x96, 0x90, 0xFF)


def lettering(size):
    for where in SHEET_FONTS:
        if os.path.isfile(where):
            return ImageFont.truetype(where, size)
    return ImageFont.load_default()


def fitted(art, room):
    """One element, as large as it goes in a cell without smoothing anything."""
    wide, high = art.size
    scale = min(BLOWN_UP, max(1, room // max(wide, high)))
    return art.resize((wide * scale, high * scale), Image.NEAREST)


def checker(size, light=(0x33, 0x33, 0x38, 0xFF), dark=(0x2A, 0x2A, 0x2E, 0xFF), square=8):
    """What transparency is drawn over, so an alpha is something you can see."""
    board = Image.new("RGBA", size, light)
    paint = ImageDraw.Draw(board)
    for y in range(0, size[1], square):
        for x in range(0, size[0], square):
            if (x // square + y // square) % 2:
                paint.rectangle([x, y, x + square - 1, y + square - 1], fill=dark)
    return board


def contact_sheet(art):
    """Every element on one page, at four times life size, on a checkerboard."""
    rows = (len(ELEMENTS) + SHEET_COLUMNS - 1) // SHEET_COLUMNS
    wide = SHEET_MARGIN * 2 + SHEET_COLUMNS * SHEET_CELL
    high = SHEET_TITLE + SHEET_MARGIN + rows * (SHEET_CELL + SHEET_LABEL)
    sheet = Image.new("RGBA", (wide, high), SHEET_PAPER)
    paint = ImageDraw.Draw(sheet)
    title = lettering(24)
    label = lettering(15)
    small = lettering(12)
    paint.text((SHEET_MARGIN, SHEET_MARGIN), "Gathering GUI elements", font=title,
               fill=SHEET_INK)

    for index, (name, (kind, size, border, inner), _) in enumerate(ELEMENTS):
        column, row = index % SHEET_COLUMNS, index // SHEET_COLUMNS
        left = SHEET_MARGIN + column * SHEET_CELL
        top = SHEET_TITLE + SHEET_MARGIN + row * (SHEET_CELL + SHEET_LABEL)
        room = SHEET_CELL - 16
        paint.rectangle([left, top, left + room + 8, top + room + 8], fill=SHEET_CELL_PAPER)
        picture = fitted(art[name], room)
        board = checker(picture.size)
        board.alpha_composite(picture)
        sheet.paste(board, (left + 4 + (room - picture.width) // 2,
                            top + 4 + (room - picture.height) // 2))
        paint.text((left, top + room + 14), name, font=label, fill=SHEET_INK)
        note = f"{art[name].width}x{art[name].height}  " + (
            "stretch" if kind == "stretch"
            else f"nine {border} {'stretch' if inner else 'tile'}")
        paint.text((left, top + room + 32), note, font=small, fill=SHEET_DIM)
    return sheet


# ---------------------------------------------------------------- main


def main():
    for key, look in LOOKS.items():
        folder = os.path.join(SPRITES, key)
        os.makedirs(folder, exist_ok=True)
        for name, (kind, size, border, inner), paint in ELEMENTS:
            paint(look).save(os.path.join(folder, name + ".png"))
            with open(os.path.join(folder, name + ".png.mcmeta"), "w") as out:
                out.write(mcmeta(kind, size, border, inner))
        os.makedirs(THEME_FILES, exist_ok=True)
        with open(os.path.join(THEME_FILES, key + ".json"), "w") as out:
            out.write(theme_file(key, look.order))

    base = {name: paint(LOOKS[BASE]) for name, _, paint in ELEMENTS}
    folder = os.path.join(SPRITES, "template")
    os.makedirs(folder, exist_ok=True)
    for name, (kind, size, border, inner), _ in ELEMENTS:
        stencil(base[name], kind, size, border).save(os.path.join(folder, name + ".png"))
        with open(os.path.join(folder, name + ".png.mcmeta"), "w") as out:
            out.write(mcmeta(kind, size, border, inner))
    with open(os.path.join(THEME_FILES, "template.json"), "w") as out:
        out.write(theme_file("template", TEMPLATE_ORDER))

    contact_sheet(base).save(os.path.join(ROOT, "docs", "gui-elements.png"))

    looks = len(LOOKS) + 1
    print(f"painted {looks * len(ELEMENTS)} sprites: "
          f"{len(ELEMENTS)} elements x {looks} looks")
    print("and the sheet to paint from: docs/gui-elements.png")
    return 0


if __name__ == "__main__":
    sys.exit(main())
