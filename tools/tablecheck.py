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

#: Any position a payload carries. Every one of these is asked about, because naming the
#: component `table` was the whole of the old test and a payload is free to call it anything:
#: `DraftPickPayload` said `pod` and `EventActionPayload` said `at`, both were table positions,
#: and neither was ever looked at by the check whose job is to look at them.
#: Wrapped as well as bare: an Optional<BlockPos> or a List<BlockPos> names a table as surely as a
#: BlockPos does, and the pattern that only matched the bare type let either through unasked.
POSITION = re.compile(r"\bBlockPos\s*(?:>\s*)?(\w+)\b")

#: Positions that are not a table's, each with the reason it is not. A payload naming one of
#: these is passed over; anything else has to implement AtATable or say why here, which makes
#: adding a position to a payload a decision somebody writes down rather than one nobody sees.
NOT_A_TABLE = {
    "collection": "a collection block, which is not a table and has no session",
    "where": "a collection block, which is not a table and has no session",
    "desk": "a scorekeeper's desk, which stands beside tables rather than being one",
    "block": "whichever block was clicked, before anything has decided what it is",
    "from": "the collection block a deck is built out of, which is not a table and has no session",
}


def serverbound() -> set:
    """The payloads a client can send, which are the only ones the guard applies to.

    A clientbound payload is written by the server and never goes through ClientNetworking.send,
    so asking it to implement AtATable would be asking it to carry an answer nobody reads.
    """
    registry = (ROOT / "common/src/main/java/dev/gathering/network/GatheringProtocol.java")
    return set(re.findall(r"toServer\((\w+)\.TYPE", registry.read_text()))


def main() -> int:
    wrong = []
    checked = 0
    fromClients = serverbound()
    for source in sorted(PAYLOADS.glob("*Payload.java")):
        found = HEADER.search(source.read_text())
        if not found:
            continue
        name, components, implemented = found.groups()
        if name not in fromClients:
            continue
        carried = [held for held in POSITION.findall(components) if held not in NOT_A_TABLE]
        if not carried:
            continue
        checked += 1
        if "AtATable" not in implemented:
            wrong.append(f"{source.relative_to(ROOT)}: {name} carries a position "
                         f"({', '.join(carried)}) but implements {implemented.strip()}, so "
                         f"ClientNetworking.send cannot ask where it is going. Implement AtATable, "
                         f"or name the component in tablecheck's NOT_A_TABLE with the reason.")

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
