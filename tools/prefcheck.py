#!/usr/bin/env python3
"""A preference counts as active only when production code actually reads it.

A settings screen is a promise. Every row on it says "this changes something", and a row that
changes nothing is worse than a missing feature: the player picks it, the file records it, the
value survives a restart, and the game goes on behaving exactly as it did. Nothing looks
broken, so nothing gets reported, and the setting can sit there for years.

This repository had eight of them at once - the text and control scales, reduced motion, the
sound toggle and its volume, hold-to-inspect, effect intensity and the turn notification. All
eight persisted, clamped, round-tripped through the file and had tests proving they did. None
of them was read by a single line of production code. The tests passed because they tested the
setting, not the behavior, which is the trap this check exists to close.

So: every getter on ClientSettings has to be called from somewhere that is not ClientSettings
itself and not a test. Tests are excluded deliberately - a preference read only by its own test
is the exact shape of the defect.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SETTINGS = ROOT / "common/src/main/java/dev/gathering/client/ClientSettings.java"

#: Where production lives. The loaders' client packages count; their test packages do not.
PRODUCTION = [
    "common/src/main/java",
    "core/src/main/java",
    "neoforge/src/main/java/dev/gathering/neoforge/client",
    "fabric/src/main/java/dev/gathering/fabric/client",
]

#: public static <something that is not void> name()
GETTER = re.compile(r"public static (?!void\b)[\w<>\[\]]+ (\w+)\(\)\s*\{")

#: A getter that is deliberately not read by the game, with the reason written beside it.
#: Being mentioned is what this looks for, the same bargain statecheck and keycheck offer.
EXCUSED = "prefcheck:"


def main() -> int:
    source = SETTINGS.read_text()
    getters = sorted(set(GETTER.findall(source)))
    if not getters:
        print("prefcheck: no preferences found at all, which cannot be right")
        return 1

    # The reason, if one is written in the ten lines above the getter.
    excused = set()
    lines = source.splitlines()
    for at, line in enumerate(lines):
        found = GETTER.search(line)
        if found and any(EXCUSED in earlier for earlier in lines[max(0, at - 10):at]):
            excused.add(found.group(1))

    files = []
    for root in PRODUCTION:
        base = ROOT / root
        if base.is_dir():
            files.extend(base.rglob("*.java"))

    read = set()
    for source_file in files:
        if source_file.name == "ClientSettings.java" or "/test/" in source_file.as_posix():
            continue
        text = source_file.read_text()
        for name in getters:
            if f"ClientSettings.{name}()" in text:
                read.add(name)

    dead = [g for g in getters if g not in read and g not in excused]
    if dead:
        for name in dead:
            print(f"ClientSettings.{name}() is offered to the player and read by no production "
                  f"code, so choosing it changes nothing")
        print(f"prefcheck: {len(dead)} of {len(getters)} preferences do nothing")
        return 1

    print(f"prefcheck: {len(getters)} preferences, all read by the game"
          + (f", {len(excused)} excused with a reason" if excused else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
