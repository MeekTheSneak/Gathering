#!/usr/bin/env python3
"""Paints the mod's GUI art: every element, once per theme.

Every rectangle the mod draws is a PNG under
``assets/gathering/textures/gui/sprites/<theme>/<element>.png``. This writes the
placeholder set for all of them, so that the change from drawing colored boxes in
code to drawing textures is invisible until somebody repaints one.

The felt theme is the reference and is exactly what the code used to draw - the
same ARGB values, so a screenshot before and after the change is the same
screenshot. The other themes are that art put through one duotone, which keeps
every element's relative weight and alpha and only moves its color. That is
deliberate: these are placeholders for a person to paint over, and a coherent
starting point is worth more than fifty hand-picked colors nobody chose.

Run it from the repo root:  python3 tools/gui_art.py
"""

import os
import shutil
import struct
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPRITES = os.path.join(
    ROOT, "common", "src", "main", "resources", "assets", "gathering",
    "textures", "gui", "sprites")

# ---------------------------------------------------------------- themes

#: The accent every theme's cursor, selection and rings are drawn in. Matched
#: exactly rather than duotoned, because an accent that drifted with luminance
#: would stop being one color.
FELT_ACCENT = (0x6F, 0xD3, 0xE8)

# The dark end carries the color, not the light end. Most of this art is dark - a scrim, a
# panel, a band under a number - so a theme whose blacks are all the same black is three
# themes nobody can tell apart, which the first attempt at this was.
THEMES = {
    # name: (darkest tone, lightest tone, accent)
    "felt": None,                                          # the reference art
    "slate": ((0x16, 0x1D, 0x28), (0xE8, 0xEE, 0xF6), (0x8F, 0xB8, 0xE8)),
    "walnut": ((0x2B, 0x1C, 0x11), (0xF8, 0xEC, 0xD2), (0xE0, 0xA9, 0x4F)),
}

def duotone(rgba, theme):
    """One pixel of the felt art, in another theme's colors.

    Luminance is kept and hue is replaced, so a border stays a border and a
    wash stays a wash. Alpha is never touched: the alpha is the design.
    """
    tone = THEMES[theme]
    if tone is None:
        return rgba
    dark, light, accent = tone
    r, g, b, a = rgba
    if a == 0:
        return rgba
    if (r, g, b) == FELT_ACCENT:
        return (accent[0], accent[1], accent[2], a)
    lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
    return (
        round(dark[0] + (light[0] - dark[0]) * lum),
        round(dark[1] + (light[1] - dark[1]) * lum),
        round(dark[2] + (light[2] - dark[2]) * lum),
        a,
    )

# ---------------------------------------------------------------- painting

def argb(value):
    """0xAARRGGBB, the form the Java constants were written in, as RGBA."""
    return ((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF, (value >> 24) & 0xFF)


def flat(size, color):
    return Image.new("RGBA", (size, size), argb(color))


def ring(size, color, thickness=1, fill=None):
    image = Image.new("RGBA", (size, size), argb(fill) if fill is not None else (0, 0, 0, 0))
    paint = ImageDraw.Draw(image)
    for step in range(thickness):
        paint.rectangle(
            [step, step, size - 1 - step, size - 1 - step], outline=argb(color))
    return image


def card_stock(size, stock, edge, rule, rule_inset):
    """Blank paper: the stock, its edge, and the inner rule that reads as a card."""
    image = Image.new("RGBA", (size, size), argb(stock))
    paint = ImageDraw.Draw(image)
    paint.rectangle([0, 0, size - 1, size - 1], outline=argb(edge))
    paint.rectangle(
        [rule_inset, rule_inset, size - 1 - rule_inset, size - 1 - rule_inset],
        outline=argb(rule))
    return image

# ---------------------------------------------------------------- the elements

# A nine-slice's inner region is either stretched or tiled. Tiling is one draw call per
# tile, so an element that covers a mat or a whole screen has to stretch: a tiled 16-pixel
# ring round a 200-pixel mat is fifty draw calls a frame for a line. The six elements that
# already existed keep the tiled inner they were drawn for.
NINE_32 = ("nine_slice", 32, 8, True)
NINE_16 = ("nine_slice", 16, 4, True)
NINE_8 = ("nine_slice", 8, 2, True)
NINE_32_TILED = ("nine_slice", 32, 8, False)
NINE_16_TILED = ("nine_slice", 16, 4, False)
STRETCH = ("stretch", 0, 0, False)

#: Every element the mod draws, the art it starts as, and how it stretches.
#:
#: The colors are the ones the Java code used to pass to ``fill`` and
#: ``renderOutline``, moved here unchanged. Keep this in step with
#: ``GatheringSprites.Element`` - ``tools/spritecheck.py`` fails the build if it
#: drifts.
ELEMENTS = [
    # Structure. These six already existed as art and are copied, not painted.
    ("panel", NINE_32_TILED, None),
    ("panel_inset", NINE_32_TILED, None),
    ("row_highlight", NINE_32_TILED, None),
    ("deck_panel", STRETCH, None),
    ("scroll_track", NINE_16_TILED, None),
    ("scroll_thumb", NINE_16_TILED, None),

    # Whole-screen washes.
    ("screen_scrim", STRETCH, lambda: flat(16, 0x80101418)),
    ("screen_backdrop", STRETCH, lambda: flat(16, 0xE8080B10)),
    ("pack_backdrop", STRETCH, lambda: flat(16, 0xC8060A0E)),
    ("sets_backdrop", STRETCH, lambda: flat(16, 0xC0000000)),
    ("inspect_backdrop", STRETCH, lambda: flat(16, 0xE6000000)),

    # The table.
    ("table_felt", STRETCH, lambda: flat(32, 0xFF1E3A2E)),
    ("seat_mat", NINE_32, lambda: flat(32, 0x30FFFFFF)),
    ("seat_mat_mine", NINE_32, lambda: flat(32, 0x406FD3E8)),
    ("seat_divider", STRETCH, lambda: flat(16, 0x66FFFFFF)),
    ("zone_border", NINE_16, lambda: ring(16, 0x66FFFFFF)),
    ("seat_ring", NINE_16, lambda: ring(16, 0xFFFFFFFF)),
    ("focus_ring", NINE_16, lambda: ring(16, 0xFF6FD3E8)),
    ("hover_ring", NINE_16, lambda: ring(16, 0xFFE8E4DC)),
    ("chosen_ring", NINE_16, lambda: ring(16, 0xFF6FD3E8, thickness=2)),
    ("select_box", NINE_16, lambda: ring(16, 0xFF6FD3E8, fill=0x206FD3E8)),
    ("aimed_pile", NINE_16, lambda: ring(16, 0xFF6FD3E8, thickness=2, fill=0x996FD3E8)),
    ("life_backing", NINE_16, lambda: flat(16, 0xA0101418)),
    ("talk_backdrop", NINE_16, lambda: flat(16, 0x99000000)),
    ("talk_typing", NINE_16, lambda: flat(16, 0xCC101C1A)),
    ("pile_badge", NINE_16, lambda: flat(16, 0xE0141210)),
    ("exposed_band", NINE_16, lambda: flat(16, 0xC02A1B12)),
    ("counter_band", STRETCH, lambda: flat(16, 0xC0000000)),
    ("tax_backing", STRETCH, lambda: flat(16, 0xB0000000)),
    ("tax_lit", STRETCH, lambda: flat(16, 0xD0000000)),
    ("ghost_tint", STRETCH, lambda: flat(16, 0x50000000)),

    # Cards on the felt.
    ("card_shadow", STRETCH, lambda: flat(16, 0x99000000)),
    ("card_cast", STRETCH, lambda: flat(16, 0x59000000)),
    ("card_footprint", NINE_16, lambda: ring(16, 0xBFFFD479)),
    ("tapped_tint", STRETCH, lambda: flat(16, 0x60000000)),
    ("frozen_tint", STRETCH, lambda: flat(16, 0x3899D9F2)),
    ("frozen_edge", STRETCH, lambda: flat(16, 0xFFE8F7FF)),
    ("name_backdrop", STRETCH, lambda: flat(16, 0xC0000000)),
    ("card_placeholder", NINE_16, lambda: ring(16, 0xFF3A3A44, fill=0xE0101014)),
    ("strength_badge", NINE_16, lambda: ring(16, 0xFFD9A441, fill=0xE02A1B12)),
    ("paper_blank", NINE_32, lambda: card_stock(32, 0xFFF1E8D2, 0xFF6E6047, 0xFFBFAE8C, 2)),
    ("paper_emblem", NINE_32, lambda: card_stock(32, 0xFF13100C, 0xFF000000, 0xFFC9A227, 2)),

    # Lists, menus and buttons.
    ("row_odd", STRETCH, lambda: flat(16, 0x18FFFFFF)),
    ("row_hover", STRETCH, lambda: flat(16, 0x30FFFFFF)),
    ("menu_rule", STRETCH, lambda: flat(16, 0xFF4A4642)),
    ("chosen_fill", NINE_16, lambda: flat(16, 0x606FD3E8)),
    ("drag_landing", STRETCH, lambda: flat(16, 0xFFFFD479)),
    ("sent_away", STRETCH, lambda: flat(16, 0xB0101418)),
    ("filter_on", STRETCH, lambda: flat(16, 0xFFE8C86A)),

    # Sealed product.
    ("pack_wrapper_edge", STRETCH, lambda: flat(16, 0xFF161A20)),
    ("pack_spark", STRETCH, lambda: flat(16, 0xFFFFFFFF)),
    ("rarity_ring", NINE_16, lambda: ring(16, 0xFFFFFFFF, thickness=2)),

    # Progress bars.
    ("bar_track", NINE_8, lambda: flat(8, 0x66000000)),
    ("bar_fill", NINE_8, lambda: flat(8, 0xFF4E9A6A)),
    ("bar_done", NINE_8, lambda: flat(8, 0xFFD9A441)),
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


def recolor(image, theme):
    if THEMES[theme] is None:
        return image.copy()
    out = image.convert("RGBA").copy()
    pixels = out.load()
    width, height = out.size
    for y in range(height):
        for x in range(width):
            pixels[x, y] = duotone(pixels[x, y], theme)
    return out


def main():
    reference = {}
    for name, (kind, size, border, inner), painter in ELEMENTS:
        if painter is None:
            # Art that already existed: the felt theme keeps it byte for byte.
            source = os.path.join(SPRITES, "felt", name + ".png")
            if not os.path.isfile(source):
                source = os.path.join(SPRITES, name + ".png")
            if not os.path.isfile(source):
                print(f"missing the existing art for {name}", file=sys.stderr)
                return 1
            reference[name] = Image.open(source).convert("RGBA")
        else:
            reference[name] = painter()

    for theme in THEMES:
        folder = os.path.join(SPRITES, theme)
        os.makedirs(folder, exist_ok=True)
        for name, (kind, size, border, inner), _ in ELEMENTS:
            recolor(reference[name], theme).save(os.path.join(folder, name + ".png"))
            with open(os.path.join(folder, name + ".png.mcmeta"), "w") as out:
                out.write(mcmeta(kind, size, border, inner))

    written = len(THEMES) * len(ELEMENTS)
    print(f"painted {written} sprites: {len(ELEMENTS)} elements x {len(THEMES)} themes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
