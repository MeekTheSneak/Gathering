#!/usr/bin/env python3
"""Every payload that names a table says so in its type.

The guided first game is played on a board this client builds for itself, filed at a position
no table in the world can occupy. Nothing done to it may leave the machine - not as a move,
and not as one of the payloads a screen sends for the handful of things the server decides.

Game moves are safe by construction: they all go through `ClientTableActions.send`, which
routes on the position. Whole payloads are not. Seventeen files call `ClientNetworking.send`,
so a guard written at the call sites would be a guard that holds until somebody adds the
eighteenth - and the failure would be silent, because a payload addressed to a position below
the world is refused at the far end rather than answered. "Refused after arriving" is not the
promise; "never sent" is.

So the guard is written once, by type, in `ClientNetworking.send`: a payload that implements
`AtATable` can be asked where it is going. This checks the half a type cannot - that a payload
carrying a table position actually says `implements AtATable`, rather than quietly not being
covered by the one guard that exists.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
PAYLOADS = ROOT / "common/src/main/java/dev/gathering/network"

#: The record header, up to the implements clause. Payloads are records without exception.
HEADER = re.compile(r"public record (\w+)\(([^)]*)\)\s*implements\s+([\w.,\s]+?)\s*\{", re.S)

#: A component that is a table's position. Named rather than typed: a payload may carry some
#: other BlockPos - a block being pointed at - and that is not where the payload is going.
TABLE = re.compile(r"\bBlockPos\s+table\b")


def main() -> int:
    wrong = []
    checked = 0
    for source in sorted(PAYLOADS.glob("*Payload.java")):
        found = HEADER.search(source.read_text())
        if not found:
            continue
        name, components, implemented = found.groups()
        if not TABLE.search(components):
            continue
        checked += 1
        if "AtATable" not in implemented:
            wrong.append(f"{source.relative_to(ROOT)}: {name} carries a table position but "
                         f"implements {implemented.strip()}, so ClientNetworking.send cannot "
                         f"ask where it is going")

    # A check that finds nothing to check has not proved anything, and must not report that it
    # has. Twenty-five of these existed when the rule was written.
    if checked == 0:
        print("tablecheck: no table-addressed payloads found at all, which cannot be right")
        return 1

    if wrong:
        for line in wrong:
            print(line)
        print(f"tablecheck: {len(wrong)} of {checked} table-addressed payloads are outside "
              f"the one guard that keeps the guided first game off the wire")
        return 1

    print(f"tablecheck: {checked} table-addressed payloads, all of them AtATable")
    return 0


if __name__ == "__main__":
    sys.exit(main())
