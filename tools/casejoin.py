#!/usr/bin/env python3
"""A display case with its ends taken off, so a row of them is one long case.

Cases put side by side used to show two glass ends and two corner posts back to back at every
join, which reads as a row of separate boxes rather than as a shop's counter. The owner asked
for the edges between adjacent cases to go away (2026-09-16).

So the plain case is cut into four: closed at both ends, open at one end, open at the other,
and open at both. Which one a block draws is decided by its neighbors - see DisplayCaseBlock.

Derived from the plain model rather than drawn again, by three rules and no judgement:

1. A piece lying entirely within the outermost pixel of a joined side is an end piece, and goes.
2. Anything else reaching that pixel is run the last pixel out to the block's edge, so the
   lining, the glass and the lid carry through the join.
3. Glass keeps one texel to one pixel, and its window slides one texel off every joined edge.

The third rule is the whole of what makes a row read as one pane, and it is worth the paragraph.
Minecraft's glass is sixteen texels square: a one-texel frame line round the outside and a plain
interior. The plain case draws it at one texel to a pixel with u following the model's own x, so
the frame texels land at x 0-1 and 15-16 - exactly under the corner posts and the end rails. The
texture's frame and the case's woodwork are the same line, which is why nobody ever saw the glass
edge on a case standing by itself.

Open an end and that registration is what breaks. The post there is gone and the pane runs the
last pixel out to x 0 or x 16, so the frame texel that used to hide under the post is drawn in
the open - and the neighbor draws its own a pixel away, which is the bright double line down
every join the owner reported (2026-09-17). Sliding the window one texel off the joined edge puts
the interior there instead and carries the frame line along to the closed end, where the post
still is. A run therefore draws the frame once at each far end of the row and nowhere between:
one long pane with an edge only where the glass actually stops.

A piece open at both ends is sixteen pixels wide and the interior is only fourteen, so no single
window can cover it. It is cut at the block's midline and each half slid off its own edge; the
seam falls in the middle of a pane, between two interior texels that are both fully transparent,
so there is nothing there to see.

That means a new case model - a v6 of the artwork, say - regenerates all four without anybody
editing three copies of somebody else's work by hand.

    python3 tools/casejoin.py            write the joined models
    python3 tools/casejoin.py --check    say what would change, and change nothing

Run it after editing the plain case, then run tools/woodwork.py, then the gate.
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MODELS = ROOT / "common/src/main/resources/assets/gathering/models/block"
PLAIN = "display_case"

#: The four cases, and which ends each has lost.
JOINS = {
    PLAIN: (False, False),
    PLAIN + "_left": (True, False),
    PLAIN + "_right": (False, True),
    PLAIN + "_middle": (True, True),
}

#: How wide the end of a case is, in model pixels. Everything inside this is the end.
END = 1.0

#: The texture whose window is slid, and how wide its frame line is. Anything else - the planks,
#: the lining - tiles or is a flat color, and nothing shows if it runs a pixel further.
GLASS = "minecraft:block/glass"
FRAME = 1

#: Where a piece open at both ends is cut in two, so each half can slide off its own edge.
MIDLINE = 8

#: Which of an element's axes each face's u runs along, and which way. These are the game's own
#: defaults: u climbs with x on a south face and falls with it on a north one, because the two
#: are looked at from opposite sides. Reading them off wrong mirrors the slide into the join it
#: is meant to leave.
U_AXIS = {"north": (0, -1), "south": (0, 1), "east": (2, -1), "west": (2, 1),
          "up": (0, 1), "down": (0, 1)}


def isGlass(model: dict, element: dict) -> bool:
    """Whether a piece is drawn in the glass whose frame line has to be kept out of a join."""
    textures = model.get("textures", {})
    for face in element.get("faces", {}).values():
        named = face.get("texture", "")
        if (textures.get(named[1:], named) if named.startswith("#") else named) == GLASS:
            return True
    return False


def pane(element: dict, low, high, offLeft: bool, offRight: bool) -> dict:
    """One glass piece spanning x low to high, its window slid a texel off each joined edge."""
    piece = json.loads(json.dumps(element))
    wasLow = element["from"][0]
    wasHigh = element["to"][0]
    piece["from"][0] = low
    piece["to"][0] = high
    for direction, face in piece.get("faces", {}).items():
        axis, way = U_AXIS[direction]
        uv = face.get("uv")
        if axis != 0 or uv is None:
            continue
        # The piece as drawn says u = way * x + start; keep that reading of the artwork and move
        # it, rather than deciding afresh where the window belongs.
        start = uv[0] - way * (wasLow if way > 0 else wasHigh)
        start += (way * FRAME if offLeft else 0) - (way * FRAME if offRight else 0)
        uv[0] = way * (low if way > 0 else high) + start
        uv[2] = way * (high if way > 0 else low) + start
    return piece


def joined(model: dict, openLeft: bool, openRight: bool) -> dict:
    """The same case with the named ends taken off and everything else run out to meet its neighbor."""
    out = json.loads(json.dumps(model))
    kept = []
    for element in out.get("elements", []):
        low = float(element["from"][0])
        high = float(element["to"][0])
        if openLeft and high <= END:
            continue
        if openRight and low >= 16 - END:
            continue
        grewLeft = openLeft and 0 < low <= END
        grewRight = openRight and 16 - END <= high < 16
        if not isGlass(out, element):
            if grewLeft:
                element["from"][0] = 0
            if grewRight:
                element["to"][0] = 16
            kept.append(element)
            continue
        if grewLeft and grewRight:
            # Sixteen pixels of pane and fourteen texels of interior, so it takes two windows.
            # Neither half keeps a face on the cut: they are the same sheet of glass.
            left = pane(element, 0, MIDLINE, True, False)
            right = pane(element, MIDLINE, 16, False, True)
            left["faces"].pop("east", None)
            right["faces"].pop("west", None)
            kept.extend([left, right])
        elif grewLeft or grewRight:
            kept.append(pane(element, 0 if grewLeft else element["from"][0],
                             16 if grewRight else element["to"][0], grewLeft, grewRight))
        else:
            kept.append(element)
    out["elements"] = kept
    return out


def main() -> int:
    check = "--check" in sys.argv
    plain = json.loads((MODELS / (PLAIN + ".json")).read_text(encoding="utf-8"))
    changed = []
    for name, (openLeft, openRight) in JOINS.items():
        if name == PLAIN:
            continue
        path = MODELS / (name + ".json")
        written = json.dumps(joined(plain, openLeft, openRight), indent=2) + "\n"
        if path.exists() and path.read_text(encoding="utf-8") == written:
            continue
        changed.append(name)
        if not check:
            path.write_text(written, encoding="utf-8")
    print(f"{len(changed)} joined case model(s) {'would change' if check else 'written'}, "
          f"{len(JOINS) - 1 - len(changed)} already current")
    return 1 if check and changed else 0


if __name__ == "__main__":
    sys.exit(main())
