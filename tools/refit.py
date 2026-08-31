#!/usr/bin/env python3
"""Rebuilds the mana badges and marks from pictures of them.

A container snapshot took the originals with it. What survived was two screenshots: a contact
sheet of all fifty-four, re-encoded on the way through a chat and so a few levels off on every
pixel, and a lossless close-up of three hybrids. Neither alone is enough. Together they are:

  - The close-up is exact, so it fixes the palette and settles the geometry - a 30-pixel circle
    in a 32-pixel canvas, cut corner to corner, one rim colour taken from the top-left half.
  - The sheet is noisy per pixel but says the same thing many times. Twenty-six symbols share
    the plain grey badge, seven views show each coloured one. Where most of them agree the
    colour is certain; the badge is built from those, and what is certain fixes the palette
    that the rest is snapped onto.
  - What is left is where the glyphs pile up in the middle, and those heal from their
    neighbours: the badge is flat bands, so a hole inside one is the band around it.

Then each symbol's mark is whatever it has that its badge does not, kept in its own file so it
can be repainted. Hybrid marks are their own files rather than shrunken copies, because the
originals were drawn at that size and a shrunken copy is not what was there.

Checked against the close-up, which is not used to build any of the six colours it shows.

    python3 tools/refit.py art/reference/orbs.png art/reference/hybrids-zoom.png
"""
import os
import statistics
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import textures  # noqa: E402
import unscale  # noqa: E402
from mana_art import (BADGES, FONT, INK, MARKS, OUT, RADIUS, SIZE,  # noqa: E402
                       font, inside, keyFor, markName, markPath, onRim, read_png,
                       regions, write_png)

# The sheet's layout, and the close-up's.
SHEET_ACROSS, SHEET_DOWN, SHEET_SCALE = 11, 5, 2
ZOOM_NAMES, ZOOM_SCALE = ("wu", "bg", "2r"), 4

# The two pictures are not the same version, and this is the difference between them: the
# sheet cuts its hybrids from top-left to bottom-right, which put both marks along the cut
# rather than one on each colour - the thing that was wrong with them. The close-up is the
# fixed cut. So the sheet is read with its own seam and written back with the fixed one.
SHEET_SEAM, GOOD_SEAM = "minus", "plus"

# The badge every other badge is measured against: the one the most symbols wear.
REFERENCE = "generic"

# How much more saturated the set ships than it was measured. The originals were a shade
# chalky on a felt background; this lifts the colour without touching how light or dark any
# band is, so the shading is exactly the shading that was recovered.
VIBRANCE = 1.20

# How close two colours have to be to count as the same one, and how many of a badge's views
# have to agree before a pixel is taken as certain.
TOL = 6
SURE = 0.68

# Where a mark's coverage stops being noise, and where it becomes solid.
FAINT = 0.22
SOLID = 0.90

# How far inside the outline a pixel has to be before it can be taken for ink.
INK_CLEAR = 3.0


def views():
    """Every whole-circle look each badge colour gets, as key -> [symbol].

    Only the plain badges. A hybrid on the sheet would add a second look at each of its two
    colours, but its marks lie across the cut - that was the thing wrong with them - so half
    of one colour's mark sits on the other colour's half and poisons anything measured there.
    Every colour has at least one plain badge, and grey has twenty-six, so nothing is lost.
    """
    out = {}
    for name in textures.SYMBOL_NAMES:
        base, second = keyFor(name)
        if not second:
            out.setdefault(base, []).append(name)
    return out


def split(x, y, seam):
    """True on the half a hybrid's second colour takes."""
    dx, dy, _ = inside(x, y)
    return (dx + dy > 0) if seam == "plus" else (dx - dy > 0)


def inRegion(x, y, region, seam):
    return region == "full" or split(x, y, seam) == (region == "br")


def clump(values, tol):
    out = []
    for value in values:
        for group in out:
            if unscale.near(value, group[0], tol):
                group.append(value)
                break
        else:
            out.append([value])
    return sorted(out, key=len, reverse=True)


def middleOf(group):
    return tuple(int(statistics.median(p[c] for p in group)) for c in range(3)) + (255,)


def assemble(shots, looks):
    """One badge colour's whole circle, from every symbol that shows any of it.

    Three passes. Where most of the views agree, that is the colour. Those certain pixels are
    the palette. Everything else is snapped onto that palette and voted on, with the two dark
    entries - rim and ink - barred off the rim, since a badge pixel away from the edge is
    never either. What is still dark after that is a hole the glyphs made, and it heals from
    the band around it.
    """
    seen = {}
    for name in looks:
        art = shots[name]
        for y in range(SIZE):
            for x in range(SIZE):
                if art[y][x][3]:
                    seen.setdefault((x, y), []).append(art[y][x])

    # With three or more views a pixel is certain when most of them agree. Colorless, snow and
    # energy get one view each, so there is nothing to agree with: every pixel is taken as it
    # is, the palette comes from clumping that one picture, and the glyph is healed out
    # afterwards like any other hole - which works because a mark is darker than any band.
    sure = {}
    for spot, values in seen.items():
        groups = clump(values, TOL)
        if len(values) < 3 or len(groups[0]) >= max(2, round(len(values) * SURE)):
            sure[spot] = middleOf(groups[0])

    counts = {}
    for color in sure.values():
        counts[color] = counts.get(color, 0) + 1
    palette = []
    for color, _ in sorted(counts.items(), key=lambda item: -item[1]):
        if not any(unscale.near(color, kept, TOL) for kept in palette):
            palette.append(color)

    def snap(color):
        return min(palette, key=lambda k: sum((a - b) ** 2 for a, b in zip(color[:3], k[:3])))

    rim = next((snap(sure[(x, y)]) for y in range(SIZE) for x in range(SIZE)
                if (x, y) in sure and onRim(x, y)), None)
    dark = {rim} if rim else set()
    rest = [c for c in palette if c != rim]
    if rest:
        dark.add(min(rest, key=lambda c: sum(c[:3])))

    badge = textures.blank(SIZE, SIZE)
    for spot, values in seen.items():
        x, y = spot
        if spot in sure:
            badge[y][x] = snap(sure[spot])
            continue
        landed = [snap(v) for v in values]
        keep = [v for v in landed if v not in dark or onRim(x, y)] or landed
        badge[y][x] = max(set(keep), key=keep.count)

    for _ in range(SIZE):
        moved = 0
        for y in range(SIZE):
            for x in range(SIZE):
                if not badge[y][x][3] or onRim(x, y) or badge[y][x] not in dark:
                    continue
                near = [badge[y + dy][x + dx]
                        for dy in (-1, 0, 1) for dx in (-1, 0, 1)
                        if 0 <= y + dy < SIZE and 0 <= x + dx < SIZE
                        and badge[y + dy][x + dx][3] and not onRim(x + dx, y + dy)
                        and badge[y + dy][x + dx] not in dark]
                if near:
                    badge[y][x] = max(set(near), key=near.count)
                    moved += 1
        if not moved:
            break
    return badge, rim


def bandsOf(badge):
    """Which shading band each pixel belongs to, as an index into a bright-to-dark list.

    Every badge is the same sphere under the same light, so the map is the same for all of
    them - only the colours change. Taken from the grey badge, which is the one built from
    twenty-six views and so the only one that is clean everywhere.
    """
    order = sorted({p for row in badge for p in row if p[3]}, key=lambda c: -sum(c[:3]))
    at = {color: index for index, color in enumerate(order)}
    return {(x, y): at[badge[y][x]]
            for y in range(SIZE) for x in range(SIZE) if badge[y][x][3]}, len(order)


def tonesOf(shots, looks, bands, count):
    """What one colour looks like in each band.

    A band is fifty to a hundred and fifty pixels and a glyph covers a slice of several, so
    the middle of a band is the band even when a mark is sitting on part of it - which is what
    makes this work for the colours that only get one or two views. Done twice: the first
    median sets the mark aside, the second is taken without it.
    """
    seen = [[] for _ in range(count)]
    for name in looks:
        art = shots[name]
        for (x, y), band in bands.items():
            if art[y][x][3]:
                seen[band].append(art[y][x])
    out = []
    for group in seen:
        if not group:
            out.append(None)
            continue
        first = middleOf(group)
        clean = [p for p in group if unscale.near(p, first, TOL * 3)] or group
        out.append(middleOf(clean))
    # A band nobody saw borrows its neighbour, so there is never a hole.
    for index, tone in enumerate(out):
        if tone is None:
            out[index] = next((t for t in out[index:] if t), None) \
                or next(t for t in reversed(out[:index]) if t)
    return out


def vivid(color, amount=VIBRANCE):
    """More colour, same brightness: each channel moves away from the pixel's own grey."""
    grey = 0.299 * color[0] + 0.587 * color[1] + 0.114 * color[2]
    return tuple(max(0, min(255, round(grey + (v - grey) * amount)))
                 for v in color[:3]) + (color[3],)


def paint(bands, tones):
    """A badge, straight from the band map and one colour's tones. No holes to heal, because
    nothing was ever measured pixel by pixel."""
    out = textures.blank(SIZE, SIZE)
    for (x, y), band in bands.items():
        out[y][x] = tones[band]
    return out


def compose(base, other, rims):
    """A hybrid's badge: each half from its own colour, and one outline, which is the base's -
    the close-up shows a red half wearing the grey badge's outline all the way round.

    Only pixels that are actually the second colour's outline get repainted. Repainting
    everything within a pixel of the edge instead puts the outline over the lit edge - the
    bright arc the light catches just inside it - and four pixels of it come out dark, which
    reads as a shadow sitting in the top left of every hybrid.
    """
    if not other[0]:
        return [row[:] for row in base[0]]
    out = textures.blank(SIZE, SIZE)
    for y in range(SIZE):
        for x in range(SIZE):
            second = split(x, y, GOOD_SEAM)
            here = other[0] if second else base[0]
            if not here[y][x][3]:
                continue
            out[y][x] = rims[0] if second and here[y][x] == other[1] else here[y][x]
    return out


def inkOf(shots, badge, looks):
    """The colour a mark is drawn in on this badge.

    Marks are not flat: the close-up shows pixels two thirds of the way from the band to the
    ink, which is an anti-aliased edge, so the ink itself is the far end of that run rather
    than the commonest value. Take the darkest clump that turns up often enough to be a real
    colour rather than a re-encoding artefact.
    """
    off = []
    for name in looks:
        art = shots[name]
        for y in range(SIZE):
            for x in range(SIZE):
                # Well clear of the edge. The outline is a shade darker than any ink and in
                # places two pixels thick, so anything caught near it wins "darkest" and the
                # ink comes out as the outline for every colour at once.
                if art[y][x][3] and inside(x, y)[2] < RADIUS - INK_CLEAR \
                        and not unscale.near(art[y][x], badge[y][x], TOL):
                    off.append(art[y][x])
    groups = [g for g in clump(off, TOL) if len(g) >= 8]
    if not groups:
        return None
    return middleOf(min(groups, key=lambda g: sum(middleOf(g)[:3])))


def alphaOf(art, badge, ink, region, seam):
    """How much ink is on each pixel: the mark, as a silhouette with soft edges.

    Every pixel is somewhere on the line between its band and the ink, so where it sits on
    that line is the coverage. Solving for it rather than thresholding is what keeps the
    handful of half-covered pixels that a mark's diagonals are made of.
    """
    out = textures.blank(SIZE, SIZE)
    for y in range(SIZE):
        for x in range(SIZE):
            if not art[y][x][3] or not inRegion(x, y, region, seam) or onRim(x, y):
                continue
            under, over = badge[y][x], ink
            span = [b - a for a, b in zip(under[:3], over[:3])]
            length = sum(v * v for v in span)
            if not length:
                continue
            along = sum((p - a) * v for p, a, v in zip(art[y][x][:3], under[:3], span))
            amount = max(0.0, min(1.0, along / length))
            # A band is a hundred-odd levels from its ink, so a few levels of re-encoding
            # noise reads as a few percent of coverage. Below the floor that is noise and the
            # pixel is bare badge; above the ceiling it is solid ink. Between them it is a
            # real soft edge - the close-up has pixels two thirds covered - and is kept.
            if amount < FAINT:
                continue
            out[y][x] = (0, 0, 0, 255 if amount > SOLID else round(amount * 255))
    return out


def press(badge, mark, ink):
    """Put a mark back on its badge, in the badge's own ink."""
    out = [row[:] for row in badge]
    for y in range(SIZE):
        for x in range(SIZE):
            amount = mark[y][x][3] / 255.0
            if not amount or not out[y][x][3]:
                continue
            out[y][x] = tuple(round(a + (b - a) * amount)
                              for a, b in zip(out[y][x][:3], ink[:3])) + (255,)
    return out


def markName(name, region):
    """Which mark file a symbol's region draws from."""
    if region == "full":
        return "p" if len(name) == 2 and name.endswith("p") else name
    return name[0] if region == "tl" else name[1]


def agree(one, two):
    return all(a == b for ra, rb in zip(one, two) for a, b in zip(ra, rb))


def vote(masks):
    """One mark from every view of it. The same phi sits on five badges; where the views
    disagree it is the re-encoding talking, and the middle of them is the mark."""
    out = textures.blank(SIZE, SIZE)
    for y in range(SIZE):
        for x in range(SIZE):
            out[y][x] = (0, 0, 0, int(statistics.median(m[y][x][3] for m in masks)))
    return out


def anchor(mark):
    """Where a mark sits, as the middle of its ink."""
    spots = [(x, y) for y in range(SIZE) for x in range(SIZE) if mark[y][x][3]]
    return (sum(x for x, _ in spots) / len(spots), sum(y for _, y in spots) / len(spots))


def shift(mark, by):
    out = textures.blank(SIZE, SIZE)
    for y in range(SIZE):
        for x in range(SIZE):
            tx, ty = x + by[0], y + by[1]
            if mark[y][x][3] and 0 <= tx < SIZE and 0 <= ty < SIZE:
                out[ty][tx] = mark[y][x]
    return out


def halfMarks(truth, badges, rims, inks):
    """The half-size marks, off the close-up, which is the only picture that has them in the
    right places. It shows each of the six in one corner only, so the other corner is the same
    art moved by the gap between the two - they are one drawing placed twice.
    """
    got = {}
    for name in ZOOM_NAMES:
        base, other = keyFor(name)
        whole = compose((badges[base], rims[base]), (badges[other], rims[other]),
                        (rims[base],))
        for region in ("tl", "br"):
            key = base if region == "tl" else other
            got[(region, markName(name, region))] = \
                alphaOf(truth[name], whole, inks[key], region, GOOD_SEAM)

    here = [anchor(m) for (region, _), m in got.items() if region == "tl"]
    there = [anchor(m) for (region, _), m in got.items() if region == "br"]
    gap = (round(sum(p[0] for p in there) / len(there) - sum(p[0] for p in here) / len(here)),
           round(sum(p[1] for p in there) / len(there) - sum(p[1] for p in here) / len(here)))

    out = dict(got)
    for (region, name), mark in got.items():
        other = ("br", name) if region == "tl" else ("tl", name)
        if other not in out:
            out[other] = shift(mark, gap if region == "tl" else (-gap[0], -gap[1]))
    return out, gap


def main(argv):
    sheet, zoom = argv[0], argv[1]
    # Deliberately not flattened. unscale's own palette pass works across the whole sheet at
    # once, which merges bands of one colour that happen to sit near bands of another; the
    # work below builds a palette per badge instead, where the bands are far apart.
    shots = dict(zip(textures.SYMBOL_NAMES,
                     unscale.take(sheet, SHEET_SCALE, SIZE, unscale.TOLERANCE,
                                  (SHEET_ACROSS, SHEET_DOWN), flatten=False)))
    truth = dict(zip(ZOOM_NAMES, unscale.take(zoom, ZOOM_SCALE, SIZE, 0,
                                              (len(ZOOM_NAMES), 1))))

    # Grey first, and only grey, the hard way: twenty-six plain badges wear it, so where most
    # of them agree the colour is certain and the rest heals. That gives one clean badge, and
    # one clean badge gives the band map every other colour is painted through.
    looks = views()
    grey, _ = assemble(shots, looks[REFERENCE])
    bands, count = bandsOf(grey)

    # Measured first, brightened second. Everything below - which pixels are ink, how much of
    # one covers a pixel - is solved against the sheet, so it has to be solved against what the
    # sheet actually holds. The lift is applied to what ships, not to what is measured.
    badges, rims, inks = {}, {}, {}
    for key in looks:
        tones = tonesOf(shots, looks[key], bands, count)
        badges[key] = paint(bands, tones)
        rims[key] = next(badges[key][y][x] for y in range(SIZE) for x in range(SIZE)
                         if badges[key][y][x][3] and onRim(x, y))
        inks[key] = inkOf(shots, badges[key], looks[key])

    # Full-size marks come off the sheet, where the plain badges have no seam to get wrong.
    whole, seen = {}, {}
    for name in textures.SYMBOL_NAMES:
        base, other = keyFor(name)
        whole[name] = compose((badges[base], rims[base]),
                              (badges[other], rims[other]) if other else (None, None),
                              (rims[base],))
        if not other:
            seen.setdefault(("full", markName(name, "full")), []).append(
                alphaOf(shots[name], whole[name], inks[base], "full", SHEET_SEAM))
    marks = {spot: vote(masks) for spot, masks in seen.items()}
    marks.update(halfMarks(truth, badges, rims, inks)[0])

    # Two versions of every symbol: the one that was measured, which is what the close-up is
    # checked against, and the one that ships, which is the same art with more colour in it.
    made = {}
    for name in textures.SYMBOL_NAMES:
        bare = [[vivid(p) if p[3] else p for p in row] for row in whole[name]]
        write_png(os.path.join(BADGES, name + ".png"), bare)
        asFound, toShip = whole[name], bare
        for region, key in regions(name):
            mark = marks[(region, markName(name, region))]
            asFound = press(asFound, mark, inks[key])
            toShip = press(toShip, mark, vivid(inks[key]))
        made[name] = asFound
        write_png(os.path.join(OUT, name + ".png"), toShip)
    for (region, name), mark in marks.items():
        write_png(markPath(region, name), mark)
    # The ink a badge draws its marks in is part of the badge, not of the mark - a mark is a
    # silhouette so that one drawing can sit on five colours. Written out beside the art so
    # composing needs nothing but the files.
    with open(INK, "w") as handle:
        handle.write("{\n" + ",\n".join(
            '  "%s": "#%02X%02X%02X"' % ((key,) + vivid(inks[key])[:3])
            for key in sorted(inks) if inks[key]) + "\n}\n")
    font(textures.SYMBOL_NAMES, FONT)

    # The close-up never fed any of this. If the rebuild matches it, the rebuild is the
    # original - and where it does not, say exactly where rather than rounding it off.
    for name, want in truth.items():
        got = made[name]
        off = [max(abs(a - b) for a, b in zip(got[y][x], want[y][x]))
               for y in range(SIZE) for x in range(SIZE) if got[y][x] != want[y][x]]
        print(f"  {name}: {SIZE * SIZE - len(off)} of {SIZE * SIZE} match"
              + (f", {len(off)} off by up to {max(off)}" if off else " exactly"))
    print(f"rebuilt {len(made)} symbols, {len(badges)} badges, {len(marks)} marks")


if __name__ == "__main__":
    main(sys.argv[1:])
