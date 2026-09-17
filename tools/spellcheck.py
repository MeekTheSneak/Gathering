#!/usr/bin/env python3
"""American spelling, in the mod's own words.

CLAUDE.md asks for American spelling throughout - identifiers, prose, lang keys - and
nothing enforced it, so British forms drifted in: a public ``practising()``, "centred",
"grey", "labelled", "cancelled". None of them break anything, which is why they lasted.

What this reads, and what it deliberately does not:

* **Comments and javadoc** in every module's Java, and every value in ``en_us.json``.
* **Identifiers**, every one the code uses - fields, parameters, locals - read by a small
  Java tokenizer: a name is words run together, and the British forms below are found inside one.
* **Not string literals.** Some of them are phases written into a save or sent on the wire
  ("cancelled" is a tournament's), and renaming one is a migration rather than a spelling
  fix. Where a literal is player-facing it lives in the lang file, which is read.
* **Not another project's names.** ``BlockBehaviour`` is Minecraft's and the ``*Behaviour``
  classes are Create's; spelling them any other way does not compile. They are known by being
  imported, or written out with their package, rather than by a list.
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

#: A name the mod spells the British way on purpose, with the reason. Everything else of another
#: project's is found from the imports rather than listed, so a new Create behaviour needs nothing.
KEPT = {
    # A tournament phase, written by name into saves and sent to clients as a word; renaming it is
    # a migration of every stored event, not a spelling fix.
    "CANCELLED": "a stored tournament phase",
}

WORD = re.compile("|".join(sorted(INSTEAD, key=len, reverse=True)), re.IGNORECASE)

SOURCES = ["common/src", "core/src", "neoforge/src", "fabric/src"]

LANG = ROOT / "common/src/main/resources/assets/gathering/lang/en_us.json"

IDENTIFIER = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")

#: A qualified name from outside the mod: its package root, then dotted names to the class.
FOREIGN = re.compile(r"(?:com|net|org|io|java|javax)(?:\s*\.\s*[A-Za-z_][A-Za-z0-9_]*)+")


def pieces(text):
    """A Java file as (line, identifier) pairs from its code and (line, text) pairs from its comments.

    Read as Java is: string and character literals are skipped with their escapes, text blocks
    too, and a comment is a comment wherever on a line it starts. The first version read comments
    by the line they began on and names only where a modifier stood in front of them, so a
    parameter, a local, a package-private field, a comment after code and a line with an escaped
    quote before its comment all went unread.
    """
    code, comments = [], []
    at, line, size = 0, 1, len(text)
    while at < size:
        c = text[at]
        ahead = text[at + 1] if at + 1 < size else ""
        if c == "/" and ahead == "/":
            end = text.find("\n", at)
            end = size if end < 0 else end
            comments.append((line, text[at + 2:end]))
            at = end
            continue
        if c == "/" and ahead == "*":
            end = text.find("*/", at + 2)
            end = size if end < 0 else end
            body = text[at + 2:end]
            for offset, part in enumerate(body.split("\n")):
                comments.append((line + offset, part))
            line += body.count("\n")
            at = end + 2
            continue
        if text.startswith('"""', at):
            end = text.find('"""', at + 3)
            end = size if end < 0 else end
            line += text.count("\n", at, end)
            at = end + 3
            continue
        if c in "\"'":
            at += 1
            while at < size and text[at] != c and text[at] != "\n":
                at += 2 if text[at] == "\\" else 1
            at += 1
            continue
        if c == "\n":
            line += 1
            at += 1
            continue
        found = FOREIGN.match(text, at)
        if found and (at == 0 or not (text[at - 1].isalnum() or text[at - 1] in "_.")):
            # Another project's class written out in full rather than imported.
            at = found.end()
            continue
        found = IDENTIFIER.match(text, at)
        if found and (at == 0 or not (text[at - 1].isalnum() or text[at - 1] == "_")):
            code.append((line, found.group(0)))
            at = found.end()
            continue
        at += 1
    return code, comments


def imported(text):
    """Every name an import line mentions: another project's, spelled its own way."""
    names = set()
    for statement in re.findall(r"^\s*import\s+(?:static\s+)?([\w.]+)\s*;", text, re.MULTILINE):
        names.update(statement.split("."))
    return names


def wrong(text, theirs=frozenset()):
    """Every British form in one piece of text, with what to write instead."""
    problems = []
    for word in IDENTIFIER.findall(text):
        if word in theirs or word in KEPT:
            continue
        for match in WORD.finditer(word):
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
        theirs = frozenset(imported(text))
        code, comments = pieces(text)
        for number, said in comments:
            for found, instead in wrong(said, theirs):
                problems.append(f"{where}:{number}: \"{found}\" - write \"{instead}\"")
        seen = set()
        for number, name in code:
            if name in seen or name in theirs or name in KEPT:
                continue
            for found, instead in wrong(name, theirs):
                seen.add(name)
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
