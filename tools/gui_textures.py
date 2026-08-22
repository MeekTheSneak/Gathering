#!/usr/bin/env python3
"""Generates the mod's GUI sprites from a palette.

The PNGs under assets/gathering/textures/gui/sprites are the source of truth for how the
mod looks - repaint one and the screens change, and a resource pack can replace any of
them. This script exists so a whole coherent set can be regenerated from one palette
instead of being edited pixel by pixel, which is what a second theme will want.

Run from the repository root:

    python3 tools/gui_textures.py

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

    "frame":        (0x08, 0x08, 0x0B),
    "frame_lip":    (0x3A, 0x3A, 0x46),
    "frame_fill":   (0x11, 0x11, 0x17),

    "track":        (0x12, 0x18, 0x2A),
    "thumb":        (0x4F, 0xC3, 0xD9),
}

# The deck panel's right edge runs from this fraction of the width at the top to this
# fraction at the bottom. DeckScreenLayout.TAPER_BOTTOM must match, or the scrollbar drawn
# along that edge will not sit on it. A theme replacing deck_panel.png keeps these.
TAPER_TOP = 1.0
TAPER_BOTTOM = 0.80

# Width of the line down the tapered edge, as a fraction of panel width. Deliberately thin
# and muted: the bright thing on that edge is the scroll thumb, not the panel border.
EDGE_FRACTION = 0.010

SUPERSAMPLE = 4


def rgba(colour, alpha=255):
    if len(colour) == 4:
        return colour
    return colour + (alpha,)


def write_png(path, width, height, pixels):
    """pixels: list of rows, each a list of (r, g, b, a)."""
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
                colour = edge
            elif depth == 1 and lip is not None:
                colour = lip
            else:
                colour = fill
            px[y][x] = rgba(colour)
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


def main():
    nine_slice("panel", PALETTE["panel"], PALETTE["panel_edge"])
    nine_slice("panel_inset", PALETTE["inset"], PALETTE["inset_edge"])
    nine_slice("row_highlight", PALETTE["accent_dim"], PALETTE["accent"])
    # A heavy border with a dark interior: what a card and its notes sit inside.
    nine_slice("frame", PALETTE["frame_fill"], PALETTE["frame"], lip=PALETTE["frame_lip"])
    deck_panel()
    scrollbar()


if __name__ == "__main__":
    main()
