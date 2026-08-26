# Testing Gathering by hand

Phases 0 to 3 are built: the card pipeline, the table and a playable game, and collection
mode. So you can import a deck, put a table down, sit at it and play; find and buy sealed
product, open it, and build a deck out of what you own; trade with somebody; and borrow a
deck the server lends out.

The sections below are in the order they were built rather than the order you'd try them.
If you only have twenty minutes, do **1** (import), **5** (tables) and **6** (start a game) —
that is the mod's spine, and everything else hangs off it.

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

## The checklist

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
  or the position is wrong. It follows the **cursor only** — a card in your hand no longer
  shadows every slot you are not pointing at.
- **Out in the world**, with a card in hand and no screen open, there is no cursor to sit
  beside, so the card fills the screen instead.

Mana and tap symbols are drawn as symbols rather than printed as `{T}` and `{1}{W}`. They are
the mod's own art — lettered discs, not Wizards' pictographs, same reasoning as the card back
— so tell me if any of them are hard to read at size. Anything the mod has no symbol for is
left as written, braces and all, rather than becoming a blank box: if you spot one, that is
a symbol worth adding.

Card art has rounded corners now, cut into the image itself, so it looks like a card wherever
it appears rather than only where something happens to be framing it.

**Worth deliberately breaking:** import a deck, quit to the title screen, and come back — or
kill the game outright. Cards in your inventory should still know their names and still have
art, without you having to open anything. That did not work before: only opening a deck asked
the server about cards, so a loose card stayed blank for the rest of the session.

**Worth checking on the odd cards.** A split card (`/gathering card Fire`) is two lots of
rules text on **one** picture — if you see the same picture twice side by side, that is the
bug. A transform card (`/gathering card Delver of Secrets`) really is two pictures. Both
should survive a restart with both halves intact.

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
- **Right-click a row** for a menu of every pile the card is not already in — deck, sideboard,
  command zone — plus taking a copy. Moving to the command zone is one entry among them rather
  than what right-click does, because a deck editor whose right-click means "make commander"
  is a Commander deck editor.
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

- **Rename it.** The title is the name. Click it and type; it saves when you close the screen.
  A deck put together by dropping one card onto another starts with no name at all and the
  title says so, which is the only place that could be said.

### 5. Put some tables together

Craft a **Gathering Table** (three wool over planks, two legs) or take one from the creative
tab. It is a 2x2 multiblock: the block you click becomes its north-west corner.

- **Right-click an edge** to take that seat, and the same edge again to leave it. Right-click
  the top, or an edge that is not a seat, and it tells you how big the cluster is and how many
  of its seats are taken.
- **Push another table against it**, edge to edge, and they become one cluster sharing one
  surface: 2 tables seat 4, 3 seat 6, 4 seat 8. A fifth will not join — a cluster is capped at
  four tables, and it refuses the placement rather than sitting next to the cluster pretending.
- **Tables only merge if they line up.** A table offset by one block shares part of an edge
  but not all of it, and stays its own cluster. That is deliberate; tell me if it feels wrong.
- **Break any quarter** and the whole table comes up. Try it with somebody seated: it should
  refuse, and refuse to let you extend the cluster too, because reshaping moves the seats.

- **Right-click with a dye** to colour the felt. The colour is stored on the table rather
  than in its blockstate, so it survives a restart and a chunk reload, and it does not
  multiply the block's state count by sixteen.

**What I would most like to know here** is whether the table is the right height and size, and
whether merging feels like pushing tables together or like a puzzle. The model is deliberately
plain — a felt top on a wooden frame — because that is a thing you will want to design rather
than have me guess at.

### 6. Start a game

This is new and it is the first time any of the game core has been reachable at all.

1. **Take a seat** at a table (right-click an edge).
2. **Crouch and right-click the table.** A game begins, with a seat for every place at the
   cluster whether or not anybody is in it.
3. **Right-click holding a deck.** Your deck goes on the table and is shuffled — two separate
   entries in the log, because an unshuffled deck is an unshuffled deck.
4. **Right-click the table with an empty hand** to read the board: every seat, who is in it,
   life, and how many cards are in each zone.
5. **`/gathering table end`** while looking at the table ends the game. It is a command rather
   than a click because it cannot be undone and a table is a thing people lean on.

**Worth deliberately breaking:** start a game, put a deck down, then quit to the title screen
and come back. The board should be exactly as you left it, shuffle included. A game also locks
its tables — you cannot break or extend the cluster until it ends.

6. **Right-click the table again while a game is running** and you sit down at the board
   itself. That is section 7.

**What you will not see** is anybody else's hand or the order of your own library, and that is
the point rather than a gap. The status readout is built from the same visibility-filtered
view a real client will be sent, so it can only say what that client would be entitled to
know.

**Where the secrets live.** The log is saved in two halves. The readable half has the shape of
the game — who is at which seat, life totals, whose turn. The sealed half has your library's
order and the shuffle seed, encrypted with a key kept in the server's config directory rather
than in the world folder. Copy a world save and you have taken the ciphertext and left the key
behind. A test flips every bit of a sealed stream in turn to confirm an edited one will not
open, because a library edited on disk that still decrypted would be a stacked deck that
looked legitimate.

### 7. Play a game

Right-clicking a table with a game on it opens the seated view. This is new, and it is the
first time any verb past "put your deck down" has been reachable at all.

- **Your hand** runs along the bottom. **Drag a card onto the table** to play it; drag it back
  to your hand to pick it up; drag one already on the table to move it.
- **Left-click a card on the table** to pick it up, **right-click** to turn it over.
- **D** draws, **S** shuffles, **U** untaps everything, **+** and **-** change your life.
- **The strip along the top** is every seat: who, life, and how many cards in each zone. Click
  one to look at their board — you can move their public cards too, because that is the
  paper-Magic rule the design keeps. The log says who did it.
- **Hold the read key** over any card, here or anywhere, and the same inspect panel opens.
- **Right-click a card and pick "Write on it"** to put a line of text on it — a keyword, a
  reminder, what the face-down card is. Everyone at the table can read it: the first words
  show on the card and resting on it reads the whole thing. Rub it out from the same panel.
- **Hold a press on a zone** — graveyard, library, exile — for about a third of a second and
  the whole pile lifts instead of the top card. Drop it on another zone to move the lot: the
  graveyard onto the library is the "shuffle it all back in" gesture, in one act rather than
  forty. The same hold on a stack of cards lying on the felt picks up everything under the
  point. A quick click still does what a quick click did, and a press that moves is still a
  drag — the hold only counts if the hand stays still.

**Goldfishing works alone.** One person at a table is not a special mode: sit down, start,
put a deck down, draw seven. That is the phase 1 deliverable, so it is the thing most worth
half an hour of your time and a verdict.

**Look at the table from across the room.** A game in progress is drawn small on the table
top — each seat's permanents along their own band, tapped cards turned sideways. It updates
as moves happen and on a slow beat for anybody who walks up mid-game. That view is the public
board and nothing else: face-down cards are card backs, and nobody's hand is in it.

**What I would most like to know** is whether half an hour of goldfishing is bearable, and
where it stops being. The layout, the sizes, the keys and the drag feel are all first guesses
and all cheap to change.

### 8. Collect, buy, and build

Collection mode is off by default. Switch it on in `config/gathering-server.toml`:

```toml
[modes]
collection_enabled = true
```

Restart. The server reads what the current set was really sold as, which takes a few seconds
and one fetch.

**Your collection.** Put a **collection** block down and right-click it: everything you own,
searchable, one page at a time. Cards go in one at a time and come out one at a time — into
the deck in your hand if you are holding one — and a whole deck poured back in is one gesture
(sneak and right-click the block with the deck).

- **Build a deck from a list.** Open a collection and press **Build a deck...**, paste a
  decklist, and you get the deck it can make with the cards taken out of the box. It matches
  by card rather than by printing, so a list off a deck site works even though it names
  printings you do not own; the plain copy goes in before the foil; basics are conjured
  rather than taken, because basics are free. What the box is short of is listed rather than
  refused — ninety cards you have and a list of the ten to find beats no deck at all.

**Trading.** Hold a card and right-click another player: a trade table opens for both of you.
Your side lists what you are carrying with how much of each is up (`2/4`); left-click puts one
up, right-click takes one back. Both of you have to press Agree, and **changing anything
clears both agreements** - which is the point, and the thing to try breaking. Put a card up,
get them to agree, then swap it for a worse one and see that their agreement went out. Walk
more than eight blocks apart and it ends.

**The card shop.**

- **Find one.** Villages build a card shop among their houses: a room with a counter, a chest
  of stock behind it and two tables to play at. `/locate structure minecraft:village_plains`
  and look for it. Or make your own - put a **shop counter** down next to an unemployed
  villager and wait a moment; they take the job the way they would take a lectern. Trade with
  them and they level up, the way any villager does.
- **What they sell** is boosters at first. Levelling them up adds rungs rather than changing
  the ones they have: prerelease kits and Commander decks in the middle, display boxes higher,
  cases at master.
- **Every card shop sells the same thing.** Two shopkeepers at the same level have the same two
  trades at the same price, deliberately - so breaking the counter and re-placing it until the
  shelf offers what you want does not work. The whole world's shelf turns over every few hours
  (`collection.sealed_rotation_hours`), all shops together.
- **Buying a box gives you the box.** Right-click it to open it: a case gives six boxes, a box
  gives thirty-six packs, a Commander deck gives the hundred cards sleeved with the commander
  already in the command zone, plus the sample pack that really came in that box.
- **Prices are in emeralds**, derived from what is inside rather than typed per product
  (`collection.sealed_price_booster` is the one number). Anything dear enough to need more than
  a stack is paid for in emerald blocks and change.

### 9. Borrow a deck, and play for keeps

**Loaner decks work whatever else is switched off.** They need neither import permission nor a
collection, which is the point: a new player on a locked-down server can still play in their
first minute.

- The server writes `config/gathering-loaners/` on first start with one deck already in it.
  Drop more decklists in there, one file each — **the file name is what players see**, so
  `mono-red_burn.txt` shows up as "Mono Red Burn".
- **Sit down at a table carrying no deck** and you are offered the shelf. That is the whole
  flow; there is nothing to find. Pick one and, if a game is waiting for a deck, it goes
  straight down.
- `/gathering loaners` lists what is lent. `/gathering loaners reload` re-reads the folder, so
  writing a decklist does not mean restarting the server.

**Ante is off by default and needs collection mode.** Turn it on:

```toml
[ante]
enabled = true
cards_per_player = 1
exclusions = ["basic lands"]
```

- **Everybody at the table is asked before the game starts**, by name, every game. It does not
  begin until they have all said yes, and one "Not tonight" stops it. Worth trying to break:
  sit down at a table that is mid-question and check you are asked too rather than the others
  agreeing without you.
- **A card the server cannot look up is never staked**, so the first for-keeps game after a
  restart may stake nothing and say so. That is the safe direction on purpose.
- **The pot sits face up in the middle of the table** for the whole game, and goes to whoever
  takes the match. A drawn set gives every card back to whoever staked it, and so does ending
  the game by hand — the pot is saved with the table, so killing the server mid-game does not
  eat it.
- `exclusions` understands `basic lands`, `lands`, `rares`, `mythics` and `foils`. A word it
  does not know protects nothing **and says so in the log**, rather than silently protecting
  everything or nothing.

**What I would most like to know** is whether the ladder is paced right - whether a display box
arrives too early or too late - and whether a few hours is the right turnover.

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

- **Placeholder buildings.** The card shop that generates in a village is real and correct -
  counter, stock chest, two tables, the village's own materials - and it is a generated
  placeholder in the same sense the textures are. `tools/village.py` writes the five .nbt
  files; replacing them changes nothing else.
- **A village might not get a shop.** A jigsaw pool has no way to say "exactly one of these",
  only how often to try, so `collection.village_shop_weight` is tuned for about one per
  village rather than guaranteeing one. Some villages will have two and some none.
- **A chest does not decide which set.** It decides which kind of booster: a collector booster
  is much likelier out of an end city than out of a village. Tying a set to a structure is an
  idea and not built.
- **No theme picker.** The GUI art is all real textures generated from one palette in
  `tools/textures.py`, so a second theme is a matter of a second palette and a place to choose
  it — but there is no such place yet, and a resource pack is the only way to swap them today.
- **Placeholder art.** The booster wrapper and the collection block are generated templates,
  not drawn textures.

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

## Commands worth knowing

Everything below works in single player and on a server. `<>` marks an argument.

| command | who | what |
|---|---|---|
| `/gathering table info` | anyone | What the server thinks is at the table you are looking at: the cluster's shape, who is in which chair, whether a session is running, whose turn it is, and how many cards are in each zone. It never names a card. This is the thing to paste when something looks wrong. |
| `/gathering table fill <cards>` | op | Plays that many cards off the top of your own library onto your own mat, spread out on a grid. For looking at a full board without playing forty cards by hand. Defaults to 12. |
| `/gathering table end` | anyone at it | Ends the game at the table you are looking at. |
| `/gathering config` | anyone | Every setting and what it is set to. Add a setting name to read one, and a value after that (op) to change it — no restart. |
| `/gathering import <decklist>` | as configured | Imports a list without opening the screen. `/gathering import 60 Forest` is a legal deck in one line. |
| `/gathering card <name>` | as configured | One named card in your hand. `/gathering foil <name>` for the foil. |
| `/gathering pack give <set>` | op | Sealed product from nothing. `open` opens one instead, `list` says what a set was sold as. |
| `/gathering coverage <set>` | op | Whether that set's own packs can produce all of it. |
| `/gathering loaners` | anyone | What the server lends. `reload` (op) re-reads the folder without a restart. |

## Checking the parts you can't see

The half of the mod that never appears on screen has its own gate:

```bash
./gradlew verify        # everything: unit tests, data generation, in-world game tests
./gradlew :core:test    # just the fast pure-core suite, a few seconds
tools/smoke.sh          # boots both loaders, client and dedicated server
```

`verify` proves the code is right. It cannot prove a *loader* was wired up right — a loader
serving the mod's classes without its assets compiles, builds, passes every test, boots,
registers everything, and then draws missing textures and untranslated keys. Fabric was doing
precisely that until the resource pack list got read on startup, so `smoke.sh` now checks for
it.

Every stage of that has been confirmed capable of failing — a gate that can't fail
manufactures confidence rather than providing it.

### What runs when

Two of these are cheap and two are not, and the cheap two catch most of it.

| | costs | run it |
|---|---|---|
| `./gradlew build` | seconds | every change |
| `:neoforge:runGameTestServer` | ~20s | every change |
| `tools/smoke.sh` | ~10 min | a new registration, a new model or texture, or a loader entry point - the things that can boot wrong. Not for a string added to the language file. |
| `tools/shots.sh` | ~11 min | on demand, and once at the end of a piece of visual work rather than after each fix |

The two slow ones boot real game instances under software rendering, which is
why they cost what they do. Batch what you are checking and run them once: three
pictures of three separate fixes tell you no more than one picture after all
three, and cost half an hour more.

One check is deliberately off by default. Booster collation is read out of MTGJSON's
published set files, and a reader of somebody else's schema can only honestly be checked
against the real thing — but four megabytes of their card data has no business living in this
repository. So download a set or two yourself and point the suite at them:

```bash
mkdir -p /tmp/mtgjson && cd /tmp/mtgjson
curl -O https://mtgjson.com/api/v5/BLB.json    # a set
curl -O https://mtgjson.com/api/v5/SPG.json    # what its play boosters reach into
cd - && GATHERING_MTGJSON_DIR=/tmp/mtgjson ./gradlew :core:test --tests '*MtgjsonRealSetTest' -i
```

It prints what each file yielded and what it would need fetching next, and fails if a real
pack cannot be opened. With `BLB` alone, five of its six kinds of booster drop out, because
its Special Guest slot is printed in another set; with both files every kind reads clean.
Skipped entirely when the variable is unset.

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
