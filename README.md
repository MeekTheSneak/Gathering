# Gathering

**Sit down at a table in Minecraft and play a real card game with your real decks.**

Gathering turns a wooden table into a full tabletop card surface: decks that are yours, actual
cards you can pick up and read, hidden hands, and four friends round one board. It works the
way a table works — you move the cards, you decide what happens. Nothing is automated and
nothing tells you no.

> **Unofficial Fan Content.** Not approved or endorsed by Wizards of the Coast. Portions of
> the materials used are property of Wizards of the Coast. ©Wizards of the Coast LLC.
>
> Card data and images come from [Scryfall](https://scryfall.com). No card images ship inside
> this mod; each player's game fetches and caches its own.

| | |
|---|---|
| **Minecraft** | 1.21.1 |
| **Loaders** | NeoForge and Fabric |
| **Players** | Singleplayer, LAN, or a dedicated server |
| **Needs** | An internet connection the first time it looks up a card |
| **Licence** | MIT — free, and always will be |

---

## What you actually do

**Craft a table.** Three wool over a frame of planks. Place it and it builds itself into a
two-by-two table you can walk around. Put four tables together and they merge into one
surface seating up to eight.

Stone ones too, for builds the wooden one does not suit: **cobblestone** on stout legs for a
cellar or a rubble hall, **polished blackstone** with a chiseled course for somewhere formal,
and **crying obsidian** standing on a single plinth that glows. They are the same table in
every way that matters — the felt dyes, the game plays, and any of them cluster into any
other — so mixing materials in one eight-seat table is a decision about the room, not about
the game.

**Get a deck.** Two ways in, and a server picks which. **Out of the box you earn your cards:**
open packs, keep a collection, and build a deck from what you own. **Or a server can let
everyone import instead** — `/gathering import`, paste a decklist, and you hold the deck a
moment later. It reads exports from Moxfield, Archidekt, MTG Arena, MTGO and deckstats, or
plain `1 Sol Ring` lines, and an Archidekt link fetches the exact printings for you.
(Operators can always import, whatever the setting.)

**Sit down and play.** Walk up holding the deck and right-click. You're seated, shuffled and
holding seven. From there it's a table: drag cards where you want them, tap them, put them in
your graveyard, count your life, and argue about the stack out loud like you would in person.

Dice and coins are there too, because Magic keeps asking for them — a d4 through d20, or any
number of sides up to twenty, and a coin. The server rolls, so nobody has to be trusted, and
the result lands in the game log under your name where the whole table can read it.

Hold **Alt** over any card to read it full size with its rules text. Press **F1** at the table
for every key. Press **V** to switch between playing on your screen and playing on the actual
table block in the world, which is also what everybody standing around it can see.

## The two things that make it different

**There is no rules engine, and there never will be.** The mod moves cards, tracks numbers and
shows things. It never says no. You can tap a tapped creature, set your life to minus eleven,
or draw six on turn one — exactly like sitting at a kitchen table with a pile of cards. The
rules live in your heads, and the game log records who did what, by name, so the table can
always check. The only exception is a deck check before a formatted game starts, the same way
a tournament checks decks at the door, and it stops the moment the game begins.

**Hidden information is real.** Your hand is yours. The identity of a card in a hidden zone is
never sent to a player who isn't entitled to see it, so there is nothing on the other end for a
modified client to read. Face-down cards travel as blank markers that change every time they
flip, and shuffles come from a seed that is never logged, never sent and never shown. This is
the one security property of the mod and it has its own test suite.

## Collecting, drafting and playing for keeps

**On by default**, because a card conjured out of a decklist and a card opened out of a pack
cannot both be ordinary at the same table — the first makes the second pointless. So unless
your server says otherwise, cards are things you own rather than things you type:

- **Find and buy sealed product.** Packs turn up in loot, and a shopkeeper villager sells
  boosters, boxes, Commander decks and cases from behind a shop counter.
- **Open a pack properly.** Right-click and the pack comes to the middle of the screen; the
  tear follows your cursor across the wrapper, and the torn edge glows before a single card is
  shown. Shift-right-click if you'd rather just have the cards.
- **Real collation.** Packs are built from the actual print sheets a set was really sold with,
  read from published set data — so a pack from a given set contains what that pack contained.
- **Keep a collection**, search it, and build a whole deck from a list in one go.
- **Draft** with four to eight players, keep your pool, and build out of it.
- **Trade** with another player, lend a deck to a friend who has none, or **play for keeps**
  with an ante — which only ever happens when everyone at the table agrees to it.

Servers control all of it in one config file the mod writes and explains on first start.
A server that would rather everybody just brought a decklist sets `allow_all_players = true`
under `[import]`, and can switch collecting off entirely if it wants nothing but the table.

## Where the cards come from

Worth knowing, because it's the part people ask about:

Card names, rules text and art all come from [Scryfall](https://scryfall.com), fetched when
they're needed and cached on disk afterward. **No card images are inside the mod**, and none
travel across the mod's own network — the server sends your game the address of a picture, and
your game fetches it itself. You need no account and no API key. A card you've already seen
works offline; a card nobody on your machine has ever looked up needs a connection once.

The server only ever sends your game the details of cards you're allowed to see. That's what
makes the hidden-information promise above hold up.

## Multiplayer

Install the mod on the server and on every client — the same version, and Fabric players also
need Fabric API. Any table seats two; four tables merged seat eight. People who aren't playing
can watch: the board renders on the table block itself, so a game is something you can walk
past and see.

## Status

**Pre-release, and honest about it.** The game is built and playable from end to end — import a
deck, sit down, play a full game, collect and draft if your server wants that. Two things stand
between this and a first release:

- **The art is placeholder.** Textures, block models and the interface graphics are generated
  stand-ins, being drawn properly now.
- **It has never been played by four humans at once.** Multiplayer is built, and tested by
  machine including the hidden-information rules, but the real four-player session that proves
  it is enjoyable hasn't happened yet.

If you're here early and want to help, that second one is the useful thing — see
[`TESTING.md`](TESTING.md), which says what to try and what's worth reporting.

## Questions people ask

**Does it enforce the rules?** No, deliberately, and it never will. See above.

**Which formats can I play?** Any, since nothing is enforced during a game. The pre-game deck
check knows Standard, Pioneer, Modern, Legacy, Vintage, Pauper, Commander and Oathbreaker — or
choose free play and it checks nothing at all.

**Can I use my Moxfield deck?** Yes — use Moxfield's More → Export → Text and paste that.
Moxfield links are recognized but can't be fetched; their API refuses other tools, and this mod
won't work around that.

**Does this need a resource pack, or a Scryfall account?** Neither.

**Will it hammer my server?** No. Card lookups are batched, rate-limited, cached on disk and
done off the main thread. A hundred-card decklist costs two requests cold and none warm.

**Is this legal?** It follows the Wizards Fan Content Policy: free, no paywalls, no real-money
anything, no Wizards trademarks in the name, and no Wizards artwork inside the download. It
follows Scryfall's API guidelines the same way.

**Is there a rules-enforcing digital client instead?** Yes, several, and they're good. This
isn't trying to be one — it's trying to be the kitchen table.

## For developers

The full design is in [`docs/design-brief.md`](docs/design-brief.md); project conventions and
version pins are in [`DIALECT.md`](DIALECT.md); [`TESTING.md`](TESTING.md) is the by-hand
checklist. Gradle must run on **Java 21**.

```bash
./gradlew verify               # the gate: build, tests, data generation, headless game tests
./gradlew :core:test           # the fast loop - pure logic only, seconds
./gradlew :neoforge:runClient  # play it
```

`core` is pure Java with no Minecraft on its classpath and `common` has no loader imports, both
enforced by the build rather than by convention, which keeps the layer that can be tested in
milliseconds as large as possible. Beside the gate sit checks for translation keys, stranded
documentation, and a scripted client that plays a whole game and photographs every step.

## Licence

Mod code is [MIT](LICENSE). It contains no Wizards of the Coast assets and no Scryfall data.
