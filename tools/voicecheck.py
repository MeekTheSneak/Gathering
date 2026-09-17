#!/usr/bin/env python3
"""Punctuation that reads as writing rather than as interface.

The owner read the mod's text through in one sitting (2026-09-16) and asked for the tells of
machine-written prose to go: em dashes, trailing ellipses, the semicolon that joins two thoughts
a player did not ask to have joined. Interface text states a fact or names an action. It does not
have a voice.

Those three are worth a check rather than a note in a document because they are objective - a
character is there or it is not - and because they come back. Every one of them arrives looking
reasonable in the line being written and reads as an essay once there are forty of them.

What this does NOT check is phrasing. "Not this, but that", the chatty second sentence that
restates the first, the explanation nobody asked for: those need a reader, and a checker that
guessed at them would be wrong often enough to be turned off. This holds the line on the part
that can be held mechanically.

    python3 tools/voicecheck.py

Passes silently. On a hit it prints the key, the text, and what to do instead.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LANG = ROOT / "common/src/main/resources/assets/gathering/lang/en_us.json"
GUIDE = ROOT / "common/src/main/resources/assets/gathering/guide/en_us"

#: What is not allowed in text a player reads, and what to write instead.
BANNED = [
    ("—", "an em dash", "a period, or a comma"),
    ("–", "an en dash", "a period, or a comma"),
    ("…", "an ellipsis", "nothing - a line that is working says so by changing"),
    ("...", "a trailing ellipsis", "nothing - a line that is working says so by changing"),
    (";", "a semicolon", "two sentences"),
]

#: Lines that are a table of examples rather than prose, where a semicolon is punctuation
#: inside the thing being shown rather than a join between two thoughts.
EXEMPT = re.compile(r"^screen\.gathering\.search\.help_")


def hits(where: str, text: str) -> list[str]:
    """Every banned mark in one piece of text, said in full."""
    found = []
    for mark, called, instead in BANNED:
        if mark in text:
            found.append(f"{where}\n    {text}\n    has {called}; write {instead}")
    return found


def main() -> int:
    problems = []

    entries = json.loads(LANG.read_text(encoding="utf-8"))
    for key, value in entries.items():
        if not isinstance(value, str) or EXEMPT.match(key):
            continue
        problems += hits(key, value)

    for page in sorted(GUIDE.glob("*.md")):
        for number, line in enumerate(page.read_text(encoding="utf-8").splitlines(), start=1):
            problems += hits(f"{page.name}:{number}", line)

    for problem in problems:
        print(problem)
    print(f"{len(entries)} lines of interface text and {len(list(GUIDE.glob('*.md')))} guide pages "
          f"read, {len(problems)} that read as writing rather than as interface")
    # a check that finds nothing to check must fail rather than pass - DIALECT.md
    if not entries or not list(GUIDE.glob("*.md")):
        print("voicecheck: no interface text or no guide pages found, so nothing was checked")
        return 1
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
