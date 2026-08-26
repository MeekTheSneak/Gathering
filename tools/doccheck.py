#!/usr/bin/env python3
"""No javadoc block may sit directly above another javadoc block.

When that happens the first one is orphaned: the member it was written for has been renamed,
moved, or replaced, and the paragraph explaining it is now attached to whatever happened to
end up underneath. Nothing catches this. It compiles, the tests pass, and the only symptom is
that the next person to read the file is told, in a confident voice, something about the wrong
method.

In a codebase where the comments carry the reasoning - why the count shrinks instead of
vanishing, why the seed is never logged - that is not a cosmetic problem. A sweep found forty
of them, including one made an hour earlier by inserting a method above the field whose
paragraph it then stole.

Plain block comments are left alone. A `/* ... */` note about what is deliberately not done is
a note, not documentation for the thing after it, and several of those exist on purpose.
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCES = [
    "common/src/main/java",
    "core/src/main/java",
    "core/src/test/java",
    "neoforge/src/main/java",
    "fabric/src/main/java",
]


def blocks(lines):
    """Every comment block in the file, as (start, end, isJavadoc), ends inclusive."""
    found = []
    index = 0
    while index < len(lines):
        stripped = lines[index].strip()
        if stripped.startswith("/*"):
            end = index
            # The opener's own text can close it, so look past the "/*" on the first line.
            while end < len(lines):
                tail = lines[end][lines[end].find("/*") + 2:] if end == index else lines[end]
                if "*/" in tail:
                    break
                end += 1
            found.append((index, min(end, len(lines) - 1), stripped.startswith("/**")))
            index = end + 1
        else:
            index += 1
    return found


def orphansIn(path):
    lines = path.read_text().splitlines()
    out = []
    for start, end, isJavadoc in blocks(lines):
        if not isJavadoc:
            continue
        after = end + 1
        while after < len(lines) and not lines[after].strip():
            after += 1
        if after < len(lines) and lines[after].strip().startswith("/**"):
            first = " ".join(
                line.strip().lstrip("*").strip() for line in lines[start:end + 1]
            )[3:].strip()
            out.append((start + 1, first[:90]))
    return out


def main() -> int:
    orphans = []
    checked = 0
    for source in SOURCES:
        for path in sorted((ROOT / source).rglob("*.java")):
            checked += 1
            for line, text in orphansIn(path):
                orphans.append(f"{path.relative_to(ROOT)}:{line}  {text}")

    for orphan in orphans:
        print(f"orphaned javadoc: {orphan}")
    print(f"\n{checked} files checked, {len(orphans)} orphaned javadoc blocks")
    return 1 if orphans else 0


if __name__ == "__main__":
    sys.exit(main())
