#!/usr/bin/env python3
"""Whether any artwork has changed since it was last signed off.

The textures, the sprite sheets, their .mcmeta files and the sounds are the one part of this
repository that is not mine to touch: they are drawn and recorded by hand, outside anything
here, and a build task that quietly regenerates one is a day of somebody's work overwritten by
a program that thought it was helping. So every one of them is hashed into docs/art-hashes.txt
and this compares the tree against that list.

It reports three things and fails on the first two:

  * changed  - a file whose bytes are not what the list says. Always wrong unless the artist
               changed it, in which case the list is what needs updating.
  * missing  - a file on the list that is no longer in the tree.
  * new      - a file in the tree that is not on the list. Not a failure: adding art is what
               the artist does. Named so the list can be brought up to date deliberately.

    python3 tools/artcheck.py             check the tree against the list
    python3 tools/artcheck.py --write     write the list from the tree, which is a decision

Build outputs and the scripted client's run directory are not artwork and are skipped.
"""
import hashlib
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LIST = os.path.join(ROOT, "docs", "art-hashes.txt")

#: What counts as artwork. Sounds are here for the same reason the textures are.
SUFFIXES = (".png", ".mcmeta", ".ogg", ".wav")

#: Directories that hold copies rather than originals.
SKIP = ("/build/", "/.git/", "/neoforge/run/", "/fabric/run/", "/.gradle/")


def art():
    found = {}
    for where, folders, files in os.walk(ROOT):
        folders[:] = [f for f in folders if f not in (".git", "build", ".gradle", "run")]
        for name in files:
            if not name.endswith(SUFFIXES):
                continue
            full = os.path.join(where, name)
            rel = os.path.relpath(full, ROOT).replace(os.sep, "/")
            if any(part in "/" + rel for part in SKIP):
                continue
            with open(full, "rb") as handle:
                found[rel] = hashlib.sha256(handle.read()).hexdigest()
    return found


def read_list():
    signed = {}
    if not os.path.isfile(LIST):
        return signed
    with open(LIST, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            digest, _, path = line.partition("  ")
            signed[path] = digest
    return signed


def write_list(found):
    os.makedirs(os.path.dirname(LIST), exist_ok=True)
    with open(LIST, "w", encoding="utf-8") as handle:
        handle.write("# sha256 of every texture, sprite sheet, .mcmeta and sound in this\n")
        handle.write("# repository. Written by tools/artcheck.py --write, which is a decision\n")
        handle.write("# somebody makes on purpose after the artist has changed something.\n")
        for path in sorted(found):
            handle.write(f"{found[path]}  {path}\n")


def main():
    found = art()
    if "--write" in sys.argv:
        write_list(found)
        print(f"{len(found)} pieces of art signed into docs/art-hashes.txt")
        return 0
    signed = read_list()
    if not signed:
        print("no docs/art-hashes.txt; run tools/artcheck.py --write once to sign the art")
        return 1
    changed = sorted(p for p in found if p in signed and found[p] != signed[p])
    missing = sorted(p for p in signed if p not in found)
    new = sorted(p for p in found if p not in signed)
    for path in changed:
        print(f"changed: {path}")
    for path in missing:
        print(f"missing: {path}")
    for path in new:
        print(f"new (not a failure, but sign it in deliberately): {path}")
    print()
    print(f"{len(found)} pieces of art checked, {len(changed)} changed, {len(missing)} missing,"
          f" {len(new)} new")
    return 1 if changed or missing else 0


if __name__ == "__main__":
    sys.exit(main())
