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

import math
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

#: And how far above it a face is actually aimed, for whatever is drawn on afterwards.
HEADROOM = 0.35


def luminance(color):
    def channel(value):
        v = value / 255.0
        return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4
    return (0.2126 * channel(color[0]) + 0.7152 * channel(color[1])
            + 0.0722 * channel(color[2]))


def contrast(one, two):
    a, b = luminance(one), luminance(two)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)


def readable(color, least=LEAST_CONTRAST + HEADROOM):
    """The same color, darkened just until the text on it can be read.

    A look is free to be as pale or as saturated as it likes right up to the point where a
    name on it stops being a name. This is where that point is, applied rather than trusted -
    Bubble came out at 2.8 to one and looked lovely and said nothing.

    Aimed a little above the floor rather than exactly at it. Several of these faces are
    grained after this runs - Retro's varies by a few levels per pixel, on purpose - and a
    face that landed on 4.2 to the level came out at 3.96 in places once the grain was on it.
    Anything a construction adds afterwards can only be a few levels, so a few levels is what
    it gets.
    """
    for step in range(0, 70):
        tone = darker(color, step / 100.0)
        if contrast(tone, TEXT) >= least:
            return tone
    return tone


#: How much colour a look's surfaces carry, above what was measured for them.
#:
#: The palettes were mixed muted on purpose - card art should be the brightest thing on any
#: screen - and muted came out closer to tinted grey than to a colour. This lifts each surface
#: away from its own grey without moving how light or dark it is, which is the same trick the
#: mana badges use: more colour, same shading. It is deliberately not applied to the ink, the
#: felt, the card stock or the washes. The felt is the surface card art actually sits on, and
#: a table that got 55% more colour would be competing with every card on it.
VIVIDNESS = 1.55


def vivid(color, amount=VIVIDNESS):
    """More colour, same brightness: every channel pushed away from the colour's own grey."""
    grey = sum(color) / 3.0
    return tuple(max(0, min(255, round(grey + (value - grey) * amount))) for value in color)


class Look:
    """One look's colors. Everything drawn below comes out of these and nothing else."""

    def __init__(self, name, order, ink, bevel, shade, body, sunk, accent, glow,
                 warn, good, paper, rule, cloth, wash, style="flat", frame=None):
        self.name = name
        self.order = order
        self.style = style      # how its edges are built - see plate()
        self.frame = frame      # a ready-made ornamental panel, by name - see framed()
        self.ink = ink          # the outline, on everything
        self.bevel = vivid(bevel)     # lit edge, top and left
        self.shade = vivid(shade)     # shaded edge, bottom and right
        self.body = readable(vivid(body))   # the flat middle of a raised thing
        self.sunk = readable(vivid(sunk))   # and of a recessed one, which is darker
        self.accent = vivid(accent)   # focus, selection, the cursor
        self.glow = vivid(glow)       # the accent's light end, for a two-tone ring
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

    # Arcade: the cabinet. Black outline, a tube of light along the top of everything, and
    # colours that would look wrong anywhere but on a screen in a dark room.
    "arcade": Look(
        "Arcade", 65,
        ink=(0x08, 0x08, 0x0C), bevel=(0x4E, 0x7E, 0xE8), shade=(0x14, 0x18, 0x38),
        body=(0x22, 0x28, 0x5E), sunk=(0x12, 0x14, 0x33),
        accent=(0xFF, 0x4D, 0xD2), glow=(0xE8, 0xF4, 0xFF),
        warn=(0xFF, 0xC4, 0x3D), good=(0x3D, 0xE8, 0x9E),
        paper=(0xE8, 0xEC, 0xFA), rule=(0x8A, 0x92, 0xC0),
        cloth=(0x14, 0x16, 0x36), wash=(0x0C, 0x0E, 0x28), style="arcade"),

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

# Four more, each built around one of BDragon1727's coloured frames rather than around a
# construction of ours. The frame is the whole point of them, so the panel is the frame - cut
# from his sheet, colours and all - and the rest of the set is drawn flat in colours taken off
# it, which is what keeps a screen looking like one thing. The looks above are untouched.
LOOKS.update({
    "ember": Look(
        "Ember", 100,
        ink=(0x00, 0x00, 0x00), bevel=(0xF8, 0x9F, 0x73), shade=(0x8A, 0x33, 0x38),
        body=(0x2C, 0x28, 0x33), sunk=(0x20, 0x1E, 0x26),
        accent=(0xE2, 0x5A, 0x61), glow=(0xFC, 0xD2, 0xA8),
        warn=(0xF8, 0xC4, 0x6A), good=(0x8A, 0xB0, 0x74),
        paper=(0xF6, 0xE6, 0xDC), rule=(0xC0, 0x8E, 0x80),
        cloth=(0x2A, 0x1C, 0x22), wash=(0x1C, 0x12, 0x16), frame="ember"),
    "arcane": Look(
        "Arcane", 110,
        ink=(0x00, 0x00, 0x00), bevel=(0xFF, 0x64, 0xEF), shade=(0x60, 0x1E, 0x74),
        body=(0x2C, 0x26, 0x36), sunk=(0x20, 0x1E, 0x26),
        accent=(0xC0, 0x40, 0xE8), glow=(0xF8, 0xB8, 0xF8),
        warn=(0xF0, 0xC8, 0x6A), good=(0x6A, 0xD0, 0xC0),
        paper=(0xF2, 0xE4, 0xF8), rule=(0xB0, 0x8C, 0xC4),
        cloth=(0x24, 0x18, 0x30), wash=(0x16, 0x0E, 0x22), frame="arcane"),
    "verdant": Look(
        "Verdant", 120,
        ink=(0x00, 0x00, 0x00), bevel=(0x2C, 0x8A, 0x4D), shade=(0x14, 0x44, 0x38),
        body=(0x26, 0x2E, 0x2A), sunk=(0x1C, 0x22, 0x1F),
        accent=(0x4E, 0xC0, 0x76), glow=(0xC0, 0xF0, 0xC4),
        warn=(0xE0, 0xC0, 0x60), good=(0x6E, 0xC8, 0x8A),
        paper=(0xEE, 0xF4, 0xE4), rule=(0x9C, 0xB4, 0x94),
        cloth=(0x1C, 0x2C, 0x24), wash=(0x10, 0x1C, 0x16), frame="verdant"),
    "royal": Look(
        "Royal", 130,
        ink=(0x00, 0x00, 0x00), bevel=(0x4D, 0x9B, 0xE5), shade=(0x28, 0x36, 0x70),
        body=(0x28, 0x2C, 0x3A), sunk=(0x20, 0x22, 0x2E),
        accent=(0x49, 0x61, 0xB0), glow=(0xB8, 0xDC, 0xF8),
        warn=(0xE8, 0xC8, 0x74), good=(0x62, 0xC0, 0xA0),
        paper=(0xE8, 0xEE, 0xF8), rule=(0x94, 0xA8, 0xC8),
        cloth=(0x1E, 0x24, 0x38), wash=(0x12, 0x16, 0x26), frame="royal"),
})

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
        # One number or one per side. A sprite cut off somebody else's sheet may need a
        # different border on each edge - see mcmeta() - and the guide has to be drawn where
        # the cut really is, or the template lies about the one thing it exists to show.
        left, top, right, bottom = border if isinstance(border, tuple) \
            else (border, border, border, border)
        for at in (left, wide - right):
            paint.line([(at, 0), (at, high - 1)], fill=GUIDE)
        for at in (top, high - bottom):
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
ROUNDING = {"flat": 0, "retro": 0, "future": 8, "bubble": 8, "arcade": 3}

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

def flatPlate(size, tones, sunken, alpha, band=None):
    """Two pixels of outline, one lit step, body.

    A heavier frame than the one-pixel outline and soft four-step blend this used to draw.
    Every sprite on the pixel sheets this GUI is aimed at is a drawn object with a real edge
    round it, and the thing that reads as that at a glance is the weight of the outline
    rather than what happens inside it - so the outline is two pixels and the blend is gone.
    """
    ink, lit, low, body = tones
    top, bottom = (low, lit) if sunken else (lit, low)
    band = band or bandOf(size)
    image = Image.new("RGBA", (size, size))
    pixels = image.load()
    for y in range(size):
        for x in range(size):
            depth = min(x, y, size - 1 - x, size - 1 - y)
            outward = top if x + y < size - 1 else bottom
            if depth <= 1:
                tone = ink
            elif depth == 2:
                tone = outward
            else:
                tone = body
            pixels[x, y] = rgba(tone, alpha)
    return image


def thicken(image, ink, alpha=255):
    """A second pixel of outline, following whatever shape this already is.

    For the four looks that build their own edges - Future Sight's sweep, Retro's inset
    border, Bubble's rounding, Arcade's rail - which cannot simply be given flatPlate's ramp
    without becoming it. This finds the ring of pixels one step inside the existing outline
    and inks those too, so the frame gets its weight and the construction keeps everything it
    does further in.

    By where the shape ends rather than by distance from the edge of the canvas: two of those
    four round their corners, and a rectangle's idea of depth cuts a chord across a curve.
    """
    pixels = image.load()
    wide, tall = image.size

    def clear(x, y):
        return not (0 <= x < wide and 0 <= y < tall) or pixels[x, y][3] == 0

    near = ((1, 0), (-1, 0), (0, 1), (0, -1))
    rim = {(x, y) for y in range(tall) for x in range(wide)
           if pixels[x, y][3] and any(clear(x + dx, y + dy) for dx, dy in near)}
    inner = [(x, y) for y in range(tall) for x in range(wide)
             if pixels[x, y][3] and (x, y) not in rim
             and any((x + dx, y + dy) in rim for dx, dy in near)]
    for x, y in inner:
        pixels[x, y] = rgba(ink, alpha)
    return image


def futurePlate(size, tones, sunken, alpha, glow, band=None):
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
    band = band or bandOf(size)
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


def retroPlate(size, tones, sunken, alpha, rule, band=None):
    """The old card border.

    A raised band of mottled stone, lit across its width rather than by one bevel line: light
    at the outer edge, mid, then shadow, so the frame has thickness. A groove cut into it, a
    hairline of tarnished gold holding the field, and a dark line under the gold so the gold
    sits in something. The field inside is board with a finer grain, because the border and the
    card face are two materials and they were two materials on the card.

    Then a stud at each corner, which is the ornament that frame steps into its corners.
    """
    ink, lit, low, body = tones
    width = band or bandOf(size)
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


def bubblePlate(size, tones, sunken, alpha, glow, band=None):
    """Blown rather than cut.

    A surface, not an edge. The rim is bright all round and brightest along the top, the whole
    upper band is glossed toward the light, the bottom band carries the bounce that comes back
    off whatever it is sitting on, and a specular sits off the top-left corner rather than on
    it. The gloss and the bounce depend only on which edge a pixel is on and how deep it is, so
    they stay uniform along a nine-slice's strips and a wide panel keeps one continuous
    highlight along its top rather than a row of repeats.
    """
    ink, lit, low, body = tones
    band = band or bandOf(size)
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


def arcadePlate(size, tones, sunken, alpha, glow, band=None):
    """A cabinet button: black outline, a bright rail along the top, a lit body under it.

    Traced from BDragon1727's pixel UI bars, which is where the rail comes from - two pixels
    of near-white running the length of the top edge, and nothing like it on the other three.
    That one asymmetry is what makes the whole thing look lit from a tube above rather than
    bevelled, and it survives a nine-slice because the top strip repeats along its own length
    and the rail depends only on how deep a pixel is.
    """
    ink, lit, low, body = tones
    band = band or bandOf(size)
    radius = min(ROUNDING["arcade"], band)
    image = Image.new("RGBA", (size, size))
    pixels = image.load()
    for y in range(size):
        for x in range(size):
            depth = depthAt(x, y, size, radius)
            if depth is None:
                continue
            side = sideOf(x, y, size)
            up = side == "bottom" if sunken else side == "top"
            down = side == "top" if sunken else side == "bottom"
            if depth == 0:
                tone = ink
            elif up and depth <= 2:
                tone = glow if depth == 1 else lighter(lit, 0.15)
            elif down and depth == 1:
                tone = darker(low, 0.45)
            elif depth == 1:
                tone = low
            elif depth < band:
                # Lit from the rail down, so the face falls away from it rather than sitting
                # flat under a line.
                tone = mix(lit, body, min(1.0, (depth - 1) / max(1, band - 2)))
            else:
                tone = body
            pixels[x, y] = rgba(tone, alpha)
    return image


def plate(size, look, sunken=False, body=None, alpha=255, ink=None, lit=None, low=None,
          band=None, heavy=False):
    """A raised or recessed rectangle, built the way its look builds things.

    The band may be given rather than taken from the size, for a canvas that is bigger than
    the construction it carries: the window panel is drawn on sixty-four so it has room for an
    ornament, but its edge is still the same eight pixels of frame as everything else.
    """
    tones = (ink if ink is not None else look.ink,
             lit if lit is not None else look.bevel,
             low if low is not None else look.shade,
             body if body is not None else (look.sunk if sunken else look.body))
    if look.style == "future":
        art = futurePlate(size, tones, sunken, alpha, look.glow, band)
    elif look.style == "retro":
        art = retroPlate(size, tones, sunken, alpha, look.accent, band)
    elif look.style == "bubble":
        art = bubblePlate(size, tones, sunken, alpha, look.glow, band)
    elif look.style == "arcade":
        art = arcadePlate(size, tones, sunken, alpha, look.glow, band)
    else:
        # flatPlate draws its own two pixels of outline, so it is already heavy enough.
        return flatPlate(size, tones, sunken, alpha, band)
    return thicken(art, tones[0], alpha) if heavy else art


#: Where the ornamental frames live, one per look built around one.
FRAMES = os.path.join(ROOT, "art", "gui", "frames")

#: How far in from the edge of the window panel the ornament is drawn, and how wide the panel
#: is. Sixty-four rather than thirty-two because an ornament needs room to be one: it has to
#: sit clear of the frame's own edge and still be a shape rather than three pixels.
PANEL_SIZE = 64
PANEL_BAND = 8


def framed(look):
    """The window panel.

    For most looks that is a plate drawn the way that look draws things. For the four built
    around one of BDragon1727's frames it is the frame itself, cut off his sheet with its own
    colours - the frame is what those looks are, and repainting it would leave nothing.

    Returns its own nine-slice along with the art, because it is sixty-four across where
    everything else is thirty-two: an ornament needs room to be an ornament rather than three
    pixels, and its corners have to fit inside a nine-slice corner tile to survive tiling.
    """
    if not look.frame:
        return plate(32, look, heavy=True)
    return Image.open(os.path.join(FRAMES, look.frame + ".png")).convert("RGBA"), NINE_64_TILED


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
            elif y < band:
                # The whole top cap lit rather than one pixel of it. A bar is drawn both ways
                # round here - along, for how much of a set is owned, and upward, for a mana
                # curve - and the top is the lit edge in the first and the leading edge in the
                # second, so it is the one highlight that reads in both.
                tone = lighter(face, 0.45)
            elif y >= size - band:
                tone = darker(face, 0.35)
            elif depth == 1:
                tone = lighter(face, 0.2) if x < size / 2 else darker(face, 0.2)
            else:
                tone = face
            pixels[x, y] = rgba(tone)
    return image


def capsule(size, look, sunken=False, alpha=255, face=None):
    """A scroll bar: a pill standing on end, rather than a small rectangular panel.

    A scroll bar is six pixels wide and as tall as the list beside it, which is the one shape
    a panel construction is wrong for - drawn as a plate it came out a long thin box with four
    square corners, and every pixel-art bar anybody has ever drawn is capped and lit.

    Everything here is arranged around what a nine-slice does to it. The four corner tiles are
    drawn once, so the cap and the chamfer live there; the rows between them are stretched, so
    every one of them is identical and the light runs down the sides rather than down the
    length. A gradient written along the length would be stretched into three flat bands - the
    first attempt at this was exactly that, and it read as a bar somebody had painted in
    stripes.
    """
    ink = darker(look.ink, 0.2)
    body = face if face is not None else (look.sunk if sunken else look.body)
    lit, low = lighter(body, 0.5), darker(body, 0.42)
    if sunken:
        # A track is a hole, so its light is the other way up and never bright: an unscrolled
        # list must not look like a scrolled one.
        lit, low = darker(body, 0.55), mix(body, lighter(body, 0.3), 0.5)
    band = bandOf(size)
    image = Image.new("RGBA", (size, size))
    pixels = image.load()
    for y in range(size):
        for x in range(size):
            # One pixel off each corner, which is a rounded end at the width a scroll bar is
            # really drawn at. It has to stay inside the nine-slice's own corner tile - two
            # pixels of eight - or the curve reaches into the stretched middle and repeats all
            # the way down the bar as a row of scallops.
            if min(x, size - 1 - x) + min(y, size - 1 - y) < 1:
                continue
            top, bottom = y < band, y >= size - band
            if min(x, y, size - 1 - x, size - 1 - y) == 0:
                tone = ink
            elif top:
                tone = lit
            elif bottom:
                tone = low
            elif x == 1:
                tone = lit
            elif x == size - 2:
                tone = low
            else:
                tone = body
            pixels[x, y] = rgba(tone, alpha)
    return image


#: How big an arrow is drawn. Odd, so its point sits on a pixel rather than between two, and
#: small enough to leave a margin inside an eighteen-pixel button.
ARROW_SIZE = 9

#: Which way each arrow points, as a step in x and y.
ARROWS = {"left": (-1, 0), "right": (1, 0), "up": (0, -1), "down": (0, 1)}


def arrow(size, look, which):
    """A direction, as a shape rather than as the character for one.

    The page turns were the letters "<" and ">" set in the game's font, which is a button
    labelled with punctuation: at a glance it reads as text somebody forgot to finish rather
    than as a control. A triangle with an outline and a lit face is what one of these is in
    every pixel-art kit, and it says which way it goes without being read.

    A forty-five degree taper - one row narrower per column - because that is the only slope a
    small triangle can take without the stair-stepping showing, and it is what the reference
    sheet's own arrows are drawn on. Blitted at its own size in the middle of whatever button
    it sits on, never scaled.
    """
    # The colour of a label, not of a button. An arrow is what is written on the button, and
    # painting it in the look's body would be painting it the same colour as the face behind
    # it - which is exactly what the first attempt did, in fourteen themes at once. TEXT is
    # already guaranteed readable on every look's button, because that is the colour every
    # button's words are set in.
    ink = darker(look.ink, 0.2)
    face = TEXT
    dx, dy = ARROWS[which]
    middle = (size - 1) // 2
    image = Image.new("RGBA", (size, size))
    pixels = image.load()
    for y in range(size):
        for x in range(size):
            # How far from the base, and how far off the middle line. The base is at the tail
            # and the point is at the head, so a right arrow's base is its left-hand column.
            if dx:
                along = x - (size - 1 - middle) if dx < 0 else middle - x
                across = abs(y - middle)
            else:
                along = y - (size - 1 - middle) if dy < 0 else middle - y
                across = abs(x - middle)
            along = -along
            if along < 0 or across > middle - along:
                continue
            if across == middle - along or along == 0:
                tone = ink
            elif across == middle - along - 1 and (y if dx else x) < middle:
                # One lit pixel along the upper edge and one shaded along the lower, so the
                # triangle has a form rather than being a flat wedge - the same one-pixel
                # bevel every other raised thing here gets.
                tone = lighter(face, 0.4)
            elif across == middle - along - 1:
                tone = darker(face, 0.25)
            else:
                tone = face
            pixels[x, y] = rgba(tone)
    # Middled in its own canvas. The triangle is nine across its base and five from base to
    # point, so left to itself it sits hard against one side and the button it is blitted into
    # looks as though the arrow slipped.
    box = image.getbbox()
    middled = Image.new("RGBA", (size, size))
    middled.paste(image.crop(box),
                  ((size - (box[2] - box[0])) // 2, (size - (box[3] - box[1])) // 2))
    return middled


#: Where the sprites cut off BDragon1727's sheets live. See tools/pack_cut.py.
PARTS = os.path.join(ROOT, "art", "gui", "parts")


#: His button is forty-eight by twenty-two with an eight-pixel cap at each end. Every button
#: the mod draws is at least sixteen each way, so the caps always have somewhere to go.
BUTTON_SLICE = ("nine_slice", (48, 22), 8, True)


def pipTones(look):
    """A pip box's ramp: its rail is bright and its unlit cells sit in the middle of it."""
    return (darker(look.ink, 0.1), lighter(look.bevel, 0.3))


def arrowTones(look):
    """An arrow is a label, so it is set in the colour a label is set in."""
    return (mix(TEXT, look.ink, 0.45), TEXT)


def flipped(art):
    """Turned upside down, which is what a raised thing looks like when it is pressed."""
    return art.transpose(Image.FLIP_TOP_BOTTOM)


#: One cut sprite, read once. Fourteen looks ask for the same handful of files.
_parts = {}


def part(name):
    if name not in _parts:
        _parts[name] = Image.open(os.path.join(PARTS, name + ".png")).convert("RGBA")
    return _parts[name]


def recut(name, look, tones=None, family=None, label=False):
    """One of his sprites, in this look's colours.

    His pixels, his shapes, his outline, his dithering and his highlights - only the hue
    moves. The sheets ship each element in three colourways, which is three answers for
    fourteen looks; picking whichever is nearest would give half the themes somebody else's
    blue. So instead his tones are sorted by how light they are and laid onto a ramp built
    out of this look's own, darkest to lightest. A pixel two steps up his ramp comes out two
    steps up ours, which is what keeps a bevel a bevel.

    The outline is held out of that and taken straight from the look's ink. It is nearly
    black in every colourway he drew and it is the one tone that must not drift toward an
    accent, or a button gets a coloured halo instead of an edge.

    A family is a set of sprites whose ramp has to be worked out across all of them at once.
    The spinner is five frames of the same ring lit at different points, and read one at a
    time the darkest frame and the brightest frame each get the ramp spread over their own
    tones - which makes them the same picture. The frames only mean anything relative to one
    another, so the tones of all five decide the ramp and each frame takes its place in it.
    """
    art = part(name).copy()
    pixels = art.load()
    dark, light = tones if tones else (darker(look.ink, 0.1), lighter(look.bevel, 0.35))
    # A face has words written on it, so its light end is darkened until they can be read.
    # His buttons are drawn to carry an icon rather than a label and their faces run bright:
    # laid on our ramp unchecked they came out between 2.2 and 3.5 against the label colour
    # in every theme, which is a button you can see and a word you cannot. Anything drawn on
    # top of a face - an arrow is one - is exempt, or the fix would darken the label too.
    if not label:
        light = readable(light)
        dark = readable(dark)
    # Every tone he used, darkest first, ignoring what is transparent.
    seen = set()
    for source in (family or [name]):
        held = part(source)
        read = held.load()
        for y in range(held.height):
            for x in range(held.width):
                if read[x, y][3]:
                    seen.add(read[x, y][:3])
    order = sorted(seen, key=luminance)
    if not order:
        return art
    # His darkest tone is the outline and stays the outline. The rest spread over the ramp.
    ours = {order[0]: darker(look.ink, 0.1)}
    rest = order[1:]
    for index, tone in enumerate(rest):
        share = index / max(1, len(rest) - 1)
        ours[tone] = mix(dark, light, share)
    for y in range(art.height):
        for x in range(art.width):
            here = pixels[x, y]
            if here[3]:
                pixels[x, y] = rgba(ours[here[:3]], here[3])
    return art


#: The five spinner frames only mean anything against each other - see recut().
SPINNER_FRAMES = ["spinner_%d" % index for index in range(5)]


def wash(size, color, alpha=255):
    return Image.new("RGBA", (size, size), rgba(color, alpha))


def ring(size, color, alpha=255, thickness=1, fill=None, fill_alpha=0, inner=None, halo=False):
    """An outline round nothing, or round a wash. The optional inner tone is a second, lighter
    line just inside the first, which is what stops a bright ring reading as a sticker laid on
    top of the thing it is marking.

    A haloed ring puts a faint line of the same color outside the bright one, which is how a
    ring that is meant to catch the eye is drawn in every pixel-art kit: the light appears to
    come off the line rather than the line simply being brighter. It cannot go outside the
    sprite - the ring is drawn at the exact bounds of the thing it marks - so the bright line
    moves in by one and the faint one takes the edge.

    A hard dark edge was tried here and rejected: it is what those sheets do, and on a ring
    laid over a card it reads as a black box drawn round the card rather than as light.
    """
    image = Image.new("RGBA", (size, size),
                      rgba(fill, fill_alpha) if fill is not None else (0, 0, 0, 0))
    pixels = image.load()
    edge = 1 if halo else 0
    for y in range(size):
        for x in range(size):
            depth = min(x, y, size - 1 - x, size - 1 - y)
            if depth < edge:
                pixels[x, y] = rgba(color, round(alpha * 0.4))
            elif depth < edge + thickness:
                pixels[x, y] = rgba(color, alpha)
            elif inner is not None and depth == edge + thickness:
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
    if look.style == "arcade":
        return arcadePlate(size, tones, False, 255, lighter(face, 0.5))
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
            elif look.style == "arcade":
                # A scanline, which is the one thing a screen in a dark room always has.
                pixels[x, y] = rgba(mix(look.cloth, look.bevel, 0.09 if y % 4 == 0 else 0))
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
NINE_64_TILED = ("nine_slice", 64, 16, False)
NINE_16_TILED = ("nine_slice", 16, 4, False)
STRETCH = ("stretch", 0, 0, False)

#: Every element the mod draws, how it stretches, and how it is painted from a look.
#:
#: Keep this in step with ``GatheringSprites.Element`` - ``tools/spritecheck.py`` fails the
#: build if it drifts. The alphas are the ones the Java code used to pass to ``fill``, moved
#: here unchanged: the alpha is the design, and only the color follows the look.
ELEMENTS = [
    # Structure.
    ("panel", NINE_32_TILED, lambda k: framed(k)),
    ("panel_inset", NINE_32_TILED, lambda k: plate(32, k, sunken=True, heavy=True)),
    ("row_highlight", NINE_32_TILED,
     lambda k: plate(32, k, body=k.accent, alpha=0x38, ink=k.accent,
                     lit=lighter(k.accent, 0.4), low=k.accent)),
    ("deck_panel", STRETCH, deck_panel),
    ("scroll_track", NINE_8, lambda k: capsule(8, k, sunken=True)),
    ("scroll_thumb", NINE_8, lambda k: capsule(8, k, face=darker(k.accent, 0.45))),
    # The mod's own button, kept. His is a fine capsule and it was tried here; this is the
    # one the project prefers, and each look already builds its own edges - Future Sight's
    # sweep, Retro's inset border, Bubble's rounding - which one silhouette off a sheet would
    # have flattened into a single shape fourteen times over.
    ("button", NINE_16, lambda k: plate(16, k, body=mix(k.body, k.bevel, 0.10), heavy=True)),
    # Through the readability floor, because mixing a third of the accent into a body that
    # was already exactly readable makes it lighter again - and the hover face is the one a
    # cursor is sitting on while somebody reads the word underneath it.
    ("button_hover", NINE_16,
     lambda k: plate(16, k, body=readable(mix(k.body, k.accent, 0.34)), lit=k.glow,
                     low=darker(k.accent, 0.62), heavy=True)),
    ("button_off", NINE_16,
     lambda k: plate(16, k, body=darker(k.body, 0.45), lit=k.shade,
                     low=darker(k.shade, 0.35), heavy=True)),
    ("button_down", NINE_16,
     lambda k: plate(16, k, sunken=True, body=mix(k.body, k.shade, 0.22), heavy=True)),

    # His arrows. Up and down are one of them turned a quarter, which on pixel art is exact -
    # every pixel lands on a pixel - where any other angle would resample it.
    ("arrow_left", STRETCH, lambda k: recut("arrow_left", k, arrowTones(k), label=True)),
    ("arrow_right", STRETCH, lambda k: recut("arrow_right", k, arrowTones(k), label=True)),
    ("arrow_up", STRETCH, lambda k: recut(
        "arrow_right", k, arrowTones(k), label=True).transpose(Image.ROTATE_90)),
    ("arrow_down", STRETCH, lambda k: recut(
        "arrow_left", k, arrowTones(k), label=True).transpose(Image.ROTATE_90)),

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
    ("focus_ring", NINE_16,
     lambda k: ring(16, k.accent, inner=darker(k.accent, 0.55), halo=True)),
    ("hover_ring", NINE_16, lambda k: ring(16, k.glow)),
    ("chosen_ring", NINE_16,
     lambda k: ring(16, k.accent, thickness=2, inner=k.glow, halo=True)),
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
    ("rarity_ring", NINE_16,
     lambda k: ring(16, k.glow, thickness=2, inner=k.accent, halo=True)),

    # The one thing on any of these screens that moves: five frames of his dashed ring, with
    # the lit dash travelling round it. Forty-four across, which is the size he drew it at -
    # halving a ring of dashes turns every dash into a smudge, so it is drawn whole and left
    # off a card too small to hold it.
    ("spinner_0", STRETCH,
     lambda k, f=0: recut("spinner_%d" % f, k, (darker(k.accent, 0.6), k.glow),
                          SPINNER_FRAMES, label=True)),
    ("spinner_1", STRETCH,
     lambda k, f=1: recut("spinner_%d" % f, k, (darker(k.accent, 0.6), k.glow),
                          SPINNER_FRAMES, label=True)),
    ("spinner_2", STRETCH,
     lambda k, f=2: recut("spinner_%d" % f, k, (darker(k.accent, 0.6), k.glow),
                          SPINNER_FRAMES, label=True)),
    ("spinner_3", STRETCH,
     lambda k, f=3: recut("spinner_%d" % f, k, (darker(k.accent, 0.6), k.glow),
                          SPINNER_FRAMES, label=True)),
    ("spinner_4", STRETCH,
     lambda k, f=4: recut("spinner_%d" % f, k, (darker(k.accent, 0.6), k.glow),
                          SPINNER_FRAMES, label=True)),

    # Progress bars: his. A hollow box with its ends cut on the diagonal, and a solid bar
    # that runs inside it - which is why the two are drawn at different rectangles rather
    # than one over the other. The shear is most of what makes them read as pixel art rather
    # than as two rectangles, and it survives the nine-slice because each end's diagonal sits
    # inside its own corner tile.
    ("bar_track", ("nine_slice", (48, 11), (6, 4, 6, 4), True),
     lambda k: recut("bar_track", k, (darker(k.sunk, 0.3), lighter(k.bevel, 0.1)), label=True)),
    ("bar_fill", ("nine_slice", (42, 5), (4, 1, 4, 1), True),
     lambda k: recut("bar_fill", k, (darker(k.good, 0.45), lighter(k.good, 0.35)), label=True)),
    ("bar_done", ("nine_slice", (42, 5), (4, 1, 4, 1), True),
     lambda k: recut("bar_fill", k, (darker(k.warn, 0.45), lighter(k.warn, 0.35)), label=True)),

    # A box of pips, in pieces: a cap, a lit cell, a dim cell, a cap. Built rather than
    # nine-sliced, because a nine-slice middle stretches in 1.21.1 and there is no tiling
    # option for one - five pips stretched would be one long smudge. All four take the same
    # ramp so the rail that runs through them is the same rail; the only thing that differs
    # between a lit pip and a dim one is what he drew inside the cell.
    ("pip_left", STRETCH, lambda k: recut("pip_left", k, pipTones(k), label=True)),
    ("pip_full", STRETCH, lambda k: recut("pip_full", k, pipTones(k), label=True)),
    ("pip_empty", STRETCH, lambda k: recut("pip_empty", k, pipTones(k), label=True)),
    ("pip_right", STRETCH, lambda k: recut("pip_right", k, pipTones(k), label=True)),

    # The mana curve is the same idea standing up, and his bar only reads one way round - the
    # shear leans, the light is along the top. So the columns keep the mod's own, which was
    # built to be drawn either way.
    ("curve_track", NINE_8, lambda k: bar(8, k, "track")),
    ("curve_fill", NINE_8, lambda k: bar(8, k, "fill")),
]

# ---------------------------------------------------------------- writing


def mcmeta(kind, size, border, stretch_inner):
    """The sprite's own scaling rule.

    The size may be a pair rather than a number. Everything the mod paints itself is square,
    but a sprite cut off somebody's sheet is whatever shape he drew it - a button forty-eight
    across and twenty-two tall - and squaring it would either stretch his art or pad it with
    nothing, which is a nine-slice border made of empty pixels.
    """
    if kind == "stretch":
        scaling = '      "type": "stretch"\n'
    else:
        wide, tall = size if isinstance(size, tuple) else (size, size)
        # The border may be one number or one per side. Per side is what lets a sprite whose
        # detail is banded along one axis survive: the scroll thumb's grip is seven rows down
        # an eighteen-row sprite eight pixels wide, so it can only stay out of the stretched
        # middle if the top and bottom borders are wider than the left and right ones. The
        # game reads either form - GuiSpriteScaling.NineSlice.Border takes an int or a record.
        if isinstance(border, tuple):
            left, top, right, bottom = border
            edge = ('{\n'
                    f'        "left": {left},\n'
                    f'        "top": {top},\n'
                    f'        "right": {right},\n'
                    f'        "bottom": {bottom}\n'
                    '      }')
        else:
            edge = str(border)
        scaling = (
            '      "type": "nine_slice",\n'
            f'      "width": {wide},\n'
            f'      "height": {tall},\n'
            f'      "border": {edge},\n'
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
        for name, spec, paint in ELEMENTS:
            art = paint(look)
            if isinstance(art, tuple):
                art, spec = art
            art.save(os.path.join(folder, name + ".png"))
            kind, size, border, inner = spec
            with open(os.path.join(folder, name + ".png.mcmeta"), "w") as out:
                out.write(mcmeta(kind, size, border, inner))
        os.makedirs(THEME_FILES, exist_ok=True)
        with open(os.path.join(THEME_FILES, key + ".json"), "w") as out:
            out.write(theme_file(key, look.order))

    base = {}
    for name, _, paint in ELEMENTS:
        art = paint(LOOKS[BASE])
        base[name] = art[0] if isinstance(art, tuple) else art
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
