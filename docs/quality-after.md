# The five tasks, afterwards

The companion to `docs/quality-baseline.md`. Same five tasks, same counting rules, same
machine — a container with no person in front of it.

**Counted the same way.** A gesture is something a player has to decide to make. Moving the
mouse to a card is not counted; pressing a button is. Waiting for the server is not counted.
Typing a word counts as one entry and is noted separately, because a name that has to be
retyped every time is a different cost from a button.

**Still not measured, and not inferred:** time to complete anything, by anyone; mistakes and
wrong turns; whether a newcomer finds F1; frame times on real hardware; latency over a real
network. `docs/playtest.md` is the form for those and it needs people and a graphics card.
Nothing below is an improvement claimed from a feature existing — each is a count off the code
path that handles it.

## The comparison

| Task | Before | After | What changed |
|---|---|---|---|
| 1. Draw / play / tap / pass | 4 | **4** | count unchanged; the cost was discovery and unrebindable keys, and both moved |
| 2. Named counter on 3 cards | 6, or 6 + typing every fresh game | **6**, typing once ever | remembered and pinned names are buttons |
| 3. Familiar token | 2, or 3 + typing | **2** | pinned names are always on the row |
| 4. Read the card underneath | unreachable (3 + two public moves) | **3, nothing moved** | a chooser lists what is under the cursor |
| 5. Missing cards for a deck | unbounded | **unbounded, but answered** | the shortfall is reported with truthful hints |

## Task by task

### 1. Draw, play, tap, pass — 4, unchanged

Still four gestures, and it should be. The baseline said the real cost was discovery and that
"the keys are not rebindable. A left-handed player, or one whose keyboard layout puts those
keys somewhere else, has no recourse."

Every verb with a key now has a real `KeyMapping` in Minecraft's own Controls screen. The F1
list interpolates the binding rather than printing the key it shipped with — fourteen lines
rather than four, so a player who rebinds draw to Z is told Z. `/` opens a search over the
verbs by name and alias, which is a route to all of them that needs no list at all.

### 2. Named counter on three cards — 6, and the typing is gone

Three shift-clicks, right-click, "Counters", one press. Unchanged.

What changed is the last one. The baseline: "Nothing remembers what *this player* names; only
what this table currently shows" — and remembered counter names were in fact being written to
disk and read by nobody, so the feature had never once worked. They are buttons now, ahead of
the names that happen to be on this table, and a name can be pinned so it survives eight other
counters being used. **Typing drops from once per fresh game to once ever, per server.**

### 3. A familiar token — 2

Right-click the felt, press the row. The baseline's "3 plus typing" case was a token that had
fallen off the recents; pinning is exactly the fix for that, and a pinned name is offered
whether or not it was used lately. Pinning costs three gestures once.

### 4. Reading the card underneath — 3, and nothing moves

The baseline's sharpest finding: "It is not a cost in gestures; it is a task the player cannot
do at all without moving other people's cards."

Right-click the card, "What else is here", choose the row: **3 gestures, and the board does not
change.** The two public moves are gone. It is also reachable from the palette and bindable to
a key, so it is not a mouse-only answer.

**A remaining gap, stated:** those three gestures reach the buried card's own *menu*, where it
can be acted on. Reading its face at full size is still the read key on whatever is under the
cursor, which is the top card. Getting the full-size read onto a buried card is not done.

### 5. Missing cards for a deck — still unbounded, but the question is answered

The baseline: "there is no view that answers the question", and building a deck from a
collection would report what it was short of and stop there.

It still has no dedicated view, so the gesture count is unchanged and this task is **not
fixed**. What did change is that the shortfall now carries truthful guidance on where those
cards come from *on this server* — read off the settings in force, so a server with the shop
off never suggests the shop, and one with collecting off says so instead of listing three
sources that cannot help.

## What the counts say now

Two of the five improved on the axis this machine can measure, one improved by removing typing
rather than gestures, one is unchanged by design, and one is unchanged and openly unfinished.
Task 4 is the one worth noting: it moved from impossible-without-touching-the-board to three
gestures, which is the only entry in the table that changed kind rather than degree.

Everything a human would have to judge — whether any of this *feels* better — is still blank,
and is still blank on purpose.
