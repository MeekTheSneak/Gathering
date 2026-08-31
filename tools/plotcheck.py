#!/usr/bin/env python3
"""No game test writes blocks outside the plot it was given.

A game test runs inside a structure template, and the server packs those templates into one
world side by side with only a small margin. Blocks written past the template's own size land
in the next test's plot - so two tests that never mention each other start deciding each
other's results, and which ones depends on how many tests exist and which way each was
rotated. The symptom is a test that fails two runs in three, at a different place every time,
with nothing in its own code to explain it.

That is what "empty" (three blocks across) plus a row of four tables (nine blocks across) was
doing: it had been quietly wrong since the cluster tests were written, and adding an unrelated
test elsewhere in the suite changed how often it showed.

So: every coordinate a test hands to place() or absolutePos() has to fit inside the template
that test declares, with room for the two blocks a table occupies.
"""
import gzip
import pathlib
import re
import struct
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
TESTS = ROOT / "neoforge/src/main/java/dev/gathering/neoforge/test"
TEMPLATES = ROOT / "common/src/main/resources/data/gathering/structure"

# A table is two blocks across, so a corner at x occupies x and x + 1.
BLOCKS_PER_TABLE = 2

TEMPLATE = re.compile(r'template\s*=\s*"([\w/]+)"')
COORDS = re.compile(
    r"(?:place|placeOf)\(helper,(?:\s*[\w.]+\(\),)?\s*(-?\d+),\s*(-?\d+),\s*(-?\d+)\)"
    r"|absolutePos\(new BlockPos\((-?\d+),\s*(-?\d+),\s*(-?\d+)\)\)")


def sizeOf(name):
    """The template's size, read straight out of the structure nbt."""
    path = TEMPLATES / f"{name}.nbt"
    if not path.exists():
        return None
    raw = path.read_bytes()
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    # The size is a three-int list; find the tag by name rather than walking the whole file.
    at = raw.find(b"\x09\x00\x04size")
    if at < 0:
        return None
    ints = at + len(b"\x09\x00\x04size") + 5
    return struct.unpack(">iii", raw[ints:ints + 12])


def main():
    problems = []
    checked = 0

    for path in sorted(TESTS.glob("*.java")):
        text = path.read_text(encoding="utf-8")
        templates = set(TEMPLATE.findall(text))
        if not templates:
            continue
        sizes = [sizeOf(name) for name in sorted(templates)]
        if any(size is None for size in sizes):
            missing = [n for n, s in zip(sorted(templates), sizes) if s is None]
            problems.append(f"{path.name} names a template that does not exist: {missing}")
            continue
        # The smallest of them, because any test in the file might be the one that reaches
        # furthest and this does not try to work out which method a coordinate sits in.
        room = [min(size[axis] for size in sizes) for axis in range(3)]

        for match in COORDS.finditer(text):
            found = [g for g in match.groups() if g is not None]
            spot = [int(value) for value in found]
            checked += 1
            for axis, label in enumerate("xyz"):
                # y is a height rather than a footprint, so only the flat axes take the table.
                width = BLOCKS_PER_TABLE if axis != 1 else 1
                if spot[axis] < 0 or spot[axis] + width > room[axis]:
                    problems.append(
                        f"{path.name} writes at {label}={spot[axis]} in a template "
                        f"{room[axis]} {label}-blocks across; that lands in another test's plot"
                    )
                    break

    if problems:
        for problem in sorted(set(problems)):
            print(problem)
        print(f"\n{len(set(problems))} placement(s) outside their own plot")
        return 1

    print(f"{checked} test placements checked, all inside their own template")
    return 0


if __name__ == "__main__":
    sys.exit(main())
