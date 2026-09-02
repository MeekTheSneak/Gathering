#!/usr/bin/env python3
"""Paints the sleeve every card in the mod shows on its reverse.

Every card in Gathering is sleeved: the back of a card is this and never the printed face
behind it, which is a security property as much as a look - a face-down card has to be
unreadable from every angle. So there is exactly one back, and it is ours.

Drawn here rather than saved out of an image editor for the reason every generator here exists:
a palette at the top of a file can be re-tinted for a theme, and a sleeve nobody can regenerate
is a sleeve that can never match one.

Deliberately its own artwork. The card back it is in the spirit of is Wizards' trade dress, and
the brief's legal posture (section 15) is that none of their imagery ships in the jar - so this
is a brown oval, a blue rim and five colored beads, painted from arithmetic, carrying no text,
no logo and no traced line.

Run from the repository root:

    python3 tools/card_back.py
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pngwrite import write_png  # noqa: E402  - the PNG writer, kept in one place

OUT = "common/src/main/resources/assets/gathering/textures/card/back.png"
PLAIN_OUT = "common/src/main/resources/assets/gathering/textures/card/sleeve.png"

# The size of a real card in millimetres, which lands where the rest of the mod lives: the
# card fronts come off Scryfall at 488 across and there is nothing to be done about that, but
# everything the mod draws itself is pixel art, and a smooth photographic back beside a
# blocky world was the one thing on the table that looked imported.
WIDTH, HEIGHT = 64, 88

# Painted at this multiple and averaged down. Two rather than three or more: enough that the
# oval does not stair-step down its flanks, little enough that every edge still lands on a
# pixel you can count. Anti-aliasing an ellipse smooth is what made the old one look imported.
OVERSAMPLE = 2

# ---------------------------------------------------------------------------
# Palette. A theme is this block and nothing else.
# ---------------------------------------------------------------------------
EDGE = (0x08, 0x07, 0x06)          # the card's own black rim
BORDER_TOP = (0x5E, 0x50, 0x26)    # weathered gold, lit from above
BORDER_BOTTOM = (0x4E, 0x2A, 0x19) # rust, where the gold has gone
BORDER_RULE = (0x1E, 0x16, 0x0B)   # the fine inset lines
GEM = (0x8E, 0x25, 0x1B)           # the four corner studs
GEM_LIGHT = (0xC8, 0x5B, 0x46)
RING_DARK = (0x0B, 0x09, 0x08)     # the oval's outline
RIM = (0x25, 0x33, 0x60)           # the blue band inside it
RIM_LIGHT = (0x52, 0x68, 0x9E)
FIELD = (0x8E, 0x5E, 0x46)         # the leather the beads sit on
FIELD_DEEP = (0x6A, 0x40, 0x2E)

# White, green, blue, red, black - the five, in the order they are laid out.
BEADS = [
    ((0xCF, 0xC9, 0xB6), (0xF4, 0xF2, 0xE8)),
    ((0x3E, 0x7A, 0x36), (0x6E, 0xA8, 0x5C)),
    ((0x38, 0x8C, 0xAA), (0x74, 0xBA, 0xD2)),
    ((0x96, 0x28, 0x20), (0xC4, 0x58, 0x46)),
    ((0x18, 0x14, 0x1A), (0x4C, 0x44, 0x50)),
]

# ---------------------------------------------------------------------------
# Shape, as fractions of the card so the numbers survive a change of size.
# ---------------------------------------------------------------------------
RIM_WIDTH = 0.020        # the black edge of the card
RULE_INSET = 0.055       # where the fine double line runs
RULE_GAP = 0.031         # and how far apart its two strokes are
GEM_INSET = 0.090        # the corner studs, on the diagonal
GEM_RADIUS = 0.042
OVAL_X, OVAL_Y = 0.416, 0.450
RING_WIDTH = 0.011       # the black outline around the oval
BAND_WIDTH = 0.023       # and the blue band inside it
BEAD_RING = 0.150        # how far the five sit from the middle
BEAD_RADIUS = 0.047


def mix(one, two, amount):
    return tuple(round(a + (b - a) * amount) for a, b in zip(one, two))


def shade(color, amount):
    """Lighter for a positive amount, darker for a negative one."""
    if amount >= 0:
        return mix(color, (255, 255, 255), amount)
    return mix(color, (0, 0, 0), -amount)


def hashed(x, y, salt):
    """A repeatable number in 0..1 for a grid point. The same file every run."""
    n = (x * 374761393 + y * 668265263 + salt * 2654435761) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def blotches(x, y, scale, salt):
    """Smooth value noise: the mottling that stops a flat fill looking printed."""
    gx, gy = x / scale, y / scale
    x0, y0 = math.floor(gx), math.floor(gy)
    fx, fy = gx - x0, gy - y0
    # Smoothstep, so the grid the noise is built on does not show as squares.
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    top = hashed(x0, y0, salt) * (1 - fx) + hashed(x0 + 1, y0, salt) * fx
    bottom = hashed(x0, y0 + 1, salt) * (1 - fx) + hashed(x0 + 1, y0 + 1, salt) * fx
    return top * (1 - fy) + bottom * fy


def weathered(x, y, width, height):
    """The border: gold at the top going to rust at the bottom, worn unevenly."""
    base = mix(BORDER_TOP, BORDER_BOTTOM, y / height)
    # Two sizes of blotch. One large enough to read as wear across a whole corner, one small
    # enough to read as the grain of whatever it was printed on.
    wear = (blotches(x, y, width * 0.30, 1) * 0.55
            + blotches(x, y, width * 0.09, 2) * 0.30
            + blotches(x, y, width * 0.02, 5) * 0.15)
    return shade(base, (wear - 0.5) * 0.80)


def leather(x, y, width, height, distance):
    """The oval's field: warm, uneven, and darker towards its edge."""
    base = mix(FIELD, FIELD_DEEP, min(1.0, max(0.0, (distance - 0.55) / 0.45)) * 0.8)
    grain = (blotches(x, y, width * 0.14, 3) * 0.40
             + blotches(x, y, width * 0.045, 4) * 0.35
             + blotches(x, y, width * 0.012, 6) * 0.25)
    return shade(base, (grain - 0.5) * 0.22)


def sphere(color, light, offset, spot=0.0):
    """A bead lit from the upper left, darkened where it turns away, with a highlight.

    @param offset where on the bead this is, -1 at the lit edge and 1 at the shaded one
    @param spot   how much of the small bright catchlight falls here
    """
    lit = mix(color, light, max(0.0, -offset) ** 1.5)
    lit = shade(lit, offset * 0.40)
    return mix(lit, (255, 255, 255), spot * 0.85)


def catchlight(x, y, centerX, centerY, radius):
    """How much of the small bright point sitting up and left of a bead's middle falls here."""
    spot = max(OVERSAMPLE * 1.0, radius * 0.34)
    gap = math.hypot(x - (centerX - radius * 0.30), y - (centerY - radius * 0.30))
    return max(0.0, 1.0 - gap / spot) ** 2


def paint():
    big_width, big_height = WIDTH * OVERSAMPLE, HEIGHT * OVERSAMPLE
    width, height = float(big_width), float(big_height)

    rim = RIM_WIDTH * width
    rule_at = RULE_INSET * width
    rule_gap = RULE_GAP * width
    gem_at = GEM_INSET * width
    gem_radius = GEM_RADIUS * width
    cx, cy = width / 2, height / 2
    rx, ry = OVAL_X * width, OVAL_Y * height
    ring = RING_WIDTH * width
    band = BAND_WIDTH * width
    bead_ring = BEAD_RING * width
    bead_radius = BEAD_RADIUS * width

    # The five, laid out as a regular pentagon with one at the top.
    beads = []
    for index, (color, light) in enumerate(BEADS):
        # White at the top, then the other four outward from it - the order the palette is
        # written in is the order they are laid out, going left before right.
        angle = math.radians([0, -72, 72, -144, 144][index] - 90)
        beads.append((cx + math.cos(angle) * bead_ring,
                      cy + math.sin(angle) * bead_ring, color, light))

    rows = []
    for by in range(big_height):
        row = []
        for bx in range(big_width):
            row.append(pixel(bx + 0.5, by + 0.5, width, height, rim, rule_at, rule_gap,
                             gem_at, gem_radius, cx, cy, rx, ry, ring, band,
                             beads, bead_radius))
        rows.append(row)

    return downsample(rows, big_width, big_height)


def pixel(x, y, width, height, rim, rule_at, rule_gap, gem_at, gem_radius,
          cx, cy, rx, ry, ring, band, beads, bead_radius):
    # The card's own black edge, outermost and over everything.
    if x < rim or y < rim or x > width - rim or y > height - rim:
        return EDGE + (255,)

    # Inside the oval, or on one of the two rings around it. Measured as a ratio rather than
    # a distance, because an ellipse has no single radius to compare against.
    away = math.hypot((x - cx) / rx, (y - cy) / ry)
    if away <= 1.0:
        inner = (rx - ring - band) / rx
        middle = (rx - ring) / rx
        if away >= middle:
            return RING_DARK + (255,)
        if away >= inner:
            # Lit along the top left of the band, which is what makes it read as raised.
            lean = ((cx - x) / rx + (cy - y) / ry) / 2
            lit = max(0.0, min(1.0, lean * 1.5 + 0.5))
            return shade(mix(RIM, RIM_LIGHT, lit), (lit - 0.5) * 0.30) + (255,)

        for beadX, beadY, color, light in beads:
            gap = math.hypot(x - beadX, y - beadY)
            if gap <= bead_radius:
                if gap >= bead_radius - OVERSAMPLE * 0.8:
                    return RING_DARK + (255,)
                # Where on the bead this is, from the lit side to the shaded one.
                offset = ((x - beadX) + (y - beadY)) / (2 * bead_radius)
                return sphere(color, light, offset,
                              catchlight(x, y, beadX, beadY, bead_radius)) + (255,)

        return leather(x, y, width, height, away) + (255,)

    # The border, and what is drawn on it.
    for corner in ((gem_at, gem_at), (width - gem_at, gem_at),
                   (gem_at, height - gem_at), (width - gem_at, height - gem_at)):
        gap = math.hypot(x - corner[0], y - corner[1])
        if gap <= gem_radius:
            if gap >= gem_radius - OVERSAMPLE * 0.7:
                return RING_DARK + (255,)
            offset = ((x - corner[0]) + (y - corner[1])) / (2 * gem_radius)
            return sphere(GEM, GEM_LIGHT, offset,
                          catchlight(x, y, corner[0], corner[1], gem_radius)) + (255,)

    for inset in (rule_at, rule_at + rule_gap):
        edge = min(x, y, width - x, height - y)
        if abs(edge - inset) <= OVERSAMPLE * 0.5:
            return BORDER_RULE + (255,)

    return weathered(x, y, width, height) + (255,)


# How coarsely the finished colors are rounded. Two levels is under a percent of the range.
QUANTIZE = 3


def step(value):
    return min(255, (value + QUANTIZE // 2) // QUANTIZE * QUANTIZE)


def downsample(rows, big_width, big_height):
    out = []
    for y in range(HEIGHT):
        row = []
        for x in range(WIDTH):
            r = g = b = a = 0
            for dy in range(OVERSAMPLE):
                source = rows[y * OVERSAMPLE + dy]
                for dx in range(OVERSAMPLE):
                    pr, pg, pb, pa = source[x * OVERSAMPLE + dx]
                    r += pr
                    g += pg
                    b += pb
                    a += pa
            count = OVERSAMPLE * OVERSAMPLE
            # Rounded to a step, which is invisible on a mottled surface and is most of the
            # file size: noise at full depth gives the compressor nothing to work with, and a
            # sleeve is one texture every card in the game loads.
            row.append((step(r // count), step(g // count), step(b // count), a // count))
        out.append(row)
    return out


# ---------------------------------------------------------------------------
# The plain sleeve: one texture that every colored sleeve is a tint of.
# ---------------------------------------------------------------------------

# Near-white, because the color arrives as a multiply. A base painted in its own mid-gray
# would drag every dye a shade towards mud, and the black sleeve would come out invisible
# against the black rim.
PLAIN_PANEL = (0xF0, 0xF0, 0xF0)   # the field a picture sits on
PLAIN_BORDER = (0xB4, 0xB4, 0xB4)  # the band around it
PLAIN_RULE = (0x7E, 0x7E, 0x7E)    # the line between the two


def plain():
    """A bordered card with nothing on it, in gray, waiting for a dye and maybe a picture."""
    big_width, big_height = WIDTH * OVERSAMPLE, HEIGHT * OVERSAMPLE
    width, height = float(big_width), float(big_height)
    rim = RIM_WIDTH * width
    band = 0.105 * width
    rule = band - OVERSAMPLE * 1.0

    rows = []
    for by in range(big_height):
        row = []
        for bx in range(big_width):
            x, y = bx + 0.5, by + 0.5
            edge = min(x, y, width - x, height - y)
            if edge <= rim:
                row.append(EDGE + (255,))
                continue
            if edge <= rule:
                # The band, woven rather than flat: a sleeve is fabric and a solid rectangle
                # of one color is the one thing that would give that away.
                weave = blotches(x, y, width * 0.05, 7)
                row.append(shade(PLAIN_BORDER, (weave - 0.5) * 0.16) + (255,))
                continue
            if edge <= band:
                row.append(PLAIN_RULE + (255,))
                continue
            grain = (blotches(x, y, width * 0.18, 8) * 0.6
                     + blotches(x, y, width * 0.04, 9) * 0.4)
            row.append(shade(PLAIN_PANEL, (grain - 0.5) * 0.12) + (255,))
        rows.append(row)
    return downsample(rows, big_width, big_height)


def main():
    write_png(OUT, WIDTH, HEIGHT, paint())
    write_png(PLAIN_OUT, WIDTH, HEIGHT, plain())


if __name__ == "__main__":
    main()
