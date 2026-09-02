#!/usr/bin/env python3
"""Paints the blocks: the felt on a table, a collection, a shop counter, a sealed box.

The one generator whose output nothing else makes. The screen sprites come out of
tools/gui_art.py and the mana symbols out of tools/mana_art.py; this is the six block faces
and the one item face that are drawn from a palette rather than by hand.

Run from the repository root:

    python3 tools/block_art.py

The felt is on the protected list in tools/pngwrite.py - it was generated once and has been
repainted since - so running this leaves it alone and says so.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pngwrite import blank, isProtected, rgba, write_png  # noqa: E402

# ---------------------------------------------------------------------------
# Palette. A theme is this block and nothing else.
# ---------------------------------------------------------------------------
PALETTE = {
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

#: How many samples across a pixel the round shapes are drawn with.
SUPERSAMPLE = 4

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


#: Each generator, and one file it writes - enough to tell whether it still owns its output.
GENERATORS = [
    (felt, "block/table_felt.png"),
    (collection, "block/collection.png"),
    (shop_counter, "block/shop_counter.png"),
    (sealed_box, "item/sealed.png"),
]


def main():
    """Runs every generator that still owns what it writes, and says which do not.

    A protected file stops the writer dead, which is right when a script asks for one by
    mistake and wrong here: three of these four are still generated, and one hand-painted
    felt used to mean none of them could be run again.
    """
    for paint, sample in GENERATORS:
        if isProtected("/textures/" + sample):
            print("leaving", sample, "alone - it is painted by hand now")
            continue
        paint()


if __name__ == "__main__":
    main()
