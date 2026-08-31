#!/usr/bin/env python3
"""Cuts the sprites this mod uses out of BDragon1727's pixel UI sheets.

Run once, by hand, with the sheets on disk. What it writes is checked in, so the build never
needs the packs and nobody has to find them again:

    python3 tools/pack_cut.py <folder holding 00.png, 03.png, 04.png>

Each sheet draws the same element several times over in different colours. Only one of each
is taken - the grey one, because it is the one whose tones carry the most detail and the
least hue, and every theme's version is made by moving that ramp onto its own colours rather
than by picking whichever of his three happens to be nearest. See recut() in gui_art.py.

The cells are given by hand rather than found, because "find the islands" gets a different
answer on a sheet whose author let two shapes touch, and a cut that silently moves is a cut
nobody notices has moved.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "art", "gui", "parts")

#: sheet -> name -> (x, y, width, height). The grey group of 00.png sits at x + 64.
CELLS = {
    "00.png": {
        "arrow_left": (19 + 64, 2, 9, 12),
        "arrow_right": (37 + 64, 2, 9, 12),
        "scroll_thumb": (4 + 64, 7, 8, 18),
        "button": (0 + 64, 85, 48, 22),
    },
    # The bar and the box it runs in. Both are sheared - the ends are cut on the diagonal -
    # which is most of what makes them read as pixel art rather than as two rectangles. The
    # grey track and the yellow fill, because recut() sorts by lightness and the colour of
    # the one it is given never survives anyway.
    "04.png": {
        "bar_track": (0, 19, 48, 11),
        "bar_fill": (51, 22, 42, 5),
    },

    # A box of pips that fill one at a time, taken apart rather than whole. The box is a
    # three-pixel cap, five identical five-pixel cells and a two-pixel cap, so cutting one
    # cell of each kind lets a bar be built for a match of any length - and the game's own
    # nine-slice cannot do it, because in 1.21.1 a nine-slice middle stretches and has no
    # tiling option, which would smear five pips into one long smudge.
    "06.png": {
        "pip_left": (1, 3, 3, 10),
        "pip_full": (4, 3, 5, 10),
        "pip_empty": (164, 3, 5, 10),
        "pip_right": (189, 3, 2, 10),
    },

    # Five frames of a dashed ring with the lit dash travelling round it, in a row. Rows two
    # to forty-five: below that the same sheet carries a ring of wedges, which is a different
    # spinner and not this one.
    "03.png": {
        "spinner_0": (2, 2, 44, 44),
        "spinner_1": (50, 2, 44, 44),
        "spinner_2": (98, 2, 44, 44),
        "spinner_3": (146, 2, 44, 44),
        "spinner_4": (194, 2, 44, 44),
    },
}


def main(argv):
    if not argv:
        raise SystemExit(__doc__)
    where = argv[0]
    os.makedirs(OUT, exist_ok=True)
    written = 0
    for sheet, cells in CELLS.items():
        path = os.path.join(where, sheet)
        if not os.path.isfile(path):
            raise SystemExit("no " + path)
        art = Image.open(path).convert("RGBA")
        for name, (x, y, wide, tall) in cells.items():
            cut = art.crop((x, y, x + wide, y + tall))
            if cut.getbbox() is None:
                raise SystemExit(name + " cut nothing out of " + sheet)
            cut.save(os.path.join(OUT, name + ".png"))
            written += 1
    print(f"cut {written} sprites into {OUT}")


if __name__ == "__main__":
    main(sys.argv[1:])
