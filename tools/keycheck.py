#!/usr/bin/env python3
"""No screen hardcodes a key the player is allowed to rebind.

Every verb with a key has a real KeyMapping, so it sits in Minecraft's own Controls screen and
a player can move it. That promise is only kept if the code that reads a press asks where the
verb is *now*. A branch written as `key == GLFW_KEY_L` does not ask, and it does not merely
shadow the mapping - it overrides it in both directions: rebinding the log to Z left L still
opening it, unbinding the log left L still opening it, and anything else bound to L never saw
the press at all. The key list went on saying "L" underneath, so nothing looked wrong.

So: a key the catalogue binds may not also be compared as a literal in a screen's input
handling. Keys the catalogue does not bind - the camera's WASD, Delete, Escape, F1 - are fixed
by design and stay literal, which is honest, because nothing offers to move them.

Reads the defaults out of TableShortcuts rather than keeping a second list of them here, so a
verb given a key tomorrow is covered tomorrow without anybody remembering this file.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CATALOGUE = ROOT / "common/src/main/java/dev/gathering/client/TableShortcuts.java"
SCREENS = ROOT / "common/src/main/java/dev/gathering/client"
SCREEN = ROOT / "common/src/main/java/dev/gathering/client/TableScreen.java"
LANG = ROOT / "common/src/main/resources/assets/gathering/lang/en_us.json"

#: DEFAULTS.put("draw", GLFW.GLFW_KEY_2);
BOUND = re.compile(r'DEFAULTS\.put\("([a-z_]+)",\s*GLFW\.(GLFW_KEY_\w+)\)')

#: Any literal comparison or switch label naming a GLFW key constant.
LITERAL = re.compile(r'GLFW\.(GLFW_KEY_\w+)')

#: Where a press is turned into an action, by shape rather than by name. Matching on the
#: signature catches the private handler a screen delegates to as well as the public one it
#: overrides - the replay's keys live in one of those, and a list of names missed it.
HANDLER = re.compile(r"\bboolean\s+(\w+)\s*\(\s*int\s+key\s*,\s*int\s+scanCode")

#: A key that genuinely means something else in this handler, said out loud on the line above
#: it. Being mentioned is what this looks for - the same bargain tools/statecheck.py offers -
#: because there is one real case and refusing to have any is how a check gets switched off.
EXCUSED = "keycheck:"

#: Only screens that play the table. A catalogue key means nothing on a screen the table's
#: verbs are not live on: the colors wheel picks white with 1, and "untap everything" is not a
#: thing that can happen there, so its 1 is its own. What this rule is really about is a screen
#: that asks the catalogue in one place and not in another - so the screens it applies to are
#: exactly the ones that ask it at all.
ASKS_THE_CATALOGUE = "TableShortcuts"


def handlerBodies(source: str):
    """Every input handler's body, by brace matching from its signature."""
    for found in HANDLER.finditer(source):
        depth = 0
        start = source.find("{", found.end())
        if start < 0:
            continue
        at = start
        while at < len(source):
            if source[at] == "{":
                depth += 1
            elif source[at] == "}":
                depth -= 1
                if depth == 0:
                    break
            at += 1
        yield found.group(1), source[start:at]


def main() -> int:
    catalogue = dict((key, verb) for verb, key in BOUND.findall(CATALOGUE.read_text()))
    if not catalogue:
        print("keycheck: no key defaults found in TableShortcuts, which cannot be right")
        return 1

    wrong = []
    handlers = 0
    excused = 0
    for source in sorted(SCREENS.glob("*.java")):
        text = source.read_text()
        if ASKS_THE_CATALOGUE not in text:
            continue
        for handler, body in handlerBodies(text):
            handlers += 1
            for found in LITERAL.finditer(body):
                key = found.group(1)
                if key not in catalogue:
                    continue
                # The comment block immediately above the press, however long it is. A
                # reason worth writing is usually a sentence or two, and cutting the window
                # at a fixed number of lines would quietly stop honoring reasons that ran
                # over it - which is a check that looks like it passes for the wrong cause.
                above = body[:found.start()].split("\n")[:-1]
                block = []
                for line in reversed(above):
                    stripped = line.strip()
                    if stripped.startswith("//") or stripped.startswith("*"):
                        block.append(stripped)
                        continue
                    if not stripped:
                        continue
                    break
                if any(EXCUSED in line for line in block):
                    excused += 1
                    continue
                if True:
                    wrong.append(
                        f"{source.relative_to(ROOT)}: {handler} compares {key} as a literal, "
                        f"but the catalogue binds it to '{catalogue[key]}' and the player may "
                        f"move it. Ask TableShortcuts instead.")

    # The other half of the same promise. A line that names a verb has to have somewhere to
    # put its key: the entry says how many keys the line is about, and the written line has to
    # have that many slots. Too few and a key is silently dropped from the one panel that
    # exists to print it; too many and the line renders with a hole in it.
    listed = re.search(r"KEY_LIST_ACTIONS\s*=(.*?);\n", SCREEN.read_text(), re.S)
    if not listed:
        print("keycheck: could not find the key list's verb map")
        return 1
    entries = re.findall(r'entry\(\s*"([\w.]+)"\s*,\s*List\.of\(([^)]*)\)', listed.group(1))
    if not entries:
        print("keycheck: the key list names no verbs at all, which cannot be right")
        return 1
    written = json.loads(LANG.read_text())
    for line, verbs in entries:
        wanted = len([v for v in verbs.split(",") if v.strip()])
        text = written.get(line)
        if text is None:
            wrong.append(f"{line} is in the key list's verb map but not in en_us.json")
            continue
        slots = text.count("%s")
        if slots != wanted:
            wrong.append(
                f"{line} names {wanted} rebindable verb(s) but its line has {slots} slot(s) "
                f"for a key: \"{text}\"")

    if handlers == 0:
        print("keycheck: found no input handlers to check, which cannot be right")
        return 1
    if wrong:
        for line in wrong:
            print(line)
        print(f"keycheck: {len(wrong)} place(s) where a rebindable key is written down "
              f"rather than asked for")
        return 1

    print(f"keycheck: {handlers} input handlers, {len(catalogue)} rebindable keys, "
          f"none of them hardcoded, {excused} excused with a reason")
    return 0


if __name__ == "__main__":
    sys.exit(main())
