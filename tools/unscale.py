#!/usr/bin/env python3
"""Takes a contact sheet back apart into the textures it was made from.

Why this exists. A container snapshot took three unpushed commits with it, and the only
surviving copy of the mana symbols was a picture of them. A contact sheet is a lossless record
of its own sources as long as it was scaled by whole pixels: every block of scale x scale in
the sheet is one pixel of the original. So rather than redraw from a screenshot by eye - which
was tried three times and missed three times - this inverts the sheet.

The grid is found rather than given: columns and rows that are entirely background are the
gutters, and what is left between them are the cells. That way it does not matter what gap or
margin the sheet was laid out with, and the same code reads a 2x sheet and a 4x one.

Background becomes transparency, but only where it is connected to the edge of its cell, so a
pixel inside a symbol that happens to match the sheet's backdrop is kept.

    python3 tools/unscale.py art/reference/orbs-original.png 2 --into art/mana/badges
    python3 tools/unscale.py sheet.png 2 --grid 11,5 --tolerance 12 --into out
    python3 tools/unscale.py sheet.png 2 --names w,u,b --check <dir of originals>
"""
import os
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import textures  # noqa: E402
from mana_art import read_png, write_png  # noqa: E402


def ground(px):
    """The backdrop, taken from the border rather than from the whole sheet: a packed sheet is
    mostly picture, and the commonest color in one of these is a rim tone, not the ground."""
    height, width = len(px), len(px[0])
    edge = [px[0][x] for x in range(width)] + [px[height - 1][x] for x in range(width)] \
        + [px[y][0] for y in range(height)] + [px[y][width - 1] for y in range(height)]
    return Counter(edge).most_common(1)[0][0]


def near(one, two, tol):
    return all(abs(a - b) <= tol for a, b in zip(one[:3], two[:3]))


def runs(flags):
    """The [start, end) spans where flags is False - the cells between the gutters."""
    out, at = [], None
    for i, gutter in enumerate(flags):
        if gutter and at is not None:
            out.append((at, i))
            at = None
        elif not gutter and at is None:
            at = i
    if at is not None:
        out.append((at, len(flags)))
    return out


def cells(px, back, tol):
    """Every cell in the sheet, in reading order, as (left, top, right, bottom)."""
    height, width = len(px), len(px[0])
    blank_col = [all(near(px[y][x], back, tol) for y in range(height)) for x in range(width)]
    blank_row = [all(near(px[y][x], back, tol) for x in range(width)) for y in range(height)]
    out = []
    for top, bottom in runs(blank_row):
        for left, right in runs(blank_col):
            if any(not near(px[y][x], back, tol)
                   for y in range(top, bottom) for x in range(left, right)):
                out.append((left, top, right, bottom))
    return out


def middle(block):
    """The per-channel median of a block. On a clean sheet every pixel in the block is the
    same and this is that pixel; on a re-encoded one it throws away the ringing at the edges,
    which an average would smear back in."""
    def pick(channel):
        run = sorted(p[channel] for p in block)
        return run[len(run) // 2]
    return (pick(0), pick(1), pick(2), pick(3))


def shrink(px, box, scale):
    """One cell, brought back down: each scale x scale block is one source pixel."""
    left, top, right, bottom = box
    wide, tall = (right - left) // scale, (bottom - top) // scale
    out = textures.blank(wide, tall)
    for y in range(tall):
        for x in range(wide):
            block = [px[top + y * scale + dy][left + x * scale + dx]
                     for dy in range(scale) for dx in range(scale)]
            out[y][x] = Counter(block).most_common(1)[0][0] if len(set(block)) == 1 \
                else middle(block)
    return out


def snap(pieces, tol):
    """Pull every recovered color onto the small palette the art was actually drawn from.

    A re-encoded sheet hands back a cloud of near-misses around each real color. These are
    pixel art: a whole set is a few dozen colors, and every one of them appears hundreds of
    times, so the frequent colors are the real ones and everything within tol of one is it.
    """
    counts = Counter(p for piece in pieces for row in piece for p in row)
    seeds = []
    for color, _ in counts.most_common():
        if not any(near(color, kept, tol) for kept in seeds):
            seeds.append(color)

    # Every near-miss votes for its nearest seed, and the seed moves to the middle of its
    # votes. Picking the commonest member instead keeps whichever noisy sample happened to
    # repeat, which is how a cloud of near-misses turns into a palette of near-misses.
    where = {}
    for color in counts:
        where[color] = min(range(len(seeds)), key=lambda i: sum(
            (a - b) ** 2 for a, b in zip(color[:3], seeds[i][:3])))
    palette = []
    for index in range(len(seeds)):
        members = [(c, n) for c, n in counts.items() if where[c] == index]
        weight = sum(n for _, n in members)
        palette.append(tuple(
            round(sum(c[channel] * n for c, n in members) / weight) for channel in range(4)))

    for piece in pieces:
        for row in piece:
            for x, color in enumerate(row):
                row[x] = palette[where[color]]
    return pieces


def clear(px, back, tol=0):
    """Background connected to the edge becomes transparent; background walled in by the
    picture is left as it is, because a symbol is allowed to contain the sheet's own color."""
    height, width = len(px), len(px[0])
    seen = [[False] * width for _ in range(height)]
    edge = [(x, 0) for x in range(width)] + [(x, height - 1) for x in range(width)] \
        + [(0, y) for y in range(height)] + [(width - 1, y) for y in range(height)]
    stack = [(x, y) for x, y in edge if near(px[y][x], back, tol)]
    for x, y in stack:
        seen[y][x] = True
    while stack:
        x, y = stack.pop()
        px[y][x] = (0, 0, 0, 0)
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < width and 0 <= ny < height and not seen[ny][nx] \
                    and near(px[ny][nx], back, tol):
                seen[ny][nx] = True
                stack.append((nx, ny))
    return px


def center(pieces, size):
    """Put each recovered cell back on the canvas it was drawn on.

    The gutter hunt finds where the ink is, not where the canvas was: a circle that leaves a
    margin has that margin eaten as gutter. So every cell is laid back into a size x size
    canvas at one shared offset, taken from the widest cell found. Shared, because the cells
    of one sheet were all drawn the same way and per-cell centering would jitter any whose
    ink happens to be narrower.
    """
    wide = max(len(p[0]) for p in pieces)
    tall = max(len(p) for p in pieces)
    if wide > size or tall > size:
        raise SystemExit(f"a cell is {wide}x{tall}, larger than the {size}x{size} canvas")
    left, top = (size - wide) // 2, (size - tall) // 2
    out = []
    for piece in pieces:
        canvas = textures.blank(size, size)
        ox = left + (wide - len(piece[0])) // 2
        oy = top + (tall - len(piece)) // 2
        for y, row in enumerate(piece):
            for x, pixel in enumerate(row):
                canvas[oy + y][ox + x] = pixel
        out.append(canvas)
    return out


def grid(px, across, down, scale, size):
    """Cells taken from the sheet's own geometry rather than hunted for.

    Gutter hunting needs the background between cells to be exactly the background, which a
    re-encoded sheet cannot promise - the ringing around each symbol fills the gutter in and
    the whole sheet comes back as one cell. Given how many are across, the pitch is arithmetic
    and the canvas sits centered in each cell, which is both exact and unbreakable.
    """
    pitch = len(px[0]) // across
    span = size * scale
    if span > pitch:
        raise SystemExit(f"{size}x{scale} is {span} wide, more than the {pitch} pitch")
    pad = (pitch - span) // 2
    return [(col * pitch + pad, row * pitch + pad,
             col * pitch + pad + span, row * pitch + pad + span)
            for row in range(down) for col in range(across)]


def take(path, scale, size=None, tol=0, shape=None):
    """Every cell of a sheet, back at its original size."""
    sheet = read_png(path)
    back = ground(sheet)
    boxes = grid(sheet, shape[0], shape[1], scale, size) if shape \
        else cells(sheet, back, tol)
    pieces = [shrink(sheet, box, scale) for box in boxes]
    if tol:
        pieces = snap(pieces, tol)
    for piece in pieces:
        clear(piece, back, tol)
    if shape:
        return pieces
    return center(pieces, size) if size else pieces


def main(argv):
    path, scale = argv[0], int(argv[1])
    names = argv[argv.index("--names") + 1].split(",") if "--names" in argv \
        else list(textures.SYMBOL_NAMES)
    into = argv[argv.index("--into") + 1] if "--into" in argv else None
    against = argv[argv.index("--check") + 1] if "--check" in argv else None
    size = int(argv[argv.index("--size") + 1]) if "--size" in argv else textures.SYMBOL_SIZE
    tol = int(argv[argv.index("--tolerance") + 1]) if "--tolerance" in argv else 0
    shape = None
    if "--grid" in argv:
        across, down = argv[argv.index("--grid") + 1].split(",")
        shape = (int(across), int(down))

    got = take(path, scale, size, tol, shape)
    if len(got) < len(names):
        raise SystemExit(f"{path}: found {len(got)} cells, expected at least {len(names)}")

    if against:
        wrong = [n for n, px in zip(names, got)
                 if px != read_png(os.path.join(against, n + ".png"))]
        if wrong:
            raise SystemExit(f"{len(wrong)} of {len(names)} differ: "
                             + ", ".join(wrong[:12]))
        print(f"all {len(names)} recovered exactly from {path}")
        return
    for name, px in zip(names, got):
        write_png(os.path.join(into, name + ".png"), px)
    print(f"recovered {len(names)} textures from {path} at {scale}x")


if __name__ == "__main__":
    main(sys.argv[1:])
