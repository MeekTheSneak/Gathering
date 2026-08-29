# Gathering — playtest brief

A Minecraft table you sit down at and play a real card game on. Nothing is automated and nothing
tells you no, so the only way to find out if it works is to play it. Work down the passes and tell
me what broke.

## 0. Before you start

- **Minecraft 1.21.1.** Everyone on the same loader — a NeoForge client can't join a Fabric server.
  - **NeoForge:** `gathering-neoforge-0.0.1.jar`, needs NeoForge 21.1.0+
  - **Fabric:** `gathering-fabric-0.0.1.jar`, needs Fabric Loader 0.19.3+ **and Fabric API**
- Jar goes in `mods/` on the server **and** every client. Fabric API goes in `mods/` too.
- Be online the first time. Card names, text and art come from Scryfall and cache to disk. No
  account, no API key. A card you've seen works offline forever.
- First server boot takes a bit longer — it works out what the current set was really sold as and
  fetches its data once.
- **Decide who can import decklists before anyone joins.** Out of the box, collecting is on and
  importing is an operator's tool, so your friends get told "only server operators can import
  decklists here". In `config/gathering-server.toml`:

```toml
[import]
allow_all_players = true     # everyone can paste a decklist
[modes]
collection_enabled = true    # leave on to test packs, shops and trading
```

Or live, no restart: `/gathering config import.allow_all_players true`

## 1. First ten minutes (alone)

**Get a deck**
- [ ] `/gathering import` **with nothing after it** — that opens a paste box with no length limit.
      Typing the list after the command sends it through chat, which caps at 256 characters.
- [ ] Paste a real decklist. Moxfield, Archidekt, Arena, MTGO and deckstats exports all work, as do
      plain `1 Sol Ring` lines. Name it, press Import, deck lands in your inventory.
- [ ] Paste an Archidekt link instead — it fetches the exact printings. (Moxfield links are
      recognised but can't be fetched; use their More → Export → Text.)
- [ ] **Break it on purpose:** a typo in one line (`1 Sol Rong`) and a bad quantity (`0 Sol Ring`).
      Both should come back named with line numbers and every *other* card should still import.
- [ ] Right-click the deck to open its list. Hover a row to read that card beside it, left-click
      takes a copy out, right-click offers every pile it isn't in. Click the title to rename it.

**Read a card**
- [ ] `/gathering card Lightning Bolt` — it should be the actual card in your hand and inventory,
      not a generic icon. Right-click turns it over.
- [ ] Hold **Left Alt** over it in your inventory — a panel follows the cursor and replaces the
      tooltip. Is Alt the right key? Is the panel the right size? Both are guesses.
- [ ] Hold **Alt** with the card in hand out in the world, no screen open — it fills the window and
      tips with your mouse.
- [ ] `/gathering foil Sol Ring` — the shine is drawn, not fetched, so it only exists while the card
      moves.
- [ ] Odd cards: `/gathering card Fire` is two lots of text on **one** picture (the same picture
      twice is the bug). `/gathering card Delver of Secrets` really is two pictures.
- [ ] Quit to the title screen and come back. Cards in your inventory should still know their names
      and still have art without you opening anything.

**Put a table down**
- [ ] Craft a Gathering Table — three wool over a frame of planks — or take one from the creative
      tab. It's a 2x2 multiblock; the block you click becomes its north-west corner. (Cobblestone,
      polished blackstone and crying obsidian versions exist and all cluster with each other.)
- [ ] Push a second table against it edge to edge. 2 tables seat 4, 3 seat 6, 4 seat 8. A fifth
      should refuse. A table offset by one block should stay its own cluster — that's deliberate.
- [ ] Right-click with a dye to colour the felt. Should survive a restart and a chunk reload.
- [ ] **Walk up holding the deck and right-click.** You should be seated, shuffled and holding
      seven, in that one click.
- [ ] Press **F1** at the board for every key. That list is the only teaching this mod does — tell
      me if anything on it is a lie.

**How it looks**
- [ ] Options → Video Settings → **Gathering look**: felt, slate, walnut, template. The template
      look draws every element as a labelled diagram *on purpose* — it's a blueprint, not a bug.
- [ ] Squash the window as small as it goes, then try GUI scale 1–4. Nothing should overlap, run off
      an edge, or disappear.

## 2. Goldfishing, alone, for an hour

One person at a table isn't a special mode. Sit down, put a deck down, draw seven, play a game
against nobody. **This is the single most useful hour anyone can give the project** — the layout,
sizes, keys and drag feel are all first guesses.

- [ ] Drag a card from your hand onto the felt, drag it back, drag one already down. Left-click
      picks up and does nothing else — it should never tap.
- [ ] **E** taps, **Q** untaps (cursor or whole selection). **U** untaps everything you own.
- [ ] **+ and −** put a +1/+1 counter on the card under the cursor, or change your life when the
      cursor is on bare felt. **P** moves the phase marker on (**shift-P** back), and so does
      clicking the turn readout at the top right.
- [ ] **Passing the turn no longer untaps anybody** — that was reported and removed. The incoming
      player presses **1** or **U**. Passing also skips chairs nobody is sitting in.
- [ ] Walk the number row: 0 pass, 1 untap all, 2 draw, 3 scry, 4 mill, 5 reveal, 6 surveil,
      7 exile, 8 graveyard, 9 bottom in no order. Every card menu prints its key beside the entry —
      if a menu says one key and another key does the thing, that's a real bug.
- [ ] Right-click a card: Write on it, Counters…, Set power/toughness…, Freeze, Attach to…, Make a
      token copy, Cascade…, Loyalty +1 / −1. Any counter name in play at the table is a button on
      the counters panel, so the second card you put a "flying" counter on is one click.
- [ ] Right-click the felt: Mulligan, Search library, Make a token…, Write a card…, Make an
      emblem…, Bring in a dungeon…, Roll a die…, Flip a coin, Sort hand by cost, Discard at
      random…, Undo my last action, Concede.
- [ ] Roll the **planar die** (on the Roll a die… list). Four blanks, a chaos and a planeswalk. The
      server rolls, so nobody has to be trusted. The result is announced across the felt for a few
      seconds as well as going in the log.
- [ ] Write a blank card (the monarch, the initiative, the ring). Then read it at the size a
      four-player board draws a card at — readable, or do you rest on it every time?
- [ ] **Hold a press on a zone** (graveyard, library, exile) for about a third of a second — the
      whole pile should lift instead of the top card. Graveyard onto library = "shuffle it all back
      in" in one gesture.
- [ ] Press **Home**, press **V**, press **Home** again. V moves the game onto the real table block
      seen from above. The board should be the same size in both views.
- [ ] Scroll with the cursor over a zone, in both views. Whatever's under the pointer should stay
      under the pointer.
- [ ] `/gathering table fill 12` plays twelve cards onto your mat, for looking at a full board
      without playing forty cards by hand.
- [ ] Quit mid-game and come back. The board should be exactly as you left it, shuffle included. A
      game locks its tables — you shouldn't be able to break or extend the cluster until it ends.
- [ ] Read the log back (**L**) after an hour. It's the whole substitute for a rules engine. Does it
      say who did what, in words, in an order a person can follow?

**The verdict I actually want:** is half an hour of goldfishing *bearable*, and where exactly does
it stop being? Not "it worked" — where you got bored, where you had to think, where you reached for
something that wasn't there.

## 3. Two people, one table

Open to LAN is enough. This is the first time any of it has been done by two humans.

- [ ] Both sit at one table and play a real game.
- [ ] Press your chat key at the board. It should reach the people playing and watching and nobody
      else, and turn up over the felt as well as in the chat window.
- [ ] Show your hand (right-click felt → Show my hand to…). Your hand wears a warm band while it's
      open; they get a "Read X's hand" row. They should read it and **not** reach into it.
- [ ] **Trade.** Hold a card, right-click the other player. Left-click puts one up, right-click
      takes it back, both press Agree. Then try to cheat: get them to agree, swap your card for a
      worse one, and check their agreement went out. Walk 8 blocks apart and it should end.
- [ ] **One of you stands beside the table and just watches.** The game draws itself on the table
      block. Face-down cards must be card backs and nobody's hand may be in it.
- [ ] Both grab the same card at the same instant. The later one should be refused quietly, not end
      up with the card in two places.
- [ ] Watch a scry from across the table; look at each other's face-down cards.

**The one thing worth staring at:** a client is never *sent* what it isn't entitled to know — not
sent-and-hidden, not sent. If you ever see something you shouldn't have, stop and write down
exactly what you did. That's the most valuable bug report this project can get.

## 4. Four at one table

The deliverable everything has been waiting on. Four tables merged = eight seats.

- [ ] **Does the board agree?** A card dragged by one player should land in the same spot on
      everybody's screen, at the same angle, in the same order in its pile.
- [ ] **How does lag feel?** Every action is a round trip and the client predicts nothing on
      purpose. If that reads as sluggish rather than deliberate, say so — only real play can show it.
- [ ] Click along the seat strip at the top and look at each other's boards. You can move other
      people's public cards too (paper-Magic rule) and the log says you did.
- [ ] Stand up and sit down mid-game. Disconnect mid-turn and reconnect. Restart the server
      mid-game. A board should outlast its player.
- [ ] Sit down carrying **no deck** — you should be offered the shelf of loaner decks, and it goes
      straight down. `/gathering loaners` lists them.
- [ ] **Sit down after the game has already started.** Your seat should say your name, not
      "(away)", and you should be able to act. This was broken and is the fix most worth
      re-testing with four people.
- [ ] **Leave the table** (right-click felt → Leave the table) and your deck comes back to you.
      Your board stays on the felt. A deck always returns to whoever put it down, even if
      somebody else has taken the chair since.
- [ ] Play one game **for keeps**. Everyone is asked by name, every game, and one "Not tonight"
      stops it. The pot sits face up in the middle all game. Try to break it: sit down at a table
      that's mid-question and check you get asked too.

```toml
[ante]
enabled = true
cards_per_player = 1
exclusions = ["basic lands"]   # also: lands, rares, mythics, foils
```

## 5. The collecting game

Packs are built from the actual print sheets each set was really sold with, so a pack from a given
set contains what that pack contained.

- [ ] `/gathering pack give <set>` for product out of nothing, or find it properly — packs turn up
      in loot and villages build a card shop (`/locate structure minecraft:village_plains`, look for
      a counter, a stock chest and two tables). Or put a shop counter next to an unemployed villager.
- [ ] **Tear a pack open.** It comes to the middle of the screen, leans towards your cursor, and the
      tear follows your cursor across the wrapper. The torn edge glows before a card is shown.
      **Watch two things:** the tear should look *torn*, not cut, and the wrapper should end exactly
      at the tear with no gap. Both were wrong until recently.
- [ ] Shift-right-click a pack instead if you'd rather just have the cards. The ceremony should
      never be compulsory.
- [ ] Open a box: a case gives 6 boxes, a box gives 36 packs, a Commander deck gives 100 cards
      sleeved with the commander in the command zone plus the sample pack that really came in it.
- [ ] Check the shop isn't gameable: two shopkeepers at the same level should have the same trades
      at the same prices, so re-placing a counter until the shelf offers what you want doesn't work.
- [ ] Collection block → right-click. Everything you own, searchable, a page at a time.
      Sneak-right-click it holding a deck to pour the whole deck back in.
- [ ] **Build a deck…** and paste a list — you get the deck it can make out of the box, and a list
      of what it was short of. Matches by card not printing; plain copies go in before foils; basics
      are conjured because basics are free.
- [ ] Set progress → left-click a set for what you're missing, coloured by rarity. Left-click a row
      to mark a card **wanted**; right-click a set to filter your collection to it. The wants list
      is saved per player — check it survives a relog.
- [ ] Hold **Alt** on a card you pulled, won or traded for. Cards remember where they came from and
      who had them before. Trade one twice and check it still reads sensibly.
- [ ] Draft with 4–8 people, keep your pool, build out of it.

## Every command

| command | who | what |
|---|---|---|
| `/gathering import` | as configured | Opens a paste box with no length limit. **Run it with nothing after it.** Takes a decklist or an Archidekt link. |
| `/gathering card <name>` | as configured | One named card into your hand. |
| `/gathering foil <name>` | as configured | The same, foil. |
| `/gathering table info` | anyone | **Paste this when something looks wrong.** Cluster shape, who's in which chair, whether a session is running, whose turn, cards per zone. It never names a card. |
| `/gathering table fill <n>` | op | Plays that many cards off your library onto your mat, on a grid. Defaults to 12. |
| `/gathering table end` | anyone at it | Ends the game at the table you're looking at. |
| `/gathering config` | anyone | Every setting and its value. Add a name to read one, a value after that (op) to change it, no restart. |
| `/gathering pack give <set>` | op | Sealed product from nothing. `open` opens one, `list` says what that set was sold as. |
| `/gathering coverage <set>` | op | Whether that set's own packs can produce all of it. |
| `/gathering loaners` | anyone | What the server lends. `reload` (op) re-reads the folder without a restart — the file name is what players see. |

## Every key at the table (or just press F1)

Matched to Tabletop Simulator's defaults where they exist.

| key | does | key | does |
|---|---|---|---|
| `0` `Enter` | End turn | `Alt` | Read the card under the cursor |
| `1` `U` | Untap everything | `F1` | Every key, on screen |
| `2` | Draw a card | `L` | Game log |
| `3` | Scry | `G` | Stack the selection |
| `4` | Mill | `Delete` | Remove tokens |
| `5` | Reveal the top card | `+` `−` | A +1/+1 counter on the card under the cursor, or a life |
| `6` | Surveil | `V` | Play on the table block itself |
| `7` | To exile | `Home` | Frame the whole table |
| `8` | To graveyard | Wheel | Zoom, on whatever's under the pointer |
| `9` | To the bottom, in no order | WASD / arrows | Move the view (middle-drag too) |
| `E` / `Q` | Tap / untap | Left-drag | Move a card — from a zone, or into one |
| `R` | Shuffle library | Hold a zone | Pick the whole pile up |
| `F` | Turn face up or face down | Shift-click | Select (box-drag selects a group) |
| `P` | Next phase (shift for the one before) | `Esc` | Close what's open, or leave the table |

## How to report something

1. **What you expected, and what happened instead.** In that order. "It broke" costs an evening of
   guessing.
2. **Which loader and version**, and whether it was singleplayer, LAN or a dedicated server.
3. **The output of `/gathering table info`** if it involved a table. It never names a card, so it's
   safe to paste in public.
4. **The log if it crashed** — `logs/latest.log`. The real error is the last `Caused by:` at the
   bottom, not the first line.
5. A copy of the world save reproduces anything: the session is an event log, so the save carries
   the whole game and the exact way it broke.

## Known, not a bug

- **The art is placeholder.** Textures, block models, the booster wrapper, the collection block and
  most interface graphics are generated stand-ins. Report how something *works*, not how it looks —
  unless it's unreadable, which is worth knowing.
- **A village may get two card shops, or none.** A jigsaw pool can only say how often to try.
- **Moxfield links can't be fetched.** Their API refuses other tools. Use More → Export → Text.
- **Nothing enforces the rules, ever.** You can tap a tapped creature, set your life to minus
  eleven, or draw six on turn one. That's the design — the rules live in your heads and the log
  records who did what.

---
*Unofficial fan content. Not approved or endorsed by Wizards of the Coast. Card data and images come
from Scryfall; no card images ship inside the mod.*
