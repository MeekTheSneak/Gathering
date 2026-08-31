#!/usr/bin/env python3
"""Cuts the mana symbols out of one SVG sheet and writes the font the mod reads them with.

The symbols were drawn here by hand for a while and it did not work. They are small, they are
read at a glance, and a player already knows what every one of them looks like - so a sun that
is nearly right reads as wrong, which is what happened twice. These are the real shapes.

Where they come from: tools/mana-symbols.svg, a community redraw of the printed symbols from
a forum post credited in its own header. It is vendored rather than fetched, so regenerating
this needs no network and produces the same files every time.

What this means for section 15 of the brief: the mana symbols are Wizards' trade dress, and the
brief used to say none of their imagery ships in the jar. That is no longer true and the brief
now says so. The Fan Content Policy covers a free, non-commercial fan mod using their marks,
which this is; nothing else in the mod changed - the card back and the sleeves are still ours.

Needs cairosvg to run, which is not needed to build the mod: the PNGs are committed.

    pip install cairosvg && python3 tools/mana.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import textures  # noqa: E402  - the PNG writer and the drawn fallbacks live there

SHEET = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mana-symbols.svg")
OUT = "common/src/main/resources/assets/gathering/textures/font/mana"
FONT = "common/src/main/resources/assets/gathering/font/mana.json"

# The sheet's own coordinates: a 10-wide grid of discs on 105-unit centers, starting here.
FIRST_X, FIRST_Y, STEP = -895.0, -160.0, 105.0
VIEW_X, VIEW_Y = -945.0, -210.002
VIEW_W, VIEW_H = 1045.0, 730.002

# Rendered this much larger than the sheet, so a 104-unit cell is 416 pixels before it is
# taken down to 32. Anything less and the thin parts of the flame come out ragged.
SCALE = 4

# Half a cell, a shade wider than the disc's own radius of 50, so nothing clips its edge.
HALF = 52.0

# Which symbol sits in which cell, row by row, exactly as the sheet lays them out. Blank
# entries are cells holding something the mod has no use for - the infinity symbol, the half,
# the chaos symbol from the planechase decks.
GRID = [
    ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9"],
    ["10", "11", "12", "13", "14", "15", "16", "17", "18", "19"],
    ["20", "x", "y", "z", "w", "u", "b", "r", "g", "s"],
    ["wu", "wb", "ub", "ur", "br", "bg", "rg", "rw", "gw", "gu"],
    ["2w", "2u", "2b", "2r", "2g", "wp", "up", "bp", "rp", "gp"],
    ["tap", "untap", "", "", "", "", "", "", "", ""],
]

# Not on the sheet, so they are drawn here instead - as vectors, in the sheet's own colors and
# at the sheet's own size, so they sit in the family rather than beside it. A pixel grid was
# tried first and looked exactly like what it was next to fifty-two smooth ones.
DISC = "#CAC5C0"
INK = "#0D0F0F"

MISSING = {
    # Colorless: a four-pointed star with the sides pulled in.
    "c": f'<path fill="{INK}" d="M50 8 C54 30 70 46 92 50 C70 54 54 70 50 92 '
         f'C46 70 30 54 8 50 C30 46 46 30 50 8 Z"/>',
    # Energy, as the bolt it is counted in.
    "energy": f'<path fill="{INK}" d="M58 8 L24 54 L44 54 L36 92 L74 42 L52 42 Z"/>',
}


def cut(sheet, name, column, row):
    """One disc, taken off the rendered sheet and brought down to the size a glyph is."""
    from PIL import Image

    x = (FIRST_X + STEP * column - VIEW_X) * SCALE
    y = (FIRST_Y + STEP * row - VIEW_Y) * SCALE
    half = HALF * SCALE
    disc = sheet.crop((round(x - half), round(y - half), round(x + half), round(y + half)))
    disc = disc.resize((textures.SYMBOL_SIZE, textures.SYMBOL_SIZE), Image.LANCZOS)
    disc.save(os.path.join(OUT, name + ".png"))


def main():
    import cairosvg
    from PIL import Image

    os.makedirs(OUT, exist_ok=True)
    rendered = os.path.join(os.path.dirname(SHEET), ".mana-sheet.png")
    cairosvg.svg2png(url=SHEET, write_to=rendered,
                     output_width=round(VIEW_W * SCALE), output_height=round(VIEW_H * SCALE))
    sheet = Image.open(rendered).convert("RGBA")

    cut_count = 0
    for row, names in enumerate(GRID):
        for column, name in enumerate(names):
            if name:
                cut(sheet, name, column, row)
                cut_count += 1
    os.remove(rendered)

    for name, shape in MISSING.items():
        drawing = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">'
                   f'<circle cx="50" cy="50" r="50" fill="{DISC}"/>{shape}</svg>')
        cairosvg.svg2png(bytestring=drawing.encode("utf-8"),
                         write_to=os.path.join(OUT, name + ".png"),
                         output_width=textures.SYMBOL_SIZE,
                         output_height=textures.SYMBOL_SIZE)

    # The font, written from the same list the mod's ManaSymbols reads - see SYMBOL_NAMES.
    providers = []
    for index, name in enumerate(textures.SYMBOL_NAMES):
        providers.append(
            "    {\n"
            '      "type": "bitmap",\n'
            f'      "file": "gathering:font/mana/{name}.png",\n'
            '      "ascent": 8,\n'
            '      "height": 9,\n'
            f'      "chars": ["\\u{textures.FIRST_CODEPOINT + index:04X}"]\n'
            "    }"
        )
    os.makedirs(os.path.dirname(FONT), exist_ok=True)
    with open(FONT, "w") as handle:
        handle.write('{\n  "providers": [\n' + ",\n".join(providers) + "\n  ]\n}\n")

    missing = [n for n in textures.SYMBOL_NAMES
               if not os.path.exists(os.path.join(OUT, n + ".png"))]
    if missing:
        raise SystemExit("no picture for: " + ", ".join(missing))
    print(f"cut {cut_count} symbols off the sheet, drew {len(MISSING)}, "
          f"wrote {len(providers)} glyphs")


if __name__ == "__main__":
    main()
