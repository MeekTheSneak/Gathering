# Changelog

What each release changed, for players and server owners.

## 1.0.0

The first public release.

**What you get**

- A table block, three blocks by three, that seats two; four in a line seat eight. Chairs, shop
  counters, Scorekeeper's Desks, collections and display cases to furnish a card room, in every
  wood and in three stones.
- A card game played the way it is at a kitchen table: real cards from Scryfall, a hand nobody
  else can see, and no rules engine. Dice, a coin and the planar die, life, counters, commander
  damage and tax, tokens, emblems, blank cards to write on, the dungeons, a game log and Undo.
- The board on the table block itself, which anyone nearby can watch, and a flat board one key
  away.
- Collecting: packs in loot and in village card shops, sold for Mana Coins found in chests;
  collation from each set's real print sheets; packs torn open one card at a time; collections you
  can search; a deck builder; drafts and cubes; trades; loaner decks; playing for keeps, only when
  everyone agrees.
- Swiss tournaments run from a Scorekeeper's Desk, with a round clock, standings, a top-eight cut
  and prizes.
- Replays of finished games, a six-step lesson, an in-game guide, keys you can rebind, and twelve
  looks a resource pack can extend.
- NeoForge and Fabric; singleplayer, LAN and dedicated servers. On NeoForge, Create's Display Links
  and Deployers, and tables on Create Aeronautics ships.

**New since 0.3.5**

- An icon, shown beside the mod in the mod list on both loaders.
- A table opens on the board on the table block. The flat board is a key away (V), and once chosen
  it is remembered.
- Every other player's hand is held at the edge of their mat, as a fan of card backs, on both
  boards. A hand shown to you is laid out as a row you can read.
- Commander damage takes life as well, and giving it back gives the life back. Crouch-click a life
  total to record it.
- Counters are written on the felt beside each player's life total.
- A refused action at the table is shown on the board as well as in chat. So are time being
  called, each extra turn, and a match recorded at time.
- On the table block, a game played for keeps shows its pot, which sits on top of whatever stands
  where it lies; a card on somebody else's mat is marked with its owner; emblems and written cards
  show their writing.
- A player alone at a table can take back their own shuffle or draw with Undo.
- A key bound to a table action does that action at the board, even if it is also your chat key
  or F1.
- The draft screen has a Done button, the deck screen says what each click does, and the trade
  screen says "Not agreed: the offer changed." when the offer moved as you agreed.

**Changed**

- Nothing on the board states a rule any more: "Going first: no draw this turn" is gone.
- Taking back a shuffle, or a draw of your own library, needs everyone at the table to agree,
  because it would show you your library's order. A seat taken, a deck put down and who goes first
  are never rewound.
- The key list, the help lines and the guide name the key each action is on now, rebound or not.
- The flat board no longer draws the world under it, and both boards draw far less each frame; a
  room full of tables no longer reloads card art over and over.
- The board's panels are spaced for the text size you choose, and writing on a blank card shrinks
  to its smallest size before a word is cut.
- V frames the table for the view you actually have when slowed or sped up.

**Fixed**

- Dying while a pack is open no longer loses the pack. Anything handed to a player who has died -
  cards from a pack, a prize, a draft pool, a deck back from a table, a pot - is kept for them and
  handed over when they respawn.
- Opening the last pack or box in your hand by clicking no longer deletes what it gives you, and a
  stack that only partly fits a creative inventory is no longer cut short.
- A collection in any wood drops as that wood. Breaking a collection, a display case or an engraved
  trophy in creative keeps what it holds.
- A deck or a pot goes to its owner wherever they are on the server.
- An explosion no longer destroys a table with a game on it, an occupied chair, a desk running a
  tournament, a collection or display case with cards in it, or a trophy someone won.
- A face-down card in its holder's hand, a hand shown to an empty chair, and a move naming a card
  the sender cannot see no longer give anything away.
- Nobody is staked in a game for keeps without their own yes, and nobody who was never asked is
  seated in it or takes its pot.
- A card put in a display case keeps its story, and so do cards built into a deck from a list,
  two cards made into a deck, and a deck poured back into a collection.
- A counter named like a power and toughness no longer breaks every client at the table.
- At a long table played apart, a highlight no longer lights the table next door.
- The lesson's Back, Skip this, Restart and Leave buttons can be clicked.
- The flat board takes no click where it draws nothing, and a watcher's camera keys move the view.
- The collection, the setup screen, the lobby and the replay list say on screen when a button is
  refused, a set clicked on the set list is answered, and an import pressed twice ends on the deck
  it made.
- A pass taken back with Undo stops counting toward a tournament table's extra turns, and a top-cut
  winner who drops concedes the next match.
- A draft pick is taken from the pack it was made on or not at all; Take comes back if the server
  never answered; a card with no details, or a pick made out of reach of the tables, says why.
- A download that stops halfway gives up instead of tying up the server until a restart, the list
  of every set is downloaded only once, and names typed into a collection's keys are looked up off
  the server's main thread.
- A pack that arrives while another screen is open goes back to it afterwards, and a second pack
  waits its turn.
- On Fabric, every display case shows its card through the glass.
