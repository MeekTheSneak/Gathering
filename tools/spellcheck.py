#!/usr/bin/env python3
"""American spelling, in the mod's own words.

CLAUDE.md asks for American spelling throughout - identifiers, prose, lang keys - and
nothing enforced it, so British forms drifted in: a public ``practising()``, "centred",
"grey", "labelled", "cancelled". None of them break anything, which is why they lasted.

What this reads, and what it deliberately does not:

* **Comments and javadoc** in every module's Java, and every value in ``en_us.json``.
* **Identifiers**, by way of the same scan: a name is words run together, and the British
  forms below are recognised inside one.
* **Not string literals.** Some of them are phases written into a save or sent on the wire
  ("cancelled" is a tournament's), and renaming one is a migration rather than a spelling
  fix. Where a literal is player-facing it lives in the lang file, which is read.
* **Not another project's names.** ``BlockBehaviour`` is Minecraft's and the ``*Behaviour``
  classes are Create's; spelling them any other way does not compile.
"""

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

# British form -> the American one to write instead.
INSTEAD = {
    "behaviour": "behavior",
    "cancelled": "canceled",
    "cancelling": "canceling",
    "centred": "centered",
    "centre": "center",
    "colour": "color",
    "defence": "defense",
    "favour": "favor",
    "grey": "gray",
    "greyed": "grayed",
    "labelled": "labeled",
    "licence": "license",
    "practise": "practice",
    "practising": "practicing",
    "practised": "practiced",
    "recognise": "recognize",
    "recognised": "recognized",
    "travelling": "traveling",
    "organise": "organize",
    "organised": "organized",
    "analyse": "analyze",
    "apologise": "apologize",
    "modelling": "modeling",
    "signalling": "signaling",
}

# Names that belong to Minecraft or to another mod, matched whole and case-sensitively.
NOT_OURS = re.compile(
    r"BlockBehaviour|BlockEntityBehaviour|DepotBehaviour|TransportedItemStackHandlerBehaviour"
    r"|[A-Za-z]*Behaviour\b|CANCELLED|Cancelable|setCanceled")

WORD = re.compile("|".join(sorted(INSTEAD, key=len, reverse=True)), re.IGNORECASE)

SOURCES = ["common/src", "core/src", "neoforge/src", "fabric/src"]

LANG = ROOT / "common/src/main/resources/assets/gathering/lang/en_us.json"


def commentary(text):
    """Every comment and javadoc line, as (line number, text) - string literals removed."""
    found = []
    inside = False
    for number, line in enumerate(text.splitlines(), start=1):
        stripped = line.strip()
        if inside:
            found.append((number, line))
            if "*/" in stripped:
                inside = False
            continue
        if stripped.startswith("/*"):
            found.append((number, line))
            if "*/" not in stripped:
                inside = True
            continue
        at = line.find("//")
        if at >= 0 and line.count('"', 0, at) % 2 == 0:
            found.append((number, line[at:]))
    return found


def declarations(text):
    """Identifiers the mod declares, as (line number, name)."""
    found = []
    pattern = re.compile(
        r"\b(?:class|interface|enum|record)\s+(\w+)"
        r"|\b(?:public|private|protected|static|final)\s+[\w.<>\[\], ?]+\s+(\w+)\s*[(;=]")
    for number, line in enumerate(text.splitlines(), start=1):
        for match in pattern.finditer(line):
            name = match.group(1) or match.group(2)
            if name:
                found.append((number, name))
    return found


def wrong(text):
    """Every British form in one piece of text, with what to write instead."""
    problems = []
    for match in WORD.finditer(text):
        at = match.start()
        window = text[max(0, at - 40):at + 40]
        if NOT_OURS.search(window):
            continue
        problems.append((match.group(0), INSTEAD[match.group(0).lower()]))
    return problems


def main():
    problems = []

    files = []
    for source in SOURCES:
        files.extend(sorted((ROOT / source).rglob("*.java")))
    if not files:
        print("spellcheck: no Java to read, which cannot be right")
        return 1

    for path in files:
        text = path.read_text(encoding="utf-8")
        where = path.relative_to(ROOT)
        for number, line in commentary(text):
            for found, instead in wrong(line):
                problems.append(f"{where}:{number}: \"{found}\" - write \"{instead}\"")
        for number, name in declarations(text):
            for found, instead in wrong(name):
                problems.append(f"{where}:{number}: the name {name} spells \"{found}\";"
                                f" write \"{instead}\"")

    if not LANG.is_file():
        print("spellcheck: no en_us.json to read")
        return 1
    lang = json.loads(LANG.read_text(encoding="utf-8"))
    if not lang:
        print("spellcheck: en_us.json is empty, which cannot be right")
        return 1
    for key, value in lang.items():
        if not isinstance(value, str):
            continue
        for found, instead in wrong(value):
            problems.append(f"en_us.json: {key} says \"{found}\" - write \"{instead}\"")

    if problems:
        print(f"spellcheck: {len(problems)} British spelling(s), against CLAUDE.md")
        for problem in problems[:60]:
            print("  " + problem)
        if len(problems) > 60:
            print(f"  ... and {len(problems) - 60} more")
        return 1

    print(f"spellcheck: {len(files)} files and {len(lang)} lang values, all American")
    return 0


if __name__ == "__main__":
    sys.exit(main())
