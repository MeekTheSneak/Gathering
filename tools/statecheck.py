#!/usr/bin/env python3
"""Every in-memory holder is emptied when the thing that filled it goes away.

The mod keeps a fair amount of state in static holders: what a server told this client, what a
server is holding for the world it is running. Both have to be dropped at the right moment -
the client's when it disconnects, the server's when it stops - or state from one world turns up
in the next. In single-player that is the same JVM, so "the next world" is one main-menu click
away.

The lists used to live in the loaders, once each, which is two copies of one rule. They drifted
exactly as you would expect: three client holders were added over time and cleared by neither
loader, so a client that left one server and joined another carried the last table's pings, its
card flights and its unread log with it.

So the lists live in ClientState and ServerState, and this checks two things:

  * every holder with a `public static void clear()` is named in the aggregator for its side
  * every loader calls both aggregators, so neither side is left holding on quietly

A holder that genuinely should survive can be named in a comment in its aggregator - being
mentioned is what this looks for, not being called - but say why.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

SIDES = {
    "client": {
        "holders": ["common/src/main/java/dev/gathering/client"],
        "aggregator": "common/src/main/java/dev/gathering/client/ClientState.java",
    },
    "server": {
        "holders": [
            "common/src/main/java/dev/gathering/server",
            "common/src/main/java/dev/gathering/service",
        ],
        "aggregator": "common/src/main/java/dev/gathering/server/ServerState.java",
    },
}

LOADERS = ["neoforge/src/main/java", "fabric/src/main/java"]

CLEARS = re.compile(r"\bpublic static void clear\(\)")


def holders(side):
    """Classes on one side that offer a static clear()."""
    found = set()
    for folder in SIDES[side]["holders"]:
        for path in sorted((ROOT / folder).glob("*.java")):
            if CLEARS.search(path.read_text(encoding="utf-8")):
                found.add(path.stem)
    return found


def main():
    problems = []
    aggregators = {}

    for side in SIDES:
        aggregator = ROOT / SIDES[side]["aggregator"]
        if not aggregator.exists():
            problems.append(f"{SIDES[side]['aggregator']} is missing")
            continue
        text = aggregator.read_text(encoding="utf-8")
        aggregators[side] = aggregator.stem
        for holder in sorted(holders(side)):
            if holder == aggregator.stem:
                continue
            if not re.search(rf"\b{holder}\b", text):
                problems.append(
                    f"{holder} can be cleared and {aggregator.stem} never clears it; "
                    f"state from one {side} would outlive it"
                )

    # And every loader calls both of them. A loader that stops is the other way this goes
    # wrong: the list stays correct and nothing reads it.
    for folder in LOADERS:
        called = set()
        for path in (ROOT / folder).rglob("*.java"):
            text = path.read_text(encoding="utf-8")
            for side, call in (("client", "ClientState.forgetTheServer()"),
                               ("server", "ServerState.forgetTheWorld()")):
                if call in text:
                    called.add(side)
        for side, call in (("client", "ClientState.forgetTheServer()"),
                           ("server", "ServerState.forgetTheWorld()")):
            if side not in called:
                problems.append(
                    f"{folder} never calls {call}, so nothing drops its {side} state"
                )

    if problems:
        for problem in problems:
            print(problem)
        print(f"\n{len(problems)} holder(s) out of step")
        return 1

    counted = sum(len(holders(side)) for side in SIDES)
    print(f"{counted} clearable holders checked, all named in one teardown list")
    return 0


if __name__ == "__main__":
    sys.exit(main())
