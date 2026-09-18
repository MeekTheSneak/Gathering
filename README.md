# Gathering

Sit down at a table in Minecraft and play a card game with real decks. Gathering turns a table
block into a tabletop surface: cards you pick up and read, a hand only you can see, and up to
eight players around one board. You move the cards and you decide what happens. Nothing is
automated.

> **Unofficial Fan Content.** Not approved or endorsed by Wizards of the Coast. Portions of the
> materials used are property of Wizards of the Coast. ©Wizards of the Coast LLC.
>
> Card data and images come from [Scryfall](https://scryfall.com). No card images ship inside
> this mod; each player's game fetches and caches its own.

| | |
|---|---|
| **Version** | 0.3.1 (pre-release) |
| **Minecraft** | 1.21.1 |
| **Loaders** | NeoForge 21.1.1+ and Fabric (Loader 0.15.11+, Fabric API 0.102.0+) |
| **Players** | Singleplayer, LAN, or a dedicated server |
| **Needs** | An internet connection the first time it looks up a card |
| **License** | MIT |

---

## What you do

**Build a table.** Three wool over a frame of planks. Place it and it becomes a three-by-three
table you can walk around, with chairs to match. Put four tables together and they merge into
one surface seating eight.

Tables, chairs, shop counters, standings desks and display cases all come in every wood in the
game, plus cobblestone, polished blackstone and crying obsidian for builds that planks don't
suit. They behave identically, so mixing materials is a decision about the room.

**Get a deck.** Servers choose one of two ways in. By default you earn your cards: open packs,
keep a collection, build a deck from what you own. A server can instead let everyone import a
decklist — `/gathering import`, paste, and you hold the deck. It reads exports from Moxfield,
Archidekt, MTG Arena, MTGO and deckstats, or plain `1 Sol Ring` lines. Paste an Archidekt link
and it fetches the deck itself, with the exact printings, since Archidekt's API gives the
Scryfall id of every card. Operators can always import, whatever the setting.

**Sit down and play.** Walk up holding a deck and right-click. You're seated, shuffled and
holding seven. From there it's a table: drag cards where you want them, tap them, put them in
the graveyard, count your life.

Dice and coins are built in — any die up to twenty sides, a coin, and the planar die with its
four blanks, chaos and planeswalk. The server rolls, so nobody has to be trusted, and the
result goes in the game log under your name.

**Talk to the table.** Your chat key at the board reaches the people playing and watching and
nobody else. It shows up over the felt as well as in the chat window.

**Show your hand** to one player or the whole table, and take it back afterwards. Only ever
your own hand: "target player reveals their hand" is settled the way it is in paper, by that
player turning it round.

**Blank cards and emblems** cover whatever Magic has invented that isn't a card — the monarch,
the initiative, the ring. Write on one and put it on the table. The four dungeons work the way
they do in paper, with the room you're in written on the card.

Hold **Alt** over any card to read it. In the world it fills the window, the card on one side
and its text on the other, and it tilts with your mouse, which is how you catch the light on a
foil. Press **F1** at the table for the full key list. Press **V** to switch between playing on
your screen and playing on the table block itself, which is also what everyone standing around
it sees. The table's shortcuts — pass turn, untap, draw, scry, mill, shuffle, the action
palette — are all rebindable in Minecraft's own Controls screen.

## Two things that make it different

**There is no rules engine, and there never will be.** The mod moves cards, tracks numbers and
shows things. It never says no. You can tap a tapped creature, set your life to minus eleven,
or draw six on turn one, exactly like sitting at a kitchen table with a pile of cards. The
rules live in your heads, and the game log records who did what, by name, so the table can
check. The one exception is a deck check before a formatted game starts, the same way a
tournament checks decks at the door, and it stops once the game begins.

**Hidden information is real.** Your hand is yours. The identity of a card in a hidden zone is
never sent to a player who isn't entitled to see it, so there is nothing on the other end for a
modified client to read. Face-down cards travel as blank markers that change every time they
flip, and shuffles come from a seed that is never logged, sent or shown. This is the mod's one
security property and it has its own test suite.

## Collecting

On by default, because a card typed out of a decklist and a card opened out of a pack can't
both be ordinary at the same table. Unless your server says otherwise, cards are things you own:

- **Find and buy sealed product.** Packs turn up in loot. Place a shop counter in a village and
  an unemployed villager takes the job, selling boosters, boxes, Commander decks and cases for
  **Mana Coins**, which are found in chests rather than farmed. What a shopkeeper stocks depends
  on their level: packs from day one, a display box once you're a regular, a case from somebody
  who knows you. Every shop in the world stocks the same shelf, and the shelf turns over.
- **Open a pack properly.** Right-click and the pack comes to the middle of the screen; the tear
  follows your cursor across the wrapper, and the torn edge glows with the best card inside
  before any of them is shown. Shift-right-click if you'd rather just have the cards.
- **Real collation.** Packs are built from the print sheets a set was actually sold with, read
  from published set data, so a pack from a given set contains what that pack contained.
- **Keep a collection.** File cards into a collection block and search it the way you search a
  card site: `t:elf c:g mv<=3`. Build a deck out of it card by card, or from a list in one go.
- **Draft** with four to eight players, keep your pool, build out of it. Cube drafting too.
- **Trade** with another player, lend a deck to somebody who has none, or play for keeps with an
  ante, which only happens when everyone at the table agrees to it.
- **Archive Packs** are the rare drop that holds what nothing else on your server can reach:
  promos, buy-a-box cards, the long tail of a set nobody sells any more. They shrink as a server
  adds products, and a server whose shops already cover its catalog drops none.
- **Display cases** put one card under glass, face up and readable across the room. Only the
  owner can put a card in or take it out.

Cards remember how they came to you — won at ante, fished out of the sea, opened in a pack — and
the card's own screen shows that history.

## Tournaments

Place a Scorekeeper's Desk and any player can run a Swiss event from it. Players sign up at the
desk, check in, and the desk pairs them, runs the round clock, keeps standings and cuts to a top
eight. Results are entered the way the Companion app asks for them: games you won, games they
won, games drawn. Prizes can be put up before the tournament exists and are handed out at the
end. One host can run more than one event, from different desks.

If Create is installed, a Display Link on the desk writes standings, pairings, the round and its
clock, final places, prizes or the sign-up list onto a board. A link on a table writes the life
totals.

## Where the cards come from

Card names, rules text and art come from [Scryfall](https://scryfall.com). No card images are
inside the mod, and none travel across the mod's own network: the server sends your game the
address of a picture and your game fetches it. You need no account and no API key.

By default the server downloads Scryfall's bulk card file once (about 110 MB) and builds a local
index, so ordinary play makes no card requests at all. The per-card API is the fallback for
anything the index doesn't have. A card you've already seen works offline.

The server only ever sends your game the details of cards you're allowed to see, which is what
makes the hidden-information promise hold.

## Multiplayer

Install the mod on the server and on every client, at the same version; Fabric players also need
Fabric API. Any table seats two, four merged tables seat eight. People who aren't playing can
watch: the board renders on the table block, so a game is something you can walk past and see.

Finished casual games can be watched back. By default only the people who played can watch;
tournament matches are public once the event is over.

## Status

Pre-release. The game is built and playable end to end: get a deck, sit down, play a full game,
collect and draft and run a tournament if your server wants that. Two things stand between this
and a first release.

**Some art is still generated.** The felt, the booster wrapper and the shopkeeper are hand
drawn, and the mana symbols are assembled from hand-drawn parts. Block faces — including the
collection block and the shop counter — and every interface sprite are still generated
stand-ins, as is the Mana Coin.

**The table hasn't been played by two people yet.** Collecting has — packs, the shop, building from
what you own, played end to end by two people with no bugs found. The game at the table is built and
tested by machine, hidden-information rules included, but no two humans have sat down at one. If
you're here early, that's the useful thing — [`TESTING.md`](TESTING.md) says what to try.

## Questions

**Does it enforce the rules?** No, and it never will.

**Which formats can I play?** Any, since nothing is enforced during a game. The pre-game deck
check knows Standard, Pioneer, Modern, Legacy, Vintage, Pauper, Premodern, Commander, Duel
Commander, Oathbreaker and Limited. Choose free play and it checks nothing.

**Can I use my Moxfield deck?** Yes. Use More → Export → Text and paste that. A Moxfield *link*
can't be fetched — their API answers other tools with 403, and working around that is not
something this mod will do. An Archidekt link works as a link: paste it and the deck is read from
their public API, printings and all.

**Does this need a resource pack or a Scryfall account?** Neither.

**Can I change how it looks?** All of it. Nothing in the mod draws a colored rectangle: every
panel, band, tint, ring, badge and bar is a texture, so a resource pack can replace any of it.
Twelve looks ship — five palettes of one construction, three built around their own card frame,
four around a frame drawn by hand — and **Options → Video Settings → Gathering look** switches
between them. A thirteenth set of sprites is a labeled template to paint over rather than a look,
so it is not in the list. Adding your own is a folder of PNGs and a four-line file, with no code; a look can
leave elements out and inherit them, so repainting six things is a complete look.
[`docs/themes.md`](docs/themes.md) is the guide and
[`docs/gui-elements.png`](docs/gui-elements.png) is the page to paint from.

**Will it hammer my server?** No. Card lookups are batched, rate-limited, cached on disk and done
off the main thread, and with the bulk index most lookups never leave the machine.

**Is there a tutorial?** Yes. A six-step guided game teaches the table's controls, naming
whichever keys you have the verbs bound to.

**Can new players sit down without a deck?** Yes, if the server sets up loaner decks: an operator
drops decklists in a directory and any table can lend one.

**Is this legal?** It follows the Wizards Fan Content Policy — free, no paywalls, no real-money
anything, no Wizards trademarks in the name, no Wizards artwork in the download. It follows
Scryfall's API guidelines the same way.

**Is there a rules-enforcing digital client instead?** Yes, several, and they're good. This isn't
trying to be one.

## For developers

The design is in [`docs/design-brief.md`](docs/design-brief.md); conventions and version pins are
in [`DIALECT.md`](DIALECT.md); [`TESTING.md`](TESTING.md) is the by-hand checklist. Gradle needs
**Java 21**.

```bash
tools/gate.sh                  # the gate: nothing is done until this exits zero
./gradlew :core:test           # the fast loop, pure logic, seconds
./gradlew :neoforge:runClient  # play it
```

`tools/gate.sh` runs `./gradlew verify` — both loaders built, unit tests, the architecture
fences, data generation and both loaders' in-world tests — then eighteen static checks
(translation keys, stranded documentation, dev-scene steps, game-test plots, one gesture per
verb, GUI art with nothing drawing it, client state, save round-trips, threading, block and item
textures against their models, art hashes, table layout, key bindings, preferences, recipes,
voice lines, spelling, and mixin registration), then three checks that the tests actually
discovered anything.

`core` is pure Java with no Minecraft on its classpath and `common` has no loader imports, both
enforced by the build. That keeps the layer testable in milliseconds as large as possible.
Beside the gate, `tools/smoke.sh` boots both loaders and `tools/shots.sh` drives a scripted
client through a whole game and photographs every step.
[`tools/README.md`](tools/README.md) lists the rest.

## License

Mod code is [MIT](LICENSE). It contains no Wizards of the Coast assets and no Scryfall data.
