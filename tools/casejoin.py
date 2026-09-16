#!/usr/bin/env python3
"""A display case with its ends taken off, so a row of them is one long case.

Cases put side by side used to show two glass ends and two corner posts back to back at every
join, which reads as a row of separate boxes rather than as a shop's counter. The owner asked
for the edges between adjacent cases to go away (2026-09-16).

So the plain case is cut into four: closed at both ends, open at one end, open at the other,
and open at both. Which one a block draws is decided by its neighbors - see DisplayCaseBlock.

Derived from the plain model rather than drawn again, by two rules and no judgement:

1. A piece lying entirely within the outermost pixel of a joined side is an end piece, and goes.
2. Anything else reaching that pixel is stretched the last pixel to the block's edge, so the
   lining, the glass and the lid run through the join without a seam.

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
        if openLeft and low <= END:
            element["from"][0] = 0
        if openRight and high >= 16 - END:
            element["to"][0] = 16
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
