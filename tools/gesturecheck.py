#!/usr/bin/env python3
"""One gesture per verb.

The table has three context menus, four buttons on every mat and a row of keys, and the same
verb kept appearing on several of them. That is not a free convenience: the cost of a second
way to do a thing is that the first way stops being the answer to "how do I do this", and
every screenshot, every explanation and every argument at the table then has to say which one
was meant. Draw, shuffle and untap-all were each reachable four ways.

What counts as one gesture, and what does not:

- A key is not a second way. Every menu row prints its own key beside it, so the row and the
  key are one affordance with a label on it - which is exactly how somebody learns the key.
- Nor is a button on your own mat, for the same reason and no other: the four on a mat are
  the four things done every turn, and each one is a shortcut to a row that still exists in
  the place that verb lives. If the mat ever grows a button for something with no home on a
  menu, that is the thing to fix, not this rule.
- The same verb on two different context menus is a second way, and the worst kind. The two
  lists are opened by right-clicking two different things, so neither of them is where the
  verb lives, and "where do I draw a card" stops having an answer.

So: no verb may sit on more than one menu. A pile's own menu owns the verbs that act on that
pile; the table menu owns what belongs to you rather than to any object; a card's menu owns
what is done to a card. Draw, shuffle and untap-all were on the table menu as well as their
own homes, and are not any more.

Run it from the repo root:  python3 tools/gesturecheck.py
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCREEN = os.path.join(
    ROOT, "common/src/main/java/dev/gathering/client/TableScreen.java")
VERBS = os.path.join(ROOT, "core/src/main/java/dev/gathering/core/ui/TableVerb.java")

#: Where each menu is built, in the order they appear, so one ends where the next begins.
MENUS = [
    ("the card menu", "private void openCardMenu"),
    ("the pile menu", "private void openPileMenu"),
    ("the table menu", "private void openTableMenu"),
]

def bodies(source):
    """Each menu builder's own source, from its signature to its closing brace.

    Counted braces rather than cut at the next method: the last of the three is followed by
    the number row and the key handler, which are full of the same verb names, and slicing to
    the end of the file reported every key as a duplicate menu row.
    """
    out = {}
    for name, start in MENUS:
        at = source.find(start)
        if at < 0:
            raise SystemExit(start + " is not in TableScreen any more")
        opened = source.index("{", at)
        depth, end = 0, None
        for index in range(opened, len(source)):
            if source[index] == "{":
                depth += 1
            elif source[index] == "}":
                depth -= 1
                if depth == 0:
                    end = index
                    break
        if end is None:
            raise SystemExit(name + " has no closing brace")
        out[name] = source[at:end]
    return out


#: Where an entry's first argument ends. Everything before one of these is the key, and a row
#: whose key is chosen by a condition has two of them in there.
ACTION = re.compile(r",\s*(?:\(\)\s*->|this::|\(\)\s*$)")


def rowsIn(text):
    """Every verb the menu offers, including the ones whose key depends on the state.

    Read as "the first argument of every entry() call" rather than as a fixed shape, because
    several rows choose their key from a condition - tap or untap, freeze or thaw, reveal or
    stop revealing - and a pattern that only understood a plain literal would quietly check
    nothing about the half-dozen rows most likely to be duplicated.
    """
    found = set()
    for match in re.finditer(r"entry\(", text):
        rest = text[match.end():match.end() + 200]
        end = ACTION.search(rest)
        found.update(re.findall(r'"([a-z0-9_]+)"', rest[:end.start()] if end else rest))
    return found


def matButtons():
    """The mat's buttons, only to report how many there are - see the note at the top."""
    body = open(VERBS, encoding="utf-8").read()
    body = body.split("public enum TableVerb {", 1)[1].split("private final String key", 1)[0]
    return {name.lower() for name in re.findall(r"^\s*([A-Z_]+)\s*[,;]$", body, re.M)}


def main():
    source = open(SCREEN, encoding="utf-8").read()
    menus = {name: rowsIn(text) for name, text in bodies(source).items()}
    mat = matButtons()
    problems = []

    names = list(menus)
    for index, one in enumerate(names):
        for two in names[index + 1:]:
            for verb in sorted(menus[one] & menus[two]):
                problems.append(
                    f"{verb} is on both {one} and {two}; a verb belongs to one of them")

    for problem in problems:
        print(f"error: {problem}", file=sys.stderr)
    if problems:
        return 1
    total = sum(len(rows) for rows in menus.values())
    print(f"{total} menu rows across {len(menus)} menus and {len(mat)} mat buttons,"
          " one gesture each")
    return 0


if __name__ == "__main__":
    sys.exit(main())
