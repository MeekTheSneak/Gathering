#!/usr/bin/env python3
"""Every translation key the mod asks for must exist in the language file.

A key with no entry does not fail, crash, or warn: Minecraft draws the key itself, so
"screen.gathering.table.key_flip" appears on screen where a sentence should be. That is
invisible to the compiler, invisible to the game tests, and visible only to whoever opens the
screen - which is why it is worth a script.

Keys are asked for in two shapes. Most are written out whole. Some are built by sticking a
name on the end of a prefix - "menu.gathering.table." + key, or "zone.gathering." + the zone's
name - and those cannot be resolved statically, so a literal that ends in a dot or an
underscore is treated as a prefix: it is never reported missing, and it marks every entry
underneath it as used.

Missing keys fail the check. Unused entries are usually just stale, so they are listed and do
not fail.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LANG = ROOT / "common/src/main/resources/assets/gathering/lang/en_us.json"
SOUNDS = ROOT / "common/src/main/resources/assets/gathering/sounds.json"

# Entries nothing in the source asks for by name, on purpose. Listing them here rather than
# letting them show up as unused every run is the difference between a check people read and a
# check people learn to skip.
EXPECTED_UNREFERENCED = {
    # Minecraft derives these from the registry id and the keybind category itself.
    "block.gathering.table",
    "item.gathering.card",
    "item.gathering.deck",
    "item.gathering.pack",
    "key.categories.gathering",
}
# Turns pass whole rather than a step at a time, so nothing shows a phase now - but PhaseSet is
# still a real event, still folded and still in saved sessions, so the names stay readable.
EXPECTED_UNREFERENCED.update(
    f"phase.gathering.{name}" for name in (
        "untap", "upkeep", "draw", "precombat_main", "begin_combat", "declare_attackers",
        "declare_blockers", "combat_damage", "end_combat", "postcombat_main", "end_step",
        "cleanup",
    )
)

# The suffix GameLogText looks for when a line is about the actor's own board.
OWN = ".own"

# A translation key as it appears in source: dotted, lower case, and about this mod.
KEY = re.compile(r'"((?:[a-zA-Z]+\.)+gathering(?:\.[a-z0-9_]+)*\.?)"')

# The same key with the mod id spliced in: "tooltip." + Gathering.MOD_ID + ".deck_size".
# Common enough in this codebase that ignoring it made the check report a dozen live keys as
# dead, which is the fastest way to teach everybody to ignore its output.
SPLICED = re.compile(
    r'"([a-zA-Z.]+\.)"\s*\+\s*(?:\w+\.)?MOD_ID\s*\+\s*"(\.[a-z0-9_]*)"')


def sources():
    for path in ROOT.rglob("*.java"):
        text = path.as_posix()
        if "/build/" in text or "/src/test/" in text:
            continue
        yield path


SHORTCUT = re.compile(r'Map\.entry\("([a-z0-9_]+)",\s*Component\.literal')

# The prefix every menu entry's label is built under. A shortcut is written beside one of
# those labels, so the label existing is exactly what makes the shortcut real.
MENU_PREFIX = "menu.gathering.table."


def strayShortcuts(entries):
    """Keys promised beside a menu entry that no menu entry goes by.

    The shortcut column is written from a table keyed on the entry's own name, so a name no
    entry uses is a key the player is never shown and nothing at all complains: the map is
    only ever read, never checked against the menu. "flip" sat there while the entries were
    called "turn_face_down" and "turn_face_up".

    Checked against the labels rather than against calls in the source, because plenty of
    entries pick their name with a ternary - tapped or untapped, log shown or hidden - and
    those names never appear next to the word "entry" at all.
    """
    promised = {}
    for source in sources():
        text = source.read_text(encoding="utf-8")
        for key in SHORTCUT.findall(text):
            promised.setdefault(key, source.relative_to(ROOT).as_posix())
    return {key: where for key, where in promised.items()
            if MENU_PREFIX + key not in entries}


def subtitles():
    """
    The subtitle keys the sound definitions ask for.

    Read rather than allowlisted, because a sound whose subtitle is missing is a subtitle
    nobody sees and an allowlist would hide exactly that. sounds.json is source too; it just
    is not Java.
    """
    if not SOUNDS.exists():
        return {}
    named = {}
    for name, sound in json.loads(SOUNDS.read_text(encoding="utf-8")).items():
        subtitle = sound.get("subtitle")
        if subtitle:
            named[subtitle] = f"sounds.json ({name})"
    return named


def main() -> int:
    entries = json.loads(LANG.read_text(encoding="utf-8"))

    whole = dict(subtitles())
    prefixes = {}
    for source in sources():
        where = source.relative_to(ROOT).as_posix()
        text = source.read_text(encoding="utf-8")
        found = list(KEY.findall(text))
        found += [head + "gathering" + tail for head, tail in SPLICED.findall(text)]
        for key in found:
            (prefixes if key.endswith((".", "_")) else whole).setdefault(key, where)

    missing = {key: where for key, where in whole.items() if key not in entries}
    used = set(whole)
    for prefix in prefixes:
        used.update(key for key in entries if key.startswith(prefix))
    # A line the log rewords when its actor and its subject are the same player lives under
    # the same key with ".own" on the end. GameLogText builds that name rather than writing
    # it, so the source never mentions it - but a "%s did it to %s's" wording with nothing
    # opposite it is still worth reporting, so it is derived rather than allowlisted.
    used.update(key + OWN for key in list(used) if key + OWN in entries)
    unused = sorted(
        key for key in entries
        if key not in used and key not in EXPECTED_UNREFERENCED)

    for key, where in sorted(missing.items()):
        print(f"missing: {key}  (asked for in {where})")
    for key in unused:
        print(f"unused:  {key}")
    stray = strayShortcuts(entries)
    for key, where in sorted(stray.items()):
        print(f"stray shortcut: {key} is not a menu entry  (promised in {where})")
    print(f"\n{len(whole)} keys written out, {len(prefixes)} prefixes, "
          f"{len(entries)} entries in en_us.json, {len(missing)} missing, {len(unused)} unused, "
          f"{len(stray)} stray shortcuts")
    return 1 if missing or stray else 0


if __name__ == "__main__":
    sys.exit(main())
