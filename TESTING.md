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

**Right-click the deck** to see what is in it, grouped by zone with copies collapsed. That
screen is section 3.

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

Then **hold Left Alt** — over the card in your inventory, or with it in your hand out in the
world.

**What should happen:** two shapes, on purpose.

- **In a screen** (inventory, chest, anywhere with a cursor) you get a small enclosed panel
  that follows the mouse, sitting where the tooltip would and replacing it. This is the
  inspect panel the table will use for cards in play, so it is worth telling me if the size
  or the position is wrong.
- **Out in the world**, with a card in hand and no screen open, there is no cursor to sit
  beside, so the card fills the screen instead.

The first look at a given card fetches the art — you may see "Fetching card art..." for a
moment, then it should be instant forever after, including across restarts.

Try `/gathering card Delver of Secrets` — a double-faced card should show **both halves**
side by side.

### 3. Open a deck and change it

**Right-click a deck** to open its list. The decklist is a panel against the left edge with a
scrollbar down its tapered edge; the hovered card and its rules text sit in two frames beside
it.

- **Just hover a row** — no key to hold — and that card fills the frame beside the list with
  its rules text in the frame after it. Nothing covers the list, so you can run down it
  reading each card in turn.
- **Left-click a row** to take one copy out of the deck. It goes into your inventory, or onto
  the floor if there is no room — a card is never destroyed to make space.
- **Right-click a row** to make that card a commander. Right-click it again in the command
  zone to send it back to the deck.
- **Put cards back the way you fill a bundle**: hold a card on the cursor and right-click the
  deck, or hold the deck and right-click a card. Either way the card goes into the deck.

**You can also start a deck from nothing.** Pick up a card in your inventory and right-click
it onto another card — the two become a deck, and the same click adds a third. That deck has
no name, so it just reads "Deck" until you give it one; everything else about it works like an
imported deck, including the list screen.

There is deliberately **no bundle-style way to take a card out**. Right-clicking a deck with
an empty cursor does nothing, because pulling an unseen card off a hundred-card deck is a
thing you would only ever do by accident. Taking a card is done from the list, where you can
read its name first.

The list is live: the server owns the deck and the screen shows whatever is in your hand, so
every change appears the moment it is applied. Drop the deck, or swap it out of that hand,
and the screen closes.

**What should happen:** the deck's tooltip count follows every edit, and a commander you set
this way shows on the item in gold like an imported one does.

## What I'd most like to know

In rough order of how much it would change what I build next:

1. **How does a card sit in your hand?** The display transforms in
   `assets/gathering/models/item/card.json` are guesses — I cannot see the result. Angle,
   scale and position are all plain model data in that file, so they are editable, but tell
   me if they are badly off and I will fix the defaults. Editing
   `neoforge/build/resources/main/assets/gathering/models/item/card.json` and pressing
   **F3+T** in game applies the change immediately, with no rebuild.
2. **Is Left Alt right, and is the cursor panel the right size?** The key conflicts with
   nothing I know of, but you'll find out in a minute what I can't. The panel sizes its art
   to 45% of the screen height and shrinks it further on a wordy card to keep the text on
   screen — both numbers are guesses.
3. **Is the sidebar readable** — width, wrapping, whether oracle text gets cut off on a
   wordy card. Try `/gathering card Kozilek, Butcher of Truth`.
4. **Does import feel fast enough** to paste a 100-card list without wondering if it hung?

## What is deliberately not there yet

- **No table, no seats, no game.** The game core — zones, the verb set, visibility, undo,
  drag-and-drop positions — is built and tested but has no block to live in. That's the next
  phase and it's gated on this one working.
- **No collection block.** Specified in the design brief, phase 3.
- **Cards don't stack, and there's no way to get rid of one** except dropping it.
- **No way to name a deck after the fact.** Import names one; a deck started from two cards
  cannot be renamed yet.
- **No theme picker.** The GUI art is all real textures generated from one palette in
  `tools/gui_textures.py`, so a second theme is a matter of a second palette and a place to
  choose it — but there is no such place yet, and a resource pack is the only way to swap them
  today.
- **No deck legality check anywhere.** Setting a card as a commander asks no questions; the
  format validator exists but nothing calls it until there is a table to sit at.

### 4. Squash the window

Grab the window edge and make it small — as small as it goes — then try GUI scale 1 through
4 in Video Settings, on both screens.

**What should happen:** nothing overlaps, nothing runs off an edge, and nothing disappears
that you needed. The deck screen sheds the rules-text frame first and then the card frame as
things get tight, because the decklist is the thing that screen is for; the import screen
gives its paste box whatever is left. Long card names shrink to fit their row rather than
running under the foil tag, and only get cut with an ellipsis when shrinking any further
would make them unreadable.

The layout maths is checked at every size from 320x240 up to 3840x2160 by
`DeckScreenLayoutTest`, so what I most want to know is whether it *looks* right, not whether
it fits.

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
