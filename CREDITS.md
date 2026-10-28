# Credits

How the mod was made: see the [AI disclosure](README.md#ai-disclosure) in the README.

## Interface art: BDragon1727's pixel UI packs

The interface's construction - the rail along the top of an Arcade panel, the way a progress bar
is capped and lit, the scroll bar as a pill standing on end, the arrows on the page turns, the
pressed face a button wears while it is held, the ring of dots that turns while a card's art is on
its way, the halo outside a ring meant to catch the eye - is drawn from BDragon1727's pixel UI
packs:

- [Basic Pixel Health bar and Scroll bar](https://bdragon1727.itch.io/basic-pixel-health-bar-and-scroll-bar)
- [Pixel Buttons pack All](https://bdragon1727.itch.io/pixel-buttons-pack-all)
- Custom Border and Panels Menu

**What is used.**

- His sprites, cut straight off the sheets: the progress bar and the hollow sheared box it runs in,
  a box of pips taken apart into its two caps and its two kinds of cell, the two arrows (turned a
  quarter for up and down) and the five frames of the spinner. Every look's version of these is
  his pixels with the hue moved, so the shapes, the outline, the dithering and the highlights are
  still his and only the color is the project's. The one thing changed on top of that is the
  contrast, where one of his sprites carries a word.
- Four 64 by 64 frames from the border pack, colors and all. The Ember, Arcane, Verdant and Royal
  looks are built around them.
- The rest of the interface - the panels, the buttons, the scroll bars, the rings, the washes, the
  felt and the card stock - is the project's own, drawn in his weight: two pixels of outline and no
  blend inside it.

**Where they are.** In the released jars, and not in the public source repository: the source
leaves out every sprite that is his or is painted from his, and
[docs/third-party-sprites.txt](docs/third-party-sprites.txt) lists them. A jar built from the source
draws Minecraft's missing-texture pattern where they would be, and the four framed looks use the
Basic look's panel instead of his frame.

**The terms.** The packs are free to use in non-commercial games, which this is, and say "modify as
desired". The author asks for a mention, and this is it. If Gathering is ever released
commercially, the packs' terms ask for a contribution of any amount, and that is a debt to settle
before it is. These sprites are used under BDragon1727's terms, not under the MIT license that
covers the mod's code.

## Card data and images: Scryfall

Card names, rules text, set lists, set symbols and card images come from
[Scryfall](https://scryfall.com), through its public API and its bulk data. None of it ships in the
mod. The server downloads card data while it runs - by default Scryfall's bulk card file, checked
once a day - and each player's game downloads and caches the images it shows. The server's requests
to Scryfall are rate-limited, and every request names the mod.

## Set data: MTGJSON

What a booster of a given set can hold - the print sheets each set was really sold with - is read
from [MTGJSON](https://mtgjson.com)'s published set files. The server downloads a set's file when
it needs it and keeps it on disk; none of it ships in the mod.

## Decks: Archidekt

When a player pastes an [Archidekt](https://archidekt.com) deck link into the import screen, the
server reads that one deck from Archidekt's public API. Nothing else is fetched from Archidekt.

## Libraries

No third-party library is bundled in the mod's jars. JSON is read with Gson, from the copy
Minecraft already ships. Create, Ponder, Flywheel, Registrate and Sable's companion are compiled
against for the optional NeoForge integrations and never shipped; they are used only when a player
has installed them.

## Wizards of the Coast

Gathering is unofficial Fan Content permitted under the Fan Content Policy. Not approved/endorsed by
Wizards. Portions of the materials used are property of Wizards of the Coast. ©Wizards of the Coast
LLC.

The mod ships no Wizards of the Coast artwork. Its card back and mana symbols are its own.

## Minecraft

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.

## Everything else

The card back, the sleeves, the mana badges and marks, the shopkeeper, every block and item
texture and every sound are the project's own. So is the icon, a render of the mod's own card back
and felt, in which the arm wears MeekTheSneak's own Minecraft skin.
