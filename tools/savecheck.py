#!/usr/bin/env python3
"""What belongs to a save is written inside that save.

Everything this mod wrote went under the game directory. That is right for one kind of thing
and wrong for every other kind. Downloaded card metadata and collation data are the same in
every world, and caching them once across all of them is the whole point. Anything anybody
owns is not: two single-player worlds in one installation share a game directory, so they
shared one file of owed rewards - a booster interrupted in world A could be claimed on joining
world B, and was then gone from the world that owed it. Replays, want lists and set-aside
broken games had the same shape of problem, quieter but the same.

So: `Platform.dataDirectory()` is for the caches and nothing else, and everything per-world
goes through `ServerRun.inSave(...)`, which resolves under the running server's own save and
refuses rather than falling back when no server is running. Falling back is the bug.

This check enforces the split by naming the files allowed to ask the platform where the game
directory is. A new holder of per-player or per-world state that reaches for dataDirectory
fails here rather than shipping and quietly mixing two worlds together.

Run it from the repo root:  python3 tools/savecheck.py
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

WHERE = [
    "common/src/main/java",
    "neoforge/src/main/java",
    "fabric/src/main/java",
]

#: The caches, which are global on purpose, plus the interface and the loaders that answer it.
MAY_ASK_FOR_THE_GAME_DIRECTORY = {
    "CardDataService",   # downloaded Scryfall printings: identical in every world
    "CollationService",  # published set collation: identical in every world
    "Platform",          # declares the method
    "NeoForgePlatform",  # answers it
    "FabricPlatform",    # answers it
    "ServerSettings",    # reads the config directory, not the data one
}

ASKS = re.compile(r"\bdataDirectory\s*\(\s*\)")


def main():
    problems = []
    for folder in WHERE:
        for path in sorted((ROOT / folder).rglob("*.java")):
            if path.stem in MAY_ASK_FOR_THE_GAME_DIRECTORY:
                continue
            text = path.read_text(encoding="utf-8")
            for number, line in enumerate(text.splitlines(), start=1):
                if ASKS.search(line) and not line.lstrip().startswith(("*", "//")):
                    problems.append(
                        f"{path.relative_to(ROOT)}:{number} asks for the game directory. "
                        f"Anything belonging to one world goes through ServerRun.inSave(...); "
                        f"only the caches are shared between worlds."
                    )

    if problems:
        for problem in problems:
            print(problem)
        print(f"\n{len(problems)} place(s) writing a world's data outside that world")
        return 1

    inSave = sum(
        len(re.findall(r"ServerRun\.inSave\(", path.read_text(encoding="utf-8")))
        for folder in WHERE
        for path in (ROOT / folder).rglob("*.java")
    )
    print(f"{inSave} per-world folder(s) resolved inside the save, "
          f"{len(MAY_ASK_FOR_THE_GAME_DIRECTORY)} files allowed the shared one")
    return 0


if __name__ == "__main__":
    sys.exit(main())
