# Credits

## Pixel UI, BDragon1727

The GUI's construction — the rail along the top of an Arcade panel, the way a progress bar is
capped and lit, the scroll bar as a pill standing on end, the four arrows on the page turns,
the pressed face a button wears while it is held, the ring of dots that turns while a card's
art is on its way, the halo outside a ring meant to catch the eye — is drawn from BDragon1727's
pixel UI packs:

- [Basic Pixel Health bar and Scroll bar](https://bdragon1727.itch.io/basic-pixel-health-bar-and-scroll-bar)
- [Pixel Buttons pack All](https://bdragon1727.itch.io/pixel-buttons-pack-all)
- Custom Border and Panels Menu

Both are free to use in non-commercial games, which this is, and both say "modify as desired".
The author asks for a mention and gets one here. If Gathering is ever released commercially the
packs' terms ask for a contribution of any amount, and that is a debt to settle before it is.

What is in this repository, precisely:

- `art/gui/parts/` holds his sprites, cut straight off the sheets by `tools/pack_cut.py` and
  checked in so the build never needs the packs: the progress bar and the hollow sheared box
  it runs in, a box of pips taken apart into its two caps and its two kinds of cell, the two
  arrows, the scroll thumb and the five frames of the spinner. His button was taken too and
  then given back - the mod's own is the one this project prefers.

  Every theme's version of those is his pixels with the hue moved: `recut()` in
  `tools/gui_art.py` sorts his tones by how light they are and lays them on that look's own
  ramp, so the shapes, the outline, the dithering and the highlights are all still his and
  only the colour is ours. The one thing changed on top of that is the contrast floor, where
  one of his sprites carries a word.
- `art/gui/frames/` holds four 64x64 frames cut from the border pack's own sheets, colours and
  all. Four looks - Ember, Arcane, Verdant and Royal - are built around them: the frame is what
  those looks are, so it is his, and the rest of each set is drawn flat in colours taken off it.
- Everything else under `textures/gui/sprites` is painted by `tools/gui_art.py` from the mod's
  own palettes: the panels, the buttons, the rings, the washes, the felt and the card stock.
  Those are the project's own shapes drawn in his weight - two pixels of outline and no blend
  inside it, and surfaces carrying more colour than they were mixed with. That weight is what
  was taken from the packs there, rather than the pixels.

The rest - the palettes, the card back, the sleeves, the mana badges - is the project's own.

## Everything else

The card back, the sleeves, the mana badges and marks, every screen sprite and every block and
item texture are the project's own. See section 15 of `docs/design-brief.md` for what that rule
is for and the one time it was broken.
