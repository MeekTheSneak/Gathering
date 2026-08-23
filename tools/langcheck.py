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

# Entries nothing in the source asks for by name, on purpose. Listing them here rather than
# letting them show up as unused every run is the difference between a check people read and a
# check people learn to skip.
EXPECTED_UNREFERENCED = {
    # Minecraft derives these from the registry id and the keybind category itself.
    "block.gathering.table",
    "item.gathering.card",
    "item.gathering.deck",
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


def main() -> int:
    entries = json.loads(LANG.read_text(encoding="utf-8"))

    whole = {}
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
    print(f"\n{len(whole)} keys written out, {len(prefixes)} prefixes, "
          f"{len(entries)} entries in en_us.json, {len(missing)} missing, {len(unused)} unused")
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
