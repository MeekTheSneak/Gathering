# Reference board and sources — WORKING NOTES, DELETE WHEN IMPLEMENTED

Temporary. Everything here is either to be built or to be folded into
`docs/design-brief.md`; this file goes away once it is.

The reference screenshot and the mod's Lua are **deliberately not committed**. The Lua is
somebody else's licensed work, and the screenshot carries card art — no Wizards imagery
ships in this repository. What is recorded here is what was read off them.

## Links

- `https://github.com/taw/magic-search-engine` — Ruby implementation of Scryfall-style card
  search, with a full card database. Useful as a reference for query syntax and for card data
  we would otherwise ask Scryfall for.
- `https://www.lethe.xyz/mtg/collation/` — how real booster packs are collated (print sheets,
  slot rules, duplicate protection). This is the thing to build against if the collection
  economy ever opens packs, rather than inventing a rarity roll.
- `https://github.com/TeamDman/Guides/blob/master/MTG/TabletopSimulator.md` — a written guide
  to the TTS Magic table: what the buttons do and how a game is actually run on it.

## What the reference board looks like

One seat's playmat, read off a top-down screenshot of a four-player table.

- The mat is a coloured rectangle with a bright border, about twice as wide as it is deep.
  Each seat's colour is its own; the arrangement is mirrored per seat so everything sits on
  its own player's outer side.
- **A bordered group of square icon buttons** stands in a column at the player's outer edge -
  three visible on the board, and the mod's script has six verbs behind them: mulligan,
  untap, draw, scry, mill, reveal. This is the affordance we do not have at all.
- **Two counted slots sit below that group**, side by side, each with a number under it.
  These are the commander slots and their tax - note *two*, not one zone holding several.
- **A line across the mat** marks off a strip along the player's own edge, and it stops at the
  button column rather than running through it. Ours already does this.
- **Life totals are large tokens in the middle of the table**, between the mats, one per
  player in that player's colour - not text in a status bar.
- Library, graveyard and exile are **invisible when empty**: they are scripting zones with no
  drawn slot. We deliberately differ, because aiming a dropped card at a zone needs the zone
  to be visible.

## What that means for us

1. Build the on-mat button group (task #47). Confirmed by the reference, and it is the
   biggest discoverability gap left.
2. Decide whether the command zone becomes two counted slots rather than one pile
   (**needs the user** - it changes the game model, not just the drawing).
3. Consider life as a token on the felt rather than only a line of status text.
