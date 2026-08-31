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
        self.body = body        # the flat middle of a raised thing
        self.sunk = sunk        # and of a recessed one, which is darker
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


def rgba(color, alpha=255):
    return (color[0], color[1], color[2], alpha)


def mix(one, two, amount):
    return tuple(round(a + (b - a) * amount) for a, b in zip(one[:3], two[:3]))


def darker(color, amount):
    return mix(color, (0, 0, 0), amount)


def lighter(color, amount):
    return mix(color, (255, 255, 255), amount)


# How round each style's corners are, in pixels of a 32-wide source, and how much grain its
# body carries. A nine-slice's corners are its border square, so a radius up to the border is
# rounded entirely inside the corner tile and the stretched edges stay straight.
ROUNDING = {"flat": 0, "retro": 0, "future": 7, "bubble": 8}
GRAIN = {"flat": 0, "retro": 6, "future": 0, "bubble": 0}


def grain(x, y, amount, seed=0):
    """A fixed speckle. The same pixel is always the same amount off, so the art does not
    change between runs, and the inner tile repeats seamlessly with itself."""
    if not amount:
        return 0
    scatter = (x * 73856093) ^ (y * 19349663) ^ (seed * 83492791)
    scatter = (scatter * 2654435761) & 0xFFFFFFFF
    return ((scatter >> 13) % (2 * amount + 1)) - amount


def speckled(color, x, y, amount, seed=0):
    step = grain(x, y, amount, seed)
    return tuple(max(0, min(255, channel + step)) for channel in color[:3])


def depthAt(x, y, size, radius):
    """How many pixels inside the shape a pixel is, or None if the rounding cut it away."""
    depth = min(x, y, size - 1 - x, size - 1 - y)
    if radius <= 0:
        return depth
    at = radius - 0.5
    cx = at if x < radius else (size - 1 - at if x >= size - radius else None)
    cy = at if y < radius else (size - 1 - at if y >= size - radius else None)
    if cx is None or cy is None:
        return depth
    away = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
    if away > radius - 0.15:
        return None
    return min(depth, int(radius - away))


def plate(size, look, sunken=False, body=None, alpha=255, ink=None, lit=None, low=None):
    """A raised or recessed rectangle, built the way its look builds things.

    The bevel is split corner to corner rather than side by side, which is the only way a
    one-pixel bevel meets itself cleanly at a corner - side by side leaves the two corner
    pixels arguing about which edge they belong to.

    Four constructions, because three of the looks are a card frame rather than a color:

    flat
        outline, one lit pixel, one quiet step, body. The Minecraft button.
    future
        the Future Sight frame: a wide-radius rounded corner and a thin line held a pixel off
        the edge, so the panel reads as a sheet of something with a curve cut out of it rather
        than as a box.
    retro
        the old card border: an outer bevel, a groove cut into it, an inner bevel the other
        way up, and a grainy stock inside. Four steps is what makes a border look pressed
        rather than drawn.
    bubble
        rounder still, with a two-pixel lit edge and a highlight that carries further down the
        top-left than a bevel would, which is what reads as blown rather than cut.
    """
    style = look.style
    body = body if body is not None else (look.sunk if sunken else look.body)
    ink = ink if ink is not None else look.ink
    lit = lit if lit is not None else look.bevel
    low = low if low is not None else look.shade
    top, bottom = (low, lit) if sunken else (lit, low)
    radius = min(ROUNDING[style], size // 3)
    speck = GRAIN[style] if size >= 16 else 0

    image = Image.new("RGBA", (size, size))
    pixels = image.load()
    for y in range(size):
        for x in range(size):
            depth = depthAt(x, y, size, radius)
            if depth is None:
                continue
            outward = top if x + y < size - 1 else bottom
            if depth == 0:
                tone = ink if style != "future" else mix(ink, body, 0.25)
            elif style == "future":
                # One hairline, held a pixel in, and nothing else. The frame is the curve.
                tone = body if depth == 1 else (outward if depth == 2 else body)
            elif style == "retro":
                tone = (outward if depth == 1
                        else ink if depth == 2
                        else (bottom if x + y < size - 1 else top) if depth == 3
                        else body)
            elif style == "bubble":
                tone = (outward if depth <= 1
                        else mix(body, outward, 0.55) if depth == 2
                        else mix(body, outward, 0.22) if depth == 3
                        else body)
            elif depth == 1:
                tone = outward
            elif depth == 2 and size >= 16:
                # A second, much quieter step. One bevel line on a flat body reads as a
                # border somebody drew; two read as a face somebody lit.
                tone = mix(body, outward, 0.26 if x + y < size - 1 else 0.4)
            else:
                tone = body
            pixels[x, y] = rgba(speckled(tone, x, y, speck), alpha)
    return image


def wash(size, color, alpha=255):
    return Image.new("RGBA", (size, size), rgba(color, alpha))


def ring(size, color, alpha=255, thickness=1, fill=None, fill_alpha=0, inner=None):
    """An outline round nothing, or round a wash. The optional inner tone is a second,
    lighter line just inside the first, which is what stops a bright ring reading as a
    sticker laid on top of the thing it is marking."""
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
    """Blank paper: the stock, its edge, and the inner rule that reads as a card.

    Built the way the look builds everything else, so a Retro card is grainy board with a
    pressed border and a Future Sight one is a rounded sheet with a hairline.
    """
    face = darker(look.paper, 0.90) if dark else look.paper
    rule = look.accent if dark else look.rule
    radius = min(ROUNDING[look.style], size // 3)
    speck = GRAIN[look.style]
    image = Image.new("RGBA", (size, size))
    pixels = image.load()
    for y in range(size):
        for x in range(size):
            depth = depthAt(x, y, size, radius)
            if depth is None:
                continue
            if depth == 0:
                tone = look.ink
            elif look.style == "retro" and depth == 1:
                tone = lighter(rule, 0.35)
            elif depth == 2:
                tone = rule
            else:
                tone = face
            pixels[x, y] = rgba(speckled(tone, x, y, speck if depth > 2 else 0))
    return image


def cloth(size, look):
    """The table. Never a slab of one color: a weave for the flat looks, a grain for Retro,
    and for Future Sight nothing at all, because that frame's ground is smooth."""
    image = Image.new("RGBA", (size, size), rgba(look.cloth))
    pixels = image.load()
    for y in range(size):
        for x in range(size):
            if look.style == "retro":
                pixels[x, y] = rgba(speckled(look.cloth, x, y, 5))
            elif look.style == "future":
                pixels[x, y] = rgba(mix(look.cloth, look.bevel, 0.03 if (x + y) % 8 == 0 else 0))
            elif (x // 2 + y // 2) % 2 == 0:
                pixels[x, y] = rgba(lighter(look.cloth, 0.05))
    return image


#: Where the deck list panel's right edge sits, top and bottom, as a fraction of its width.
TAPER_TOP, TAPER_BOTTOM = 0.90, 0.74


def deck_panel(look, width=256, height=512):
    """The deck list: flush left, tapering right, with the outline following the taper.

    Stretched rather than nine-sliced, because the taper is the point - so it is drawn at the
    size it is usually seen and the stretch is small.
    """
    image = Image.new("RGBA", (width, height))
    pixels = image.load()
    for y in range(height):
        edge = width * (TAPER_TOP + (TAPER_BOTTOM - TAPER_TOP) * (y / height))
        for x in range(width):
            if x > edge:
                continue
            if y < 2 or y > height - 3 or x > edge - 2:
                pixels[x, y] = rgba(look.ink)
            elif y < 4 or x < 2:
                pixels[x, y] = rgba(look.bevel)
            elif y > height - 6 or x > edge - 5:
                pixels[x, y] = rgba(look.shade)
            else:
                pixels[x, y] = rgba(look.body)
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
    ("bar_track", NINE_8, lambda k: plate(8, k, sunken=True, alpha=0xCC)),
    ("bar_fill", NINE_8,
     lambda k: plate(8, k, body=k.good, lit=lighter(k.good, 0.4), low=darker(k.good, 0.4))),
    ("bar_done", NINE_8,
     lambda k: plate(8, k, body=k.warn, lit=lighter(k.warn, 0.4), low=darker(k.warn, 0.4))),
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
