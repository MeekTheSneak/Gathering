#!/usr/bin/env python3
"""Every test class in :core actually ran.

The gate already refuses an in-world run that discovered nothing, on both loaders. `:core` had
no such check, and a Gradle test task with no tests to find exits 0: a class whose annotations
stopped matching, a nested class the engine cannot see, a source set that stopped being scanned,
all read as green. Fourteen jqwik properties sat in JUnit @Nested classes for months exactly like
that, and one of them had been wrong the whole time.

So each source file under core/src/test that declares a test has to have run, with at least one
test case, in Gradle's own results for it - the class, or one of its nested classes.

Run it from the repo root after :core:test:  python3 tools/coretestcheck.py
"""
import pathlib
import re
import sys
import xml.etree.ElementTree as ElementTree

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCES = ROOT / "core/src/test/java"
RESULTS = ROOT / "core/build/test-results/test"

DECLARES = re.compile(r"^\s*@(?:org\.junit\.jupiter\.api\.)?(?:Test|ParameterizedTest|RepeatedTest)\b"
                      r"|^\s*@(?:net\.jqwik\.api\.)?(?:Property|Example)\b", re.MULTILINE)


def main():
    if not SOURCES.is_dir():
        print("core test sources are not where this looks, so nothing was checked")
        return 1
    if not RESULTS.is_dir():
        print(f"no results under {RESULTS.relative_to(ROOT)} - run :core:test first")
        return 1

    ran = {}
    for report in RESULTS.glob("TEST-*.xml"):
        try:
            suite = ElementTree.parse(report).getroot()
        except ElementTree.ParseError:
            continue
        # By the file, not the suite: a class with a @DisplayName reports that as its suite name.
        name = report.stem[len("TEST-"):]
        outer = name.split("$", 1)[0]
        ran[outer] = ran.get(outer, 0) + int(suite.get("tests", "0"))

    classes = 0
    missing = []
    for source in sorted(SOURCES.rglob("*.java")):
        if not DECLARES.search(source.read_text(encoding="utf-8")):
            continue
        classes += 1
        qualified = ".".join(source.relative_to(SOURCES).with_suffix("").parts)
        if ran.get(qualified, 0) <= 0:
            missing.append(qualified)

    if classes == 0:
        print("no test classes found under core/src/test, which cannot be right")
        return 1
    if missing:
        for name in missing:
            print(f"{name} declares tests and none of them ran")
        print(f"\n{len(missing)} of {classes} test classes ran nothing")
        return 1
    print(f"{classes} test classes, every one of them ran, {sum(ran.values())} tests in all")
    return 0


if __name__ == "__main__":
    sys.exit(main())
