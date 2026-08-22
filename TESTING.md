# Testing Gathering by hand

What's built so far is **Phase 0** — the card pipeline — plus the **pure core of Phase 1**,
which has no in-world presence yet. So: you can import decks and read cards. There is no
table to sit at yet.

## Setup

**Gradle itself must run on Java 21** — not just your compiler. Minecraft 1.21.1 requires
it, and Fabric Loom sets Minecraft up inside the Gradle daemon, so a Java 17 daemon fails
the whole build including NeoForge-only tasks. The build checks this up front and tells you
how to fix it, but to save a round trip:

```bash
java -version                      # if this says 17, set JAVA_HOME first
```

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)      # macOS
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk         # Linux
```

```cmd
set JAVA_HOME=C:\Program Files\Java\jdk-21          :: Windows, cmd - no quotes, no spaces round =
```

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"     # Windows, PowerShell
```

The two Windows shells are not interchangeable. Check where your JDK actually landed —
Adoptium installs under `C:\Program Files\Eclipse Adoptium\jdk-21.x.x-hotspot`. No JDK 21?
[Temurin 21](https://adoptium.net/temurin/releases/?version=21).

Then:

```bash
git clone -b claude/new-session-beye3i https://github.com/MeekTheSneak/Gathering
cd Gathering
./gradlew --stop                   # drops any daemon still on the old JDK
./gradlew :neoforge:runClient      # gradlew, without ./, on Windows
```

The first build pulls and decompiles Minecraft — expect **5–10 minutes**, once. After that
it's seconds. A launch window will open; make a creative world.

## The Phase 0 checklist

This is the deliverable from the design brief: *paste the list, get a deck, read every card
in full resolution.*

### 1. Import a decklist

```
/gathering import
```

Run it **with nothing after it** — that opens a paste box with no length limit. Typing the
list after the command instead sends it through chat, which caps at 256 characters and will
never fit a real deck.

Into the box you can paste either:

**An Archidekt link** — `https://archidekt.com/decks/1234567/whatever`. The deck is read
straight from Archidekt, which hands over exact printings, so nothing is guessed.

Moxfield links are recognised but can't be read: their API refuses other tools, and working
around that isn't something the mod will do. Use Moxfield's More → Export → Text and paste
that instead.

**Or the decklist text.** Paste a real list — Moxfield, Archidekt, Arena and MTGO exports all work, as do
plain `1 Sol Ring` lines. Something like:

```
Commander
1 Halana and Alena, Partners (VOW) 239

Deck
1 Sol Ring (LTC) 284 *F*
1 Arcane Signet
1 Fire // Ice
4 Persistent Petitioners
30 Forest
```

Name it and add a description if you like - both show on the item, and a Commander deck also
shows its commander. Press **Import**, and a deck lands in your inventory.

**Right-click the deck** to see what is in it, grouped by zone with copies collapsed. Hold
Left Alt over any row to read that card.

**What should happen:** roughly a second on a cold cache, near-instant if you import the same
list again. The deck item is named after the list and its tooltip shows the card count.

**Worth deliberately breaking:** put a typo in a line (`1 Sol Rong`) and a bad quantity
(`0 Sol Ring`). Both should come back named, with line numbers, and the *other* cards should
still import. A list that half works should still give you a deck.

### 2. Read a card

Import gives you a deck, not loose cards, so to hold one:

```
/gathering card Lightning Bolt
/gathering foil Sol Ring
```

The card renders as **the actual card** in your hand and in the inventory, not a generic
icon. **Right-click** to turn it over: a double-faced card shows its other side, anything else
shows its back.

Then **hold Left Alt** with the card in your hand, or over it in your inventory.

**What should happen:** the card fills the screen at full resolution with its oracle text
beside it. The first look at a given card fetches the art — you may see "Fetching card
art..." for a moment, then it should be instant forever after, including across restarts.

Try `/gathering card Delver of Secrets` — a double-faced card should show **both halves**
side by side.

## What I'd most like to know

In rough order of how much it would change what I build next:

1. **How does a card sit in your hand?** The display transforms in
   `assets/gathering/models/item/card.json` are guesses — I cannot see the result. Angle,
   scale and position are all plain model data in that file, so they are editable, but tell
   me if they are badly off and I will fix the defaults. Editing
   `neoforge/build/resources/main/assets/gathering/models/item/card.json` and pressing
   **F3+T** in game applies the change immediately, with no rebuild.
2. **Is Left Alt right?** It conflicts with nothing I know of, but you'll find out in a
   minute what I can't.
3. **Is the sidebar readable** — width, wrapping, whether oracle text gets cut off on a
   wordy card. Try `/gathering card Kozilek, Butcher of Truth`.
4. **Does import feel fast enough** to paste a 100-card list without wondering if it hung?

## What is deliberately not there yet

- **No table, no seats, no game.** The game core — zones, the verb set, visibility, undo,
  drag-and-drop positions — is built and tested but has no block to live in. That's the next
  phase and it's gated on this one working.
- **No collection block.** Specified in the design brief, phase 3.
- **Cards don't stack, and there's no way to get rid of one** except dropping it.

## Checking the parts you can't see

The half of the mod that never appears on screen has its own gate:

```bash
./gradlew verify        # everything: unit tests, data generation, in-world game tests
./gradlew :core:test    # just the fast pure-core suite, a few seconds
```

Every stage of that has been confirmed capable of failing — a gate that can't fail
manufactures confidence rather than providing it.

## If something goes wrong

- **Cards import but show no art.** The client fetches images directly from Scryfall. Check
  the client log for `Could not load card art`; a proxy or firewall between you and
  `cards.scryfall.io` would do it.
- **`The card pipeline is not running on this server.`** The pipeline starts with the world.
  Rejoining fixes it; if not, the server log will say why the cache directory couldn't open.
- **The import screen says it can't reach Scryfall.** Their API was unreachable. The cache
  keeps everything it already fetched, so a retry costs nothing.
- **Anything crashes.** The log is in `run/logs/latest.log`. The real error is the last
  `Caused by:` at the bottom, not the first line.
