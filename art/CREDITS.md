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

- Every sprite under `textures/gui/sprites` is painted by `tools/gui_art.py` from the mod's own
  palettes. None of the packs' own files are in the tree.
- `art/gui/frames/` holds four 64x64 frames cut from the border pack's own sheets, colours and
  all. Four looks - Ember, Arcane, Verdant and Royal - are built around them: the frame is what
  those looks are, so it is his, and the rest of each set is drawn flat in colours taken off it.
  The other ten looks contain nothing of his.

The rest - the constructions, the palettes, the bars, the card back, the sleeves, the mana
badges - is the project's own, drawn in the idiom those packs taught.

## Everything else

The card back, the sleeves, the mana badges and marks, every screen sprite and every block and
item texture are the project's own. See section 15 of `docs/design-brief.md` for what that rule
is for and the one time it was broken.
