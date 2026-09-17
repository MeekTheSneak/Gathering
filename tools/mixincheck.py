#!/usr/bin/env python3
"""Every mixin the mod has written is one the loader is told to apply.

A mixin that is not named in its loader's `gathering.mixins.json` is dead code that compiles, ships,
and does nothing - and nothing anywhere says so. That is not hypothetical: the hook that lets a deck
be swept over cards was dropped from both loaders' lists in a merge, so the gesture did nothing at all
in a build that otherwise looked finished, and it took a scripted client run and an afternoon to find.

Both directions are checked:

* a mixin class in the loader's mixin package that no list names, and
* a name in a list with no class behind it, which is a mixin that was renamed or deleted and will stop
  the game loading rather than quietly do nothing.

Run from the repository root:

    python3 tools/mixincheck.py
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

#: Each loader, and where its mixins and their list live.
LOADERS = {
    "neoforge": ("neoforge/src/main/java/dev/gathering/neoforge/mixin",
                 "neoforge/src/main/resources/gathering.mixins.json"),
    "fabric": ("fabric/src/main/java/dev/gathering/fabric/mixin",
               "fabric/src/main/resources/gathering.mixins.json"),
}


def main():
    problems = []
    counted = 0
    for loader, (where, listed) in sorted(LOADERS.items()):
        folder = ROOT / where
        config = ROOT / listed
        if not folder.is_dir() or not config.is_file():
            problems.append(f"{loader}: no mixin folder or no {config.name} to read")
            continue
        written = {path.stem for path in sorted(folder.glob("*.java"))}
        said = json.loads(config.read_text(encoding="utf-8"))
        named = set(said.get("mixins", [])) | set(said.get("client", [])) | set(said.get("server", []))
        counted += len(written)
        for mixin in sorted(written - named):
            problems.append(f"{loader}: {mixin} is a mixin nothing applies; name it in {config.name}")
        for mixin in sorted(named - written):
            problems.append(f"{loader}: {config.name} names {mixin}, which is not there")
    for problem in problems:
        print("  " + problem)
    print(f"mixincheck: {counted} mixins across {len(LOADERS)} loaders, {len(problems)} problems")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
