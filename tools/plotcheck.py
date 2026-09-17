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
that test declares, with room for the three blocks a table occupies.
"""
import gzip
import pathlib
import re
import struct
import sys

#: Where a check never looks: a helper's copy of the repository.
#: Work is sometimes done in a git worktree under .claude/worktrees, which is a second checkout of this
#: same project sitting inside it. Walking the tree found both copies, so a file being written in one of
#: them failed the checks of the other - a lang key from a worktree, missing from the real lang file.
NOT_THIS_REPOSITORY = ("/.claude/", "\\.claude\\")


def ours(path):
    """Whether a path is this checkout's own, rather than a worktree's inside it."""
    said = str(path).replace("\\", "/")
    return "/.claude/" not in said


ROOT = pathlib.Path(__file__).resolve().parent.parent

#: Where the NeoForge in-world tests live. It moved once, from src/main to src/gametest, and this
#: went on reading the old directory - found nothing, checked nothing, and said all was well. So a
#: missing root, or a run that checks nothing, is a failure now.
TESTS = ROOT / "neoforge/src/gametest/java"
TEMPLATES = ROOT / "common/src/main/resources/data/gathering/structure"

# A table is three blocks across, so a corner at x occupies x to x + 2.
BLOCKS_PER_TABLE = 3

ANNOTATION = re.compile(r"@GameTest\(([^)]*)\)")
TEMPLATE = re.compile(r'template\s*=\s*"([\w/]+)"')
METHOD = re.compile(r"^\s*(?:public|private|protected|static|final|\s)*[\w<>\[\],.? ]+\s+(\w+)\s*\([^;]*$")
#: A whole table put down: the shared helpers and the per-file ones that wrap them.
TABLE = re.compile(r"\b(?:place|placeOf)\(\s*\w+\s*,(?:\s*[\w.]+\(\)\s*,)?\s*(-?\d+)\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*\)")
#: One block written or looked up at a literal position.
BLOCK = re.compile(r"absolutePos\(new BlockPos\(\s*(-?\d+)\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*\)\)")


def sizeOf(name):
    """The template's size, read straight out of the structure nbt."""
    path = TEMPLATES / f"{name}.nbt"
    if not path.exists():
        return None
    raw = path.read_bytes()
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    at = raw.find(b"\x09\x00\x04size")
    if at < 0:
        return None
    ints = at + len(b"\x09\x00\x04size") + 5
    return struct.unpack(">iii", raw[ints:ints + 12])


def main():
    if not TESTS.is_dir():
        print(f"plotcheck: {TESTS.relative_to(ROOT)} does not exist, so nothing could be checked")
        return 1
    problems = []
    checked = 0

    for path in sorted(TESTS.rglob("*.java")):
        if not ours(path):
            continue
        pending = None   # the template of an annotation waiting for its method
        current = None   # the template of the method whose body this line is in; None in a helper
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            found = ANNOTATION.search(line)
            if found:
                named = TEMPLATE.search(found.group(1))
                pending = named.group(1) if named else "empty"
                continue
            if METHOD.match(line) and not line.strip().startswith(("return", "if", "for", "while", "new ")):
                # Each method starts afresh: a test takes the template its annotation named, and a helper
                # between tests is not charged to the test before it.
                current, pending = pending, None
            if current is None:
                continue
            size = sizeOf(current)
            if size is None:
                problems.append(f"{path.name}:{number} names a template that does not exist: {current}")
                continue
            for pattern, footprint in ((TABLE, BLOCKS_PER_TABLE), (BLOCK, 1)):
                for match in pattern.finditer(line):
                    spot = [int(value) for value in match.groups()]
                    checked += 1
                    for axis, label in enumerate("xyz"):
                        width = footprint if axis != 1 else 1
                        if spot[axis] < 0 or spot[axis] + width > size[axis]:
                            problems.append(
                                f"{path.relative_to(ROOT)}:{number} writes at {label}={spot[axis]} "
                                f"({'a table' if footprint > 1 else 'a block'}) in template '{current}', "
                                f"{size[axis]} {label}-blocks across; that lands in another test's plot")
                            break

    if checked == 0:
        print("plotcheck: no placements found in any test, which cannot be right")
        return 1
    if problems:
        for problem in sorted(set(problems)):
            print(problem)
        print(f"\n{len(set(problems))} placement(s) outside their own plot")
        return 1

    print(f"{checked} test placements checked, all inside their own template")
    return 0


if __name__ == "__main__":
    sys.exit(main())
